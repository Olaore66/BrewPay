package com.brewpay.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PackageRulesTest {

  private static final String KOLOMONI = "com.brewpay.banking.kolomoni..";

  private final JavaClasses productionClasses =
      new ClassFileImporter().importPath(Path.of("target", "classes"));

  @Test
  void domainPackagesDoNotDependOnTheKolomoniAdapter() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAnyPackage(
                "com.brewpay.ledger..",
                "com.brewpay.payments..",
                "com.brewpay.identity..",
                "com.brewpay.access..",
                "com.brewpay.audit..",
                "com.brewpay.reconciliation..",
                "com.brewpay.notifications..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(KOLOMONI)
            .because("partner payloads stay in the adapter");

    rule.check(productionClasses);
  }

  @Test
  void providerPortDoesNotDependOnTheKolomoniAdapter() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("com.brewpay.banking")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(KOLOMONI)
            .because("the port is the boundary the adapter implements");

    rule.check(productionClasses);
  }

  @Test
  void ledgerDoesNotDependOnIdentity() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("com.brewpay.ledger..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.brewpay.identity..")
            .because("the session already carries the user and the organization");

    rule.check(productionClasses);
  }

  @Test
  void ledgerDoesNotDependOnPayments() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("com.brewpay.ledger..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.brewpay.payments..")
            .because("payments posts to the ledger");

    rule.check(productionClasses);
  }
}
