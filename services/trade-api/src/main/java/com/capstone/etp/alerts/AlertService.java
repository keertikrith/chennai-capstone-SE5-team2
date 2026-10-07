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

        String symbol = normalisedSymbol(request.symbol());
        ensureKnownSymbol(symbol);
        AlertDirection direction = request.direction().normalised();
        ensureNotCrossedYet(symbol, direction, request.threshold());

        Long id = mapper.insertWithinCap(accountId, symbol, direction, request.threshold(), cap);
        if (id == null) {
            throw new ModuleException(HttpStatus.CONFLICT, "ALR-409",
                    "You can keep at most " + cap + " alerts. Remove one to add another");
        }

        PriceAlert created = mapper.findByIdAndAccount(id, accountId);
        index.add(created);
        log.info("[ALERTS] created alert {} accountId={} symbol={} {} {}",
                id, accountId, symbol, direction, request.threshold());
        return AlertResponse.from(created);
    }

    public AlertResponse update(Long accountId, Long alertId, UpdateAlertRequest request) {
        authorizer.ensureOwnAccount(accountId);
        accountService.validateAccountId(accountId);

        PriceAlert current = mapper.findByIdAndAccount(alertId, accountId);
        if (current == null) {
            throw notFound();
        }
        if (current.getStatus() == AlertStatus.TRIGGERED) {
            throw new ModuleException(HttpStatus.CONFLICT, "ALR-409",
                    "A fired alert cannot be edited");
        }

        String symbol = normalisedSymbol(request.symbol());
        ensureKnownSymbol(symbol);
        AlertDirection direction = request.direction().normalised();
        ensureNotCrossedYet(symbol, direction, request.threshold());

        if (mapper.updateEditable(alertId, accountId, symbol, direction, request.threshold()) == 0) {
            throw new ModuleException(HttpStatus.CONFLICT, "ALR-409",
                    "This alert cannot be edited right now");
        }

        PriceAlert updated = mapper.findByIdAndAccount(alertId, accountId);
        if (updated == null) {
            throw notFound();
        }
        if (current.getStatus() == AlertStatus.ACTIVE) {
            index.remove(current.getSymbol(), current.getId());
            index.add(updated);
        }
        return AlertResponse.from(updated);
    }

    public AlertResponse setEnabled(Long accountId, Long alertId, boolean enabled) {
        authorizer.ensureOwnAccount(accountId);
        accountService.validateAccountId(accountId);

        PriceAlert current = mapper.findByIdAndAccount(alertId, accountId);
        if (current == null) {
            throw notFound();
        }
        if (current.getStatus() == AlertStatus.TRIGGERED) {
            throw new ModuleException(HttpStatus.CONFLICT, "ALR-409",
                    "A fired alert cannot be enabled or disabled");
        }

        if (enabled) {
            if (current.getStatus() == AlertStatus.ACTIVE) {
                return AlertResponse.from(current);
            }
            ensureNotCrossedYet(current.getSymbol(), current.getDirection(), current.getThreshold());
            if (mapper.enable(alertId, accountId) == 0) {
                throw new ModuleException(HttpStatus.CONFLICT, "ALR-409",
                        "This alert could not be enabled");
            }
            PriceAlert updated = mapper.findByIdAndAccount(alertId, accountId);
            if (updated == null) {
                throw notFound();
            }
            index.add(updated);
            return AlertResponse.from(updated);
        }

        if (current.getStatus() == AlertStatus.CANCELLED) {
            return AlertResponse.from(current);
        }
        if (mapper.disable(alertId, accountId) == 0) {
            throw new ModuleException(HttpStatus.CONFLICT, "ALR-409",
                    "This alert could not be disabled");
        }
        index.remove(current.getSymbol(), current.getId());
        PriceAlert updated = mapper.findByIdAndAccount(alertId, accountId);
        if (updated == null) {
            throw notFound();
        }
        return AlertResponse.from(updated);
    }


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

    private static String normalisedSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private void ensureKnownSymbol(String symbol) {
        if (accountMapper.countInstrumentsBySymbol(symbol) == 0) {
            throw new InstrumentNotFoundException(symbol, InstrumentNotFoundException.Reason.UNKNOWN_SYMBOL);
        }
    }

    private void ensureNotCrossedYet(String symbol, AlertDirection direction, java.math.BigDecimal threshold) {
        Optional<MarketQuote> current = quoteCache.get(symbol);
        if (current.isPresent() && current.get().getPrice() != null
                && !Boolean.TRUE.equals(current.get().getStale())
                && direction.isCrossedBy(current.get().getPrice(), threshold)) {
            throw new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422",
                    "The price of " + symbol + " is already "
                            + direction.relationText() + " that level. Choose a level the price has not reached yet");
        }
    }
}
