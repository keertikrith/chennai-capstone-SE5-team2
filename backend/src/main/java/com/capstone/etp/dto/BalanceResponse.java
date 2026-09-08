package com.capstone.etp.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceResponse {
    private Long accountId;
    private BigDecimal cashBalance;
    private String currency;
    private LocalDateTime asOf;
}
