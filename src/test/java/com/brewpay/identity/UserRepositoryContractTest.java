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
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

class UserRepositoryContractTest {

  @Test
  void userReadsAreScopedToAnOrganization() {
    assertFalse(CrudRepository.class.isAssignableFrom(UserRepository.class));
    assertFalse(ListCrudRepository.class.isAssignableFrom(UserRepository.class));
    assertFalse(PagingAndSortingRepository.class.isAssignableFrom(UserRepository.class));
    assertFalse(JpaRepository.class.isAssignableFrom(UserRepository.class));

    Set<String> methods =
        Arrays.stream(UserRepository.class.getMethods())
            .filter(method -> method.getDeclaringClass() != Object.class)
            .map(Method::getName)
            .collect(Collectors.toSet());

    assertEquals(
        Set.of(
            "save",
            "findByIdAndOrganizationId",
            "findByOrganizationIdOrderByCreatedAtAsc",
            "existsByOrganizationIdAndEmail"),
        methods);
  }
}
