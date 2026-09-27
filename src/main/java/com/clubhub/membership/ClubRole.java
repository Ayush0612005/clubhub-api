package com.clubhub.membership;

/** Role of a user inside ONE club. Ordered from most to least privileged. */
public enum ClubRole {
    CLUB_ADMIN,
    CORE,
    MEMBER;

    /** True if this role has at least the privileges of {@code required}. */
    public boolean atLeast(ClubRole required) {
        return this.ordinal() <= required.ordinal();
    }
}
