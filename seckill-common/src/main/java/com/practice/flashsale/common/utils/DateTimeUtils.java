package com.practice.flashsale.common.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/13 17:31
 * @Version: v1.0.0
 * @Description: 日期时间工具类
 **/
public class DateTimeUtils {

    /**
     * 时区
     */
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private DateTimeUtils() {}

    /**
     * 将业务本地时间转换为 Unix 毫秒时间戳
     */
    public static long toEpochMilli(LocalDateTime localDateTime) {
        return localDateTime.atZone(BUSINESS_ZONE_ID)
                .toInstant()
                .toEpochMilli();
    }
}
