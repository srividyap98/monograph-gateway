package com.monograph.gateway.error;

/**
 * The target of a mutation does not exist. Queries return null for a missing entity instead;
 * a write to something that is not there is a client error the caller must see.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
