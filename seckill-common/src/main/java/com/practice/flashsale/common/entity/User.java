package com.practice.flashsale.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>
 * 用户表
 * </p>
 *
 * @author 沃淇淋
 * @since 2026-09-28
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("t_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户昵称
     */
    @TableField("nickname")
    @NotBlank(message = "用户昵称不能为空")
    private String nickname;

    /**
     * 密码（加密存储）
     */
    @TableField("password")
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 手机号
     */
    @TableField("mobile")
    @NotBlank(message = "手机号不能为空")
    private String mobile;

    /**
     * 头像URL
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 状态（0：禁用 1：启用）
     */
    @TableField("status")
    private Integer status;

    /**
     * 创建时间
     */
    @TableField("create_time")
    @NotNull(message = "创建时间不能为空")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    @NotNull(message = "更新时间不能为空")
    private LocalDateTime updateTime;
}
