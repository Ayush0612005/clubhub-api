package com.clubhub.auth;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.notification.email.EmailSender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "clubhub.auth.email-verification=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmailVerificationTest {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired MockMvc mvc;
    @MockitoBean EmailSender sender;

    @Test
    void newAccountMustConfirmEmailBeforeLoggingIn() throws Exception {
        String email = uniqueEmail("verify");
        register(email, "first-password-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationRequired").value(true));

        login(email, "first-password-1")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        String token = tokenFromEmail(email, "Confirm your ClubHub email");
        post("/api/auth/verify-email", "{\"token\":\"" + token + "\"}").andExpect(status().isNoContent());

        login(email, "first-password-1").andExpect(status().isOk());
        // single use
        post("/api/auth/verify-email", "{\"token\":\"" + token + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordOnUnverifiedAccountStillSaysInvalidCredentials() throws Exception {
        String email = uniqueEmail("unverified");
        register(email, "right-password-1").andExpect(status().isCreated());

        // the "not verified" hint is only for someone who knows the password
        login(email, "wrong-password-1").andExpect(status().isUnauthorized());
    }

    @Test
    void resendIsSilentForUnknownAddressesAndRespectsCooldown() throws Exception {
        post("/api/auth/resend-verification", "{\"email\":\"" + uniqueEmail("ghost") + "\"}")
                .andExpect(status().isAccepted());
        verify(sender, after(500).never()).send(contains("ghost"), anyString(), anyString());

        String email = uniqueEmail("cooldown");
        register(email, "some-password-1").andExpect(status().isCreated());
        verify(sender, timeout(5000)).send(eq(email), anyString(), anyString());
        clearInvocations(sender);

        // a second link within 60 s would just flood the inbox
        post("/api/auth/resend-verification", "{\"email\":\"" + email + "\"}").andExpect(status().isAccepted());
        verify(sender, after(500).never()).send(eq(email), anyString(), anyString());
    }

    @Test
    void passwordResetChangesPasswordEndsSessionsAndIsSingleUse() throws Exception {
        String email = uniqueEmail("reset");
        register(email, "old-password-1").andExpect(status().isCreated());
        String verifyToken = tokenFromEmail(email, "Confirm your ClubHub email");
        post("/api/auth/verify-email", "{\"token\":\"" + verifyToken + "\"}").andExpect(status().isNoContent());
        String refreshToken = JsonPath.read(login(email, "old-password-1").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.refreshToken");

        post("/api/auth/forgot-password", "{\"email\":\"" + email + "\"}").andExpect(status().isAccepted());
        String resetToken = tokenFromEmail(email, "Reset your ClubHub password");

        post("/api/auth/reset-password", "{\"token\":\"" + resetToken + "\",\"password\":\"new-password-1\"}")
                .andExpect(status().isNoContent());

        login(email, "old-password-1").andExpect(status().isUnauthorized());
        login(email, "new-password-1").andExpect(status().isOk());
        post("/api/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}").andExpect(status().isUnauthorized());
        post("/api/auth/reset-password", "{\"token\":\"" + resetToken + "\",\"password\":\"another-pass-1\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordForUnknownAddressLooksTheSame() throws Exception {
        post("/api/auth/forgot-password", "{\"email\":\"" + uniqueEmail("nobody") + "\"}")
                .andExpect(status().isAccepted());
        verify(sender, after(500).never()).send(contains("nobody"), anyString(), anyString());
    }

    @Test
    void verifyTokenCannotResetPassword() throws Exception {
        String email = uniqueEmail("purpose");
        register(email, "purpose-password-1").andExpect(status().isCreated());
        String verifyToken = tokenFromEmail(email, "Confirm your ClubHub email");

        post("/api/auth/reset-password", "{\"token\":\"" + verifyToken + "\",\"password\":\"hijack-pass-1\"}")
                .andExpect(status().isBadRequest());
    }

    private String tokenFromEmail(String to, String subject) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender, timeout(5000).atLeastOnce()).send(eq(to), eq(subject), body.capture());
        Matcher m = TOKEN.matcher(body.getValue());
        assertThat(m.find()).as("email body contains a token link").isTrue();
        return m.group(1);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@srmist.edu.in";
    }

    private ResultActions register(String email, String password) throws Exception {
        return post("/api/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"fullName\":\"Test Student\"}");
    }

    private ResultActions login(String email, String password) throws Exception {
        return post("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private ResultActions post(String url, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
