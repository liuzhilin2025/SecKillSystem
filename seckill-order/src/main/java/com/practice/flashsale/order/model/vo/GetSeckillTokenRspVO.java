package com.practice.flashsale.order.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/16 10:12
 * @Version: v1.0.0
 * @Description: 获取秒杀令牌
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GetSeckillTokenRspVO {

    /**
     * 秒杀令牌
     */
    private String token;

    /**
     * 过期时间（单位：秒）
     */
    private Long expireSeconds;
}
