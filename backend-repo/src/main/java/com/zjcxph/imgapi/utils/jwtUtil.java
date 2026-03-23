package com.zjcxph.imgapi.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;

public class jwtUtil {

    static String secret = "sbkedbkvuirkhkpwzetralhtaenrqlhio";

    public static String getToken(String username)
    {
        return JWT.create().withClaim("username", username)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000 * 24))
                .sign(Algorithm.HMAC256(secret));
    }

    public static String parseToken(String token)
    {
        return JWT.require(Algorithm.HMAC256(secret)).build().verify(token).getClaim("username").asString();
    }
}
