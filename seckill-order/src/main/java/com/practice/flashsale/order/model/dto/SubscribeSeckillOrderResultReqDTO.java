package com.practice.flashsale.order.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/13 11:40
 * @Version: v1.0.0
 * @Description: 订阅秒杀订单结果入参
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubscribeSeckillOrderResultReqDTO {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}
