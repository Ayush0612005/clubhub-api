package com.clubhub.plan;

/** Thrown when a club hits a plan limit (409) or uses a feature its plan doesn't include (403). */
public final class PlanExceptions {

    private PlanExceptions() {
    }

    public static class PlanLimitExceededException extends RuntimeException {
        public PlanLimitExceededException(Plan plan, String what, int limit) {
            super("The " + plan + " plan allows at most " + limit + " " + what + ". Upgrade to PRO for more.");
        }
    }

    public static class FeatureNotAvailableException extends RuntimeException {
        public FeatureNotAvailableException(Plan plan, Feature feature) {
            super(feature + " is not included in this club's " + plan + " plan.");
        }
    }
}
