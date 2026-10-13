package com.practice.flashsale.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.practice.flashsale.common.entity.SeckillOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {

    /**
     * 根据订单号和用户ID查询订单（用于幂等性校验/防重复下单）
     */
    @Select("SELECT * FROM t_seckill_order " +
            "WHERE order_no = #{orderNo} " +
            "AND user_id = #{userId} " +
            "AND is_deleted = 0 " +
            "LIMIT 1")
    SeckillOrder selectByOrderNoAndUserId(@Param("orderNo") String orderNo,
                                          @Param("userId") Long userId);
}
