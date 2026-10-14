package com.practice.flashsale.common.config;

import com.practice.flashsale.common.mq.MessagePublishFailureHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/12 17:05
 * @Version: v1.0.0
 * @Description: RabbitMQ 生产者确认配置
 **/
@Configuration
@Slf4j
public class RabbitMQPublisherConfirmConfig {

    @Resource
    private ObjectProvider<MessagePublishFailureHandler> messagePublishFailureHandlerProvider;

    /**
     * 自定义 RabbitTemplate，注册 Confirm 和 Return 回调。
     *
     * @return
     */
    @Bean
    public RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return rabbitTemplate -> {
            // mandatory=true 时，消息到达交换机但无法路由到队列，会触发 ReturnsCallback。
            rabbitTemplate.setMandatory(true);

            rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
                String correlationId = correlationData == null ? null : correlationData.getId();

                if (ack) {
                    log.info("==> 秒杀下单消息已被 Broker Confirm Ack, correlationId：{}", correlationId);
                    return;
                }

                log.error("==> 秒杀下单消息被 Broker Nack，Broker 未承担消息责任, correlationId：{}, cause：{}", correlationId, cause);

                messagePublishFailureHandlerProvider.orderedStream()
                        .forEach(handler -> handler.handleConfirmNack(correlationId, cause));

                // TODO: 生产环境建议在这里接入告警，并将发送失败的消息落库，后续通过定时任务补偿重发
            });

            rabbitTemplate.setReturnsCallback(returned -> {
                String body = parseBody(returned);

                log.error("==> 秒杀下单消息无法路由到队列, exchange：{}, routingKey：{}, replyCode：{}, replyText：{}, body：{}",
                        returned.getExchange(),
                        returned.getRoutingKey(),
                        returned.getReplyCode(),
                        returned.getReplyText(),
                        body);

                Object messageId = returned.getMessage().getMessageProperties()
                        .getHeaders().get(MessagePublishFailureHandler.MESSAGE_ID_HEADER);

                if (Objects.isNull(messageId)) {
                    log.error("==> 返回消息缺少关联 ID，无法执行业务补偿, body：{}", body);
                    return;
                }

                messagePublishFailureHandlerProvider.orderedStream()
                        .forEach(handler -> handler.handleReturned(String.valueOf(messageId),
                                returned.getExchange(), returned.getRoutingKey()));

                // TODO: 生产环境建议在这里接入告警，并将无法路由的消息落库，后续人工排查绑定关系或补偿重发。
            });
        };
    }

    /**
     * 将被退回的消息体转成可读字符串，方便排查
     *
     * @param returned
     * @return
     */
    private String parseBody(ReturnedMessage returned) {
        if (returned == null || returned.getMessage() == null || returned.getMessage().getBody() == null) {
            return "";
        }
        return new String(returned.getMessage().getBody(), StandardCharsets.UTF_8);
    }
}
