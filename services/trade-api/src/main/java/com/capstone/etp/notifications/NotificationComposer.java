package com.capstone.etp.notifications;

import com.capstone.etp.kafka.MalformedEventException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Turns an event into the words a customer reads. Every message is built from a fixed list of
 * fields: symbol, side, quantity, prices and a reason CODE mapped to a sentence. Order ids, account
 * numbers, e-mail addresses and anything else in the event never reach the text.
 */
@Component
class NotificationComposer {

    record Draft(NotificationKind kind, String subject, String body) {
    }

    Draft compose(String eventType, TradeEvent event) {
        String order = event.side() + " " + event.quantity() + " " + event.symbol();
        return switch (eventType) {
            case "ORDER_FILLED" -> new Draft(NotificationKind.ORDER_FILLED,
                    "Order filled: " + order,
                    "Your " + event.side() + " order for " + event.quantity() + " " + event.symbol()
                            + " was filled at " + money(event.executedPrice())
                            + (event.price() != null ? " (your limit was " + money(event.price()) + ")" : "")
                            + ".");
            case "ORDER_REJECTED" -> new Draft(NotificationKind.ORDER_REJECTED,
                    "Order rejected: " + order,
                    "Your " + event.side() + " order for " + event.quantity() + " " + event.symbol()
                            + " was not carried out because " + rejectionReason(event.reason()) + ".");
            case "ORDER_CANCELLED" -> new Draft(NotificationKind.ORDER_CANCELLED,
                    "Order cancelled: " + order,
                    "Your " + event.side() + " order for " + event.quantity() + " " + event.symbol()
                            + " was cancelled" + ("CANCELLED_BY_CUSTOMER".equals(event.reason())
                            ? " at your request." : "."));
            default -> throw new MalformedEventException("Unsupported event type");
        };
    }

    /** A reason code becomes a sentence; a code nobody has mapped becomes a pointer to the blotter, never the raw code. */
    static String rejectionReason(String code) {
        if (code == null) {
            return "it could not be completed. See your order history for details";
        }
        return switch (code) {
            case "INSUFFICIENT_FUNDS" -> "there was not enough cash in your account";
            case "INSUFFICIENT_HOLDINGS" -> "you do not hold enough of this instrument to sell that quantity";
            case "PRICE_NOT_MET" -> "the market price did not meet your limit price";
            case "INSTRUMENT_NOT_TRADABLE" -> "the instrument is not tradable right now";
            case "ACCOUNT_NOT_ACTIVE" -> "your account is not active";
            default -> "it could not be completed. See your order history for details";
        };
    }

    static String money(BigDecimal amount) {
        return amount == null ? "the market price" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
