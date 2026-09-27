package com.clubhub.recruitment;

import java.util.EnumSet;
import java.util.Set;

/**
 * The recruitment pipeline as an explicit state machine. Every allowed move is listed here, so an
 * illegal jump (e.g. APPLIED straight to SELECTED, or reviving a REJECTED application) is impossible
 * no matter which endpoint or service tries it.
 */
public enum ApplicationStatus {
    APPLIED,
    SHORTLISTED,
    INTERVIEW,
    SELECTED,
    REJECTED,
    WITHDRAWN;

    public Set<ApplicationStatus> nextStatuses() {
        return switch (this) {
            case APPLIED -> EnumSet.of(SHORTLISTED, REJECTED, WITHDRAWN);
            case SHORTLISTED -> EnumSet.of(INTERVIEW, REJECTED, WITHDRAWN);
            case INTERVIEW -> EnumSet.of(SELECTED, REJECTED, WITHDRAWN);
            case SELECTED, REJECTED, WITHDRAWN -> EnumSet.noneOf(ApplicationStatus.class); // terminal
        };
    }

    public boolean canMoveTo(ApplicationStatus target) {
        return nextStatuses().contains(target);
    }

    public boolean isTerminal() {
        return nextStatuses().isEmpty();
    }
}
