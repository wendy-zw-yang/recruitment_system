package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 求职者收藏职位（联合唯一）。
 *
 * <p>对应表：{@code favorite_job}。</p>
 *
 * <p>v0.5 修订：移除 {@code @TableLogic}。原因：表上有 UNIQUE 约束
 * {@code uk_favorite_candidate_job(candidate_id, job_id)}，软删除后行依然存在，
 * 再次 INSERT 同 (candidate_id, job_id) 会触发 UNIQUE 冲突 → 500。
 * 收藏 toggle 是破坏性操作，无需保留审计行，直接硬删除即可。</p>
 */
@Data
@TableName("favorite_job")
public class FavoriteJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long candidateId;

    private Long jobId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
