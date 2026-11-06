package com.smartstudy.common.exception;

/**
 * Thrown when a record does not exist <em>or does not belong to the current student</em>.
 * Both cases return 404 so that an attacker cannot discover other students' record ids.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " " + id + " was not found.");
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
