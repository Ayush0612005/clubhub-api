package com.clubhub.tenant;

import com.clubhub.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformTenantControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void createsClubAndReturns201() throws Exception {
        mvc.perform(post("/api/platform/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"api_chess_club","name":"Chess Club"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.slug").value("api_chess_club"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        mvc.perform(get("/api/platform/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("api_chess_club")));
    }

    @Test
    void duplicateSlugIs409() throws Exception {
        String body = """
                {"slug":"api_quiz_club","name":"Quiz Club"}""";
        mvc.perform(post("/api/platform/tenants").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/platform/tenants").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Club with slug 'api_quiz_club' already exists"));
    }

    @Test
    void invalidBodyIs400ProblemDetail() throws Exception {
        mvc.perform(post("/api/platform/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"Bad Slug","name":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"));
    }
}
