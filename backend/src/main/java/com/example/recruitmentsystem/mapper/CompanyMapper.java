package com.example.recruitmentsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruitmentsystem.entity.Company;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CompanyMapper extends BaseMapper<Company> {
    // 公司审核使用 MP 的 LambdaQueryWrapper，无需额外 XML。参见 CompanyService / AdminCompanyServiceImpl。
}
