package ar.com.hexium.hcop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

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

  /**
   * F3.0.1: la allow-list de módulos legacy — <b>es el tracker de progreso ejecutable de F3</b>.
   * Cada módulo se saca de {@link #TRACKED_LEGACY_MODULES} en el mismo commit que lo deja
   * cumpliendo domain/application/infrastructure (el último paso del gate por módulo, ver
   * PROGRESO.md). Las reglas R1/R2/R5/R6/R7/R8/R9 de abajo se relajan solo para lo que sigue acá.
   */
  private static final String[] TRACKED_LEGACY_MODULES = {
      "catalog", "diagnosis", "infusion", "integration", "media",
      "patient", "qr", "treatment", "workflow"
  };

  /**
   * Módulos que el plan decide explícitamente NO hexagonalizar en F3 — {@code auth} lo absorbió
   * F2 (una reescritura completa es el momento de nacer hexagonal, no volver a tocarlo acá);
   * {@code config}/{@code common} son infraestructura transversal (F3.4 los renombra a
   * {@code platform}/{@code sharedkernel}, no los reorganiza en capas). Nunca se sacan de esta
   * lista por un commit de "migración de módulo".
   */
  private static final String[] PERMANENTLY_EXEMPT_MODULES = {"auth", "common", "config"};

  private static String[] allowListedPackages() {
    return Stream.concat(
            Arrays.stream(TRACKED_LEGACY_MODULES), Arrays.stream(PERMANENTLY_EXEMPT_MODULES))
        .map(module -> "ar.com.hexium.hcop." + module + "..")
        .toArray(String[]::new);
  }

  /** Excluye records anidados (los DTO del patrón, "anidados como records") y package-info — la
   * regla de naming de puertos aplica solo a la interfaz de primer nivel, no a sus comandos/vistas. */
  private static final DescribedPredicate<JavaClass> IS_TOP_LEVEL_NAMED_CLASS = DescribedPredicate.describe(
      "ser una clase de primer nivel con nombre propio",
      javaClass -> javaClass.getEnclosingClass().isEmpty() && !javaClass.getSimpleName().equals("package-info"));

  /** El switch exhaustivo de un *FailureAdvice sobre el enum Type del *Failure genera, además de
   * la clase Advice, una clase sintética de soporte del compilador ({@code Advice$1}) — su
   * "simple name" no termina en "Advice", pero su nombre completo sí lo contiene. */
  private static final DescribedPredicate<JavaClass> NOT_RELATED_TO_ADVICE = DescribedPredicate.describe(
      "no pertenecer a un *Advice (ni su clase sintética de switch)",
      javaClass -> !javaClass.getFullName().contains("Advice"));

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

  // ---------- F3.0.1: 10 reglas nuevas, permisivas contra TRACKED_LEGACY_MODULES ----------

  @ArchTest
  static final ArchRule r1_noLooseClassesAtModuleRoot = noClasses()
      .that().resideOutsideOfPackages(allowListedPackages())
      .should().resideInAPackage("ar.com.hexium.hcop.*")
      .because("R1: un módulo migrado no tiene clases sueltas en la raíz de su paquete, todo vive "
          + "en domain/application/infrastructure — la regla de mayor valor: es la allow-list "
          + "de arriba, el tracker ejecutable de F3.");

  @ArchTest
  static final ArchRule r2_onlyPersistenceOrCatalogAdaptersTouchJdbc = noClasses()
      .that().resideOutsideOfPackages(allowListedPackages())
      .and().resideOutsideOfPackages("..infrastructure.persistence..", "..infrastructure.catalog..")
      .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
      .because("R2: JDBC es un detalle de un adapter de persistencia o catálogo — nunca de "
          + "application ni de otro adapter (hallazgo de fugas del plan).");

  @ArchTest
  static final ArchRule r3_applicationDoesNotDependOnApiException = noClasses()
      .that().resideInAPackage("..application..")
      .should().dependOnClassesThat().haveFullyQualifiedName("ar.com.hexium.hcop.common.ApiException")
      .because("R3: application no conoce HTTP — ApiException lleva un HttpStatus adentro; el "
          + "reemplazo es un *Failure funcional traducido en el borde web.");

  /**
   * R4 (hallazgo 7 del plan): hoy hay un ciclo real entre módulos — @ArchIgnore a propósito hasta
   * que F3.3.0 (puertos cruzados) lo rompa. Es la meta visible de esa etapa: sacar el
   * {@code @ArchIgnore} y ver la regla pasar es el criterio de aceptación.
   */
  @ArchTest
  @ArchIgnore
  static final ArchRule r4_slicesAreFreeOfCycles = SlicesRuleDefinition.slices()
      .matching("ar.com.hexium.hcop.(*)..")
      .should().beFreeOfCycles();

  @ArchTest
  static final ArchRule r5_restControllersOnlyInInfrastructureWeb = classes()
      .that().areAnnotatedWith(RestController.class)
      .and().resideOutsideOfPackages(allowListedPackages())
      .should().resideInAPackage("..infrastructure.web..")
      .because("R5: el contador de progreso más comunicable del plan — hoy fallan 24 de 30.");

  @ArchTest
  static final ArchRule r6_webDoesNotDependOnApplicationService = noClasses()
      .that().resideInAPackage("..infrastructure.web..")
      .and().resideOutsideOfPackages(allowListedPackages())
      .and(NOT_RELATED_TO_ADVICE)
      .should().dependOnClassesThat().resideInAPackage("..application.service..")
      .because("R6: el controller solo conoce el puerto de entrada — *Advice es la única "
          + "excepción tolerada, porque necesita el *Failure funcional (vive en application.service) "
          + "para traducirlo a HTTP.");

  @ArchTest
  static final ArchRule r7_applicationServicesAreFinal = classes()
      .that().haveSimpleNameEndingWith("ApplicationService")
      .and().resideOutsideOfPackages(allowListedPackages())
      .should().haveModifier(JavaModifier.FINAL)
      .because("R7: final es deliberado — fuerza wiring explícito y lo hace instanciable en test "
          + "sin contexto Spring.");

  @ArchTest
  static final ArchRule r7_applicationServicesAreNotSpringBeansThemselves = classes()
      .that().haveSimpleNameEndingWith("ApplicationService")
      .and().resideOutsideOfPackages(allowListedPackages())
      .should().notBeAnnotatedWith(Service.class)
      .because("R7: sin @Service — quien decora con @Service/@Transactional es el wiring de "
          + "infrastructure.configuration (los dos patrones del plan), no el propio servicio.");

  @ArchTest
  static final ArchRule r8_transactionalMethodsOnlyInInfrastructure = methods()
      .that().areAnnotatedWith(Transactional.class)
      .and().areDeclaredInClassesThat().resideOutsideOfPackages(allowListedPackages())
      .should().beDeclaredInClassesThat().resideInAPackage("..infrastructure..")
      .because("R8: @Transactional es un detalle de wiring de infraestructura, nunca de un "
          + "*ApplicationService (que ni siquiera es un bean de Spring, ver R7).");

  @ArchTest
  static final ArchRule r9_inboundPortsAreNamedUseCase = classes()
      .that().resideInAPackage("..application.port.in..")
      .and().resideOutsideOfPackages(allowListedPackages())
      .and(IS_TOP_LEVEL_NAMED_CLASS)
      .should().haveSimpleNameEndingWith("UseCase")
      .because("R9: naming de puertos de entrada — *UseCase, patrón real de "
          + "configuration/guide/protocol (el doc base los deja sueltos en application/).");

  @ArchTest
  static final ArchRule r9_outboundPortsAreNamedStorePortOrException = classes()
      .that().resideInAPackage("..application.port.out..")
      .and().resideOutsideOfPackages(allowListedPackages())
      .and(IS_TOP_LEVEL_NAMED_CLASS)
      .should().haveSimpleNameEndingWith("Store")
      .orShould().haveSimpleNameEndingWith("Port")
      .orShould().haveSimpleNameEndingWith("Exception")
      .because("R9: naming de puertos de salida — *Store para persistencia, *Port el resto, "
          + "*Exception para las excepciones de puerto (patrón real, no está en el doc base literal).");

  // R10 no es una regla ArchUnit — es OpenApiDocumentationKeysTest (F3.0.3), el guardián del
  // hallazgo 1: cada clave Controller.metodo de OpenApiConfiguration resuelve a un @RestController
  // y un método reales. Documentado acá para que la numeración R1-R10 del plan quede completa.
}
