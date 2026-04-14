package com.seckill.order.service;

import com.seckill.order.model.dto.UserLoginDTO;
import com.seckill.order.model.dto.UserRegisterDTO;
import com.seckill.order.model.vo.UserVO;

public interface UserService {
    UserVO register(UserRegisterDTO dto);
    UserVO login(UserLoginDTO dto);
}
