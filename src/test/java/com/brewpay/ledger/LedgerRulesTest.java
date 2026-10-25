package com.brewpay.ledger;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brewpay.identity.TestKyb;
import com.brewpay.payments.IdempotencyKeys;
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
@Import(LedgerRulesTest.SteppedClockConfig.class)
class LedgerRulesTest {

  private static final String PASSWORD = "password-1";
  private static final long KOBO = 150_000L;

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  static final SteppingClock CLOCK = new SteppingClock();

  static {
    postgres.start();
  }

  @Autowired private MockMvc mockMvc;

  @Test
  void aBalancedJournalIsStoredInKobo() throws Exception {
    CreatedOrg ada = createOrganization("Ada Ledger", "ada-ledger@a.example");
    String token = login(ada.id(), "ada-ledger@a.example");
    String cash = createAccount(token, "cash", "Cash", "ASSET");
    String sales = createAccount(token, "sales", "Sales", "INCOME");

    String body =
        mockMvc
            .perform(
                post("/api/v1/ledger/journals")
                    .header("Authorization", bearer(token))
                    .header(IdempotencyKeys.HEADER, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(journalJson("Till sales", cash, sales, KOBO, KOBO)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.organizationId").value(ada.id()))
            .andExpect(jsonPath("$.description").value("Till sales"))
            .andExpect(jsonPath("$.currency").value("NGN"))
            .andExpect(jsonPath("$.reversesJournalId").value(nullValue()))
            .andExpect(jsonPath("$.lines[0].accountId").value(cash))
            .andExpect(jsonPath("$.lines[0].direction").value("DEBIT"))
            .andExpect(jsonPath("$.lines[0].amountKobo").value(150000))
            .andExpect(jsonPath("$.lines[0].position").value(1))
            .andExpect(jsonPath("$.lines[1].accountId").value(sales))
            .andExpect(jsonPath("$.lines[1].direction").value("CREDIT"))
            .andExpect(jsonPath("$.lines[1].amountKobo").value(150000))
            .andExpect(jsonPath("$.lines[1].position").value(2))
            .andExpect(header().exists(CorrelationIds.HEADER))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String journalId = JsonPath.read(body, "$.id");

    mockMvc
        .perform(get("/api/v1/ledger/journals/" + journalId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("NGN"))
        .andExpect(jsonPath("$.lines[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.lines[1].direction").value("CREDIT"));

    mockMvc
        .perform(get("/api/v1/ledger/accounts/" + cash).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("CASH"));
  }

  @Test
  void anUnbalancedJournalIsRejected() throws Exception {
    CreatedOrg ada = createOrganization("Ada Unbalanced", "ada-unbalanced@a.example");
    String token = login(ada.id(), "ada-unbalanced@a.example");
    String cash = createAccount(token, "cash", "Cash", "ASSET");
    String sales = createAccount(token, "sales", "Sales", "INCOME");

    mockMvc
        .perform(
            post("/api/v1/ledger/journals")
                .header("Authorization", bearer(token))
                .header(IdempotencyKeys.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(journalJson("Short", cash, sales, 100L, 90L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("Journal is not balanced."))
        .andExpect(jsonPath("$.details").isEmpty())
        .andExpect(header().exists(CorrelationIds.HEADER));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    mockMvc
        .perform(get("/api/v1/audit-events").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].action", not(hasItem("JOURNAL_POSTED"))));
  }

  @Test
  void aWrappedTotalCannotLookBalanced() throws Exception {
    CreatedOrg ada = createOrganization("Ada Overflow", "ada-overflow@a.example");
    String token = login(ada.id(), "ada-overflow@a.example");
    String cash = createAccount(token, "cash", "Cash", "ASSET");
    String sales = createAccount(token, "sales", "Sales", "INCOME");
    long tooBig = Long.MAX_VALUE;

    mockMvc
        .perform(
            post("/api/v1/ledger/journals")
                .header("Authorization", bearer(token))
                .header(IdempotencyKeys.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "description": "Too big",
                      "lines": [
                        {"accountId": "%s", "direction": "DEBIT", "amountKobo": %d},
                        {"accountId": "%s", "direction": "DEBIT", "amountKobo": %d},
                        {"accountId": "%s", "direction": "CREDIT", "amountKobo": %d},
                        {"accountId": "%s", "direction": "CREDIT", "amountKobo": %d}
                      ]
                    }
                    """
                        .formatted(cash, tooBig, cash, tooBig, sales, tooBig, sales, tooBig)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Journal amount is too large."));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void aReversalIsANewJournalAndTheOriginalLinesStay() throws Exception {
    CreatedOrg ada = createOrganization("Ada Reversal", "ada-reversal@a.example");
    String token = login(ada.id(), "ada-reversal@a.example");
    String cash = createAccount(token, "cash", "Cash", "ASSET");
    String sales = createAccount(token, "sales", "Sales", "INCOME");
    String originalId = postJournal(token, "Till sales", cash, sales, KOBO, KOBO);

    String reversalBody =
        mockMvc
            .perform(
                post("/api/v1/ledger/journals/" + originalId + "/reversals")
                    .header("Authorization", bearer(token)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(not(originalId)))
            .andExpect(jsonPath("$.reversesJournalId").value(originalId))
            .andExpect(jsonPath("$.description").value("Reversal of Till sales"))
            .andExpect(jsonPath("$.currency").value("NGN"))
            .andExpect(jsonPath("$.lines[0].accountId").value(cash))
            .andExpect(jsonPath("$.lines[0].direction").value("CREDIT"))
            .andExpect(jsonPath("$.lines[0].amountKobo").value(150000))
            .andExpect(jsonPath("$.lines[1].accountId").value(sales))
            .andExpect(jsonPath("$.lines[1].direction").value("DEBIT"))
            .andExpect(jsonPath("$.lines[1].amountKobo").value(150000))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String reversalId = JsonPath.read(reversalBody, "$.id");

    mockMvc
        .perform(
            get("/api/v1/ledger/journals/" + originalId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Till sales"))
        .andExpect(jsonPath("$.reversesJournalId").value(nullValue()))
        .andExpect(jsonPath("$.lines[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.lines[0].amountKobo").value(150000))
        .andExpect(jsonPath("$.lines[1].direction").value("CREDIT"))
        .andExpect(jsonPath("$.lines[1].amountKobo").value(150000));

    mockMvc
        .perform(get("/api/v1/audit-events").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.action == 'JOURNAL_POSTED')].targetId", contains(originalId)))
        .andExpect(jsonPath("$[?(@.action == 'JOURNAL_REVERSED')].targetId", contains(reversalId)));
  }

  @Test
  void eachJournalIsReversedByOneNewJournal() throws Exception {
    CreatedOrg ada = createOrganization("Ada Once", "ada-once@a.example");
    String token = login(ada.id(), "ada-once@a.example");
    String cash = createAccount(token, "cash", "Cash", "ASSET");
    String sales = createAccount(token, "sales", "Sales", "INCOME");
    String originalId = postJournal(token, "Till sales", cash, sales, KOBO, KOBO);
    String reversalId = reverse(token, originalId);

    mockMvc
        .perform(
            post("/api/v1/ledger/journals/" + originalId + "/reversals")
                .header("Authorization", bearer(token)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.message").value("Journal is already reversed."))
        .andExpect(jsonPath("$.details").isEmpty());

    String secondHop = reverse(token, reversalId);
    mockMvc
        .perform(get("/api/v1/ledger/journals/" + secondHop).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reversesJournalId").value(reversalId))
        .andExpect(jsonPath("$.lines[0].direction").value("DEBIT"));

    mockMvc
        .perform(
            get("/api/v1/ledger/journals/" + originalId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.lines[1].direction").value("CREDIT"));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  void anotherOrganizationCannotReadTheJournalOrAccount() throws Exception {
    CreatedOrg ada = createOrganization("Ada Books", "ada-books@a.example");
    CreatedOrg bola = createOrganization("Bola Books", "bola-books@b.example");
    String adaToken = login(ada.id(), "ada-books@a.example");
    String bolaToken = login(bola.id(), "bola-books@b.example");
    String cash = createAccount(adaToken, "cash", "Cash", "ASSET");
    String sales = createAccount(adaToken, "sales", "Sales", "INCOME");
    String journalId = postJournal(adaToken, "Till sales", cash, sales, KOBO, KOBO);

    mockMvc
        .perform(
            get("/api/v1/ledger/journals/" + journalId)
                .header("Authorization", bearer(bolaToken))
                .header(TenantContext.HEADER, ada.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Journal was not found."));

    mockMvc
        .perform(
            get("/api/v1/ledger/accounts/" + cash)
                .header("Authorization", bearer(bolaToken))
                .header(TenantContext.HEADER, ada.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Account was not found."));

    mockMvc
        .perform(
            post("/api/v1/ledger/journals")
                .header("Authorization", bearer(bolaToken))
                .header(IdempotencyKeys.HEADER, UUID.randomUUID().toString())
                .header(TenantContext.HEADER, ada.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(journalJson("Borrowed", cash, sales, KOBO, KOBO)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Account was not found."));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(bolaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(adaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void staffCannotPostOrReverse() throws Exception {
    CreatedOrg ada = createOrganization("Ada Staff Books", "ada-staff-books@a.example");
    String ownerToken = login(ada.id(), "ada-staff-books@a.example");
    String cash = createAccount(ownerToken, "cash", "Cash", "ASSET");
    String sales = createAccount(ownerToken, "sales", "Sales", "INCOME");
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson("Chi", "chi-books@a.example")))
        .andExpect(status().isCreated());
    String staffToken = login(ada.id(), "chi-books@a.example");

    mockMvc
        .perform(
            post("/api/v1/ledger/journals")
                .header("Authorization", bearer(staffToken))
                .header(IdempotencyKeys.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(journalJson("Staff sale", cash, sales, KOBO, KOBO)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(
            jsonPath("$.message").value("You do not have permission to perform this action."));

    String journalId = postJournal(ownerToken, "Owner sale", cash, sales, KOBO, KOBO);

    mockMvc
        .perform(
            post("/api/v1/ledger/journals/" + journalId + "/reversals")
                .header("Authorization", bearer(staffToken)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(staffToken)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/v1/ledger/journals/" + journalId).header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.reversesJournalId").value(nullValue()));

    mockMvc
        .perform(get("/api/v1/audit-events").header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[*].action",
                contains(
                    "USER_CREATED",
                    "SESSION_CREATED",
                    "KYB_SUBMITTED",
                    "SESSION_CREATED",
                    "ACCOUNT_CREATED",
                    "ACCOUNT_CREATED",
                    "USER_CREATED",
                    "SESSION_CREATED",
                    "JOURNAL_POST_DENIED",
                    "JOURNAL_POSTED",
                    "JOURNAL_REVERSE_DENIED")));
  }

  @Test
  void accountCodesAreUniquePerOrganization() throws Exception {
    CreatedOrg ada = createOrganization("Ada Codes", "ada-codes@a.example");
    CreatedOrg bola = createOrganization("Bola Codes", "bola-codes@b.example");
    String adaToken = login(ada.id(), "ada-codes@a.example");
    String bolaToken = login(bola.id(), "bola-codes@b.example");

    mockMvc
        .perform(
            post("/api/v1/ledger/accounts")
                .header("Authorization", bearer(adaToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson("cash", "Cash", "ASSET")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("CASH"));

    mockMvc
        .perform(
            post("/api/v1/ledger/accounts")
                .header("Authorization", bearer(adaToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson("Cash", "Petty cash", "ASSET")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(
            jsonPath("$.message")
                .value("An account with this code already exists in the organization."));

    mockMvc
        .perform(
            post("/api/v1/ledger/accounts")
                .header("Authorization", bearer(bolaToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson("cash", "Cash", "ASSET")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.organizationId").value(bola.id()))
        .andExpect(jsonPath("$.code").value("CASH"));
  }

  private String postJournal(
      String token,
      String description,
      String debitAccount,
      String creditAccount,
      long debit,
      long credit)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/ledger/journals")
                    .header("Authorization", bearer(token))
                    .header(IdempotencyKeys.HEADER, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(journalJson(description, debitAccount, creditAccount, debit, credit)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private String reverse(String token, String journalId) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/ledger/journals/" + journalId + "/reversals")
                    .header("Authorization", bearer(token)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private String createAccount(String token, String code, String name, String type)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/ledger/accounts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(accountJson(code, name, type)))
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
                    .content(organizationJson(name, email)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(body, "$.id");
    TestKyb.approve(mockMvc, login(id, email));
    return new CreatedOrg(id);
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

  private static String accountJson(String code, String name, String type) {
    return """
        {"code": "%s", "name": "%s", "type": "%s"}
        """
        .formatted(code, name, type);
  }

  private static String journalJson(
      String description, String debitAccount, String creditAccount, long debit, long credit) {
    return """
        {
          "description": "%s",
          "lines": [
            {"accountId": "%s", "direction": "DEBIT", "amountKobo": %d},
            {"accountId": "%s", "direction": "CREDIT", "amountKobo": %d}
          ]
        }
        """
        .formatted(description, debitAccount, debit, creditAccount, credit);
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
