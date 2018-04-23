package com.n26.restful.api.dto;

/**
 * Data transfer object for Transactions for use in Rest
 * @author Andrew Polyakov
 */
public class TransactionDto {

    public TransactionDto() {
        //default
    }

    public TransactionDto(Double amount, Long timestamp) {
        this.amount = amount;
        this.timestamp = timestamp;
    }

    private Double amount;

    private Long timestamp;

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
