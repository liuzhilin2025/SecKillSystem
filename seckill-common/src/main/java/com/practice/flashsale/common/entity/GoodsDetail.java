package com.practice.flashsale.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 11:44
 * @Version: v1.0.0
 * @Description: 商品详情表 DO
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("t_goods_detail")
public class GoodsDetail {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long goodsId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String detailContent;
}
