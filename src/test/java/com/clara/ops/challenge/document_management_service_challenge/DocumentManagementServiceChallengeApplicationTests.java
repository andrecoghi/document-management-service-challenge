package com.clara.ops.challenge.document_management_service_challenge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class DocumentManagementServiceChallengeApplicationTests {

  @Container
  static GenericContainer<?> minio =
      new GenericContainer<>("minio/minio:latest")
          .withEnv("MINIO_ROOT_USER", "minioadmin")
          .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
          .withCommand("server /data")
          .withExposedPorts(9000);

  @DynamicPropertySource
  static void registerMinio(DynamicPropertyRegistry registry) {
    registry.add(
        "minio.endpoint", () -> "http://" + minio.getHost() + ":" + minio.getFirstMappedPort());
    registry.add("minio.access-key", () -> "minioadmin");
    registry.add("minio.secret-key", () -> "minioadmin");
  }

  @Test
  void contextLoads() {}
}
