package ar.com.hexium.hcop.config;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.hexium.hcop.catalog.AjccCatalogController;
import ar.com.hexium.hcop.catalog.LegacyCatalogController;
import ar.com.hexium.hcop.tools.infrastructure.web.CalculatorCatalogController;
import ar.com.hexium.hcop.integration.LlmController;
import ar.com.hexium.hcop.integration.LlmController.AgentChatRequest;
import ar.com.hexium.hcop.patient.ClinicalDocumentController;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.JsonNode;

class OpenApiConfigurationTest {

  @Test
  void registraLosContratosReutilizablesDeErrorYAutenticacion() {
    OpenAPI openApi = new OpenAPI();

    new OpenApiConfiguration().reusableSchemas().customise(openApi);

    assertThat(openApi.getComponents().getSchemas())
        .containsKeys("ApiError", "AuthenticationRequired");
    assertThat(openApi.getComponents().getSchemas().get("ApiError").getRequired())
        .containsExactlyInAnyOrder("ok", "error", "status");
    assertThat(openApi.getComponents().getSchemas().get("AuthenticationRequired").getRequired())
        .contains(
            "ok",
            "authenticated",
            "loginRequired",
            "error",
            "code",
            "status");
  }

  @Test
  void registraLosContratosTipadosDelAgenteClinico() {
    OpenAPI openApi = new OpenAPI();

    new OpenApiConfiguration().reusableSchemas().customise(openApi);

    assertThat(openApi.getComponents().getSchemas())
        .containsKeys(
            "AgentHistoryMessage",
            "AgentChatRequest",
            "AgentTableArtifact",
            "AgentChartPoint",
            "AgentChartSeries",
            "AgentChartArtifact",
            "AgentArtifact",
            "AgentHighlight",
            "AgentChatResponse",
            "LlmStatusResponse");
    var request = openApi.getComponents().getSchemas().get("AgentChatRequest");
    assertThat(request.getRequired()).containsExactly("message");
    assertThat(((io.swagger.v3.oas.models.media.Schema<?>)
        request.getProperties().get("message")).getMaxLength()).isEqualTo(8_000);
    assertThat(((io.swagger.v3.oas.models.media.Schema<?>)
        request.getProperties().get("clinicalText")).getMaxLength()).isEqualTo(350_000);
    assertThat(request.getProperties()).containsKeys(
        "history", "timelineEvents", "consultAgents");
    assertThat(openApi.getComponents().getSchemas().get("AgentChatResponse").getRequired())
        .containsExactlyInAnyOrder(
            "ok", "answer", "model", "artifacts", "followUps", "highlights");
    var response = openApi.getComponents().getSchemas().get("AgentChatResponse");
    assertThat(((io.swagger.v3.oas.models.media.ArraySchema)
        response.getProperties().get("artifacts")).getMaxItems()).isEqualTo(8);
    assertThat(((io.swagger.v3.oas.models.media.ArraySchema)
        response.getProperties().get("artifacts")).getItems().get$ref())
        .isEqualTo("#/components/schemas/AgentArtifact");
    assertThat(((io.swagger.v3.oas.models.media.ArraySchema)
        response.getProperties().get("followUps")).getMaxItems()).isEqualTo(8);
    assertThat(((io.swagger.v3.oas.models.media.ArraySchema)
        response.getProperties().get("highlights")).getMaxItems()).isEqualTo(20);
    assertThat(openApi.getComponents().getSchemas().get("AgentArtifact").getOneOf())
        .extracting(item -> ((io.swagger.v3.oas.models.media.Schema<?>) item).get$ref())
        .containsExactly(
            "#/components/schemas/AgentTableArtifact",
            "#/components/schemas/AgentChartArtifact");
    assertThat(openApi.getComponents().getSchemas().get("LlmStatusResponse").getRequired())
        .containsExactlyInAnyOrder("ok", "enabled", "model", "provider", "configured");
  }

  @Test
  void aplicaEnSwaggerLosContratosYElPermisoDelAgente() throws Exception {
    Operation operation = operationWithSuccess();
    HandlerMethod handler = handler(
        "agent", AgentChatRequest.class, HttpServletRequest.class);

    new OpenApiConfiguration().documentedOperations().customize(operation, handler);

    assertThat(operation.getExtensions().get("x-hcop-permission"))
        .isEqualTo("section.agent.view");
    assertThat(operation.getRequestBody().getContent()
        .get("application/json").getSchema().get$ref())
        .isEqualTo("#/components/schemas/AgentChatRequest");
    assertThat(operation.getResponses().get("200").getContent()
        .get("application/json").getSchema().get$ref())
        .isEqualTo("#/components/schemas/AgentChatResponse");
  }

  @Test
  void aplicaEnSwaggerElContratoYElPermisoDelEstadoLlm() throws Exception {
    Operation operation = operationWithSuccess();
    HandlerMethod handler = handler("status", HttpServletRequest.class);

    new OpenApiConfiguration().documentedOperations().customize(operation, handler);

    assertThat(operation.getExtensions().get("x-hcop-permission"))
        .isEqualTo("section.agent.view");
    assertThat(operation.getResponses().get("200").getContent()
        .get("application/json").getSchema().get$ref())
        .isEqualTo("#/components/schemas/LlmStatusResponse");
  }

  @Test
  void documentaElPermisoDeLosDosContratosCompatiblesDeProtocolos() throws Exception {
    LegacyCatalogController controller = new LegacyCatalogController(null, null, null, null);
    HandlerMethod list = new HandlerMethod(
        controller,
        LegacyCatalogController.class.getDeclaredMethod(
            "protocols", String.class, HttpServletRequest.class));
    HandlerMethod detail = new HandlerMethod(
        controller,
        LegacyCatalogController.class.getDeclaredMethod(
            "protocolDetail", String.class, String.class, HttpServletRequest.class));

    Operation listOperation = operationWithSuccess();
    Operation detailOperation = operationWithSuccess();
    OpenApiConfiguration configuration = new OpenApiConfiguration();
    configuration.documentedOperations().customize(listOperation, list);
    configuration.documentedOperations().customize(detailOperation, detail);

    assertThat(listOperation.getSummary()).isEqualTo("Listar protocolos compatibles");
    assertThat(detailOperation.getSummary()).isEqualTo("Abrir protocolo compatible");
    assertThat(listOperation.getExtensions().get("x-hcop-permission"))
        .isEqualTo("section.protocols.view");
    assertThat(detailOperation.getExtensions().get("x-hcop-permission"))
        .isEqualTo("section.protocols.view");
    assertThat(listOperation.getSecurity()).isNotEmpty();
    assertThat(detailOperation.getSecurity()).isNotEmpty();
  }

  @Test
  void documentaLosPermisosDiferenciadosDelCatalogoYCalculoAjcc() throws Exception {
    AjccCatalogController controller = new AjccCatalogController(null, null);
    OpenApiConfiguration configuration = new OpenApiConfiguration();

    HandlerMethod list = new HandlerMethod(
        controller,
        AjccCatalogController.class.getDeclaredMethod("list", HttpServletRequest.class));
    HandlerMethod detail = new HandlerMethod(
        controller,
        AjccCatalogController.class.getDeclaredMethod(
            "detail", String.class, HttpServletRequest.class));
    HandlerMethod stage = new HandlerMethod(
        controller,
        AjccCatalogController.class.getDeclaredMethod(
            "stage", java.util.Map.class, HttpServletRequest.class));
    Operation listOperation = operationWithSuccess();
    Operation detailOperation = operationWithSuccess();
    Operation stageOperation = operationWithSuccess();

    configuration.documentedOperations().customize(listOperation, list);
    configuration.documentedOperations().customize(detailOperation, detail);
    configuration.documentedOperations().customize(stageOperation, stage);

    assertThat(listOperation.getExtensions().get("x-hcop-permission"))
        .isEqualTo("section.tools.view");
    assertThat(detailOperation.getExtensions().get("x-hcop-permission"))
        .isEqualTo("section.tools.view");
    assertThat(stageOperation.getExtensions().get("x-hcop-permission"))
        .isEqualTo("section.tools.use");
    assertThat(listOperation.getSecurity()).isNotEmpty();
    assertThat(detailOperation.getSecurity()).isNotEmpty();
    assertThat(stageOperation.getSecurity()).isNotEmpty();
  }

  @Test
  void documentaElCatalogoOperativoDeCalculadorasConPermisoDeUso() throws Exception {
    CalculatorCatalogController controller = new CalculatorCatalogController(null, null, null);
    OpenApiConfiguration configuration = new OpenApiConfiguration();
    HandlerMethod list = new HandlerMethod(
        controller,
        CalculatorCatalogController.class.getDeclaredMethod("list", HttpServletRequest.class));
    Operation operation = operationWithSuccess();

    configuration.documentedOperations().customize(operation, list);

    assertThat(operation.getExtensions().get("x-hcop-permission"))
        .isEqualTo("section.tools.use");
    assertThat(operation.getSummary()).isEqualTo("Listar calculadoras operativas");
    assertThat(operation.getSecurity()).isNotEmpty();
  }

  @Test
  void documentaLosLimitesYCodigosDelEditorDeConclusion() throws Exception {
    ClinicalDocumentController controller = new ClinicalDocumentController(
        null, null, null, null, null);
    HandlerMethod put = new HandlerMethod(
        controller,
        ClinicalDocumentController.class.getDeclaredMethod(
            "put", JsonNode.class, HttpServletRequest.class));
    Operation operation = operationWithSuccess();

    new OpenApiConfiguration().documentedOperations().customize(operation, put);

    assertThat(operation.getExtensions().get("x-hcop-permission"))
        .isEqualTo(
            "section.history.edit + permiso específico de edición si prescriptions, studies o externalStudies cambian");
    assertThat(operation.getDescription())
        .contains(
            "studies",
            "externalStudies",
            "narrative.summary",
            "narrative.plan",
            "narrative.physicalExam",
            "exam.weightKg",
            "exam.heightM",
            "0.01 a 500",
            "0.3 a 2.5",
            "50.000",
            "CLINICAL_SUMMARY_INVALID",
            "CLINICAL_SUMMARY_TOO_LONG",
            "CLINICAL_PLAN_INVALID",
            "CLINICAL_PLAN_TOO_LONG",
            "CLINICAL_SUMMARY_PLAN_EMPTY",
            "CLINICAL_SUMMARY_PLAN_REASON_REQUIRED",
            "CLINICAL_SUMMARY_PLAN_REASON_INVALID",
            "CLINICAL_SUMMARY_PLAN_REASON_TOO_LONG",
            "CLINICAL_PHYSICAL_EXAM_WEIGHT_INVALID",
            "CLINICAL_PHYSICAL_EXAM_WEIGHT_OUT_OF_RANGE",
            "CLINICAL_PHYSICAL_EXAM_HEIGHT_INVALID",
            "CLINICAL_PHYSICAL_EXAM_HEIGHT_OUT_OF_RANGE",
            "CLINICAL_PHYSICAL_EXAM_TEXT_INVALID",
            "CLINICAL_PHYSICAL_EXAM_TEXT_TOO_LONG",
            "CLINICAL_PHYSICAL_EXAM_EMPTY",
            "CLINICAL_PHYSICAL_EXAM_REASON_REQUIRED",
            "CLINICAL_PHYSICAL_EXAM_REASON_INVALID",
            "CLINICAL_PHYSICAL_EXAM_REASON_TOO_LONG",
            "estado canónico",
            "sesión",
            "VERSION_CONFLICT");
  }

  private Operation operationWithSuccess() {
    return new Operation().responses(new ApiResponses()
        .addApiResponse("200", new ApiResponse().description("OK")));
  }

  private HandlerMethod handler(String name, Class<?>... parameterTypes) throws Exception {
    LlmController controller = new LlmController(null, null, null, null, null);
    return new HandlerMethod(
        controller,
        LlmController.class.getDeclaredMethod(name, parameterTypes));
  }
}
