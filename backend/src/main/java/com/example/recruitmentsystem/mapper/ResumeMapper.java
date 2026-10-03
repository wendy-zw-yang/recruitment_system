package com.example.recruitmentsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruitmentsystem.entity.Resume;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {

    /**
     * 查询候选人当前 ACTIVE 简历（{@code is_archived = false}）。
     * 用于单一 ACTIVE 约束校验。
     */
    default Resume selectActiveByCandidate(Long candidateId) {
        return selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getCandidateId, candidateId)
                .eq(Resume::getArchived, false)
                .last("limit 1"));
    }
}
