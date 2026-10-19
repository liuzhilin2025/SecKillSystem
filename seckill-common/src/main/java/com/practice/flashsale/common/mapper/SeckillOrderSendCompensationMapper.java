package com.practice.flashsale.common.mapper;

import com.practice.flashsale.common.entity.SeckillOrderSendCompensation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;


/**
 * <p>
 * 秒杀订单发送失败补偿表 Mapper 接口
 * </p>
 *
 * @author 沃淇淋
 * @since 2026-10-19
 */
public interface SeckillOrderSendCompensationMapper extends BaseMapper<SeckillOrderSendCompensation> {

    /**
     * 按订单号查询补偿记录（判断是否已登记过）
     */
    @Select("SELECT * FROM t_seckill_order_send_compensation " +
            "WHERE order_no = #{orderNo} AND is_deleted = 0")
    SeckillOrderSendCompensation selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 查询可重发的待重试记录（状态为待重试，且已到达下次重发时间）
     */
    @Select("SELECT * FROM t_seckill_order_send_compensation " +
            "WHERE status = 0 AND is_deleted = 0 AND next_retry_time <= #{now} " +
            "ORDER BY next_retry_time ASC " +
            "LIMIT #{limit}")
    List<SeckillOrderSendCompensation> selectPendingList(@Param("now") LocalDateTime now,
                                                         @Param("limit") int limit);

    /**
     * 按订单号更新补偿状态（标记为已补偿 / 待人工处理）
     *
     * @param orderNo
     * @param status
     * @return
     */
    @Update("UPDATE t_seckill_order_send_compensation " +
            "SET status = #{status}, update_time = NOW() " +
            "WHERE order_no = #{orderNo} AND is_deleted = 0")
    int updateStatusByOrderNo(@Param("orderNo") String orderNo, @Param("status") Integer status);

    /**
     * 按订单号更新重试计划（重试次数 +1、下次重发时间退避拉长、记录失败原因）
     */
    @Update("UPDATE t_seckill_order_send_compensation " +
            "SET retry_count = #{retryCount}, next_retry_time = #{nextRetryTime}, " +
            "fail_reason = #{failReason}, update_time = NOW() " +
            "WHERE order_no = #{orderNo} AND is_deleted = 0")
    int updateRetryPlan(@Param("orderNo") String orderNo,
                        @Param("retryCount") Integer retryCount,
                        @Param("nextRetryTime") LocalDateTime nextRetryTime,
                        @Param("failReason") String failReason);
}

