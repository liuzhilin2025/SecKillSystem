package com.practice.flashsale.order.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/15 16:50
 * @Version: v1.0.0
 * @Description: 秒杀接口限流注解（配置载体）
 **/
@Target(ElementType.METHOD) // 表示这个注解只能加在方法上
@Retention(RetentionPolicy.RUNTIME) // 表示运行时也能拿到这个注解，AOP 才能读取里面的参数
public @interface SeckillRateLimit {

    /**
     * 用户维度最大请求次数
     */
    long userMaxCount() default 5;

    /**
     * 用户维度限流窗口时间，单位：秒
     */
    long userWindowSeconds() default 10;

    /**
     * IP 维度最大请求次数
     */
    long ipMaxCount() default 50;

    /**
     * IP 维度限流窗口时间，单位：秒
     */
    long ipWindowSeconds() default 10;
}
