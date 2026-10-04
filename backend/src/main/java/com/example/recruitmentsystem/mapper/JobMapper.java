package com.example.recruitmentsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.recruitmentsystem.entity.Job;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JobMapper extends BaseMapper<Job> {

    /**
     * 候选人端分页查询（仅 ONLINE，无审核要求）。
     * v0.4：移除 audit_status=APPROVED 条件。
     */
    IPage<Job> selectPageForCandidate(Page<Job> page,
                                      @Param("keyword") String keyword,
                                      @Param("industryId") Long industryId,
                                      @Param("cityId") Long cityId,
                                      @Param("province") String province,
                                      @Param("sort") String sort);
}
