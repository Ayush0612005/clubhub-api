package com.clubhub.recruitment;

import com.clubhub.common.ConflictException;
import com.clubhub.common.NotFoundException;
import com.clubhub.recruitment.ApplicationDtos.AnswerRequest;
import com.clubhub.recruitment.ApplicationDtos.ApplyRequest;
import com.clubhub.recruitment.ApplicationDtos.MyApplicationResponse;
import com.clubhub.recruitment.DriveDtos.DriveResponse;
import com.clubhub.recruitment.DriveDtos.DriveSummary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The applicant's side of recruitment. Runs in the club schema bound by ClubBySlugFilter;
 * the applicant is always the authenticated caller, never an id from the request.
 */
@Service
public class StudentApplicationService {

    private final DriveRepository drives;
    private final ApplicationRepository applications;
    private final StatusChangeRepository statusChanges;

    public StudentApplicationService(DriveRepository drives, ApplicationRepository applications,
                                     StatusChangeRepository statusChanges) {
        this.drives = drives;
        this.applications = applications;
        this.statusChanges = statusChanges;
    }

    @Transactional(readOnly = true)
    public List<DriveSummary> openDrives() {
        Instant now = Instant.now();
        return drives.findAllByStatusOrderByCreatedAtDesc(DriveStatus.OPEN).stream()
                .filter(d -> d.isAcceptingApplications(now))
                .map(DriveSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DriveResponse openDrive(Long driveId) {
        return DriveResponse.from(drives.findWithQuestionsById(driveId)
                .filter(d -> d.getStatus() == DriveStatus.OPEN)
                .orElseThrow(() -> new NotFoundException("Drive not found")));
    }

    @Transactional
    public MyApplicationResponse apply(Long driveId, UUID applicantId, ApplyRequest request) {
        RecruitmentDrive drive = drives.findWithQuestionsById(driveId)
                .filter(d -> d.getStatus() != DriveStatus.DRAFT) // drafts don't exist for outsiders
                .orElseThrow(() -> new NotFoundException("Drive not found"));
        if (!drive.isAcceptingApplications(Instant.now())) {
            throw new ConflictException("This drive is not accepting applications");
        }
        if (applications.findByDriveIdAndApplicantUserId(driveId, applicantId).isPresent()) {
            throw new ConflictException("You have already applied to this drive");
        }

        Application application = new Application(driveId, applicantId);
        for (AnswerRequest answer : validAnswers(drive, request.answers())) {
            application.addAnswer(answer.questionId(), answer.answer().strip());
        }
        try {
            applications.saveAndFlush(application);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("You have already applied to this drive"); // double-click race
        }
        statusChanges.save(new ApplicationStatusChange(
                application.getId(), null, ApplicationStatus.APPLIED, applicantId, null));
        return MyApplicationResponse.from(application, drive.getTitle());
    }

    @Transactional(readOnly = true)
    public List<MyApplicationResponse> mine(UUID applicantId) {
        List<Application> mine = applications.findAllByApplicantUserIdOrderBySubmittedAtDesc(applicantId);
        Map<Long, String> titles = drives.findAllById(mine.stream().map(Application::getDriveId).distinct().toList())
                .stream().collect(Collectors.toMap(RecruitmentDrive::getId, RecruitmentDrive::getTitle));
        return mine.stream().map(a -> MyApplicationResponse.from(a, titles.get(a.getDriveId()))).toList();
    }

    @Transactional
    public MyApplicationResponse withdraw(Long applicationId, UUID applicantId) {
        // someone else's application is "not found", not "forbidden": ids reveal nothing
        Application application = applications.findById(applicationId)
                .filter(a -> a.getApplicantUserId().equals(applicantId))
                .orElseThrow(() -> new NotFoundException("Application not found"));
        ApplicationStatus previous;
        try {
            previous = application.moveTo(ApplicationStatus.WITHDRAWN);
        } catch (IllegalStateException e) {
            throw new ConflictException("This application can no longer be withdrawn");
        }
        statusChanges.save(new ApplicationStatusChange(
                applicationId, previous, ApplicationStatus.WITHDRAWN, applicantId, null));
        String title = drives.findById(application.getDriveId()).map(RecruitmentDrive::getTitle).orElse(null);
        return MyApplicationResponse.from(application, title);
    }

    /** Every answer must belong to this drive, at most once, and every required question must be answered. */
    private static List<AnswerRequest> validAnswers(RecruitmentDrive drive, List<AnswerRequest> answers) {
        Map<Long, DriveQuestion> questions = drive.getQuestions().stream()
                .collect(Collectors.toMap(DriveQuestion::getId, Function.identity()));
        Set<Long> answered = new HashSet<>();
        for (AnswerRequest answer : answers) {
            if (!questions.containsKey(answer.questionId())) {
                throw new IllegalArgumentException("Question " + answer.questionId() + " is not part of this drive");
            }
            if (!answered.add(answer.questionId())) {
                throw new IllegalArgumentException("Question " + answer.questionId() + " is answered twice");
            }
        }
        for (DriveQuestion q : drive.getQuestions()) {
            if (q.isRequired() && !answered.contains(q.getId())) {
                throw new IllegalArgumentException("Question " + q.getSortOrder() + " is required");
            }
        }
        return answers;
    }
}
