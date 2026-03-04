package com.sw103302.backend.service;

import com.sw103302.backend.dto.AuthResponse;
import com.sw103302.backend.dto.LoginRequest;
import com.sw103302.backend.dto.RegisterRequest;
import com.sw103302.backend.entity.RefreshToken;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.RefreshTokenRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenHashService tokenHashService;

    @Mock
    private TwoFactorService twoFactorService;

    @Mock
    private StringRedisTemplate redisTemplate;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                userSettingsRepository,
                passwordEncoder,
                jwtService,
                tokenHashService,
                twoFactorService,
                redisTemplate
        );
    }

    @Test
    void register_withNewEmail_shouldCreateUserAndReturnAccessAndRefreshToken() {
        RegisterRequest request = new RegisterRequest("newuser@example.com", "password123");
        String hashedPassword = "hashed_password";
        String expectedAccessToken = "jwt_token";

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn(hashedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.createAccessToken(anyString(), anyString())).thenReturn(expectedAccessToken);
        when(tokenHashService.hash(anyString())).thenReturn("refresh_hash");

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo(expectedAccessToken);
        assertThat(response.refreshToken()).isNotBlank();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(request.email());
        assertThat(savedUser.getPasswordHash()).isEqualTo(hashedPassword);
        assertThat(savedUser.getRole()).isEqualTo("ROLE_USER");
    }

    @Test
    void register_withExistingEmail_shouldThrowException() {
        RegisterRequest request = new RegisterRequest("existing@example.com", "password123");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("email already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_withValidCredentials_shouldReturnAccessAndRefreshToken() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        User user = new User("user@example.com", "hashed_password", "ROLE_USER");
        String expectedAccessToken = "jwt_token";

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
        when(userSettingsRepository.findByUser_Id(any())).thenReturn(Optional.empty());
        when(jwtService.createAccessToken(user.getEmail(), user.getRole())).thenReturn(expectedAccessToken);
        when(tokenHashService.hash(anyString())).thenReturn("refresh_hash");

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo(expectedAccessToken);
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.requires2FA()).isFalse();
    }

    @Test
    void login_withNonExistentEmail_shouldThrowException() {
        LoginRequest request = new LoginRequest("nonexistent@example.com", "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid credentials");
    }

    @Test
    void login_withWrongPassword_shouldThrowException() {
        LoginRequest request = new LoginRequest("user@example.com", "wrongpassword");
        User user = new User("user@example.com", "hashed_password", "ROLE_USER");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid credentials");

        verify(jwtService, never()).createAccessToken(anyString(), anyString());
    }

    @Test
    void refresh_withValidToken_shouldRotateRefreshToken() {
        User user = new User("user@example.com", "hash", "ROLE_USER");
        RefreshToken stored = new RefreshToken(
                user,
                "db_token_placeholder",
                "old_hash",
                LocalDateTime.now().plusDays(1)
        );

        when(tokenHashService.hash(anyString())).thenReturn("old_hash", "new_hash");
        when(refreshTokenRepository.findByTokenHash("old_hash")).thenReturn(Optional.of(stored));
        when(jwtService.createAccessToken(user.getEmail(), user.getRole())).thenReturn("new_access_token");

        AuthResponse response = authService.refreshAccessToken("old_raw_refresh_token");

        assertThat(response.accessToken()).isEqualTo("new_access_token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotEqualTo("old_raw_refresh_token");
    }
}
