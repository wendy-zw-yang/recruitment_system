package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 城市字典。{@code name} 唯一。
 *
 * <p>对应表：{@code dict_city}。</p>
 */
@Data
@TableName("dict_city")
public class DictCity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 省份 / 直辖市，与 {@code dict_city.province} 列对应；空字符串表示未填 */
    private String province;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
