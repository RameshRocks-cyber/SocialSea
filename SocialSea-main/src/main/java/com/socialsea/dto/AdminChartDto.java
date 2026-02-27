package com.socialsea.dto;

import java.util.List;

public class AdminChartDto {

    private List<String> labels;
    private List<Long> values;

    public AdminChartDto() {}

    public AdminChartDto(List<String> labels, List<Long> values) {
        this.labels = labels;
        this.values = values;
    }

    public List<String> getLabels() {
        return labels;
    }

    public List<Long> getValues() {
        return values;
    }
}
