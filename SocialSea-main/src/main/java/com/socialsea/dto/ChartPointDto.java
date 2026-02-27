package com.socialsea.dto;

public class ChartPointDto {

    private String date;
    private Long count;

    public ChartPointDto() {
    }

    public ChartPointDto(String date, Long count) {
        this.date = date;
        this.count = count;
    }

    public String getDate() {
        return date;
    }

    public Long getCount() {
        return count;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
