package com.practice.flashsale.app;
import com.practice.flashsale.common.entity.User;
import com.practice.flashsale.common.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;


@SpringBootTest
class UserTests {

    @Resource
    private UserMapper userMapper;


    /**
     * 添加一条用户记录
     */
    @Test
    void testInsertUser() {
        userMapper.insert(User.builder()
                .nickname("犬小哈")
                .password("123456")
                .mobile("18019988888")
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build());
    }

}

