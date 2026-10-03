package com.example.recruitmentsystem.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应包装。用于所有列表接口的 {@code data} 字段。
 *
 * <p>约定参数命名：{@code pageNum}（1 基）、{@code pageSize}。
 * 由前端 axios 拦截器自动注入，详见 {@code docs/系统设计/功能模块设计.md §0.5}。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private List<T> list;
}
