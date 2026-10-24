package com.brewpay.identity;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brewpay.platform.correlation.CorrelationIds;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(LoginSessionTest.SteppedClockConfig.class)
class LoginSessionTest {

  private static final String PASSWORD = "password-1";

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  static final SteppingClock CLOCK = new SteppingClock();

  static {
    postgres.start();
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionPolicy sessionPolicy;

  @Test
  void wrongCredentialsShareOneMessage() throws Exception {
    CreatedOrg ada = createOrganization("Ada Login", "Ada.Login@A.example");

    mockMvc
        .perform(
            post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionJson(ada.id(), "Ada.Login@A.example", "wrong-password")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").value("Email or password is wrong."))
        .andExpect(jsonPath("$.details").isEmpty())
        .andExpect(header().exists(CorrelationIds.HEADER));

    mockMvc
        .perform(
            post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionJson(ada.id(), "missing@a.example", PASSWORD)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Email or password is wrong."));

    mockMvc
        .perform(
            post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    sessionJson(UUID.randomUUID().toString(), "Ada.Login@A.example", PASSWORD)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Email or password is wrong."));

    String token = login(ada.id(), "ada.login@a.example");
    String audit =
        mockMvc
            .perform(get("/api/v1/audit-events").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> actions = JsonPath.read(audit, "$[*].action");
    assertEquals(2, actions.stream().filter("SESSION_REJECTED"::equals).count());
    assertEquals(false, audit.contains(PASSWORD));
    assertEquals(false, audit.contains(token));
  }

  @Test
  void theSameEmailLogsIntoTheNamedOrganization() throws Exception {
    CreatedOrg ada = createOrganization("Ada Same", "shared-login@example.com");
    CreatedOrg bola = createOrganization("Bola Same", "shared-login@example.com");

    String adaToken = login(ada.id(), "shared-login@example.com");
    String bolaToken = login(bola.id(), "shared-login@example.com");

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(adaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].organizationId", contains(ada.id())));

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(bolaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].organizationId", contains(bola.id())));
  }

  @Test
  void revokingOneSessionLeavesTheOther() throws Exception {
    CreatedOrg ada = createOrganization("Ada Revoke", "ada-revoke@a.example");
    String first = login(ada.id(), "ada-revoke@a.example");
    String second = login(ada.id(), "ada-revoke@a.example");

    mockMvc
        .perform(delete("/api/v1/sessions/current").header("Authorization", bearer(first)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(first)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Session is invalid."));

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(second)))
        .andExpect(status().isOk());
  }

  @Test
  void anExpiredSessionIsRejected() throws Exception {
    CreatedOrg ada = createOrganization("Ada Expiry", "ada-expiry@a.example");
    String token = login(ada.id(), "ada-expiry@a.example");

    CLOCK.advance(sessionPolicy.ttl().plusHours(1));

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(token)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Session is invalid."));
  }

  @Test
  void staffCannotAddAUserOrReadTheAudit() throws Exception {
    CreatedOrg ada = createOrganization("Ada Staff", "ada-staff@a.example");
    String ownerToken = login(ada.id(), "ada-staff@a.example");
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson("Chi", "chi-staff@a.example")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("STAFF"));

    String staffToken = login(ada.id(), "chi-staff@a.example");
    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(staffToken)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson("Dee", "dee-staff@a.example")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(
            jsonPath("$.message").value("You do not have permission to perform this action."))
        .andExpect(jsonPath("$.details").isEmpty());

    mockMvc
        .perform(get("/api/v1/audit-events").header("Authorization", bearer(staffToken)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/v1/audit-events").header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[*].action",
                contains(
                    "USER_CREATED",
                    "SESSION_CREATED",
                    "USER_CREATED",
                    "SESSION_CREATED",
                    "USER_CREATE_DENIED",
                    "AUDIT_READ_DENIED")));
  }

  @Test
  void auditStaysInsideTheOrganizationAndOmitsSecrets() throws Exception {
    CreatedOrg ada = createOrganization("Ada Audit", "ada-audit@a.example");
    CreatedOrg bola = createOrganization("Bola Audit", "bola-audit@b.example");
    String adaToken = login(ada.id(), "ada-audit@a.example");
    login(bola.id(), "bola-audit@b.example");

    mockMvc
        .perform(delete("/api/v1/sessions/current").header("Authorization", bearer(adaToken)))
        .andExpect(status().isNoContent());

    String fresh = login(ada.id(), "ada-audit@a.example");
    String body =
        mockMvc
            .perform(get("/api/v1/audit-events").header("Authorization", bearer(fresh)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].organizationId", not(hasItem(bola.id()))))
            .andExpect(content().string(not(containsString(PASSWORD))))
            .andExpect(content().string(not(containsString(fresh))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> actions = JsonPath.read(body, "$[*].action");
    assertEquals(true, actions.contains("SESSION_CREATED"));
    assertEquals(true, actions.contains("SESSION_REVOKED"));
  }

  @Test
  void healthStaysOpenAndAShortPasswordIsRejected() throws Exception {
    mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Short",
                      "owner": {
                        "firstName": "Ada",
                        "lastName": "Okeke",
                        "email": "short@a.example",
                        "password": "short"
                      }
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  private CreatedOrg createOrganization(String name, String email) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(organizationJson(name, email)))
            .andExpect(status().isCreated())
            .andExpect(content().string(not(containsString(PASSWORD))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new CreatedOrg(JsonPath.read(body, "$.id"));
  }

  private String login(String organizationId, String email) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(sessionJson(organizationId, email, PASSWORD)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.token");
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private static String organizationJson(String name, String email) {
    return """
        {
          "name": "%s",
          "owner": {
            "firstName": "Ada",
            "lastName": "Okeke",
            "email": "%s",
            "password": "%s"
          }
        }
        """
        .formatted(name, email, PASSWORD);
  }

  private static String userJson(String firstName, String email) {
    return """
        {
          "firstName": "%s",
          "lastName": "Nwosu",
          "email": "%s",
          "password": "%s"
        }
        """
        .formatted(firstName, email, PASSWORD);
  }

  private static String sessionJson(String organizationId, String email, String password) {
    return """
        {
          "organizationId": "%s",
          "email": "%s",
          "password": "%s"
        }
        """
        .formatted(organizationId, email, password);
  }

  private record CreatedOrg(String id) {}

  @TestConfiguration
  static class SteppedClockConfig {

    @Bean
    @Primary
    Clock steppedClock() {
      return CLOCK;
    }
  }

  static final class SteppingClock extends Clock {

    private Instant current = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public synchronized Instant instant() {
      Instant value = current;
      current = current.plusSeconds(1);
      return value;
    }

    synchronized void advance(Duration duration) {
      current = current.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
