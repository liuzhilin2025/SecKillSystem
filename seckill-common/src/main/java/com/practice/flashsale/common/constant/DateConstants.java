package com.practice.flashsale.common.constant;

import java.time.format.DateTimeFormatter;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 9:51
 * @Version: v1.0.0
 * @Description: 日期常量
 **/
public class DateConstants {

    /**
     * 年-月-日 时:分:秒
     */
    public static final DateTimeFormatter DATE_FORMAT_Y_M_D_H_M_S = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 年-月-日
     */
    public static final DateTimeFormatter DATE_FORMAT_Y_M_D = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 时:分:秒
     */
    public static final DateTimeFormatter DATE_FORMAT_H_M_S = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 年-月
     */
    public static final DateTimeFormatter DATE_FORMAT_Y_M = DateTimeFormatter.ofPattern("yyyy-MM");
}
