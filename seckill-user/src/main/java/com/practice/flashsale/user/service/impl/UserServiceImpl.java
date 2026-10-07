package com.practice.flashsale.user.service.impl;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.spring.plugins.secondary.SecondaryVerificationApplication;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.practice.flashsale.common.entity.User;
import com.practice.flashsale.common.enums.ResultCodeEnum;
import com.practice.flashsale.common.exception.BizException;
import com.practice.flashsale.common.mapper.UserMapper;
import com.practice.flashsale.user.enums.LoginTypeEnum;
import com.practice.flashsale.user.enums.UserStatusEnum;
import com.practice.flashsale.user.enums.VerifyCodeTypeEnum;
import com.practice.flashsale.user.model.dto.LoginUserReqDTO;
import com.practice.flashsale.user.model.dto.RegisterUserReqDTO;
import com.practice.flashsale.user.model.dto.SendVerifyCodeReqDTO;
import com.practice.flashsale.user.model.vo.LoginUserRspVO;
import com.practice.flashsale.user.service.UserService;
import com.practice.flashsale.common.utils.NicknameUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author 沃淇淋
 * @since 2026-09-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource(name = "bizExecutor")
    private Executor bizExecutor;

    // 常量
    /** Redis 中验证码的 Key 前缀 */
    private static final String VERIFY_CODE_KEY_PREFIX = "verify_code:";
    /** Redis 中发送频率限制的 Key 前缀 */
    private static final String VERIFY_CODE_LIMIT_KEY_PREFIX = "verify_code_limit:";
    /** 验证码有效期：5 分钟 */
    private static final long VERIFY_CODE_EXPIRE_MINUTES = 5;
    /** 同一手机号发送间隔：60 秒 */
    private static final long VERIFY_CODE_LIMIT_SECONDS = 60;
    /** 验证码长度 */
    private static final int VERIFY_CODE_LENGTH = 6;
    /** Redis 中每日发送次数限制的 Key 前缀 */
    private static final String VERIFY_CODE_DAILY_LIMIT_KEY_PREFIX = "verify_code_daily:";
    /** 每日发送次数上限 */
    private static final int VERIFY_CODE_DAILY_LIMIT = 10;
    /** Redis 中登录失败次数的 Key 前缀 */
    private static final String LOGIN_FAIL_COUNT_KEY_PREFIX = "login_fail_count:";
    /** 登录失败次数上限（超过此值则临时锁定账号） */
    private static final int LOGIN_FAIL_MAX_COUNT = 5;
    /** 账号临时锁定时间（分钟） */
    private static final long LOGIN_LOCK_MINUTES = 30L;
    /** Redis 中行为验证码 ID 一次性标记的 Key 前缀（防同一个 captchaId 被重放） */
    private static final String CAPTCHA_USED_KEY_PREFIX = "captcha_used:";
    /** 行为验证码应用（tianai-captcha） */
    private final ImageCaptchaApplication imageCaptchaApplication;


    // 依赖
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    /** 验证码校验 Lua 脚本 */
    private final DefaultRedisScript<Long> checkAndDeleteVerifyCodeScript;
    /** 登录失败次数 Lua 脚本 */
    private final DefaultRedisScript<Long> checkAndIncrementLoginFailScript;
    /** 每日发送次数限制 Lua 脚本 */
    private final DefaultRedisScript<Long> checkAndIncrementDailyLimitScript;

    /**
     * 启动期校验：行为验证码必须开启二次校验
     * <p>
     * 若未开启，注入的 ImageCaptchaApplication 不是 SecondaryVerificationApplication 实例，
     * 二次校验会恒为 false，导致所有短信都发不出去（静默瘫痪）。
     * 与其运行期才发现，不如启动期直接失败。
     */
    @PostConstruct
    public void checkCaptchaSecondaryVerification() {
        if (!(imageCaptchaApplication instanceof SecondaryVerificationApplication)) {
            throw new IllegalStateException(
                    "ImageCaptchaApplication 未启用二次校验，请检查 captcha.secondary-verification.enabled 配置项");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long registerUser(RegisterUserReqDTO dto) {

        // 1. 校验两次密码输入是否一致
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BizException(ResultCodeEnum.USER_CONFIRM_PASSWORD_ERROR);
        }

        // 2. 校验短信验证码
        checkVerifyCode(dto.getVerifyCode(), dto.getMobile(), VerifyCodeTypeEnum.REGISTER.getPurpose());

        // 3. 手机号唯一性预检
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("mobile", dto.getMobile());
        Long count = userMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ResultCodeEnum.USER_MOBILE_EXISTS);
        }

        // 4. 组装实体，密码 BCrypt 加密
        User user = new User();
        user.setNickname(NicknameUtils.generate());
        user.setMobile(dto.getMobile());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(UserStatusEnum.ENABLED.getCode());
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // 5. 落库，唯一索引兜底
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发场景：两个请求同时通过第 2 步，靠 t_user.mobile 的唯一索引拦下来
            log.warn("注册并发冲突, mobile: {}", dto.getMobile());
            throw new BizException(ResultCodeEnum.USER_MOBILE_EXISTS);
        }

        log.info("用户注册成功, userId={}, mobile={}", user.getId(), user.getMobile());
        return user.getId();
    }

    @Override
    public LoginUserRspVO loginUser(LoginUserReqDTO dto) {
        // 根据手机号查询用户
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getMobile, dto.getMobile()));

        // 1. 判断用户是否存在
        if (user == null) {
            // 密码登录：返回模糊错误提示
            if (LoginTypeEnum.PASSWORD.getCode().equals(dto.getType())) {
                throw new BizException(ResultCodeEnum.USER_LOGIN_CREDENTIAL_ERROR);
            }
            throw new BizException(ResultCodeEnum.USER_MOBILE_NOT_REGISTERED);
        }

        // 2. 校验用户状态（是否被禁用）
        if (UserStatusEnum.DISABLED.getCode().equals(user.getStatus())) {
            throw new BizException(ResultCodeEnum.USER_STATUS_DISABLED);
        }

        // 3. 根据登录类型，进行身份验证
        if (LoginTypeEnum.PASSWORD.getCode().equals(dto.getType())) {
            // 检查登录失败次数
            checkLoginFailLimit(dto.getMobile());

            // 密码登录：校验密码是否正确
            checkPassword(dto.getPassword(), user.getPassword(), dto.getMobile());
        } else if (LoginTypeEnum.VERIFY_CODE.getCode().equals(dto.getType())) {
            // 验证码登录：校验验证码是否正确
            checkVerifyCode(dto.getVerifyCode(), dto.getMobile(), VerifyCodeTypeEnum.LOGIN.getPurpose());
        }

        // 4. 调用 SaToken 执行登录，传入用户 ID
        StpUtil.login(user.getId());

        // 5. 获取 SaToken 生成的 Token
        String token = StpUtil.getTokenValue();

        // 6. 构建用户信息
        LoginUserRspVO.UserInfo userInfo = new LoginUserRspVO.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setNickname(user.getNickname());
        userInfo.setAvatar(user.getAvatar());

        // 7. 组装响应
        LoginUserRspVO vo = new LoginUserRspVO();
        vo.setToken(token);
        vo.setUserInfo(userInfo);

        log.info("用户登录成功, userId={}, mobile={}", user.getId(), user.getMobile());
        return vo;
    }

    /**
     * 校验密码
     *
     * @param rawPassword     用户输入的明文密码
     * @param encodedPassword 数据库中存储的密文密码
     * @param mobile          手机号（用于构建 Redis Key）
     */
    private void checkPassword(String rawPassword, String encodedPassword, String mobile) {
        // 1. 判空：passwordEncoder.matches(null, ...) 会抛 IllegalArgumentException
        if (!StringUtils.hasText(rawPassword)) {
            // 登录失败次数 +1
            addLoginFailCount(mobile);

            throw new BizException(ResultCodeEnum.USER_LOGIN_CREDENTIAL_ERROR); // 改为模糊提示
        }

        // 2. BCrypt 校验：必须用 matches()，绝不能用 equals()
        //    （BCrypt 每次加密结果都不同，因为自带随机盐）
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            // 登录失败次数 +1
            addLoginFailCount(mobile);

            throw new BizException(ResultCodeEnum.USER_LOGIN_CREDENTIAL_ERROR); // 改为模糊提示
        }

        // 密码校验成功
        String failCountKey = LOGIN_FAIL_COUNT_KEY_PREFIX + mobile;
        stringRedisTemplate.delete(failCountKey);
    }

    /**
     * 校验短信验证码
     *
     * @param mobile     手机号（需要用它来拼 Redis Key）
     * @param purpose    场景（登录/注册）
     * @param verifyCode 用户输入的验证码
     */
    private void checkVerifyCode(String verifyCode, String mobile, String purpose) {
        // 1. 非空校验
        if (!StringUtils.hasText(verifyCode)) {
            throw new BizException(ResultCodeEnum.USER_VERIFY_CODE_ERROR);
        }

        // 从 Redis 中获取对应手机号，对应场景的验证码
        String redisKey = VERIFY_CODE_KEY_PREFIX + purpose + ":" + mobile;

        // 执行 Lua 脚本：原子性地比对验证码并删除（匹配返回 1；不匹配或 Key 不存在返回 0）
        Long result = stringRedisTemplate.execute(checkAndDeleteVerifyCodeScript,
                Collections.singletonList(redisKey), verifyCode);

        // 验证码错误或已过期
        if (!Long.valueOf(1L).equals(result)) {
            log.warn("验证码校验失败, mobile={}, purpose={}, result={}", mobile, purpose, result);
            throw new BizException(ResultCodeEnum.USER_VERIFY_CODE_ERROR);
        }

    }

    @Override
    public void sendVerifyCode(SendVerifyCodeReqDTO dto) {
        String mobile = dto.getMobile();
        String captchaId = dto.getCaptchaId();

        // 1. 行为验证码二次校验（放在限流之前：没过滑块的请求不该消耗每日额度）
        //    启动期已断言 imageCaptchaApplication 一定是 SecondaryVerificationApplication 实例
        boolean verified = ((SecondaryVerificationApplication) imageCaptchaApplication)
                .secondaryVerification(captchaId);
        if (!verified) {
            log.warn("行为验证码二次校验未通过, mobile={}, captchaId={}", mobile, captchaId);
            throw new BizException(ResultCodeEnum.CAPTCHA_VERIFICATION_FAILED);
        }

        // 2. 一次性标记：同一个 captchaId 只能兑换一次短信，防重放
        //    （不依赖 tianai 内部是否删除，自己兜底）
        String captchaUsedKey = CAPTCHA_USED_KEY_PREFIX + captchaId;
        Boolean firstUse = stringRedisTemplate.opsForValue()
                .setIfAbsent(captchaUsedKey, "1", VERIFY_CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(firstUse)) {
            log.warn("行为验证码 ID 被重复使用, mobile={}, captchaId={}", mobile, captchaId);
            throw new BizException(ResultCodeEnum.CAPTCHA_VERIFICATION_FAILED);
        }

        // 3. 校验验证码类型
        VerifyCodeTypeEnum typeEnum = VerifyCodeTypeEnum.of(dto.getType());
        if (typeEnum == null) {
            throw new BizException(ResultCodeEnum.VERIFY_CODE_TYPE_ERROR);
        }
        String purpose = typeEnum.getPurpose();

        // 4. 每日发送次数限制：同一手机号、同一场景，每天最多 10 条（Lua 原子：先判后增）
        String dailyLimitKey = VERIFY_CODE_DAILY_LIMIT_KEY_PREFIX + purpose
                + ":" + mobile + ":" + LocalDate.now();
        checkDailySendLimit(dailyLimitKey);

        // 5. 频控：同一手机号 60 秒内只能发一次
        String limitKey = VERIFY_CODE_LIMIT_KEY_PREFIX + purpose + ":" + mobile;
        Boolean firstSend = stringRedisTemplate.opsForValue()
                .setIfAbsent(limitKey, "1", VERIFY_CODE_LIMIT_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(firstSend)) {
            throw new BizException(ResultCodeEnum.VERIFY_CODE_SEND_TOO_FREQUENT);
        }

        // 6. 生成 6 位随机数字验证码
        String verifyCode = RandomUtil.randomNumbers(VERIFY_CODE_LENGTH);

        // 7. 写入 Redis，5 分钟有效
        String redisKey = VERIFY_CODE_KEY_PREFIX + purpose + ":" + mobile;
        stringRedisTemplate.opsForValue()
                .set(redisKey, verifyCode, VERIFY_CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // 8. 异步发送短信验证码
        bizExecutor.execute(() -> sendSms(mobile, verifyCode));

        log.info("验证码已下发, mobile={}, type={}", mobile, typeEnum.getDescription());
    }

    /**
     * 校验并累加每日发送次数（Lua 原子执行：先判后增，超限不累加，并保证 Key 有过期时间）
     *
     * @param dailyLimitKey 每日计数 Key（含日期，如 verify_code_daily:register:13800138001:2026-10-06）
     */
    private void checkDailySendLimit(String dailyLimitKey) {
        // 用同一个 now 计算，避免跨零点时 Key 日期与 TTL 不一致
        LocalDateTime now = LocalDateTime.now();
        long secondsUntilMidnight = Duration.between(
                now, now.toLocalDate().plusDays(1).atStartOfDay()).getSeconds();
        // 兜底：23:59:59.x 时算出来是 0，而 EXPIRE 0 会直接删掉 Key，至少给 1 秒
        secondsUntilMidnight = Math.max(secondsUntilMidnight, 1L);

        // 执行 Lua 脚本：达上限返回 -1；未达上限返回累加后的值
        Long dailyCount = stringRedisTemplate.execute(checkAndIncrementDailyLimitScript,
                Collections.singletonList(dailyLimitKey),
                String.valueOf(VERIFY_CODE_DAILY_LIMIT),
                String.valueOf(secondsUntilMidnight));

        // fail-closed：拿不到计数就拒绝，绝不放行（否则限流会被绕过）
        if (dailyCount == null) {
            log.error("验证码每日计数获取失败, key={}", dailyLimitKey);
            throw new BizException(ResultCodeEnum.SYSTEM_ERROR);
        }

        // 已达每日上限
        if (Long.valueOf(-1L).equals(dailyCount)) {
            log.warn("验证码每日发送次数已达上限, key={}", dailyLimitKey);
            throw new BizException(ResultCodeEnum.VERIFY_CODE_DAILY_LIMIT_EXCEEDED);
        }

        log.info("验证码每日计数 +1, 今日第 {} 次, key={}", dailyCount, dailyLimitKey);
    }

    /**
     * 发送短信验证码（异步执行，由线程池调度）
     *
     * @param mobile     手机号
     * @param verifyCode 验证码
     */

    private void sendSms(String mobile, String verifyCode) {
        try {
            // TODO: 调用短信服务商 API 发送验证码

            // 开发阶段通过日志打印验证码，方便调试
            log.info("==> 验证码发送成功, mobile: {}", mobile);
            // 测试用：生产环境日志级别为 INFO 时不会输出，避免验证码泄露
            log.debug("==> 【调试】验证码：{}", verifyCode);
        } catch (Exception e) {
            log.error("==> 验证码发送失败, mobile: {}", mobile, e);
        }
    }

    /**
     * 检查登录失败次数是否超限
     *
     * @param mobile 手机号
     */
    private void checkLoginFailLimit(String mobile) {
        // 构建 Redis Key
        String failCountKey = LOGIN_FAIL_COUNT_KEY_PREFIX + mobile;

        // 查询 Redis 缓存中的计数
        String failCountStr = stringRedisTemplate.opsForValue().get(failCountKey);

        // key 不存在 = 从未失败过，直接放行
        if (!StringUtils.hasText(failCountStr)) {
            return;
        }

        int failCount = Convert.toInt(failCountStr, 0);
        if (failCount >= LOGIN_FAIL_MAX_COUNT) {
            log.warn("账号已被临时锁定, mobile={}, failCount={}", mobile, failCount);
            throw new BizException(ResultCodeEnum.LOGIN_FAIL_TOO_MANY);
        }
    }

    /**
     * 累加登录失败次数
     *
     * @param mobile 手机号
     */
    private void addLoginFailCount(String mobile) {
        // 构建 Redis Key
        String failCountKey = LOGIN_FAIL_COUNT_KEY_PREFIX + mobile;

        // 执行 Lua 脚本：原子性地检查失败次数并累加（超限返回 -1；未超限返回累加后的值）
        Long result = stringRedisTemplate.execute(checkAndIncrementLoginFailScript,
                Collections.singletonList(failCountKey),
                String.valueOf(LOGIN_FAIL_MAX_COUNT),
                String.valueOf(LOGIN_LOCK_MINUTES * 60));

        // fail-closed：拿不到计数就拒绝，绝不放行（否则限流会被绕过）
        if (result == null) {
            log.error("登录失败次数获取失败, key={}", failCountKey);
            throw new BizException(ResultCodeEnum.SYSTEM_ERROR);
        }
        // 失败次数已达上限，直接拒绝
        if (Long.valueOf(-1L).equals(result)) {
            throw new BizException(ResultCodeEnum.LOGIN_FAIL_TOO_MANY);
        }

        log.info("登录失败次数 +1, mobile={}, failCount={}", mobile, result);
    }

    /**
     * 退出登录
     *
     */
    @Override
    public void logout() {
        // 未登录直接拒绝，不依赖 Sa-Token 拦截器是否已配置
        if (!StpUtil.isLogin()) {
            throw new BizException(ResultCodeEnum.UNAUTHORIZED);
        }

        // 获取当前请求中的Token值
        String tokenValue = StpUtil.getTokenValue();
        // 获取当前登录用户的ID
        Object userId = StpUtil.getLoginId();

        // 调用 SaToken 的退出登录方法
        // 此方法会自动从请求头中获取 Token，然后清楚该 Token 对应的会话信息
        StpUtil.logout();

        log.info("==> 用户退出登录，userId：{}, token：{}", userId, maskToken(tokenValue));
    }

    /**
     * 令牌脱敏，避免完整凭证落盘到日志文件
     */
    private String maskToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "无";
        }
        return token.length() > 8 ? token.substring(0, 8) + "******" : "******";
    }
}


