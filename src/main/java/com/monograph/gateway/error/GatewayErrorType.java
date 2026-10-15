package com.monograph.gateway.error;

import graphql.ErrorClassification;

/**
 * Error classifications the gateway adds on top of Spring's {@code ErrorType}
 * (BAD_REQUEST, NOT_FOUND, ...). Serialized as {@code extensions.classification}.
 */
public enum GatewayErrorType implements ErrorClassification {

    /** The request was valid but conflicts with current state in a backend (e.g. duplicate email). */
    CONFLICT,

    /** A backend service timed out, was unreachable or failed; retrying later may succeed. */
    DOWNSTREAM_UNAVAILABLE
}
