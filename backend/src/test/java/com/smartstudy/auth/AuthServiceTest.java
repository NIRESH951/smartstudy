package com.smartstudy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.smartstudy.common.exception.BadRequestException;
import com.smartstudy.common.exception.ConflictException;
import com.smartstudy.user.ProfileResponse;
import com.smartstudy.user.User;
import com.smartstudy.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private SecurityContextRepository securityContextRepository;
    @Mock
    private CsrfAuthenticationStrategy csrfAuthenticationStrategy;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-31T06:00:00Z"), ZoneOffset.UTC);
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager,
                securityContextRepository, csrfAuthenticationStrategy, clock);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registrationHashesThePasswordAndStartsASession() {
        when(userRepository.existsByEmailIgnoreCase("asha@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProfileResponse profile = authService.register(
                new RegisterRequest(" Asha Verma ", "Asha@Example.com", "Study@2026", "Asia/Kolkata"),
                request, response);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.getEmail()).isEqualTo("asha@example.com");
        assertThat(user.getFullName()).isEqualTo("Asha Verma");
        assertThat(user.getPasswordHash()).isNotEqualTo("Study@2026").startsWith("$2");
        assertThat(passwordEncoder.matches("Study@2026", user.getPasswordHash())).isTrue();
        assertThat(profile.email()).isEqualTo("asha@example.com");
        assertThat(profile.timeZone()).isEqualTo("Asia/Kolkata");
        verify(securityContextRepository).saveContext(any(), any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
    }

    @Test
    void duplicateEmailIsRejected() {
        when(userRepository.existsByEmailIgnoreCase("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Asha", "asha@example.com", "Study@2026", "UTC"),
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void unknownTimeZoneIsRejected() {
        when(userRepository.existsByEmailIgnoreCase("asha@example.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Asha", "asha@example.com", "Study@2026", "Moon/Base"),
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void loginAuthenticatesWithTheAuthenticationManagerAndStoresTheContext() {
        User user = new User("Asha", "asha@example.com", passwordEncoder.encode("Study@2026"), "UTC");
        AppUserDetails principal = new AppUserDetails(1L, "asha@example.com", "Asha", null, true, "STUDENT");
        when(authenticationManager.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ProfileResponse profile = authService.login(new LoginRequest("  ASHA@example.com ", "Study@2026"),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        ArgumentCaptor<UsernamePasswordAuthenticationToken> attempt =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(attempt.capture());
        assertThat(attempt.getValue().getPrincipal()).isEqualTo("asha@example.com");
        assertThat(profile.email()).isEqualTo("asha@example.com");
        assertThat(user.getLastLoginAt()).isEqualTo(Instant.parse("2026-10-31T06:00:00Z"));
        verify(securityContextRepository).saveContext(any(), any(), any());
    }

    @Test
    void wrongPasswordDoesNotCreateASession() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("asha@example.com", "wrong"),
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(BadCredentialsException.class);
        verify(securityContextRepository, never()).saveContext(any(), any(), any());
    }

    @Test
    void principalErasesItsPasswordHash() {
        AppUserDetails details = new AppUserDetails(1L, "a@b.c", "A", "$2a$hash", true, "STUDENT");
        details.eraseCredentials();
        assertThat(details.getPassword()).isNull();
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_STUDENT");
    }
}
