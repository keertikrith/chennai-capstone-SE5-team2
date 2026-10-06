package com.capstone.etp.alerts;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A customer asks to be told when a price crosses a level. The account is taken from the path and
 * checked against the token; it is not a field here.
 */
public record CreateAlertRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9.:^=\\-]+$") String symbol,
        @NotNull AlertDirection direction,
        @NotNull @DecimalMin("0.0001") @Digits(integer = 14, fraction = 4) BigDecimal threshold) {
}
