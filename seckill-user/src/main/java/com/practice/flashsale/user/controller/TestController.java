package com.practice.flashsale.user.controller;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.practice.flashsale.common.aspect.ApiOperationLog;
import com.practice.flashsale.common.entity.User;
import com.practice.flashsale.common.enums.ResultCodeEnum;
import com.practice.flashsale.common.exception.BizException;
import com.practice.flashsale.common.mapper.UserMapper;
import com.practice.flashsale.common.utils.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@Slf4j
@Profile("dev")
public class TestController {

    @Resource
    private UserMapper userMapper;

    /**
     * 测试公共返参 - 成功响应
     */
    @GetMapping("/test/result")
    @ApiOperationLog(description = "测试公共返参")
    public Result<String> testResult(@RequestParam String name) {
        return Result.success("Hello," + name + " !");
    }

    /**
     * 测试业务异常捕获
     */
    @GetMapping("/test/bizException")
    @ApiOperationLog(description = "测试业务异常捕获")
    public Result<String> testBizException() {
        // 模拟抛出业务异常
        throw new BizException(ResultCodeEnum.PARAM_NOT_VALID);
    }

    /**
     * 测试系统异常捕获
     */
    @GetMapping("/test/systemException")
    @ApiOperationLog(description = "测试系统异常捕获")
    public Result<String> testSystemException() {
        // 模拟抛出系统异常
        int i = 1 / 0;
        return Result.success("不会走到这里");
    }

    /**
     * 验证 Log4j2 是否使用了 Disruptor 异步日志
     */
    @GetMapping("/test/checkLogger")
    public Result<String> checkLogger() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        String loggerClass = ctx.getRootLogger().getClass().getName();
        return Result.success("Root Logger 实现类: " + loggerClass);
    }

    /**
     * 测试是否真的登录了
     */
    @GetMapping("/test/isLogin")
    public Result<?> isLogin() {
        // 调用 SaToken 提供的方法，判断当前请求是否已登录
        boolean isLogin = StpUtil.isLogin();

        if (isLogin) {
            // 已登录，获取当前登录的用户 ID
            long loginId = StpUtil.getLoginIdAsLong();
            log.info("==> 当前已登录, userId: {}", loginId);
            return Result.success("当前登录用户 ID: " + loginId);
        } else {
            // 未登录
            return Result.success("当前未登录");
        }
    }

    /**
     * 批量登录，将 Token 写入本地 CSV 文件（仅供压测使用，压测完成后，记得删除此方法）
     *
     * @return
     */
    @PostMapping("/test/batchLogin")
    public Result<String> batchLogin(HttpServletResponse httpResponse) throws IOException {
        List<String> tokens = new ArrayList<>();

        // 查询所有测试用户（昵称以 "测试用户" 开头）
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // 等价于 WHERE nickname LIKE '测试用户%'
        wrapper.likeRight(User::getNickname, "测试用户");

        // 2. 执行查询
        List<User> testUsers = userMapper.selectList(wrapper);

        // 逐个执行登录，获取 Token
        for (User userDO : testUsers) {
            // 执行登录，并禁止将 Token 写入响应头
            StpUtil.login(userDO.getId(), new SaLoginModel()
                    .setIsWriteHeader(false));

            // 通过用户 ID 从 Redis 中获取该用户的 Token
            String tokenValue = StpUtil.getTokenValueByLoginId(userDO.getId());
            tokens.add(tokenValue);
        }

        // 将 Token 写入本地 CSV 文件，供 JMeter 使用
        // 文件内容：第一行为表头 token，后续每行一个 Token
        Path path = Paths.get("tokens.csv");
        List<String> lines = Stream.concat(
                Stream.of("token"),   // CSV 表头
                tokens.stream()       // 100 个 Token
        ).collect(Collectors.toList());
        Files.write(path, lines);

        log.info("==> 批量登录完成, 共获取 {} 个 Token, 已写入 {}", tokens.size(), path.toAbsolutePath());

        // 清除因 100 次 login 调用累积的响应头，避免响应头溢出
        httpResponse.reset();

        return Result.success("Token 已写入 " + path.toAbsolutePath() + "，共 " + tokens.size() + " 个");
    }
}
