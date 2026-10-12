package com.practice.flashsale.order.consumer;

import com.practice.flashsale.common.config.RabbitMQConfig;
import com.practice.flashsale.order.model.dto.SeckillOrderMqDTO;
import com.practice.flashsale.order.service.OrderService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/12 13:55
 * @Version: v1.0.0
 * @Description: 秒杀订单消费者，异步扣库存、建订单
 **/
@Component
@Slf4j
public class SeckillOrderConsumer {

    @Resource
    private OrderService orderService;

    /**
     * 监听秒杀订单队列，收到消息后，调用业务层扣减库存、创建订单
     *
     * @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
     * 把这个方法注册成秒杀订单队列的监听器，应用一启动就会自动连上 RabbitMQ 持续监听这个队列，有消息进来就触发 consume()；
     *
     * 方法参数直接写成 SeckillOrderMqDTO message。Spring AMQP 会用我们配的 Jackson2JsonMessageConverter
     * 把收到的 JSON 消息自动反序列化成这个对象，不用自己手动解析；
     *
     * @param message
     */
    // concurrency = "5-10"：5~10 个消费线程并发消费，提升消费速度（替代默认的单线程串行）
    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE, concurrency = "5-10")
    public void consume(SeckillOrderMqDTO seckillOrderMqDTO, Channel channel, Message message) throws IOException {
        // deliveryTag 是 RabbitMQ 给当前投递消息分配的唯一编号，手动 ACK/NACK 时必须带上它
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        log.info("## 收到秒杀订单消息：{}", seckillOrderMqDTO);

        try {
            orderService.createSeckillOrder(seckillOrderMqDTO);
        } catch (Exception e) {
            // 只有 “业务未成功”才 nack 进死信
            log.error("## 秒杀订单消息处理出现系统异常，进入死信队列，orderNo：{}, deliveryTag：{}",
                    seckillOrderMqDTO.getOrderNo(), deliveryTag, e);

            // requeue=false：不重新入主队列，交给死信队列兜底，避免异常消息无限重试
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        // 手动 ACK，RabbitMQ 才会将该消息从队列中删除
        // 确认当前这一条消息已经得到明确处理结果
        channel.basicAck(deliveryTag, false);
        log.info("## 秒杀订单消息已 ACK, orderNo：{}, deliveryTag：{}", seckillOrderMqDTO.getOrderNo(), deliveryTag);
    }
}
