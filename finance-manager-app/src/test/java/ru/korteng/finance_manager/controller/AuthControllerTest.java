package ru.korteng.finance_manager.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.utility.TestcontainersConfiguration;
import ru.korteng.finance_manager.entity.User;
import ru.korteng.finance_manager.repository.UserRepository;
import ru.korteng.finance_manager.security.JwtUtil;
import ru.korteng.finance_manager.security.TokenBlacklistService;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestcontainersConfiguration.class)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @Test
    void register_NewUsername_Returns200WithToken() throws Exception {
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(42L);
            return u;
        });
        when(jwtUtil.generateToken(any(), anyString())).thenReturn("token123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"newuser","password":"pass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token123"));
    }

    @Test
    void register_ExistingUsername_Returns400() throws Exception {
        User existing = new User();
        existing.setUsername("taken");
        when(userRepository.findByUsername("taken")).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"taken","password":"pass123"}
                                """))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_ValidCredentials_Returns200WithToken() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("userA");
        user.setPasswordHash("hashed");
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "userA")).thenReturn("token456");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"userA","password":"pass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token456"));
    }

    @Test
    void login_NonexistentUser_Returns401WithGenericMessage() throws Exception {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ghost","password":"whatever"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void login_WrongPassword_Returns401WithSameGenericMessage() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("userA");
        user.setPasswordHash("hashed");
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "hashed")).thenReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"userA","password":"wrongpass"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void logout_ValidToken_Returns200AndBlacklistsToken() throws Exception {
        when(jwtUtil.getRemainingValidityMs("sometoken")).thenReturn(60000L);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer sometoken"))
                .andExpect(status().isOk());

        verify(tokenBlacklistService).blacklist(eq("sometoken"), eq(60000L));
    }
}