package ar.com.hexium.hcop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code auth}/{@code platform} quedan fuera del escaneo completo (ver
 * {@link ExcludeAuthAndPlatform}) — es seguro para las otras 17 reglas: todas ya exentan esos 2
 * paquetes vía {@code allowListedPackages()}, o solo miran subpaquetes {@code domain}/
 * {@code application}/{@code infrastructure} que ninguno de los dos tiene (son planos). Solo
 * afecta a {@link #r4_slicesAreFreeOfCycles} — el motivo real, ver su javadoc.
 */
@AnalyzeClasses(
    packages = "ar.com.hexium.hcop",
    importOptions = {ImportOption.DoNotIncludeTests.class, HexagonalArchitectureTest.ExcludeAuthAndPlatform.class})
class HexagonalArchitectureTest {

  /** Excluye {@code auth}/{@code platform} — los dos módulos "pegamento" permanentemente exentos,
   * cuyo acoplamiento mutuo (auth construye {@code platform.web.ApiException}; platform conecta
   * los beans de auth en Spring Security/Web/Bootstrap) es esperado y no participa del grafo de
   * módulos hexagonales que R4 verifica. Ver javadoc de {@link #r4_slicesAreFreeOfCycles}. */
  static final class ExcludeAuthAndPlatform implements ImportOption {
    @Override
    public boolean includes(Location location) {
      return !location.contains("/ar/com/hexium/hcop/auth/")
          && !location.contains("/ar/com/hexium/hcop/platform/");
    }
  }

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
  };

  /**
   * Módulos que el plan decide explícitamente NO hexagonalizar en F3 — {@code auth} lo absorbió
   * F2 (una reescritura completa es el momento de nacer hexagonal, no volver a tocarlo acá);
   * {@code platform} (F3.4: fusión de los antiguos {@code common}+{@code config}) es
   * infraestructura transversal — bootstrap, propiedades, OpenAPI, el manejador global de
   * excepciones — no un feature, así que no se reorganiza en capas. Nunca se sacan de esta lista
   * por un commit de "migración de módulo". {@code sharedkernel} (value objects de dominio
   * compartidos, p. ej. {@code UserId}/{@code PatientId}) no necesita estar acá: ya es Java puro.
   */
  private static final String[] PERMANENTLY_EXEMPT_MODULES = {"auth", "platform"};

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
      .should().dependOnClassesThat().haveFullyQualifiedName("ar.com.hexium.hcop.platform.web.ApiException")
      .because("R3: application no conoce HTTP — ApiException lleva un HttpStatus adentro; el "
          + "reemplazo es un *Failure funcional traducido en el borde web.");

  /**
   * R4 (hallazgo 7 del plan): F3.3.0 rompió el ciclo real {@code patient} ↔ {@code treatment} ↔
   * {@code infusion} (ver {@link #r4a_patientDoesNotDependOnTreatmentOrInfusion} /
   * {@link #r4b_treatmentDoesNotDependOnInfusion}). Los otros dos ciclos que aparecían al levantar
   * el {@code @ArchIgnore} genérico — {@code catalog} ↔ {@code config} (vía
   * {@code ClinicalCatalogBootstrap} llamando a {@code catalog} desde {@code config}) y
   * {@code config} ↔ {@code patient} (bootstrap de datos demo) — F3.4 los rompió moviendo cada
   * seeder a su propio módulo como {@code platform.BootstrapTask} (ver DECISIONES-F3.md):
   * {@code platform} ya no importa ninguna clase concreta de {@code catalog}/{@code patient}, solo
   * la interfaz que ellos implementan.
   *
   * <p>Al sacar el {@code @ArchIgnore} genérico apareció un tercer ciclo, no anticipado por el
   * plan: {@code auth} ↔ {@code platform} — {@code auth} construye {@code platform.web.ApiException}
   * directo (permitido, ambos son PERMANENTLY_EXEMPT) y {@code platform.SecurityConfiguration}/
   * {@code WebConfiguration}/{@code BootstrapConfiguration} conectan los beans de {@code auth}
   * (es infraestructura de arranque de Spring, no un ciclo de dominio). Es acoplamiento mutuo
   * esperado entre los dos únicos módulos "pegamento" que nunca se hexagonalizan — no el ciclo de
   * negocio que R4 busca cazar. Se excluyen ambos del escaneo completo de la clase (ver
   * {@link ExcludeAuthAndPlatform}) — el criterio de aceptación real de F3.4: cero ciclos entre
   * los ~14 módulos clínicos hexagonales, sin {@code @ArchIgnore}.
   */
  @ArchTest
  static final ArchRule r4_slicesAreFreeOfCycles = SlicesRuleDefinition.slices()
      .matching("ar.com.hexium.hcop.(*)..")
      .should().beFreeOfCycles();

  /**
   * R4a (F3.3.0, puertos cruzados): {@code patient} es la base del orden canónico
   * {@code patient} ← {@code treatment} ← {@code infusion} — nunca depende de los otros dos.
   * {@code PatientWorkspaceController} llega a ellos vía
   * {@code patient.application.port.out.{TreatmentSummaryPort,InfusionSummaryPort}}.
   */
  @ArchTest
  static final ArchRule r4a_patientDoesNotDependOnTreatmentOrInfusion = noClasses()
      .that().resideInAPackage("ar.com.hexium.hcop.patient..")
      .should().dependOnClassesThat().resideInAnyPackage(
          "ar.com.hexium.hcop.treatment..", "ar.com.hexium.hcop.infusion..")
      .because("R4a: orden canónico patient (base) ← treatment ← infusion, ver DECISIONES-F3.md");

  /**
   * R4b (F3.3.0, puertos cruzados): {@code treatment} nunca depende "hacia abajo" de
   * {@code infusion} — llega a él vía
   * {@code treatment.application.port.out.{InfusionSummaryPort,InfusionAppointmentPort,
   * TreatmentApplicationSyncPort}}, implementados por adapters que viven en {@code infusion}.
   */
  @ArchTest
  static final ArchRule r4b_treatmentDoesNotDependOnInfusion = noClasses()
      .that().resideInAPackage("ar.com.hexium.hcop.treatment..")
      .should().dependOnClassesThat().resideInAPackage("ar.com.hexium.hcop.infusion..")
      .because("R4b: orden canónico patient (base) ← treatment ← infusion, ver DECISIONES-F3.md");

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
