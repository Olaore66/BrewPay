package com.brewpay.payments;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brewpay.banking.fake.FakeBankProperties;
import com.brewpay.banking.fake.FakeBankWebhookController;
import com.brewpay.platform.correlation.CorrelationIds;
import com.brewpay.platform.tenant.TenantContext;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
@Import(CollectionRulesTest.SteppedClockConfig.class)
class CollectionRulesTest {

  private static final String PASSWORD = "password-1";
  private static final long KOBO = 250_000L;

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  static final SteppingClock CLOCK = new SteppingClock();

  static {
    postgres.start();
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeBankProperties fakeBank;

  @Test
  void aDuplicateWebhookCreditsTheMerchantOnce() throws Exception {
    CreatedOrg ada = createOrganization("Ada Collect", "ada-collect@a.example");
    String token = login(ada.id(), "ada-collect@a.example");
    String clearing = createAccount(token, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(token, "payable", "Merchant payable", "LIABILITY");
    String reference = openCollectionAccount(token, clearing, merchant);
    String eventId = "evt-paid-0001";

    String first =
        webhook(reference, eventId, KOBO, "SUCCESSFUL")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
            .andExpect(jsonPath("$.collection.organizationId").value(ada.id()))
            .andExpect(jsonPath("$.collection.amountKobo").value(250000))
            .andExpect(jsonPath("$.collection.providerEventId").value(eventId))
            .andExpect(header().exists(CorrelationIds.HEADER))
            .andExpect(
                content()
                    .string(
                        org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString(fakeBank.webhookSecret()))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String collectionId = JsonPath.read(first, "$.collection.id");
    String journalId = JsonPath.read(first, "$.collection.journalId");

    webhook(reference, "  " + eventId + "  ", KOBO, "SUCCESSFUL")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.collection.id").value(collectionId))
        .andExpect(jsonPath("$.collection.journalId").value(journalId));

    mockMvc
        .perform(get("/api/v1/collections").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(get("/api/v1/ledger/journals/" + journalId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("NGN"))
        .andExpect(jsonPath("$.lines[0].accountId").value(clearing))
        .andExpect(jsonPath("$.lines[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.lines[0].amountKobo").value(250000))
        .andExpect(jsonPath("$.lines[1].accountId").value(merchant))
        .andExpect(jsonPath("$.lines[1].direction").value("CREDIT"))
        .andExpect(jsonPath("$.lines[1].amountKobo").value(250000));

    String audit =
        mockMvc
            .perform(get("/api/v1/audit-events").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    org.junit.jupiter.api.Assertions.assertEquals(1, count(audit, "COLLECTION_CREDITED"));
    org.junit.jupiter.api.Assertions.assertEquals(1, count(audit, "JOURNAL_POSTED"));
    org.junit.jupiter.api.Assertions.assertFalse(audit.contains(fakeBank.webhookSecret()));
  }

  @Test
  void aChangedWebhookDoesNotPostASecondCredit() throws Exception {
    CreatedOrg ada = createOrganization("Ada Changed", "ada-changed@a.example");
    String token = login(ada.id(), "ada-changed@a.example");
    String clearing = createAccount(token, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(token, "payable", "Merchant payable", "LIABILITY");
    String reference = openCollectionAccount(token, clearing, merchant);

    webhook(reference, "evt-paid-0002", KOBO, "SUCCESSFUL").andExpect(status().isCreated());
    webhook(reference, "evt-paid-0002", 100_000L, "SUCCESSFUL")
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
        .andExpect(jsonPath("$[0].lines[0].amountKobo").value(250000));
  }

  @Test
  void aDifferentEventPostsAnotherCollection() throws Exception {
    CreatedOrg ada = createOrganization("Ada Two Events", "ada-two-events@a.example");
    String token = login(ada.id(), "ada-two-events@a.example");
    String clearing = createAccount(token, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(token, "payable", "Merchant payable", "LIABILITY");
    String reference = openCollectionAccount(token, clearing, merchant);

    String first =
        webhook(reference, "evt-paid-0003", KOBO, "SUCCESSFUL")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String second =
        webhook(reference, "evt-paid-0004", KOBO, "SUCCESSFUL")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String firstJournal = JsonPath.read(first, "$.collection.journalId");
    String secondJournal = JsonPath.read(second, "$.collection.journalId");
    org.junit.jupiter.api.Assertions.assertNotEquals(firstJournal, secondJournal);
    mockMvc
        .perform(get("/api/v1/collections").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void aPendingWebhookDoesNotCredit() throws Exception {
    CreatedOrg ada = createOrganization("Ada Pending", "ada-pending@a.example");
    String token = login(ada.id(), "ada-pending@a.example");
    String clearing = createAccount(token, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(token, "payable", "Merchant payable", "LIABILITY");
    String reference = openCollectionAccount(token, clearing, merchant);

    webhook(reference, "evt-wait-0001", KOBO, "PENDING")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.collection").value(nullValue()));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    String credited =
        webhook(reference, "evt-wait-0001", KOBO, "SUCCESSFUL")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String journalId = JsonPath.read(credited, "$.collection.journalId");
    webhook(reference, "evt-wait-0001", KOBO, "SUCCESSFUL")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.collection.journalId").value(journalId));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void aBadOrUnreadableWebhookDoesNotCredit() throws Exception {
    CreatedOrg ada = createOrganization("Ada Signed", "ada-signed@a.example");
    String token = login(ada.id(), "ada-signed@a.example");
    String clearing = createAccount(token, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(token, "payable", "Merchant payable", "LIABILITY");
    String reference = openCollectionAccount(token, clearing, merchant);
    String body = webhookJson(reference, "evt-paid-0005", KOBO, "SUCCESSFUL");

    mockMvc
        .perform(
            post("/api/v1/webhooks/fake-bank")
                .contentType(MediaType.APPLICATION_JSON)
                .header(FakeBankWebhookController.SIGNATURE, "not-a-signature")
                .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").value("Webhook signature is invalid."));

    mockMvc
        .perform(
            post("/api/v1/webhooks/fake-bank")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Webhook signature is invalid."));

    String empty = "";
    mockMvc
        .perform(
            post("/api/v1/webhooks/fake-bank")
                .contentType(MediaType.APPLICATION_JSON)
                .header(FakeBankWebhookController.SIGNATURE, sign(empty))
                .content(empty))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Webhook could not be read."));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void anotherOrganizationCannotReadTheCollection() throws Exception {
    CreatedOrg ada = createOrganization("Ada Books In", "ada-books-in@a.example");
    CreatedOrg bola = createOrganization("Bola Books In", "bola-books-in@b.example");
    String adaToken = login(ada.id(), "ada-books-in@a.example");
    String bolaToken = login(bola.id(), "bola-books-in@b.example");
    String clearing = createAccount(adaToken, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(adaToken, "payable", "Merchant payable", "LIABILITY");
    String reference = openCollectionAccount(adaToken, clearing, merchant);
    String credited =
        webhook(reference, "evt-paid-0006", KOBO, "SUCCESSFUL")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String collectionId = JsonPath.read(credited, "$.collection.id");

    mockMvc
        .perform(
            get("/api/v1/collections/" + collectionId)
                .header("Authorization", bearer(bolaToken))
                .header(TenantContext.HEADER, ada.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Collection was not found."));

    mockMvc
        .perform(get("/api/v1/collections").header("Authorization", bearer(bolaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    webhook("FAKE-missing-account-ref", "evt-paid-0007", KOBO, "SUCCESSFUL")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Collection account was not found."));

    mockMvc
        .perform(get("/api/v1/collections").header("Authorization", bearer(adaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void staffCannotOpenACollectionAccount() throws Exception {
    CreatedOrg ada = createOrganization("Ada Staff Collect", "ada-staff-collect@a.example");
    String ownerToken = login(ada.id(), "ada-staff-collect@a.example");
    String clearing = createAccount(ownerToken, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(ownerToken, "payable", "Merchant payable", "LIABILITY");
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson("Chi", "chi-collect@a.example")))
        .andExpect(status().isCreated());
    String staffToken = login(ada.id(), "chi-collect@a.example");

    mockMvc
        .perform(
            post("/api/v1/collection-accounts")
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountPair(clearing, merchant)))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message").value("You do not have permission to perform this action."));

    String audit =
        mockMvc
            .perform(get("/api/v1/audit-events").header("Authorization", bearer(ownerToken)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    org.junit.jupiter.api.Assertions.assertEquals(
        1, count(audit, "COLLECTION_ACCOUNT_OPEN_DENIED"));

    mockMvc
        .perform(get("/api/v1/collection-accounts").header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void aCollectionAccountMustBeAnAssetAndALiability() throws Exception {
    CreatedOrg ada = createOrganization("Ada Types", "ada-types@a.example");
    String token = login(ada.id(), "ada-types@a.example");
    String cash = createAccount(token, "cash", "Cash", "ASSET");
    String sales = createAccount(token, "sales", "Sales", "INCOME");

    mockMvc
        .perform(
            post("/api/v1/collection-accounts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountPair(sales, cash)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Clearing account must be an asset."));

    mockMvc
        .perform(
            post("/api/v1/collection-accounts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountPair(cash, sales)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Merchant account must be a liability."));

    mockMvc
        .perform(get("/api/v1/collection-accounts").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
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

  private ResultActions webhook(String reference, String eventId, long amount, String paymentStatus)
      throws Exception {
    String body = webhookJson(reference, eventId, amount, paymentStatus);
    return mockMvc.perform(
        post("/api/v1/webhooks/fake-bank")
            .contentType(MediaType.APPLICATION_JSON)
            .header(FakeBankWebhookController.SIGNATURE, sign(body))
            .content(body));
  }

  private String sign(String body) throws NoSuchAlgorithmException, InvalidKeyException {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(
        new SecretKeySpec(fakeBank.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }

  private String openCollectionAccount(String token, String clearing, String merchant)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/collection-accounts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(accountPair(clearing, merchant)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.externalReference");
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

  private static String accountPair(String clearing, String merchant) {
    return """
        {"clearingAccountId": "%s", "merchantAccountId": "%s"}
        """
        .formatted(clearing, merchant);
  }

  private static String webhookJson(
      String reference, String eventId, long amount, String paymentStatus) {
    return """
        {"eventId":"%s","accountReference":"%s","amountKobo":%d,"status":"%s"}
        """
        .formatted(eventId.trim(), reference, amount, paymentStatus)
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
