package com.clubhub.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Bound from clubhub.storage.*. Credentials are NOT configured here in production: the AWS default
 * chain picks them up (env vars locally, the EC2 instance role in production). accessKey/secretKey
 * exist only for local emulators and tests.
 */
@Validated
@ConfigurationProperties("clubhub.storage")
public record StorageProperties(
        @NotBlank String bucket,
        @NotBlank String region,
        String endpoint,
        String accessKey,
        String secretKey,
        @NotNull Duration uploadUrlTtl,
        @NotNull Duration downloadUrlTtl,
        @Positive long maxUploadBytes) {

    boolean hasStaticCredentials() {
        return accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank();
    }
}
