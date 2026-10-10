package com.practice.flashsale.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 15:46
 * @Version: v1.0.0
 * @Description: SaToken 配置类
 **/
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Saoken 拦截器，打开注解式鉴权功能
        registry.addInterceptor(new SaInterceptor(handle -> {
            // 秒杀下单接口，需要登录
            SaRouter.match("/seckill/order", r -> StpUtil.checkLogin());
            // 登出接口，需要登陆
            SaRouter.match("/user/logout", r -> StpUtil.checkLogin());
            // 管理端接口，需要登录
            SaRouter.match("/admin/**", r -> StpUtil.checkLogin());
        })).addPathPatterns("/**");
    }
}
