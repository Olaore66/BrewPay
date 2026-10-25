package com.brewpay.reconciliation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brewpay.banking.ProviderPaymentStatus;
import com.brewpay.banking.StatementLine;
import com.brewpay.banking.StatementSubject;
import com.brewpay.banking.fake.FakeBankProperties;
import com.brewpay.banking.fake.FakeBankWebhookController;
import com.brewpay.banking.fake.FakeStatementProvider;
import com.brewpay.banking.fake.FakeTransferProvider;
import com.brewpay.identity.TestKyb;
import com.brewpay.payments.IdempotencyKeys;
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
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
@Import(ReconciliationRulesTest.SteppedClockConfig.class)
class ReconciliationRulesTest {

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
  @Autowired private FakeStatementProvider statements;
  @Autowired private FakeTransferProvider fakeTransfers;
  @Autowired private FakeBankProperties fakeBank;

  @BeforeEach
  void resetFakeBank() {
    statements.clear();
    fakeTransfers.clearScript();
  }

  @Test
  void aProviderSuccessDoesNotRewriteAnUnknownTransfer() throws Exception {
    CreatedOrg ada = createOrganization("Ada Recon Unknown", "ada-recon-unknown@a.example");
    String token = login(ada.id(), "ada-recon-unknown@a.example");
    String clearing = createAccount(token, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(token, "payable", "Merchant payable", "LIABILITY");
    String key = "xfer-recon-0001";
    fakeTransfers.timeoutNextSubmit();
    String transferId = transferId(submit(token, key, clearing, merchant, KOBO));

    statements.addLine(
        UUID.fromString(ada.id()),
        new StatementLine(StatementSubject.TRANSFER, key, KOBO, ProviderPaymentStatus.SUCCESSFUL));

    String first =
        run(token)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.findings.length()").value(1))
            .andExpect(jsonPath("$.findings[0].code").value("STATUS_MISMATCH"))
            .andExpect(jsonPath("$.findings[0].subjectType").value("TRANSFER"))
            .andExpect(jsonPath("$.findings[0].subjectId").value(transferId))
            .andExpect(jsonPath("$.findings[0].ourStatus").value("UNKNOWN"))
            .andExpect(jsonPath("$.findings[0].providerStatus").value("SUCCESSFUL"))
            .andExpect(jsonPath("$.findings[0].providerReference").value(nullValue()))
            .andExpect(jsonPath("$.findings[0].ourAmountKobo").value(250000))
            .andExpect(jsonPath("$.findings[0].providerAmountKobo").value(250000))
            .andExpect(header().exists(CorrelationIds.HEADER))
            .andExpect(content().string(not(containsString(key))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String findingId = JsonPath.read(first, "$.findings[0].id");
    String firstRun = JsonPath.read(first, "$.id");

    String second =
        run(token)
            .andExpect(status().isCreated())
            .andExpect(content().string(not(containsString(key))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secondFinding = JsonPath.read(second, "$.findings[0].id");
    String secondRun = JsonPath.read(second, "$.id");
    assertEquals(findingId, secondFinding);
    assertNotEquals(firstRun, secondRun);

    mockMvc
        .perform(get("/api/v1/transfers/" + transferId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNKNOWN"))
        .andExpect(jsonPath("$.journalId").value(nullValue()));
    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mockMvc
        .perform(get("/api/v1/reconciliation-findings").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    String audit = audit(token);
    assertEquals(2, count(audit, "RECONCILIATION_RUN"));
    assertEquals(0, count(audit, "JOURNAL_POSTED"));
    assertFalse(audit.contains(key));
  }

  @Test
  void aDifferentAmountDoesNotRewriteTheCollection() throws Exception {
    CreatedOrg ada = createOrganization("Ada Recon Amount", "ada-recon-amount@a.example");
    String token = login(ada.id(), "ada-recon-amount@a.example");
    String clearing = createAccount(token, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(token, "payable", "Merchant payable", "LIABILITY");
    String reference = openCollectionAccount(token, clearing, merchant);
    String eventId = "evt-recon-0001";
    String credited =
        webhook(reference, eventId, KOBO, "SUCCESSFUL")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String collectionId = JsonPath.read(credited, "$.collection.id");
    String journalId = JsonPath.read(credited, "$.collection.journalId");

    statements.addLine(
        UUID.fromString(ada.id()),
        new StatementLine(
            StatementSubject.COLLECTION, eventId, 100_000L, ProviderPaymentStatus.SUCCESSFUL));

    String first = createdRun(token);
    assertEquals("AMOUNT_MISMATCH", JsonPath.read(first, "$.findings[0].code"));
    assertEquals(collectionId, JsonPath.read(first, "$.findings[0].subjectId"));
    String findingId = JsonPath.read(first, "$.findings[0].id");
    run(token)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.findings[0].id").value(findingId))
        .andExpect(jsonPath("$.findings[0].ourAmountKobo").value(250000))
        .andExpect(jsonPath("$.findings[0].providerAmountKobo").value(100000));

    mockMvc
        .perform(get("/api/v1/collections/" + collectionId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amountKobo").value(250000));
    mockMvc
        .perform(get("/api/v1/ledger/journals/" + journalId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].amountKobo").value(250000))
        .andExpect(jsonPath("$.lines[1].amountKobo").value(250000));
    mockMvc
        .perform(get("/api/v1/reconciliation-findings").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void aMissingProviderLineDoesNotReverseTheTransfer() throws Exception {
    CreatedOrg ada = createOrganization("Ada Recon Missing", "ada-recon-missing@a.example");
    String token = login(ada.id(), "ada-recon-missing@a.example");
    String clearing = createAccount(token, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(token, "payable", "Merchant payable", "LIABILITY");
    String posted = submit(token, "xfer-recon-0002", clearing, merchant, KOBO);
    String transferId = transferId(posted);
    String journalId = JsonPath.read(posted, "$.journalId");
    String providerReference = JsonPath.read(posted, "$.providerReference");

    String first = createdRun(token);
    assertEquals("MISSING_AT_PROVIDER", JsonPath.read(first, "$.findings[0].code"));
    String findingId = JsonPath.read(first, "$.findings[0].id");
    run(token)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.findings[0].id").value(findingId))
        .andExpect(jsonPath("$.findings[0].subjectId").value(transferId))
        .andExpect(jsonPath("$.findings[0].ourStatus").value("SUCCESSFUL"))
        .andExpect(jsonPath("$.findings[0].providerStatus").value(nullValue()))
        .andExpect(jsonPath("$.findings[0].providerAmountKobo").value(nullValue()))
        .andExpect(jsonPath("$.findings[0].ourAmountKobo").value(250000))
        .andExpect(jsonPath("$.findings[0].providerReference").value(providerReference));

    mockMvc
        .perform(get("/api/v1/transfers/" + transferId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
        .andExpect(jsonPath("$.journalId").value(journalId));
    mockMvc
        .perform(get("/api/v1/ledger/journals/" + journalId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].amountKobo").value(250000));
  }

  @Test
  void aProviderLineWithoutALedgerRowDoesNotPost() throws Exception {
    CreatedOrg ada = createOrganization("Ada Recon Extra", "ada-recon-extra@a.example");
    String token = login(ada.id(), "ada-recon-extra@a.example");
    statements.addLine(
        UUID.fromString(ada.id()),
        new StatementLine(
            StatementSubject.COLLECTION,
            "evt-missing-0001",
            KOBO,
            ProviderPaymentStatus.SUCCESSFUL));

    String first = createdRun(token);
    String findingId = JsonPath.read(first, "$.findings[0].id");
    run(token)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.findings[0].id").value(findingId))
        .andExpect(jsonPath("$.findings[0].code").value("MISSING_ON_LEDGER"))
        .andExpect(jsonPath("$.findings[0].subjectId").value(nullValue()))
        .andExpect(jsonPath("$.findings[0].ourStatus").value(nullValue()))
        .andExpect(jsonPath("$.findings[0].ourAmountKobo").value(nullValue()))
        .andExpect(jsonPath("$.findings[0].providerAmountKobo").value(250000))
        .andExpect(jsonPath("$.findings[0].providerReference").value("evt-missing-0001"));

    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mockMvc
        .perform(get("/api/v1/collections").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void anAgreedStatementAddsNoFinding() throws Exception {
    CreatedOrg ada = createOrganization("Ada Recon Match", "ada-recon-match@a.example");
    String token = login(ada.id(), "ada-recon-match@a.example");
    String clearing = createAccount(token, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(token, "payable", "Merchant payable", "LIABILITY");
    String posted = submit(token, "xfer-recon-0003", clearing, merchant, KOBO);
    String transferId = transferId(posted);
    String providerReference = JsonPath.read(posted, "$.providerReference");
    statements.addLine(
        UUID.fromString(ada.id()),
        new StatementLine(
            StatementSubject.TRANSFER, providerReference, KOBO, ProviderPaymentStatus.SUCCESSFUL));

    run(token).andExpect(status().isCreated()).andExpect(jsonPath("$.findings").isEmpty());
    mockMvc
        .perform(get("/api/v1/transfers/" + transferId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESSFUL"));
    mockMvc
        .perform(get("/api/v1/ledger/journals").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mockMvc
        .perform(get("/api/v1/reconciliation-findings").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void anotherOrganizationCannotReadTheFinding() throws Exception {
    CreatedOrg ada = createOrganization("Ada Recon Wall", "ada-recon-wall@a.example");
    CreatedOrg bola = createOrganization("Bola Recon Wall", "bola-recon-wall@b.example");
    String adaToken = login(ada.id(), "ada-recon-wall@a.example");
    String bolaToken = login(bola.id(), "bola-recon-wall@b.example");
    String clearing = createAccount(adaToken, "clearing", "Clearing", "ASSET");
    String merchant = createAccount(adaToken, "payable", "Merchant payable", "LIABILITY");
    String key = "xfer-recon-0004";
    fakeTransfers.timeoutNextSubmit();
    String transferId = transferId(submit(adaToken, key, clearing, merchant, KOBO));
    statements.addLine(
        UUID.fromString(ada.id()),
        new StatementLine(StatementSubject.TRANSFER, key, KOBO, ProviderPaymentStatus.SUCCESSFUL));
    String findingId = JsonPath.read(createdRun(adaToken), "$.findings[0].id");

    mockMvc
        .perform(
            get("/api/v1/reconciliation-findings/" + findingId)
                .header("Authorization", bearer(bolaToken))
                .header(TenantContext.HEADER, ada.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Reconciliation finding was not found."));
    mockMvc
        .perform(get("/api/v1/reconciliation-findings").header("Authorization", bearer(bolaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    run(bolaToken).andExpect(status().isCreated()).andExpect(jsonPath("$.findings").isEmpty());
    mockMvc
        .perform(get("/api/v1/reconciliation-findings").header("Authorization", bearer(adaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mockMvc
        .perform(get("/api/v1/transfers/" + transferId).header("Authorization", bearer(adaToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNKNOWN"));
  }

  @Test
  void staffCannotRunReconciliation() throws Exception {
    CreatedOrg ada = createOrganization("Ada Recon Staff", "ada-recon-staff@a.example");
    String ownerToken = login(ada.id(), "ada-recon-staff@a.example");
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson("Chi", "chi-recon@a.example")))
        .andExpect(status().isCreated());
    String staffToken = login(ada.id(), "chi-recon@a.example");

    run(staffToken)
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message").value("You do not have permission to perform this action."));
    assertEquals(1, count(audit(ownerToken), "RECONCILIATION_RUN_DENIED"));
    mockMvc
        .perform(get("/api/v1/reconciliation-findings").header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  private String createdRun(String token) throws Exception {
    return run(token)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.findings.length()").value(1))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private ResultActions run(String token) throws Exception {
    return mockMvc.perform(
        post("/api/v1/reconciliation-runs").header("Authorization", bearer(token)));
  }

  private String transferId(String body) {
    return JsonPath.read(body, "$.id");
  }

  private String submit(String token, String key, String clearing, String merchant, long amount)
      throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Authorization", bearer(token))
                .header(IdempotencyKeys.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clearingAccountId":"%s","merchantAccountId":"%s","amountKobo":%d,"destinationAccountNumber":"%s","destinationBankCode":"%s"}
                    """
                        .formatted(clearing, merchant, amount, ACCOUNT, BANK)
                        .trim()))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String openCollectionAccount(String token, String clearing, String merchant)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/collection-accounts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clearingAccountId":"%s","merchantAccountId":"%s"}
                        """
                            .formatted(clearing, merchant)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.externalReference");
  }

  private ResultActions webhook(String reference, String eventId, long amount, String paymentStatus)
      throws Exception {
    String body =
        """
        {"eventId":"%s","accountReference":"%s","amountKobo":%d,"status":"%s"}
        """
            .formatted(eventId, reference, amount, paymentStatus)
            .trim();
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
                        {"code":"%s","name":"%s","type":"%s"}
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
                    .content(
                        """
                        {"organizationId":"%s","email":"%s","password":"%s"}
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
        {"firstName":"%s","lastName":"Nwosu","email":"%s","password":"%s"}
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
