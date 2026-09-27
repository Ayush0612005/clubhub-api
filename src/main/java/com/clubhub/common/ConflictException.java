package com.clubhub.common;

/** Request is valid but clashes with current state (duplicate, last admin, ...): HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
