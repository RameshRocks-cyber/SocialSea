package com.socialsea.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class AdminChartDto {
    private List<String> labels;
    private List<Long> values;
}