package com.practice.flashsale.user.model.vo;

import cn.hutool.system.UserInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginUserRspVO {

    /**
     * Token 令牌
     */
    private String token;

    /**
     * 用户信息
     */
    private UserInfo userInfo;

    /**
     * 用户信息内部类
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class UserInfo {

        /**
         * 用户 ID
         */
        private Long id;

        /**
         * 昵称
         */
        private String nickname;

        /**
         * 头像
         */
        private String avatar;
    }
}
