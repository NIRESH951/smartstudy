package com.smartstudy.user;

import com.smartstudy.common.TimeUtils;
import com.smartstudy.common.exception.BadRequestException;
import com.smartstudy.common.exception.ResourceNotFoundException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile, Pomodoro preferences and password management for the signed-in student. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User require(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        return ProfileResponse.from(require(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = require(userId);
        user.setFullName(request.fullName().trim());
        user.setTimeZone(TimeUtils.requireValidZone(request.timeZone().trim()));
        user.setFocusMinutes(request.focusMinutes());
        user.setShortBreakMinutes(request.shortBreakMinutes());
        user.setLongBreakMinutes(request.longBreakMinutes());
        user.setLongBreakInterval(request.longBreakInterval());
        return ProfileResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = require(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("currentPassword", "Current password is incorrect.");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BadRequestException("newPassword", "New password must be different from the current one.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }
}
