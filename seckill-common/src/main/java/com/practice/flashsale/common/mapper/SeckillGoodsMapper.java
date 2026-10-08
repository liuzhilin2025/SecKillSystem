package com.practice.flashsale.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.practice.flashsale.common.entity.SeckillGoods;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface SeckillGoodsMapper extends BaseMapper<SeckillGoods> {

    /**
     * 扣减秒杀库存
     * <p>
     * 关键：把"判断 stock > 0" 和 "扣减 stock - 1" 放进同一条 SQL，
     * 由 InnoDB 的行锁保证原子性。
     *
     * @param id 秒杀商品 ID
     * @return 影响行数：1=扣减成功，0=库存不足
     */
    @Update("UPDATE t_seckill_goods " +
            "SET seckill_stock = seckill_stock - 1 " +
            "WHERE id = #{id} AND seckill_stock > 0")
    int deductStock(@Param("id") Long id);
}
