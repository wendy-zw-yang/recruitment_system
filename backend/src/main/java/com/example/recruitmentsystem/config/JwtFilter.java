package com.example.recruitmentsystem.config;

import com.example.recruitmentsystem.common.context.CurrentUserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 解析 {@code Authorization: Bearer <token>} 头，写入 {@link CurrentUserContext}（ThreadLocal）。
 *
 * <p>由 Spring Boot Filter Chain 注册（详见 {@link WebConfig}），<strong>在拦截器之前</strong>。</p>
 *
 * <p>行为：</p>
 * <ul>
 *   <li>无 token 或 token 为空 → 跳过（由 AuthInterceptor 决定是否抛 401）</li>
 *   <li>token 格式错误 / 过期 → 跳过 + 记日志（AuthInterceptor 抛 401）</li>
 *   <li>token 合法 → 写入 ThreadLocal，{@code afterCompletion} 时清理</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            try {
                Claims claims = jwtUtil.parse(token);
                Long userId = jwtUtil.getUserId(claims);
                String role = jwtUtil.getRole(claims);
                CurrentUserContext.set(userId, role);
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("JWT 解析失败：{}", e.getMessage());
                // 不抛异常，让拦截器按"未登录"处理
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }
}
