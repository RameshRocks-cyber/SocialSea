package com.socialsea.util;

import com.socialsea.dto.ChartPointDto;
import java.util.List;

public class CsvUtil {

    public static String toCsv(List<ChartPointDto> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("label,count\n");

        for (ChartPointDto d : data) {
            sb.append(d.getLabel())
              .append(",")
              .append(d.getCount())
              .append("\n");
        }
        return sb.toString();
    }
}