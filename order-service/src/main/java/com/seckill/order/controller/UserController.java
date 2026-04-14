package com.seckill.order.controller;

import com.seckill.order.model.dto.UserLoginDTO;
import com.seckill.order.model.dto.UserRegisterDTO;
import com.seckill.order.model.vo.ResultVO;
import com.seckill.order.model.vo.UserVO;
import com.seckill.order.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResultVO<UserVO> register(@Valid @RequestBody UserRegisterDTO dto) {
        return ResultVO.success(userService.register(dto));
    }

    @PostMapping("/login")
    public ResultVO<UserVO> login(@Valid @RequestBody UserLoginDTO dto) {
        return ResultVO.success(userService.login(dto));
    }
}
