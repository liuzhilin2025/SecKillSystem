package com.practice.flashsale.order.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.practice.flashsale.common.aspect.ApiOperationLog;
import com.practice.flashsale.common.utils.Result;
import com.practice.flashsale.order.model.dto.DoSeckillReqDTO;
import com.practice.flashsale.order.model.dto.FindSeckillOrderResultReqDTO;
import com.practice.flashsale.order.model.dto.SubscribeSeckillOrderResultReqDTO;
import com.practice.flashsale.order.model.vo.DoSeckillRspVO;
import com.practice.flashsale.order.model.vo.FindSeckillOrderResultRspVO;
import com.practice.flashsale.order.service.OrderService;
import com.practice.flashsale.order.service.SeckillOrderResultNotifyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @Resource
    private SeckillOrderResultNotifyService seckillOrderResultNotifyService;

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

    /**
     * 查询秒杀订单处理结果
     *
     * @param reqDTO
     * @return
     */
    @PostMapping("/result")
    @ApiOperationLog(description = "查询秒杀订单处理结果")
    public Result<FindSeckillOrderResultRspVO> findSeckillOrderResult(@RequestBody @Validated FindSeckillOrderResultReqDTO reqDTO) {
        return Result.success(orderService.findSeckillOrderResult(reqDTO));
    }

    /**
     * 订阅秒杀订单处理结果
     *
     * @param reqDTO
     * @return
     */
    // produces 表示这个接口返回给客户端的数据类型
    @PostMapping(value = "/result/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperationLog(description = "订阅秒杀订单处理结果")
    public SseEmitter subscribeSeckillOrderResult(@RequestBody @Validated SubscribeSeckillOrderResultReqDTO reqDTO) {
        // 获取当前登录用户 ID
        long userId = StpUtil.getLoginIdAsLong();
        return seckillOrderResultNotifyService.subscribe(userId, reqDTO.getOrderNo());
    }
}
