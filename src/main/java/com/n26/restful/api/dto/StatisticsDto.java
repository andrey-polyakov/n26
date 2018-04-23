package com.n26.restful.api.dto;

/**
 * Data transfer objects for rolling statistics to be returned to the readers.
 */
public class StatisticsDto {

    private final Long count;
    private final Double min;
    private final Double max;
    private final Double avg;
    private final Double sum;

    public StatisticsDto() {
        this.count = 0l;
        this.min = 0.0;
        this.max = 0.0;
        this.avg = 0.0;
        this.sum = 0.0;
    }

    public StatisticsDto(Long count, Double min, Double max, Double avg, Double sum) {
        this.count = count;
        this.min = min;
        this.max = max;
        this.avg = avg;
        this.sum = sum;
    }

    public Long getCount() {
        return count;
    }

    public Double getMin() {
        return min;
    }

    public Double getMax() {
        return max;
    }

    public Double getAvg() {
        return avg;
    }

    public Double getSum() {
        return sum;
    }

    @Override
    public String toString() {
        return "StatisticsDto{" +
                "count=" + count +
                ", min=" + min +
                ", max=" + max +
                ", avg=" + avg +
                ", sum=" + sum +
                '}';
    }
}
