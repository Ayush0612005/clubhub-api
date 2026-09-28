package com.clubhub.auth;

import com.clubhub.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtDecoder jwtDecoder;

    @Test
    void registerThenLoginReturnsVerifiableBearerToken() throws Exception {
        register("login.ok@srmist.edu.in", "correct-horse-battery", "Login Ok")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("login.ok@srmist.edu.in"))
                .andExpect(jsonPath("$.password").doesNotExist());

        String body = login("LOGIN.OK@srmist.edu.in", "correct-horse-battery")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
        assertThat(jwt.getClaimAsString("email")).isEqualTo("login.ok@srmist.edu.in");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
    }

    @Test
    void duplicateEmailIgnoringCaseIs409() throws Exception {
        register("dup.auth@srmist.edu.in", "password-123", "First").andExpect(status().isCreated());

        register("Dup.Auth@SRMIST.edu.in", "password-456", "Second")
                .andExpect(status().isConflict());
    }

    @Test
    void nonCollegeEmailCannotRegisterOrLogIn() throws Exception {
        register("someone@gmail.com", "password-123", "Outsider")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("@srmist.edu.in")));

        login("someone@gmail.com", "password-123").andExpect(status().isForbidden());
    }

    @Test
    void weakOrInvalidInputIs400() throws Exception {
        register("not-an-email", "short", "").andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordAndUnknownEmailLookIdentical() throws Exception {
        register("enum.check@srmist.edu.in", "right-password", "Enum Check").andExpect(status().isCreated());

        String wrongPassword = login("enum.check@srmist.edu.in", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody.here@srmist.edu.in", "whatever-password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // same status and same message: the API must not reveal which emails are registered
        assertThat(JsonPath.<String>read(wrongPassword, "$.detail"))
                .isEqualTo(JsonPath.<String>read(unknownEmail, "$.detail"))
                .isEqualTo("Invalid email or password");
    }

    private ResultActions register(String email, String password, String fullName) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","fullName":"%s"}""".formatted(email, password, fullName)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, password)));
    }
}
