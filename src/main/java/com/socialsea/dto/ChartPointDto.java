package com.socialsea.dto;

import java.io.Serializable;

public class ChartPointDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String label;
    private Long count;

    public ChartPointDto() {
    }

    public ChartPointDto(String label, Long count) {
        this.label = label;
        this.count = count;
    }

    public String getLabel() { return label; }
    public Long getCount() { return count; }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
