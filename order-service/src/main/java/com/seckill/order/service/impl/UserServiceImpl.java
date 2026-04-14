package com.seckill.order.service.impl;

import com.seckill.order.datasource.DS;
import com.seckill.order.datasource.DataSourceType;
import com.seckill.order.mapper.UserMapper;
import com.seckill.order.model.dto.UserLoginDTO;
import com.seckill.order.model.dto.UserRegisterDTO;
import com.seckill.order.model.entity.User;
import com.seckill.order.model.vo.UserVO;
import com.seckill.order.service.UserService;
import com.seckill.order.utils.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(UserMapper userMapper, JwtUtils jwtUtils) {
        this.userMapper = userMapper;
        this.jwtUtils = jwtUtils;
    }

    @Override
    @DS(DataSourceType.MASTER)
    @Transactional
    public UserVO register(UserRegisterDTO dto) {
        if (userMapper.findByUsername(dto.getUsername()) != null) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        userMapper.insert(user);
        log.info("register({}) -> MASTER DB", dto.getUsername());
        return buildUserVO(user);
    }

    @Override
    @DS(DataSourceType.MASTER)
    public UserVO login(UserLoginDTO dto) {
        User user = userMapper.findByUsername(dto.getUsername());
        if (user == null) throw new RuntimeException("用户不存在");
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) throw new RuntimeException("密码错误");
        if (user.getStatus() != 1) throw new RuntimeException("账号已被禁用");
        return buildUserVO(user);
    }

    private UserVO buildUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setToken(jwtUtils.generateToken(user.getId(), user.getUsername()));
        return vo;
    }
}
