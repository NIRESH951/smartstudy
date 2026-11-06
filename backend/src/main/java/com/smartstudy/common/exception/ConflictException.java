package com.smartstudy.common.exception;

/** Thrown when a request conflicts with existing data (duplicate name, overlapping session, ...). HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
