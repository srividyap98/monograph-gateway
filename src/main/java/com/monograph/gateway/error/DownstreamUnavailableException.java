package com.monograph.gateway.error;

/**
 * A backend call failed for a reason the client cannot fix: timeout, connection failure,
 * error status or unreadable response. The message names the service but carries no
 * response body or stack detail; the cause is logged, not returned.
 */
public class DownstreamUnavailableException extends RuntimeException {

    public DownstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
