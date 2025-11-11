package com.selimhorri.app.e2e.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to generate JWT tokens for E2E tests
 * Mirrors the JWT generation logic from proxy-client Gateway
 */
public class JwtTestHelper {
    
    private static final String SECRET_KEY = "secret";
    private static final long EXPIRATION_TIME = 1000 * 60 * 60 * 10; // 10 hours
    
    /**
     * Generate a valid JWT token for testing
     * @param username The username to include in the token
     * @return JWT token string
     */
    public static String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, username);
    }
    
    @SuppressWarnings("deprecation") // Using deprecated API to match Gateway implementation
    private static String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
                .compact();
    }
    
    /**
     * Generate Authorization header value with Bearer token
     * @param username The username to generate token for
     * @return Authorization header value (e.g., "Bearer eyJhbGc...")
     */
    public static String getAuthorizationHeader(String username) {
        return "Bearer " + generateToken(username);
    }
}
