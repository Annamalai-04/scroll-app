package com.example.demo.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import com.example.demo.model.User;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;

    @Value("${app.jwt.expiration}")
    private long expiration;

    public JwtService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String generateToken(User user) {

        Instant now = Instant.now();

        Instant expiry = now.plus(
                expiration,
                ChronoUnit.MILLIS
        );

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("scrollserver")
                .issuedAt(now)
                .expiresAt(expiry)
                .subject(user.getId())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .build();

        JwsHeader header = JwsHeader.with(
                MacAlgorithm.HS256
        ).build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}