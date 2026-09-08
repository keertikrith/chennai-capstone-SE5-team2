package com.capstone.etp.dto;

import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private String orderId;
    private OrderStatus status;
    private String message;
    private String symbol;
    private OrderSide side;
    private Integer quantity;
    private BigDecimal price;
}
