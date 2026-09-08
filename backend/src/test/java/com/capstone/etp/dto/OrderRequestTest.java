 package com.capstone.etp.dto;

import com.capstone.etp.enums.OrderSide;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation
                .buildDefaultValidatorFactory()
                .getValidator();
    }

    private OrderRequest validRequest() {
        return OrderRequest.builder()
                .accountId(1L)
                .symbol("AAPL")
                .side(OrderSide.BUY)
                .quantity(1)
                .price(new BigDecimal("0.01"))
                .idempotencyKey("12345678")
                .build();
    }

    @Test
    void nullAccountIdIsRejected() {

        OrderRequest request = validRequest();
        request.setAccountId(null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void zeroAccountIdIsRejected() {

        OrderRequest request = validRequest();
        request.setAccountId(0L);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void accountIdOneIsAccepted() {

        OrderRequest request = validRequest();
        request.setAccountId(1L);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void nullSymbolIsRejected() {

        OrderRequest request = validRequest();
        request.setSymbol(null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void emptySymbolIsRejected() {

        OrderRequest request = validRequest();
        request.setSymbol("");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void symbolOfLengthOneIsAccepted() {

        OrderRequest request = validRequest();
        request.setSymbol("A");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void symbolOfLengthFiveIsAccepted() {

        OrderRequest request = validRequest();
        request.setSymbol("ABCDE");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void symbolOfLengthSixIsRejected() {

        OrderRequest request = validRequest();
        request.setSymbol("ABCDEF");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void nullSideIsRejected() {

        OrderRequest request = validRequest();
        request.setSide(null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void buySideIsAccepted() {

        OrderRequest request = validRequest();
        request.setSide(OrderSide.BUY);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void sellSideIsAccepted() {

        OrderRequest request = validRequest();
        request.setSide(OrderSide.SELL);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void nullQuantityIsRejected() {

        OrderRequest request = validRequest();
        request.setQuantity(null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void zeroQuantityIsRejected() {

        OrderRequest request = validRequest();
        request.setQuantity(0);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void negativeQuantityIsRejected() {

        OrderRequest request = validRequest();
        request.setQuantity(-1);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void quantityOneIsAccepted() {

        OrderRequest request = validRequest();
        request.setQuantity(1);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void nullPriceIsRejected() {

        OrderRequest request = validRequest();
        request.setPrice(null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void zeroPriceIsRejected() {

        OrderRequest request = validRequest();
        request.setPrice(new BigDecimal("0.00"));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void negativePriceIsRejected() {

        OrderRequest request = validRequest();
        request.setPrice(new BigDecimal("-0.01"));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void priceOfOneCentIsAccepted() {

        OrderRequest request = validRequest();
        request.setPrice(new BigDecimal("0.01"));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void nullIdempotencyKeyIsRejected() {

        OrderRequest request = validRequest();
        request.setIdempotencyKey(null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void sevenCharacterIdempotencyKeyIsRejected() {

        OrderRequest request = validRequest();
        request.setIdempotencyKey("1234567");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void eightCharacterIdempotencyKeyIsAccepted() {

        OrderRequest request = validRequest();
        request.setIdempotencyKey("12345678");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void hundredCharacterIdempotencyKeyIsAccepted() {

        OrderRequest request = validRequest();
        request.setIdempotencyKey("a".repeat(100));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void oneHundredAndOneCharacterIdempotencyKeyIsRejected() {

        OrderRequest request = validRequest();
        request.setIdempotencyKey("a".repeat(101));

        assertFalse(validator.validate(request).isEmpty());
    }
}