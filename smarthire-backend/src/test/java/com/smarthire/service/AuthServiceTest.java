package com.smarthire.service;

import com.smarthire.dto.AuthResponse;
import com.smarthire.dto.LoginRequest;
import com.smarthire.dto.RegisterRequest;
import com.smarthire.model.User;
import com.smarthire.repository.UserRepository;
import com.smarthire.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .username("sarah.chen")
                .email("sarah@techcorp.io")
                .password("SecurePass123!")
                .build();

        loginRequest = LoginRequest.builder()
                .username("sarah.chen")
                .password("SecurePass123!")
                .build();

        savedUser = User.builder()
                .id(1L)
                .username("sarah.chen")
                .email("sarah@techcorp.io")
                .password("$2a$10$encodedpassword")
                .role(User.Role.RECRUITER)
                .build();
    }

    @Test
    void registerSuccess() {
        when(userRepository.existsByUsername("sarah.chen")).thenReturn(false);
        when(userRepository.existsByEmail("sarah@techcorp.io")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("$2a$10$encodedpassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(any())).thenReturn("jwt.token.here");

        AuthResponse result = authService.register(registerRequest);

        assertThat(result).isNotNull();
        assertThat(result.getToken()).isEqualTo("jwt.token.here");
        assertThat(result.getUsername()).isEqualTo("sarah.chen");
        assertThat(result.getRole()).isEqualTo("RECRUITER");
        verify(passwordEncoder, times(1)).encode("SecurePass123!");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerFailsWhenUsernameTaken() {
        when(userRepository.existsByUsername("sarah.chen")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Username already taken");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerFailsWhenEmailRegistered() {
        when(userRepository.existsByUsername("sarah.chen")).thenReturn(false);
        when(userRepository.existsByEmail("sarah@techcorp.io")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    void loginSuccess() {
        when(userRepository.findByUsername("sarah.chen")).thenReturn(Optional.of(savedUser));
        when(jwtService.generateToken(any())).thenReturn("jwt.token.here");

        AuthResponse result = authService.login(loginRequest);

        assertThat(result).isNotNull();
        assertThat(result.getToken()).isEqualTo("jwt.token.here");
        assertThat(result.getUsername()).isEqualTo("sarah.chen");
    }

    @Test
    void loginWrongPassword() {
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginUserNotFound() {
        when(userRepository.findByUsername("sarah.chen")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }
}