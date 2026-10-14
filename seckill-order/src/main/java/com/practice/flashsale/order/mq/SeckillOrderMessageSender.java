package com.practice.flashsale.order.mq;

import com.practice.flashsale.common.config.RabbitMQConfig;
import com.practice.flashsale.common.mq.MessagePublishFailureHandler;
import com.practice.flashsale.order.model.dto.SeckillOrderMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/12 17:35
 * @Version: v1.0.0
 * @Description: 秒杀订单消息发送器
 **/
@Component
@Slf4j
public class SeckillOrderMessageSender {

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送秒杀下单信息
     *
     * @param message 秒杀下单消息体
     */
    public boolean send(SeckillOrderMqDTO message) {
        // 使用订单号作为关联 ID，方便 ConfirmCallback 中定位是哪一笔订单消息发送失败。
        CorrelationData correlationData = new CorrelationData(message.getOrderNo());

        try {
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
        } catch (AmqpException e) {
            log.error("==> 秒杀下单消息发送时发生同步异常，投递结果未知, orderNo：{}", message.getOrderNo(), e);
            return false;
        }

        log.info("==> 秒杀下单消息发送请求已提交, orderNo：{}, userId：{}, activityId：{}, goodsId：{}",
                message.getOrderNo(), message.getUserId(), message.getActivityId(), message.getGoodsId());

        return true;
    }
}
