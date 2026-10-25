package com.brewpay.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

class KycRepositoryContractTest {

  @Test
  void kycRepositoryDoesNotExposeDeletesOrUnscopedIdReads() {
    assertFalse(CrudRepository.class.isAssignableFrom(KycSubmissionRepository.class));
    assertFalse(JpaRepository.class.isAssignableFrom(KycSubmissionRepository.class));
    Set<String> methods =
        Arrays.stream(KycSubmissionRepository.class.getMethods())
            .filter(method -> method.getDeclaringClass() != Object.class)
            .map(Method::getName)
            .collect(Collectors.toSet());
    assertEquals(
        Set.of(
            "save",
            "saveAndFlush",
            "findByIdAndOrganizationIdAndUserId",
            "findByOrganizationIdAndUserIdOrderByCreatedAtAsc"),
        methods);
    boolean hasSetter =
        Arrays.stream(KycSubmission.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().startsWith("set"));
    assertFalse(hasSetter);
  }
}
