package ar.com.hexium.hcop.integration.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

/** Direct JDBC contract: all connections and deadlines are synthetic and local. */
class ExternalStudyPathologyTest {
  private static final String DNI = "99000123";
  private static final String JDBC = "jdbc:postgresql://fixture/db";
  private static final String FILE = "carpeta uno/informe A+B.pdf";
  private static final String REPORT = "https://pathology.fixture.invalid/reports/carpeta%20uno/informe%20A%2BB.pdf";
  // Legacy pathology identity was the first 24 hex characters of SHA-256(report URL).
  private static final String STUDY_ID = "external:patologia:e4eae89e7a8503e7e12de3bb";
  private final ObjectMapper mapper = new ObjectMapper();
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement statement = mock(PreparedStatement.class);
  private final ResultSet rows = mock(ResultSet.class);
  private final ExternalStudyHttp http = mock(ExternalStudyHttp.class);
  private MockEnvironment environment;

  @BeforeEach void setUp() throws Exception {
    environment = new MockEnvironment()
        .withProperty("PATHOLOGY_DB_JDBC_URL", JDBC)
        .withProperty("PATHOLOGY_DB_USERNAME", "fixture-user")
        .withProperty("PATHOLOGY_DB_PASSWORD", "fixture-password")
        .withProperty("EXTERNAL_STUDIES_PATHOLOGY_FILES_BASE_URL", "https://pathology.fixture.invalid/reports/")
        .withProperty("EXTERNAL_STUDIES_MAX_RESULTS", "7");
    when(http.remainingMillis()).thenReturn(5_000L);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.getString("archivo")).thenReturn(FILE);
    when(rows.getString("fecha_carga")).thenReturn("2026-09-16 08:30:00");
  }

  @Test void sinJdbcConsultaSoloFallbackDePatologiaSinIntentarAbrirBase() {
    environment.setProperty("PATHOLOGY_DB_JDBC_URL", "");
    when(http.get(anyString())).thenReturn("0 ESTUDIOS ENCONTRADOS");
    try (var jdbc = mockStatic(DriverManager.class)) {
      var result = search();
      assertThat(result.partial()).isFalse();
      assertThat(result.studies()).isEmpty();
      var requestedUrl = ArgumentCaptor.forClass(String.class);
      verify(http).get(requestedUrl.capture());
      var uri = URI.create(requestedUrl.getValue());
      assertThat(uri.getHost()).isEqualTo("fuesmen.com");
      assertThat(uri.getPath()).isEqualTo("/buscar_estudios_externos/lista_estudios_rr.php");
      assertThat(uri.getRawQuery().split("&")).containsExactlyInAnyOrder("dni=" + DNI, "fuesmen=no", "idm=no", "centro=no", "espanol=no", "labo=no");
      jdbc.verifyNoInteractions();
      verify(http, never()).post(anyString(), any());
    }
  }

  @Test void fallbackConDosPdfYEspaciosEnSusEnlacesConservaAmbasFilas() {
    environment.setProperty("PATHOLOGY_DB_JDBC_URL", "");
    when(http.get(anyString())).thenReturn("""
        12/31/20232 ESTUDIOS ENCONTRADOS <BR><table>
        <tr><td>2026-09-16</td><td>PATOLOGIA</td><td>PATOLOGIA</td><td>PATOLOGIA</td>
          <td><a href='http://pangeasystem.com/registro_tumores/carpeta%20ficticia/informe uno.pdf'>Ver informe</a></td>
          <td><a href='http://pangeasystem.com/registro_tumores/carpeta%20ficticia/informe uno.pdf'>Ver estudio</a></td></tr>
        <tr><td>2026-09-15</td><td>PATOLOGIA</td><td>PATOLOGIA</td><td>PATOLOGÍA</td>
          <td><a href='http://pangeasystem.com/registro_tumores/carpeta ficticia/informe dos.pdf'>Ver informe</a></td>
          <td><a href='http://pangeasystem.com/registro_tumores/carpeta ficticia/informe dos.pdf'>Ver estudio</a></td></tr>
        </table>
        """);
    try (var jdbc = mockStatic(DriverManager.class)) {
      var result = search();
      assertThat(result.partial()).isFalse();
      assertThat(result.message()).isBlank();
      assertThat(result.studies()).hasSize(2);
      assertThat(result.studies()).extracting(ExternalStudy::date).containsExactly("2026-09-16", "2026-09-15");
      assertThat(result.studies()).extracting(ExternalStudy::reportUrl).containsExactly(
          "http://pangeasystem.com/registro_tumores/carpeta%20ficticia/informe%20uno.pdf",
          "http://pangeasystem.com/registro_tumores/carpeta%20ficticia/informe%20dos.pdf");
      assertThat(result.studies()).allSatisfy(study -> {
        assertThat(study.sourceId()).isEqualTo("patologia");
        assertThat(study.studyUrl()).isEqualTo(study.reportUrl());
        assertThat(study.id()).startsWith("external:patologia:");
      });
      assertThat(result.studies()).extracting(ExternalStudy::id).doesNotHaveDuplicates();
      jdbc.verifyNoInteractions();
    }
  }

  @Test void esquemaInvalidoOCredencialesAusentesSeRechazanAntesDeAbrirBase() {
    try (var jdbc = mockStatic(DriverManager.class)) {
      environment.setProperty("PATHOLOGY_DB_JDBC_URL", "jdbc:unsupported://fixture/db");
      assertThatThrownBy(this::search).isInstanceOf(ExternalStudyFailure.class)
          .hasMessageContaining("PostgreSQL o MySQL");
      environment.setProperty("PATHOLOGY_DB_JDBC_URL", JDBC);
      environment.setProperty("PATHOLOGY_DB_PASSWORD", "");
      assertThatThrownBy(this::search).isInstanceOf(ExternalStudyFailure.class)
          .hasMessageContaining("credenciales");
      jdbc.verifyNoInteractions();
      verify(http, never()).get(anyString());
    }
  }

  @Test void consultaPreparadaPorDniYLimiteConservaIdentidadYEnlaceCanonico() throws Exception {
    when(rows.next()).thenReturn(true, true, false);
    try (var jdbc = connected()) {
      var result = search();
      assertThat(result.partial()).isFalse();
      assertThat(result.message()).isBlank();
      assertThat(result.studies()).containsExactly(expectedStudy());
      var sql = ArgumentCaptor.forClass(String.class);
      verify(connection).prepareStatement(sql.capture());
      assertThat(sql.getValue()).contains("SELECT archivo, fecha_carga", "WHERE dni = ?", "ORDER BY fecha_carga DESC", "LIMIT ?")
          .doesNotContain(DNI, "fixture-user", "fixture-password");
      verify(statement).setString(1, DNI);
      verify(statement).setInt(2, 7);
      verify(statement).setQueryTimeout(5);
      var properties = ArgumentCaptor.forClass(Properties.class);
      jdbc.verify(() -> DriverManager.getConnection(eq(JDBC), properties.capture()));
      assertThat(properties.getValue()).containsEntry("user", "fixture-user").containsEntry("password", "fixture-password")
          .containsEntry("sslmode", "verify-full").containsEntry("connectTimeout", "5").containsEntry("socketTimeout", "5");
      assertThat(mapper.writeValueAsString(result)).doesNotContain(DNI, JDBC, "fixture-password", "fixture-user");
      verify(http, never()).get(anyString());
      verify(http, never()).post(anyString(), any());
      verifyClosed();
    }
  }

  @Test void ausenciaRealDeFilasEsVacioExitosoYLiberaRecursos() throws Exception {
    when(rows.next()).thenReturn(false);
    try (var ignored = connected()) {
      var result = search();
      assertThat(result.partial()).isFalse();
      assertThat(result.studies()).isEmpty();
      assertThat(result.message()).isBlank();
      verifyClosed();
    }
  }

  @Test void archivoAusenteRutaInseguraOFechaImposibleNoOcultanFilaValida() throws Exception {
    when(rows.next()).thenReturn(true, true, true, true, true, false);
    when(rows.getString("archivo")).thenReturn(null, "../fuera.pdf", "https://untrusted.fixture.invalid/document", "fecha.pdf", FILE);
    when(rows.getString("fecha_carga")).thenReturn("2026-09-16", "2026-09-16", "2026-09-16", "31/02/2026", "2026-09-16");
    try (var ignored = connected()) {
      var result = search();
      assertThat(result.partial()).isTrue();
      assertThat(result.studies()).containsExactly(expectedStudy());
      assertThat(mapper.writeValueAsString(result)).doesNotContain("../", "untrusted.fixture.invalid", "31/02/2026", "fecha.pdf");
      verifyClosed();
    }
  }

  @Test void falloSqlTrasPrimeraFilaLaConservaSinExponerDetalleDelServidor() throws Exception {
    when(rows.next()).thenReturn(true).thenThrow(new SQLException("SELECT archivo error " + DNI + " fixture-password " + JDBC));
    try (var ignored = connected()) {
      var result = search();
      assertThat(result.partial()).isTrue();
      assertThat(result.studies()).containsExactly(expectedStudy());
      assertThat(result.message()).contains("Patología").doesNotContain("SELECT", DNI, "fixture-password", JDBC);
      verifyClosed();
    }
  }

  @Test void plazoAgotadoDuranteLecturaConservaFilasPreviasYCierraConsulta() throws Exception {
    var expired = new AtomicBoolean();
    var reads = new AtomicInteger();
    when(http.remainingMillis()).thenAnswer(call -> {
      if (expired.get()) throw new ExternalStudyFailure("La fuente excedió el tiempo de espera.");
      return 5_000L;
    });
    when(rows.next()).thenAnswer(call -> {
      if (reads.incrementAndGet() > 1) expired.set(true);
      return true;
    });
    try (var ignored = connected()) {
      var result = search();
      assertThat(result.partial()).isTrue();
      assertThat(result.studies()).containsExactly(expectedStudy());
      assertThat(result.message()).contains("tiempo de espera");
      assertThat(reads).hasValue(2);
      verifyClosed();
    }
  }

  @Test void falloDeConexionEsParcialSeguroSinFallbackHttp() throws Exception {
    // SQLException consults DriverManager's logger during construction; create
    // it before static mocking so it cannot interrupt an unfinished stubbing.
    var connectionFailure = new SQLException("authentication failed: fixture-user fixture-password " + DNI + " " + JDBC);
    try (var jdbc = mockStatic(DriverManager.class)) {
      jdbc.when(() -> DriverManager.getConnection(eq(JDBC), any(Properties.class)))
          .thenThrow(connectionFailure);
      var result = search();
      assertThat(result.partial()).isTrue();
      assertThat(result.studies()).isEmpty();
      assertThat(result.message()).contains("Patología");
      assertThat(mapper.writeValueAsString(result)).doesNotContain("authentication", "fixture-user", "fixture-password", DNI, JDBC);
      verify(http, never()).get(anyString());
      verify(http, never()).post(anyString(), any());
    }
  }

  private MockedStatic<DriverManager> connected() {
    var jdbc = mockStatic(DriverManager.class);
    jdbc.when(() -> DriverManager.getConnection(eq(JDBC), any(Properties.class))).thenReturn(connection);
    return jdbc;
  }

  private ExternalStudyResult search() {
    return new ExternalStudyProviders(mapper, new ExternalStudyConfiguration(environment), http).search(ExternalStudySource.PATOLOGIA, DNI);
  }

  private static ExternalStudy expectedStudy() {
    return new ExternalStudy(STUDY_ID, "2026-09-16", "Patología", "Anatomía patológica", "Patología", REPORT, REPORT, "patologia");
  }

  private void verifyClosed() throws Exception {
    verify(rows).close();
    verify(statement).close();
    verify(connection).close();
  }
}
