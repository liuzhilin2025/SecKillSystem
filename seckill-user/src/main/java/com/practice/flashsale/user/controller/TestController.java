package com.practice.flashsale.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.practice.flashsale.common.aspect.ApiOperationLog;
import com.practice.flashsale.common.enums.ResultCodeEnum;
import com.practice.flashsale.common.exception.BizException;
import com.practice.flashsale.common.utils.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class TestController {
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
}
