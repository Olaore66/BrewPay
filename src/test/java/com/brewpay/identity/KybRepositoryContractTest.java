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

class KybRepositoryContractTest {

  @Test
  void kybRepositoryDoesNotExposeDeletesOrUnscopedIdReads() {
    assertFalse(CrudRepository.class.isAssignableFrom(KybSubmissionRepository.class));
    assertFalse(JpaRepository.class.isAssignableFrom(KybSubmissionRepository.class));
    Set<String> methods =
        Arrays.stream(KybSubmissionRepository.class.getMethods())
            .filter(method -> method.getDeclaringClass() != Object.class)
            .map(Method::getName)
            .collect(Collectors.toSet());
    assertEquals(
        Set.of(
            "save",
            "saveAndFlush",
            "findByIdAndOrganizationId",
            "findByOrganizationIdOrderByCreatedAtAsc"),
        methods);
    boolean hasSetter =
        Arrays.stream(KybSubmission.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().startsWith("set"));
    assertFalse(hasSetter);
  }
}
