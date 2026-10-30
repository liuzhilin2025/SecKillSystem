package com.practice.flashsale.order.service.impl;

import cn.hutool.core.util.StrUtil;
import com.practice.flashsale.common.constant.RedisKeyConstants;
import com.practice.flashsale.common.utils.JsonUtils;
import com.practice.flashsale.order.enums.OrderStatusEnum;
import com.practice.flashsale.order.enums.SeckillStockCompensationResultEnum;
import com.practice.flashsale.order.model.dto.SeckillOrderMqDTO;
import com.practice.flashsale.order.model.vo.FindSeckillOrderResultRspVO;
import com.practice.flashsale.order.service.SeckillOrderPreDeductCompensationService;
import com.practice.flashsale.order.service.SeckillOrderResultNotifyService;
import com.practice.flashsale.order.service.SeckillStockService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/14 13:45
 * @Version: v1.0.0
 * @Description: 秒杀订单预扣回补
 **/
@Service
@Slf4j
public class SeckillOrderPreDeductCompensationServiceImpl implements SeckillOrderPreDeductCompensationService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SeckillStockService seckillStockService;

    @Resource
    private SeckillOrderResultNotifyService seckillOrderResultNotifyService;

    /**
     * MQ 发布明确失败时，回补 Redis 预扣库存和用户购买标记
     *
     * @param orderNo
     * @param reason
     */
    @Override
    public void compensateWhenPublishFailed(String orderNo, String reason) {
        // 构建回补上下文 Redis Key
        String compensationKey = RedisKeyConstants.buildSeckillOrderCompensationKey(orderNo);
        // 查询 Redis
        String cachedMessage = stringRedisTemplate.opsForValue().get(compensationKey);

        if (StrUtil.isBlank(cachedMessage)) {
            log.warn("==> 未找到回补上下文, 跳过 Redis 回补, orderNo：{}, reason：{}", orderNo, reason);
            return;
        }

        // 将上下文转 Json 换位实体类
        SeckillOrderMqDTO seckillOrderMqDTO = JsonUtils.parseObject(cachedMessage, SeckillOrderMqDTO.class);

        // 执行 Lua 脚本，回补秒杀库存，并删除用户购买标记
        SeckillStockCompensationResultEnum result = seckillStockService.compensatePreDeductStock(
                seckillOrderMqDTO.getActivityId(), seckillOrderMqDTO.getGoodsId(), seckillOrderMqDTO.getUserId(), seckillOrderMqDTO.getOrderNo());

        // 库存回补成功
        if (Objects.equals(result, SeckillStockCompensationResultEnum.SUCCESS)) {
            // 设置订单状态为 “秒杀失败”，并 SSE 推送结果
            saveOrderFailedStatus(seckillOrderMqDTO);

            // 删除 Redis 中的回补上下文
            stringRedisTemplate.delete(compensationKey);
            log.warn("==> MQ 发布失败已完成 Redis 预扣回补, orderNo: {}, reason: {}", orderNo, reason);
            return;
        }

        log.error("==> Redis 库存 Key 不存在，暂不自动回补, orderNo：{}, reason：{}", orderNo, reason);
    }

    /**
     * 设置订单状态为 “秒杀失败”，并 SSE 推送结果
     * @param seckillOrderMqDTO
     */
    private void saveOrderFailedStatus(SeckillOrderMqDTO seckillOrderMqDTO) {
        // 构建订单状态 Key
        String statusKey = RedisKeyConstants.SECKILL_ORDER_STATUS_PREFIX
                + seckillOrderMqDTO.getUserId() + ":" + seckillOrderMqDTO.getOrderNo();

        // 设置订单状态为秒杀失败
        stringRedisTemplate.opsForValue().set(statusKey,
                String.valueOf(OrderStatusEnum.SECKILL_FAILED.getStatus()),
                RedisKeyConstants.SECKILL_ORDER_STATUS_TTL_MINUTES, TimeUnit.MINUTES);

        // SSE 推送秒杀订单处理结果
        seckillOrderResultNotifyService.notifyOrderResult(seckillOrderMqDTO.getUserId(),
                FindSeckillOrderResultRspVO.builder()
                        .orderNo(seckillOrderMqDTO.getOrderNo())
                        .status(OrderStatusEnum.SECKILL_FAILED.getStatus())
                        .statusDesc(OrderStatusEnum.SECKILL_FAILED.getDescription())
                        .build());
    }
}
