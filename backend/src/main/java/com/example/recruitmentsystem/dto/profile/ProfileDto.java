package com.example.recruitmentsystem.dto.profile;

import lombok.Data;

/**
 * 当前登录用户的个人资料 DTO。
 *
 * <p>返回 email / username / phone / roleCode / status，用于 Profile 页初始化。</p>
 */
@Data
public class ProfileDto {

    private Long id;
    private String email;
    private String username;
    private String phone;
    private String roleCode;
    private String status;
}
