package com.practice.flashsale.order.controller;

import com.practice.flashsale.common.config.RabbitMQConfig;
import com.practice.flashsale.common.utils.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/test/mq")
@Slf4j
public class TestMqController {

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送测试消息到 RabbitMQ
     *
     * @return
     */
    @GetMapping("/send")
    public Result<String> sendTestMessage() {
        String message = "Hello RabbitMQ! 发送时间：" + LocalDateTime.now();

        // 通过 RabbitTemplate 发送消息：指定交换机、路由键、消息内容
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TEST_EXCHANGE,
                RabbitMQConfig.TEST_ROUTING_KEY,
                message
        );

        log.info("==> 测试消息发送成功：{}", message);
        return Result.success("消息发送成功");
    }
}
