package com.practice.flashsale.order.service;

import com.practice.flashsale.order.model.dto.DoSeckillReqDTO;
import com.practice.flashsale.order.model.dto.FindSeckillOrderResultReqDTO;
import com.practice.flashsale.order.model.dto.SeckillOrderMqDTO;
import com.practice.flashsale.order.model.vo.DoSeckillRspVO;
import com.practice.flashsale.order.model.vo.FindSeckillOrderResultRspVO;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 14:55
 * @Version: v1.0.0
 * @Description: 订单模块业务
 **/
public interface OrderService {

    /**
     * 秒杀下单
     *
     * @param dto
     * @return
     */
    DoSeckillRspVO doSeckill(DoSeckillReqDTO dto);

    /**
     * 异步消费秒杀下单信息：扣减库存 + 创建订单
     *
     * @param message
     */
    void createSeckillOrder(SeckillOrderMqDTO message);

    /**
     * 查询秒杀订单处理结果
     *
     * @param dto
     * @return
     */
    FindSeckillOrderResultRspVO findSeckillOrderResult(FindSeckillOrderResultReqDTO dto);
}
