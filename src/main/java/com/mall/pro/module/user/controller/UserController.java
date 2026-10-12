package com.mall.pro.module.user.controller;

import com.mall.pro.common.Result;
import com.mall.pro.module.user.dto.LoginResponseDTO;
import com.mall.pro.module.user.dto.UserAuthDTO;
import com.mall.pro.module.user.entity.User;
import com.mall.pro.module.user.entity.UserAttendee;
import com.mall.pro.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor

public class UserController {
    private final UserService userService;

    // 1. 用户注册
    @PostMapping("/register")
    public Result<User> register(@RequestBody UserAuthDTO dto) {
        return Result.success(userService.register(dto));
    }

    // 2. 用户登录 (返回 JWT 令牌)
    @PostMapping("/login")
    public Result<LoginResponseDTO> login(@RequestBody UserAuthDTO dto) {
        return Result.success(userService.login(dto));
    }

    // 3. 查询当前登录人个人资料 (免参：直接从 UserContext 读取)
    @GetMapping("/profile")
    public Result<User> getProfile() {
        return Result.success(userService.getCurrentProfile());
    }

    // 4. 添加常用实名观演人
    @PostMapping("/attendee/add")
    public Result<UserAttendee> addAttendee(
            @RequestParam("realName") String realName,
            @RequestParam("idCard") String idCard,
            @RequestParam(value = "phone", required = false) String phone) {
        return Result.success(userService.addAttendee(realName, idCard, phone));
    }

    // 5. 查询当前用户的常用观演人列表
    @GetMapping("/attendee/list")
    public Result<List<UserAttendee>> listAttendees() {
        return Result.success(userService.listAttendees());
    }
}
