package com.practice.flashsale.goods.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.practice.flashsale.common.constant.RedisKeyConstants;
import com.practice.flashsale.common.entity.*;
import com.practice.flashsale.common.enums.ActivityStatusEnum;
import com.practice.flashsale.common.enums.ResultCodeEnum;
import com.practice.flashsale.common.exception.BizException;
import com.practice.flashsale.common.mapper.*;
import com.practice.flashsale.common.utils.JsonUtils;
import com.practice.flashsale.goods.model.dto.FindSeckillGoodsDetailReqDTO;
import com.practice.flashsale.goods.model.dto.FindSeckillGoodsListReqDTO;
import com.practice.flashsale.goods.model.vo.FindSeckillGoodsDetailRspVO;
import com.practice.flashsale.goods.service.GoodsService;
import com.practice.flashsale.goods.model.vo.FindSeckillGoodsListRspVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 10:04
 * @Version: v1.0.0
 * @Description: 商品模块业务
 **/
@Service
@Slf4j
public class GoodsServiceImpl implements GoodsService {

    @Resource
    private SeckillGoodsMapper seckillGoodsMapper;

    @Resource
    private SeckillActivityMapper seckillActivityMapper;

    @Resource
    private GoodsMapper goodsMapper;

    @Resource
    private GoodsDetailMapper goodsDetailMapper;

    @Resource
    private GoodsImgMapper goodsImgMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private Cache<String, String> goodsListLocalCache;

    @Resource
    private Cache<String, String> goodsDetailLocalCache;

    /**
     * 处理缓存命中的商品列表数据: 反序列化 → 补充库存 → 重新计算活动状态
     *
     * @param redisJsonValue
     * @param activityId
     * @return
     */
    private List<FindSeckillGoodsListRspVO> processCachedGoodsList(String redisJsonValue, Long activityId) {
        // 缓存命中：手动将 String 字符串反序列化为商品列表
        List<FindSeckillGoodsListRspVO> cachedList = JsonUtils
                .parseArray(redisJsonValue, FindSeckillGoodsListRspVO.class);

        // 库存高频变化，缓存中不保证实时，命中后回源补齐
        supplementStock(cachedList, activityId);

        // 活动状态依赖当前时间，用缓存里的活动时间实时重算（判空，防止缓存为空数组时越界）
        if (CollUtil.isNotEmpty(cachedList)) {
            FindSeckillGoodsListRspVO first = cachedList.get(0);
            ActivityStatusEnum activityStatusEnum = calculateActivityStatus(first.getBeginTime(), first.getEndTime());
            cachedList.forEach(item ->
                    item.setActivityStatus(activityStatusEnum.getStatus()));
        }
        return cachedList;
    }

    /**
     * 查询秒杀商品列表
     *
     * @param dto
     * @return
     */
    @Override
    public List<FindSeckillGoodsListRspVO> findSeckillGoodsList(FindSeckillGoodsListReqDTO dto) {
        // 活动 ID
        Long activityId = dto.getActivityId();
        log.info("==> 查询秒杀商品列表, activityId: {}", activityId);

        // 构建 Redis 缓存 Key
        String redisKey = RedisKeyConstants.GOODS_LIST_PREFIX + activityId;

        // L1: 先查 Caffeine 本地缓存（微秒级，无网络开销）
        String localCachedValue = goodsListLocalCache.getIfPresent(redisKey);

        if (StrUtil.isNotBlank(localCachedValue)) {
            log.info("==> 命中本地缓存（L1）, key：{}", redisKey);
            // 手动将 String 字符串，反序列化为商品列表
            return processCachedGoodsList(localCachedValue, activityId);
        }

        // 第一道防线：布隆过滤器校验活动是否存在
        // 如果布隆过滤器返回 “不存在”，绝对正确，说明该活动 ID 一定不合法，直接拒绝掉
        RBloomFilter<Long> activityBloom = redissonClient.getBloomFilter(RedisKeyConstants.SECKILL_ACTIVITY_BLOOM_KEY);

        if (activityBloom.isExists() && !activityBloom.contains(activityId)) {
            log.info("==> 布隆过滤器拦截：活动不存在，activityId：{}", activityId);
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
        }

        // L2：查 Redis 缓存
        String redisJsonValue = stringRedisTemplate.opsForValue().get(redisKey);

        // 若缓存不为空
        if (StrUtil.isNotBlank(redisJsonValue)) {
            log.info("==> 命中商品列表 Redis 缓存（L2）, redisKey：{}", redisKey);

            // 防止缓存穿透，判断缓存是否是 NULL
            if (Objects.equals(RedisKeyConstants.NULL_CACHE_VALUE, redisJsonValue)) {
                log.info("==> 命中空值缓存，活动不存在, redisKey: {}", redisKey);
                throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
            }
            // 手动将 String 字符串，反序列化为商品列表
            List<FindSeckillGoodsListRspVO> cachedList = processCachedGoodsList(redisJsonValue, activityId);

            // 能走到这里，说明 L1 本地缓存未命中，需要回填，以便后续请求能够命中 L1
            goodsListLocalCache.put(redisKey, redisJsonValue);
            return cachedList;
        }

        // 1. 查询活动信息
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            log.error("==> 查询秒杀商品列表, activityId: {}, 活动信息不存在", activityId);
            // 缓存空值，防止缓存穿透
            cacheNullValue(redisKey);

            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
        }

        // 2. 根据活动 ID 查询该活动下的所有秒杀商品
        List<SeckillGoods> seckillGoods = seckillGoodsMapper.selectList(// ✅ 引用实体方法，编译器会帮你查字段是否存在
                Wrappers.<SeckillGoods>lambdaQuery().eq(SeckillGoods::getActivityId, activityId)
        );
        if (CollUtil.isEmpty(seckillGoods)) {
            log.info("==> 该活动下暂无秒杀商品，activityId: {}", activityId);
            return Collections.emptyList();
        }

        // 3. 批量查询关联的商品信息（目的是为了获取对应秒杀商品的原价）
        List<Long> goodsIds = seckillGoods.stream()
                .map(SeckillGoods::getGoodsId)
                .toList();

        // 一次性批量查询所有商品，提升查询性能
        List<Goods> goodsList = goodsMapper.selectByIds(goodsIds);

        // 将商品 ID 和商品信息映射为 Map，方便后续查找
        Map<Long, Goods> goodsMap = goodsList.stream()
                .collect(Collectors.toMap(Goods::getId, goods -> goods));

        // 4. 计算活动状态（基于当前时间动态判断）
        ActivityStatusEnum activityStatusEnum = calculateActivityStatus(activity);

        // 5. 组装响应数据
        List<FindSeckillGoodsListRspVO> rspVOS = new ArrayList<>();
        for (SeckillGoods seckillGoodsDO : seckillGoods) {
            FindSeckillGoodsListRspVO rspVO = new FindSeckillGoodsListRspVO();
            rspVO.setId(seckillGoodsDO.getId());
            rspVO.setGoodsId(seckillGoodsDO.getGoodsId());
            rspVO.setActivityId(seckillGoodsDO.getActivityId());
            rspVO.setSeckillTitle(seckillGoodsDO.getSeckillTitle());
            rspVO.setSeckillImg(seckillGoodsDO.getSeckillImg());
            rspVO.setSeckillPrice(seckillGoodsDO.getSeckillPrice());
            rspVO.setSeckillTotal(seckillGoodsDO.getSeckillTotal());
            rspVO.setSeckillStock(seckillGoodsDO.getSeckillStock());
            rspVO.setActivityStatus(activityStatusEnum.getStatus());
            rspVO.setBeginTime(activity.getBeginTime());
            rspVO.setEndTime(activity.getEndTime());

            // 设置商品原价
            Goods goodsDO = goodsMap.get(seckillGoodsDO.getGoodsId());
            if (Objects.nonNull(goodsDO)) {
                rspVO.setGoodsPrice(goodsDO.getGoodsPrice());
            }

            rspVOS.add(rspVO);
        }

        // 将商品列表写入 Redis 缓存和本地缓存
        log.info("==> 商品列表缓存未命中，将数据写入 Redis 和本地缓存中，Key：{}", redisKey);
        // 写入本地缓存
        goodsListLocalCache.put(redisKey, JsonUtils.toJsonString(rspVOS));

        // 6. 组装完成，写回缓存（TTL 带随机抖动，避免同一时刻批量失效）
        cacheGoodsList(redisKey, rspVOS);

        return rspVOS;
    }

    /**
     * 实时补充库存字段（库存变化频繁，每次从数据库实时查询）
     *
     * @param cachedList 缓存中的商品列表
     * @param activityId 活动 ID
     */
    private void supplementStock(List<FindSeckillGoodsListRspVO> cachedList, Long activityId) {
        if (CollUtil.isEmpty(cachedList)) {
            return;
        }

        // 1. 库存回源：按活动 ID 只查一次，拿到最新剩余库存
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(
                Wrappers.<SeckillGoods>lambdaQuery().eq(SeckillGoods::getActivityId, activityId));
        if (CollUtil.isEmpty(seckillGoodsList)) {
            return;
        }

        Map<Long, Integer> stockMap = new HashMap<>(seckillGoodsList.size());
        for (SeckillGoods goods : seckillGoodsList) {
            stockMap.put(goods.getId(), goods.getSeckillStock());
        }

        // 2. 用数据库里的最新库存覆盖缓存值
        for (FindSeckillGoodsListRspVO rspVO : cachedList) {
            Integer seckillStock = stockMap.get(rspVO.getId());
            if (Objects.nonNull(seckillStock)) {
                rspVO.setSeckillStock(seckillStock);
            }
        }
    }

    /**
     * 商品列表写入 Redis 缓存（TTL 加随机抖动，防止同一时刻批量失效）
     *
     * @param redisKey 缓存 Key
     * @param rspVOS   商品列表
     */
    private void cacheGoodsList(String redisKey, List<FindSeckillGoodsListRspVO> rspVOS) {
        // 空列表暂不缓存，留待 5.6 空值策略统一处理
        if (CollUtil.isEmpty(rspVOS)) {
            return;
        }

        // TTL 跟随活动结束时间动态计算（活动已结束则用短 TTL 兜底，并叠加随机抖动）
        long ttlSeconds = RedisKeyConstants.resolveTtlSeconds(rspVOS.get(0).getEndTime());

        stringRedisTemplate.opsForValue()
                .set(redisKey, JsonUtils.toJsonString(rspVOS), ttlSeconds, TimeUnit.SECONDS);
        log.info("==> 商品列表写入缓存, redisKey: {}, ttlSeconds: {}", redisKey, ttlSeconds);
    }

    /**
     * 商品详情写入 Redis 缓存（TTL 加随机抖动，防止同一时刻批量失效）
     *
     * @param redisKey 缓存 Key
     * @param rspVO    商品详情
     */
    private void cacheGoodsDetail(String redisKey, FindSeckillGoodsDetailRspVO rspVO) {
        // TTL 跟随活动结束时间动态计算（活动已结束则用短 TTL 兜底，并叠加随机抖动）
        long ttlSeconds = RedisKeyConstants.resolveTtlSeconds(rspVO.getEndTime());

        stringRedisTemplate.opsForValue()
                .set(redisKey, JsonUtils.toJsonString(rspVO), ttlSeconds, TimeUnit.SECONDS);
        log.info("==> 商品详情写入缓存, redisKey: {}, ttlSeconds: {}", redisKey, ttlSeconds);
    }

    /**
     * 根据当前时间动态计算活动状态
     *
     * @param activityDO
     * @return
     */
    private ActivityStatusEnum calculateActivityStatus(SeckillActivity activityDO) {
        return calculateActivityStatus(activityDO.getBeginTime(), activityDO.getEndTime());
    }

    /**
     * 根据当前时间动态计算活动状态
     *
     * @param beginTime
     * @param endTime
     * @return
     */
    private ActivityStatusEnum calculateActivityStatus(LocalDateTime beginTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(beginTime)) { // 当前时间早于活动开始时间，则活动未开始
            return ActivityStatusEnum.NOT_STARTED;
        } else if (now.isAfter(endTime)) { // 当前时间晚于活动结束时间，则活动已结束
            return ActivityStatusEnum.ENDED;
        } else { // 活动进行中
            return ActivityStatusEnum.ING;
        }
    }


    /**
     * 缓存空值，防止缓存穿透
     *
     * @param redisKey
     */
    private void cacheNullValue(String redisKey) {
        // 当数据库中查不到数据时，往 Redis 写入一个空值标记，短时间内不再查 DB
        stringRedisTemplate.opsForValue().set(redisKey, RedisKeyConstants.NULL_CACHE_VALUE,
                        RedisKeyConstants.NULL_CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        log.info("==> 缓存空值，防止穿透, redisKey: {}, TTL: {}min", redisKey, RedisKeyConstants.NULL_CACHE_TTL_MINUTES);
    }

    /**
     * 处理缓存命中的商品详情数据: 反序列化 → 补充库存 → 重新计算活动状态
     *
     * @param redisJsonValue
     * @param activityId
     * @param goodsId
     * @return
     */
    private FindSeckillGoodsDetailRspVO processCachedGoodsDetail(String redisJsonValue, Long activityId, Long goodsId) {
        // 缓存命中
        // 手动将 String 字符串，反序列化为商品详情对象
        FindSeckillGoodsDetailRspVO cachedDetail = JsonUtils
                .parseObject(redisJsonValue, FindSeckillGoodsDetailRspVO.class);

        // 设置库存字段值（因为库存变化频繁，需要从数据库查最新的）
        SeckillGoods seckillGoods = seckillGoodsMapper.selectOne(
                Wrappers.<SeckillGoods>lambdaQuery()
                        .eq(SeckillGoods::getActivityId, activityId)
                        .eq(SeckillGoods::getGoodsId, goodsId)
        );
        if (Objects.nonNull(seckillGoods)) {
            cachedDetail.setSeckillStock(seckillGoods.getSeckillStock());
        }

        // 实时重新计算活动状态
        ActivityStatusEnum activityStatusEnum = calculateActivityStatus(
                cachedDetail.getBeginTime(), cachedDetail.getEndTime());
        cachedDetail.setActivityStatus(activityStatusEnum.getStatus());

        return cachedDetail;
    }

    /**
     * 查询秒杀商品详情
     *
     * @param dto
     * @return
     */
    @Override
    public FindSeckillGoodsDetailRspVO findSeckillGoodsDetail(FindSeckillGoodsDetailReqDTO dto) {
        Long activityId = dto.getActivityId();
        Long goodsId = dto.getGoodsId();
        log.info("==> 查询秒杀商品详情, activityId: {}, goodsId: {}", activityId, goodsId);

        // 构建 Redis 缓存 Key
        String redisKey = RedisKeyConstants.GOODS_DETAIL_PREFIX + activityId + ":" + goodsId;

        // L1：先查 Caffeine 本地缓存（微秒级，无网络开销）
        String localCachedValue = goodsDetailLocalCache.getIfPresent(redisKey);
        if (StrUtil.isNotBlank(localCachedValue)) {
            log.info("==> 命中本地缓存（L1），key：{}", redisKey);
            // 手动将 String 字符串，反序列化为商品详情对象，并响应
            return processCachedGoodsDetail(localCachedValue, activityId, goodsId);
        }

        // 第一道防线：布隆过滤器校验活动是否存在
        RBloomFilter<Long> activityBloom = redissonClient.getBloomFilter(RedisKeyConstants.SECKILL_ACTIVITY_BLOOM_KEY);

        if (activityBloom.isExists() && !activityBloom.contains(activityId)) {
            log.info("==> 布隆过滤器拦截：活动不存在, activityId：{}", activityId);
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
        }

        // 第二道防线：布隆过滤器校验活动下的商品是否存在
        RBloomFilter<String> goodsBloom = redissonClient.getBloomFilter(RedisKeyConstants.SECKILL_GOODS_BLOOM_KEY);

        if (goodsBloom.isExists() && !goodsBloom.contains(activityId + ":" + goodsId)) {
            log.info("==> 布隆过滤器拦截：商品不存在, activityId：{}, goodsId：{}", activityId, goodsId);
            throw new BizException(ResultCodeEnum.SECKILL_GOODS_NOT_EXIST);
        }

        // L2：查 Redis 缓存
        String redisJsonValue = stringRedisTemplate.opsForValue().get(redisKey);

        // 若缓存不为空
        if (StrUtil.isNotBlank(redisJsonValue)) {
            log.info("==> 命中商品详情 Redis 缓存（L2），redisKey：{}", redisKey);

            // 防止缓存穿透，判断缓存是否是 NULL
            if (Objects.equals(RedisKeyConstants.NULL_CACHE_VALUE, redisJsonValue)) {
                log.info("==> 命中空值缓存，商品不存在, redisKey: {}", redisKey);
                throw new BizException(ResultCodeEnum.SECKILL_GOODS_NOT_EXIST);
            }

            // 手动将 String 字符串，反序列化为商品详情对象，并响应
            FindSeckillGoodsDetailRspVO rspVO = processCachedGoodsDetail(redisJsonValue, activityId, goodsId);

            // 能走到这里，说明 L1 本地缓存未命中，需要回填，以便后续请求能够命中 L1
            goodsDetailLocalCache.put(redisKey, JsonUtils.toJsonString(rspVO));

            return rspVO;
        }

        // 1. 查询活动信息（不存在直接抛异常）
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (Objects.isNull(activity)) {
            // 缓存空值，防止缓存穿透（攻击者用不存在的 activityId 反复请求）
            cacheNullValue(redisKey);
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
        }

        // 2. 联合查询：活动 ID + 商品 ID
        SeckillGoods seckillGoods = seckillGoodsMapper.selectOne(
                Wrappers.<SeckillGoods>lambdaQuery()
                        .eq(SeckillGoods::getActivityId, activityId)
                        .eq(SeckillGoods::getGoodsId, goodsId)
        );
        if (seckillGoods == null) {
            log.error("==> 秒杀商品不存在, activityId: {}, goodsId: {}", activityId, goodsId);
            // 缓存空值，防止缓存穿透（攻击者用不存在的 goodsId 反复请求）
            cacheNullValue(redisKey);

            throw new BizException(ResultCodeEnum.SECKILL_GOODS_NOT_EXIST);
        }

        // 3. 查询商品信息（获取原价）
        Goods goods = goodsMapper.selectById(seckillGoods.getGoodsId());

        // 4. 根据 goodsId 查询商品轮播图列表，将 List<GoodsImgDO> 转为 List<String>（只保留图片链接）
        List<GoodsImg> goodsImgsDOS = goodsImgMapper.selectList(
                Wrappers.<GoodsImg>lambdaQuery().eq(GoodsImg::getGoodsId, goodsId));
        List<String> goodsImgs = null;
        if (CollUtil.isNotEmpty(goodsImgsDOS)) {
            goodsImgs = goodsImgsDOS.stream()
                    .map(GoodsImg::getImgUrl)
                    .toList();
        }

        // 5. 根据 goodsId 查询商品详情 HTML
        GoodsDetail goodsDetail = goodsDetailMapper.selectOne(
                Wrappers.<GoodsDetail>lambdaQuery().eq(GoodsDetail::getGoodsId, goodsId));

        // 6. 计算活动状态
        ActivityStatusEnum activityStatusEnum = calculateActivityStatus(activity);

        // 7. 组装响应
        FindSeckillGoodsDetailRspVO rspVO = new FindSeckillGoodsDetailRspVO();
        rspVO.setId(seckillGoods.getId());
        rspVO.setGoodsId(goods.getId());
        rspVO.setActivityId(seckillGoods.getActivityId());
        rspVO.setSeckillPrice(seckillGoods.getSeckillPrice());
        rspVO.setSeckillTotal(seckillGoods.getSeckillTotal());
        rspVO.setSeckillStock(seckillGoods.getSeckillStock());
        rspVO.setActivityStatus(activityStatusEnum.getStatus());
        rspVO.setBeginTime(activity.getBeginTime());
        rspVO.setEndTime(activity.getEndTime());
        rspVO.setGoodsImgs(goodsImgs);

        // 设置商品基本信息
        if (Objects.nonNull(goods)) {
            rspVO.setGoodsName(goods.getGoodsName());
            rspVO.setGoodsPrice(goods.getGoodsPrice());
        }

        // 设置商品详情 HTML
        if (Objects.nonNull(goodsDetail)) {
            rspVO.setGoodsDetail(goodsDetail.getDetailContent());
        }

        // 将商品详情写入 Redis 缓存和本地缓存
        log.info("==> 商品详情缓存未命中，将数据写入 Redis 和本地缓存，key：{}", redisKey);
        // 写入本地缓存
        goodsDetailLocalCache.put(redisKey, JsonUtils.toJsonString(rspVO));

        // 将商品详情写入 Redis
        cacheGoodsDetail(redisKey, rspVO);

        return rspVO;
    }

    /**
     * 预热指定活动的商品缓存
     *
     * @param activityId 活动 ID
     * @return 已预热过的商品数量
     */
    @Override
    public int preheatActivityGoods(Long activityId) {
        // 记录开始时间，用于最后打印耗时
        long start = System.currentTimeMillis();
        log.info("==> 开始预热活动商品缓存, activityId: {}", activityId);

        // 1. 查询活动信息（主要为了拿活动结束时间，用于计算动态 TTL）
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (Objects.isNull(activity)) {
            log.info("==> 预热跳过：活动不存在, activityId: {}", activityId);
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
        }

        // 2. 计算动态缓存 TTL：活动剩余时间 + 安全缓冲；返回 null 说明活动早已结束，没有预热价值
        Long ttlSeconds = RedisKeyConstants.calculateTtlSeconds(activity.getEndTime());
        if (Objects.isNull(ttlSeconds) || ttlSeconds <= 0) {
            log.info("==> 预热跳过：活动已结束, activityId: {}", activityId);
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_ENDED);
        }

        // 3. 查询该活动下所有秒杀商品
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(
                Wrappers.<SeckillGoods>lambdaQuery().eq(SeckillGoods::getActivityId, activityId));
        if (CollUtil.isEmpty(seckillGoodsList)) {
            log.info("==> 预热跳过：活动下无商品, activityId: {}", activityId);
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_GOODS_EMPTY);
        }

        // 初始化活动布隆过滤器
        RBloomFilter<Long> activityBloom = redissonClient.getBloomFilter(RedisKeyConstants.SECKILL_ACTIVITY_BLOOM_KEY);
        // 初始化之前，如果之前已经创建了，先删除掉
        activityBloom.delete();
        // 预期插入一万个活动，误判率为 1%
        activityBloom.tryInit(10000L, 0.01);
        // 写入活动 ID
        activityBloom.add(activityId);
        // 设置过期时间，防止布隆过滤器一直占用着 Redis 内存
        redissonClient.getKeys().expire(RedisKeyConstants.SECKILL_ACTIVITY_BLOOM_KEY, 7, TimeUnit.DAYS);

        log.info("==> 活动布隆过滤器写入成功, activityId：{}", activityId);

        // 初始化商品布隆过滤器
        RBloomFilter<String> goodsBloom = redissonClient.getBloomFilter(RedisKeyConstants.SECKILL_GOODS_BLOOM_KEY);
        // 初始化之前，如果之前已经创建过了，先删除掉
        goodsBloom.delete();
        // 预期插入十万个活动，误判率为 1%
        goodsBloom.tryInit(100000L, 0.01);
        // 写入活动下所有商品
        seckillGoodsList.forEach(seckillGoods -> {
            goodsBloom.add(activityId + ":" + seckillGoods.getGoodsId());
        });
        // 设置过期时间，防止布隆过滤器一直占用着 Redis 内存
        redissonClient.getKeys().expire(RedisKeyConstants.SECKILL_GOODS_BLOOM_KEY, 7, TimeUnit.DAYS);

        log.info("==> 商品布隆过滤器写入成功, activityId：{}, 商品数：{}", activityId, seckillGoodsList.size());

        // 4. 批量查询商品信息，转 Map 方便取原价（下面组装详情时复用，避免循环里重复查库）
        List<Long> goodsIds = seckillGoodsList.stream()
                .map(SeckillGoods::getGoodsId)
                .toList();
        Map<Long, Goods> goodsMap = goodsMapper.selectByIds(goodsIds).stream()
                .collect(Collectors.toMap(Goods::getId, goodsDO -> goodsDO));

        // 活动状态只与活动时间有关，循环外算一次即可
        Integer activityStatus = calculateActivityStatus(activity).getStatus();

        // 5. 组装并预热商品列表缓存
        List<FindSeckillGoodsListRspVO> listRspVOS = new ArrayList<>(seckillGoodsList.size());
        for (SeckillGoods seckillGoods : seckillGoodsList) {
            FindSeckillGoodsListRspVO rspVO = FindSeckillGoodsListRspVO.builder()
                    .id(seckillGoods.getId())
                    .goodsId(seckillGoods.getGoodsId())
                    .activityId(seckillGoods.getActivityId())
                    .seckillTitle(seckillGoods.getSeckillTitle())
                    .seckillImg(seckillGoods.getSeckillImg())
                    .seckillPrice(seckillGoods.getSeckillPrice())
                    .seckillTotal(seckillGoods.getSeckillTotal())
                    .seckillStock(seckillGoods.getSeckillStock())
                    .activityStatus(activityStatus)
                    .beginTime(activity.getBeginTime())
                    .endTime(activity.getEndTime())
                    .build();

            // 设置商品原价
            Goods goods = goodsMap.get(seckillGoods.getGoodsId());
            if (Objects.nonNull(goods)) {
                rspVO.setGoodsPrice(goods.getGoodsPrice());
            }

            listRspVOS.add(rspVO);
        }

        // 构建 Redis Key
        String listKey = RedisKeyConstants.GOODS_LIST_PREFIX + activityId;
        stringRedisTemplate.opsForValue()  // 写入 Redis
                .set(listKey, JsonUtils.toJsonString(listRspVOS), ttlSeconds, TimeUnit.SECONDS);
        log.info("==> 预热商品列表缓存成功, key: {}, ttlSeconds: {}", listKey, ttlSeconds);

        // 6. 逐个预热商品详情缓存
        for (SeckillGoods seckillGoods : seckillGoodsList) {
            String detailKey = RedisKeyConstants.GOODS_DETAIL_PREFIX
                    + activityId + ":" + seckillGoods.getGoodsId();

            // 查询商品轮播图（只保留图片链接）
            List<String> goodsImgs = goodsImgMapper.selectList(Wrappers.<GoodsImg>lambdaQuery()
                            .eq(GoodsImg::getGoodsId, seckillGoods.getGoodsId()))
                    .stream()
                    .map(GoodsImg::getImgUrl)
                    .toList();

            // 查询商品详情 HTML
            GoodsDetail goodsDetail = goodsDetailMapper.selectOne(Wrappers.<GoodsDetail>lambdaQuery()
                    .eq(GoodsDetail::getGoodsId, seckillGoods.getGoodsId()));

            // 组装详情 VO
            FindSeckillGoodsDetailRspVO detailVO = FindSeckillGoodsDetailRspVO.builder()
                    .id(seckillGoods.getId())
                    .goodsId(seckillGoods.getGoodsId())
                    .activityId(seckillGoods.getActivityId())
                    .seckillPrice(seckillGoods.getSeckillPrice())
                    .seckillTotal(seckillGoods.getSeckillTotal())
                    .seckillStock(seckillGoods.getSeckillStock())
                    .activityStatus(activityStatus)
                    .beginTime(activity.getBeginTime())
                    .endTime(activity.getEndTime())
                    .goodsImgs(goodsImgs)
                    .build();

            // 设置商品名称与原价（复用第 4 步的 Map，不再重复查库）
            Goods goods = goodsMap.get(seckillGoods.getGoodsId());
            if (Objects.nonNull(goods)) {
                detailVO.setGoodsName(goods.getGoodsName());
                detailVO.setGoodsPrice(goods.getGoodsPrice());
            }

            // 设置商品详情 HTML
            if (Objects.nonNull(goodsDetail)) {
                detailVO.setGoodsDetail(goodsDetail.getDetailContent());
            }

            stringRedisTemplate.opsForValue()
                    .set(detailKey, JsonUtils.toJsonString(detailVO), ttlSeconds, TimeUnit.SECONDS);
        }

        log.info("==> 预热活动商品缓存完成, activityId: {}, 商品数: {}, 耗时: {}ms",
                activityId, seckillGoodsList.size(), System.currentTimeMillis() - start);

        return seckillGoodsList.size();
    }
}
