package com.practice.flashsale.user.service.impl;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
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
import java.time.LocalTime;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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
    /** 账号临时锁定时间（分钟）*/
    private static final long LOGIN_LOCK_MINUTES = 30L;



    // 依赖
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    /** 验证码校验 Lua 脚本 */
    private final DefaultRedisScript<Long> checkAndDeleteVerifyCodeScript;
    /** 登录失败次数 Lua 脚本 */
    private final DefaultRedisScript<Long> checkAndIncrementLoginFailScript;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

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
        } else if (LoginTypeEnum.VERIFY_CODE.getCode().equals(dto.getType())){
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
     * @param mobile 手机号（用于构建 Redis Key）
     */
    private void checkPassword(String rawPassword, String encodedPassword, String mobile) {
        // 1. 判空：passwordEncoder.matches(null, ...) 会抛 IllegalArgumentException
        if (!StringUtils.hasText(rawPassword)) {
            // 登录失败次数 +1
            addLoginFailCount(mobile);

            throw new BizException(ResultCodeEnum.USER_PASSWORD_ERROR);
        }

        // 2. BCrypt 校验：必须用 matches()，绝不能用 equals()
        //    （BCrypt 每次加密结果都不同，因为自带随机盐）
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            // 登录失败次数 +1
            addLoginFailCount(mobile);

            throw new BizException(ResultCodeEnum.USER_PASSWORD_ERROR);
        }

        // 密码校验成功
        String failCountKey = LOGIN_FAIL_COUNT_KEY_PREFIX + mobile;
        stringRedisTemplate.delete(failCountKey);
    }

    /**
     * 校验短信验证码
     *
     * @param mobile 手机号（需要用它来拼 Redis Key）
     * @param purpose 场景（登录/注册）
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

        // 1. 校验验证码类型
        VerifyCodeTypeEnum typeEnum = VerifyCodeTypeEnum.of(dto.getType());
        if (typeEnum == null) {
            throw new BizException(ResultCodeEnum.VERIFY_CODE_TYPE_ERROR);
        }
        String purpose = typeEnum.getPurpose();

        // 2. 频控：同一手机号 60 秒 内只能发一次
        String limitKey = VERIFY_CODE_LIMIT_KEY_PREFIX + purpose + ":" + mobile;
        Boolean firstSend = stringRedisTemplate.opsForValue()
                .setIfAbsent(limitKey, "1", VERIFY_CODE_LIMIT_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(firstSend)) {
            throw new BizException(ResultCodeEnum.VERIFY_CODE_SEND_TOO_FREQUENT);
        }

        // 每日发送次数限制：同一手机号、同一场景，每天最多发送 10 条
        String dailyLimitKey = VERIFY_CODE_DAILY_LIMIT_KEY_PREFIX + purpose
                + ":" + mobile + ":" + LocalDate.now();
        // 发送次数 +1
        Long dailyCount = stringRedisTemplate.opsForValue().increment(dailyLimitKey);

        // fail-closed：拿不到计数就拒绝，绝不放行（否则限流会被绕过）
        if (dailyCount == null) {
            log.error("验证码每日计数获取失败, key={}", dailyLimitKey);
            throw new BizException(ResultCodeEnum.SYSTEM_ERROR);
        }

        // 首次设置缓存时，计算到当天结束的剩余秒数，作为 Key 的 TTL 过期时间
        if (dailyCount == 1L) {
            // 计算从当前时间，到第二天凌晨零点之间还剩下多少秒
            long secondsUntilMidnight = Duration.between(
                    LocalDateTime.now(),
                    LocalDate.now().plusDays(1).atStartOfDay()
            ).getSeconds();

            Boolean expired = stringRedisTemplate.expire(
                    dailyLimitKey, secondsUntilMidnight, TimeUnit.SECONDS);
            if (!expired) {
                // Key 里带了日期，TTL 没设上也不影响第二天的计数，只是会残留一个垃圾 Key
                log.warn("设置每日上限 Key 的过期时间失败, key={}", dailyLimitKey);
            }
        }

        // 如果已经超过 10 条，抛出业务异常
        if (dailyCount > VERIFY_CODE_DAILY_LIMIT) {
            throw new BizException(ResultCodeEnum.VERIFY_CODE_DAILY_LIMIT_EXCEEDED);
        }

        // 3. 生成 6 位随机数字验证码
        String verifyCode = RandomUtil.randomNumbers(VERIFY_CODE_LENGTH);

        // 4. 通过 Pipeline 通道，批量写入 Redis （频率限制 Key + 验证码），减少网络往返，降低部分失败的风险
        String redisKey = VERIFY_CODE_KEY_PREFIX + typeEnum.getPurpose() + ":" + mobile;
        stringRedisTemplate.opsForValue()
                .set(redisKey, verifyCode, VERIFY_CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // 异步发送短信验证码
        bizExecutor.execute(() -> sendSms(mobile, verifyCode));

        log.info("验证码已下发, mobile={}, type={}", mobile, typeEnum.getDescription());
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
        Long result = redisTemplate.execute(checkAndIncrementLoginFailScript,
                Collections.singletonList(failCountKey),
                String.valueOf(LOGIN_FAIL_MAX_COUNT),
                String.valueOf(LOGIN_LOCK_MINUTES * 60));

        // 失败次数已达上限，直接拒绝
        if (Long.valueOf(-1L).equals(result)) {
            throw new BizException(ResultCodeEnum.LOGIN_FAIL_TOO_MANY);
        }
    }
}


