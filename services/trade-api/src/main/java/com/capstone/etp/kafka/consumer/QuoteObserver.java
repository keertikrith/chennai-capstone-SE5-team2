package com.capstone.etp.kafka.consumer;

import com.capstone.etp.dto.MarketQuote;

/**
 * Something that reacts to a quote read from {@code market-data}.
 *
 * The topic is read by exactly one listener, in the {@code watchlist-service} group. Two listeners
 * in one group would split the partitions between them and each would see only part of the stream,
 * so anything that needs every quote subscribes here instead of opening a second consumer.
 * An observer must not throw: a failure in one is logged and does not stop the others or the stream.
 */
public interface QuoteObserver {

    void onQuote(MarketQuote quote);
}
