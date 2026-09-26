package com.clubhub.tenancy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @Test
    void isEmptyOutsideAScope() {
        assertThat(TenantContext.currentSchema()).isEmpty();
    }

    @Test
    void isSetOnlyInsideTheScope() {
        TenantContext.runAs("club_a", () ->
                assertThat(TenantContext.currentSchema()).contains("club_a"));

        // no clear() needed: the binding ended with the scope
        assertThat(TenantContext.currentSchema()).isEmpty();
    }

    @Test
    void nestedScopeOverridesAndRestores() throws Exception {
        String inner = TenantContext.callAs("club_a", () -> {
            String nested = TenantContext.callAs("club_b", () -> TenantContext.currentSchema().orElseThrow());
            assertThat(TenantContext.currentSchema()).contains("club_a");
            return nested;
        });

        assertThat(inner).isEqualTo("club_b");
    }

    @Test
    void rejectsBlankSchema() {
        assertThatThrownBy(() -> TenantContext.runAs(" ", () -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
