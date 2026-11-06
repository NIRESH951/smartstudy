package com.smartstudy.common.exception;

/**
 * Thrown when a syntactically valid request breaks a business rule
 * (for example completing a session that was never started). HTTP 422.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
