package com.practice.flashsale.order.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.practice.flashsale.common.constant.RedisKeyConstants;
import com.practice.flashsale.common.entity.SeckillOrder;
import com.practice.flashsale.common.enums.ResultCodeEnum;
import com.practice.flashsale.common.exception.BizException;
import com.practice.flashsale.common.mapper.SeckillGoodsMapper;
import com.practice.flashsale.common.mapper.SeckillOrderMapper;
import com.practice.flashsale.common.model.dto.SeckillActivityGoodsMetaDTO;
import com.practice.flashsale.common.utils.DateTimeUtils;
import com.practice.flashsale.common.utils.JsonUtils;
import com.practice.flashsale.order.enums.OrderStatusEnum;
import com.practice.flashsale.order.enums.SeckillStockDeductResultEnum;
import com.practice.flashsale.order.model.dto.DoSeckillReqDTO;
import com.practice.flashsale.order.model.dto.FindSeckillOrderResultReqDTO;
import com.practice.flashsale.order.model.dto.GetSeckillTokenReqDTO;
import com.practice.flashsale.order.model.dto.SeckillOrderMqDTO;
import com.practice.flashsale.order.model.vo.DoSeckillRspVO;
import com.practice.flashsale.order.model.vo.FindSeckillOrderResultRspVO;
import com.practice.flashsale.order.model.vo.GetSeckillTokenRspVO;
import com.practice.flashsale.order.mq.SeckillOrderMessageSender;
import com.practice.flashsale.order.service.OrderService;
import com.practice.flashsale.order.service.SeckillOrderResultNotifyService;
import com.practice.flashsale.order.service.SeckillStockService;
import com.practice.flashsale.order.utils.OrderLockUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

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
    private SeckillGoodsMapper seckillGoodsMapper;

    @Resource
    private SeckillOrderMapper seckillOrderMapper;

    @Resource
    private OrderLockUtils orderLockUtils;

    @Resource
    private SeckillStockService seckillStockService;

    @Resource
    private SeckillOrderMessageSender seckillOrderMessageSender;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SeckillOrderResultNotifyService seckillOrderResultNotifyService;

    @Resource(name = "seckillOrderQueueDrainExecutor")
    private ThreadPoolTaskExecutor seckillOrderQueueDrainExecutor;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 秒杀令牌随机数生成器
     */
    private static final SecureRandom SECKILL_TOKEN_RANDOM = new SecureRandom();

    /**
     * 秒杀令牌校验并删除 Lua 脚本
     */
    private final DefaultRedisScript<Long> seckillCheckTokenScript;

    /**
     * 秒杀令牌大闸 Lua 脚本
     */
    private final DefaultRedisScript<String> seckillTokenGateScript;

    /**
     * 初始化秒杀令牌校验并删除 Lua 脚本
     */
    public OrderServiceImpl() {
        this.seckillCheckTokenScript = new DefaultRedisScript<>();
        this.seckillCheckTokenScript.setLocation(new ClassPathResource("lua/seckill_check_token.lua"));
        this.seckillCheckTokenScript.setResultType(Long.class);

        this.seckillTokenGateScript = new DefaultRedisScript<>();
        this.seckillTokenGateScript.setLocation(new ClassPathResource("lua/seckill_token_gate.lua"));
        this.seckillTokenGateScript.setResultType(String.class);
    }

    /**
     * 保存秒杀订单异步处理状态
     *
     * @param userId
     * @param orderNo
     * @param status
     */
    private void saveOrderStatus(Long userId, String orderNo, Integer status) {
        String redisKey = RedisKeyConstants.SECKILL_ORDER_STATUS_PREFIX + userId + ":" + orderNo;
        stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(status),
                RedisKeyConstants.SECKILL_ORDER_STATUS_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 秒杀下单
     *
     * @param dto
     * @return
     */
    @Override
    public DoSeckillRspVO doSeckill(DoSeckillReqDTO dto) {
        // 获取登录用户 ID
        long userId = StpUtil.getLoginIdAsLong();

        try {
            // 将秒杀下单核心逻辑, 提交到固定大小线程池中，限制同一时刻真正进入下单链路的请求数量
            return seckillOrderQueueDrainExecutor.submit(() -> doSeckillInternal(dto, userId)).get();
        } catch (RejectedExecutionException e) { // 线程池队列已满，说明当前瞬时请求过多，直接快速失败
            log.warn("==> 秒杀下单队列泄洪线程池已满, activityId: {}, goodsId: {}, userId: {}",
                    dto.getActivityId(), dto.getGoodsId(), userId);

            throw new BizException(ResultCodeEnum.SECKILL_SYSTEM_BUSY);
        } catch (InterruptedException e) { // 当前请求线程被中断，快速失败
            Thread.currentThread().interrupt();
            log.warn("==> 秒杀下单任务被中断, activityId: {}, goodsId: {}, userId: {}",
                    dto.getActivityId(), dto.getGoodsId(), userId, e);

            throw new BizException(ResultCodeEnum.SECKILL_SYSTEM_BUSY);
        } catch (ExecutionException e) { // 线程内部执行错误，业务异常需要原样抛出
            Throwable cause = e.getCause();

            if (cause instanceof BizException bizException) {
                throw bizException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("秒杀下单任务执行失败", cause);
        }
    }

    private DoSeckillRspVO doSeckillInternal(DoSeckillReqDTO dto, Long userId) {
        // 活动 ID
        Long activityId = dto.getActivityId();
        // 商品 ID
        Long goodsId = dto.getGoodsId();

        // 1. 获取当前登录用户 ID
        log.info("==> 当前登录用户 ID：{}", userId);

        // 校验秒杀令牌
        checkSeckillToken(activityId, goodsId, userId, dto.getToken());

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
        // 从 Redis 中查询秒杀商品元数据
        String redisKey = RedisKeyConstants.buildSeckillActivityGoodsMetaKey(activityId, goodsId);
        String cachedValue = stringRedisTemplate.opsForValue().get(redisKey);

        if (StrUtil.isBlank(cachedValue)) {
            log.error("==> 秒杀下单入口元数据未预热, key：{}", redisKey);
            throw new IllegalStateException("秒杀下单入口元数据未预热，请先预热活动");
        }

        // 转换 Json 字符串为实体类
        SeckillActivityGoodsMetaDTO activityGoodsMetaDTO = JsonUtils.parseObject(cachedValue, SeckillActivityGoodsMetaDTO.class);

        // 3. 校验秒杀活动时间
        LocalDateTime now = LocalDateTime.now();

        // 根据活动结束时间，来计算用户购买标记缓存的 TTL，覆盖整个秒杀活动周期
        Long userOrderTtlSeconds = RedisKeyConstants.calculateTtlSeconds(activityGoodsMetaDTO.getEndTime());

        // 由于业务需要，避免售罄标记，抢在活动状态校验、一人一单之前返回，不然提示信息，对用户不友好
        // 校验活动开始、结束时间
        if (now.isBefore(activityGoodsMetaDTO.getBeginTime())) {
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_STARTED);
        }

        if (!now.isBefore(activityGoodsMetaDTO.getEndTime())) {
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_ENDED);
        }

        // 如果用户已经参与过本场秒杀时，优先返回重复参与，而不是被售罄标记拦截
        String userOrderKey = RedisKeyConstants.buildSeckillUserOrderKey(activityId, goodsId, userId);
        if (stringRedisTemplate.hasKey(userOrderKey)) {
            throw new BizException(ResultCodeEnum.SECKILL_ORDER_DUPLICATE);
        }

        // 秒杀商品已经售罄时，直接快速失败，不再继续生成 orderNo 和执行 Lua 脚本
        String soldOutKey = RedisKeyConstants.buildSeckillSoldOutKey(activityId, goodsId);
        if (stringRedisTemplate.hasKey(soldOutKey)) {
            log.info("==> 秒杀商品已售罄，命中售罄标记快速失败, key：{}", soldOutKey);
            throw new BizException(ResultCodeEnum.SECKILL_GOODS_SOLD_OUT);
        }

        // 使用 Hutool 提供的工具方法，通过雪花算法生成订单号
        String orderNo = IdUtil.getSnowflakeNextIdStr();

        // 构建消息体
        SeckillOrderMqDTO seckillOrderMqDTO = SeckillOrderMqDTO.builder()
                .userId(userId)
                .activityId(activityId)
                .seckillGoodsId(activityGoodsMetaDTO.getSeckillGoodsId())
                .goodsName(activityGoodsMetaDTO.getGoodsName())
                .goodsImg(activityGoodsMetaDTO.getGoodsImg())
                .seckillPrice(activityGoodsMetaDTO.getSeckillPrice())
                .goodsId(goodsId)
                .orderNo(orderNo)
                .requestTime(now)
                .build();

        // 执行 Redis Lua 脚本：原子校验一人一单并预扣库存
        SeckillStockDeductResultEnum deductResult = seckillStockService.preDeductStock(
                seckillOrderMqDTO, userOrderTtlSeconds,
                DateTimeUtils.toEpochMilli(activityGoodsMetaDTO.getBeginTime()),
                DateTimeUtils.toEpochMilli(activityGoodsMetaDTO.getEndTime()));

        // 判断 Lua 脚本执行结果
        // 秒杀活动还没开始
        if (Objects.equals(deductResult, SeckillStockDeductResultEnum.ACTIVITY_NOT_STARTED)) {
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_STARTED);
        }

        // 秒杀活动已经结束
        if (Objects.equals(deductResult, SeckillStockDeductResultEnum.ACTIVITY_ENDED)) {
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_ENDED);
        }

        // 已售罄
        if (Objects.equals(deductResult, SeckillStockDeductResultEnum.SOLD_OUT)) {
            throw new BizException(ResultCodeEnum.SECKILL_GOODS_SOLD_OUT);
        }

        // 请勿重复参与秒杀
        if (Objects.equals(deductResult, SeckillStockDeductResultEnum.REPEATED_ORDER)) {
            throw new BizException(ResultCodeEnum.SECKILL_ORDER_DUPLICATE);
        }

        // 记录异步处理状态，前端后续可以根据 orderNo 查询最终结果
        saveOrderStatus(userId, orderNo, OrderStatusEnum.PROCESSING.getStatus());

        // 发送 MQ，内部会携带 CorrelationData(orderNo)，方便生产者确认回调定位消息
        boolean isSendSuccess = seckillOrderMessageSender.send(seckillOrderMqDTO);

        if (!isSendSuccess) {
            log.warn("==> 秒杀下单消息发送结果未知，订单保持处理中等待后续确认, orderNo：{}", orderNo);
        }

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
        // 记录消费开始时间
        long startTime = System.currentTimeMillis();
        Long activityId = message.getActivityId();
        Long goodsId = message.getGoodsId();
        Long userId = message.getUserId();
        String orderNo = message.getOrderNo();

        log.info("==> 消费秒杀下单消息, orderNo: {}, userId: {}, activityId: {}, goodsId: {}",
                orderNo, userId, activityId, goodsId);

        // 订单过期时间：当前时间 + 30 分钟
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(30);

        // 编程式事务，精确控制事务边界
        try {
            SeckillOrder orderDO = transactionTemplate.execute(status -> {
                // 扣减库存
                long deductStartTime = System.currentTimeMillis();

                int count = seckillGoodsMapper.deductStock(message.getSeckillGoodsId());
                // 扣库存耗时
                long deductCost = System.currentTimeMillis() - deductStartTime;
                log.info("#### 秒杀扣库存耗时, orderNo：{}, seckillGoodsId：{}, cost：{} ms",
                        orderNo, message.getSeckillGoodsId(), deductCost);
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
                        .goodsName(message.getGoodsName())
                        .goodsImg(message.getGoodsImg())
                        .status(OrderStatusEnum.PENDING_PAYMENT.getStatus())
                        .expireTime(expireTime)
                        .isDeleted(0)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build();

                seckillOrderMapper.insert(order);
                return order;
            });

            // 扣库存失败，更新 Redis 中订单状态为秒杀失败
            if (Objects.isNull(orderDO)) {
                saveOrderStatus(userId, orderNo, OrderStatusEnum.SECKILL_FAILED.getStatus());

                // 清理 Redis 中的回补上下文
                deleteCompensationContext(orderNo);

                // 推送 sse 结果
                seckillOrderResultNotifyService.notifyOrderResult(userId,
                        buildStatusResult(orderNo, OrderStatusEnum.SECKILL_FAILED));
                return;
            }
            // 订单创建成功，更新 Redis 中订单状态为待支付
            saveOrderStatus(userId, orderNo, OrderStatusEnum.PENDING_PAYMENT.getStatus());

            // 消费者已经正常落库，删除待回补上下文，避免 Redis 中残留无效数据，占着内存
            deleteCompensationContext(orderNo);

            // 推送 sse 结果
            seckillOrderResultNotifyService.notifyOrderResult(userId, buildOrderResult(orderDO));
            log.info("==> 异步秒杀下单成功, orderNo：{}", orderNo);

        } catch (DuplicateKeyException e) {
            // 幂等兜底：order_no 唯一索引命中，说明是重复投递的消息
            // 直接当作成功处理，不再抛异常，避免消费者把消息无限重投
            log.warn("==> 重复消费秒杀消息，订单已存在，幂等返回, orderNo: {}", orderNo);

            // 不能直接写 PENDING_PAYMENT,避免把已支付、已取消等状态回退
            SeckillOrder existedOrder = seckillOrderMapper.selectByOrderNoAndUserId(orderNo, userId);
            if (Objects.nonNull(existedOrder)) {
                saveOrderStatus(userId, orderNo, existedOrder.getStatus());

                // 清理 Redis 中的回补上下文
                deleteCompensationContext(orderNo);

                // 推送 sse 结果
                seckillOrderResultNotifyService.notifyOrderResult(userId, buildOrderResult(existedOrder));
            } else {
                log.warn("==> 重复消费命中唯一索引，但未查询到当前用户订单, orderNo：{}, userId：{}", orderNo, userId);
            }
        } finally {
            log.info("#### 秒杀订单消费链路总耗时, orderNo：{}, cost：{} ms",
                    orderNo, System.currentTimeMillis() - startTime);
        }
    }

    private FindSeckillOrderResultRspVO buildOrderResult(SeckillOrder orderDO) {
        return FindSeckillOrderResultRspVO.builder()
                .orderId(orderDO.getId())
                .orderNo(orderDO.getOrderNo())
                .status(orderDO.getStatus())
                .statusDesc(OrderStatusEnum.getDescriptionByStatus(orderDO.getStatus()))
                .goodsId(orderDO.getGoodsId())
                .goodsName(orderDO.getGoodsName())
                .goodsImg(orderDO.getGoodsImg())
                .seckillPrice(orderDO.getSeckillPrice())
                .build();
    }

    private FindSeckillOrderResultRspVO buildStatusResult(String orderNo, OrderStatusEnum status) {
        return FindSeckillOrderResultRspVO.builder()
                .orderNo(orderNo)
                .status(status.getStatus())
                .statusDesc(status.getDescription())
                .build();
    }

    /**
     * 删除秒杀订单待回补上下文
     *
     * @param orderNo 订单号
     */
    private void deleteCompensationContext(String orderNo) {
        String compensationKey = RedisKeyConstants.buildSeckillOrderCompensationKey(orderNo);
        stringRedisTemplate.delete(compensationKey);
    }


    /**
     * 查询秒杀订单处理结果
     *
     * @param dto
     * @return
     */
    @Override
    public FindSeckillOrderResultRspVO findSeckillOrderResult(FindSeckillOrderResultReqDTO dto) {
        // 订单号
        String orderNo = dto.getOrderNo();
        // 当前登录用户 ID
        long userId = StpUtil.getLoginIdAsLong();

        // 查询 Redis 获取订单状态
        String redisKey = RedisKeyConstants.SECKILL_ORDER_STATUS_PREFIX + userId + ":" + orderNo;
        String orderStatus = stringRedisTemplate.opsForValue().get(redisKey);

        if (StrUtil.isNotBlank(orderStatus)) {
            OrderStatusEnum statusEnum = OrderStatusEnum.getByStatus(Integer.valueOf(orderStatus));

            // Redis 中订单状态，如果是 PROCESSING 或 SECKILL_FAILED，直接返回
            if (OrderStatusEnum.PROCESSING.equals(statusEnum)
                    || OrderStatusEnum.SECKILL_FAILED.equals(statusEnum)) {
                return FindSeckillOrderResultRspVO.builder()
                        .orderNo(orderNo)
                        .status(statusEnum.getStatus())
                        .statusDesc(statusEnum.getDescription())
                        .build();
            }
        }

        // 如果是 PENDING_PAYMENT 待支付状态，继续回查订单表，返回完整订单信息
        SeckillOrder order = seckillOrderMapper.selectByOrderNoAndUserId(orderNo, userId);

        // 如果表里没订单记录，可能消费者还没来得及消费，返回处理中。。。
        if (Objects.isNull(order)) {
            return FindSeckillOrderResultRspVO.builder()
                    .orderNo(orderNo)
                    .status(OrderStatusEnum.PROCESSING.getStatus())
                    .statusDesc(OrderStatusEnum.PROCESSING.getDescription())
                    .build();
        }

        return FindSeckillOrderResultRspVO.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .status(order.getStatus())
                .statusDesc(OrderStatusEnum.getDescriptionByStatus(order.getStatus()))
                .goodsId(order.getGoodsId())
                .goodsName(order.getGoodsName())
                .goodsImg(order.getGoodsImg())
                .seckillPrice(order.getSeckillPrice())
                .build();
    }

    /**
     * 生成秒杀令牌
     */
    private String generateSeckillToken() {
        // 使用 SecureRandom 生成 32 字节随机数，尽量让令牌无规律，防止被猜测到
        byte[] bytes = new byte[32];
        SECKILL_TOKEN_RANDOM.nextBytes(bytes);

        // 转成字符串
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 校验秒杀令牌
     *
     * @param activityId 活动 ID
     * @param goodsId    商品 ID
     * @param userId     用户 ID
     * @param token      请求携带的秒杀令牌
     */
    private void checkSeckillToken(Long activityId, Long goodsId, Long userId, String token) {
        // 构建当前用户、当前活动、当前商品对应的令牌 Key
        String tokenKey = RedisKeyConstants.buildSeckillOrderTokenKey(activityId, goodsId, userId);

        // 通过 Lua 脚本，来原子型的完成 “比对令牌 + 删除令牌”
        Long result = stringRedisTemplate.execute(seckillCheckTokenScript, List.of(tokenKey), token);
        if (Objects.isNull(result)) {
            throw new IllegalStateException("执行秒杀令牌校验 Lua 脚本失败");
        }

        // 返回 0，表示令牌不存在、已过期、已被使用过，或者请求中的令牌不正确
        if (!Objects.equals(result, 1L)) {
            log.warn("==> 秒杀令牌校验失败, userId：{}, activityId：{}, goodsId：{}, tokenKey：{}",
                    userId, activityId, goodsId, tokenKey);
            throw new BizException(ResultCodeEnum.SECKILL_TOKEN_INVALID);
        }

        log.info("==> 秒杀令牌校验通过并删除, userId：{}, activityId：{}, goodsId：{}, tokenKey：{}",
                userId, activityId, goodsId, tokenKey);
    }

    /**
     * 获取秒杀令牌
     *
     * @param reqDTO
     * @return
     */
    @Override
    public GetSeckillTokenRspVO getSeckillToken(GetSeckillTokenReqDTO reqDTO) {
        // 活动 ID
        Long activityId = reqDTO.getActivityId();
        // 商品 ID
        Long goodsId = reqDTO.getGoodsId();
        // 当前登录用户 ID
        long userId = StpUtil.getLoginIdAsLong();

        // 从 Redis 中查询秒杀商品元数据，确认该活动商品已经预热
        String redisKey = RedisKeyConstants.buildSeckillActivityGoodsMetaKey(activityId, goodsId);

        String cachedValue = stringRedisTemplate.opsForValue().get(redisKey);

        if (StrUtil.isBlank(cachedValue)) {
            log.error("==> 秒杀下单入口元数据未预热, key：{}", redisKey);
            throw new IllegalStateException("秒杀下单入口元数据未预热，请先预热活动");
        }

        SeckillActivityGoodsMetaDTO activityGoodsMetaDTO = JsonUtils.parseObject(cachedValue, SeckillActivityGoodsMetaDTO.class);

        // 获取令牌前，先校验活动时间，避免活动未开始，或已结束时继续发放令牌
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activityGoodsMetaDTO.getBeginTime())) {
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_STARTED);
        }

        if (!now.isBefore(activityGoodsMetaDTO.getEndTime())) {
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_ENDED);
        }

        // 构建秒杀令牌 Key
        String tokenKey = RedisKeyConstants.buildSeckillOrderTokenKey(activityId, goodsId, userId);
        // 构建令牌大闸 Key
        String tokenGateKey = RedisKeyConstants.buildSeckillTokenGateKey(activityId, goodsId);
        // 先生成一个候选秒杀令牌，至于是否写入 Redis，在 Lua 脚本中去原子判断
        String candidateToken = generateSeckillToken();

        // 执行 Lua 脚本
        String tokenGateResult = stringRedisTemplate.execute(seckillTokenGateScript, List.of(tokenKey, tokenGateKey),
                candidateToken, String.valueOf(RedisKeyConstants.SECKILL_ORDER_TOKEN_TTL_SECONDS));

        return buildTokenGateResponse(tokenGateResult, userId, activityId, goodsId, tokenKey, tokenGateKey);
    }

    /**
     * 解析秒杀令牌剩余过期时间
     */
    private Long resolveTokenExpireSeconds(Long expireSeconds) {
        if (Objects.isNull(expireSeconds) || expireSeconds <= 0) {
            return RedisKeyConstants.SECKILL_ORDER_TOKEN_TTL_SECONDS;
        }
        return expireSeconds;
    }

    /**
     * 构建秒杀令牌大闸响应
     */
    private GetSeckillTokenRspVO buildTokenGateResponse(String tokenGateResult, Long userId, Long activityId,
                                                        Long goodsId, String tokenKey, String tokenGateKey) {
        if (StrUtil.isBlank(tokenGateResult)) {
            throw new IllegalStateException("执行秒杀令牌大闸 Lua 脚本失败");
        }

        // 分割 Lua 执行结果
        String[] resultArr = tokenGateResult.split("\\|", -1);
        if (resultArr.length != 3) {
            throw new IllegalStateException("秒杀令牌大闸 Lua 脚本返回格式错误：" + tokenGateResult);
        }

        // 返回码
        Long resultCode = Long.valueOf(resultArr[0]);
        // 秒杀令牌
        String token = resultArr[1];
        // 过期时间
        Long expireSeconds = Long.valueOf(resultArr[2]);

        if (Objects.equals(resultCode, -1L)) {
            log.error("==> 秒杀令牌大闸未预热, key: {}", tokenGateKey);
            throw new IllegalStateException("秒杀令牌大闸未预热，请先预热活动");
        }

        if (Objects.equals(resultCode, 0L)) {
            log.info("==> 秒杀令牌大闸名额已发完, key: {}", tokenGateKey);
            throw new BizException(ResultCodeEnum.SECKILL_TOKEN_GATE_EMPTY);
        }

        if (Objects.equals(resultCode, 2L)) {
            log.info("==> 当前用户已存在有效秒杀令牌, userId: {}, activityId: {}, goodsId: {}, tokenKey: {}",
                    userId, activityId, goodsId, tokenKey);
        } else if (Objects.equals(resultCode, 1L)) {
            log.info("==> 生成秒杀令牌, userId: {}, activityId: {}, goodsId: {}, tokenKey: {}",
                    userId, activityId, goodsId, tokenKey);
        } else {
            throw new IllegalStateException("秒杀令牌大闸 Lua 脚本返回未知结果码：" + resultCode);
        }

        return GetSeckillTokenRspVO.builder()
                .token(token)
                .expireSeconds(resolveTokenExpireSeconds(expireSeconds))
                .build();
    }
}
