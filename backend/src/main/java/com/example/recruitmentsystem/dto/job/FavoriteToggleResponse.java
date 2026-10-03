package com.example.recruitmentsystem.dto.job;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 收藏 toggle 响应：返回当前收藏状态。
 */
@Data
@AllArgsConstructor
public class FavoriteToggleResponse {

    private Long jobId;

    private Boolean favorited;
}
