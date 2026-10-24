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

class SessionRepositoryContractTest {

  @Test
  void sessionLookupIsByTokenHashOrByIdInsideTheOrganization() {
    assertFalse(CrudRepository.class.isAssignableFrom(SessionRepository.class));
    assertFalse(JpaRepository.class.isAssignableFrom(SessionRepository.class));

    Set<String> methods =
        Arrays.stream(SessionRepository.class.getMethods())
            .filter(method -> method.getDeclaringClass() != Object.class)
            .map(Method::getName)
            .collect(Collectors.toSet());

    assertEquals(
        Set.of("save", "saveAndFlush", "findByTokenHash", "findByIdAndOrganizationId"), methods);
  }
}
