package com.practice.flashsale.user.controller;

import com.practice.flashsale.common.aspect.ApiOperationLog;
import com.practice.flashsale.common.enums.ResultCodeEnum;
import com.practice.flashsale.common.exception.BizException;
import com.practice.flashsale.common.utils.Result;
import lombok.extern.slf4j.Slf4j;
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
}
