package com.brewpay.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brewpay.banking.fake.FakeNameEnquiryProvider;
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
@Import(NameEnquiryRulesTest.SteppedClockConfig.class)
class NameEnquiryRulesTest {

  private static final String PASSWORD = "password-1";
  private static final String ACCOUNT = "0123456789";
  private static final String BANK = "058";

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  static final SteppingClock CLOCK = new SteppingClock();

  static {
    postgres.start();
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeNameEnquiryProvider fakeBank;

  @BeforeEach
  void resetFakeBank() {
    fakeBank.clear();
  }

  @Test
  void anEnquiryReturnsTheProviderName() throws Exception {
    CreatedOrg ada = createOrganization("Ada Name", "ada-name@a.example");
    String token = login(ada.id(), "ada-name@a.example");
    fakeBank.answerNext("Ada Merchant");

    String first =
        enquire(token, ACCOUNT, BANK)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accountName").value("Ada Merchant"))
            .andExpect(jsonPath("$.organizationId").value(ada.id()))
            .andExpect(jsonPath("$.destinationAccountNumber").value(ACCOUNT))
            .andExpect(jsonPath("$.destinationBankCode").value(BANK))
            .andExpect(header().exists(CorrelationIds.HEADER))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String enquiryId = JsonPath.read(first, "$.id");

    fakeBank.answerNext("Ada Merchant");
    String second =
        enquire(token, ACCOUNT, BANK)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accountName").value("Ada Merchant"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secondId = JsonPath.read(second, "$.id");
    assertNotEquals(enquiryId, secondId);
    assertEquals(2, fakeBank.enquireCount());

    mockMvc
        .perform(
            get("/api/v1/account-name-enquiries/" + enquiryId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountName").value("Ada Merchant"));
    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mockMvc
        .perform(get("/api/v1/transfers").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    String audit = audit(token);
    assertEquals(2, count(audit, "ACCOUNT_NAME_ENQUIRED"));
    assertFalse(audit.contains(ACCOUNT));
  }

  @Test
  void aMissingAccountDoesNotStoreAnEnquiry() throws Exception {
    CreatedOrg ada = createOrganization("Ada Name Miss", "ada-name-miss@a.example");
    String token = login(ada.id(), "ada-name-miss@a.example");
    fakeBank.missNext();

    enquire(token, ACCOUNT, BANK)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Account name was not found."));
    assertEquals(1, fakeBank.enquireCount());
    mockMvc
        .perform(get("/api/v1/account-name-enquiries").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    assertEquals(0, count(audit(token), "ACCOUNT_NAME_ENQUIRED"));
  }

  @Test
  void aSilentProviderDoesNotStoreAnEnquiry() throws Exception {
    CreatedOrg ada = createOrganization("Ada Name Timeout", "ada-name-timeout@a.example");
    String token = login(ada.id(), "ada-name-timeout@a.example");
    fakeBank.timeoutNext();

    enquire(token, ACCOUNT, BANK)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Provider did not answer."));
    assertEquals(1, fakeBank.enquireCount());
    mockMvc
        .perform(get("/api/v1/account-name-enquiries").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    assertEquals(0, count(audit(token), "ACCOUNT_NAME_ENQUIRED"));
  }

  @Test
  void anotherOrganizationCannotReadTheEnquiry() throws Exception {
    CreatedOrg ada = createOrganization("Ada Name Isolation", "ada-name-iso@a.example");
    CreatedOrg bola = createOrganization("Bola Name Isolation", "bola-name-iso@b.example");
    String adaToken = login(ada.id(), "ada-name-iso@a.example");
    String bolaToken = login(bola.id(), "bola-name-iso@b.example");
    fakeBank.answerNext("Ada Merchant");
    String enquiryId =
        JsonPath.read(
            enquire(adaToken, ACCOUNT, BANK)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    mockMvc
        .perform(
            get("/api/v1/account-name-enquiries/" + enquiryId)
                .header("Authorization", bearer(bolaToken))
                .header(TenantContext.HEADER, ada.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Account name enquiry was not found."));
    mockMvc
        .perform(get("/api/v1/account-name-enquiries").header("Authorization", bearer(bolaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mockMvc
        .perform(get("/api/v1/account-name-enquiries").header("Authorization", bearer(adaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void staffCannotEnquireAnAccountName() throws Exception {
    CreatedOrg ada = createOrganization("Ada Name Staff", "ada-name-staff@a.example");
    String ownerToken = login(ada.id(), "ada-name-staff@a.example");
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson("Chi", "chi-name@a.example")))
        .andExpect(status().isCreated());
    String staffToken = login(ada.id(), "chi-name@a.example");

    enquire(staffToken, ACCOUNT, BANK)
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message").value("You do not have permission to perform this action."));
    assertEquals(0, fakeBank.enquireCount());
    assertEquals(1, count(audit(ownerToken), "ACCOUNT_NAME_ENQUIRY_DENIED"));
    mockMvc
        .perform(get("/api/v1/account-name-enquiries").header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void anInvalidAccountDoesNotAskTheProvider() throws Exception {
    CreatedOrg ada = createOrganization("Ada Name Invalid", "ada-name-invalid@a.example");
    String token = login(ada.id(), "ada-name-invalid@a.example");

    enquire(token, "12345", BANK)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Destination account number is invalid."));
    enquire(token, ACCOUNT, "12")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Destination bank code is invalid."));
    assertEquals(0, fakeBank.enquireCount());
    mockMvc
        .perform(get("/api/v1/account-name-enquiries").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  private ResultActions enquire(String token, String accountNumber, String bankCode)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/account-name-enquiries")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"destinationAccountNumber":"%s","destinationBankCode":"%s"}
                """
                    .formatted(accountNumber, bankCode)
                    .trim()));
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
                    .content(
                        """
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
                            .formatted(name, email, PASSWORD)))
            .andExpect(status().isCreated())
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

  private static String userJson(String firstName, String email) {
    return """
        {"firstName": "%s", "lastName": "Nwosu", "email": "%s", "password": "%s"}
        """
        .formatted(firstName, email, PASSWORD);
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
