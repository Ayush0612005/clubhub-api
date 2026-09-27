package com.clubhub.security;

import com.clubhub.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real tokens, real filter chain: no mocked authentication in this class. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityRulesTest {

    @Autowired MockMvc mvc;

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/platform/tenants"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
        mvc.perform(get("/api/club/profile"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageOrForgedTokenIs401() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
        // structurally valid but unsigned ("alg":"none") token must never be accepted
        String unsigned = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhdHRhY2tlciIsInJvbGVzIjpbIlBMQVRGT1JNX0FETUlOIl19.";
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + unsigned))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsStayOpen() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@srmist.edu.in","password":"x"}"""))
                .andExpect(status().isUnauthorized())          // 401 from bad credentials,
                .andExpect(jsonPath("$.detail").exists());     // not from the security filter
    }

    @Test
    void realLoginTokenUnlocksProtectedEndpoints() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"sec.flow@srmist.edu.in","password":"flow-password-1","fullName":"Sec Flow"}"""))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"sec.flow@srmist.edu.in","password":"flow-password-1"}"""))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sec.flow@srmist.edu.in"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));

        // authenticated is not the same as authorized: a USER token can't reach platform admin endpoints
        mvc.perform(get("/api/platform/tenants").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
