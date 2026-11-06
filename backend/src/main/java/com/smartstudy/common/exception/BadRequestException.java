package com.smartstudy.common.exception;

/** Thrown for invalid input that bean validation cannot express (e.g. an unknown time zone). HTTP 400. */
public class BadRequestException extends RuntimeException {

    private final String field;

    public BadRequestException(String message) {
        this(null, message);
    }

    public BadRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
