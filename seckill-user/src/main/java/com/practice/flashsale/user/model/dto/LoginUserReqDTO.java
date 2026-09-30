package com.practice.flashsale.user.model.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginUserReqDTO {

    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    /**
     * 登录类型：1=密码登录，2=验证码登录
     */
    @NotNull(message = "登录类型不能为空")
    @Min(value = 1, message = "登录类型不正确")
    @Max(value = 2, message = "登录类型不正确")
    private Integer type;

    /**
     * 密码（type=1 时必填）
     */
    private String password;

    /**
     * 短信验证码（type=2 时必填）
     */
    private String verifyCode;
}
