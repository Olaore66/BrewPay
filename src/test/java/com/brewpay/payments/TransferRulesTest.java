package com.brewpay.payments;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brewpay.banking.fake.FakeTransferProvider;
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
@Import(TransferRulesTest.SteppedClockConfig.class)
class TransferRulesTest {

  private static final String PASSWORD = "password-1";
  private static final long KOBO = 250_000L;
  private static final String ACCOUNT = "0123456789";
  private static final String BANK = "058";

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  static final SteppingClock CLOCK = new SteppingClock();

  static {
    postgres.start();
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeTransferProvider fakeBank;

  @BeforeEach
  void resetFakeBank() {
    fakeBank.clearScript();
  }

  @Test
  void aTimedOutTransferStaysUnknown() throws Exception {
    CreatedOrg ada = createOrganization("Ada Timeout", "ada-timeout@a.example");
    String token = login(ada.id(), "ada-timeout@a.example");
    String clearing = createAccount(token, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(token, "payable", "Merchant payable", "LIABILITY");
    String key = "xfer-unknown-01";
    fakeBank.timeoutNextSubmit();

    String first =
        submit(token, key, clearing, merchant, KOBO, ACCOUNT, BANK)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("UNKNOWN"))
            .andExpect(jsonPath("$.organizationId").value(ada.id()))
            .andExpect(jsonPath("$.amountKobo").value(250000))
            .andExpect(jsonPath("$.providerReference").value(nullValue()))
            .andExpect(jsonPath("$.journalId").value(nullValue()))
            .andExpect(header().exists(CorrelationIds.HEADER))
            .andExpect(content().string(not(containsString(key))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String transferId = JsonPath.read(first, "$.id");

    submit(token, key, clearing, merchant, KOBO, ACCOUNT, BANK)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(transferId))
        .andExpect(jsonPath("$.status").value("UNKNOWN"));
    assertEquals(1, fakeBank.submitCount());

    submit(token, key, clearing, merchant, 100_000L, ACCOUNT, BANK)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(
            jsonPath("$.message")
                .value("Idempotency key was already used with a different request."))
        .andExpect(jsonPath("$.details").isEmpty());
    assertEquals(1, fakeBank.submitCount());

    mockMvc
        .perform(get("/api/v1/transfers/" + transferId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNKNOWN"))
        .andExpect(jsonPath("$.journalId").value(nullValue()));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    String audit = audit(token);
    assertEquals(1, count(audit, "TRANSFER_SUBMITTED"));
    assertEquals(0, count(audit, "JOURNAL_POSTED"));
    assertFalse(audit.contains(key));
    assertFalse(audit.contains(ACCOUNT));
  }

  @Test
  void aSuccessfulTransferPostsOneJournal() throws Exception {
    CreatedOrg ada = createOrganization("Ada Paid Out", "ada-paid-out@a.example");
    String token = login(ada.id(), "ada-paid-out@a.example");
    String clearing = createAccount(token, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(token, "payable", "Merchant payable", "LIABILITY");

    String first =
        submit(token, "xfer-paid-0001", clearing, merchant, KOBO, ACCOUNT, BANK)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
            .andExpect(jsonPath("$.providerReference", startsWith("FAKE-XFER-")))
            .andExpect(jsonPath("$.amountKobo").value(250000))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String journalId = JsonPath.read(first, "$.journalId");
    String transferId = JsonPath.read(first, "$.id");

    submit(token, "xfer-paid-0001", clearing, merchant, KOBO, ACCOUNT, BANK)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(transferId))
        .andExpect(jsonPath("$.journalId").value(journalId));
    assertEquals(1, fakeBank.submitCount());

    mockMvc
        .perform(get("/api/v1/ledger/journals/" + journalId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("NGN"))
        .andExpect(jsonPath("$.lines[0].accountId").value(merchant))
        .andExpect(jsonPath("$.lines[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.lines[0].amountKobo").value(250000))
        .andExpect(jsonPath("$.lines[1].accountId").value(clearing))
        .andExpect(jsonPath("$.lines[1].direction").value("CREDIT"))
        .andExpect(jsonPath("$.lines[1].amountKobo").value(250000));

    String second =
        submit(token, "xfer-paid-0002", clearing, merchant, KOBO, ACCOUNT, BANK)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secondJournal = JsonPath.read(second, "$.journalId");
    assertNotEquals(journalId, secondJournal);

    mockMvc
        .perform(get("/api/v1/transfers").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
    assertEquals(2, fakeBank.submitCount());
  }

  @Test
  void aFailedTransferDoesNotPost() throws Exception {
    CreatedOrg ada = createOrganization("Ada Failed Xfer", "ada-failed-xfer@a.example");
    String token = login(ada.id(), "ada-failed-xfer@a.example");
    String clearing = createAccount(token, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(token, "payable", "Merchant payable", "LIABILITY");
    fakeBank.failNextSubmit();

    String first =
        submit(token, "xfer-fail-0001", clearing, merchant, KOBO, ACCOUNT, BANK)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.providerReference", startsWith("FAKE-XFER-")))
            .andExpect(jsonPath("$.journalId").value(nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String transferId = JsonPath.read(first, "$.id");

    submit(token, "xfer-fail-0001", clearing, merchant, KOBO, ACCOUNT, BANK)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(transferId))
        .andExpect(jsonPath("$.status").value("FAILED"));
    assertEquals(1, fakeBank.submitCount());

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void aRejectedTransferDoesNotConsumeTheKey() throws Exception {
    CreatedOrg ada = createOrganization("Ada Reject Xfer", "ada-reject-xfer@a.example");
    String token = login(ada.id(), "ada-reject-xfer@a.example");
    String clearing = createAccount(token, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(token, "payable", "Merchant payable", "LIABILITY");

    submit(token, "xfer-reject-002", clearing, merchant, KOBO, "12345", BANK)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Destination account number is invalid."));
    submit(token, "xfer-reject-001", clearing, merchant, 0, ACCOUNT, BANK)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Transfer amount must be positive."));
    assertEquals(0, fakeBank.submitCount());

    submit(token, "xfer-reject-001", clearing, merchant, KOBO, ACCOUNT, BANK)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESSFUL"));
    assertEquals(1, fakeBank.submitCount());
    mockMvc
        .perform(get("/api/v1/transfers").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void anotherOrganizationCannotReadTheTransfer() throws Exception {
    CreatedOrg ada = createOrganization("Ada Sends", "ada-sends@a.example");
    CreatedOrg bola = createOrganization("Bola Sends", "bola-sends@b.example");
    String adaToken = login(ada.id(), "ada-sends@a.example");
    String bolaToken = login(bola.id(), "bola-sends@b.example");
    String clearing = createAccount(adaToken, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(adaToken, "payable", "Merchant payable", "LIABILITY");
    fakeBank.timeoutNextSubmit();
    String body =
        submit(adaToken, "xfer-unknown-02", clearing, merchant, KOBO, ACCOUNT, BANK)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String transferId = JsonPath.read(body, "$.id");

    mockMvc
        .perform(
            get("/api/v1/transfers/" + transferId)
                .header("Authorization", bearer(bolaToken))
                .header(TenantContext.HEADER, ada.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Transfer was not found."));
    mockMvc
        .perform(get("/api/v1/transfers").header("Authorization", bearer(bolaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mockMvc
        .perform(get("/api/v1/transfers").header("Authorization", bearer(adaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void staffCannotSubmitATransfer() throws Exception {
    CreatedOrg ada = createOrganization("Ada Staff Xfer", "ada-staff-xfer@a.example");
    String ownerToken = login(ada.id(), "ada-staff-xfer@a.example");
    String clearing = createAccount(ownerToken, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(ownerToken, "payable", "Merchant payable", "LIABILITY");
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson("Chi", "chi-xfer@a.example")))
        .andExpect(status().isCreated());
    String staffToken = login(ada.id(), "chi-xfer@a.example");
    String key = "xfer-staff-0001";

    submit(staffToken, key, clearing, merchant, KOBO, ACCOUNT, BANK)
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message").value("You do not have permission to perform this action."));
    assertEquals(0, fakeBank.submitCount());
    assertEquals(1, count(audit(ownerToken), "TRANSFER_SUBMIT_DENIED"));

    submit(ownerToken, key, clearing, merchant, KOBO, ACCOUNT, BANK)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESSFUL"));
    assertEquals(1, fakeBank.submitCount());
    mockMvc
        .perform(get("/api/v1/transfers").header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void aTransferUsesAnAssetAndALiability() throws Exception {
    CreatedOrg ada = createOrganization("Ada Xfer Types", "ada-xfer-types@a.example");
    String token = login(ada.id(), "ada-xfer-types@a.example");
    String cash = createAccount(token, "cash", "Cash", "ASSET");
    String sales = createAccount(token, "sales", "Sales", "INCOME");

    submit(token, "xfer-type-0001", cash, cash, KOBO, ACCOUNT, BANK)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Transfer uses two different ledger accounts."));
    submit(token, "xfer-type-0002", sales, cash, KOBO, ACCOUNT, BANK)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Clearing account must be an asset."));
    submit(token, "xfer-type-0003", cash, sales, KOBO, ACCOUNT, BANK)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Merchant account must be a liability."));
    assertEquals(0, fakeBank.submitCount());
    mockMvc
        .perform(get("/api/v1/transfers").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
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

  private ResultActions submit(
      String token,
      String key,
      String clearing,
      String merchant,
      long amount,
      String accountNumber,
      String bankCode)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/transfers")
            .header("Authorization", bearer(token))
            .header(IdempotencyKeys.HEADER, key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(transferJson(clearing, merchant, amount, accountNumber, bankCode)));
  }

  private String createAccount(String token, String code, String name, String type)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/ledger/accounts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"code": "%s", "name": "%s", "type": "%s"}
                        """
                            .formatted(code, name, type)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
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

  private static String transferJson(
      String clearing, String merchant, long amount, String accountNumber, String bankCode) {
    return """
        {"clearingAccountId":"%s","merchantAccountId":"%s","amountKobo":%d,"destinationAccountNumber":"%s","destinationBankCode":"%s"}
        """
        .formatted(clearing, merchant, amount, accountNumber, bankCode)
        .trim();
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
