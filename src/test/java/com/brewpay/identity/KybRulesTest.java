package com.brewpay.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brewpay.banking.fake.FakeKycProvider;
import com.brewpay.platform.correlation.CorrelationIds;
import com.brewpay.platform.tenant.TenantContext;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(KybRulesTest.SteppedClockConfig.class)
class KybRulesTest {

  private static final String PASSWORD = "password-1";
  private static final String REGISTRATION = "RC1234567";

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  static final SteppingClock CLOCK = new SteppingClock();

  static {
    postgres.start();
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeKycProvider fakeKyc;

  @BeforeEach
  void resetFakeKyc() {
    fakeKyc.clear();
  }

  @Test
  void anApprovedBusinessCanUseTheLedger() throws Exception {
    CreatedOrg ada = createOrganization("Ada Approved", "ada-kyb@a.example");
    String token = login(ada.id(), "ada-kyb@a.example");

    String body =
        submit(token, "Ada Trading", "LIMITED_COMPANY", REGISTRATION)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kycStatus").value("APPROVED"))
            .andExpect(jsonPath("$.organizationStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.organizationId").value(ada.id()))
            .andExpect(jsonPath("$.tradingName").value("Ada Trading"))
            .andExpect(jsonPath("$.businessType").value("LIMITED_COMPANY"))
            .andExpect(jsonPath("$.registrationNumber").value(REGISTRATION))
            .andExpect(header().exists(CorrelationIds.HEADER))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String submissionId = JsonPath.read(body, "$.id");
    assertEquals(ada.id(), fakeKyc.lastApplication().organizationId().toString());
    assertEquals("Ada Approved", fakeKyc.lastApplication().legalName());
    assertEquals(REGISTRATION, fakeKyc.lastApplication().registrationNumber());

    mockMvc
        .perform(
            get("/api/v1/kyb-submissions/" + submissionId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registrationNumber").value(REGISTRATION))
        .andExpect(jsonPath("$.organizationStatus").value("ACTIVE"));
    mockMvc
        .perform(
            post("/api/v1/ledger/accounts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson()))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    String audit = audit(token);
    assertEquals(1, count(audit, "KYB_SUBMITTED"));
    assertFalse(audit.contains(REGISTRATION));
  }

  @Test
  void anUnverifiedBusinessCannotMoveMoney() throws Exception {
    CreatedOrg ada = createOrganization("Ada Closed", "ada-kyb-closed@a.example");
    String token = login(ada.id(), "ada-kyb-closed@a.example");

    mockMvc
        .perform(
            post("/api/v1/ledger/accounts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Business verification is not complete."));
    mockMvc
        .perform(
            post("/api/v1/beneficiaries")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"destinationAccountNumber":"0123456789","destinationBankCode":"058"}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Business verification is not complete."));
    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Business verification is not complete."));
    assertEquals(0, fakeKyc.reviewCount());
    String audit = audit(token);
    assertEquals(0, count(audit, "ACCOUNT_CREATED"));
    assertEquals(0, count(audit, "JOURNAL_POSTED"));
    assertEquals(0, count(audit, "KYB_SUBMITTED"));
    mockMvc
        .perform(get("/api/v1/kyb-submissions").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void aPendingReviewCannotMoveMoney() throws Exception {
    CreatedOrg ada = createOrganization("Ada Pending", "ada-kyb-pending@a.example");
    String token = login(ada.id(), "ada-kyb-pending@a.example");
    fakeKyc.pendingNext();

    String body =
        submit(token, "Ada Trading", "SOLE_PROPRIETOR", null)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kycStatus").value("PENDING"))
            .andExpect(jsonPath("$.organizationStatus").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String submissionId = JsonPath.read(body, "$.id");

    mockMvc
        .perform(
            post("/api/v1/ledger/accounts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Business verification is not complete."));
    submit(token, "Ada Trading", "SOLE_PROPRIETOR", null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Business verification is already in review."));
    assertEquals(1, fakeKyc.reviewCount());
    mockMvc
        .perform(get("/api/v1/kyb-submissions").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(submissionId));
    assertEquals(0, count(audit(token), "ACCOUNT_CREATED"));
  }

  @Test
  void aRejectedBusinessCanSubmitAgain() throws Exception {
    CreatedOrg ada = createOrganization("Ada Rejected", "ada-kyb-rejected@a.example");
    String token = login(ada.id(), "ada-kyb-rejected@a.example");
    fakeKyc.rejectNext();

    String rejected =
        submit(token, "Ada Trading", "SOLE_PROPRIETOR", null)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kycStatus").value("REJECTED"))
            .andExpect(jsonPath("$.organizationStatus").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String rejectedId = JsonPath.read(rejected, "$.id");
    mockMvc
        .perform(
            post("/api/v1/ledger/accounts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Business verification is not complete."));

    String approved =
        submit(token, "Ada Trading Again", "PARTNERSHIP", "BN1234567")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kycStatus").value("APPROVED"))
            .andExpect(jsonPath("$.organizationStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.registrationNumber").value("BN1234567"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String approvedId = JsonPath.read(approved, "$.id");
    assertNotEquals(rejectedId, approvedId);
    mockMvc
        .perform(get("/api/v1/kyb-submissions").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(rejectedId))
        .andExpect(jsonPath("$[1].id").value(approvedId));
    mockMvc
        .perform(
            post("/api/v1/ledger/accounts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson()))
        .andExpect(status().isCreated());
    submit(token, "Ada Trading Again", "PARTNERSHIP", "BN1234567")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Business is already verified."));
    assertEquals(2, fakeKyc.reviewCount());
    assertFalse(audit(token).contains("BN1234567"));
  }

  @Test
  void anotherOrganizationCannotReadTheSubmission() throws Exception {
    CreatedOrg ada = createOrganization("Ada Kyb Wall", "ada-kyb-wall@a.example");
    CreatedOrg bola = createOrganization("Bola Kyb Wall", "bola-kyb-wall@b.example");
    String adaToken = login(ada.id(), "ada-kyb-wall@a.example");
    String bolaToken = login(bola.id(), "bola-kyb-wall@b.example");

    String adaId =
        JsonPath.read(
            submit(adaToken, "Ada Trading", "SOLE_PROPRIETOR", null)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    String bolaId =
        JsonPath.read(
            submit(bolaToken, "Bola Trading", "SOLE_PROPRIETOR", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(bola.id()))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    assertNotEquals(adaId, bolaId);

    mockMvc
        .perform(
            get("/api/v1/kyb-submissions/" + adaId)
                .header("Authorization", bearer(bolaToken))
                .header(TenantContext.HEADER, ada.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("KYB submission was not found."));
    mockMvc
        .perform(get("/api/v1/kyb-submissions").header("Authorization", bearer(adaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(adaId));
  }

  @Test
  void staffCannotSubmitKyb() throws Exception {
    CreatedOrg ada = createOrganization("Ada Kyb Staff", "ada-kyb-staff@a.example");
    String ownerToken = login(ada.id(), "ada-kyb-staff@a.example");
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson("Chi", "chi-kyb@a.example")))
        .andExpect(status().isCreated());
    String staffToken = login(ada.id(), "chi-kyb@a.example");

    submit(staffToken, "Ada Trading", "SOLE_PROPRIETOR", null)
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message").value("You do not have permission to perform this action."));
    assertEquals(0, fakeKyc.reviewCount());
    assertEquals(1, count(audit(ownerToken), "KYB_SUBMIT_DENIED"));
    mockMvc
        .perform(get("/api/v1/kyb-submissions").header("Authorization", bearer(staffToken)))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message").value("You do not have permission to perform this action."));
    assertEquals(1, count(audit(ownerToken), "KYB_SUBMIT_DENIED"));
    assertEquals(0, count(audit(ownerToken), "KYB_SUBMITTED"));
  }

  @Test
  void aLimitedCompanyRequiresARegistrationNumber() throws Exception {
    CreatedOrg ada = createOrganization("Ada Kyb Reg", "ada-kyb-reg@a.example");
    String token = login(ada.id(), "ada-kyb-reg@a.example");

    submit(token, "Ada Trading", "LIMITED_COMPANY", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Registration number is required."));
    submit(token, "Ada Trading", "LIMITED_COMPANY", "   ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Registration number is required."));
    submit(token, "Ada Trading", "PARTNERSHIP", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Registration number is required."));
    submit(token, "Ada Trading", "SHOP", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Business type is invalid."));
    assertEquals(0, fakeKyc.reviewCount());
    mockMvc
        .perform(get("/api/v1/kyb-submissions").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  private ResultActions submit(
      String token, String tradingName, String businessType, String registrationNumber)
      throws Exception {
    String registration =
        registrationNumber == null ? "" : "\"registrationNumber\": \"" + registrationNumber + "\",";
    return mockMvc.perform(
        post("/api/v1/kyb-submissions")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "tradingName": "%s",
                  "businessType": "%s",
                  %s
                  "addressLine": "12 Market Road",
                  "state": "Lagos",
                  "category": "Retail"
                }
                """
                    .formatted(tradingName, businessType, registration)));
  }

  private String audit(String token) throws Exception {
    return mockMvc
        .perform(get("/api/v1/audit-events").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static int count(String audit, String action) {
    int found = 0;
    List<String> actions = JsonPath.read(audit, "$[*].action");
    for (String value : actions) {
      if (action.equals(value)) {
        found++;
      }
    }
    return found;
  }

  private CreatedOrg createOrganization(String name, String email) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(organizationJson(name, email)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.kycStatus").value("NOT_SUBMITTED"))
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
        {"organizationId": "%s", "email": "%s", "password": "%s"}
        """
        .formatted(organizationId, email, password);
  }

  private static String accountJson() {
    return """
        {"code": "cash", "name": "Cash", "type": "ASSET"}
        """;
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
