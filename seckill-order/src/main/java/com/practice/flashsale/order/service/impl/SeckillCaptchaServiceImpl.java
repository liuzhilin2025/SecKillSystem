package com.practice.flashsale.order.service.impl;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.spring.plugins.secondary.SecondaryVerificationApplication;
import cn.hutool.core.util.StrUtil;
import com.practice.flashsale.common.enums.ResultCodeEnum;
import com.practice.flashsale.common.exception.BizException;
import com.practice.flashsale.order.service.SeckillCaptchaService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/19 10:03
 * @Version: v1.0.0
 * @Description: TODO
 **/
@Service
@Slf4j
public class SeckillCaptchaServiceImpl implements SeckillCaptchaService {

    @Resource
    private ImageCaptchaApplication imageCaptchaApplication;

    /**
     * 校验秒杀行为验证码
     *
     * @param captchaId 行为验证码二次校验 ID
     */
    @Override
    public void verify(String captchaId) {
        // 行为验证码二次校验 ID 不能为空
        if (StrUtil.isBlank(captchaId)) {
            throw new BizException(ResultCodeEnum.PARAM_NOT_VALID);
        }

        // 二次校验
        boolean verified = false;
        if (imageCaptchaApplication instanceof SecondaryVerificationApplication secondaryVerificationApplication) {
            verified = secondaryVerificationApplication.secondaryVerification(captchaId);
        }

        // 如果校验失败，说明用户没有完成验证码，或者 captchaId 已过期 / 已使用
        if (!verified) {
            log.warn("==> 秒杀行为验证码校验失败, captchaId：{}", captchaId);
            throw new BizException(ResultCodeEnum.CAPTCHA_VERIFICATION_FAILED);
        }
    }
}
