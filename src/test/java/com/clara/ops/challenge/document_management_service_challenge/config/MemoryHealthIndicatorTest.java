package com.clara.ops.challenge.document_management_service_challenge.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;

class MemoryHealthIndicatorTest {

  @Test
  void healthIsUpWhenUsageBelowThreshold() {
    MemoryHealthIndicator indicator = new MemoryHealthIndicator();
    ReflectionTestUtils.setField(indicator, "threshold", Long.MAX_VALUE);

    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void healthIsDownWhenUsageExceedsThreshold() {
    MemoryHealthIndicator indicator = new MemoryHealthIndicator();
    ReflectionTestUtils.setField(indicator, "threshold", -1L);

    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
  }
}
