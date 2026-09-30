package com.practice.flashsale.user.service;

import com.practice.flashsale.common.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.practice.flashsale.user.model.dto.LoginUserReqDTO;
import com.practice.flashsale.user.model.dto.RegisterUserReqDTO;
import com.practice.flashsale.user.model.dto.SendVerifyCodeReqDTO;
import com.practice.flashsale.user.model.vo.LoginUserRspVO;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author 沃淇淋
 * @since 2026-09-28
 */
public interface UserService extends IService<User> {
    /**
     * 用户注册
     *
     * @param dto 注册入参
     * @return 新用户 ID
     */
    Long registerUser(RegisterUserReqDTO dto);

    /**
     * 用户登录
     *
     * @param dto 登录入参
     * @return 登录结果
     */
    LoginUserRspVO loginUser(LoginUserReqDTO dto);

    /**
     * 发送验证码
     */
    void sendVerifyCode(SendVerifyCodeReqDTO dto);
}
