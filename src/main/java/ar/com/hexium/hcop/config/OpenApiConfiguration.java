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
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
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
      doc("PatientController.importPatient", "Abrir paciente local", "Activa una historia ya consolidada en PostgreSQL; no consulta Lira y proyecta las secciones según permisos."),
      doc("ClinicalDocumentController.get", "Leer historia clínica", "Recupera la hoja del paciente activo o la plantilla en blanco; requiere acceso a Historia, omite prescriptions sin permiso de lectura de Prescripción y omite studies/externalStudies sin permiso de lectura de Estudios."),
      doc("ClinicalDocumentController.put", "Guardar historia clínica", "Guarda con control optimista de revisión y devuelve el estado canónico confirmado. Si prescriptions cambia exige edición de Prescripción; si studies o externalStudies cambian exige edición de Estudios; si esos campos fueron ocultados, conserva sus valores existentes. Los cambios de narrative.chiefComplaint, narrative.currentIllness, narrative.backgroundClinical, narrative.currentMedication, narrative.familyOncology, narrative.gynecology, narrative.physicalExam, narrative.summary y narrative.plan deben ser texto de hasta 50.000 caracteres; exam.weightKg acepta 0.01 a 500 kg y exam.heightM conserva 0.3 a 2.5 metros aunque la UI muestre centímetros; valores legacy atípicos que no cambian se preservan. Java genera actor, fecha, versión y auditoría de Motivo de consulta, Antecedentes de enfermedad actual, Antecedentes personales, Examen físico y Conclusión / resumen usando la sesión y no confía en esos metadatos enviados por el cliente. La validación 400 de Motivo de consulta usa CLINICAL_CHIEF_COMPLAINT_INVALID, CLINICAL_CHIEF_COMPLAINT_TOO_LONG, CLINICAL_CHIEF_COMPLAINT_EMPTY, CLINICAL_CHIEF_COMPLAINT_REASON_REQUIRED, CLINICAL_CHIEF_COMPLAINT_REASON_INVALID o CLINICAL_CHIEF_COMPLAINT_REASON_TOO_LONG. Antecedentes de enfermedad actual usa CLINICAL_CURRENT_ILLNESS_INVALID, CLINICAL_CURRENT_ILLNESS_TOO_LONG, CLINICAL_CURRENT_ILLNESS_EMPTY, CLINICAL_CURRENT_ILLNESS_REASON_REQUIRED, CLINICAL_CURRENT_ILLNESS_REASON_INVALID o CLINICAL_CURRENT_ILLNESS_REASON_TOO_LONG. Antecedentes personales usa CLINICAL_PERSONAL_HISTORY_BACKGROUND_CLINICAL_INVALID, CLINICAL_PERSONAL_HISTORY_BACKGROUND_CLINICAL_TOO_LONG, CLINICAL_PERSONAL_HISTORY_CURRENT_MEDICATION_INVALID, CLINICAL_PERSONAL_HISTORY_CURRENT_MEDICATION_TOO_LONG, CLINICAL_PERSONAL_HISTORY_FAMILY_ONCOLOGY_INVALID, CLINICAL_PERSONAL_HISTORY_FAMILY_ONCOLOGY_TOO_LONG, CLINICAL_PERSONAL_HISTORY_GYNECOLOGY_INVALID, CLINICAL_PERSONAL_HISTORY_GYNECOLOGY_TOO_LONG, CLINICAL_PERSONAL_HISTORY_EMPTY, CLINICAL_PERSONAL_HISTORY_REASON_REQUIRED, CLINICAL_PERSONAL_HISTORY_REASON_INVALID o CLINICAL_PERSONAL_HISTORY_REASON_TOO_LONG. Examen físico usa CLINICAL_PHYSICAL_EXAM_WEIGHT_INVALID, CLINICAL_PHYSICAL_EXAM_WEIGHT_OUT_OF_RANGE, CLINICAL_PHYSICAL_EXAM_HEIGHT_INVALID, CLINICAL_PHYSICAL_EXAM_HEIGHT_OUT_OF_RANGE, CLINICAL_PHYSICAL_EXAM_TEXT_INVALID, CLINICAL_PHYSICAL_EXAM_TEXT_TOO_LONG, CLINICAL_PHYSICAL_EXAM_EMPTY, CLINICAL_PHYSICAL_EXAM_REASON_REQUIRED, CLINICAL_PHYSICAL_EXAM_REASON_INVALID o CLINICAL_PHYSICAL_EXAM_REASON_TOO_LONG. La validación de Conclusión / resumen usa CLINICAL_SUMMARY_INVALID, CLINICAL_SUMMARY_TOO_LONG, CLINICAL_PLAN_INVALID, CLINICAL_PLAN_TOO_LONG, CLINICAL_SUMMARY_PLAN_EMPTY, CLINICAL_SUMMARY_PLAN_REASON_REQUIRED, CLINICAL_SUMMARY_PLAN_REASON_INVALID o CLINICAL_SUMMARY_PLAN_REASON_TOO_LONG. Los conflictos 409 usan ACTIVE_PATIENT_REQUIRED, CLINICAL_REVISION_REQUIRED, CLINICAL_PATIENT_MISMATCH o VERSION_CONFLICT."),
      doc("ClinicalDocumentController.restoreDemo", "Compatibilidad de persistencia", "Confirma que la historia es persistente y que no se restaura un demo."),
      doc("DiagnosisController.list", "Listar diagnósticos", "Lista todos los diagnósticos oncológicos no archivados del paciente."),
      doc("DiagnosisController.link", "Validar diagnóstico de tratamiento", "Confirma que el diagnóstico seleccionado pertenece a la historia del paciente."),
      doc("TreatmentController.list", "Listar tratamientos", "Devuelve tratamientos oncológicos locales y su estado actual."),
      doc("TreatmentController.create", "Prescribir tratamiento", "Crea tratamiento, ciclos y una logística independiente para cada día con medicación; también agrega una evolución clínica inmutable. Una discordancia diagnóstica evidente exige confirmación y motivo clínico."),
      doc("TreatmentController.options", "Opciones de prescripción", "Devuelve diagnósticos y esquemas con su grupo clínico, tipos, intención y estados de consentimiento."),
      doc("TreatmentController.requirements", "Calcular requisitos del esquema", "Indica antropometría y variables necesarias antes de iniciar el protocolo."),
      doc("TreatmentController.detail", "Abrir detalle de tratamiento", "Integra protocolo, drogas, ciclos y turnos reales de PostgreSQL."),
      doc("TreatmentController.schemes", "Buscar esquemas", "Busca protocolos COIR y personalizados activos."),
      doc("TreatmentController.duration", "Consultar duración", "Devuelve la duración operativa estimada del esquema."),
      doc("TreatmentDocumentController.consent", "Abrir consentimiento", "Entrega el archivo de consentimiento guardado; un estado firmado sin archivo se informa como documento pendiente y este endpoint responde 404."),
      doc("TreatmentDocumentController.treatmentSheet", "Generar hoja de tratamiento", "Genera una hoja imprimible con paciente, esquema, drogas, turno y estados."),
      doc("TreatmentDocumentController.prescription", "Abrir prescripción", "Entrega el documento de prescripción guardado sin reconstruir uno inexistente."),
      doc("InfusionController.list", "Listar turnos", "Lista turnos por paciente y/o fecha con farmacia, administración y medicación."),
      doc("InfusionController.create", "Asignar aplicación a sillón", "Reserva el bloque de un ciclo y día de aplicación concretos; PostgreSQL rechaza duplicados y superposiciones concurrentes."),
      doc("InfusionController.update", "Actualizar turno", "Mueve, cancela o avanza un turno usando control de versión."),
      doc("InfusionController.candidates", "Listar aplicaciones pendientes", "Ordena por fecha cada ciclo y día con medicación que todavía no tiene turno."),
      doc("InfusionController.logistics", "Actualizar farmacia por aplicación", "Registra para un ciclo y día concretos la medicación recibida, en poder del paciente y el estado de prescripción."),
      doc("QrWorkflowController.document", "Imprimir QR", "Genera un QR firmado para identificar paciente, tratamiento, ciclo y día de aplicación sin texto clínico abierto."),
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
      doc("CalculatorCatalogController.list", "Listar calculadoras operativas", "Devuelve únicamente las calculadoras activas y los ajustes institucionales necesarios para ejecutarlas desde Herramientas, sin exponer administración ni historial."),
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
      doc("LlmController.status", "Consultar estado LLM", "Requiere section.agent.view e informa proveedor, modelo y si la integración está habilitada y posee endpoint configurado. No prueba conectividad ni expone secretos."),
      doc("LlmController.test", "Probar conexión LLM", "Prueba un borrador de configuración sin guardarlo."),
      doc("LlmController.timeline", "Extraer línea de tiempo", "Solicita eventos estructurados y auditables a partir de texto clínico."),
      doc("LlmController.summarize", "Resumir eventos", "Resume hasta 250 eventos sin inventar información."),
      doc("LlmController.agent", "Consultar agente clínico", "Requiere section.agent.view. Acepta una consulta de hasta 8000 caracteres y envía sólo los últimos 12 mensajes no vacíos del historial, con hasta 8000 caracteres cada uno. Solicita JSON estructurado común a proveedores OpenAI compatibles y Ollama, valida tablas, gráficos, seguimientos y resaltados, y conserva como respuesta textual cualquier salida tradicional o JSON incompleto. Si el último mensaje user repite la consulta actual, se elimina del historial antes de llamar al LLM. timelineEvents y consultAgents se aceptan por compatibilidad pero no se incorporan al prompt en esta versión."),
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
      doc("LegacyCatalogController.medicationSearch", "Buscar medicamentos", "Busca por genérico, marca o presentación en el catálogo local de drogas. Requiere permiso de lectura de Prescripción."),
      doc("LegacyCatalogController.status", "Consultar catálogos locales", "Informa disponibilidad y cantidad de protocolos y esquemas TNM locales."),
      doc("LegacyCatalogController.update", "Releer catálogos locales", "Confirma que los catálogos empaquetados ya están disponibles y versionados."),
      doc("PatientWorkspaceController.activate", "Activar paciente y abrir espacio clínico", "Asocia el paciente a la sesión y devuelve identidad, historia, tratamientos, turnos y conteos, omitiendo secciones sin permiso."),
      doc("PatientWorkspaceController.workspace", "Abrir espacio clínico del paciente", "Devuelve el agregado de trabajo del paciente sin cambiar otra sesión y aplica la misma proyección por permisos."),
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
      permission("ClinicalDocumentController.get", "section.history.view"),
      permission("ClinicalDocumentController.put", "section.history.edit + permiso específico de edición si prescriptions, studies o externalStudies cambian"),
      permission("ConfigurationController.list", "section.configuration.view"),
      permission("ConfigurationController.create", "section.configuration.manage"),
      permission("ConfigurationController.update", "section.configuration.manage"),
      permission("ConfigurationController.archive", "section.configuration.manage"),
      permission("ConfigurationController.versions", "section.configuration.view"),
      permission("ConfigurationController.version", "section.configuration.view"),
      permission("ResearchFormCatalogController.list", "section.research.view"),
      permission("CalculatorCatalogController.list", "section.tools.use"),
      permission("DiagnosisController.list", "section.history.view"),
      permission("DiagnosisController.link", "section.history.edit"),
      permission("AjccCatalogController.list", "section.tools.view"),
      permission("AjccCatalogController.detail", "section.tools.view"),
      permission("AjccCatalogController.stage", "section.tools.use"),
      permission("GuideCatalogController.list", "section.tools.view"),
      permission("GuideCatalogController.file", "section.tools.view"),
      permission("GuideCatalogController.upload", "section.configuration.manage"),
      permission("InfusionController.list", "section.day-hospital.view"),
      permission("InfusionController.create", "application.schedule.manage"),
      permission("InfusionController.update", "application.schedule.manage"),
      permission("InfusionController.candidates", "section.day-hospital.view"),
      permission("InfusionController.logistics", "application.pharmacy.manage"),
      permission("InfusionApplicationWorkflowController.list", "section.day-hospital.view"),
      permission("InfusionApplicationWorkflowController.get", "section.day-hospital.view"),
      permission("InfusionApplicationWorkflowController.pharmacyValidation", "application.pharmacy.manage"),
      permission("InfusionApplicationWorkflowController.stockReservation", "application.pharmacy.manage"),
      permission("InfusionApplicationWorkflowController.clinicalAuthorization", "application.triage.manage"),
      permission("InfusionApplicationWorkflowController.preparationStart", "application.preparation.manage"),
      permission("InfusionApplicationWorkflowController.preparationComplete", "application.preparation.manage"),
      permission("InfusionApplicationWorkflowController.preparationRelease", "application.preparation.manage"),
      permission("InfusionApplicationWorkflowController.preparationRestart", "application.preparation.manage"),
      permission("InfusionApplicationWorkflowController.preparationLabel", "application.preparation.manage"),
      permission("InfusionApplicationWorkflowController.administrationStart", "application.administration.manage"),
      permission("InfusionApplicationWorkflowController.administrationInterrupt", "application.administration.manage"),
      permission("InfusionApplicationWorkflowController.administrationResolve", "application.administration.manage"),
      permission("InfusionApplicationWorkflowController.administrationComplete", "application.administration.manage"),
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
      permission("LegacyCatalogController.medicationSearch", "section.prescriptions.view"),
      permission("LegacyCatalogController.protocols", "section.protocols.view"),
      permission("LegacyCatalogController.protocolDetail", "section.protocols.view"),
      permission("ProtocolController.list", "section.protocols.view"),
      permission("ProtocolController.get", "section.protocols.view"),
      permission("ProtocolController.create", "section.protocols.edit"),
      permission("ProtocolController.update", "section.protocols.edit"),
      permission("ProtocolController.archive", "section.protocols.edit"),
      permission("ProtocolController.coir", "section.protocols.view"),
      permission("ProtocolController.drugs", "section.protocols.view"),
      permission("QrWorkflowController.document", "section.day-hospital.view"),
      permission("QrWorkflowController.scan", "application.administration.manage"),
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
      Map.entry("applicationDay", "Día del ciclo en el que se administra esta aplicación."),
      Map.entry("includeScheduled", "Incluye aplicaciones que ya poseen un turno activo; se usa en Farmacia."),
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
      Map.entry("tags", "Etiquetas separadas por comas para facilitar la búsqueda y clasificación."),
      Map.entry("author", "Autor o institución responsable de la imagen."),
      Map.entry("attribution", "Texto de atribución requerido por el autor o la licencia."),
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
      openApi.getComponents().addSchemas(
          "AuthenticationRequired",
          authenticationRequiredSchema());
      openApi.getComponents().addSchemas("AgentHistoryMessage", agentHistoryMessageSchema());
      openApi.getComponents().addSchemas("AgentChatRequest", agentChatRequestSchema());
      openApi.getComponents().addSchemas("AgentTableArtifact", agentTableArtifactSchema());
      openApi.getComponents().addSchemas("AgentChartPoint", agentChartPointSchema());
      openApi.getComponents().addSchemas("AgentChartSeries", agentChartSeriesSchema());
      openApi.getComponents().addSchemas("AgentChartArtifact", agentChartArtifactSchema());
      openApi.getComponents().addSchemas("AgentArtifact", agentArtifactSchema());
      openApi.getComponents().addSchemas("AgentHighlight", agentHighlightSchema());
      openApi.getComponents().addSchemas("AgentChatResponse", agentChatResponseSchema());
      openApi.getComponents().addSchemas("LlmStatusResponse", llmStatusResponseSchema());
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
      describeStructuredContract(operation, key);
      return operation;
    };
  }

  private static void describeStructuredContract(
      io.swagger.v3.oas.models.Operation operation,
      String key) {
    if ("LlmController.agent".equals(key)) {
      operation.setRequestBody(new RequestBody()
          .required(true)
          .description(
              "Consulta clínica y conversación previa acotada. timelineEvents y consultAgents "
                  + "se aceptan sólo por compatibilidad y no se envían al LLM.")
          .content(jsonContent("#/components/schemas/AgentChatRequest")));
      setSuccessSchema(operation, "#/components/schemas/AgentChatResponse");
    } else if ("LlmController.status".equals(key)) {
      setSuccessSchema(operation, "#/components/schemas/LlmStatusResponse");
    }
  }

  private static void setSuccessSchema(
      io.swagger.v3.oas.models.Operation operation,
      String schemaReference) {
    ApiResponses responses = operation.getResponses();
    if (responses == null) {
      responses = new ApiResponses();
      operation.setResponses(responses);
    }
    ApiResponse success = responses.get("200");
    if (success == null) {
      success = new ApiResponse().description("Solicitud procesada correctamente.");
      responses.put("200", success);
    }
    success.setContent(jsonContent(schemaReference));
  }

  private static Content jsonContent(String schemaReference) {
    return new Content().addMediaType(
        "application/json",
        new io.swagger.v3.oas.models.media.MediaType()
            .schema(new Schema<>().$ref(schemaReference)));
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
      ensureErrorResponse(
          responses,
          "401",
          "Sesión ausente, vencida o revocada.",
          "#/components/schemas/AuthenticationRequired");
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
    ensureErrorResponse(responses, status, description, "#/components/schemas/ApiError");
  }

  private static void ensureErrorResponse(
      io.swagger.v3.oas.models.responses.ApiResponses responses,
      String status,
      String description,
      String schemaReference) {
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
              .schema(new Schema<>().$ref(schemaReference)));
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

  private static Schema<?> authenticationRequiredSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.setDescription(
        "Sesión obligatoria ausente o vencida. Conserva los indicadores utilizados por la interfaz.");
    schema.addProperty("ok", new BooleanSchema()
        .description("Siempre false.")
        ._default(false));
    schema.addProperty("authenticated", new BooleanSchema()
        .description("Siempre false para este rechazo.")
        ._default(false));
    schema.addProperty("loginRequired", new BooleanSchema()
        .description("Siempre true: la interfaz debe presentar el acceso.")
        ._default(true));
    schema.addProperty("error", new StringSchema()
        .description("Mensaje seguro y apto para mostrar al usuario."));
    schema.addProperty("code", new StringSchema()
        .description("Código estable AUTHENTICATION_REQUIRED."));
    schema.addProperty("status", new IntegerSchema()
        .format("int32")
        .description("Siempre 401."));
    schema.setRequired(List.of(
        "ok",
        "authenticated",
        "loginRequired",
        "error",
        "code",
        "status"));
    return schema;
  }

  private static Schema<?> agentHistoryMessageSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.setDescription(
        "Mensaje previo. Los valores vacíos se ignoran y los roles distintos de user/assistant "
            + "se normalizan como user por compatibilidad.");
    schema.addProperty("role", new StringSchema()
        ._enum(List.of("user", "assistant"))
        .description("Rol conversacional."));
    schema.addProperty("content", new StringSchema()
        .maxLength(8_000)
        .description("Contenido previo; el servidor conserva como máximo 8000 caracteres."));
    return schema;
  }

  private static Schema<?> agentChatRequestSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.setDescription(
        "Consulta del agente clínico. Los campos desconocidos se ignoran para conservar compatibilidad.");
    schema.addProperty("message", new StringSchema()
        .minLength(1)
        .maxLength(8_000)
        .example("¿Qué toxicidades documentadas requieren seguimiento?")
        .description("Consulta actual; obligatoria luego de quitar espacios exteriores."));
    schema.addProperty("clinicalText", new StringSchema()
        .maxLength(350_000)
        .description("Contexto clínico desidentificado; se trunca de forma segura."));
    schema.addProperty("history", new ArraySchema()
        .items(new Schema<>().$ref("#/components/schemas/AgentHistoryMessage"))
        .description(
            "Historial cronológico. Se usan sólo los últimos 12 mensajes no vacíos; "
                + "la consulta actual duplicada al final se descarta."));
    schema.addProperty("timelineEvents", new ArraySchema()
        .items(new ObjectSchema())
        .description("Aceptado por compatibilidad; no se incorpora al prompt en esta versión."));
    schema.addProperty("consultAgents", new BooleanSchema()
        ._default(false)
        .description("Aceptado por compatibilidad; no activa otros agentes en esta versión."));
    schema.setRequired(List.of("message"));
    return schema;
  }

  private static Schema<?> agentTableArtifactSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.setDescription("Tabla clínica opcional, ya acotada y validada por el servidor.");
    schema.addProperty("type", new StringSchema()
        ._enum(List.of("table"))
        .description("Discriminador fijo de tabla."));
    schema.addProperty("title", new StringSchema()
        .maxLength(160)
        .description("Título opcional."));
    schema.addProperty("columns", new ArraySchema()
        .minItems(1)
        .maxItems(12)
        .items(new StringSchema().maxLength(160))
        .description("Encabezados; determinan la cantidad final de celdas por fila."));
    schema.addProperty("rows", new ArraySchema()
        .maxItems(100)
        .items(new ArraySchema()
            .maxItems(12)
            .items(new StringSchema().maxLength(500)))
        .description("Filas normalizadas al ancho de columns."));
    schema.setRequired(List.of("type", "title", "columns", "rows"));
    return schema;
  }

  private static Schema<?> agentChartPointSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.setDescription("Punto numérico finito de una serie clínica.");
    schema.addProperty("x", new StringSchema().maxLength(160));
    schema.addProperty("y", new NumberSchema().format("double"));
    schema.addProperty("label", new StringSchema().maxLength(160));
    schema.setRequired(List.of("x", "y", "label"));
    return schema;
  }

  private static Schema<?> agentChartSeriesSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.setDescription("Serie de hasta 100 puntos válidos.");
    schema.addProperty("name", new StringSchema().maxLength(160));
    schema.addProperty("color", new StringSchema()
        .pattern("^#[0-9a-fA-F]{6}$")
        .example("#2274A5")
        .description("Color hexadecimal opcional; cualquier otro formato se descarta."));
    schema.addProperty("points", new ArraySchema()
        .minItems(1)
        .maxItems(100)
        .items(new Schema<>().$ref("#/components/schemas/AgentChartPoint")));
    schema.setRequired(List.of("name", "points"));
    return schema;
  }

  private static Schema<?> agentChartArtifactSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.setDescription("Gráfico clínico opcional con series verificadas.");
    schema.addProperty("type", new StringSchema()
        ._enum(List.of("chart"))
        .description("Discriminador fijo de gráfico."));
    schema.addProperty("title", new StringSchema().maxLength(160));
    schema.addProperty("chartType", new StringSchema()
        ._enum(List.of("line", "bar", "pie"))
        ._default("line"));
    schema.addProperty("xLabel", new StringSchema().maxLength(160));
    schema.addProperty("series", new ArraySchema()
        .minItems(1)
        .maxItems(8)
        .items(new Schema<>().$ref("#/components/schemas/AgentChartSeries")));
    schema.setRequired(List.of("type", "title", "chartType", "xLabel", "series"));
    return schema;
  }

  private static Schema<?> agentArtifactSchema() {
    ComposedSchema schema = new ComposedSchema();
    schema.setDescription("Artefacto permitido: tabla o gráfico.");
    schema.setOneOf(List.of(
        new Schema<>().$ref("#/components/schemas/AgentTableArtifact"),
        new Schema<>().$ref("#/components/schemas/AgentChartArtifact")));
    return schema;
  }

  private static Schema<?> agentHighlightSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.setDescription("Términos literales y categoría visual para enfocar la historia.");
    schema.addProperty("terms", new ArraySchema()
        .minItems(1)
        .maxItems(20)
        .items(new StringSchema().minLength(3).maxLength(160)));
    schema.addProperty("color", new StringSchema()
        ._enum(List.of(
            "study", "pathology", "chemotherapy", "evolution", "hormone",
            "systemic", "radiotherapy", "surgery", "immunotherapy", "targeted"))
        ._default("study"));
    schema.setRequired(List.of("terms", "color"));
    return schema;
  }

  private static Schema<?> agentChatResponseSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.setDescription("Respuesta estable del agente clínico.");
    schema.addProperty("ok", new BooleanSchema()
        ._default(true)
        .description("Siempre true cuando el LLM respondió correctamente."));
    schema.addProperty("answer", new StringSchema()
        .maxLength(32_000)
        .description("Respuesta clínica en español. Si el proveedor no entrega JSON estructurado válido, contiene su texto plano acotado."));
    schema.addProperty("model", new StringSchema()
        .description("Modelo informado por el proveedor."));
    schema.addProperty("artifacts", new ArraySchema()
        .maxItems(8)
        .items(new Schema<>().$ref("#/components/schemas/AgentArtifact"))
        .description("Tablas y gráficos opcionales validados; entradas inválidas se omiten."));
    schema.addProperty("followUps", new ArraySchema()
        .maxItems(8)
        .items(new StringSchema().maxLength(500))
        .description("Preguntas sugeridas no vacías y sin duplicados."));
    schema.addProperty("highlights", new ArraySchema()
        .maxItems(20)
        .items(new Schema<>().$ref("#/components/schemas/AgentHighlight"))
        .description("Grupos de términos literales aptos para enfocar la historia."));
    schema.setRequired(List.of(
        "ok", "answer", "model", "artifacts", "followUps", "highlights"));
    return schema;
  }

  private static Schema<?> llmStatusResponseSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.setDescription("Estado no sensible de la integración LLM.");
    schema.addProperty("ok", new BooleanSchema()
        ._default(true)
        .description("Siempre true cuando el estado pudo consultarse."));
    schema.addProperty("enabled", new BooleanSchema()
        .description("Indica si el uso del LLM está habilitado."));
    schema.addProperty("model", new StringSchema()
        .description("Modelo configurado."));
    schema.addProperty("provider", new StringSchema()
        .description("Proveedor configurado."));
    schema.addProperty("configured", new BooleanSchema()
        .description("Indica si existe un endpoint base; no prueba conectividad."));
    schema.setRequired(List.of("ok", "enabled", "model", "provider", "configured"));
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
