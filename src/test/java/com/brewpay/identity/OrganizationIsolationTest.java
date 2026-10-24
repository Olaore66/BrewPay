package com.brewpay.identity;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brewpay.platform.correlation.CorrelationIds;
import com.brewpay.platform.tenant.TenantContext;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
@Import(OrganizationIsolationTest.SteppedClockConfig.class)
class OrganizationIsolationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  static {
    postgres.start();
  }

  @Autowired private MockMvc mockMvc;

  @Test
  void organizationACannotReadOrganizationBUser() throws Exception {
    CreatedOrg ada = createOrganization("Ada Stores", "Ada@A.example");
    CreatedOrg bola = createOrganization("Bola Stores", "bola@b.example");

    mockMvc
        .perform(get("/api/v1/users/" + ada.ownerId()).header(TenantContext.HEADER, ada.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ada@a.example"))
        .andExpect(jsonPath("$.organizationId").value(ada.id()));

    mockMvc
        .perform(get("/api/v1/users/" + bola.ownerId()).header(TenantContext.HEADER, ada.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("User was not found."))
        .andExpect(jsonPath("$.details").isEmpty())
        .andExpect(header().exists(CorrelationIds.HEADER))
        .andExpect(jsonPath("$.correlationId").exists())
        .andExpect(jsonPath("$.email").doesNotExist());
  }

  @Test
  void unknownUserLooksLikeAForeignUser() throws Exception {
    CreatedOrg ada = createOrganization("Ada Unknown", "ada-unknown@a.example");

    mockMvc
        .perform(get("/api/v1/users/" + UUID.randomUUID()).header(TenantContext.HEADER, ada.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("User was not found."));
  }

  @Test
  void listReturnsOnlyTheCallerOrganizationInCreatedOrder() throws Exception {
    CreatedOrg ada = createOrganization("Ada List", "ada-list@a.example");
    CreatedOrg bola = createOrganization("Bola List", "bola-list@b.example");
    String chiId = createUser(ada.id(), "Chi", "chi-list@a.example");

    mockMvc
        .perform(get("/api/v1/users").header(TenantContext.HEADER, ada.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", contains(ada.ownerId(), chiId)))
        .andExpect(jsonPath("$[*].organizationId", contains(ada.id(), ada.id())))
        .andExpect(jsonPath("$[*].email", contains("ada-list@a.example", "chi-list@a.example")));

    mockMvc
        .perform(get("/api/v1/users").header(TenantContext.HEADER, bola.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].email", contains("bola-list@b.example")));
  }

  @Test
  void emailIsUniqueInsideOneOrganization() throws Exception {
    CreatedOrg ada = createOrganization("Ada Unique", "Ada.Unique@A.example");

    mockMvc
        .perform(
            post("/api/v1/users")
                .header(TenantContext.HEADER, ada.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson("Ada", "ada.unique@a.example")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(
            jsonPath("$.message")
                .value("A user with this email already exists in the organization."));
  }

  @Test
  void theSameEmailCanExistInAnotherOrganization() throws Exception {
    createOrganization("Ada Shared", "shared@example.com");
    CreatedOrg bola = createOrganization("Bola Shared", "bola-shared@b.example");

    mockMvc
        .perform(
            post("/api/v1/users")
                .header(TenantContext.HEADER, bola.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson("Ada", "shared@example.com")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("shared@example.com"))
        .andExpect(jsonPath("$.organizationId").value(bola.id()));
  }

  @Test
  void userRoutesRequireAValidOrganizationHeader() throws Exception {
    mockMvc
        .perform(get("/api/v1/users"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("Organization context is required."));

    mockMvc
        .perform(get("/api/v1/users").header(TenantContext.HEADER, "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Organization context is invalid."));
  }

  @Test
  void aMissingOrganizationCannotGainAUser() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users")
                .header(TenantContext.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson("Ada", "missing-org@a.example")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Organization was not found."));
  }

  @Test
  void organizationContextDoesNotLeakToTheNextRequest() throws Exception {
    CreatedOrg ada = createOrganization("Ada Leak", "ada-leak@a.example");

    mockMvc
        .perform(get("/api/v1/users").header(TenantContext.HEADER, ada.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("ada-leak@a.example"));

    mockMvc
        .perform(get("/api/v1/users"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Organization context is required."));
  }

  @Test
  void creatingAnOrganizationRejectsABlankName() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(organizationJson(" ", "blank-name@a.example")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("Request validation failed."));
  }

  private CreatedOrg createOrganization(String name, String email) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(organizationJson(name, email)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.owner.status").value("ACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new CreatedOrg(JsonPath.read(body, "$.id"), JsonPath.read(body, "$.owner.id"));
  }

  private String createUser(String organizationId, String firstName, String email)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header(TenantContext.HEADER, organizationId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(userJson(firstName, email)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private static String organizationJson(String name, String email) {
    return """
        {
          "name": "%s",
          "owner": {
            "firstName": "Ada",
            "lastName": "Okeke",
            "email": "%s"
          }
        }
        """
        .formatted(name, email);
  }

  private static String userJson(String firstName, String email) {
    return """
        {
          "firstName": "%s",
          "lastName": "Nwosu",
          "email": "%s"
        }
        """
        .formatted(firstName, email);
  }

  private record CreatedOrg(String id, String ownerId) {}

  @TestConfiguration
  static class SteppedClockConfig {

    @Bean
    @Primary
    Clock steppedClock() {
      return new SteppingClock();
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
