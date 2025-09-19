package com.clara.ops.challenge.document_management_service_challenge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("memory")
public class MemoryHealthIndicator implements HealthIndicator {

  @Value("${management.health.memory.threshold:52428800}")
  private long threshold;

  @Override
  public Health health() {
    long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    if (used > threshold) {
      return Health.down()
          .withDetail("memory.used", used)
          .withDetail("threshold", threshold)
          .build();
    }
    return Health.up().withDetail("memory.used", used).withDetail("threshold", threshold).build();
  }
}
