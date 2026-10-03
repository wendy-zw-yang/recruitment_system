package com.example.recruitmentsystem.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 JwtFilter + AuthInterceptor + CORS。
 *
 * <p>详见 {@code docs/系统设计/详细设计/基础设施.md §8.7}。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtFilter jwtFilter;
    private final AuthInterceptor authInterceptor;

    /**
     * 前端 Vite dev server 端口（用于 CORS 白名单）。默认 5173（Vite 默认），
     * 个人本地可通过 application-local.yml 覆盖 {@code VITE_PORT} 环境变量。
     */
    @Value("${VITE_PORT:5173}")
    private String vitePort;

    /**
     * 注册拦截器：拦截所有 Controller 方法（除 {@code /error}）。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/error");
    }

    /**
     * 注册 JwtFilter：放在最前（{@code order(0)}），拦截所有请求解析 token。
     */
    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtFilter> reg = new FilterRegistrationBean<>(jwtFilter);
        reg.addUrlPatterns("/*");
        reg.setOrder(0);
        return reg;
    }

    /**
     * CORS：开发态允许 {@code http://localhost:<vitePort>} 与 {@code http://127.0.0.1:<vitePort>}
     * （Vite 两种 host 写法均覆盖）。
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.addAllowedOrigin("http://localhost:" + vitePort);
        cfg.addAllowedOrigin("http://127.0.0.1:" + vitePort);
        cfg.addAllowedMethod("*");
        cfg.addAllowedHeader("*");
        cfg.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return new CorsFilter(source);
    }
}
