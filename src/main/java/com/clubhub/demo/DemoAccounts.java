package com.clubhub.demo;

import com.clubhub.user.User;

/**
 * Names shared by the demo sandbox and the few places that must treat it specially.
 *
 * Every demo account lives under a {@code .invalid} domain: RFC 2606 reserves that TLD, so the
 * addresses can never belong to a real person and no mail provider will ever deliver to them.
 */
public final class DemoAccounts {

    public static final String DOMAIN = "demo.clubhub.invalid";
    /** The one account every "Try the demo" click signs in as. It has no usable password. */
    public static final String VISITOR_EMAIL = "visitor@" + DOMAIN;
    public static final String CLUB_SLUG = "demo";
    public static final String CLUB_NAME = "Demo Robotics Club";

    private DemoAccounts() {
    }

    public static boolean isDemoEmail(String email) {
        String normalized = User.normalizeEmail(email);
        return normalized != null && normalized.endsWith("@" + DOMAIN);
    }
}
