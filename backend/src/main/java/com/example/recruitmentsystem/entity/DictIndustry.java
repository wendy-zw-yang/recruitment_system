package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行业字典（一级 + 二级树形，{@code parent_id} 指向自身 id）。
 *
 * <p>对应表：{@code dict_industry}（详见 {@code docs/DataBase/schema.sql}）。</p>
 */
@Data
@TableName("dict_industry")
public class DictIndustry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** NULL = 一级行业；非 NULL = 二级行业 */
    private Long parentId;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
