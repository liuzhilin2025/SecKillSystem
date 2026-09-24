package com.practice.flashsale.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置
 */
@Configuration
@MapperScan("com.practice.flashsale.common.mapper")   // 扫描生成的 Mapper 接口
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 乐观锁（第 04 章防超卖会用到：实体字段加 @Version）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 2. 防止全表更新与删除（写漏 where 时的救命稻草）
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // 3. 分页插件 —— 注意必须放在最后
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);   // 单页最大 500 条，防止被恶意请求拉全表
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
