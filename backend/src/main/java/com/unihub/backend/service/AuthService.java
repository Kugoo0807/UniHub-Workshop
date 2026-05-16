package com.unihub.backend.service;

import com.unihub.backend.dto.*;
import com.unihub.backend.entity.User;
import com.unihub.backend.enums.Role;
import com.unihub.backend.exception.ForbiddenOperationException;
import com.unihub.backend.exception.UnauthorizedException;
import com.unihub.backend.repository.UserRepository;
import com.unihub.backend.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public void studentRegister(StudentRegisterRequest request) {
        User user = userRepository
                .findByStudentCodeAndFullNameAndEmail(
                        request.getStudentCode(),
                        request.getFullName(),
                        request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Student information not found in the system. Please contact the administrator."));

        if (user.getPassword() != null) {
            throw new IllegalArgumentException("This account has already been activated. Please login instead.");
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setStatus("ACTIVE");
        userRepository.save(user);

        log.info("Student account activated: {}", user.getEmail());
    }

    @Transactional
    public void adminStaffRegister(AdminStaffRegisterRequest request) {
        String role = request.getRole().toUpperCase();
        if (!role.equals(Role.ADMIN.name()) && !role.equals(Role.STAFF.name())) {
            throw new IllegalArgumentException("Invalid role. Must be ADMIN or STAFF.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Account with this email already exists.");
        }

        User newUser = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(role)
                .status("ACTIVE")
                .build();

        userRepository.save(newUser);

        log.info("Admin/Staff account created: {}", newUser.getEmail());
    }

    public AuthResponse webLogin(LoginRequest request) {
        User user = authenticateUser(request.getEmail(), request.getPassword());

        if (Role.STAFF.name().equalsIgnoreCase(user.getRole())) {
            throw new ForbiddenOperationException(
                    "Staff accounts are not allowed to login via Web. Please use the mobile app.");
        }

        return buildAuthResponse(user);
    }

    public AuthResponse appLogin(LoginRequest request) {
        User user = authenticateUser(request.getEmail(), request.getPassword());

        if (Role.STUDENT.name().equalsIgnoreCase(user.getRole())
                || Role.ADMIN.name().equalsIgnoreCase(user.getRole())) {
            throw new ForbiddenOperationException(
                    "Only Staff accounts are allowed to login via mobile app.");
        }

        return buildAuthResponse(user);
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        try {
            Claims claims = jwtUtil.validateRefreshToken(request.getRefreshToken());
            Long userId = jwtUtil.extractUserId(claims);
            String role = jwtUtil.extractRole(claims);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UnauthorizedException("User not found"));

            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                throw new UnauthorizedException("Account is not active");
            }

            return buildAuthResponse(user);

        } catch (ExpiredJwtException | MalformedJwtException | SecurityException e) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        return UserProfileResponse.builder()
                .userId(String.valueOf(user.getId()))
                .fullName(user.getFullName())
                .email(user.getEmail())
                .studentCode(user.getStudentCode())
                .phoneNumber(user.getPhoneNumber())
                .chatId(user.getChatId())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }

    private User authenticateUser(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalArgumentException("Account is not active. Please contact the administrator.");
        }

        return user;
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getRole());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(String.valueOf(user.getId()))
                .role(user.getRole())
                .build();
    }
}
