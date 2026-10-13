package com.practice.flashsale.order.service.impl;

import com.practice.flashsale.order.model.vo.FindSeckillOrderResultRspVO;
import com.practice.flashsale.order.service.SeckillOrderResultNotifyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/13 11:09
 * @Version: v1.0.0
 * @Description: 秒杀订单结果 SSE 通知
 **/
@Service
@Slf4j
public class SeckillOrderResultNotifyServiceImpl implements SeckillOrderResultNotifyService {

    /**
     * SSE 连接超时时间：60 秒
     * 连接最多挂 60 秒，避免用户一直不关页面导致连接长期占用
     */
    private static final long SSE_TIMEOUT_MILLTS = 60000L;

    /**
     * 存储用户维度的 SSE 连接
     * SSE 订阅和消费者推送不一定在同一个线程里，必须使用线程安全的 Map
     */
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    /**
     * 订阅秒杀订单处理结果
     *
     * @param userId
     * @param orderNo
     * @return
     */
    @Override
    public SseEmitter subscribe(Long userId, String orderNo) {
        // 连接唯一标识
        String emitterKey = userId + ":" + orderNo;

        // 创建 SSE 连接
        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT_MILLTS);

        // 存储 SSE 连接
        emitterMap.put(emitterKey, sseEmitter);

        // 浏览器断开、连接超时或推送异常时，要清理一下连接，避免内存泄漏
        sseEmitter.onCompletion(() -> emitterMap.remove(emitterKey));
        sseEmitter.onTimeout(() -> emitterMap.remove(emitterKey));
        sseEmitter.onError(e -> emitterMap.remove(emitterKey));

        log.info("==> 秒杀订单结果 SSE 订阅成功，userId：{}, orderNo：{}", userId, orderNo);
        return sseEmitter;
    }

    /**
     * 推送秒杀订单处理结果
     *
     * @param userId
     * @param result
     */
    @Override
    public void notifyOrderResult(Long userId, FindSeckillOrderResultRspVO result) {
        // 连接唯一标识
        String emitterKey = userId + ":" + result.getOrderNo();

        // 取出当前用户这笔订单对应的 SSE 连接，同时把它从 Map 里删除掉
        SseEmitter sseEmitter = emitterMap.remove(emitterKey);

        if (Objects.isNull(sseEmitter)) {
            log.info("==> 秒杀订单结果 SSE 连接不存在, 跳过推送, userId：{}, orderNo：{}", userId, result.getOrderNo());
            return;
        }

        try {
            // 推送结果
            sseEmitter.send(SseEmitter.event()
                    .name("seckill-order-result")
                    .data(result));

            // 关闭 SSE 连接
            sseEmitter.complete();

            log.info("==> 秒杀订单结果 SSE 推送成功, userId：{}, orderNo：{}, status：{}", userId, result.getOrderNo(), result.getStatus());
        } catch (IOException e) {
            // 如果 send() 抛出 IOException，大概率是浏览器已经断开连接。
            // 这里只记录日志，不再把它当成订单处理失败。订单状态已经写入 Redis / MySQL，前端后续可以通过查询接口兜底。
            log.error("==> 秒杀订单结果 SSE 推送失败, userId：{}, orderNo：{}", userId, result.getOrderNo(), e);
        }
    }
}
