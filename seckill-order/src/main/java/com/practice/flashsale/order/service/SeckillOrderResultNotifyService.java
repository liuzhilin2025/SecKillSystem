package com.practice.flashsale.order.service;

import com.practice.flashsale.order.model.vo.FindSeckillOrderResultRspVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/13 11:04
 * @Version: v1.0.0
 * @Description: 秒杀订单结果通知
 **/
public interface SeckillOrderResultNotifyService {

    /**
     * 订阅秒杀订单处理结果
     *
     * SseEmitter 是 Spring MVC 提供的一个 SSE 推送对象。可以把它理解成：服务端握在手里的一根推送管道。
     * @param userId
     * @param orderNo
     * @return
     */
    SseEmitter subscribe(Long userId, String orderNo);

    /**
     * 推送秒杀订单处理结果
     *
     * @param userId
     * @param result
     */
    void notifyOrderResult(Long userId, FindSeckillOrderResultRspVO result);
}
