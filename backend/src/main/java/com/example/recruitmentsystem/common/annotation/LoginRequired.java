package com.example.recruitmentsystem.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法 / 类需要登录（任意角色）。
 *
 * <p>由 {@link com.example.recruitmentsystem.config.AuthInterceptor} 检查；
 * 未登录时抛 {@link com.example.recruitmentsystem.common.exception.BusinessException}(401)。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginRequired {
}
