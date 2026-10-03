package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 求职者收藏职位（联合唯一）。
 *
 * <p>对应表：{@code favorite_job}。</p>
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

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
