package com.practice.flashsale.order.consumer;

import com.practice.flashsale.common.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/12 09:39
 * @Version: v1.0.0
 * @Description: 测试消费者
 **/
@Component
@Slf4j
public class TestMqConsumer {

    /**
     * 监听测试队列，收到消息后打印日志
     *
     * @param message
     */
    @RabbitListener(queues = RabbitMQConfig.TEST_QUEUE)
    public void consume(String message) {
        log.info("## 收到测试消息：{}", message);
    }
}
