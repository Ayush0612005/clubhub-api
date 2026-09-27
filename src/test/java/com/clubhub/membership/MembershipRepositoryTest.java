package com.clubhub.membership;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.tenant.Tenant;
import com.clubhub.tenant.TenantRepository;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class MembershipRepositoryTest {

    @Autowired MembershipRepository memberships;
    @Autowired UserRepository users;
    @Autowired TenantRepository tenants;
    @Autowired EntityManager entityManager;

    @Test
    void userCanHoldDifferentRolesInDifferentClubs() {
        User user = user("multi@srmist.edu.in");
        Tenant coding = tenant("m_coding");
        Tenant robotics = tenant("m_robotics");

        memberships.saveAndFlush(new Membership(user.getId(), coding.getId(), ClubRole.CLUB_ADMIN));
        memberships.saveAndFlush(new Membership(user.getId(), robotics.getId(), ClubRole.MEMBER));
        entityManager.clear();

        assertThat(memberships.findByUserIdAndTenantId(user.getId(), coding.getId()))
                .get().extracting(Membership::getRole).isEqualTo(ClubRole.CLUB_ADMIN);
        assertThat(memberships.findByUserIdAndTenantId(user.getId(), robotics.getId()))
                .get().extracting(Membership::getRole).isEqualTo(ClubRole.MEMBER);
        assertThat(memberships.findAllByUserId(user.getId())).hasSize(2);
    }

    @Test
    void userCannotJoinTheSameClubTwice() {
        User user = user("twice@srmist.edu.in");
        Tenant club = tenant("m_twice");
        memberships.saveAndFlush(new Membership(user.getId(), club.getId(), ClubRole.MEMBER));

        assertThatThrownBy(() -> memberships.saveAndFlush(new Membership(user.getId(), club.getId(), ClubRole.CORE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeysRejectUnknownUserOrClub() {
        Tenant club = tenant("m_fk");

        assertThatThrownBy(() -> memberships.saveAndFlush(new Membership(UUID.randomUUID(), club.getId(), ClubRole.MEMBER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roleHierarchy() {
        assertThat(ClubRole.CLUB_ADMIN.atLeast(ClubRole.CORE)).isTrue();
        assertThat(ClubRole.CORE.atLeast(ClubRole.CORE)).isTrue();
        assertThat(ClubRole.MEMBER.atLeast(ClubRole.CORE)).isFalse();
    }

    private User user(String email) {
        return users.saveAndFlush(new User(email, "{noop}x", "Test User"));
    }

    private Tenant tenant(String slug) {
        return tenants.saveAndFlush(new Tenant(slug, slug));
    }
}
