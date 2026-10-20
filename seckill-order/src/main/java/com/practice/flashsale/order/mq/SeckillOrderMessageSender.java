package com.practice.flashsale.order.mq;

import com.practice.flashsale.common.config.RabbitMQConfig;
import com.practice.flashsale.common.entity.SeckillOrderSendCompensation;
import com.practice.flashsale.common.mapper.SeckillOrderSendCompensationMapper;
import com.practice.flashsale.common.mq.MessagePublishFailureHandler;
import com.practice.flashsale.common.utils.JsonUtils;
import com.practice.flashsale.order.enums.SendCompensationStatusEnum;
import com.practice.flashsale.order.model.dto.SeckillOrderMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/12 17:35
 * @Version: v1.0.0
 * @Description: 秒杀订单消息发送器
 **/
@Component
@Slf4j
public class SeckillOrderMessageSender {

    /**
     * 补偿重发的初始间隔（秒）：首次登记后 10 秒，即可被定时任务捞起重发
     */
    private static final int INITIAL_RETRY_DELAY_SECONDS = 10;

    /**
     * 补偿重发间隔的上限（秒）：指数退避封顶 5 分钟，避免间隔被无限拉长
     */
    private static final int MAX_RETRY_DELAY_SECONDS = 300;

    /**
     * 补偿重发最大次数：超过后不再自动重发，转人工处理
     */
    private static final int MAX_RETRY_COUNT = 5;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private SeckillOrderSendCompensationMapper sendCompensationMapper;

    /**
     * 发送秒杀下单信息
     *
     * @param message 秒杀下单消息体
     */
    @Retryable(
            retryFor = {AmqpException.class}, // 仅对 AmqpException 同步异常重试
            maxAttempts = 3,                  // 最多尝试 3 次
            backoff = @Backoff(delay = 1000, multiplier = 2) // 重试间隔 1s、2s（指数退避）
    )
    public boolean send(SeckillOrderMqDTO message) {
        // 使用订单号作为关联 ID，方便 ConfirmCallback 中定位是哪一笔订单消息发送失败。
        CorrelationData correlationData = new CorrelationData(message.getOrderNo());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ROUTING_KEY,
                message,
                amqpMessage -> {
                    // Return 回调拿不到 CorrelationData，这里将订单号写入到消息头中
                    amqpMessage.getMessageProperties().setHeader(
                            MessagePublishFailureHandler.MESSAGE_ID_HEADER, message.getOrderNo());
                    return amqpMessage;
                },
                correlationData
        );

        log.info("==> 秒杀下单消息发送请求已提交, orderNo：{}, userId：{}, activityId：{}, goodsId：{}",
                message.getOrderNo(), message.getUserId(), message.getActivityId(), message.getGoodsId());
        return true;
    }

    /**
     * 重试耗尽后的兜底回调：将发送失败的消息，登记到补偿表
     *
     * @param e
     * @param message
     * @return
     */
    @Recover
    public boolean recover(AmqpException e, SeckillOrderMqDTO message) {
        String orderNo = message.getOrderNo();
        log.error("==> 秒杀下单消息重试耗尽, 进入发送失败补偿登记, orderNo: {}", orderNo, e);

        try {
            // 先按订单号查补偿表，区分是 “首次登记”，还是 “补偿重发又失败”
            SeckillOrderSendCompensation existCompensation = sendCompensationMapper.selectByOrderNo(orderNo);

            // 若表中无记录，说明是首次登记
            if (Objects.isNull(existCompensation)) {
                // 将完整消息体序列化为 JSON 落库，状态设置为 “待重试”，10 秒后允许被补偿任务捞起
                SeckillOrderSendCompensation compensation = SeckillOrderSendCompensation.builder()
                        .orderNo(orderNo)
                        .userId(message.getUserId())
                        .activityId(message.getActivityId())
                        .goodsId(message.getGoodsId())
                        .seckillGoodsId(message.getSeckillGoodsId())
                        .messageBody(JsonUtils.toJsonString(message))
                        .status(SendCompensationStatusEnum.RETRY_PENDING.getStatus())
                        .retryCount(0)
                        .failReason(e.getMessage())
                        .nextRetryTime(LocalDateTime.now().plusSeconds(10))
                        .build();

                sendCompensationMapper.insert(compensation);

                log.info("==> 发送失败补偿登记成功, 等待补偿重发, orderNo: {}", orderNo);
            } else if (existCompensation.getRetryCount() + 1 >= MAX_RETRY_COUNT) {
                // 补偿重发又失败，并且达到了重试上限：不再自动重发，置为 "待人工处理"，等待人工介入或告警
                sendCompensationMapper.updateStatusByOrderNo(orderNo, SendCompensationStatusEnum.MANUAL_PENDING.getStatus());

                log.error("==> 发送失败补偿重发已达上限 {} 次, 转人工处理, orderNo: {}", MAX_RETRY_COUNT, orderNo);
            } else {
                // 补偿重发又失败：重试次数 +1，退避间隔随次数翻倍（10s -> 20s -> 40s -> ... 封顶 5 分钟）
                int nextRetryCount = existCompensation.getRetryCount() + 1;

                long delaySeconds = Math.min((long) INITIAL_RETRY_DELAY_SECONDS << nextRetryCount, MAX_RETRY_DELAY_SECONDS);

                sendCompensationMapper.updateRetryPlan(orderNo, nextRetryCount,
                        LocalDateTime.now().plusSeconds(delaySeconds), e.getMessage());

                log.warn("==> 发送失败补偿重发失败, 第 {} 次重试将于 {} 秒后再执行, orderNo: {}",
                        nextRetryCount, delaySeconds, orderNo);
            }
        } catch (Exception ex) {
            log.error("==> 发送失败补偿登记异常, orderNo：{}", orderNo, ex);
        }

        // 告知调用方本轮发送失败，但不再向上抛异常
        return false;
    }
}
