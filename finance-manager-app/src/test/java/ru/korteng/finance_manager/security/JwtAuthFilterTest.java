package ru.korteng.finance_manager.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthorizationHeader_DoesNotAuthenticate() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void headerWithoutBearerPrefix_DoesNotAuthenticate() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic somecreds");

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validNonBlacklistedToken_SetsAuthenticationWithUsernameAndUserId() throws Exception {
        Claims claims = mock(Claims.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer validtoken");
        when(jwtUtil.isValid("validtoken")).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted("validtoken")).thenReturn(false);
        when(jwtUtil.extractClaims("validtoken")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("userA");
        when(claims.get("userId", Long.class)).thenReturn(5L);

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("userA", auth.getPrincipal());
        assertEquals(5L, auth.getDetails());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void blacklistedToken_DoesNotAuthenticateEvenIfSignatureValid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer revokedtoken");
        when(jwtUtil.isValid("revokedtoken")).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted("revokedtoken")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtil, never()).extractClaims(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidToken_DoesNotAuthenticate() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer garbage");
        when(jwtUtil.isValid("garbage")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}