package com.brewpay.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

class IdempotencyRepositoryContractTest {

  @Test
  void idempotencyRecordsCannotBeDeletedOrReadWithoutTheOrganization() {
    assertFalse(CrudRepository.class.isAssignableFrom(IdempotencyRepository.class));
    assertFalse(JpaRepository.class.isAssignableFrom(IdempotencyRepository.class));

    Set<String> methods =
        Arrays.stream(IdempotencyRepository.class.getMethods())
            .filter(method -> method.getDeclaringClass() != Object.class)
            .map(Method::getName)
            .collect(Collectors.toSet());
    assertEquals(
        Set.of("save", "saveAndFlush", "findByOrganizationIdAndActionAndIdempotencyKey"), methods);

    boolean hasSetter =
        Arrays.stream(IdempotencyRecord.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().startsWith("set"));
    assertFalse(hasSetter);
  }
}
