package com.practice.flashsale.order.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import com.practice.flashsale.order.model.vo.DoSeckillRspVO;
import com.practice.flashsale.order.service.OrderService;
import com.practice.flashsale.order.utils.OrderLockUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

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
    private TransactionTemplate transactionTemplate;

    @Resource
    private OrderLockUtils orderLockUtils;

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

        // 6. 查询商品信息，用于冗余到订单中
        Goods goods = goodsMapper.selectById(goodsId);
        // 使用 Hutool 提供的工具方法，通过雪花算法生成订单号
        String orderNo = IdUtil.getSnowflakeNextIdStr();
        // 订单过期时间：当前时间 + 30 分钟
        LocalDateTime expireTime = now.plusMinutes(30);

        // 编程式事务，精确控制事务边界
        SeckillOrder orderDO = transactionTemplate.execute(status -> {
            // 7. 扣减库存
            int rows = seckillGoodsMapper.deductStock(seckillGoods.getId());
            if (rows == 0) {
                log.warn("===> 秒杀商品库存不足, seckillGoodsId: {}", seckillGoods.getId());
                throw new BizException(ResultCodeEnum.SECKILL_GOODS_SOLD_OUT);
            }

            // 8. 创建订单
            SeckillOrder order = SeckillOrder.builder()
                    .userId(userId)
                    .activityId(activityId)
                    .goodsId(goodsId)
                    .orderNo(orderNo)
                    .seckillPrice(seckillGoods.getSeckillPrice())
                    .goodsName(goods.getGoodsName())
                    .goodsImg(goods.getGoodsImg())
                    .status(OrderStatusEnum.PENDING_PAYMENT.getStatus())
                    .expireTime(expireTime)
                    .isDeleted(0)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            try {
                seckillOrderMapper.insert(order);
            } catch (DuplicateKeyException e) {
                log.warn("==> 重复下单, userId：{}, activityId：{}, goodsId：{}", userId, activityId, goodsId);
                throw new BizException(ResultCodeEnum.SECKILL_ORDER_DUPLICATE);
            }

            return order;

        });

        log.info("==> 秒杀下单成功, orderId：{}, orderNo：{}", orderDO.getId(), orderNo);

        // 9. 组装响应数据
        return DoSeckillRspVO.builder()
                .orderId(orderDO.getId())
                .orderNo(orderNo)
                .goodsName(goods.getGoodsName())
                .goodsImg(goods.getGoodsImg())
                .seckillPrice(seckillGoods.getSeckillPrice())
                .status(OrderStatusEnum.PENDING_PAYMENT.getStatus())
                .expireTime(expireTime)
                .build();
    }
}
