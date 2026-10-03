package com.example.recruitmentsystem.controller.auth;

import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.dto.auth.CodeLoginRequest;
import com.example.recruitmentsystem.dto.auth.HrRegisterRequest;
import com.example.recruitmentsystem.dto.auth.LoginRequest;
import com.example.recruitmentsystem.dto.auth.LoginResponse;
import com.example.recruitmentsystem.dto.auth.RegisterRequest;
import com.example.recruitmentsystem.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证端点（无需登录）。详见 {@code docs/系统设计/详细设计/用户与认证.md §1.2}。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** UC-01 求职者注册 */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success("注册成功", null);
    }

    /** UC-02 HR 注册 */
    @PostMapping("/register-hr")
    public Result<Void> registerHr(@Valid @RequestBody HrRegisterRequest request) {
        authService.registerHr(request);
        return Result.success("注册成功", null);
    }

    /** UC-03 密码登录 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success("登录成功", authService.login(request));
    }

    /** 发送登录验证码 */
    @PostMapping("/send-login-code")
    public Result<Void> sendLoginCode(@Valid @RequestBody LoginRequest request) {
        authService.sendLoginCode(request.getEmail());
        return Result.success("验证码已生成，请查看控制台输出", null);
    }

    /** UC-04 验证码登录 */
    @PostMapping("/code-login")
    public Result<LoginResponse> codeLogin(@Valid @RequestBody CodeLoginRequest request) {
        return Result.success("登录成功", authService.codeLogin(request));
    }
}
