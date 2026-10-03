package com.example.recruitmentsystem.config;

import com.example.recruitmentsystem.common.annotation.LoginRequired;
import com.example.recruitmentsystem.common.annotation.RoleAdmin;
import com.example.recruitmentsystem.common.annotation.RoleCandidate;
import com.example.recruitmentsystem.common.annotation.RoleHR;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * 注解式角色拦截器。检查目标方法（或所在类）上的：
 * <ul>
 *   <li>{@link LoginRequired} → 必须已登录</li>
 *   <li>{@link RoleCandidate} / {@link RoleHR} / {@link RoleAdmin} → 必须是对应角色</li>
 * </ul>
 *
 * <p>角色注解隐含登录检查。</p>
 *
 * <p>未通过检查时抛 {@link BusinessException}：</p>
 * <ul>
 *   <li>未登录 → code 401</li>
 *   <li>角色不匹配 → code 403</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        boolean classLogin = hm.getBeanType().isAnnotationPresent(LoginRequired.class);
        boolean methodLogin = hm.hasMethodAnnotation(LoginRequired.class);
        boolean needsLogin = classLogin || methodLogin;

        boolean candidateRole = hasAnyRole(hm, RoleCandidate.class);
        boolean hrRole = hasAnyRole(hm, RoleHR.class);
        boolean adminRole = hasAnyRole(hm, RoleAdmin.class);
        boolean needsRole = candidateRole || hrRole || adminRole;

        if (!needsLogin && !needsRole) {
            return true;
        }

        Long userId = CurrentUserContext.getUserId();
        String role = CurrentUserContext.getRole();

        // 角色注解隐含登录检查
        if (userId == null || role == null) {
            throw new BusinessException(401, "请先登录");
        }

        if (!needsRole) {
            return true;
        }

        boolean roleOk =
                (candidateRole && "CANDIDATE".equals(role)) ||
                (hrRole && "HR".equals(role)) ||
                (adminRole && "ADMIN".equals(role));

        if (!roleOk) {
            throw new BusinessException(403, "无权限访问该资源");
        }
        return true;
    }

    private boolean hasAnyRole(HandlerMethod hm, Class<? extends java.lang.annotation.Annotation> roleClass) {
        return hm.hasMethodAnnotation(roleClass)
                || Arrays.stream(hm.getMethod().getDeclaringClass().getAnnotations())
                        .anyMatch(a -> a.annotationType().equals(roleClass));
    }
}
