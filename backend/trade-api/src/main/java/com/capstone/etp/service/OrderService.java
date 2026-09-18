package com.capstone.etp.service;

import com.capstone.etp.dto.OrderResponse;
import com.capstone.etp.dto.PlaceOrderRequest;
import com.capstone.etp.entity.Account;
import com.capstone.etp.entity.Instrument;
import com.capstone.etp.entity.Order;
import com.capstone.etp.entity.PortfolioHolding;
import com.capstone.etp.enums.OrderStatus;
import com.capstone.etp.exception.AccountNotFoundException;
import com.capstone.etp.exception.IllegalOrderStateException;
import com.capstone.etp.exception.InstrumentNotFoundException;
import com.capstone.etp.exception.OptimisticLockException;
import com.capstone.etp.kafka.producer.OrderEventProducer;
import com.capstone.etp.mapper.OrderMapper;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.validator.OrderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderValidator orderValidator;
    private final AccountAuthorizer accountAuthorizer;
    private final OrderEventProducer orderEventProducer;

    // create a new order
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        // Perform database operations in a transaction
        PlaceOrderInfo info = persistOrder(request);
 
        // Note: publishing is performed inside the transactional persistOrder
        // so failures during serialization/publish will cause the DB transaction
        // to roll back and avoid inserting orders for messages that cannot be published.

        return OrderResponse.builder()
                .orderId(info.order.getId())
                .status(info.order.getStatus())
                .message("Order created successfully")
                .symbol(info.instrument.getSymbol())
                .side(info.order.getSide())
                .quantity(info.order.getQuantity())
                .price(info.order.getLimitPrice())
                .build();
    }

    @Transactional
    private PlaceOrderInfo persistOrder(PlaceOrderRequest request) {
        accountAuthorizer.ensureAccountAccess(request.getAccountId());

        Account account = orderMapper.findAccountById(request.getAccountId());
        if (account == null) {
            throw new AccountNotFoundException(
                    request.getAccountId());
        }

        Instrument instrument = orderMapper.findInstrumentBySymbol(request.getSymbol());
        if (instrument == null) {
            throw new InstrumentNotFoundException(
                    null,
                    InstrumentNotFoundException.Reason.UNKNOWN_SYMBOL);
        }

        PortfolioHolding holding = orderMapper.findPortfolioHolding(
                request.getAccountId(),
                instrument.getId());

        List<Order> existingOrders = orderMapper.findExistingOrdersByAccount(
                request.getAccountId());

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .accountId(request.getAccountId())
                .instrumentId(instrument.getId())
                .idempotencyKey(request.getIdempotencyKey())
                .side(request.getSide())
                .quantity(request.getQuantity())
                .limitPrice(request.getPrice())
                .status(OrderStatus.NEW)
                .createdAt(Instant.now())
                .build();

        orderValidator.validate(order, account, instrument, holding, existingOrders);

        orderMapper.insertOrder(order);
        int touchedAccounts = orderMapper.incrementAccountVersionIfVersionMatches(
                account.getId(),
                account.getVersion());
        if (touchedAccounts == 0) {
            throw new OptimisticLockException(
                    "Account version mismatch during order placement; concurrent modification detected");
        }
 
        // Publish event BEFORE the transactional method returns so that any
        // publish/serialization failure will cause the surrounding transaction
        // to roll back and avoid persisting an order for an event that cannot
        // be successfully published.
        orderEventProducer.publishOrderPlaced(order, instrument.getSymbol());

        return new PlaceOrderInfo(order, instrument);
    }

   @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
       Order order = orderMapper.findOrderById(orderId);
       accountAuthorizer.ensureAccountAccess(order.getAccountId());
       Account account = orderMapper.findAccountById(order.getAccountId());
       if (account == null) {
           throw new AccountNotFoundException(order.getAccountId());
       }
       try{
                order.cancel();
       } catch (IllegalOrderStateException e) {
           throw new IllegalOrderStateException("Failed to cancel order: " + e.getMessage());
       }
       

       int affectedRows = orderMapper.updateOrderStatusIfCurrentStatusMatches(
                orderId,
               OrderStatus.NEW,
               order.getStatus(),
               order.getTerminalAt());
       if (affectedRows == 0) {
           throw new OptimisticLockException(
                   "Order status mismatch during cancellation; concurrent modification detected");
       }

       int touchedAccounts = orderMapper.incrementAccountVersionIfVersionMatches(
               account.getId(),
               account.getVersion());
       if (touchedAccounts == 0) {
           throw new OptimisticLockException(
                   "Account version mismatch during order cancellation; concurrent modification detected");
       }

       return OrderResponse.builder()
               .orderId(orderId)
               .status(order.getStatus())
               .message("Order cancelled successfully")
               .build();
    }

    private static class PlaceOrderInfo {
        final Order order;
        final Instrument instrument;

        PlaceOrderInfo(Order order, Instrument instrument) {
            this.order = order;
            this.instrument = instrument;
        }
    }
}
