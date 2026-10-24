package com.brewpay.platform.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class DotenvTest {

  @Test
  void aLocalFileSuppliesTheDatabasePasswordWithoutKeepingAComment() {
    var values =
        Dotenv.parse(
            List.of(
                "",
                "# local only",
                "DB_URL=jdbc:postgresql://localhost:5432/brewpay_db",
                "export DB_USERNAME=merchant",
                "DB_PASSWORD=\"local-secret\"",
                "IGNORED"));

    assertEquals("jdbc:postgresql://localhost:5432/brewpay_db", values.get("DB_URL"));
    assertEquals("merchant", values.get("DB_USERNAME"));
    assertEquals("local-secret", values.get("DB_PASSWORD"));
    assertFalse(values.containsKey("IGNORED"));
    assertFalse(values.containsKey("# local only"));
  }
}
