package com.clubhub.auth;

public final class AuthExceptions {

    private AuthExceptions() {
    }

    public static class EmailAlreadyUsedException extends RuntimeException {
        public EmailAlreadyUsedException() {
            super("An account with this email already exists");
        }
    }

    /** The address is outside the allowed college domains. The rule is public, so saying so leaks nothing. */
    public static class EmailNotAllowedException extends RuntimeException {
        public EmailNotAllowedException(java.util.Collection<String> allowedDomains) {
            super("Use your college email ("
                    + String.join(", ", allowedDomains.stream().sorted().map(d -> "@" + d).toList())
                    + ") to sign up or sign in");
        }
    }

    /** Only thrown after the password matched, so it doesn't reveal which addresses have accounts. */
    public static class EmailNotVerifiedException extends RuntimeException {
        public EmailNotVerifiedException() {
            super("Confirm your email first: open the link we sent you, or ask for a new one");
        }
    }

    /** Unknown, expired, already used or wrong-purpose email link: all look the same to the caller. */
    public static class InvalidEmailTokenException extends RuntimeException {
        public InvalidEmailTokenException() {
            super("This link is invalid or has expired. Ask for a new one.");
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
