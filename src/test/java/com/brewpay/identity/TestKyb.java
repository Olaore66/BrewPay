package com.brewpay.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Approves the signed-in business through the public KYB route. The fake provider agrees. */
public final class TestKyb {

  private TestKyb() {}

  public static void approve(MockMvc mockMvc, String token) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/kyb-submissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "tradingName": "Ada Stores",
                      "businessType": "SOLE_PROPRIETOR",
                      "addressLine": "12 Market Road",
                      "state": "Lagos",
                      "category": "Retail"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kycStatus").value("APPROVED"))
        .andExpect(jsonPath("$.organizationStatus").value("ACTIVE"));
  }
}
