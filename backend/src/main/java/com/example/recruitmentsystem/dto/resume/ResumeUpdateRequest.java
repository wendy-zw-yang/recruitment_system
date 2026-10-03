package com.example.recruitmentsystem.dto.resume;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ResumeUpdateRequest {

    @Size(max = 64, message = "姓名过长")
    private String basicName;

    @Size(max = 32, message = "电话过长")
    private String basicPhone;

    @Size(max = 255, message = "邮箱过长")
    private String basicEmail;

    private List<EducationItem> education;
    private List<WorkItem> work;
    private List<ProjectItem> projects;
    private List<String> skills;

    private String selfIntro;
}
