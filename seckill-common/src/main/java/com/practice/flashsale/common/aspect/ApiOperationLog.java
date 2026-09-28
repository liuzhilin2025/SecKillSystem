package com.practice.flashsale.common.aspect;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD}) // 指定注解只能加在方法上，不能加在类、字段等上面
@Documented // 注解会被包含在JavaDoc中
public @interface ApiOperationLog {
    /**
     * API 功能描述
     *
     * @return
     */
    String description() default "";
}
