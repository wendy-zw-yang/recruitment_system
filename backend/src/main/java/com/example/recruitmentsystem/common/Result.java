package com.example.recruitmentsystem.common;

import lombok.Data;

/**
 * 全局统一响应包装。所有 Controller 方法返回 {@code Result<T>} 而非裸对象。
 *
 * <p>HTTP 状态码语义（与 Spring {@code @ResponseStatus} 配合）：</p>
 * <ul>
 *   <li>200 — 业务成功（即使 code = 200，HTTP 仍为 200）</li>
 *   <li>400 — 业务校验失败（{@code BusinessException} 默认 code = 400）</li>
 *   <li>401 — 未登录（拦截器层抛）</li>
 *   <li>403 — 无权限（拦截器层抛）</li>
 *   <li>500 — 系统异常（兜底）</li>
 * </ul>
 *
 * <p>详见 {@code docs/系统设计/功能模块设计.md §0.4} 统一响应格式约定。</p>
 */
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> success(String message, T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    public static <T> Result<T> fail(String message) {
        Result<T> r = new Result<>();
        r.setCode(400);
        r.setMessage(message);
        r.setData(null);
        return r;
    }

    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(null);
        return r;
    }
}
