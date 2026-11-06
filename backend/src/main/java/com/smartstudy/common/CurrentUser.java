package com.smartstudy.common;

import com.smartstudy.auth.AppUserDetails;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the id of the signed-in student. Controllers pass this id to every service call,
 * and services always filter by it, so a student can never read or modify another
 * student's records by changing an id in the URL.
 */
@Component
public class CurrentUser {

    public Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails user) {
            return user.getId();
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated student");
    }
}
