package com.monograph.gateway.error;

/** A backend rejected a write because of its current state (HTTP 409 downstream). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
