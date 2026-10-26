package com.homepage.frontend.web;

import org.springframework.core.NestedExceptionUtils;

/** Log text for a failed call to the backend. */
final class BackendFailure {

    private BackendFailure() {
    }

    /**
     * "Connection refused (ConnectException)" rather than Spring's "I/O error on GET request for …: null": a refused
     * connection carries no message of its own, so the cause's type is named too.
     */
    static String describe(Exception e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        String type = cause.getClass().getSimpleName();
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? type + " (" + e.getMessage().replaceAll(": null$", "") + ")"
                : cause.getMessage() + " (" + type + ")";
    }
}
