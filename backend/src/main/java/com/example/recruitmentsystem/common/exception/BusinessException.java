package com.example.recruitmentsystem.common.exception;

/**
 * 业务异常。所有 Service 层抛出的业务级错误必须用本类，
 * 由 {@link com.example.recruitmentsystem.common.handler.GlobalExceptionHandler} 统一转为 {@code Result.fail(code, message)}。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>code 默认 400（业务校验失败）</li>
 *   <li>401 / 403 由拦截器层抛，仍可用本类（HTTP 状态由拦截器决定）</li>
 *   <li>catch 后必须处理：要么抛、要么记日志，**禁止吞异常**（AGENTS.md §2.2）</li>
 * </ul>
 */
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
