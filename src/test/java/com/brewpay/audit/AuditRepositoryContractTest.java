package com.brewpay.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

class AuditRepositoryContractTest {

  @Test
  void auditEventsCannotBeDeletedThroughTheRepository() {
    assertFalse(CrudRepository.class.isAssignableFrom(AuditRepository.class));
    assertFalse(JpaRepository.class.isAssignableFrom(AuditRepository.class));

    Set<String> methods =
        Arrays.stream(AuditRepository.class.getMethods())
            .filter(method -> method.getDeclaringClass() != Object.class)
            .map(Method::getName)
            .collect(Collectors.toSet());
    assertEquals(Set.of("save", "findByOrganizationIdOrderByCreatedAtAsc"), methods);

    boolean hasSetter =
        Arrays.stream(AuditEvent.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().startsWith("set"));
    assertFalse(hasSetter);
  }
}
