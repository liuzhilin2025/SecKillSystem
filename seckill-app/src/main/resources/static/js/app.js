/**
 * FlashSale 秒杀系统前端
 * 对接接口全部来自本项目后端，无任何第三方依赖。
 */
(function () {
    'use strict';

    /* ============================ 基础工具 ============================ */

    var $ = function (sel, root) { return (root || document).querySelector(sel); };
    var $$ = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };

    var TOKEN_KEY = 'flashsale_token';
    var USER_KEY = 'flashsale_user';

    var state = {
        token: localStorage.getItem(TOKEN_KEY) || '',
        user: safeParse(localStorage.getItem(USER_KEY)),
        activityId: 1,
        goodsList: [],
        currentGoods: null,
        timer: null,
        seckillAbort: null,
        pollingTimer: null
    };

    function safeParse(text) {
        if (!text) return null;
        try { return JSON.parse(text); } catch (e) { return null; }
    }

    function esc(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    var toastTimer = null;
    function toast(message, isError) {
        var el = $('#toast');
        el.textContent = message;
        el.className = 'toast' + (isError ? ' error' : '');
        el.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { el.hidden = true; }, 2600);
    }

    function openModal(id) { $('#' + id).hidden = false; }
    function closeModal(id) { $('#' + id).hidden = true; }

    function money(value) {
        var n = Number(value);
        if (isNaN(n)) return '--';
        return n.toFixed(2);
    }

    /** 后端 LocalDateTime 格式为 "yyyy-MM-dd HH:mm:ss"，Safari 需要把空格换成 T */
    function parseDate(text) {
        if (!text) return null;
        var normalized = String(text).replace(' ', 'T');
        var d = new Date(normalized);
        return isNaN(d.getTime()) ? null : d;
    }

    function remainText(target) {
        var diff = target.getTime() - Date.now();
        if (diff <= 0) return null;
        var totalSec = Math.floor(diff / 1000);
        var d = Math.floor(totalSec / 86400);
        var h = Math.floor((totalSec % 86400) / 3600);
        var m = Math.floor((totalSec % 3600) / 60);
        var s = totalSec % 60;
        var pad = function (v) { return v < 10 ? '0' + v : String(v); };
        if (d > 0) return d + ' 天 ' + pad(h) + ':' + pad(m) + ':' + pad(s);
        return pad(h) + ':' + pad(m) + ':' + pad(s);
    }

    /* ============================ 请求封装 ============================ */

    function buildHeaders() {
        var headers = { 'Content-Type': 'application/json' };
        if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
        return headers;
    }

    async function request(path, body, method) {
        var options = {
            method: method || 'POST',
            headers: buildHeaders()
        };
        if (body !== undefined && body !== null) {
            options.body = JSON.stringify(body);
        }

        var resp;
        try {
            resp = await fetch(path, options);
        } catch (e) {
            throw new Error('网络异常，请确认后端已启动（' + path + '）');
        }

        var text = await resp.text();
        if (!resp.ok) {
            throw new Error('HTTP ' + resp.status + ' ' + (text || resp.statusText));
        }

        var json;
        try {
            json = JSON.parse(text);
        } catch (e) {
            throw new Error('响应不是合法 JSON：' + text.slice(0, 120));
        }

        // 本项目统一响应体：{ success, message, errorCode, data }
        if (json && typeof json.success === 'boolean') {
            if (!json.success) {
                var err = new Error(json.message || '请求失败');
                err.code = json.errorCode;
                err.silent = false;
                if (isNotLogin(err)) clearLoginState();
                throw err;
            }
            return json.data;
        }

        return json;
    }

    function isNotLogin(err) {
        var msg = (err && err.message) || '';
        return msg.indexOf('未登录') >= 0 || msg.indexOf('登录') >= 0 && msg.indexOf('无效') >= 0;
    }

    /* ============================ 登录态 ============================ */

    function saveLogin(token, user) {
        state.token = token;
        state.user = user;
        localStorage.setItem(TOKEN_KEY, token);
        localStorage.setItem(USER_KEY, JSON.stringify(user || {}));
        renderUser();
    }

    function clearLoginState() {
        state.token = '';
        state.user = null;
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        renderUser();
    }

    function isLogin() { return !!state.token; }

    function renderUser() {
        var box = $('#userBox');
        if (!state.user) {
            box.innerHTML = '<button class="btn btn-primary" id="btnOpenLogin">登录 / 注册</button>';
            $('#btnOpenLogin').addEventListener('click', function () { openModal('loginModal'); });
            return;
        }
        var name = state.user.nickname || (state.user.id ? '用户' + state.user.id : '已登录');
        var initial = name.slice(0, 1).toUpperCase();
        var avatarInner = state.user.avatar
            ? '<img src="' + esc(state.user.avatar) + '" alt="">'
            : esc(initial);

        box.innerHTML =
            '<div class="user-chip">' +
            '  <span class="user-avatar">' + avatarInner + '</span>' +
            '  <span class="user-name">' + esc(name) + '</span>' +
            '</div>' +
            '<button class="btn btn-ghost" id="btnLogout">退出</button>';

        $('#btnLogout').addEventListener('click', async function () {
            try {
                await request('/user/logout', {});
            } catch (e) {
                /* 退出失败也要清本地登录态 */
            }
            clearLoginState();
            toast('已退出登录');
        });
    }

    function requireLogin() {
        if (isLogin()) return true;
        toast('请先登录', true);
        openModal('loginModal');
        return false;
    }

    /* ============================ 滑块验证码 ============================ */

    var captchaResolver = null;
    var captchaRejecter = null;
    var captchaState = {
        id: '',
        bgNaturalWidth: 0,
        bgNaturalHeight: 0,
        templateNaturalWidth: 0,
        templateNaturalHeight: 0,
        offsetY: 0,
        scale: 1,
        maxMove: 0,
        moving: false,
        startX: 0,
        moveX: 0,
        startTime: 0,
        trackList: []
    };

    /** 打开滑块验证码，成功时 resolve(captchaId) */
    function openCaptcha() {
        return new Promise(function (resolve, reject) {
            captchaResolver = resolve;
            captchaRejecter = reject;
            openModal('captchaModal');
            resetCaptchaSlider();
            loadCaptcha();
        });
    }

    function cancelCaptcha() {
        if (captchaRejecter) {
            captchaRejecter(new Error('已取消验证'));
        }
        captchaResolver = null;
        captchaRejecter = null;
    }

    function resetCaptchaSlider() {
        var slider = $('#captchaSlider');
        slider.classList.remove('success', 'fail');
        $('#captchaThumb').style.left = '0px';
        $('#captchaProgress').style.width = '0px';
        $('#captchaSliderText').textContent = '按住滑块拖动';
        $('#captchaSliderText').style.opacity = '1';
    }

    async function loadCaptcha() {
        $('#captchaLoading').hidden = false;
        $('#captchaBg').removeAttribute('src');
        $('#captchaPiece').removeAttribute('src');

        var resp;
        try {
            resp = await fetch('/captcha/gen?type=SLIDER', {
                method: 'POST',
                headers: buildHeaders()
            });
        } catch (e) {
            captchaFail('验证码加载失败，请检查后端服务');
            return;
        }

        var json;
        try {
            json = await resp.json();
        } catch (e) {
            captchaFail('验证码响应解析失败');
            return;
        }

        if (!json || Number(json.code) !== 200 || !json.data) {
            captchaFail((json && (json.msg || json.message)) || '验证码生成失败');
            return;
        }

        var data = json.data;
        captchaState.id = data.id;
        captchaState.offsetY = (data.data && Number(data.data.y)) || 0;

        var bg = $('#captchaBg');
        var piece = $('#captchaPiece');

        bg.onload = function () {
            captchaState.bgNaturalWidth = bg.naturalWidth;
            captchaState.bgNaturalHeight = bg.naturalHeight;
            layoutCaptcha();
            $('#captchaLoading').hidden = true;
        };
        bg.onerror = function () { captchaFail('背景图加载失败'); };

        piece.onload = function () {
            captchaState.templateNaturalWidth = piece.naturalWidth;
            captchaState.templateNaturalHeight = piece.naturalHeight;
            layoutCaptcha();
        };

        bg.src = data.backgroundImage;
        piece.src = data.templateImage;
    }

    function layoutCaptcha() {
        var bg = $('#captchaBg');
        var piece = $('#captchaPiece');
        if (!bg.clientWidth || !bg.naturalWidth || !piece.naturalWidth) return;

        captchaState.scale = bg.clientWidth / bg.naturalWidth;

        var pieceWidth = piece.naturalWidth * captchaState.scale;
        piece.style.width = pieceWidth + 'px';
        piece.style.left = '0px';
        piece.style.top = (captchaState.offsetY * captchaState.scale) + 'px';

        var slider = $('#captchaSlider');
        var thumb = $('#captchaThumb');
        captchaState.maxMove = Math.max(0, slider.clientWidth - thumb.offsetWidth);
    }

    function captchaFail(message) {
        $('#captchaLoading').hidden = true;
        $('#captchaSliderText').textContent = message;
        toast(message, true);
    }

    function bindCaptchaDrag() {
        var slider = $('#captchaSlider');
        var thumb = $('#captchaThumb');
        var progress = $('#captchaProgress');
        var text = $('#captchaSliderText');
        var piece = $('#captchaPiece');

        function onDown(e) {
            if (slider.classList.contains('success')) return;
            var point = e.touches ? e.touches[0] : e;
            captchaState.moving = true;
            captchaState.startX = point.clientX;
            captchaState.moveX = parseFloat(thumb.style.left) || 0;
            captchaState.baseX = captchaState.moveX;
            captchaState.startTime = Date.now();
            captchaState.trackList = [{ x: point.clientX - slider.getBoundingClientRect().left, y: point.clientY, type: 'down', t: Date.now() }];
            text.style.opacity = '0';
            e.preventDefault();
        }

        function onMove(e) {
            if (!captchaState.moving) return;
            var point = e.touches ? e.touches[0] : e;
            var delta = point.clientX - captchaState.startX;
            var left = Math.min(Math.max(captchaState.baseX + delta, 0), captchaState.maxMove);

            thumb.style.left = left + 'px';
            progress.style.width = left + 'px';
            piece.style.left = left + 'px';

            captchaState.trackList.push({
                x: point.clientX - slider.getBoundingClientRect().left,
                y: point.clientY,
                type: 'move',
                t: Date.now()
            });
            captchaState.moveX = left;
            e.preventDefault();
        }

        function onUp(e) {
            if (!captchaState.moving) return;
            captchaState.moving = false;

            var rect = slider.getBoundingClientRect();
            captchaState.trackList.push({
                x: (e.changedTouches ? e.changedTouches[0].clientX : e.clientX) - rect.left,
                y: (e.changedTouches ? e.changedTouches[0].clientY : e.clientY),
                type: 'up',
                t: Date.now()
            });

            if (captchaState.moveX < 4) {
                resetCaptchaSlider();
                return;
            }
            submitCaptcha();
        }

        thumb.addEventListener('mousedown', onDown);
        thumb.addEventListener('touchstart', onDown, { passive: false });
        document.addEventListener('mousemove', onMove);
        document.addEventListener('touchmove', onMove, { passive: false });
        document.addEventListener('mouseup', onUp);
        document.addEventListener('touchend', onUp);
    }

    async function submitCaptcha() {
        var slider = $('#captchaSlider');
        var text = $('#captchaSliderText');
        text.style.opacity = '1';
        text.textContent = '校验中…';

        var payload = {
            id: captchaState.id,
            data: {
                bgImageWidth: captchaState.bgNaturalWidth,
                bgImageHeight: captchaState.bgNaturalHeight,
                templateImageWidth: captchaState.templateNaturalWidth,
                templateImageHeight: captchaState.templateNaturalHeight,
                startTime: captchaState.startTime,
                stopTime: Date.now(),
                trackList: captchaState.trackList,
                left: Math.round(captchaState.moveX / captchaState.scale),
                top: captchaState.offsetY
            }
        };

        var json;
        try {
            var resp = await fetch('/captcha/check', {
                method: 'POST',
                headers: buildHeaders(),
                body: JSON.stringify(payload)
            });
            json = await resp.json();
        } catch (e) {
            text.textContent = '校验请求失败';
            slider.classList.add('fail');
            setTimeout(function () { resetCaptchaSlider(); loadCaptcha(); }, 1000);
            return;
        }

        if (json && Number(json.code) === 200) {
            slider.classList.add('success');
            text.textContent = '验证通过';
            var captchaId = (json.data && json.data.id) || captchaState.id;
            setTimeout(function () {
                closeModal('captchaModal');
                if (captchaResolver) {
                    var resolve = captchaResolver;
                    captchaResolver = null;
                    captchaRejecter = null;
                    resolve(captchaId);
                }
            }, 420);
        } else {
            slider.classList.add('fail');
            text.textContent = (json && (json.msg || json.message)) || '验证失败，请重试';
            setTimeout(function () { resetCaptchaSlider(); loadCaptcha(); }, 1000);
        }
    }

    /* ============================ 登录 / 注册 ============================ */

    var authTab = 'password';

    function switchAuthTab(tab) {
        authTab = tab;
        $$('.tab').forEach(function (btn) {
            btn.classList.toggle('active', btn.dataset.authTab === tab);
        });
        $$('[data-auth-pane]').forEach(function (el) {
            var pane = el.dataset.authPane;
            var show = pane === tab || (pane === 'code' && tab === 'register');
            el.hidden = !show;
        });
        $('#loginTitle').textContent = tab === 'register' ? '注册账号' : '登录';
        $('#btnAuthSubmit').textContent = tab === 'register' ? '注 册' : '登 录';
        $('#authTip').textContent = '';
        $('#authTip').className = 'form-tip';
    }

    function setAuthTip(message, type) {
        var el = $('#authTip');
        el.textContent = message || '';
        el.className = 'form-tip' + (type ? ' ' + type : '');
    }

    async function sendVerifyCode() {
        var mobile = $('#authMobile').value.trim();
        if (!/^1\d{10}$/.test(mobile)) {
            setAuthTip('请输入正确的手机号', 'error');
            return;
        }
        var btn = $('#btnSendCode');
        btn.disabled = true;
        try {
            var captchaId = await openCaptcha();
            setAuthTip('正在发送…');
            await request('/user/code/send', {
                mobile: mobile,
                type: authTab === 'register' ? 1 : 2,
                captchaId: captchaId
            });
            setAuthTip('验证码已发送，请查看后端日志', 'ok');
            var seconds = 60;
            var timer = setInterval(function () {
                seconds--;
                if (seconds <= 0) {
                    clearInterval(timer);
                    btn.disabled = false;
                    btn.textContent = '发送验证码';
                } else {
                    btn.textContent = seconds + 's';
                }
            }, 1000);
            btn.textContent = seconds + 's';
        } catch (e) {
            btn.disabled = false;
            setAuthTip(e.message, 'error');
        }
    }

    async function submitAuth() {
        var mobile = $('#authMobile').value.trim();
        if (!/^1\d{10}$/.test(mobile)) {
            setAuthTip('请输入正确的手机号', 'error');
            return;
        }

        var btn = $('#btnAuthSubmit');
        btn.disabled = true;
        try {
            if (authTab === 'register') {
                var password = $('#authPassword').value;
                var confirm = $('#authConfirmPassword').value;
                if (password.length < 6) { setAuthTip('密码至少 6 位', 'error'); return; }
                if (password !== confirm) { setAuthTip('两次密码不一致', 'error'); return; }
                await request('/user/register', {
                    mobile: mobile,
                    password: password,
                    confirmPassword: confirm,
                    verifyCode: $('#authVerifyCode').value.trim()
                });
                setAuthTip('注册成功，正在自动登录…', 'ok');
                var loginData = await request('/user/login', { mobile: mobile, type: 1, password: password });
                saveLogin(loginData.token, loginData.userInfo);
            } else if (authTab === 'password') {
                var data = await request('/user/login', {
                    mobile: mobile,
                    type: 1,
                    password: $('#authPassword').value
                });
                saveLogin(data.token, data.userInfo);
            } else {
                var codeData = await request('/user/login', {
                    mobile: mobile,
                    type: 2,
                    verifyCode: $('#authVerifyCode').value.trim()
                });
                saveLogin(codeData.token, codeData.userInfo);
            }

            closeModal('loginModal');
            toast('登录成功');
            if (state.activityId) loadGoodsList();
        } catch (e) {
            setAuthTip(e.message, 'error');
        } finally {
            btn.disabled = false;
        }
    }

    /* ============================ 商品列表 ============================ */

    function statusBadge(status) {
        if (status === 0) return '<span class="goods-status status-0">未开始</span>';
        if (status === 1) return '<span class="goods-status status-1">进行中</span>';
        return '<span class="goods-status status-2">已结束</span>';
    }

    function renderGoodsList() {
        var grid = $('#goodsGrid');
        if (!state.goodsList.length) {
            grid.innerHTML = '<div class="empty-state"><div class="empty-icon"></div><p>该活动下暂无可秒杀商品</p></div>';
            return;
        }

        grid.innerHTML = state.goodsList.map(function (item) {
            var total = Number(item.seckillTotal) || 0;
            var stock = Number(item.seckillStock) || 0;
            var soldPercent = total > 0 ? Math.min(100, Math.round((total - stock) / total * 100)) : 100;
            var disabled = item.activityStatus !== 1 || stock <= 0;
            var btnText = stock <= 0 ? '已售罄' : (item.activityStatus === 0 ? '未开始' : (item.activityStatus === 2 ? '已结束' : '立即抢购'));

            return '' +
                '<article class="goods-card" data-goods-id="' + esc(item.goodsId) + '">' +
                '  <div class="goods-thumb">' +
                statusBadge(item.activityStatus) +
                '    <img src="' + esc(item.seckillImg || '') + '" alt="' + esc(item.seckillTitle) + '" ' +
                '         onerror="this.style.display=\'none\'">' +
                '  </div>' +
                '  <div class="goods-body">' +
                '    <div class="goods-title">' + esc(item.seckillTitle || '') + '</div>' +
                '    <div class="price-row">' +
                '      <span class="price-now"><small>&yen;</small>' + money(item.seckillPrice) + '</span>' +
                '      <span class="price-old">&yen;' + money(item.goodsPrice) + '</span>' +
                '    </div>' +
                '    <div class="stock-track"><div class="stock-bar" style="width:' + soldPercent + '%"></div></div>' +
                '    <div class="goods-foot">' +
                '      <span class="stock-text">剩余 <b>' + stock + '</b> / ' + total + '</span>' +
                '      <span class="countdown" data-begin="' + esc(item.beginTime) + '" data-end="' + esc(item.endTime) + '"></span>' +
                '    </div>' +
                '    <button class="btn-buy" ' + (disabled ? 'disabled' : '') + ' data-buy="' + esc(item.goodsId) + '">' + btnText + '</button>' +
                '  </div>' +
                '</article>';
        }).join('');

        tickCountdown();

        $$('.goods-card').forEach(function (card) {
            card.addEventListener('click', function (e) {
                if (e.target.closest('[data-buy]')) return;
                openDetail(Number(card.dataset.goodsId));
            });
        });

        $$('[data-buy]').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.stopPropagation();
                var goods = state.goodsList.filter(function (g) { return Number(g.goodsId) === Number(btn.dataset.buy); })[0];
                if (goods) startSeckill(goods);
            });
        });
    }

    function tickCountdown() {
        $$('.countdown').forEach(function (el) {
            var begin = parseDate(el.dataset.begin);
            var end = parseDate(el.dataset.end);
            if (!begin || !end) { el.textContent = ''; return; }

            if (Date.now() < begin.getTime()) {
                el.innerHTML = '距开始 <b>' + remainText(begin) + '</b>';
            } else if (Date.now() < end.getTime()) {
                el.innerHTML = '距结束 <b>' + remainText(end) + '</b>';
            } else {
                el.textContent = '活动已结束';
            }
        });

        var detailEl = $('.detail-countdown b');
        if (detailEl && state.currentGoods) {
            var goods = state.currentGoods;
            var begin2 = parseDate(goods.beginTime);
            var end2 = parseDate(goods.endTime);
            if (end2 && Date.now() >= end2.getTime()) {
                detailEl.textContent = '已结束';
            } else if (begin2 && Date.now() < begin2.getTime()) {
                detailEl.textContent = remainText(begin2);
            } else if (end2) {
                detailEl.textContent = remainText(end2);
            }
        }
    }

    async function loadGoodsList() {
        var activityId = Number($('#activityIdInput').value);
        if (!activityId || activityId <= 0) {
            toast('请输入合法的活动 ID', true);
            return;
        }
        state.activityId = activityId;

        var grid = $('#goodsGrid');
        grid.innerHTML = '<div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div>';
        $('#listMeta').textContent = '加载中…';

        try {
            var list = await request('/seckill/goods/list', { activityId: activityId });
            state.goodsList = list || [];
            $('#listMeta').textContent = '活动 #' + activityId + ' · 共 ' + state.goodsList.length + ' 件商品';
            renderGoodsList();
        } catch (e) {
            state.goodsList = [];
            grid.innerHTML = '<div class="empty-state"><div class="empty-icon"></div><p>' + esc(e.message) + '</p></div>';
            $('#listMeta').textContent = '加载失败';
        }
    }

    /* ============================ 商品详情 ============================ */

    async function openDetail(goodsId) {
        $('#listView').hidden = true;
        $('#detailView').hidden = false;
        $('#detailWrap').innerHTML = '<div class="skeleton" style="height:420px"></div>';

        try {
            var detail = await request('/seckill/goods/detail', { activityId: state.activityId, goodsId: goodsId });
            state.currentGoods = detail;
            renderDetail(detail);
        } catch (e) {
            $('#detailWrap').innerHTML = '<div class="empty-state"><div class="empty-icon"></div><p>' + esc(e.message) + '</p></div>';
        }
    }

    function renderDetail(item) {
        var total = Number(item.seckillTotal) || 0;
        var stock = Number(item.seckillStock) || 0;
        var soldPercent = total > 0 ? Math.min(100, Math.round((total - stock) / total * 100)) : 100;
        var imgs = item.goodsImgs && item.goodsImgs.length ? item.goodsImgs : [''];
        var disabled = item.activityStatus !== 1 || stock <= 0;
        var btnText = stock <= 0 ? '已售罄' : (item.activityStatus === 0 ? '未开始' : (item.activityStatus === 2 ? '已结束' : '立即抢购'));

        var thumbs = imgs.map(function (src, index) {
            return '<img src="' + esc(src) + '" data-index="' + index + '" class="' + (index === 0 ? 'active' : '') + '" alt="">';
        }).join('');

        $('#detailWrap').innerHTML =
            '<div class="detail-gallery">' +
            '  <img id="detailMainImg" src="' + esc(imgs[0]) + '" alt="' + esc(item.goodsName) + '">' +
            '  <div class="detail-thumbs">' + thumbs + '</div>' +
            '</div>' +
            '<div class="detail-info">' +
            '  <h2 class="detail-title">' + esc(item.goodsName || '') + '</h2>' +
            '  <div class="detail-price-box">' +
            '    <span class="price-now"><small>&yen;</small>' + money(item.seckillPrice) + '</span>' +
            '    <span class="price-old">&yen;' + money(item.goodsPrice) + '</span>' +
            '    ' + statusBadge(item.activityStatus) +
            '  </div>' +
            '  <div class="detail-countdown">倒计时 <b>--:--:--</b></div>' +
            '  <div class="stock-track"><div class="stock-bar" style="width:' + soldPercent + '%"></div></div>' +
            '  <div class="stock-text">已抢 ' + (total - stock) + ' 件，剩余 <b>' + stock + '</b> 件，共 ' + total + ' 件</div>' +
            '  <div class="detail-actions">' +
            '    <button class="btn btn-primary" id="btnDetailBuy" ' + (disabled ? 'disabled' : '') + '>' + btnText + '</button>' +
            '  </div>' +
            '  <div class="detail-desc">' +
            '    <h4>商品详情</h4>' +
            '    <div>' + (item.goodsDetail || '暂无详情') + '</div>' +
            '  </div>' +
            '</div>';

        $$('.detail-thumbs img').forEach(function (thumb) {
            thumb.addEventListener('click', function () {
                $$('.detail-thumbs img').forEach(function (t) { t.classList.remove('active'); });
                thumb.classList.add('active');
                $('#detailMainImg').src = thumb.src;
            });
        });

        var buyBtn = $('#btnDetailBuy');
        if (!disabled) {
            buyBtn.addEventListener('click', function () { startSeckill(item); });
        }

        tickCountdown();
    }

    /* ============================ 抢购主流程 ============================ */

    function logLine(text, type) {
        var box = $('#seckillLog');
        var line = document.createElement('div');
        line.className = type ? 'log-' + type : '';
        line.textContent = '[' + new Date().toLocaleTimeString('zh-CN', { hour12: false }) + '] ' + text;
        box.appendChild(line);
        box.scrollTop = box.scrollHeight;
    }

    function setStep(name, status) {
        var li = $('.steps li[data-step="' + name + '"]');
        if (!li) return;
        li.classList.remove('doing', 'done', 'error');
        if (status) li.classList.add(status);
    }

    function showBanner(text, type) {
        var banner = $('#resultBanner');
        banner.textContent = text;
        banner.className = 'result-banner ' + type;
        banner.hidden = false;
    }

    function resetSeckillModal() {
        $('#orderNoRow').hidden = true;
        $('#orderNoText').textContent = '';
        $('#resultBanner').hidden = true;
        $('#seckillLog').innerHTML = '';
        $$('.steps li').forEach(function (li) { li.classList.remove('doing', 'done', 'error'); });
    }

    async function startSeckill(goods) {
        if (!requireLogin()) return;

        if (goods.activityStatus !== 1) {
            toast(goods.activityStatus === 0 ? '活动还未开始' : '活动已结束', true);
            return;
        }
        if (Number(goods.seckillStock) <= 0) {
            toast('商品已售罄', true);
            return;
        }

        resetSeckillModal();
        openModal('seckillModal');
        logLine('开始抢购：' + (goods.seckillTitle || goods.goodsName || goods.goodsId));

        try {
            setStep('token', 'doing');
            logLine('打开人机校验…', 'info');
            var captchaId = await openCaptcha();
            logLine('验证码通过，captchaId=' + captchaId, 'ok');

            var tokenData = await request('/seckill/order/token', {
                activityId: state.activityId,
                goodsId: goods.goodsId,
                captchaId: captchaId
            });
            logLine('令牌签发成功，有效期 ' + tokenData.expireSeconds + ' 秒', 'ok');
            setStep('token', 'done');

            setStep('submit', 'doing');
            var order = await request('/seckill/order', {
                activityId: state.activityId,
                goodsId: goods.goodsId,
                token: tokenData.token
            });
            logLine('预扣成功，orderNo=' + order.orderNo + '，状态=' + statusText(order.status), 'ok');
            setStep('submit', 'done');
            setStep('queue', 'doing');

            $('#orderNoRow').hidden = false;
            $('#orderNoText').textContent = order.orderNo;
            showBanner('请求已受理，正在异步排队处理…', 'pending');

            waitOrderResult(order.orderNo);
        } catch (e) {
            if (e.message === '已取消验证') {
                logLine('已取消人机校验');
                closeModal('seckillModal');
                return;
            }
            setStep('submit', 'error');
            logLine('失败：' + e.message, 'err');
            showBanner(e.message, 'fail');
        }
    }

    function statusText(status) {
        if (status === -1) return '处理中';
        if (status === -2) return '秒杀失败';
        if (status === 0) return '待支付';
        return '状态 ' + status;
    }

    function finishOrder(result) {
        if (state.pollingTimer) {
            clearInterval(state.pollingTimer);
            state.pollingTimer = null;
        }
        if (state.seckillAbort) {
            try { state.seckillAbort.abort(); } catch (e) { /* ignore */ }
            state.seckillAbort = null;
        }

        setStep('queue', 'done');
        setStep('result', result.status === -2 ? 'error' : 'done');

        if (result.status === -2) {
            showBanner('秒杀失败：' + (result.statusDesc || '库存不足或被系统拒绝'), 'fail');
            logLine('最终结果：秒杀失败', 'err');
        } else if (result.status === 0) {
            showBanner('抢购成功，订单待支付（订单号 ' + result.orderNo + '）', 'ok');
            logLine('最终结果：待支付', 'ok');
        } else {
            showBanner('最终结果：' + (result.statusDesc || statusText(result.status)), 'pending');
            logLine('最终结果：' + (result.statusDesc || statusText(result.status)));
        }
    }

    async function waitOrderResult(orderNo) {
        // 1. 先订阅 SSE：能推则推
        subscribeResult(orderNo);

        // 2. 同时开轮询兜底：SSE 只推订阅后的事件，可能错过
        var deadline = Date.now() + 25000;
        state.pollingTimer = setInterval(async function () {
            if (Date.now() > deadline) {
                clearInterval(state.pollingTimer);
                state.pollingTimer = null;
                showBanner('等待超时，请稍后到「结果查询」重试', 'pending');
                return;
            }
            try {
                var result = await request('/seckill/order/result', { orderNo: orderNo });
                if (result && result.status !== -1) {
                    logLine('轮询获取到最终状态：' + statusText(result.status), 'ok');
                    finishOrder(result);
                }
            } catch (e) {
                /* 轮询失败不打断主流程 */
            }
        }, 800);
    }

    async function subscribeResult(orderNo) {
        var controller = new AbortController();
        state.seckillAbort = controller;

        try {
            var resp = await fetch('/seckill/order/result/subscribe', {
                method: 'POST',
                headers: buildHeaders(),
                body: JSON.stringify({ orderNo: orderNo }),
                signal: controller.signal
            });

            if (!resp.ok || !resp.body) {
                logLine('SSE 连接失败，改用轮询兜底', 'err');
                return;
            }

            logLine('SSE 已连接，等待服务端推送…', 'info');
            var reader = resp.body.getReader();
            var decoder = new TextDecoder();
            var buffer = '';

            while (true) {
                var chunk = await reader.read();
                if (chunk.done) break;

                buffer += decoder.decode(chunk.value, { stream: true });
                var blocks = buffer.split('\n\n');
                buffer = blocks.pop();

                blocks.forEach(function (block) {
                    var dataLines = block.split('\n')
                        .filter(function (line) { return line.indexOf('data:') === 0; })
                        .map(function (line) { return line.slice(5).trim(); });

                    if (!dataLines.length) return;
                    try {
                        var payload = JSON.parse(dataLines.join('\n'));
                        if (payload && payload.status !== undefined && payload.status !== -1) {
                            logLine('SSE 推送结果：' + statusText(payload.status), 'ok');
                            finishOrder(payload);
                        } else if (payload) {
                            logLine('SSE 推送：' + (payload.statusDesc || statusText(payload.status)));
                        }
                    } catch (e) {
                        /* 心跳或非 JSON 数据，忽略 */
                    }
                });
            }
        } catch (e) {
            if (e.name !== 'AbortError') {
                logLine('SSE 断开：' + e.message, 'err');
            }
        }
    }

    /* ============================ 管理端预热 ============================ */

    async function preheat() {
        var activityId = Number($('#adminActivityId').value);
        if (!activityId || activityId <= 0) {
            toast('请输入合法的活动 ID', true);
            return;
        }
        var tip = $('#adminTip');
        tip.className = 'form-tip';
        tip.textContent = '预热中…';

        var btn = $('#btnPreheat');
        btn.disabled = true;
        try {
            var count = await request('/admin/seckill/goods/cache/preheat', { activityId: activityId });
            tip.className = 'form-tip ok';
            tip.textContent = '预热成功，共处理 ' + count + ' 件商品（缓存 / 库存 / 令牌大闸 / 布隆过滤器）';
            toast('预热完成');
        } catch (e) {
            tip.className = 'form-tip error';
            tip.textContent = e.message;
        } finally {
            btn.disabled = false;
        }
    }

    /* ============================ 事件绑定 ============================ */

    function bindEvents() {
        $('#btnLoadList').addEventListener('click', loadGoodsList);
        $('#activityIdInput').addEventListener('keydown', function (e) {
            if (e.key === 'Enter') loadGoodsList();
        });

        $('#btnBackToList').addEventListener('click', function () {
            $('#detailView').hidden = true;
            $('#listView').hidden = false;
        });

        $('#btnAdmin').addEventListener('click', function () {
            if (!requireLogin()) return;
            $('#adminActivityId').value = state.activityId;
            $('#adminTip').textContent = '';
            openModal('adminModal');
        });

        $('#btnPreheat').addEventListener('click', preheat);

        $$('.tab').forEach(function (btn) {
            btn.addEventListener('click', function () { switchAuthTab(btn.dataset.authTab); });
        });

        $('#btnAuthSubmit').addEventListener('click', submitAuth);
        $('#btnSendCode').addEventListener('click', sendVerifyCode);

        $('#btnRefreshCaptcha').addEventListener('click', function () {
            resetCaptchaSlider();
            loadCaptcha();
        });

        $$('[data-close]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var id = btn.dataset.close;
                closeModal(id);
                if (id === 'captchaModal') cancelCaptcha();
            });
        });

        $$('.modal-mask').forEach(function (mask) {
            mask.addEventListener('mousedown', function (e) {
                if (e.target !== mask) return;
                mask.hidden = true;
                if (mask.id === 'captchaModal') cancelCaptcha();
            });
        });

        window.addEventListener('resize', function () {
            if (!$('#captchaModal').hidden) layoutCaptcha();
        });
    }

    /* ============================ 启动 ============================ */

    function start() {
        bindEvents();
        bindCaptchaDrag();
        renderUser();
        switchAuthTab('password');

        state.timer = setInterval(tickCountdown, 1000);
        loadGoodsList();
    }

    document.addEventListener('DOMContentLoaded', start);
})();
