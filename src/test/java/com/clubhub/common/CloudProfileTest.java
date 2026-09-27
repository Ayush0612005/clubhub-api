package com.clubhub.common;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.notification.InProcessEventRelay;
import com.clubhub.notification.KafkaEventRelay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the free-hosting "cloud" profile. The env vars it requires get placeholder values; the
 * Testcontainers service connections stand in for Neon and Upstash.
 */
@SpringBootTest(properties = {
        "DATABASE_URL=jdbc:postgresql://replaced-by-service-connection/db",
        "DATABASE_USERNAME=unused",
        "DATABASE_PASSWORD=unused",
        "REDIS_URL=redis://replaced-by-service-connection:6379"})
@ActiveProfiles("cloud")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CloudProfileTest {

    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;

    @Test
    void startsWithoutKafkaAndDeliversEventsInProcess() {
        assertThat(context.getBeanNamesForType(KafkaTemplate.class)).isEmpty();
        assertThat(context.getBeanNamesForType(KafkaEventRelay.class)).isEmpty();
        assertThat(context.getBeanNamesForType(InProcessEventRelay.class)).hasSize(1);
    }

    @Test
    void healthIsPublicButMetricsAreNotExposed() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().is4xxClientError());
    }
}
