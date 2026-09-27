package com.clubhub.recruitment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PipelineDtos {

    private PipelineDtos() {
    }

    public record Applicant(UUID userId, String email, String fullName) {
    }

    public record ApplicationSummary(Long id, Applicant applicant, ApplicationStatus status,
                                     Instant submittedAt, Instant updatedAt) {
    }

    public record AnswerView(Long questionId, String prompt, String answer) {
    }

    public record StatusChangeView(ApplicationStatus from, ApplicationStatus to, UUID changedBy,
                                   String note, Instant changedAt) {

        static StatusChangeView from(ApplicationStatusChange c) {
            return new StatusChangeView(c.getFromStatus(), c.getToStatus(), c.getChangedBy(), c.getNote(),
                    c.getChangedAt());
        }
    }

    public record ApplicationDetail(Long id, Long driveId, Applicant applicant, ApplicationStatus status,
                                    Instant submittedAt, List<AnswerView> answers, List<StatusChangeView> history) {
    }

    public record TransitionRequest(@NotNull ApplicationStatus status, @Size(max = 500) String note) {
    }
}
