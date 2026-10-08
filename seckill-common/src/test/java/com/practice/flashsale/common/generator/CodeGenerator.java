package com.practice.flashsale.common.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.DbColumnType;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.sql.Types;
import java.util.Collections;

/**
 * MyBatis-Plus 代码生成器
 * 直接把 main 方法跑一遍即可，生成完这个类可以留着备用
 */
public class CodeGenerator {

    /** 数据库连接 */
    private static final String URL =
            "jdbc:mysql://127.0.0.1:3306/seckill"
                    + "?useUnicode=true&characterEncoding=utf8"
                    + "&serverTimezone=Asia/Shanghai"
                    + "&useSSL=false&allowPublicKeyRetrieval=true"
                    + "&remarks=true&useInformationSchema=true";   // 这两个参数让生成器能读到表注释
    private static final String USERNAME = "root";
    private static final String PASSWORD = "AdS27KHe5dwB";

    /** 输出目录（路径写死最简单，按你的实际路径改） */
    private static final String JAVA_DIR =
            "D:/java_practice/FlashSaleSystem/seckill-common/src/main/java";
    private static final String XML_DIR =
            "D:/java_practice/FlashSaleSystem/seckill-common/src/main/java/mapper";

    public static void main(String[] args) {
        FastAutoGenerator.create(URL, USERNAME, PASSWORD)

                // ---------- 全局配置 ----------
                .globalConfig(builder -> builder
                        .author("沃淇淋")
                        .outputDir(JAVA_DIR)
                        .commentDate("yyyy-MM-dd")
                        .disableOpenDir()                 // 生成完不自动弹目录
                )

                // ---------- 数据源配置 ----------
                .dataSourceConfig(builder -> builder
                        .typeConvertHandler((globalConfig, typeRegistry, metaInfo) -> {
                            // MySQL tinyint 默认会转成 Boolean/Byte，统一转成 Integer 更好用
                            if (metaInfo.getJdbcType().TYPE_CODE == Types.TINYINT) {
                                return DbColumnType.INTEGER;
                            }
                            return typeRegistry.getColumnType(metaInfo);
                        })
                )

                // ---------- 包配置 ----------
                .packageConfig(builder -> builder
                        .parent("com.practice.flashsale.common")
                        .entity("entity")
                        .mapper("mapper")
                        .xml("mapper")
                        .pathInfo(Collections.singletonMap(OutputFile.xml, XML_DIR))
                )

                // ---------- 策略配置 ----------
                .strategyConfig(builder -> builder
                                // 要生成的表
                                .addInclude("t_user")
                                // 生成时去掉表前缀：t_user -> User
                                .addTablePrefix("t_")

                                // ===== 实体类策略 =====
                                .entityBuilder()
                                .enableLombok()                       // 用 @Data 代替 getter/setter
                                .enableTableFieldAnnotation()         // 生成 @TableField 注解
                                .logicDeleteColumnName("deleted")     // 有逻辑删除字段就启用
                                .versionColumnName("version")         // 有乐观锁字段就启用

                                // ===== Mapper 策略 =====
                                .mapperBuilder()
                                .enableBaseResultMap()
                                .enableBaseColumnList()
                                .formatMapperFileName("%sMapper")
                                .formatXmlFileName("%sMapper")

                                // ===== Service 策略 =====
                                .serviceBuilder()
                                .formatServiceFileName("%sService")
                                .formatServiceImplFileName("%sServiceImpl")

                        // ===== Controller 策略（本项目先不生成，需要时把注释打开） =====
                        // .controllerBuilder().disable()
                )

                // 模板引擎（默认 Velocity，这里用 Freemarker）
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }
}
