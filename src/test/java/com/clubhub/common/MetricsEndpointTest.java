package com.clubhub.common;

import com.clubhub.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Spring Boot switches metric exporters off in tests by default; this test is about the exporter.
@SpringBootTest(properties = {
        "management.defaults.metrics.export.enabled=true",
        "management.prometheus.metrics.export.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MetricsEndpointTest {

    @Autowired MockMvc mvc;

    @Test
    void prometheusScrapeExposesJvmHttpAndPoolMetricsTaggedWithTheApp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()); // produce an http.server.requests sample

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("hikaricp_connections_active")))
                .andExpect(content().string(containsString("http_server_requests_seconds_bucket")))
                .andExpect(content().string(containsString("application=\"clubhub-api\"")));
    }

    @Test
    void containerProbesArePublic() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void otherActuatorEndpointsStayClosed() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
        mvc.perform(get("/actuator/beans")).andExpect(status().is4xxClientError());
    }
}
