package com.practice.flashsale.order.service.impl;

import com.practice.flashsale.common.constant.RedisKeyConstants;
import com.practice.flashsale.order.enums.SeckillStockDeductResultEnum;
import com.practice.flashsale.order.service.SeckillStockService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/13 15:27
 * @Version: v1.0.0
 * @Description: 秒杀库存服务
 **/
@Service
@Slf4j
public class SeckillStockServiceImpl implements SeckillStockService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀库存预扣 Lua 脚本
     */
    private final DefaultRedisScript<Long> seckillPreDeductStockScript;

    public SeckillStockServiceImpl() {
        this.seckillPreDeductStockScript = new DefaultRedisScript<>();
        this.seckillPreDeductStockScript.setLocation(new ClassPathResource("lua/seckill_pre_deduct_stock.lua"));
        this.seckillPreDeductStockScript.setResultType(Long.class);
    }

    /**
     * Redis Lua 原子预扣库存
     *
     * @param activityId
     * @param goodsId
     */
    @Override
    public SeckillStockDeductResultEnum preDeductStock(Long activityId, Long goodsId,
                                                       Long userId, Long userOrderTtlSeconds,
                                                       Long activityBeginTimeMillis, Long activityEndTimeMillis) {
        // 构建库存 Redis Key
        String stockKey = RedisKeyConstants.buildSeckillStockKey(activityId, goodsId);

        // 构建用户购买标记 Key
        String userOrderKey = RedisKeyConstants.buildSeckillUserOrderKey(activityId, goodsId, userId);

        // 执行 Lua 脚本
        Long resultCode = stringRedisTemplate.execute(seckillPreDeductStockScript,
                List.of(stockKey, userOrderKey), String.valueOf(userOrderTtlSeconds),
                String.valueOf(activityBeginTimeMillis), String.valueOf(activityEndTimeMillis));
        if (Objects.isNull(resultCode)) {
            throw new IllegalArgumentException("执行秒杀库存预扣 Lua 脚本失败");
        }

        SeckillStockDeductResultEnum result = SeckillStockDeductResultEnum.getByCode(resultCode);
        if (Objects.isNull(result)) {
            throw new IllegalArgumentException("秒杀库存预扣 Lua 脚本返回未知结果码：" + resultCode);
        }

        if (Objects.equals(result, SeckillStockDeductResultEnum.STOCK_NOT_PREHEATED)) { // 库存未预热
            log.warn("==> 秒杀库存未预热, key：{}", stockKey);
        } else if (Objects.equals(result, SeckillStockDeductResultEnum.REPEATED_ORDER)) { // 重复参与秒杀
            log.info("==> 重复参与秒杀, userId：{}, activityId：{}, goodsId：{}", userId, activityId, goodsId);
        } else {
            log.info("==> 秒杀库存预扣完成, key：{}, result：{}", stockKey, result.getDescription());
        }

        return result;
    }
}
