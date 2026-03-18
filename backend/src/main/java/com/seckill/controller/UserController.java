package com.seckill.controller;

import com.seckill.model.dto.UserLoginDTO;
import com.seckill.model.dto.UserRegisterDTO;
import com.seckill.model.vo.ResultVO;
import com.seckill.model.vo.UserVO;
import com.seckill.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户注册
     * POST /api/user/register
     */
    @PostMapping("/register")
    public ResultVO<UserVO> register(@Valid @RequestBody UserRegisterDTO dto) {
        UserVO vo = userService.register(dto);
        return ResultVO.success(vo);
    }

    /**
     * 用户登录
     * POST /api/user/login
     */
    @PostMapping("/login")
    public ResultVO<UserVO> login(@Valid @RequestBody UserLoginDTO dto) {
        UserVO vo = userService.login(dto);
        return ResultVO.success(vo);
    }
}
