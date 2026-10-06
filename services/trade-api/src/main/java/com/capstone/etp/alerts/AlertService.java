package com.capstone.etp.alerts;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.exception.InstrumentNotFoundException;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.mapper.AccountMapper;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.service.AccountService;
import com.capstone.etp.service.MarketQuoteCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Price alerts, from the customer's side: create, list, cancel or remove. Every method authorises
 * against the verified token's account first, and every query that follows is keyed on that same
 * account, never on an identifier the client supplied.
 *
 * <p><b>The cap.</b> An account may hold at most {@code app.alerts.max-per-account} alerts, finished
 * ones included. The check and the insert are one SQL statement, so concurrent requests cannot exceed
 * it. Finished alerts are removed by the customer, which is how they make room, so
 * create-then-cancel loops cannot grow the table. Without a cap one account could fill the table and
 * make the quote consumer, which runs in the process that places orders, do work proportional to it.
 *
 * <p>Instruments are referenced by symbol and validated through the platform's own mapper; nothing
 * about an instrument is copied here.
 */
@Slf4j
@Service
public class AlertService {

    private final AlertMapper mapper;
    private final AlertIndex index;
    private final AccountAuthorizer authorizer;
    private final AccountService accountService;
    private final AccountMapper accountMapper;
    private final MarketQuoteCache quoteCache;
    private final int cap;

    AlertService(AlertMapper mapper,
                 AlertIndex index,
                 AccountAuthorizer authorizer,
                 AccountService accountService,
                 AccountMapper accountMapper,
                 MarketQuoteCache quoteCache,
                 @Value("${app.alerts.max-per-account:25}") int cap) {
        this.mapper = mapper;
        this.index = index;
        this.authorizer = authorizer;
        this.accountService = accountService;
        this.accountMapper = accountMapper;
        this.quoteCache = quoteCache;
        this.cap = cap;
    }

    public List<AlertResponse> list(Long accountId) {
        authorizer.ensureOwnAccount(accountId);
        return mapper.findByAccount(accountId).stream().map(AlertResponse::from).toList();
    }

    public AlertResponse create(Long accountId, CreateAlertRequest request) {
        authorizer.ensureOwnAccount(accountId);
        accountService.validateAccountId(accountId);

        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        if (accountMapper.countInstrumentsBySymbol(symbol) == 0) {
            throw new InstrumentNotFoundException(symbol, InstrumentNotFoundException.Reason.UNKNOWN_SYMBOL);
        }

        // An alert whose level the price has already passed would fire on the next quote and tell the
        // customer nothing they did not know. Refuse it and say why.
        Optional<MarketQuote> current = quoteCache.get(symbol);
        if (current.isPresent() && current.get().getPrice() != null
                && !Boolean.TRUE.equals(current.get().getStale())
                && request.direction().isCrossedBy(current.get().getPrice(), request.threshold())) {
            throw new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422",
                    "The price of " + symbol + " is already "
                            + (request.direction() == AlertDirection.ABOVE ? "above " : "below ")
                            + "that level. Choose a level the price has not reached yet");
        }

        Long id = mapper.insertWithinCap(accountId, symbol, request.direction(), request.threshold(), cap);
        if (id == null) {
            throw new ModuleException(HttpStatus.CONFLICT, "ALR-409",
                    "You can keep at most " + cap + " alerts. Remove one to add another");
        }

        PriceAlert created = mapper.findByIdAndAccount(id, accountId);
        index.add(created);
        log.info("[ALERTS] created alert {} accountId={} symbol={} {} {}",
                id, accountId, symbol, request.direction(), request.threshold());
        return AlertResponse.from(created);
    }

    /**
     * Cancels an active alert, or removes a finished one. A fired alert whose hand-over to
     * notifications is still unresolved is kept: removing it would hide that the customer may not
     * have been told.
     */
    public AlertResponse remove(Long accountId, Long alertId) {
        authorizer.ensureOwnAccount(accountId);
        PriceAlert alert = mapper.findByIdAndAccount(alertId, accountId);
        if (alert == null) {
            throw notFound();
        }

        if (alert.getStatus() == AlertStatus.ACTIVE) {
            if (mapper.cancel(alertId, accountId) == 1) {
                index.remove(alert.getSymbol(), alertId);
                return AlertResponse.from(mapper.findByIdAndAccount(alertId, accountId));
            }
            // It fired between the read and the cancel. Say so rather than pretend it was cancelled.
            PriceAlert now = mapper.findByIdAndAccount(alertId, accountId);
            if (now == null) {
                throw notFound();
            }
            return AlertResponse.from(now);
        }

        if (mapper.deleteFinished(alertId, accountId) == 0) {
            throw new ModuleException(HttpStatus.CONFLICT, "ALR-409",
                    "This alert fired and is still being handed over for delivery, so it cannot be removed yet");
        }
        return AlertResponse.from(alert);
    }

    private static ModuleException notFound() {
        return new ModuleException(HttpStatus.NOT_FOUND, "ALR-404", "Alert not found");
    }
}
