package com.practice.flashsale.goods.controller;

import com.practice.flashsale.common.aspect.ApiOperationLog;
import com.practice.flashsale.common.utils.Result;
import com.practice.flashsale.goods.model.dto.FindSeckillGoodsDetailReqDTO;
import com.practice.flashsale.goods.model.dto.FindSeckillGoodsListReqDTO;
import com.practice.flashsale.goods.model.vo.FindSeckillGoodsDetailRspVO;
import com.practice.flashsale.goods.model.vo.FindSeckillGoodsListRspVO;
import com.practice.flashsale.goods.service.GoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 10:47
 * @Version: v1.0.0
 * @Description: 商品模块
 **/
@RestController
@RequestMapping("/seckill/goods")
@Slf4j
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

    /**
     * 查询秒杀商品列表
     *
     * @param dto
     * @return
     */
    @PostMapping("/list")
    @ApiOperationLog(description = "查询秒杀商品列表")
    public Result<List<FindSeckillGoodsListRspVO>> getSeckillGoodsList(@RequestBody @Validated FindSeckillGoodsListReqDTO dto) {
        return Result.success(goodsService.findSeckillGoodsList(dto));
    }

    /**
     * 查询秒杀商品详情
     *
     * @param dto
     * @return
     */
    @PostMapping("/detail")
    @ApiOperationLog(description = "查询秒杀商品详情")
    public Result<FindSeckillGoodsDetailRspVO> getSeckillGoodsDetail(@RequestBody @Validated FindSeckillGoodsDetailReqDTO dto) {
        return Result.success(goodsService.findSeckillGoodsDetail(dto));
    }
}
