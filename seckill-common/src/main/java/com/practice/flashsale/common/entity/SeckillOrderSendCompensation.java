package com.practice.flashsale.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.*;

/**
 * <p>
 * 秒杀订单发送失败补偿表
 * </p>
 *
 * @author 沃淇淋
 * @since 2026-10-19
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("t_seckill_order_send_compensation")
public class SeckillOrderSendCompensation implements Serializable {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单号（补偿记录的唯一业务标识，防止同一笔订单重复写入表中） */
    private String orderNo;

    private Long userId;

    private Long activityId;

    private Long goodsId;

    private Long seckillGoodsId;

    private Integer status;

    private Integer retryCount;

    private String failReason;

    private LocalDateTime nextRetryTime;

    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String messageBody;
}
