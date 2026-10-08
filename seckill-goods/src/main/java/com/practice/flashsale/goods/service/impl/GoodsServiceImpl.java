package com.practice.flashsale.goods.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.practice.flashsale.common.entity.*;
import com.practice.flashsale.common.enums.ActivityStatusEnum;
import com.practice.flashsale.common.enums.ResultCodeEnum;
import com.practice.flashsale.common.exception.BizException;
import com.practice.flashsale.common.mapper.*;
import com.practice.flashsale.goods.model.dto.FindSeckillGoodsDetailReqDTO;
import com.practice.flashsale.goods.model.dto.FindSeckillGoodsListReqDTO;
import com.practice.flashsale.goods.model.vo.FindSeckillGoodsDetailRspVO;
import com.practice.flashsale.goods.service.GoodsService;
import com.practice.flashsale.goods.model.vo.FindSeckillGoodsListRspVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
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

        // 1. 查询活动信息
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            log.error("==> 查询秒杀商品列表, activityId: {}, 活动信息不存在", activityId);
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
        for (SeckillGoods goods : seckillGoods) {
            FindSeckillGoodsListRspVO rspVO = new FindSeckillGoodsListRspVO();
            rspVO.setId(goods.getId());
            rspVO.setGoodsId(goods.getGoodsId());
            rspVO.setActivityId(goods.getActivityId());
            rspVO.setSeckillTitle(goods.getSeckillTitle());
            rspVO.setSeckillImg(goods.getSeckillImg());
            rspVO.setSeckillPrice(goods.getSeckillPrice());
            rspVO.setSeckillTotal(goods.getSeckillTotal());
            rspVO.setSeckillStock(goods.getSeckillStock());
            rspVO.setActivityStatus(activityStatusEnum.getStatus());
            rspVO.setBeginTime(activity.getBeginTime());
            rspVO.setEndTime(activity.getEndTime());

            // 设置商品原价
            Goods goodsDO = goodsMap.get(goods.getGoodsId());
            if (Objects.nonNull(goodsDO)) {
                rspVO.setGoodsPrice(goodsDO.getGoodsPrice());
            }

            rspVOS.add(rspVO);
        }

        return rspVOS;
    }

    /**
     * 根据当前时间动态计算活动状态
     *
     * @param activity
     * @return
     */
    private ActivityStatusEnum calculateActivityStatus(SeckillActivity activity) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getBeginTime())) { // 当前时间早于活动开始时间，则活动未开始
            return ActivityStatusEnum.NOT_STARTED;
        } else if (now.isAfter(activity.getEndTime())) { // 当前时间晚于活动结束时间，则活动已结束
            return ActivityStatusEnum.ENDED;
        } else { // 活动进行中
            return ActivityStatusEnum.ING;
        }
    }

    /**
     * 查询活动，不存在则抛业务异常
     */
    private SeckillActivity getActivityOrThrow(Long activityId) {
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            log.error("===> 秒杀活动不存在, activityId: {}", activityId);
            throw new BizException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
        }
        return activity;
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

        // 1. 查询活动信息（不存在直接抛异常）
        SeckillActivity activity = getActivityOrThrow(activityId);

        // 2. 联合查询：活动 ID + 商品 ID
        SeckillGoods seckillGoods = seckillGoodsMapper.selectOne(
                Wrappers.<SeckillGoods>lambdaQuery()
                        .eq(SeckillGoods::getActivityId, activityId)
                        .eq(SeckillGoods::getGoodsId, goodsId)
        );
        if (seckillGoods == null) {
            log.error("==> 秒杀商品不存在, activityId: {}, goodsId: {}", activityId, goodsId);
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

        return rspVO;
    }
}
