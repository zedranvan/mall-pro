package com.mall.pro.common.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;

public class JWTUtil {

    private static final String SECRET = "MALL_PRO_JWT_SECRET_KEY_2026";
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET);
    private static final long EXPIRE_MILLIS = 7 * 24 * 3600 * 1000L; // 7 天过期

    /**
     * 生成 JWT 令牌
     */
    public static String generateToken(Long userId, String username) {
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRE_MILLIS))
                .sign(ALGORITHM);
    }

    /**
     * 校验并提取 Token 中的 userId
     */
    public static Long parseUserId(String token) {
        try {
            JWTVerifier verifier = JWT.require(ALGORITHM).build();
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getClaim("userId").asLong();
        } catch (Exception e) {
            return null;
        }
    }

}
