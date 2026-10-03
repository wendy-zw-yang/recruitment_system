package com.example.recruitmentsystem;

import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.dto.auth.LoginRequest;
import com.example.recruitmentsystem.dto.auth.RegisterRequest;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.mapper.CompanyMapper;
import com.example.recruitmentsystem.mapper.EmailCodeMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.AuthService;
import com.example.recruitmentsystem.service.impl.AuthServiceImpl;
import com.example.recruitmentsystem.config.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * §1 用户与认证模块单元测试。
 *
 * <p>使用真实 MySQL + schema.sql（test profile 共用 dev DB）。
 * 每个 {@code @Test} 在事务中回滚。</p>
 */
@SpringBootTest
class AuthServiceTest {

    @Autowired private UserMapper userMapper;
    @Autowired private EmailCodeMapper emailCodeMapper;
    @Autowired private CompanyMapper companyMapper;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private com.example.recruitmentsystem.service.MailService mailService;

    /**
     * 用真实依赖组装 AuthService（不用 Spring 注入，避免与 Application 启动顺序耦合）。
     */
    private AuthService authService() {
        return new AuthServiceImpl(userMapper, emailCodeMapper, companyMapper,
                passwordEncoder, jwtUtil, mailService);
    }

    @Test
    @Transactional
    void registerCandidate_success() {
        String email = "candidate-" + System.nanoTime() + "@test.local";
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("password123");

        authService().register(req);

        User saved = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getEmail, email));
        assertNotNull(saved);
        assertEquals("CANDIDATE", saved.getRoleCode());
        assertEquals("ENABLED", saved.getStatus());
        assertNotNull(saved.getPasswordHash());
        assertNotNull(saved.getUsername());
    }

    @Test
    @Transactional
    void register_duplicateEmail_fails() {
        String email = "dup-" + System.nanoTime() + "@test.local";
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("password123");

        authService().register(req);

        RegisterRequest dup = new RegisterRequest();
        dup.setEmail(email);
        dup.setPassword("password456");

        assertThrows(RuntimeException.class, () -> authService().register(dup));
    }

    @Test
    @Transactional
    void login_wrongPassword_fails() {
        String email = "login-" + System.nanoTime() + "@test.local";
        RegisterRequest reg = new RegisterRequest();
        reg.setEmail(email);
        reg.setPassword("password123");
        authService().register(reg);

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("wrong-password");

        assertThrows(RuntimeException.class, () -> authService().login(login));
    }

    @Test
    @Transactional
    void login_success_returnsJwt() {
        String email = "jwt-" + System.nanoTime() + "@test.local";
        RegisterRequest reg = new RegisterRequest();
        reg.setEmail(email);
        reg.setPassword("password123");
        authService().register(reg);

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("password123");

        var resp = authService().login(login);
        assertNotNull(resp.getToken());
        assertNotNull(resp.getUserInfo());
        assertEquals("CANDIDATE", resp.getUserInfo().getRole());
    }

    @Test
    @Transactional
    void sendLoginCode_persistsToDb() {
        String email = "code-" + System.nanoTime() + "@test.local";
        authService().sendLoginCode(email);

        var codes = emailCodeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.recruitmentsystem.entity.EmailCode>()
                        .eq(com.example.recruitmentsystem.entity.EmailCode::getEmail, email)
                        .eq(com.example.recruitmentsystem.entity.EmailCode::getCodeType, "login")
                        .orderByDesc(com.example.recruitmentsystem.entity.EmailCode::getCreatedAt)
                        .last("limit 1"));
        assertEquals(1, codes.size());
        assertEquals(6, codes.get(0).getCode().length());
    }
}
