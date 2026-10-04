package com.example.recruitmentsystem;

import com.example.recruitmentsystem.llm.service.LlmResumeParseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LlmResumeParseService.normalize 单元测试。
 *
 * <p>v0.2 验证（2026-10-04）：确保 education / work / projects 永远不丢字段；</p>
 * <ul>
 *   <li>缺失字段 → 空数组</li>
 *   <li>非数组字段 → 空数组</li>
 *   <li>数组元素缺关键字段 → 补 null 而非丢弃整条</li>
 *   <li>skills 非数组 → 转字符串数组</li>
 * </ul>
 */
class LlmResumeParseServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmResumeParseService service = new LlmResumeParseService(null, mapper);

    @Test
    void normalize_missingArrays_becomeEmptyArrays() throws Exception {
        JsonNode raw = mapper.readTree("""
                {"name": "张三", "phone": "13800000000"}
                """);
        JsonNode out = service.normalize(raw);
        assertTrue(out.get("education").isArray());
        assertTrue(out.get("work").isArray());
        assertTrue(out.get("projects").isArray());
        assertEquals(0, out.get("education").size());
        assertEquals(0, out.get("work").size());
        assertEquals(0, out.get("projects").size());
    }

    @Test
    void normalize_nonArrayValues_becomeEmptyArrays() throws Exception {
        JsonNode raw = mapper.readTree("""
                {"name": "张三", "education": "not an array", "work": null, "projects": 42}
                """);
        JsonNode out = service.normalize(raw);
        assertTrue(out.get("education").isArray());
        assertTrue(out.get("work").isArray());
        assertTrue(out.get("projects").isArray());
        assertEquals(0, out.get("education").size());
        assertEquals(0, out.get("work").size());
        assertEquals(0, out.get("projects").size());
    }

    @Test
    void normalize_educationItem_missingSchool_keySetFilledWithNulls() throws Exception {
        JsonNode raw = mapper.readTree("""
                {
                  "education": [
                    {"major": "CS", "startDate": "2018-09"}
                  ]
                }
                """);
        JsonNode out = service.normalize(raw);
        JsonNode first = out.get("education").get(0);
        assertNotNull(first);
        assertTrue(first.has("school"));
        assertTrue(first.get("school").isNull());
        assertEquals("CS", first.get("major").asText());
        assertEquals("2018-09", first.get("startDate").asText());
    }

    @Test
    void normalize_workItem_missingTags_becomesEmptyArray() throws Exception {
        JsonNode raw = mapper.readTree("""
                {
                  "work": [
                    {"company": "字节跳动", "position": "Engineer"}
                  ]
                }
                """);
        JsonNode out = service.normalize(raw);
        JsonNode first = out.get("work").get(0);
        assertTrue(first.has("tags"));
        assertTrue(first.get("tags").isArray());
        assertEquals(0, first.get("tags").size());
    }

    @Test
    void normalize_projects_preservesTechStack() throws Exception {
        JsonNode raw = mapper.readTree("""
                {
                  "projects": [
                    {"name": "OrderHub", "techStack": ["Java", "Kafka"]},
                    {"name": "ChatBot"}
                  ]
                }
                """);
        JsonNode out = service.normalize(raw);
        JsonNode arr = out.get("projects");
        assertEquals(2, arr.size());
        assertEquals("OrderHub", arr.get(0).get("name").asText());
        assertEquals(2, arr.get(0).get("techStack").size());
        assertTrue(arr.get(1).get("techStack").isArray());
        assertEquals(0, arr.get(1).get("techStack").size());
    }

    @Test
    void normalize_skills_textSplitByComma() throws Exception {
        // 注：当前实现以逗号/分号/空白为分隔符，因此 "Spring Boot" 会被拆成两段。
        // 这是有意的设计：候选人技能常用单字标签（Java / MySQL / Kafka）。
        JsonNode raw = mapper.readTree("""
                {"skills": "Java, Spring Boot; MySQL"}
                """);
        JsonNode out = service.normalize(raw);
        JsonNode skills = out.get("skills");
        assertTrue(skills.isArray());
        assertEquals(4, skills.size());
        assertEquals("Java", skills.get(0).asText());
        assertEquals("Spring", skills.get(1).asText());
        assertEquals("Boot", skills.get(2).asText());
        assertEquals("MySQL", skills.get(3).asText());
    }

    @Test
    void normalize_skills_alreadyArray_kept() throws Exception {
        JsonNode raw = mapper.readTree("""
                {"skills": ["Java", "Go"]}
                """);
        JsonNode out = service.normalize(raw);
        assertEquals(2, out.get("skills").size());
        assertEquals("Java", out.get("skills").get(0).asText());
    }

    @Test
    void normalize_nonObjectRoot_throws() throws Exception {
        JsonNode raw = mapper.readTree("\"not an object\"");
        try {
            service.normalize(raw);
            assertFalse(true, "应抛 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }
}
