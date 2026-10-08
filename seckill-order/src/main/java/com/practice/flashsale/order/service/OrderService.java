package com.practice.flashsale.order.service;

import com.practice.flashsale.order.model.dto.DoSeckillReqDTO;
import com.practice.flashsale.order.model.vo.DoSeckillRspVO;

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
}
