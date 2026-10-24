package com.brewpay.payments;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
@Import(IdempotencyRulesTest.SteppedClockConfig.class)
class IdempotencyRulesTest {

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
  void theSameKeyReturnsTheFirstJournal() throws Exception {
    CreatedOrg ada = createOrganization("Ada Idem", "ada-idem@a.example");
    String token = login(ada.id(), "ada-idem@a.example");
    String cash = createAccount(token, "cash", "Cash", "ASSET");
    String sales = createAccount(token, "sales", "Sales", "INCOME");
    String key = "till-sales-1";

    String first =
        postJournal(token, key, "Till sales", cash, sales, KOBO, KOBO)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.organizationId").value(ada.id()))
            .andExpect(header().exists(CorrelationIds.HEADER))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String journalId = JsonPath.read(first, "$.id");

    postJournal(token, "  " + key + "  ", "Till sales", cash, sales, KOBO, KOBO)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(journalId))
        .andExpect(jsonPath("$.lines[0].amountKobo").value(150000))
        .andExpect(content().string(not(containsString(key))));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    String audit =
        mockMvc
            .perform(get("/api/v1/audit-events").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEqualsOnePosted(audit, key);
  }

  @Test
  void theSameKeyWithADifferentRequestIsRejected() throws Exception {
    CreatedOrg ada = createOrganization("Ada Mismatch", "ada-mismatch@a.example");
    String token = login(ada.id(), "ada-mismatch@a.example");
    String cash = createAccount(token, "cash", "Cash", "ASSET");
    String sales = createAccount(token, "sales", "Sales", "INCOME");
    String key = "till-sales-2";
    postJournal(token, key, "Till sales", cash, sales, KOBO, KOBO).andExpect(status().isCreated());

    postJournal(token, key, "Till sales", cash, sales, 100L, 100L)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(
            jsonPath("$.message")
                .value("Idempotency key was already used with a different request."))
        .andExpect(jsonPath("$.details").isEmpty());

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].lines[0].amountKobo").value(150000));
  }

  @Test
  void aDifferentKeyPostsAnotherJournal() throws Exception {
    CreatedOrg ada = createOrganization("Ada Two Keys", "ada-two-keys@a.example");
    String token = login(ada.id(), "ada-two-keys@a.example");
    String cash = createAccount(token, "cash", "Cash", "ASSET");
    String sales = createAccount(token, "sales", "Sales", "INCOME");

    String first =
        postJournal(token, "till-sales-3a", "Till sales", cash, sales, KOBO, KOBO)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String second =
        postJournal(token, "till-sales-3b", "Till sales", cash, sales, KOBO, KOBO)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String firstId = JsonPath.read(first, "$.id");
    String secondId = JsonPath.read(second, "$.id");
    org.junit.jupiter.api.Assertions.assertNotEquals(firstId, secondId);

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void anotherOrganizationCanUseTheSameKey() throws Exception {
    CreatedOrg ada = createOrganization("Ada Shared Key", "ada-shared-key@a.example");
    CreatedOrg bola = createOrganization("Bola Shared Key", "bola-shared-key@b.example");
    String adaToken = login(ada.id(), "ada-shared-key@a.example");
    String bolaToken = login(bola.id(), "bola-shared-key@b.example");
    String adaCash = createAccount(adaToken, "cash", "Cash", "ASSET");
    String adaSales = createAccount(adaToken, "sales", "Sales", "INCOME");
    String bolaCash = createAccount(bolaToken, "cash", "Cash", "ASSET");
    String bolaSales = createAccount(bolaToken, "sales", "Sales", "INCOME");
    String key = "shared-key-1";

    String adaBody =
        postJournal(adaToken, key, "Till sales", adaCash, adaSales, KOBO, KOBO)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String adaJournalId = JsonPath.read(adaBody, "$.id");

    postJournal(bolaToken, key, "Till sales", bolaCash, bolaSales, KOBO, KOBO)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.organizationId").value(bola.id()))
        .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(adaJournalId)));

    mockMvc
        .perform(
            post("/api/v1/ledger/journals")
                .header("Authorization", bearer(bolaToken))
                .header(IdempotencyKeys.HEADER, "shared-key-2")
                .header(TenantContext.HEADER, ada.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(journalJson("Borrowed", adaCash, adaSales, KOBO, KOBO)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Account was not found."));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(adaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(adaJournalId));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(bolaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].organizationId").value(bola.id()));
  }

  @Test
  void aRejectedPostDoesNotConsumeTheKey() throws Exception {
    CreatedOrg ada = createOrganization("Ada Retry", "ada-retry@a.example");
    String token = login(ada.id(), "ada-retry@a.example");
    String cash = createAccount(token, "cash", "Cash", "ASSET");
    String sales = createAccount(token, "sales", "Sales", "INCOME");
    String key = "retry-key-1";

    postJournal(token, key, "Short", cash, sales, 100L, 90L)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Journal is not balanced."));

    String body =
        postJournal(token, key, "Till sales", cash, sales, KOBO, KOBO)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String journalId = JsonPath.read(body, "$.id");

    postJournal(token, key, "Till sales", cash, sales, KOBO, KOBO)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(journalId));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void aJournalPostRequiresAValidIdempotencyKey() throws Exception {
    CreatedOrg ada = createOrganization("Ada Key", "ada-key@a.example");
    String token = login(ada.id(), "ada-key@a.example");
    String cash = createAccount(token, "cash", "Cash", "ASSET");
    String sales = createAccount(token, "sales", "Sales", "INCOME");

    mockMvc
        .perform(
            post("/api/v1/ledger/journals")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(journalJson("Till sales", cash, sales, KOBO, KOBO)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("Idempotency key is required."));

    mockMvc
        .perform(
            post("/api/v1/ledger/journals")
                .header("Authorization", bearer(token))
                .header(IdempotencyKeys.HEADER, "short")
                .contentType(MediaType.APPLICATION_JSON)
                .content(journalJson("Till sales", cash, sales, KOBO, KOBO)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Idempotency key is invalid."));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void staffCannotConsumeAnIdempotencyKey() throws Exception {
    CreatedOrg ada = createOrganization("Ada Staff Key", "ada-staff-key@a.example");
    String ownerToken = login(ada.id(), "ada-staff-key@a.example");
    String cash = createAccount(ownerToken, "cash", "Cash", "ASSET");
    String sales = createAccount(ownerToken, "sales", "Sales", "INCOME");
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson("Chi", "chi-key@a.example")))
        .andExpect(status().isCreated());
    String staffToken = login(ada.id(), "chi-key@a.example");
    String key = "staff-key-1";

    postJournal(staffToken, key, "Staff sale", cash, sales, KOBO, KOBO)
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message").value("You do not have permission to perform this action."));

    postJournal(ownerToken, key, "Owner sale", cash, sales, KOBO, KOBO)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.description").value("Owner sale"));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  private static void assertEqualsOnePosted(String audit, String key) {
    int posted = 0;
    for (String action : JsonPath.<java.util.List<String>>read(audit, "$[*].action")) {
      if ("JOURNAL_POSTED".equals(action)) {
        posted++;
      }
    }
    org.junit.jupiter.api.Assertions.assertEquals(1, posted);
    org.junit.jupiter.api.Assertions.assertFalse(audit.contains(key));
  }

  private ResultActions postJournal(
      String token,
      String key,
      String description,
      String debitAccount,
      String creditAccount,
      long debit,
      long credit)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/ledger/journals")
            .header("Authorization", bearer(token))
            .header(IdempotencyKeys.HEADER, key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(journalJson(description, debitAccount, creditAccount, debit, credit)));
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
