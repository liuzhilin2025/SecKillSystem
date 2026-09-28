package com.practice.flashsale.common.service.impl;

import com.practice.flashsale.common.entity.User;
import com.practice.flashsale.common.mapper.UserMapper;
import com.practice.flashsale.common.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author 沃淇淋
 * @since 2026-09-28
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

}
