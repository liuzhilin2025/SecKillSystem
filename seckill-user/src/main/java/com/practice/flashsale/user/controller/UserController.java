package com.practice.flashsale.user.controller;

import com.practice.flashsale.common.aspect.ApiOperationLog;
import com.practice.flashsale.common.utils.Result;
import com.practice.flashsale.user.model.dto.LoginUserReqDTO;
import com.practice.flashsale.user.model.dto.RegisterUserReqDTO;
import com.practice.flashsale.user.model.dto.SendVerifyCodeReqDTO;
import com.practice.flashsale.user.model.vo.LoginUserRspVO;
import com.practice.flashsale.user.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    @ApiOperationLog(description = "用户注册")
    public Result<Long> register(@Valid @RequestBody RegisterUserReqDTO dto) {
        return Result.success(userService.registerUser(dto));
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @ApiOperationLog(description = "用户登录")
    public Result<LoginUserRspVO> login(@Valid @RequestBody LoginUserReqDTO dto) {
        return Result.success(userService.loginUser(dto));
    }

    /**
     * 发送验证码
     */
    @PostMapping("/code/send")
    @ApiOperationLog(description = "发送验证码")
    public Result<Void> sendVerifyCode(@Valid @RequestBody SendVerifyCodeReqDTO dto) {
        userService.sendVerifyCode(dto);
        return Result.success(null);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    @ApiOperationLog(description = "退出登录")
    public Result<Void> logout() {
        userService.logout();
        return Result.success(null);
    }
}
