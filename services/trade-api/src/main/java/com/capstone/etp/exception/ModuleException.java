package com.capstone.etp.exception;

import org.springframework.http.HttpStatus;

/**
 * A refusal raised by one of the extension modules (preferences, notifications, alerts,
 * portfolio). It carries its own HTTP status so the module's error handler does not need to know
 * every code. The codes it carries extend the platform catalogue only where nothing in it fits:
 * MKT-503, NTF-404, ALR-404 and ALR-409.
 */
public class ModuleException extends ETPException {

    private final HttpStatus status;

    public ModuleException(HttpStatus status, String catalogueCode, String message) {
        super(catalogueCode, message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
