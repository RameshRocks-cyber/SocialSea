package com.socialsea.dto;

public class ChartPointDto {

    private String label;
    private Long count;

    public ChartPointDto(String label, Long count) {
        this.label = label;
        this.count = count;
    }

    public String getLabel() { return label; }
    public Long getCount() { return count; }
}