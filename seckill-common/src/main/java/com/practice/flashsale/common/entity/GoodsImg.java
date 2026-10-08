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
 * @Date: 2026/10/8 11:41
 * @Version: v1.0.0
 * @Description: 商品轮播图表 DO
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("t_goods_img")
public class GoodsImg {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long goodsId;

    private String imgUrl;

    private Integer sort;

    private LocalDateTime createTime;
}
