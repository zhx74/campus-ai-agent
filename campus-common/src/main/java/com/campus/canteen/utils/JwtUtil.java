package com.campus.canteen.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JwtUtil {

    private static SecretKey getKey(String secretKey) {
        return new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        long expMillis = System.currentTimeMillis() + ttlMillis;
        return Jwts.builder()
                .claims(claims)
                .expiration(new Date(expMillis))
                .signWith(getKey(secretKey))
                .compact();
    }

    public static Claims parseJWT(String secretKey, String token) {
        return Jwts.parser()
                .verifyWith(getKey(secretKey))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
