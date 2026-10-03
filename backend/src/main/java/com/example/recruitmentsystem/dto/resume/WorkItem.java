package com.example.recruitmentsystem.dto.resume;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class WorkItem {

    private String company;

    private String position;

    private LocalDate startDate;

    private LocalDate endDate;

    private String description;

    private List<String> tags;
}
