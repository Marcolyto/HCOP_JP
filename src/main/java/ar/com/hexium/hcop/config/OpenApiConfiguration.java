package ar.com.hexium.hcop.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.method.HandlerMethod;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "HCOP JP API",
        version = "1.0.0",
        description = """
            API del sistema integrado de Historia Clínica Oncológica y Hospital de Día.

            La aplicación sigue MVC: los controladores reciben y validan HTTP, los servicios
            concentran reglas clínicas y transacciones, y los repositorios son la única capa
            que accede a PostgreSQL. Los actos clínicos relevantes generan auditoría y
            evoluciones inmutables.
            """,
        contact = @Contact(name = "HCOP JP"),
        license = @License(name = "Uso interno clínico")),
    servers = {
        @Server(url = "/", description = "Servidor actual")
    },
    tags = {
        @Tag(name = "Autenticación", description = "Login obligatorio, sesión y paciente activo."),
        @Tag(name = "Pacientes e historia", description = "Identidad, historia clínica, diagnósticos y evoluciones."),
        @Tag(name = "Tratamientos", description = "Prescripción, protocolos, ciclos y documentos."),
        @Tag(name = "Hospital de Día", description = "Turnero por sillón, farmacia, administración y QR."),
        @Tag(name = "Flujos clínicos", description = "Suspensión, continuidad y solicitudes entre usuarios."),
        @Tag(name = "Configuración", description = "Protocolos, guías, calculadoras, formularios y parámetros."),
        @Tag(name = "Catálogos", description = "AJCC, TNM, CIE-10, SNOMED, drogas y formularios."),
        @Tag(name = "Archivos clínicos", description = "Estudios, imágenes y plantillas con control de sesión."),
        @Tag(name = "Administración", description = "Usuarios, roles, permisos y seguridad."),
        @Tag(name = "Integraciones", description = "Configuración y uso opcional del LLM."),
        @Tag(name = "Estado", description = "Salud y diagnóstico operativo del sistema.")
    })
@SecurityScheme(
    name = "sessionCookie",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.COOKIE,
    paramName = "HCOP_SESSION",
    description = "Cookie HttpOnly obtenida mediante POST /api/auth/login.")
public class OpenApiConfiguration {

  private static final Map<String, Documentation> DOCUMENTATION = Map.ofEntries(
      doc("AuthController.me", "Consultar sesión", "Devuelve el usuario, roles, permisos y paciente activo; no expone el token."),
      doc("AuthController.login", "Iniciar sesión", "Valida usuario y contraseña y crea una cookie HttpOnly SameSite=Strict."),
      doc("AuthController.logout", "Cerrar sesión", "Revoca la sesión actual y elimina su cookie."),
      doc("AuthController.password", "Cambiar contraseña", "Cambia la contraseña y revoca las otras sesiones del usuario."),
      doc("AuthController.activePatient", "Cambiar paciente activo", "Asocia o limpia el paciente activo únicamente para la sesión actual."),
      doc("PatientController.search", "Buscar pacientes", "Sin consulta devuelve los pacientes recientes; con texto filtra por nombre, apellido, DNI, historia clínica o identificador local."),
      doc("PatientController.create", "Crear paciente", "Crea un paciente local, su hoja clínica en blanco y lo deja activo."),
      doc("PatientController.preview", "Previsualizar paciente", "Resume disponibilidad y cantidad de registros antes de abrir la historia."),
      doc("PatientController.importPatient", "Abrir paciente local", "Activa una historia ya consolidada en PostgreSQL; no consulta Lira."),
      doc("ClinicalDocumentController.get", "Leer historia clínica", "Recupera la hoja del paciente activo o la plantilla en blanco."),
      doc("ClinicalDocumentController.put", "Guardar historia clínica", "Guarda con control optimista de revisión para evitar pisar cambios concurrentes."),
      doc("ClinicalDocumentController.restoreDemo", "Compatibilidad de persistencia", "Confirma que la historia es persistente y que no se restaura un demo."),
      doc("DiagnosisController.list", "Listar diagnósticos", "Lista todos los diagnósticos oncológicos no archivados del paciente."),
      doc("DiagnosisController.link", "Validar diagnóstico de tratamiento", "Confirma que el diagnóstico seleccionado pertenece a la historia del paciente."),
      doc("TreatmentController.list", "Listar tratamientos", "Devuelve tratamientos oncológicos locales y su estado actual."),
      doc("TreatmentController.create", "Prescribir tratamiento", "Crea tratamiento, ciclos, logística y una evolución clínica inmutable; una discordancia diagnóstica evidente exige confirmación y motivo clínico."),
      doc("TreatmentController.options", "Opciones de prescripción", "Devuelve diagnósticos y esquemas con su grupo clínico, tipos, intención y estados de consentimiento."),
      doc("TreatmentController.requirements", "Calcular requisitos del esquema", "Indica antropometría y variables necesarias antes de iniciar el protocolo."),
      doc("TreatmentController.detail", "Abrir detalle de tratamiento", "Integra protocolo, drogas, ciclos y turnos reales de PostgreSQL."),
      doc("TreatmentController.schemes", "Buscar esquemas", "Busca protocolos COIR y personalizados activos."),
      doc("TreatmentController.duration", "Consultar duración", "Devuelve la duración operativa estimada del esquema."),
      doc("TreatmentDocumentController.consent", "Abrir consentimiento", "Entrega el archivo de consentimiento guardado; un estado firmado sin archivo se informa como documento pendiente y este endpoint responde 404."),
      doc("TreatmentDocumentController.treatmentSheet", "Generar hoja de tratamiento", "Genera una hoja imprimible con paciente, esquema, drogas, turno y estados."),
      doc("TreatmentDocumentController.prescription", "Abrir prescripción", "Entrega el documento de prescripción guardado sin reconstruir uno inexistente."),
      doc("InfusionController.list", "Listar turnos", "Lista turnos por paciente y/o fecha con farmacia, administración y medicación."),
      doc("InfusionController.create", "Asignar turno a sillón", "Reserva el bloque completo; PostgreSQL rechaza superposiciones concurrentes."),
      doc("InfusionController.update", "Actualizar turno", "Mueve, cancela o avanza un turno usando control de versión."),
      doc("InfusionController.candidates", "Listar ciclos pendientes", "Ordena los ciclos no turnados por fecha planificada y continuidad."),
      doc("InfusionController.logistics", "Actualizar farmacia y prescripción", "Registra medicación recibida, en poder del paciente y estado de prescripción."),
      doc("InfusionController.finalizeInfusion", "Finalizar administración", "Completa la aplicación y agrega una evolución inmutable, de forma idempotente."),
      doc("QrWorkflowController.document", "Imprimir QR", "Genera un QR firmado para identificar paciente, tratamiento y ciclo sin texto clínico abierto."),
      doc("QrWorkflowController.scan", "Escanear QR", "Verifica firma, abre el turno correcto y documenta el escaneo en la historia."),
      doc("TreatmentWorkflowController.suspend", "Suspender tratamiento", "Suspende transitoria o definitivamente y documenta el motivo."),
      doc("TreatmentWorkflowController.resume", "Reanudar tratamiento", "Reanuda desde un ciclo válido y exige nueva prescripción cuando corresponde."),
      doc("TreatmentWorkflowController.create", "Crear solicitud clínica", "Solicita prescripción o continuidad a un usuario autorizado."),
      doc("TreatmentWorkflowController.inbox", "Consultar solicitudes", "Lista las solicitudes asignadas al usuario activo."),
      doc("TreatmentWorkflowController.seen", "Marcar solicitud leída", "Registra que el destinatario abrió la solicitud."),
      doc("TreatmentWorkflowController.resolve", "Resolver solicitud", "Confirma, rechaza, suspende o continúa y deja trazabilidad clínica."),
      doc("ConfigurationController.list", "Listar configuración", "Lista elementos activos o históricos de un tipo permitido."),
      doc("ConfigurationController.create", "Crear configuración", "Crea una definición versionada de guía, cálculo, formulario o parámetro."),
      doc("ConfigurationController.update", "Modificar configuración", "Actualiza con revisión optimista y conserva la versión anterior."),
      doc("ConfigurationController.archive", "Archivar configuración", "Desactiva el elemento sin borrar su historial."),
      doc("ConfigurationController.versions", "Listar versiones", "Devuelve el historial auditable del elemento."),
      doc("ConfigurationController.version", "Leer versión", "Recupera una revisión histórica exacta."),
      doc("ProtocolController.list", "Listar protocolos administrables", "Combina protocolos personalizados y catálogo COIR no vinculado."),
      doc("ProtocolController.get", "Abrir protocolo", "Devuelve componentes, duración, periodicidad y vínculos a drogas."),
      doc("ProtocolController.create", "Crear protocolo", "Crea un protocolo completo y actualiza inmediatamente el catálogo clínico."),
      doc("ProtocolController.update", "Modificar protocolo", "Edita componentes, preparación, tiempo y periodicidad con versionado."),
      doc("ProtocolController.archive", "Archivar protocolo", "Retira un protocolo de nuevas prescripciones sin romper tratamientos existentes."),
      doc("ProtocolController.coir", "Listar catálogo COIR", "Expone esquemas COIR, duración y periodicidad para vinculación."),
      doc("ProtocolController.drugs", "Buscar drogas de protocolo", "Busca drogas locales para relacionarlas con componentes del protocolo."),
      doc("ClinicalFileController.uploadStudy", "Subir estudio", "Guarda por streaming, valida formato y permite borrar durante la misma sesión."),
      doc("ClinicalFileController.study", "Abrir archivo de estudio", "Entrega el archivo autenticado con tipo y nombre seguros."),
      doc("ClinicalFileController.deleteStudy", "Eliminar carga reciente", "Elimina únicamente con el token temporal de la sesión que subió el archivo."),
      doc("ClinicalFileController.uploadImage", "Guardar imagen clínica", "Guarda una imagen o anotación rasterizada y valida su firma binaria."),
      doc("ClinicalFileController.image", "Abrir imagen clínica", "Entrega una imagen local autenticada y cacheable."),
      doc("StudyTemplateController.list", "Listar plantillas anatómicas", "Combina biblioteca incluida y plantillas personalizadas."),
      doc("StudyTemplateController.create", "Crear plantilla anatómica", "Guarda imagen, metadatos, licencia y confirmación de derechos."),
      doc("LlmController.config", "Leer configuración LLM", "Devuelve endpoint, modelo y parámetros sin revelar la API key."),
      doc("LlmController.updateConfig", "Guardar configuración LLM", "Valida y cifra la API key antes de persistirla."),
      doc("LlmController.status", "Consultar estado LLM", "Informa si la integración está habilitada y configurada."),
      doc("LlmController.test", "Probar conexión LLM", "Prueba un borrador de configuración sin guardarlo."),
      doc("LlmController.timeline", "Extraer línea de tiempo", "Solicita eventos estructurados y auditables a partir de texto clínico."),
      doc("LlmController.summarize", "Resumir eventos", "Resume hasta 250 eventos sin inventar información."),
      doc("LlmController.agent", "Consultar agente clínico", "Responde sobre el contexto entregado y diferencia hechos de inferencias."),
      doc("LlmController.fillSystemic", "Completar formulario sistémico", "Extrae únicamente campos configurados como asistidos por LLM."),
      doc("AdminController.users", "Listar usuarios", "Lista usuarios, roles y estado para administración."),
      doc("AdminController.createUser", "Crear usuario", "Crea una cuenta y asigna roles existentes."),
      doc("AdminController.updateUser", "Modificar usuario", "Actualiza perfil, estado, contraseña y roles."),
      doc("AdminController.roles", "Listar roles", "Lista roles y permisos disponibles."),
      doc("AdminController.createRole", "Crear rol", "Crea un rol personalizado con permisos explícitos."),
      doc("AdminController.updateRole", "Modificar rol", "Actualiza nombre, estado y permisos del rol."),
      doc("AdminController.security", "Leer seguridad", "Devuelve la política de acceso obligatorio y duración de sesión."),
      doc("AdminController.updateSecurity", "Modificar seguridad", "Mantiene login obligatorio y actualiza la duración de sesión."),
      doc("AdminController.clinicalUsers", "Buscar destinatarios clínicos", "Lista usuarios habilitados para una capacidad de flujo."),
      doc("StatusController.clinical", "Estado clínico", "Comprueba PostgreSQL y confirma que el sistema es local, unificado e independiente."),
      doc("StatusController.liraCompatibility", "Compatibilidad Lira", "Informa que las rutas históricas operan sobre HCOP JP local."),
      doc("StatusController.runtime", "Estado de ejecución", "Expone versión y motor para diagnóstico y automatización."),
      doc("StatusController.stop", "Instrucciones de detención", "Indica el mecanismo seguro de parada del contenedor."),
      doc("AjccCatalogController.list", "Listar sitios AJCC 8", "Devuelve los sitios tumorales y variables de estadificación disponibles en el catálogo local."),
      doc("AjccCatalogController.detail", "Abrir definición AJCC 8", "Devuelve TNM, factores específicos y reglas del sitio AJCC seleccionado."),
      doc("AjccCatalogController.stage", "Calcular estadio AJCC 8", "Calcula el grupo de estadio de forma determinística a partir del sitio y los valores TNM/factores."),
      doc("DiagnosisCatalogController.search", "Buscar SNOMED CT o CIE-10", "Filtra el catálogo diagnóstico local por sistema, texto y límite de resultados."),
      doc("GuideCatalogController.list", "Listar guías clínicas", "Lista las guías locales activas y, con permiso administrativo, también las archivadas."),
      doc("GuideCatalogController.file", "Abrir guía clínica", "Entrega el PDF local solicitado con nombre y tipo de contenido seguros."),
      doc("GuideCatalogController.upload", "Importar guía clínica", "Guarda un PDF institucional y actualiza el catálogo de guías."),
      doc("LegacyCatalogController.protocols", "Listar protocolos compatibles", "Mantiene el contrato histórico de la interfaz y responde desde los catálogos locales."),
      doc("LegacyCatalogController.protocolDetail", "Abrir protocolo compatible", "Devuelve el detalle local de un protocolo COIR o personalizado usando el contrato histórico."),
      doc("LegacyCatalogController.medicationSearch", "Buscar medicamentos", "Busca por genérico, marca o presentación en el catálogo local de drogas."),
      doc("LegacyCatalogController.status", "Consultar catálogos locales", "Informa disponibilidad y cantidad de protocolos y esquemas TNM locales."),
      doc("LegacyCatalogController.update", "Releer catálogos locales", "Confirma que los catálogos empaquetados ya están disponibles y versionados."),
      doc("PatientWorkspaceController.activate", "Activar paciente y abrir espacio clínico", "Asocia el paciente a la sesión y devuelve identidad, historia, tratamientos, turnos y conteos en una respuesta."),
      doc("PatientWorkspaceController.workspace", "Abrir espacio clínico del paciente", "Devuelve el agregado de trabajo del paciente sin cambiar otra sesión."),
      doc("SeerTnmCatalogController.list", "Listar esquemas TNM", "Devuelve el catálogo SEER/TNM local disponible para las herramientas de estadificación."),
      doc("SeerTnmCatalogController.detail", "Abrir esquema TNM", "Devuelve campos, opciones y reglas del esquema TNM seleccionado."),
      doc("SystemicFormController.forms", "Listar formularios sistémicos", "Devuelve los formularios institucionales y sus campos para prescripción y documentación.")
  );

  private static final Map<String, String> PERMISSIONS = Map.ofEntries(
      permission("AdminController.users", "admin.manage-users"),
      permission("AdminController.createUser", "admin.manage-users"),
      permission("AdminController.updateUser", "admin.manage-users"),
      permission("AdminController.roles", "admin.manage-roles"),
      permission("AdminController.createRole", "admin.manage-roles"),
      permission("AdminController.updateRole", "admin.manage-roles"),
      permission("AdminController.security", "admin.manage-security"),
      permission("AdminController.updateSecurity", "admin.manage-security"),
      permission("ClinicalDocumentController.put", "section.history.edit"),
      permission("ConfigurationController.list", "section.configuration.view"),
      permission("ConfigurationController.create", "section.configuration.manage"),
      permission("ConfigurationController.update", "section.configuration.manage"),
      permission("ConfigurationController.archive", "section.configuration.manage"),
      permission("ConfigurationController.versions", "section.configuration.view"),
      permission("ConfigurationController.version", "section.configuration.view"),
      permission("DiagnosisController.list", "section.history.view"),
      permission("DiagnosisController.link", "section.history.edit"),
      permission("GuideCatalogController.list", "section.tools.view"),
      permission("GuideCatalogController.file", "section.tools.view"),
      permission("GuideCatalogController.upload", "section.configuration.manage"),
      permission("InfusionController.list", "section.day-hospital.view"),
      permission("InfusionController.create", "section.day-hospital.edit"),
      permission("InfusionController.update", "section.day-hospital.edit"),
      permission("InfusionController.candidates", "section.day-hospital.view"),
      permission("InfusionController.logistics", "section.day-hospital.edit"),
      permission("InfusionController.finalizeInfusion", "section.day-hospital.edit"),
      permission("LlmController.config", "section.configuration.view"),
      permission("LlmController.updateConfig", "section.configuration.manage"),
      permission("LlmController.status", "section.agent.view"),
      permission("LlmController.test", "section.configuration.manage"),
      permission("LlmController.timeline", "section.timeline.view"),
      permission("LlmController.summarize", "section.timeline.view"),
      permission("LlmController.agent", "section.agent.view"),
      permission("LlmController.fillSystemic", "section.prescriptions.edit"),
      permission("ClinicalFileController.uploadStudy", "section.studies.edit"),
      permission("ClinicalFileController.study", "section.studies.view"),
      permission("ClinicalFileController.deleteStudy", "section.studies.edit"),
      permission("ClinicalFileController.uploadImage", "section.studies.edit"),
      permission("ClinicalFileController.image", "section.studies.view"),
      permission("PatientController.search", "section.history.view"),
      permission("PatientController.create", "section.history.edit"),
      permission("PatientController.preview", "section.history.view"),
      permission("PatientController.importPatient", "section.history.view"),
      permission("PatientWorkspaceController.activate", "section.history.view"),
      permission("PatientWorkspaceController.workspace", "section.history.view"),
      permission("ProtocolController.list", "section.protocols.view"),
      permission("ProtocolController.get", "section.protocols.view"),
      permission("ProtocolController.create", "section.protocols.edit"),
      permission("ProtocolController.update", "section.protocols.edit"),
      permission("ProtocolController.archive", "section.protocols.edit"),
      permission("ProtocolController.coir", "section.protocols.view"),
      permission("ProtocolController.drugs", "section.protocols.view"),
      permission("QrWorkflowController.document", "section.day-hospital.view"),
      permission("QrWorkflowController.scan", "section.day-hospital.edit"),
      permission("StudyTemplateController.list", "section.studies.view"),
      permission("StudyTemplateController.create", "section.configuration.manage"),
      permission("SystemicFormController.forms", "section.prescriptions.view"),
      permission("TreatmentController.list", "section.prescriptions.view"),
      permission("TreatmentController.create", "section.prescriptions.edit"),
      permission("TreatmentController.options", "section.prescriptions.view"),
      permission("TreatmentController.requirements", "section.prescriptions.view"),
      permission("TreatmentController.detail", "section.prescriptions.view"),
      permission("TreatmentController.schemes", "section.protocols.view"),
      permission("TreatmentController.duration", "section.protocols.view"),
      permission("TreatmentDocumentController.consent", "section.prescriptions.view"),
      permission("TreatmentDocumentController.treatmentSheet", "section.prescriptions.view"),
      permission("TreatmentDocumentController.prescription", "section.prescriptions.view"),
      permission("TreatmentWorkflowController.suspend", "workflow.suspend"),
      permission("TreatmentWorkflowController.resume", "workflow.resume"),
      permission("TreatmentWorkflowController.create", "workflow.request-prescription | workflow.request-continuity"),
      permission("TreatmentWorkflowController.resolve", "workflow.resolve-prescription | workflow.resolve-continuity"),
      permission("StatusController.stop", "admin.manage-security")
  );

  private static final Map<String, String> PARAMETER_DESCRIPTIONS = Map.ofEntries(
      Map.entry("patientId", "Identificador local inmutable del paciente."),
      Map.entry("treatmentId", "Identificador local o importado del tratamiento."),
      Map.entry("cycleNumber", "Número de ciclo, comenzando en 1."),
      Map.entry("cycle", "Número de ciclo, comenzando en 1."),
      Map.entry("id", "Identificador del recurso solicitado."),
      Map.entry("revision", "Revisión histórica exacta del recurso."),
      Map.entry("kind", "Tipo de configuración permitido por el servicio."),
      Map.entry("name", "Nombre seguro del archivo o recurso."),
      Map.entry("q", "Texto de búsqueda; admite coincidencia parcial."),
      Map.entry("query", "Texto de búsqueda; admite coincidencia parcial."),
      Map.entry("limit", "Cantidad máxima de resultados devueltos."),
      Map.entry("date", "Fecha operativa en formato ISO 8601 (AAAA-MM-DD)."),
      Map.entry("schemeId", "Identificador estable del protocolo o esquema terapéutico."),
      Map.entry("studyId", "Identificador del estudio dentro de la historia clínica del paciente."),
      Map.entry("scope", "Origen de plantillas: all, bundled o custom."),
      Map.entry("includeInactive", "Use 1 para incluir elementos archivados; 0 devuelve sólo activos."),
      Map.entry("includeArchived", "Incluye protocolos archivados cuando vale true."),
      Map.entry("includeCatalog", "Incluye esquemas COIR todavía no vinculados cuando vale true."),
      Map.entry("capability", "Permiso o capacidad clínica que debe poseer el usuario destinatario."),
      Map.entry("system", "Sistema terminológico: snomed o cie10."),
      Map.entry("source", "Origen del catálogo compatible; por defecto coir."),
      Map.entry("X-Study-Delete-Token", "Token temporal emitido al subir el archivo; sólo permite eliminarlo durante esa sesión."),
      Map.entry("title", "Título visible de la plantilla anatómica."),
      Map.entry("category", "Categoría anatómica usada para ordenar y filtrar la plantilla."),
      Map.entry("author", "Autor o institución responsable de la imagen."),
      Map.entry("license", "Licencia o condición de uso declarada."),
      Map.entry("description", "Descripción clínica y visual de la plantilla."),
      Map.entry("sourceUrl", "URL de procedencia declarada; no se descarga automáticamente."),
      Map.entry("licenseUrl", "URL donde puede verificarse la licencia."),
      Map.entry("rightsConfirmed", "Debe valer 1 para confirmar que se poseen derechos de uso.")
  );

  private static final Set<String> CREATED_OPERATIONS = Set.of(
      "AdminController.createRole",
      "AdminController.createUser",
      "ClinicalFileController.uploadImage",
      "ClinicalFileController.uploadStudy",
      "ConfigurationController.create",
      "InfusionController.create",
      "PatientController.create",
      "ProtocolController.create",
      "StudyTemplateController.create",
      "TreatmentController.create",
      "TreatmentWorkflowController.create"
  );

  private static final Map<String, BinaryRequest> BINARY_REQUESTS = Map.of(
      "ClinicalFileController.uploadStudy",
      new BinaryRequest(
          List.of("application/octet-stream"),
          "Contenido binario original del estudio. El tipo real se envía en Content-Type y debe coincidir con la firma del archivo."),
      "GuideCatalogController.upload",
      new BinaryRequest(
          List.of("application/pdf"),
          "Archivo PDF institucional completo, con un máximo de 50 MB."),
      "StudyTemplateController.create",
      new BinaryRequest(
          List.of(
              "image/png",
              "image/jpeg",
              "image/gif",
              "image/webp",
              "image/bmp",
              "image/tiff"),
          "Imagen anatómica completa. Los metadatos y la confirmación de derechos se envían como parámetros de consulta.")
  );

  @Bean
  OpenApiCustomizer reusableSchemas() {
    return openApi -> {
      if (openApi.getComponents() == null) openApi.setComponents(new Components());
      openApi.getComponents().addSchemas("ApiError", apiErrorSchema());
      if (openApi.getPaths() == null || openApi.getTags() == null) return;
      Set<String> usedTags = new HashSet<>();
      openApi.getPaths().values().forEach(path ->
          path.readOperations().forEach(operation -> usedTags.addAll(operation.getTags())));
      openApi.setTags(openApi.getTags().stream()
          .filter(tag -> usedTags.contains(tag.getName()))
          .toList());
    };
  }

  @Bean
  GroupedOpenApi completeApi(
      OperationCustomizer documentedOperations,
      OpenApiCustomizer reusableSchemas) {
    return GroupedOpenApi.builder()
        .group("hcop-jp-completa")
        .displayName("HCOP JP · API completa")
        .pathsToMatch("/api/**")
        .addOperationCustomizer(documentedOperations)
        .addOpenApiCustomizer(reusableSchemas)
        .build();
  }

  @Bean
  GroupedOpenApi clinicalApi(
      OperationCustomizer documentedOperations,
      OpenApiCustomizer reusableSchemas) {
    return GroupedOpenApi.builder()
        .group("clinica")
        .displayName("Clínica y Hospital de Día")
        .pathsToMatch(
            "/api/clinical/**",
            "/api/hc/**",
            "/api/media/**",
            "/api/diagnosis-catalogs/**",
            "/api/ajcc8/**",
            "/api/tnm/**",
            "/api/protocols/**",
            "/api/medications/**",
            "/api/systemic-forms/**",
            "/api/guides/**",
            "/api/llm/**",
            "/api/agent/**")
        .addOperationCustomizer(documentedOperations)
        .addOpenApiCustomizer(reusableSchemas)
        .build();
  }

  @Bean
  GroupedOpenApi administrationApi(
      OperationCustomizer documentedOperations,
      OpenApiCustomizer reusableSchemas) {
    return GroupedOpenApi.builder()
        .group("administracion")
        .displayName("Administración y configuración")
        .pathsToMatch(
            "/api/admin/**",
            "/api/config",
            "/api/llm/test",
            "/api/clinical/configuration/**",
            "/api/clinical/protocols/**",
            "/api/clinical/coir-catalog",
            "/api/clinical/drugs",
            "/api/guides/**",
            "/api/study-templates/**")
        .addOperationCustomizer(documentedOperations)
        .addOpenApiCustomizer(reusableSchemas)
        .build();
  }

  @Bean
  OperationCustomizer documentedOperations() {
    return (operation, handlerMethod) -> {
      Method method = handlerMethod.getMethod();
      String controller = handlerMethod.getBeanType().getSimpleName();
      Documentation documentation = DOCUMENTATION.get(controller + "." + method.getName());
      if (documentation != null) {
        operation.setSummary(documentation.summary());
        operation.setDescription(documentation.description());
      } else if (operation.getSummary() == null || operation.getSummary().isBlank()) {
        operation.setSummary(method.getName());
        operation.setDescription("Operación MVC del módulo " + controller.replace("Controller", "") + ".");
      }
      operation.setTags(List.of(tag(controller)));
      operation.addExtension("x-hcop-controller", controller);
      String key = controller + "." + method.getName();
      boolean secured = requiresSession(controller, method.getName());
      if (secured) {
        operation.addSecurityItem(new SecurityRequirement().addList("sessionCookie"));
        operation.addExtension("x-hcop-authentication", "cookie HttpOnly y permiso por rol");
        operation.addExtension("x-hcop-permission", PERMISSIONS.getOrDefault(key, "authenticated"));
      } else {
        operation.addExtension("x-hcop-authentication", "public");
        operation.addExtension("x-hcop-permission", "public");
      }
      describeBinaryRequest(operation, key);
      describeParameters(operation);
      describeResponses(operation, key, controller, secured, isWrite(method));
      return operation;
    };
  }

  private static void describeBinaryRequest(
      io.swagger.v3.oas.models.Operation operation,
      String key) {
    BinaryRequest binary = BINARY_REQUESTS.get(key);
    if (binary == null) return;
    Content content = new Content();
    binary.contentTypes().forEach(contentType ->
        content.addMediaType(
            contentType,
            new io.swagger.v3.oas.models.media.MediaType()
                .schema(new StringSchema().format("binary"))));
    operation.setRequestBody(new RequestBody()
        .required(true)
        .description(binary.description())
        .content(content));
  }

  private static void describeParameters(io.swagger.v3.oas.models.Operation operation) {
    if (operation.getParameters() != null) {
      operation.getParameters().forEach(parameter -> {
        if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
          String description = PARAMETER_DESCRIPTIONS.get(parameter.getName());
          if (description != null) parameter.setDescription(description);
        }
      });
    }
    if (operation.getRequestBody() != null &&
        (operation.getRequestBody().getDescription() == null ||
            operation.getRequestBody().getDescription().isBlank())) {
      operation.getRequestBody().setDescription(
          "Datos validados por el controlador y las reglas clínicas del servicio.");
    }
  }

  private static void describeResponses(
      io.swagger.v3.oas.models.Operation operation,
      String key,
      String controller,
      boolean secured,
      boolean write) {
    var responses = operation.getResponses();
    if (responses == null) return;
    describeSuccessResponse(responses, key);
    if (secured) {
      ensureErrorResponse(responses, "401", "Sesión ausente, vencida o revocada.");
      ensureErrorResponse(responses, "403", "El usuario no posee el permiso requerido.");
    } else if ("AuthController.login".equals(key)) {
      ensureErrorResponse(responses, "401", "Usuario o contraseña incorrectos.");
    }
    ensureErrorResponse(responses, "400", "Parámetros o cuerpo de solicitud inválidos.");
    if (secured) {
      ensureErrorResponse(
          responses,
          "404",
          "Paciente, tratamiento, archivo o recurso inexistente.");
    }
    if (write && !"AuthController".equals(controller)) {
      ensureErrorResponse(
          responses,
          "409",
          "Conflicto de revisión, estado, superposición o integridad.");
    }
    if (BINARY_REQUESTS.containsKey(key) || "ClinicalFileController.uploadImage".equals(key)) {
      ensureErrorResponse(responses, "413", "El archivo supera el límite permitido.");
      ensureErrorResponse(responses, "415", "El tipo declarado o la firma binaria no están permitidos.");
    }
    if ("ClinicalFileController.uploadImage".equals(key)
        || "DiagnosisController.link".equals(key)
        || "TreatmentController.create".equals(key)) {
      ensureErrorResponse(responses, "422", "Los datos son válidos sintácticamente pero no cumplen una regla clínica.");
    }
    if ("LlmController".equals(controller)
        && !Set.of("config", "updateConfig", "status").contains(key.substring(key.indexOf('.') + 1))) {
      ensureErrorResponse(responses, "502", "El servicio LLM respondió con un resultado inválido.");
      ensureErrorResponse(responses, "503", "El servicio LLM está desactivado o no está configurado.");
      ensureErrorResponse(responses, "504", "El servicio LLM excedió el tiempo de espera.");
    }
    ensureErrorResponse(
        responses,
        "500",
        "Error interno sin exposición de detalles sensibles.");
  }

  private static void describeSuccessResponse(
      io.swagger.v3.oas.models.responses.ApiResponses responses,
      String key) {
    if (CREATED_OPERATIONS.contains(key)) {
      ApiResponse created = responses.remove("200");
      if (created == null) created = new ApiResponse();
      created.setDescription("Recurso creado correctamente.");
      responses.put("201", created);
      return;
    }
    ApiResponse success = responses.get("200");
    if (success != null &&
        (success.getDescription() == null || "OK".equalsIgnoreCase(success.getDescription()))) {
      success.setDescription("Solicitud procesada correctamente.");
    }
  }

  private static void ensureErrorResponse(
      io.swagger.v3.oas.models.responses.ApiResponses responses,
      String status,
      String description) {
    ApiResponse response = responses.get(status);
    if (response == null) {
      response = new ApiResponse();
      responses.put(status, response);
    }
    response.setDescription(description);
    Content content = response.getContent();
    if (content == null) content = new Content();
    if (!content.containsKey("application/json")) {
      content.addMediaType(
          "application/json",
          new io.swagger.v3.oas.models.media.MediaType()
              .schema(new Schema<>().$ref("#/components/schemas/ApiError")));
    }
    response.setContent(content);
  }

  private static Schema<?> apiErrorSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.setDescription("Respuesta uniforme de error de HCOP JP.");
    schema.addProperty("ok", new BooleanSchema()
        .description("Siempre false en una respuesta de error.")
        ._default(false));
    schema.addProperty("error", new StringSchema()
        .description("Mensaje seguro y apto para mostrar al usuario."));
    schema.addProperty("code", new StringSchema()
        .description("Código estable opcional para integraciones y automatización."));
    schema.addProperty("status", new IntegerSchema()
        .format("int32")
        .description("Código de estado HTTP."));
    schema.setRequired(List.of("ok", "error", "status"));
    return schema;
  }

  private static boolean isWrite(Method method) {
    return method.isAnnotationPresent(PostMapping.class)
        || method.isAnnotationPresent(PutMapping.class)
        || method.isAnnotationPresent(PatchMapping.class)
        || method.isAnnotationPresent(DeleteMapping.class);
  }

  private static boolean requiresSession(String controller, String method) {
    if ("StatusController".equals(controller) && !"stop".equals(method)) return false;
    return !("AuthController".equals(controller) && ("login".equals(method) || "me".equals(method)));
  }

  private static String tag(String controller) {
    if (controller.startsWith("Auth")) return "Autenticación";
    if (controller.startsWith("Patient") || controller.startsWith("ClinicalDocument") ||
        controller.startsWith("Diagnosis")) return "Pacientes e historia";
    if (controller.startsWith("TreatmentDocument") || controller.startsWith("TreatmentController")) return "Tratamientos";
    if (controller.startsWith("Infusion") || controller.startsWith("Qr")) return "Hospital de Día";
    if (controller.startsWith("TreatmentWorkflow")) return "Flujos clínicos";
    if (controller.startsWith("Configuration") || controller.startsWith("Protocol")) return "Configuración";
    if (controller.startsWith("ClinicalFile") || controller.startsWith("StudyTemplate")) return "Archivos clínicos";
    if (controller.startsWith("Admin")) return "Administración";
    if (controller.startsWith("Llm")) return "Integraciones";
    if (controller.startsWith("Status")) return "Estado";
    return "Catálogos";
  }

  private static Map.Entry<String, Documentation> doc(String key, String summary, String description) {
    return Map.entry(key, new Documentation(summary, description));
  }

  private static Map.Entry<String, String> permission(String key, String value) {
    return Map.entry(key, value);
  }

  private record Documentation(String summary, String description) {
  }

  private record BinaryRequest(List<String> contentTypes, String description) {
  }
}
