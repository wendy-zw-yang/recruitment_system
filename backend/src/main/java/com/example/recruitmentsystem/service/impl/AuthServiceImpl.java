package com.example.recruitmentsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.config.JwtUtil;
import com.example.recruitmentsystem.dto.auth.CodeLoginRequest;
import com.example.recruitmentsystem.dto.auth.HrRegisterRequest;
import com.example.recruitmentsystem.dto.auth.LoginRequest;
import com.example.recruitmentsystem.dto.auth.LoginResponse;
import com.example.recruitmentsystem.dto.auth.RegisterRequest;
import com.example.recruitmentsystem.entity.Company;
import com.example.recruitmentsystem.entity.EmailCode;
import com.example.recruitmentsystem.entity.User;
import com.example.recruitmentsystem.mapper.CompanyMapper;
import com.example.recruitmentsystem.mapper.EmailCodeMapper;
import com.example.recruitmentsystem.mapper.UserMapper;
import com.example.recruitmentsystem.service.AuthService;
import com.example.recruitmentsystem.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * AuthService 实现。详见 {@code docs/系统设计/详细设计/用户与认证.md §1.3}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final EmailCodeMapper emailCodeMapper;
    private final CompanyMapper companyMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final MailService mailService;

    private static final int CODE_EXPIRE_MINUTES = 5;
    private static final int SEND_CODE_THROTTLE_SECONDS = 60;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        validateEmailAvailable(request.getEmail());
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoleCode("CANDIDATE");
        user.setStatus("ENABLED");
        user.setUsername(request.getEmail().split("@")[0]); // 默认昵称
        userMapper.insert(user);
    }

    @Override
    @Transactional
    public void registerHr(HrRegisterRequest request) {
        validateEmailAvailable(request.getEmail());
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoleCode("HR");
        user.setStatus("ENABLED");
        user.setUsername(request.getEmail().split("@")[0]);
        userMapper.insert(user);

        // 同步创建 company（PENDING 状态）
        Company company = new Company();
        company.setHrUserId(user.getId());
        company.setName(request.getCompanyName());
        company.setScale(request.getScale());
        company.setDescription(request.getDescription());
        company.setAuthStatus("PENDING");
        companyMapper.insert(company);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user;
        try {
            user = findEnabledUserByEmail(request.getEmail());
        } catch (BusinessException e) {
            log.info("[AuthService.login] 登录失败 email={} reason={}", request.getEmail(), e.getMessage());
            throw e;
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.info("[AuthService.login] 密码不匹配 email={}", request.getEmail());
            throw new BusinessException(401, "邮箱或密码错误");
        }

        try {
            return buildLoginResponse(user);
        } catch (Exception e) {
            log.error("[AuthService.login] 签发 JWT 失败 email={} userId={}", request.getEmail(), user.getId(), e);
            throw new BusinessException(500, "登录失败：令牌签发出错");
        }
    }

    @Override
    public void sendLoginCode(String email) {
        // 60 秒内同邮箱限 1 次
        LambdaQueryWrapper<EmailCode> throttle = new LambdaQueryWrapper<>();
        throttle.eq(EmailCode::getEmail, email)
                .eq(EmailCode::getCodeType, "login")
                .orderByDesc(EmailCode::getCreatedAt)
                .last("limit 1");
        EmailCode recent = emailCodeMapper.selectOne(throttle);
        if (recent != null && recent.getCreatedAt().plusSeconds(SEND_CODE_THROTTLE_SECONDS).isAfter(LocalDateTime.now())) {
            throw new BusinessException(400, "请稍后再试");
        }

        String code = String.format("%06d", new Random().nextInt(1_000_000));
        EmailCode ec = new EmailCode();
        ec.setEmail(email);
        ec.setCode(code);
        ec.setCodeType("login");
        ec.setUsed(0);
        ec.setExpireTime(LocalDateTime.now().plusMinutes(CODE_EXPIRE_MINUTES));
        emailCodeMapper.insert(ec);

        mailService.send(email, "登录验证码", "您的登录验证码为：" + code + "（5 分钟内有效）");
    }

    @Override
    @Transactional
    public LoginResponse codeLogin(CodeLoginRequest request) {
        LambdaQueryWrapper<EmailCode> q = new LambdaQueryWrapper<>();
        q.eq(EmailCode::getEmail, request.getEmail())
                .eq(EmailCode::getCodeType, "login")
                .eq(EmailCode::getCode, request.getCode())
                .orderByDesc(EmailCode::getCreatedAt)
                .last("limit 1");
        EmailCode ec = emailCodeMapper.selectOne(q);

        if (ec == null) {
            throw new BusinessException(400, "验证码错误");
        }
        if (ec.getUsed() != null && ec.getUsed() == 1) {
            throw new BusinessException(400, "验证码已使用");
        }
        if (ec.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "验证码已过期");
        }

        // 标记已使用
        ec.setUsed(1);
        emailCodeMapper.updateById(ec);

        User user = findEnabledUserByEmail(request.getEmail());
        return buildLoginResponse(user);
    }

    // ============ 私有方法 ============

    private void validateEmailAvailable(String email) {
        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<>();
        q.eq(User::getEmail, email);
        if (userMapper.selectCount(q) > 0) {
            throw new BusinessException(400, "该邮箱已注册");
        }
    }

    private User findEnabledUserByEmail(String email) {
        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<>();
        q.eq(User::getEmail, email);
        User user = userMapper.selectOne(q);
        if (user == null) {
            throw new BusinessException(401, "邮箱或密码错误");
        }
        if (!"ENABLED".equals(user.getStatus())) {
            throw new BusinessException(401, "账号已被禁用");
        }
        return user;
    }

    private LoginResponse buildLoginResponse(User user) {
        String token = jwtUtil.sign(user.getId(), user.getRoleCode());
        LoginResponse.UserInfo info = new LoginResponse.UserInfo(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getRoleCode());
        return new LoginResponse(token, info);
    }
}
