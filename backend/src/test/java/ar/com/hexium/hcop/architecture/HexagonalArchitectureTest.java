package ar.com.hexium.hcop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "ar.com.hexium.hcop",
    importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

  private static final String[] FRAMEWORK_PACKAGES = {
      "org.springframework..",
      "jakarta.servlet..",
      "jakarta.validation..",
      "org.flywaydb..",
      "org.postgresql..",
      "tools.jackson..",
      "com.fasterxml.jackson.databind..",
      "io.swagger.v3.."
  };

  @ArchTest
  static final ArchRule domainIsIndependentFromFrameworks =
      noClasses()
          .that().resideInAPackage("..domain..")
          .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORK_PACKAGES)
          .because("el dominio debe seguir siendo Java puro y probarse sin infraestructura");

  @ArchTest
  static final ArchRule applicationDoesNotDependOnInfrastructure =
      noClasses()
          .that().resideInAPackage("..application..")
          .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
          .because("los casos de uso dependen de puertos y no de adaptadores concretos");

  @ArchTest
  static final ArchRule applicationDoesNotDependOnWebOrPersistenceFrameworks =
      noClasses()
          .that().resideInAPackage("..application..")
          .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORK_PACKAGES)
          .because("la aplicación debe poder ejecutarse sin Spring MVC, JDBC, JSON ni Swagger");

  @ArchTest
  static final ArchRule persistenceDoesNotDependOnWeb =
      noClasses()
          .that().resideInAPackage("..infrastructure.persistence..")
          .should().dependOnClassesThat().resideInAPackage("..infrastructure.web..")
          .because("persistencia y HTTP son adaptadores hermanos");

  @ArchTest
  static final ArchRule webDoesNotDependOnPersistence =
      noClasses()
          .that().resideInAPackage("..infrastructure.web..")
          .should().dependOnClassesThat().resideInAPackage("..infrastructure.persistence..")
          .because("el adaptador web sólo debe invocar puertos de entrada");
}
