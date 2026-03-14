package com.system.SchoolManagementSystem.config;

import com.system.SchoolManagementSystem.auth.service.CustomUserDetailsService;
import com.system.SchoolManagementSystem.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

    private final CustomUserDetailsService userDetailsService;
    private final JwtTokenUtil jwtTokenUtil;
    private final ObjectMapper objectMapper;

    // Public endpoints that bypass JWT validation
    private static final List<String> PUBLIC_ENDPOINTS = Arrays.asList(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh-token",
            "/api/auth/health",
            "/api/public/",
            "/actuator/",
            "/swagger-ui/",
            "/v3/api-docs/",
            "/api-docs/",
            "/webjars/",
            "/css/",
            "/js/",
            "/images/"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // CRITICAL: Handle OPTIONS requests immediately for CORS preflight
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        final String requestTokenHeader = request.getHeader("Authorization");
        String username = null;
        String jwtToken = null;

        // Extract JWT token from Authorization header
        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
            jwtToken = requestTokenHeader.substring(7);

            try {
                username = jwtTokenUtil.getUsernameFromToken(jwtToken);
                log.debug("Extracted username from JWT: {}", username);
            } catch (IllegalArgumentException e) {
                log.error("Unable to get JWT Token: {}", e.getMessage());
            } catch (ExpiredJwtException e) {
                log.error("JWT Token has expired");
                sendErrorResponse(response, "Token has expired", HttpStatus.UNAUTHORIZED.value());
                return;
            } catch (UnsupportedJwtException | MalformedJwtException | SignatureException e) {
                log.error("Invalid JWT Token: {}", e.getMessage());
                sendErrorResponse(response, "Invalid token", HttpStatus.UNAUTHORIZED.value());
                return;
            }
        } else {
            log.debug("No JWT token found or doesn't start with Bearer");
        }

        // Validate token and set authentication in context
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authenticationToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    log.debug("Authenticated user: {}", username);
                } else {
                    log.warn("Token validation failed for user: {}", username);
                    sendErrorResponse(response, "Invalid token", HttpStatus.UNAUTHORIZED.value());
                    return;
                }
            } catch (Exception e) {
                log.error("Error loading user details for {}: {}", username, e.getMessage());
                sendErrorResponse(response, "Authentication failed", HttpStatus.UNAUTHORIZED.value());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int status) throws IOException {
        response.setContentType("application/json");
        response.setStatus(status);

        ApiResponse<Object> errorResponse = ApiResponse.error(message);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Skip JWT filter for public endpoints
        boolean shouldSkip = PUBLIC_ENDPOINTS.stream()
                .anyMatch(path::startsWith);

        // Also skip for root and error pages
        shouldSkip = shouldSkip ||
                path.equals("/") ||
                path.equals("/error") ||
                path.equals("/favicon.ico");

        if (shouldSkip) {
            log.debug("Skipping JWT filter for path: {}", path);
        }

        return shouldSkip;
    }
}