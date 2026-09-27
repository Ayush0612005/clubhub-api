package com.clubhub.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Bound from clubhub.jwt.*; startup fails fast if the secret is missing or too short for HS256. */
@Validated
@ConfigurationProperties("clubhub.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 bytes for HS256") String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl) {
}
