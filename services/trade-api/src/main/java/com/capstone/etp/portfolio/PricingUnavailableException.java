package com.capstone.etp.portfolio;

import com.capstone.etp.exception.ModuleException;
import org.springframework.http.HttpStatus;

/**
 * No price could be obtained for any held instrument: Fauxnance is unreachable or its quota is spent,
 * and no earlier price is held. 503 MKT-503, as contracts/portfolio-api.yaml specifies. It is raised
 * only on the portfolio routes; order placement does not depend on pricing and is unaffected.
 */
public class PricingUnavailableException extends ModuleException {

    public PricingUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "MKT-503", "Pricing unavailable");
    }
}
