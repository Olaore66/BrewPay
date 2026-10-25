package com.brewpay.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Approves the signed-in business and that owner. Money tests need both. The fake provider agrees.
 */
public final class TestKyb {

  public static final String IDENTIFICATION = "22123456789";

  private TestKyb() {}

  public static void approve(MockMvc mockMvc, String token) throws Exception {
    approveBusiness(mockMvc, token);
    approvePerson(mockMvc, token);
  }

  public static void approveBusiness(MockMvc mockMvc, String token) throws Exception {
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

  public static void approvePerson(MockMvc mockMvc, String token) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/kyc-submissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "dateOfBirth": "1990-04-12",
                      "phone": "08031234567",
                      "identificationNumber": "%s"
                    }
                    """
                        .formatted(IDENTIFICATION)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kycStatus").value("APPROVED"));
  }
}
