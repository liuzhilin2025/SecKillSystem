package com.practice.flashsale.order.controller;

import com.practice.flashsale.common.aspect.ApiOperationLog;
import com.practice.flashsale.common.utils.Result;
import com.practice.flashsale.order.model.dto.DoSeckillReqDTO;
import com.practice.flashsale.order.model.vo.DoSeckillRspVO;
import com.practice.flashsale.order.service.OrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 15:53
 * @Version: v1.0.0
 * @Description: 订单模块
 **/
@RestController
@RequestMapping("/seckill/order")
@Slf4j
public class OrderController {

    @Resource
    private OrderService orderService;

    /**
     * 秒杀下单
     *
     * @param dto
     * @return
     */
    @PostMapping
    @ApiOperationLog(description = "秒杀下单")
    public Result<DoSeckillRspVO> doSeckill(@RequestBody @Validated DoSeckillReqDTO dto) {
        return Result.success(orderService.doSeckill(dto));
    }
}
