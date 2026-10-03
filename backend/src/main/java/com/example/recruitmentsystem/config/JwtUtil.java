package com.example.recruitmentsystem.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析。
 *
 * <p>Claims 结构：</p>
 * <ul>
 *   <li>{@code sub} — userId（Long）</li>
 *   <li>{@code role} — CANDIDATE / HR / ADMIN</li>
 *   <li>{@code exp} — 过期时间戳</li>
 * </ul>
 *
 * <p>密钥 / 过期时间从 {@code application.yml} 的 {@code jwt.secret} / {@code jwt.expire-hours} 读取。</p>
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-hours}") Integer expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 60L * 60L * 1000L;
    }

    /**
     * 签发 token。
     */
    public String sign(Long userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token。失败时抛 {@link RuntimeException}（由 JwtFilter 捕获转 401）。
     */
    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        return jws.getPayload();
    }

    /**
     * 从 Claims 中取 userId（{@code sub}）。
     */
    public Long getUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Claims 中取 role。
     */
    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }
}
