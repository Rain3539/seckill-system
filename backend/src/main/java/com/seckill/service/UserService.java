package com.seckill.service;

import com.seckill.model.dto.UserLoginDTO;
import com.seckill.model.dto.UserRegisterDTO;
import com.seckill.model.vo.UserVO;

public interface UserService {
    UserVO register(UserRegisterDTO dto);
    UserVO login(UserLoginDTO dto);
}
