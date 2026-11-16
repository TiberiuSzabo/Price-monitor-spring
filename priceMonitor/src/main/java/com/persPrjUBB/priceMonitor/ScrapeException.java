package com.persPrjUBB.priceMonitor;

import java.util.Locale;

public class ScrapeException extends RuntimeException {

    /** Why a scrape failed. Used as the {@code reason} tag on the failure metric. */
    public enum Reason {
        /** eMAG did not answer within the configured timeout. */
        TIMEOUT,
        /** eMAG refused the request (HTTP 403/429) or answered with a captcha page. */
        BLOCKED,
        /** The page loaded but the title or price could not be found - the markup changed. */
        PARSE_ERROR,
        /** Anything else: DNS, connection reset, HTTP 5xx... */
        OTHER;

        public String tag() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Reason reason;

    public ScrapeException(String message) {
        this(Reason.OTHER, message);
    }

    public ScrapeException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ScrapeException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
