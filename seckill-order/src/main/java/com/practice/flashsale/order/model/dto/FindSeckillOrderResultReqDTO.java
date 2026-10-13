package com.practice.flashsale.order.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/13 09:06
 * @Version: v1.0.0
 * @Description: 查询秒杀订单结果
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindSeckillOrderResultReqDTO {

    /**
     * 订单号
     */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}
