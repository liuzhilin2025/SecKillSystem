package com.practice.flashsale.order.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.practice.flashsale.common.config.RabbitMQConfig;
import com.practice.flashsale.common.entity.Goods;
import com.practice.flashsale.common.entity.SeckillActivity;
import com.practice.flashsale.common.entity.SeckillGoods;
import com.practice.flashsale.common.entity.SeckillOrder;
import com.practice.flashsale.common.enums.ResultCodeEnum;
import com.practice.flashsale.common.exception.BizException;
import com.practice.flashsale.common.mapper.GoodsMapper;
import com.practice.flashsale.common.mapper.SeckillActivityMapper;
import com.practice.flashsale.common.mapper.SeckillGoodsMapper;
import com.practice.flashsale.common.mapper.SeckillOrderMapper;
import com.practice.flashsale.order.enums.OrderStatusEnum;
import com.practice.flashsale.order.model.dto.DoSeckillReqDTO;
import com.practice.flashsale.order.model.dto.SeckillOrderMqDTO;
import com.practice.flashsale.order.model.vo.DoSeckillRspVO;
import com.practice.flashsale.order.mq.SeckillOrderMessageSender;
import com.practice.flashsale.order.service.OrderService;
import com.practice.flashsale.order.utils.OrderLockUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 14:57
 * @Version: v1.0.0
 * @Description: 订单模块业务实现
 **/
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Resource
    private SeckillActivityMapper seckillActivityMapper;

    @Resource
    private SeckillGoodsMapper seckillGoodsMapper;

    @Resource
    private GoodsMapper goodsMapper;

    @Resource
    private SeckillOrderMapper seckillOrderMapper;

    @Resource
    private OrderLockUtils orderLockUtils;

    @Resource
    private SeckillOrderMessageSender seckillOrderMessageSender;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 秒杀下单
     *
     * @param dto
     * @return
     */
    @Override
    public DoSeckillRspVO doSeckill(DoSeckillReqDTO dto) {
        // 活动 ID
        Long activityId = dto.getActivityId();
        // 商品 ID
        Long goodsId = dto.getGoodsId();

        // 1. 获取当前登录用户 ID
        long userId = StpUtil.getLoginIdAsLong();
        log.info("==> 当前登录用户 ID：{}", userId);

        // 应用层锁：防止同一用户并发重复下单
        // 构建锁 Key "userId:activityId:goodsId"
        String lockKey = userId + ":" + activityId + ":" + goodsId;

        // 尝试获取锁，获取失败，则说明该用户对该商品已经有请求在处理中
        if (!orderLockUtils.tryLock(lockKey)) {
            log.warn("==> 应用层锁拦截重复下单, userId: {}, activityId: {}, goodsId: {}", userId, activityId, goodsId);
            throw new BizException(ResultCodeEnum.SECKILL_ORDER_PROCESSING);
        }

        try {
            return processSeckill(activityId, goodsId, userId);
        } finally {
            // 无论成功还是异常，都要释放锁
            orderLockUtils.unlock(lockKey);
        }
    }

    /**
     * 秒杀下单逻辑
     */
    private DoSeckillRspVO processSeckill(Long activityId, Long goodsId, long userId) {
        // 2. 校验活动是否存在
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
        }

        // 3. 校验秒杀活动时间
        LocalDateTime now = LocalDateTime.now();
        // 活动是否还没开始
        if (now.isBefore(activity.getBeginTime())) {
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_STARTED);
        }

        // 活动已经结束
        if (now.isAfter(activity.getEndTime())) {
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_ENDED);
        }

        // 4. 根据活动 ID 和商品 ID 查询秒杀商品，校验此活动下商品是否存在
        SeckillGoods seckillGoods = seckillGoodsMapper.selectOne(
                Wrappers.<SeckillGoods>lambdaQuery()
                        .eq(SeckillGoods::getActivityId, activityId)
                        .eq(SeckillGoods::getGoodsId, goodsId)
        );
        if (seckillGoods == null) {
            log.error("==> 秒杀商品不存在, activityId: {}, goodsId: {}", activityId, goodsId);
            throw new BizException(ResultCodeEnum.SECKILL_GOODS_NOT_EXIST);
        }

        // 5. 库存校验
        if (seckillGoods.getSeckillStock() == null || seckillGoods.getSeckillStock() <= 0) {
            throw new BizException(ResultCodeEnum.SECKILL_GOODS_SOLD_OUT);
        }

        // 使用 Hutool 提供的工具方法，通过雪花算法生成订单号
        String orderNo = IdUtil.getSnowflakeNextIdStr();

        // 构建消息体
        SeckillOrderMqDTO seckillOrderMqDTO = SeckillOrderMqDTO.builder()
                .userId(userId)
                .activityId(activityId)
                .seckillGoodsId(seckillGoods.getId())
                .seckillPrice(seckillGoods.getSeckillPrice())
                .goodsId(goodsId)
                .orderNo(orderNo)
                .requestTime(now)
                .build();

        // 发送 MQ，内部会携带 CorrelationData(orderNo)，方便生产者确认回调定位消息
        seckillOrderMessageSender.send(seckillOrderMqDTO);

        log.info("==> 秒杀下单消息已发送至 MQ, orderNo: {}, userId：{}, activityId：{}, goodsId：{}",
                orderNo, userId, activityId, goodsId);

        // 立即响应 “处理中”，返回 orderNo + status(-1 处理中)，不再干等那 ~100ms 的写库操作；
        return DoSeckillRspVO.builder()
                .orderNo(orderNo)
                .status(OrderStatusEnum.PROCESSING.getStatus())
                .build();
    }

    /**
     * 异步消费秒杀下单消息：扣减库存 + 创建订单
     *
     * @param message
     */
    @Override
    public void createSeckillOrder(SeckillOrderMqDTO message) {
        Long activityId = message.getActivityId();
        Long goodsId = message.getGoodsId();
        Long userId = message.getUserId();
        String orderNo = message.getOrderNo();

        log.info("==> 消费秒杀下单消息, orderNo: {}, userId: {}, activityId: {}, goodsId: {}",
                orderNo, userId, activityId, goodsId);

        // 查询商品信息，用于冗余到订单中
        Goods goods = goodsMapper.selectById(goodsId);

        // 订单过期时间：当前时间 + 30 分钟
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(30);

        // 编程式事务，精确控制事务边界
        try {
            SeckillOrder orderDO = transactionTemplate.execute(status -> {
                // 扣减库存
                int count = seckillGoodsMapper.deductStock(message.getSeckillGoodsId());
                if (count == 0) {
                    log.warn("==> 扣减库存失败，商品已售罄或已下架，orderNo：{}", orderNo);
                    return null;
                }

                // 创建订单
                SeckillOrder order = SeckillOrder.builder()
                        .userId(userId)
                        .activityId(activityId)
                        .goodsId(goodsId)
                        .orderNo(orderNo)
                        .seckillPrice(message.getSeckillPrice())
                        .goodsName(goods.getGoodsName())
                        .goodsImg(goods.getGoodsImg())
                        .status(OrderStatusEnum.PENDING_PAYMENT.getStatus())
                        .expireTime(expireTime)
                        .isDeleted(0)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build();

                seckillOrderMapper.insert(order);
                return order;
            });

            if (Objects.nonNull(orderDO)) {
                log.info("==> 异步秒杀下单成功, orderNo: {}", orderNo);
            }
        } catch (DuplicateKeyException e) {
            // 幂等兜底：order_no 唯一索引命中，说明是重复投递的消息
            // 直接当作成功处理，不再抛异常，避免消费者把消息无限重投
            log.warn("==> 重复消费秒杀消息，订单已存在，幂等返回, orderNo: {}", orderNo);
        }
    }
}
