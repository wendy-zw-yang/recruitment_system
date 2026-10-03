package com.example.recruitmentsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码哈希 Bean。
 *
 * <p>轻量引入 Spring Security Crypto（仅 {@code BCryptPasswordEncoder}），不引入完整 Spring Security。</p>
 */
@Configuration
public class PasswordConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
