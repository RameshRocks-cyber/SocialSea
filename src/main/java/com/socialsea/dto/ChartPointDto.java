package com.socialsea.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class ChartPointDto {
    private LocalDate date;
    private long count;
}