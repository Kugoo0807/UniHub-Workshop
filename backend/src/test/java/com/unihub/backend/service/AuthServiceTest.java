package com.unihub.backend.service;

import com.unihub.backend.dto.AdminStaffRegisterRequest;
import com.unihub.backend.dto.AuthResponse;
import com.unihub.backend.dto.LoginRequest;
import com.unihub.backend.dto.RefreshTokenRequest;
import com.unihub.backend.dto.StudentRegisterRequest;
import com.unihub.backend.dto.UserProfileResponse;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @Test
    void studentRegister_success_updatesAccountAndSaves() {
        StudentRegisterRequest request = studentRequest();
        User existing = baseUser(1L, Role.STUDENT.name(), "INACTIVE", null);
        existing.setStudentCode(request.getStudentCode());
        existing.setFullName(request.getFullName());
        existing.setEmail(request.getEmail());

        when(userRepository.findByStudentCodeAndFullNameAndEmail(
                request.getStudentCode(), request.getFullName(), request.getEmail()))
                .thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed");

        authService.studentRegister(request);

        assertEquals("hashed", existing.getPassword());
        assertEquals("ACTIVE", existing.getStatus());
        assertEquals(request.getPhoneNumber(), existing.getPhoneNumber());
        verify(userRepository).save(existing);
    }

    @Test
    void studentRegister_notFound_throwsIllegalArgumentException() {
        StudentRegisterRequest request = studentRequest();
        when(userRepository.findByStudentCodeAndFullNameAndEmail(
                request.getStudentCode(), request.getFullName(), request.getEmail()))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.studentRegister(request));
    }

    @Test
    void studentRegister_alreadyActivated_throwsIllegalArgumentException() {
        StudentRegisterRequest request = studentRequest();
        User existing = baseUser(1L, Role.STUDENT.name(), "ACTIVE", "hashed");
        existing.setStudentCode(request.getStudentCode());
        existing.setFullName(request.getFullName());
        existing.setEmail(request.getEmail());

        when(userRepository.findByStudentCodeAndFullNameAndEmail(
                request.getStudentCode(), request.getFullName(), request.getEmail()))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> authService.studentRegister(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void studentRegister_repositoryFailure_bubblesUp() {
        StudentRegisterRequest request = studentRequest();
        when(userRepository.findByStudentCodeAndFullNameAndEmail(
                request.getStudentCode(), request.getFullName(), request.getEmail()))
                .thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> authService.studentRegister(request));
    }

    @Test
    void adminStaffRegister_success_createsActiveUser() {
        AdminStaffRegisterRequest request = adminRequest(Role.ADMIN.name());
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed");

        authService.adminStaffRegister(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(request.getFullName(), saved.getFullName());
        assertEquals(request.getEmail(), saved.getEmail());
        assertEquals("hashed", saved.getPassword());
        assertEquals(request.getPhoneNumber(), saved.getPhoneNumber());
        assertEquals(Role.ADMIN.name(), saved.getRole());
        assertEquals("ACTIVE", saved.getStatus());
    }

    @Test
    void adminStaffRegister_invalidRole_throwsIllegalArgumentException() {
        AdminStaffRegisterRequest request = adminRequest("MANAGER");

        assertThrows(IllegalArgumentException.class, () -> authService.adminStaffRegister(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void adminStaffRegister_emailExists_throwsIllegalArgumentException() {
        AdminStaffRegisterRequest request = adminRequest(Role.STAFF.name());
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.adminStaffRegister(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void adminStaffRegister_repositoryFailure_bubblesUp() {
        AdminStaffRegisterRequest request = adminRequest(Role.ADMIN.name());
        when(userRepository.existsByEmail(request.getEmail())).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> authService.adminStaffRegister(request));
    }

    @Test
    void webLogin_success_returnsTokensForAdmin() {
        LoginRequest request = loginRequest();
        User user = baseUser(10L, Role.ADMIN.name(), "ACTIVE", "hashed");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);
        when(jwtUtil.generateAccessToken(user.getId(), user.getRole())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(user.getId(), user.getRole())).thenReturn("refresh");

        AuthResponse response = authService.webLogin(request);

        assertEquals("access", response.getAccessToken());
        assertEquals("refresh", response.getRefreshToken());
        assertEquals(String.valueOf(user.getId()), response.getUserId());
        assertEquals(user.getRole(), response.getRole());
        verify(jwtUtil).generateAccessToken(user.getId(), user.getRole());
        verify(jwtUtil).generateRefreshToken(user.getId(), user.getRole());
    }

    @Test
    void webLogin_staffRole_forbidden() {
        LoginRequest request = loginRequest();
        User user = baseUser(10L, Role.STAFF.name(), "ACTIVE", "hashed");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);

        assertThrows(ForbiddenOperationException.class, () -> authService.webLogin(request));
        verify(jwtUtil, never()).generateAccessToken(any(Long.class), any(String.class));
    }

    @Test
    void webLogin_inactiveAccount_throwsIllegalArgumentException() {
        LoginRequest request = loginRequest();
        User user = baseUser(10L, Role.ADMIN.name(), "INACTIVE", "hashed");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.webLogin(request));
    }

    @Test
    void appLogin_success_returnsTokensForStaff() {
        LoginRequest request = loginRequest();
        User user = baseUser(20L, Role.STAFF.name(), "ACTIVE", "hashed");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);
        when(jwtUtil.generateAccessToken(user.getId(), user.getRole())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(user.getId(), user.getRole())).thenReturn("refresh");

        AuthResponse response = authService.appLogin(request);

        assertEquals("access", response.getAccessToken());
        assertEquals("refresh", response.getRefreshToken());
        assertEquals(String.valueOf(user.getId()), response.getUserId());
        assertEquals(user.getRole(), response.getRole());
    }

    @Test
    void appLogin_studentRole_forbidden() {
        LoginRequest request = loginRequest();
        User user = baseUser(20L, Role.STUDENT.name(), "ACTIVE", "hashed");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);

        assertThrows(ForbiddenOperationException.class, () -> authService.appLogin(request));
    }

    @Test
    void appLogin_adminRole_forbidden() {
        LoginRequest request = loginRequest();
        User user = baseUser(20L, Role.ADMIN.name(), "ACTIVE", "hashed");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);

        assertThrows(ForbiddenOperationException.class, () -> authService.appLogin(request));
    }

    @Test
    void appLogin_invalidCredentials_throwsIllegalArgumentException() {
        LoginRequest request = loginRequest();
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.appLogin(request));
    }

    @Test
    void refreshToken_success_returnsNewTokens() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh").build();
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        User user = baseUser(99L, Role.STAFF.name(), "ACTIVE", "hashed");

        when(jwtUtil.validateRefreshToken(request.getRefreshToken())).thenReturn(claims);
        when(jwtUtil.extractUserId(claims)).thenReturn(user.getId());
        when(jwtUtil.extractRole(claims)).thenReturn(user.getRole());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(user.getId(), user.getRole())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(user.getId(), user.getRole())).thenReturn("refresh");

        AuthResponse response = authService.refreshToken(request);

        assertEquals("access", response.getAccessToken());
        assertEquals("refresh", response.getRefreshToken());
        assertEquals(String.valueOf(user.getId()), response.getUserId());
        assertEquals(user.getRole(), response.getRole());
    }

    @Test
    void refreshToken_userNotFound_throwsUnauthorized() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh").build();
        Claims claims = org.mockito.Mockito.mock(Claims.class);

        when(jwtUtil.validateRefreshToken(request.getRefreshToken())).thenReturn(claims);
        when(jwtUtil.extractUserId(claims)).thenReturn(99L);
        when(jwtUtil.extractRole(claims)).thenReturn(Role.STAFF.name());
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(request));
    }

    @Test
    void refreshToken_inactiveUser_throwsUnauthorized() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh").build();
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        User user = baseUser(99L, Role.STAFF.name(), "INACTIVE", "hashed");

        when(jwtUtil.validateRefreshToken(request.getRefreshToken())).thenReturn(claims);
        when(jwtUtil.extractUserId(claims)).thenReturn(user.getId());
        when(jwtUtil.extractRole(claims)).thenReturn(user.getRole());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(request));
    }

    @Test
    void refreshToken_expiredToken_throwsUnauthorized() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh").build();
        when(jwtUtil.validateRefreshToken(request.getRefreshToken()))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(request));
    }

    @Test
    void refreshToken_malformedToken_throwsUnauthorized() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh").build();
        when(jwtUtil.validateRefreshToken(request.getRefreshToken()))
                .thenThrow(new MalformedJwtException("bad"));

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(request));
    }

    @Test
    void refreshToken_securityException_throwsUnauthorized() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh").build();
        when(jwtUtil.validateRefreshToken(request.getRefreshToken()))
                .thenThrow(new SecurityException("bad"));

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(request));
    }

    @Test
    void getCurrentUserProfile_success_returnsProfile() {
        User user = baseUser(55L, Role.STUDENT.name(), "ACTIVE", "hashed");
        user.setStudentCode("S123");
        user.setPhoneNumber("123456");

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserProfileResponse response = authService.getCurrentUserProfile(user.getId());

        assertNotNull(response);
        assertEquals(String.valueOf(user.getId()), response.getUserId());
        assertEquals(user.getFullName(), response.getFullName());
        assertEquals(user.getEmail(), response.getEmail());
        assertEquals(user.getStudentCode(), response.getStudentCode());
        assertEquals(user.getPhoneNumber(), response.getPhoneNumber());
        assertEquals(user.getRole(), response.getRole());
        assertEquals(user.getStatus(), response.getStatus());
    }

    @Test
    void getCurrentUserProfile_userNotFound_throwsUnauthorized() {
        when(userRepository.findById(77L)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.getCurrentUserProfile(77L));
    }

    private StudentRegisterRequest studentRequest() {
        return StudentRegisterRequest.builder()
                .fullName("Student A")
                .studentCode("S001")
                .email("student@unihub.edu")
                .phoneNumber("123456789")
                .password("password123")
                .build();
    }

    private AdminStaffRegisterRequest adminRequest(String role) {
        return AdminStaffRegisterRequest.builder()
                .fullName("Admin User")
                .email("admin@unihub.edu")
                .phoneNumber("555000")
                .password("password123")
                .role(role)
                .build();
    }

    private LoginRequest loginRequest() {
        return LoginRequest.builder()
                .email("user@unihub.edu")
                .password("password123")
                .build();
    }

    private User baseUser(Long id, String role, String status, String password) {
        return User.builder()
                .id(id)
                .fullName("User Name")
                .email("user@unihub.edu")
                .password(password)
                .role(role)
                .status(status)
                .build();
    }
}

