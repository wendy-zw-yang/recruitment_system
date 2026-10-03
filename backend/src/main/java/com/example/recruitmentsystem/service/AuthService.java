package com.example.recruitmentsystem.service;

import com.example.recruitmentsystem.dto.auth.CodeLoginRequest;
import com.example.recruitmentsystem.dto.auth.HrRegisterRequest;
import com.example.recruitmentsystem.dto.auth.LoginRequest;
import com.example.recruitmentsystem.dto.auth.LoginResponse;
import com.example.recruitmentsystem.dto.auth.RegisterRequest;

/**
 * 用户与认证业务接口。
 *
 * <p>覆盖 UC-01（求职者注册）、UC-02（HR 注册）、UC-03（密码登录）、UC-04（验证码登录）。</p>
 */
public interface AuthService {

    /** UC-01 求职者注册 */
    void register(RegisterRequest request);

    /** UC-02 HR 注册（同步创建 company） */
    void registerHr(HrRegisterRequest request);

    /** UC-03 密码登录 */
    LoginResponse login(LoginRequest request);

    /** 发送登录验证码（开发态打印到 console） */
    void sendLoginCode(String email);

    /** UC-04 验证码登录 */
    LoginResponse codeLogin(CodeLoginRequest request);
}
