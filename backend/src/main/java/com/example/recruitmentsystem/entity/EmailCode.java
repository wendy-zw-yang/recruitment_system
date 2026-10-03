package com.example.recruitmentsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮箱验证码。
 *
 * <p>对应表：{@code email_code}（详见 {@code docs/DataBase/schema.sql}）。</p>
 *
 * <p>{@code code_type} 取值：</p>
 * <ul>
 *   <li>{@code register} — 注册</li>
 *   <li>{@code login} — 登录</li>
 *   <li>{@code reset} — 找回密码（暂未实现）</li>
 * </ul>
 *
 * <p>约定：{@code expire_time} 距 created_at 5 分钟；{@code used=1} 表示已用（单次有效）。</p>
 */
@Data
@TableName("email_code")
public class EmailCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    /** 6 位数字验证码 */
    private String code;

    private String codeType;

    /** 0 未使用 / 1 已使用 */
    private Integer used;

    private LocalDateTime expireTime;

    private LocalDateTime createdAt;
}
