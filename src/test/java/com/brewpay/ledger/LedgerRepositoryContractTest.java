package com.brewpay.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

class LedgerRepositoryContractTest {

  @Test
  void ledgerHistoryCannotBeDeletedOrReadWithoutTheOrganization() {
    assertClosed(
        LedgerAccountRepository.class,
        Set.of(
            "save",
            "saveAndFlush",
            "findByIdAndOrganizationId",
            "findByOrganizationIdOrderByCreatedAtAsc",
            "existsByOrganizationIdAndCode"));
    assertClosed(
        JournalRepository.class,
        Set.of(
            "save",
            "saveAndFlush",
            "findByIdAndOrganizationId",
            "findByOrganizationIdOrderByCreatedAtAsc",
            "existsByOrganizationIdAndReversesJournalId"));
    assertClosed(
        JournalLineRepository.class,
        Set.of("save", "saveAndFlush", "findByJournalIdAndOrganizationIdOrderByPositionAsc"));
    assertNoSetters(LedgerAccount.class);
    assertNoSetters(Journal.class);
    assertNoSetters(JournalLine.class);
  }

  private static void assertClosed(Class<?> repository, Set<String> allowed) {
    assertFalse(CrudRepository.class.isAssignableFrom(repository));
    assertFalse(JpaRepository.class.isAssignableFrom(repository));
    Set<String> methods =
        Arrays.stream(repository.getMethods())
            .filter(method -> method.getDeclaringClass() != Object.class)
            .map(Method::getName)
            .collect(Collectors.toSet());
    assertEquals(allowed, methods);
  }

  private static void assertNoSetters(Class<?> type) {
    boolean hasSetter =
        Arrays.stream(type.getDeclaredMethods())
            .anyMatch(method -> method.getName().startsWith("set"));
    assertFalse(hasSetter);
  }
}
