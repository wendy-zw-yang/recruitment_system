package com.example.recruitmentsystem.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法 / 类仅 HR（{@code role=HR}）可访问。
 *
 * <p>隐含 {@link LoginRequired} 检查。求职者 / 管理员调用会抛 403。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RoleHR {
}
