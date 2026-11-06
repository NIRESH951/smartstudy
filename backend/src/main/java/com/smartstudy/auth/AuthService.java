package com.smartstudy.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.smartstudy.common.TimeUtils;
import com.smartstudy.common.exception.ConflictException;
import com.smartstudy.common.exception.ResourceNotFoundException;
import com.smartstudy.user.ProfileResponse;
import com.smartstudy.user.User;
import com.smartstudy.user.UserRepository;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login.
 *
 * <p>Login authenticates the credentials with the {@link AuthenticationManager} (BCrypt
 * comparison), then stores the security context in the HTTP session. Before that the session
 * id is changed to prevent session-fixation attacks, and afterwards the CSRF token is rotated.</p>
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfAuthenticationStrategy csrfAuthenticationStrategy;
    private final Clock clock;
    private final SecurityContextHolderStrategy contextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       SecurityContextRepository securityContextRepository,
                       CsrfAuthenticationStrategy csrfAuthenticationStrategy, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfAuthenticationStrategy = csrfAuthenticationStrategy;
        this.clock = clock;
    }

    /** Creates the account and signs the new student in immediately. */
    @Transactional
    public ProfileResponse register(RegisterRequest request, HttpServletRequest httpRequest,
                                    HttpServletResponse httpResponse) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        String timeZone = request.timeZone() == null || request.timeZone().isBlank()
                ? "UTC"
                : TimeUtils.requireValidZone(request.timeZone().trim());
        User user = new User(request.fullName().trim(), email, passwordEncoder.encode(request.password()), timeZone);
        user.setLastLoginAt(Instant.now(clock));
        userRepository.save(user);

        AppUserDetails principal = AppUserDetails.from(user);
        principal.eraseCredentials();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        establishSession(authentication, httpRequest, httpResponse);
        return ProfileResponse.from(user);
    }

    /** Verifies the credentials and starts an authenticated session. Wrong credentials produce HTTP 401. */
    @Transactional
    public ProfileResponse login(LoginRequest request, HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse) {
        String email = normalizeEmail(request.email());
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        establishSession(authentication, httpRequest, httpResponse);

        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));
        user.setLastLoginAt(Instant.now(clock));
        return ProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public ProfileResponse me(Long userId) {
        return userRepository.findById(userId)
                .map(ProfileResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private void establishSession(Authentication authentication, HttpServletRequest request,
                                  HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        csrfAuthenticationStrategy.onAuthentication(authentication, request, response);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
