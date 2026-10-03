package com.example.recruitmentsystem.dto.resume;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ProjectItem {

    private String name;

    private String role;

    private LocalDate startDate;

    private LocalDate endDate;

    private String description;

    private List<String> techStack;
}
