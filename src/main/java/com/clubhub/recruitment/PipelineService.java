package com.clubhub.recruitment;

import com.clubhub.common.ConflictException;
import com.clubhub.common.NotFoundException;
import com.clubhub.common.PageResponse;
import com.clubhub.membership.ClubRole;
import com.clubhub.membership.Membership;
import com.clubhub.membership.MembershipRepository;
import com.clubhub.recruitment.PipelineDtos.AnswerView;
import com.clubhub.recruitment.PipelineDtos.Applicant;
import com.clubhub.recruitment.PipelineDtos.ApplicationDetail;
import com.clubhub.recruitment.PipelineDtos.ApplicationSummary;
import com.clubhub.recruitment.PipelineDtos.StatusChangeView;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The core team's review pipeline: list applicants per drive, read an application, move it
 * through the state machine. Every move is recorded in application_status_changes.
 */
@Service
public class PipelineService {

    private final DriveRepository drives;
    private final ApplicationRepository applications;
    private final StatusChangeRepository statusChanges;
    private final UserRepository users;
    private final MembershipRepository memberships;

    public PipelineService(DriveRepository drives, ApplicationRepository applications,
                           StatusChangeRepository statusChanges, UserRepository users,
                           MembershipRepository memberships) {
        this.drives = drives;
        this.applications = applications;
        this.statusChanges = statusChanges;
        this.users = users;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationSummary> list(Long driveId, ApplicationStatus status, Pageable pageable) {
        if (!drives.existsById(driveId)) {
            throw new NotFoundException("Drive not found");
        }
        Page<Application> page = status == null
                ? applications.findAllByDriveId(driveId, pageable)
                : applications.findAllByDriveIdAndStatus(driveId, status, pageable);
        // one users query per page instead of one per row (avoids N+1)
        Map<UUID, User> byId = users.findAllById(page.getContent().stream()
                        .map(Application::getApplicantUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(page, a -> new ApplicationSummary(a.getId(),
                applicant(a.getApplicantUserId(), byId.get(a.getApplicantUserId())),
                a.getStatus(), a.getSubmittedAt(), a.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public ApplicationDetail get(Long applicationId) {
        return detail(find(applicationId));
    }

    /**
     * Moves an application to its next pipeline stage. Selecting an applicant also makes them a
     * MEMBER of the club (public.memberships) in the SAME transaction: both happen or neither.
     */
    @Transactional
    public ApplicationDetail transition(Long applicationId, ApplicationStatus target, String note,
                                        UUID reviewerId, UUID tenantId) {
        if (target == ApplicationStatus.WITHDRAWN) {
            throw new ConflictException("Only the applicant can withdraw an application");
        }
        Application application = find(applicationId);
        ApplicationStatus previous;
        try {
            previous = application.moveTo(target);
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        statusChanges.save(new ApplicationStatusChange(applicationId, previous, target, reviewerId,
                note == null || note.isBlank() ? null : note.strip()));

        if (target == ApplicationStatus.SELECTED
                && memberships.findByUserIdAndTenantId(application.getApplicantUserId(), tenantId).isEmpty()) {
            memberships.save(new Membership(application.getApplicantUserId(), tenantId, ClubRole.MEMBER));
        }
        return detail(application);
    }

    private Application find(Long applicationId) {
        return applications.findWithAnswersById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));
    }

    private ApplicationDetail detail(Application a) {
        Map<Long, String> prompts = drives.findWithQuestionsById(a.getDriveId())
                .map(d -> d.getQuestions().stream()
                        .collect(Collectors.toMap(DriveQuestion::getId, DriveQuestion::getPrompt)))
                .orElse(Map.of());
        List<AnswerView> answers = a.getAnswers().stream()
                .map(ans -> new AnswerView(ans.getQuestionId(), prompts.get(ans.getQuestionId()), ans.getAnswer()))
                .toList();
        List<StatusChangeView> history = statusChanges.findAllByApplicationIdOrderByChangedAtAsc(a.getId())
                .stream().map(StatusChangeView::from).toList();
        User user = users.findById(a.getApplicantUserId()).orElse(null);
        return new ApplicationDetail(a.getId(), a.getDriveId(), applicant(a.getApplicantUserId(), user),
                a.getStatus(), a.getSubmittedAt(), answers, history);
    }

    private static Applicant applicant(UUID id, User user) {
        return user == null ? new Applicant(id, null, null) : new Applicant(id, user.getEmail(), user.getFullName());
    }
}
