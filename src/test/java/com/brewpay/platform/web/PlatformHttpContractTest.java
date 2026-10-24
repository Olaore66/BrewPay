package com.brewpay.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brewpay.platform.correlation.CorrelationFilter;
import com.brewpay.platform.correlation.CorrelationIds;
import com.brewpay.platform.error.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {HealthController.class, ProbeController.class})
@AutoConfigureMockMvc
@Import({CorrelationFilter.class, ApiExceptionHandler.class})
class PlatformHttpContractTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void healthEchoesACallerCorrelationId() throws Exception {
    mockMvc
        .perform(get("/api/v1/health").header(CorrelationIds.HEADER, "req-123456"))
        .andExpect(status().isOk())
        .andExpect(header().string(CorrelationIds.HEADER, "req-123456"))
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void healthReplacesAnUnsafeCorrelationId() throws Exception {
    mockMvc
        .perform(get("/api/v1/health").header(CorrelationIds.HEADER, "bad id"))
        .andExpect(status().isOk())
        .andExpect(header().exists(CorrelationIds.HEADER))
        .andExpect(header().string(CorrelationIds.HEADER, org.hamcrest.Matchers.not("bad id")));
  }

  @Test
  void platformExceptionUsesTheErrorShape() throws Exception {
    mockMvc
        .perform(get("/api/v1/probe/missing").header(CorrelationIds.HEADER, "req-123456"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Account was not found."))
        .andExpect(jsonPath("$.correlationId").value("req-123456"))
        .andExpect(jsonPath("$.details").isArray())
        .andExpect(jsonPath("$.details").isEmpty());
  }

  @Test
  void validationFailureListsFieldsAndHidesNothingElse() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/probe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header(CorrelationIds.HEADER, "req-123456"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("Request validation failed."))
        .andExpect(jsonPath("$.correlationId").value("req-123456"))
        .andExpect(jsonPath("$.details[0].field").value("name"))
        .andExpect(jsonPath("$.details.length()").value(1));
  }

  @Test
  void unknownFailureDoesNotLeakTheExceptionText() throws Exception {
    mockMvc
        .perform(get("/api/v1/probe/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("Something went wrong."))
        .andExpect(jsonPath("$.correlationId").isNotEmpty())
        .andExpect(jsonPath("$.details").isEmpty())
        .andExpect(
            jsonPath("$.message").value(org.hamcrest.Matchers.not("secret database password")));
  }

  @Test
  void missingRouteUsesTheSameErrorShape() throws Exception {
    mockMvc
        .perform(get("/api/v1/does-not-exist").header(CorrelationIds.HEADER, "req-123456"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").value("req-123456"))
        .andExpect(jsonPath("$.details").isEmpty());
  }
}
