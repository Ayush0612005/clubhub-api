package com.clubhub.auth;

public final class AuthExceptions {

    private AuthExceptions() {
    }

    public static class EmailAlreadyUsedException extends RuntimeException {
        public EmailAlreadyUsedException() {
            super("An account with this email already exists");
        }
    }

    /** Unknown, expired, revoked or reused refresh token: the client must log in again. */
    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException() {
            super("Invalid or expired refresh token");
        }
    }

    /** Deliberately vague: never reveal whether the email exists or the password was wrong. */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid email or password");
        }
    }
}
