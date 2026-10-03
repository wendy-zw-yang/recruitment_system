package com.example.recruitmentsystem.common.context;

/**
 * 当前请求线程的用户上下文。基于 {@link ThreadLocal} 持有 {@code userId} 与 {@code role}。
 *
 * <p>由 {@link com.example.recruitmentsystem.config.JwtFilter} 写入；</p>
 * <p>由 {@link com.example.recruitmentsystem.config.AuthInterceptor} 校验角色；</p>
 * <p>由 {@link com.example.recruitmentsystem.config.JwtFilter#afterCompletion}（或拦截器 afterCompletion）清理。</p>
 *
 * <p>当前请求线程外的任何调用（如 {@code @Async} 异步任务）应通过参数显式传递 userId，<strong>不能依赖 ThreadLocal</strong>。</p>
 */
public final class CurrentUserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(Long userId, String role) {
        USER_ID.set(userId);
        ROLE.set(role);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static String getRole() {
        return ROLE.get();
    }

    public static void clear() {
        USER_ID.remove();
        ROLE.remove();
    }
}
