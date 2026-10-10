package com.practice.flashsale.goods.controller;

import com.practice.flashsale.common.aspect.ApiOperationLog;
import com.practice.flashsale.common.utils.Result;
import com.practice.flashsale.goods.model.dto.PreheatActivityCacheReqDTO;
import com.practice.flashsale.goods.service.GoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/9
 * @Version: v1.0.0
 * @Description: 商品模块管理端
 **/
@RestController
@RequestMapping("/admin/seckill/goods")
@Slf4j
@RequiredArgsConstructor
public class AdminGoodsController {

    private final GoodsService goodsService;

    /**
     * 手动预热指定活动的商品缓存
     *
     * @param reqDTO 入参（活动 ID）
     * @return 预热结果（已预热过的商品数量）
     */
    @PostMapping("/cache/preheat")
    @ApiOperationLog(description = "手动预热商品缓存")
    public Result<Integer> preheatActivityGoods(@RequestBody @Validated PreheatActivityCacheReqDTO reqDTO) {
        return Result.success(goodsService.preheatActivityGoods(reqDTO.getActivityId()));
    }
}
