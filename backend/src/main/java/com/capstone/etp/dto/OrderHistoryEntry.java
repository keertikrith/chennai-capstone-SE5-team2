package com.capstone.etp.dto;

import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.enums.OrderStatus;
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
public class OrderHistoryEntry {
    private String orderId;             
    private Long accountId;
    private String symbol;
    private OrderSide side;
    private Integer quantity;
    private BigDecimal price; 
    private OrderStatus status;
    private LocalDateTime createdOn;
}
