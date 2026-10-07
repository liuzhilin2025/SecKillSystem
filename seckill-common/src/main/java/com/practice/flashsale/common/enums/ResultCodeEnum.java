package com.practice.flashsale.common.enums;

import com.practice.flashsale.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCodeEnum implements BaseExceptionInterface {

    // 通用状态异常码
    SYSTEM_ERROR("10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("10001", "参数错误"),

    // 业务异常状态码


    // 用户模块异常状态码
    USER_MOBILE_EXISTS("20001", "该手机号已注册"),
    USER_VERIFY_CODE_ERROR("20002", "验证码错误"),
    USER_PASSWORD_ERROR("20003", "密码错误"),
    USER_CONFIRM_PASSWORD_ERROR("20004", "两次输入的密码不一致"),
    USER_MOBILE_NOT_REGISTERED("20005", "该手机号未注册"),
    USER_STATUS_DISABLED("20006", "账号已被禁用，请联系管理员"),
    VERIFY_CODE_TYPE_ERROR("20007", "验证码类型错误"),
    VERIFY_CODE_SEND_TOO_FREQUENT("20008", "验证码发送过于频繁，请稍后再试"),
    VERIFY_CODE_DAILY_LIMIT_EXCEEDED("20009", "验证码每日发送次数已达上限，请明天再试"),
    LOGIN_FAIL_TOO_MANY("20010", "密码错误次数过多，请 30 分钟后再试"),
    UNAUTHORIZED("20011", "未登录，请先登录"),
    USER_LOGIN_CREDENTIAL_ERROR("20012", "手机号或密码错误"),
    CAPTCHA_VERIFICATION_FAILED("20013", "行为验证码校验失败，请重新验证"),
    ;

    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;
}
