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
@Import(KycRulesTest.SteppedClockConfig.class)
class KycRulesTest {

  private static final String PASSWORD = "password-1";
  private static final String IDENTIFICATION = "22123456789";

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
  void anApprovedIdentityCanUseTheLedger() throws Exception {
    CreatedOrg ada = createOrganization("Ada Person", "ada-kyc@a.example");
    String token = login(ada.id(), "ada-kyc@a.example");
    TestKyb.approveBusiness(mockMvc, token);

    String body =
        submit(token, "1990-04-12", "08031234567", IDENTIFICATION)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kycStatus").value("APPROVED"))
            .andExpect(jsonPath("$.organizationId").value(ada.id()))
            .andExpect(jsonPath("$.userId").value(ada.ownerId()))
            .andExpect(jsonPath("$.identificationNumber").value(IDENTIFICATION))
            .andExpect(header().exists(CorrelationIds.HEADER))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String submissionId = JsonPath.read(body, "$.id");
    assertEquals("Ada Okeke", fakeKyc.lastPerson().legalName());
    assertEquals(IDENTIFICATION, fakeKyc.lastPerson().identificationNumber());

    mockMvc
        .perform(
            get("/api/v1/kyc-submissions/" + submissionId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.identificationNumber").value(IDENTIFICATION));
    mockMvc
        .perform(get("/api/v1/users/" + ada.ownerId()).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kycStatus").value("APPROVED"));
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
    assertEquals(1, count(audit, "KYC_SUBMITTED"));
    assertFalse(audit.contains(IDENTIFICATION));
  }

  @Test
  void anUnverifiedIdentityCannotMoveMoney() throws Exception {
    CreatedOrg ada = createOrganization("Ada Person Closed", "ada-kyc-closed@a.example");
    String token = login(ada.id(), "ada-kyc-closed@a.example");
    TestKyb.approveBusiness(mockMvc, token);

    mockMvc
        .perform(
            post("/api/v1/ledger/accounts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Identity verification is not complete."));
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
        .andExpect(jsonPath("$.message").value("Identity verification is not complete."));
    assertEquals(0, fakeKyc.personReviewCount());
    assertEquals(0, count(audit(token), "ACCOUNT_CREATED"));
    assertEquals(0, count(audit(token), "KYC_SUBMITTED"));
  }

  @Test
  void aPendingIdentityCannotMoveMoney() throws Exception {
    CreatedOrg ada = createOrganization("Ada Person Pending", "ada-kyc-pending@a.example");
    String token = login(ada.id(), "ada-kyc-pending@a.example");
    TestKyb.approveBusiness(mockMvc, token);
    fakeKyc.pendingPersonNext();

    String body =
        submit(token, "1990-04-12", "08031234567", IDENTIFICATION)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kycStatus").value("PENDING"))
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
        .andExpect(jsonPath("$.message").value("Identity verification is not complete."));
    submit(token, "1990-04-12", "08031234567", IDENTIFICATION)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Identity verification is already in review."));
    assertEquals(1, fakeKyc.personReviewCount());
    mockMvc
        .perform(get("/api/v1/kyc-submissions").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(submissionId));
  }

  @Test
  void aRejectedIdentityCanSubmitAgain() throws Exception {
    CreatedOrg ada = createOrganization("Ada Person Rejected", "ada-kyc-rejected@a.example");
    String token = login(ada.id(), "ada-kyc-rejected@a.example");
    TestKyb.approveBusiness(mockMvc, token);
    fakeKyc.rejectPersonNext();

    String rejected =
        submit(token, "1990-04-12", "08031234567", IDENTIFICATION)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kycStatus").value("REJECTED"))
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
        .andExpect(jsonPath("$.message").value("Identity verification is not complete."));

    String approved =
        submit(token, "1990-04-12", "+2348031234567", "10987654321")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kycStatus").value("APPROVED"))
            .andExpect(jsonPath("$.identificationNumber").value("10987654321"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String approvedId = JsonPath.read(approved, "$.id");
    assertNotEquals(rejectedId, approvedId);
    mockMvc
        .perform(get("/api/v1/kyc-submissions").header("Authorization", bearer(token)))
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
    submit(token, "1990-04-12", "08031234567", "10987654321")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Identity is already verified."));
    assertEquals(2, fakeKyc.personReviewCount());
    assertFalse(audit(token).contains("10987654321"));
    assertFalse(audit(token).contains(IDENTIFICATION));
  }

  @Test
  void anotherOrganizationCannotReadTheIdentity() throws Exception {
    CreatedOrg ada = createOrganization("Ada Kyc Wall", "ada-kyc-wall@a.example");
    CreatedOrg bola = createOrganization("Bola Kyc Wall", "bola-kyc-wall@b.example");
    String adaToken = login(ada.id(), "ada-kyc-wall@a.example");
    String bolaToken = login(bola.id(), "bola-kyc-wall@b.example");

    String adaId =
        JsonPath.read(
            submit(adaToken, "1990-04-12", "08031234567", IDENTIFICATION)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    String bolaId =
        JsonPath.read(
            submit(bolaToken, "1991-05-02", "08039876543", "10987654321")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(bola.id()))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    assertNotEquals(adaId, bolaId);
    mockMvc
        .perform(
            get("/api/v1/kyc-submissions/" + adaId)
                .header("Authorization", bearer(bolaToken))
                .header(TenantContext.HEADER, ada.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("KYC submission was not found."));
    mockMvc
        .perform(get("/api/v1/kyc-submissions").header("Authorization", bearer(adaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(adaId));
  }

  @Test
  void aUserCannotReadAnotherUsersIdentity() throws Exception {
    CreatedOrg ada = createOrganization("Ada Kyc Staff", "ada-kyc-staff@a.example");
    String ownerToken = login(ada.id(), "ada-kyc-staff@a.example");
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson("Chi", "chi-kyc@a.example")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kycStatus").value("NOT_SUBMITTED"));
    String staffToken = login(ada.id(), "chi-kyc@a.example");

    String staffSubmission =
        JsonPath.read(
            submit(staffToken, "1992-08-01", "08031112233", IDENTIFICATION)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    mockMvc
        .perform(
            get("/api/v1/kyc-submissions/" + staffSubmission)
                .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("KYC submission was not found."));
    mockMvc
        .perform(get("/api/v1/kyc-submissions").header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mockMvc
        .perform(get("/api/v1/kyc-submissions").header("Authorization", bearer(staffToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(staffSubmission));
    assertEquals(1, fakeKyc.personReviewCount());
  }

  @Test
  void anInvalidIdentityDoesNotAskTheProvider() throws Exception {
    CreatedOrg ada = createOrganization("Ada Kyc Invalid", "ada-kyc-invalid@a.example");
    String token = login(ada.id(), "ada-kyc-invalid@a.example");

    submit(token, "yesterday", "08031234567", IDENTIFICATION)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Date of birth is invalid."));
    submit(token, "2027-01-02", "08031234567", IDENTIFICATION)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Date of birth is invalid."));
    submit(token, "1990-04-12", "12", IDENTIFICATION)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Phone number is invalid."));
    submit(token, "1990-04-12", "08031234567", "12345")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Identification number is invalid."));
    assertEquals(0, fakeKyc.personReviewCount());
    mockMvc
        .perform(get("/api/v1/kyc-submissions").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  private ResultActions submit(
      String token, String dateOfBirth, String phone, String identification) throws Exception {
    return mockMvc.perform(
        post("/api/v1/kyc-submissions")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "dateOfBirth": "%s",
                  "phone": "%s",
                  "identificationNumber": "%s"
                }
                """
                    .formatted(dateOfBirth, phone, identification)));
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
            .andExpect(jsonPath("$.owner.kycStatus").value("NOT_SUBMITTED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new CreatedOrg(JsonPath.read(body, "$.id"), JsonPath.read(body, "$.owner.id"));
  }

  private String login(String organizationId, String email) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"organizationId": "%s", "email": "%s", "password": "%s"}
                        """
                            .formatted(organizationId, email, PASSWORD)))
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

  private static String accountJson() {
    return """
        {"code": "cash", "name": "Cash", "type": "ASSET"}
        """;
  }

  private record CreatedOrg(String id, String ownerId) {}

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
