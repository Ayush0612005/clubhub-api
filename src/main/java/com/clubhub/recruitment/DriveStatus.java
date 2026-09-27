package com.clubhub.recruitment;

/**
 * DRAFT (being prepared) -> OPEN (accepting applications) -> CLOSED (reviewing only).
 * A closed drive can be reopened (e.g. deadline extended); nothing goes back to DRAFT,
 * because students may already have applied against its questions.
 */
public enum DriveStatus {
    DRAFT,
    OPEN,
    CLOSED;

    public boolean canMoveTo(DriveStatus target) {
        return switch (this) {
            case DRAFT -> target == OPEN;
            case OPEN -> target == CLOSED;
            case CLOSED -> target == OPEN;
        };
    }
}
