package com.practice.flashsale.common.utils;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/15 15:18
 * @Version: v1.0.0
 * @Description: IP 工具类
 **/
public class IpUtils {

    /**
     * 代理服务器无法识别真实 IP 时，可能会把请求头值设置为 unknown
     */
    private static final String UNKNOWN = "unknown";

    /**
     * 获取客户端 IP
     *
     * @param request HTTP
     * @return 客户端 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        // 真实生产环境中，请求通常会经过 Nginx、网关、负载均衡等代理层，
        // 这些代理层会把原始客户端 IP 放到下面这些请求头中
        String ip = getFirstNotBlankHeader(request,
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP");

        // 如果没有经过代理，或者代理没有传递这些请求头，
        // 则回退到 request.getRemoteAddr() 方法来获取
        if (StrUtil.isBlank(ip)) {
            ip = request.getRemoteAddr();
        }

        // X-Forwarded-For 可能是多个 IP，用逗号分隔，第一个通常是原始客户端 IP
        if (StrUtil.isNotBlank(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 按顺序读取请求头，返回第一个非空、且不是 unknow 的值
     */
    private static String getFirstNotBlankHeader(HttpServletRequest request, String... headerNames) {
        for (String headName : headerNames) {
            String value = request.getHeader(headName);
            if (StrUtil.isNotBlank(value) && !UNKNOWN.equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }
}
