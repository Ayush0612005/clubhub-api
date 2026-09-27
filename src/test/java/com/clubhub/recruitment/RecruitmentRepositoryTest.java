package com.clubhub.recruitment;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.tenancy.TenantContext;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.user.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

import static com.clubhub.support.TestAuth.newUserId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecruitmentRepositoryTest {

    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;
    @Autowired DriveRepository drives;
    @Autowired ApplicationRepository applications;
    @Autowired TransactionTemplate tx;

    String schema;
    String otherSchema;
    UUID ownerId;

    @BeforeAll
    void createClubs() {
        ownerId = newUserId(users);
        schema = provisioningService.provision("recruit_repo_club", "Recruit Repo Club", ownerId).getSchemaName();
        otherSchema = provisioningService.provision("recruit_repo_other", "Other Club", ownerId).getSchemaName();
    }

    /** Runs the block in the club's schema inside its own transaction. */
    <T> T inClub(String clubSchema, Supplier<T> block) {
        return TenantContext.supplyAs(clubSchema, () -> tx.execute(status -> block.get()));
    }

    Long newDrive(String clubSchema, String title) {
        return inClub(clubSchema, () -> {
            RecruitmentDrive drive = new RecruitmentDrive(title, "Join us", null, ownerId);
            drive.addQuestion("Why do you want to join?", true);
            drive.addQuestion("Portfolio link", false);
            return drives.save(drive).getId();
        });
    }

    @Test
    void savesDriveWithOrderedQuestions() {
        Long driveId = newDrive(schema, "Tech team 2026");

        RecruitmentDrive loaded = inClub(schema, () -> drives.findWithQuestionsById(driveId).orElseThrow());

        assertThat(loaded.getStatus()).isEqualTo(DriveStatus.DRAFT);
        assertThat(loaded.getQuestions()).extracting(DriveQuestion::getPrompt)
                .containsExactly("Why do you want to join?", "Portfolio link");
    }

    @Test
    void drivesAreIsolatedPerClub() {
        newDrive(schema, "Only in the first club");

        long otherCount = inClub(otherSchema, () -> drives.findAllByOrderByCreatedAtDesc().stream()
                .filter(d -> d.getTitle().equals("Only in the first club")).count());

        assertThat(otherCount).isZero();
    }

    @Test
    void savesApplicationWithAnswers() {
        Long driveId = newDrive(schema, "Design team");
        UUID applicant = newUserId(users);

        Long appId = inClub(schema, () -> {
            RecruitmentDrive drive = drives.findWithQuestionsById(driveId).orElseThrow();
            Application app = new Application(driveId, applicant);
            app.addAnswer(drive.getQuestions().get(0).getId(), "I love design");
            return applications.save(app).getId();
        });

        Application loaded = inClub(schema, () -> applications.findWithAnswersById(appId).orElseThrow());
        assertThat(loaded.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(loaded.getAnswers()).extracting(ApplicationAnswer::getAnswer).containsExactly("I love design");

        long applied = inClub(schema, () -> applications
                .findAllByDriveIdAndStatus(driveId, ApplicationStatus.APPLIED, PageRequest.of(0, 10))
                .getTotalElements());
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void oneApplicationPerStudentPerDrive() {
        Long driveId = newDrive(schema, "Events team");
        UUID applicant = newUserId(users);
        inClub(schema, () -> applications.save(new Application(driveId, applicant)));

        assertThatThrownBy(() -> inClub(schema, () -> applications.saveAndFlush(new Application(driveId, applicant))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void applicantMustBeARealUser() {
        Long driveId = newDrive(schema, "Media team");

        // cross-schema FK: club_x.applications.applicant_user_id -> public.users(id)
        assertThatThrownBy(() -> inClub(schema,
                () -> applications.saveAndFlush(new Application(driveId, UUID.randomUUID()))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stateMachineAllowsOnlyForwardMoves() {
        Application app = new Application(1L, UUID.randomUUID());

        assertThat(app.moveTo(ApplicationStatus.SHORTLISTED)).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(app.moveTo(ApplicationStatus.INTERVIEW)).isEqualTo(ApplicationStatus.SHORTLISTED);
        assertThatThrownBy(() -> app.moveTo(ApplicationStatus.APPLIED)).isInstanceOf(IllegalStateException.class);

        app.moveTo(ApplicationStatus.SELECTED);
        assertThat(ApplicationStatus.SELECTED.isTerminal()).isTrue();
        assertThatThrownBy(() -> app.moveTo(ApplicationStatus.WITHDRAWN)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void driveAcceptsApplicationsOnlyWhileOpenAndBeforeDeadline() {
        Instant now = Instant.now();
        RecruitmentDrive drive = new RecruitmentDrive("t", null, now.plus(1, ChronoUnit.DAYS), ownerId);
        assertThat(drive.isAcceptingApplications(now)).isFalse(); // DRAFT

        drive.changeStatus(DriveStatus.OPEN);
        assertThat(drive.isAcceptingApplications(now)).isTrue();
        assertThat(drive.isAcceptingApplications(now.plus(2, ChronoUnit.DAYS))).isFalse();
    }
}
