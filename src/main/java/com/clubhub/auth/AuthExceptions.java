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

    /**
     * Same answer for "club doesn't exist", "club suspended" and "you're not a member":
     * a non-member must not be able to probe which club slugs exist.
     */
    public static class ClubAccessDeniedException extends RuntimeException {
        public ClubAccessDeniedException() {
            super("You are not a member of this club");
        }
    }

    /** Deliberately vague: never reveal whether the email exists or the password was wrong. */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid email or password");
        }
    }
}
