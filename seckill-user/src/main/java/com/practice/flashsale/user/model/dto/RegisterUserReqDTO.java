package com.practice.flashsale.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegisterUserReqDTO {

    /**
     * 手机号（充当登录账号）
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须在6-32位之间")
    private String password;

    /**
     * 确认密码
     * 前端校验：密码和确认密码必须一致
     * 后端校验：密码和确认密码必须一致
     */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    /**
     * 短信验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String verifyCode;
}
