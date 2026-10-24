package com.brewpay.platform.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorrelationIdsTest {

  @Test
  void keepsASafeCallerId() {
    assertThat(CorrelationIds.resolve("req-1234")).isEqualTo("req-1234");
  }

  @Test
  void replacesAMissingHeader() {
    assertThat(CorrelationIds.resolve(null)).isNotBlank().isNotEqualTo("null");
  }

  @Test
  void replacesInjectionAndShortValues() {
    assertThat(CorrelationIds.resolve("bad id")).isNotEqualTo("bad id");
    assertThat(CorrelationIds.resolve("short")).isNotEqualTo("short");
    assertThat(CorrelationIds.resolve("line\nbreak-value")).doesNotContain("\n");
  }
}
