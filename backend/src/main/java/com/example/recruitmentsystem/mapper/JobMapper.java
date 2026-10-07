package com.example.recruitmentsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.recruitmentsystem.entity.Job;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface JobMapper extends BaseMapper<Job> {

    /**
     * 候选人端分页查询（仅 ONLINE，无审核要求）。
     * v0.4：移除 audit_status=APPROVED 条件。
     * v0.5 扩展：增加 cityName + favoritedOnly + candidateId 参数。
     *
     * @param cityName      按城市名（dict_city.name）精确匹配；用于首页搜索栏文本输入
     * @param favoritedOnly true = 仅查询 candidateId 收藏的职位；需配合 candidateId 使用
     * @param candidateId   候选人 userId；favoritedOnly=true 时必填
     */
    IPage<Job> selectPageForCandidate(Page<Job> page,
                                      @Param("keyword") String keyword,
                                      @Param("industryId") Long industryId,
                                      @Param("cityId") Long cityId,
                                      @Param("province") String province,
                                      @Param("cityName") String cityName,
                                      @Param("favoritedOnly") Boolean favoritedOnly,
                                      @Param("candidateId") Long candidateId,
                                      @Param("sort") String sort);

    /**
     * 批量查询 candidateId 收藏的 jobId 列表（用于 listForCandidate 填充 favorited 字段）。
     * 返回值仅用于 in-memory 比对，避免 N+1 查询。
     */
    List<Long> selectFavoriteJobIds(@Param("candidateId") Long candidateId,
                                    @Param("jobIds") List<Long> jobIds);

    /**
     * v0.7.3：候选人首页"推荐职位"候选集合。
     *
     * <p>宽筛候选集合：偏好字段任一命中即入选（行业 OR 城市 OR 省份 OR 关键词）。
     * 多级排序（Tier 1-4）由 Java 内存打分；SQL 仅过滤 + LIMIT。</p>
     *
     * @param industryId   期望行业（nullable）
     * @param cityId       期望城市（nullable）
     * @param province     期望省份（nullable，空字符串视为未传）
     * @param keywordTokens 期望职位拆词列表（nullable；任一字段 LIKE 任一拆词即命中）
     * @param offset LIMIT 起始偏移
     * @param limit LIMIT 条数
     */
    List<Job> selectPageForRecommendation(@Param("industryId") Long industryId,
                                          @Param("cityId") Long cityId,
                                          @Param("province") String province,
                                          @Param("keywordTokens") List<String> keywordTokens,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);
}
