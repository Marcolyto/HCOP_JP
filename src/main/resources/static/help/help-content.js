(function exposeHcopHelpContent(global) {
  "use strict";

  const topics = [
    {
      id: "overview",
      page: "main",
      category: "Primeros pasos",
      label: "Vista general",
      summary: "Cómo se organiza la ficha clínica y cómo ganar espacio en cada mitad.",
      docsHref: "/docs/manual-usuario.html#pantalla",
      steps: [
        {
          id: "header",
          target: ".app-header",
          title: "Cabecera principal",
          body: "Desde aquí se crea o abre un paciente, se accede a Configuración y al Hospital de día unificado, además de imprimir o pedir ayuda."
        },
        {
          id: "active-user",
          target: "#clinicalUserChip",
          title: "Usuario activo",
          body: "Nombre y roles identifican a la persona cuyas acciones quedan firmadas y auditadas. Las secciones y controles disponibles dependen de sus permisos.",
          optional: true
        },
        {
          id: "inbox",
          target: "#clinicalInboxBtn",
          title: "Bandeja clínica",
          body: "La campana muestra solicitudes de prescripción o continuidad asignadas al usuario. Sólo aparece cuando su rol permite resolverlas.",
          optional: true
        },
        {
          id: "workspace",
          target: ".split-workspace",
          title: "Una sola ficha de trabajo",
          body: "La historia clínica permanece a la izquierda y las herramientas asistenciales trabajan a la derecha sobre el mismo paciente."
        },
        {
          id: "splitter",
          target: ".splitter-presets",
          title: "Distribución del espacio",
          body: "Las flechas muestran una sola mitad y el botón central restaura el reparto equilibrado. También puede arrastrar el divisor."
        },
        {
          id: "right-tabs",
          target: ".right-panel-tabs",
          title: "Secciones clínicas",
          body: "Estas pestañas reúnen estudios, tratamientos, prescripción, agente, investigación, cronología, protocolos y herramientas."
        }
      ]
    },
    {
      id: "patients",
      page: "main",
      category: "Pacientes",
      label: "Crear, abrir y actualizar pacientes",
      summary: "Comenzar una ficha en blanco o recuperar una historia existente.",
      docsHref: "/docs/manual-usuario.html#paciente",
      steps: [
        {
          id: "new",
          target: "#newPatientBtn",
          title: "Nuevo paciente",
          body: "Inicia una historia sin valores clínicos heredados. La hoja conserva visibles sus secciones y formularios para comenzar la carga desde cero.",
          optional: true,
          dynamic: true
        },
        {
          id: "open",
          target: "#openLiraImportBtn",
          title: "Abrir paciente",
          body: "Busca por nombre, DNI, historia clínica o identificador y permite revisar una vista previa antes de abrir la ficha."
        },
        {
          id: "search",
          target: "#liraImportModal",
          title: "Búsqueda y confirmación",
          body: "El buscador no cambia la ficha activa hasta confirmar el paciente seleccionado.",
          prepare: { adapter: "modal", value: "liraImportModal" }
        },
        {
          id: "refresh",
          target: "#refreshActiveLiraPatientBtn",
          title: "Actualizar la ficha",
          body: "Recarga desde la base clínica canónica los datos del paciente abierto. El botón sólo se habilita cuando existe un identificador válido."
        }
      ]
    },
    {
      id: "history",
      page: "main",
      category: "Historia clínica",
      label: "Historia clínica",
      summary: "Buscar, editar, versionar y agregar evoluciones.",
      docsHref: "/docs/manual-usuario.html#historia",
      steps: [
        {
          id: "document",
          target: "#clinicalDocument",
          title: "Documento clínico",
          body: "La hoja conserva el orden clínico, las evoluciones y la auditoría. En una historia nueva también muestra los bloques narrativos, de estudios y examen con su acción Cargar; esos avisos no forman parte del dato clínico."
        },
        {
          id: "search",
          target: "[data-action=\"toggle-clinical-search\"]",
          title: "Buscar dentro de la historia",
          body: "La lupa abre una búsqueda local y resalta las coincidencias visibles sin cambiar el contenido."
        },
        {
          id: "highlight",
          target: "[data-action=\"highlight-clinical-selection\"]",
          title: "Marcar información importante",
          body: "Seleccione texto en la hoja y use el resaltador. El control vecino permite retirar una marca existente."
        },
        {
          id: "edit",
          target: "[data-action=\"edit-section\"]",
          title: "Cargar o editar con historial",
          body: "Cargar abre el formulario propio de cada bloque. La primera carga queda auditada como inicial; las modificaciones posteriores piden motivo y crean una nueva versión. El recorrido no abre ni guarda el formulario.",
          optional: true,
          dynamic: true
        },
        {
          id: "evolution",
          target: "[data-action=\"open-evolution\"]",
          title: "Agregar evolución",
          body: "Abre el registro de una evolución con fecha, profesional, motivo, texto y adjuntos.",
          optional: true,
          dynamic: true
        }
      ]
    },
    {
      id: "studies",
      page: "main",
      category: "Historia clínica",
      label: "Estudios e imágenes",
      summary: "Cargar estudios o plantillas anatómicas, marcar imágenes y adjuntarlas a una evolución.",
      docsHref: "/docs/manual-usuario.html#estudios",
      prepare: { adapter: "rightTab", value: "studies" },
      steps: [
        {
          id: "tab",
          target: "[data-right-tab=\"studies\"]",
          title: "Estudios",
          body: "La pestaña reúne el listado importado, las imágenes y el formulario de carga local."
        },
        {
          id: "list",
          target: "#studyList",
          title: "Listado del paciente",
          body: "Seleccione un estudio para revisar su detalle y los archivos vinculados. Una imagen recién subida puede eliminarse durante esta misma sesión; las históricas permanecen protegidas."
        },
        {
          id: "templates",
          target: "#openStudyTemplateBtn",
          title: "Plantillas anatómicas",
          body: "Abre una biblioteca local con búsqueda, categorías y miniaturas. La plantilla elegida se agrega como imagen del paciente y conserva autor, fuente y licencia."
        },
        {
          id: "form",
          target: "#studyForm",
          title: "Carga estructurada",
          body: "Fecha, tipo, título, origen, resumen y archivo se registran juntos. Cargar es una acción manual del usuario.",
          optional: true
        },
        {
          id: "images",
          target: "#studyImagesPanel",
          title: "Imágenes y documentos",
          body: "Desde aquí puede ampliar, imprimir, escribir o dibujar, agregar a una evolución y cambiar el orden de las imágenes sueltas."
        },
        {
          id: "viewer",
          target: "#studyImageModal",
          title: "Visor y anotaciones",
          body: "El visor permite lápiz, resaltador y texto. En una imagen suelta guarda una nueva versión rasterizada; en un estudio histórico conserva el original y genera una copia anotada. La ayuda nunca guarda cambios.",
          optional: true
        }
      ]
    },
    {
      id: "care",
      page: "main",
      category: "Tratamientos",
      label: "Tratamientos y aplicaciones",
      summary: "Consultar planes, ciclos, aplicaciones y documentos.",
      docsHref: "/docs/manual-usuario.html#hospital",
      prepare: [
        { adapter: "rightTab", value: "care" },
        { adapter: "careView", value: "treatments" }
      ],
      steps: [
        {
          id: "tab",
          target: "[data-right-tab=\"care\"]",
          title: "H. de día",
          body: "La sección integrada resume los tratamientos y sus aplicaciones sin abandonar la historia clínica."
        },
        {
          id: "views",
          target: ".care-view-tabs",
          title: "Tratamientos y drogas",
          body: "Cambie entre la jerarquía de tratamientos y la consulta de drogas y esquemas."
        },
        {
          id: "new",
          target: "#careHierarchyNewTreatmentBtn",
          title: "Nuevo tratamiento",
          body: "Inicia la prescripción de un plan para el paciente activo. No se guarda hasta completar y confirmar el formulario."
        },
        {
          id: "hierarchy",
          target: "#careTreatmentHierarchy",
          title: "Tratamiento, ciclos y aplicaciones",
          body: "El signo más despliega el plan. Cada tarjeta conserva esquema, estado, cantidad de ciclos y fechas límite."
        },
        {
          id: "wide",
          target: "#openCareInfusionManagerBtn",
          title: "Hospital de día",
          body: "Abre el único modal en el orden Nuevo tratamiento, Farmacia, Sillones y Tratamientos. Farmacia y Sillones funcionan aunque no haya un paciente abierto."
        }
      ]
    },
    {
      id: "treatment-new",
      page: "main",
      category: "Tratamientos",
      label: "Prescribir un tratamiento",
      summary: "Elegir diagnóstico, protocolo, ciclos y fecha inicial.",
      docsHref: "/docs/manual-usuario.html#tratamientos",
      steps: [
        {
          id: "dialog",
          target: "#careTreatmentModal",
          title: "Nuevo tratamiento",
          body: "Este formulario reúne el diagnóstico, el esquema, el profesional, la cantidad de ciclos y la fecha del primer ciclo.",
          optional: true
        },
        {
          id: "projection",
          target: "#careTreatmentProjection",
          title: "Proyección de ciclos",
          body: "Al elegir el protocolo y la fecha inicial se muestran duración estimada y fechas de los ciclos siguientes."
        },
        {
          id: "requirements",
          target: "#careTreatmentRequirementList",
          title: "Requisitos previos",
          body: "Los requisitos definidos por el protocolo aparecen para su confirmación antes de iniciar."
        },
        {
          id: "save",
          target: "#saveCareTreatmentBtn",
          title: "Guardar tratamiento",
          body: "Valida y registra el plan. También agrega a la hoja izquierda una evolución inmutable con diagnóstico, esquema, ciclos, peso, talla en cm y cálculos disponibles. La demostración nunca lo presiona."
        }
      ]
    },
    {
      id: "treatment-detail",
      page: "main",
      category: "Tratamientos",
      label: "Detalle de tratamiento",
      summary: "Drogas, aplicaciones, Farmacia, consentimiento y documentos.",
      docsHref: "/docs/manual-usuario.html#detalle-tratamiento",
      steps: [
        {
          id: "modal",
          target: "#careTreatmentManagerModal",
          title: "Gestión amplia",
          body: "La lista utiliza los mismos datos que la pestaña integrada, con espacio adicional para el detalle.",
          optional: true
        },
        {
          id: "table",
          target: "#careTreatmentManagerTable",
          title: "Tratamientos del paciente",
          body: "Cada fila muestra los datos principales; el icono de documento informa el consentimiento y el ojo abre el detalle."
        },
        {
          id: "detail",
          target: "#careTreatmentManagerDetail",
          title: "Detalle completo",
          body: "Aquí aparecen ciclos, drogas, aplicaciones, Farmacia y documentos disponibles.",
          optional: true
        },
        {
          id: "chairs-shortcut",
          target: "[data-care-manager-detail-action=\"schedule\"]",
          title: "Continuar en Sillones",
          body: "El atajo lleva el ciclo seleccionado a la agenda integrada. Resalta su tarjeta pendiente o el turno ya asignado, sin abrir otro formulario.",
          optional: true,
          dynamic: true
        }
      ]
    },
    {
      id: "scheduler-pharmacy",
      page: "main",
      category: "Hospital de día",
      label: "Hospital de día · Farmacia",
      summary: "Confirmar dónde está la medicación antes de asignar sillón.",
      docsHref: "/docs/manual-usuario.html#hospital",
      prepare: [
        { adapter: "modal", value: "careTreatmentManagerModal" },
        { adapter: "hospitalTab", value: "pharmacy" }
      ],
      steps: [
        {
          id: "mode",
          target: "[data-care-hospital-tab=\"pharmacy\"]",
          title: "Etapa de Farmacia",
          body: "Antes del turno se registra si la medicación está pendiente, fue recibida o la llevará el paciente. Esta vista general no requiere una historia abierta."
        },
        {
          id: "search",
          target: "#careSchedulePharmacySearch",
          title: "Buscar ciclos pendientes",
          body: "Filtra por paciente, DNI, esquema o diagnóstico."
        },
        {
          id: "rows",
          target: "#careSchedulePharmacyRows",
          title: "Estado de la medicación",
          body: "Cada fila permite elegir un único estado. La ayuda no cambia ninguna selección.",
          dynamic: true
        }
      ]
    },
    {
      id: "scheduler-qr",
      page: "main",
      category: "Hospital de día",
      label: "Hospital de día · Escanear QR",
      summary: "Identificar la aplicación exacta, abrir su administración y documentar la lectura.",
      docsHref: "/docs/manual-usuario.html#escanear-qr",
      prepare: [
        { adapter: "modal", value: "careTreatmentManagerModal" }
      ],
      steps: [
        {
          id: "qr-entry",
          target: "#openCareQrScannerBtn",
          title: "Escanear QR",
          body: "Abre el lector integrado. Puede usar la cámara en HTTPS o localhost, elegir una imagen o pegar el contenido del código. Requiere permiso para editar Hospital de día."
        },
        {
          id: "qr-capture",
          target: "#careQrScannerModal",
          title: "Verificar identidad",
          body: "El sistema valida paciente, tratamiento, ciclo y aplicación y muestra primero su resumen. Revíselo y pulse Abrir ficha de administración; hasta confirmarlo no cambia el paciente activo. La lectura deja una evolución inmutable, pero no cambia ningún estado.",
          optional: true,
          dynamic: true
        },
        {
          id: "qr-treatment",
          target: "[data-care-qr-open-treatment]",
          title: "Abrir tratamiento completo",
          body: "Desde la ficha identificada abre el tratamiento y el ciclo exactos sin marcar la administración como realizada.",
          optional: true,
          dynamic: true
        },
        {
          id: "qr-next-step",
          target: "[data-care-qr-next-step]",
          title: "Un solo siguiente paso",
          body: "La ficha resume Estado clínico, Farmacia, Administración y Drogas. Ofrece únicamente el siguiente paso válido según lo que todavía falta completar.",
          optional: true,
          dynamic: true
        },
        {
          id: "qr-finalize",
          target: "#careQrAdministrationCompletionForm",
          title: "Finalización separada",
          body: "Sólo se habilita en Observación, con Farmacia liberada o no requerida y todas las drogas resueltas. Exige confirmar y escribir una observación; ningún avance general reemplaza este cierre dedicado, que registra usuario, evolución y auditoría.",
          optional: true,
          dynamic: true
        }
      ]
    },
    {
      id: "scheduler-chairs",
      page: "main",
      category: "Hospital de día",
      label: "Hospital de día · Sillones",
      summary: "Asignar, mover y retirar turnos sin superposición.",
      docsHref: "/docs/manual-usuario.html#hospital",
      prepare: [
        { adapter: "modal", value: "careTreatmentManagerModal" },
        { adapter: "hospitalTab", value: "chairs" }
      ],
      steps: [
        {
          id: "mode",
          target: "[data-care-hospital-tab=\"chairs\"]",
          title: "Agenda por sillón",
          body: "Cada sillón es una columna y cada celda representa la fracción configurada de tiempo. La agenda general no requiere un paciente activo."
        },
        {
          id: "filter",
          target: "#careScheduleCandidateFilter",
          title: "Lista de espera",
          body: "Puede mostrar pacientes prescriptos, con medicación confirmada o que llevan la medicación."
        },
        {
          id: "candidate",
          target: "#careScheduleCandidates",
          title: "Tratamientos pendientes",
          body: "Los ciclos aparecen por fecha prevista, primero el más próximo. Seleccione un paciente para ver en celeste todos los espacios libres donde entra.",
          dynamic: true
        },
        {
          id: "manage",
          target: ".care-schedule-candidate-manage",
          title: "Gestionar el ciclo",
          body: "Abre suspensión transitoria o definitiva con motivo, o dirige una solicitud a un médico habilitado. Para una suspensión transitoria, el mismo botón guía dos pasos: solicitar la prescripción del ciclo suspendido y, cuando esté confirmada, reanudar ese mismo tratamiento sin reiniciar los ciclos. Cada envío, decisión y cambio queda como evolución auditada e inmutable en la hoja clínica.",
          dynamic: true
        },
        {
          id: "board",
          target: "#careScheduleGrid",
          title: "Asignación visual",
          body: "Arrastre el tratamiento al horario deseado. La X libera el sillón incluso si el tratamiento fue suspendido; quitar el turno no reactiva ni modifica esa suspensión. El recorrido anima el cursor como ejemplo, pero no genera un turno.",
          cursor: { from: "left", to: "center", gesture: "drag" }
        },
        {
          id: "viewport",
          target: ".care-schedule-chair-viewport-controls",
          title: "Más sillones o mayor detalle",
          body: "Las flechas recorren grupos de sillones y las lupas cambian cuántos se ven al mismo tiempo."
        }
      ]
    },
    {
      id: "prescription",
      page: "main",
      category: "Indicaciones",
      label: "Prescripción",
      summary: "Medicamentos, certificados, estudios, texto libre y formularios.",
      docsHref: "/docs/manual-usuario.html#prescripcion",
      prepare: { adapter: "rightTab", value: "prescription" },
      steps: [
        {
          id: "tab",
          target: "[data-right-tab=\"prescription\"]",
          title: "Prescripción e indicaciones",
          body: "Todos los documentos se generan desde la ficha activa."
        },
        {
          id: "types",
          target: ".prescription-tabs",
          title: "Tipo de documento",
          body: "Elija medicamento, certificado, estudio, texto libre o formulario sistémico."
        },
        {
          id: "form",
          target: "#prescriptionForm",
          title: "Editor",
          body: "Complete los campos del tipo elegido. En medicamentos se incluyen cobertura y número de afiliado."
        },
        {
          id: "submit",
          target: "#rxSubmitBtn",
          title: "Prescribir o generar",
          body: "Valida y prepara el documento. La ayuda nunca envía el formulario."
        },
        {
          id: "records",
          target: "#rxDraftList",
          title: "Documentos registrados",
          body: "Desde cada tarjeta se puede imprimir, duplicar o eliminar con confirmación."
        }
      ]
    },
    {
      id: "agent",
      page: "main",
      category: "Apoyo clínico",
      label: "Agente clínico",
      summary: "Consultar la historia activa y navegar a los hallazgos.",
      docsHref: "/docs/manual-usuario.html#agente",
      prepare: { adapter: "rightTab", value: "agent" },
      steps: [
        {
          id: "suggestions",
          target: "#agentSuggestions",
          title: "Consultas sugeridas",
          body: "Estos accesos preparan preguntas frecuentes sobre evolución, tratamientos, marcadores y hallazgos."
        },
        {
          id: "composer",
          target: "#agentChatForm",
          title: "Pregunta clínica",
          body: "Escriba una consulta sobre el paciente activo. El envío requiere el servicio configurado."
        },
        {
          id: "messages",
          target: "#agentChatMessages",
          title: "Respuesta navegable",
          body: "Fechas, filas y gráficos pueden llevar a la ubicación relacionada de la historia izquierda."
        }
      ]
    },
    {
      id: "research",
      page: "main",
      category: "Apoyo clínico",
      label: "Investigación",
      summary: "Completar formularios configurables y registrarlos en la historia.",
      docsHref: "/docs/manual-usuario.html#investigacion",
      prepare: { adapter: "rightTab", value: "research" },
      steps: [
        {
          id: "template",
          target: "#researchTemplateSelect",
          title: "Elegir formulario",
          body: "La lista combina el registro general con los formularios activos creados en Configuración."
        },
        {
          id: "form",
          target: "#researchForm",
          title: "Registro estructurado",
          body: "Complete identificación del protocolo, evento, participante, tratamiento, respuesta y seguridad según corresponda."
        },
        {
          id: "records",
          target: "#researchRecordList",
          title: "Registros incorporados",
          body: "Los eventos guardados se ordenan por fecha y pueden localizarse en la historia."
        }
      ]
    },
    {
      id: "timeline",
      page: "main",
      category: "Apoyo clínico",
      label: "Línea de tiempo",
      summary: "Explorar eventos por período y localizar su origen.",
      docsHref: "/docs/manual-usuario.html#timeline",
      prepare: { adapter: "rightTab", value: "timeline" },
      steps: [
        {
          id: "surface",
          target: "#rightTimeline",
          title: "Cronología clínica",
          body: "Agrupa historia, estudios, evoluciones, tratamientos y registros de investigación."
        },
        {
          id: "filters",
          target: "[data-action=\"toggle-milestones\"]",
          title: "Filtrar hitos",
          body: "Muestra sólo los eventos marcados como importantes.",
          optional: true,
          dynamic: true
        },
        {
          id: "periods",
          target: "[data-action=\"toggle-timeline-period\"]",
          title: "Abrir períodos",
          body: "Años y meses se despliegan sin modificar los registros.",
          optional: true,
          dynamic: true
        },
        {
          id: "event",
          target: ".right-timeline-item",
          title: "Volver a la fuente",
          body: "Un evento lleva a la fecha o al registro relacionado y lo resalta en celeste.",
          optional: true,
          dynamic: true
        }
      ]
    },
    {
      id: "protocols",
      page: "main",
      category: "Apoyo clínico",
      label: "Protocolos",
      summary: "Consultar esquemas, drogas, administración y duración.",
      docsHref: "/docs/manual-usuario.html#protocolos",
      prepare: { adapter: "rightTab", value: "protocols" },
      steps: [
        {
          id: "source",
          target: "#protocolSource",
          title: "Fuente de esquemas",
          body: "Puede consultar protocolos COIR o el catálogo farmacológico disponible."
        },
        {
          id: "category",
          target: "#protocolCategory",
          title: "Patología o grupo",
          body: "Acota el catálogo antes de elegir un esquema."
        },
        {
          id: "scheme",
          target: "#protocolScheme",
          title: "Esquema",
          body: "La selección carga duración, frecuencia, drogas y preparación."
        },
        {
          id: "overview",
          target: "#protocolOverview",
          title: "Vista del protocolo",
          body: "Resume el esquema completo y permite revisar cada componente."
        }
      ]
    },
    {
      id: "tools-guides",
      page: "main",
      category: "Herramientas",
      label: "Herramientas · Guías",
      summary: "Buscar y abrir documentos clínicos locales.",
      docsHref: "/docs/manual-usuario.html#herramientas",
      prepare: [
        { adapter: "rightTab", value: "tools" },
        { adapter: "toolTab", value: "guides" }
      ],
      steps: [
        {
          id: "tabs",
          target: ".tools-tabs",
          title: "Herramientas clínicas",
          body: "Cambie entre Guías, Estadificación TNM y Calculadoras."
        },
        {
          id: "search",
          target: "#guideSearch",
          title: "Buscar una guía",
          body: "Filtra la biblioteca local por tumor, título o fuente."
        },
        {
          id: "list",
          target: "#guideList",
          title: "Biblioteca local",
          body: "Una tarjeta abre el PDF en el visor interno."
        }
      ]
    },
    {
      id: "tools-tnm",
      page: "main",
      category: "Herramientas",
      label: "Herramientas · TNM",
      summary: "Registrar una estadificación estructurada con reglas locales.",
      docsHref: "/docs/manual-usuario.html#herramientas",
      prepare: [
        { adapter: "rightTab", value: "tools" },
        { adapter: "toolTab", value: "tnm" }
      ],
      steps: [
        {
          id: "site",
          target: "#tnmPrimarySite",
          title: "Sitio primario",
          body: "El sitio determina las categorías T, N y M y los factores específicos disponibles."
        },
        {
          id: "axes",
          target: ".tnm-grid--three",
          title: "Componentes TNM",
          body: "Seleccione tumor, ganglios y metástasis según la evaluación clínica o patológica."
        },
        {
          id: "result",
          target: "#tnmResult",
          title: "Resultado determinístico",
          body: "El agrupamiento se calcula con tablas locales y no envía datos fuera del equipo."
        }
      ]
    },
    {
      id: "tools-calculators",
      page: "main",
      category: "Herramientas",
      label: "Herramientas · Calculadoras",
      summary: "Abrir las herramientas clínicas activas.",
      docsHref: "/docs/manual-usuario.html#herramientas",
      prepare: [
        { adapter: "rightTab", value: "tools" },
        { adapter: "toolTab", value: "calculators" }
      ],
      steps: [
        {
          id: "frame",
          target: "#clinicalCalculatorFrame",
          title: "Calculadoras y scores",
          body: "La biblioteca funciona dentro de esta sección y usa un único desplazamiento."
        },
        {
          id: "inside",
          target: "#clinicalCalculatorFrame",
          title: "Ayuda de la herramienta activa",
          body: "Dentro de la calculadora puede abrir el recorrido propio para conocer variables, cálculo e interpretación."
        }
      ]
    },
    {
      id: "config-protocols",
      page: "configuration",
      category: "Configuración",
      label: "Configurar protocolos",
      summary: "Abrir el editor completo de esquemas y drogas.",
      docsHref: "/docs/manual-usuario.html#configuracion",
      prepare: { adapter: "configTab", value: "protocols" },
      steps: [
        {
          id: "navigation",
          target: "[data-config-tab=\"protocols\"]",
          title: "Protocolos",
          body: "Esta opción abre el catálogo versionado de esquemas."
        },
        {
          id: "frame",
          target: ".protocol-panel iframe",
          title: "Editor de protocolos",
          body: "El editor tiene su propia ayuda para catálogo, datos generales, drogas, preparación y vista previa."
        }
      ]
    },
    {
      id: "config-guides",
      page: "configuration",
      category: "Configuración",
      label: "Configurar guías",
      summary: "Agregar PDF y gestionar metadatos y visibilidad.",
      docsHref: "/docs/manual-usuario.html#configuracion",
      prepare: { adapter: "configTab", value: "guides" },
      steps: [
        {
          id: "navigation",
          target: "[data-config-tab=\"guides\"]",
          title: "Guías",
          body: "Administra la biblioteca que aparece en Herramientas."
        },
        {
          id: "catalog",
          target: "#guideConfigList",
          title: "Catálogo",
          body: "Seleccione una guía para editar sus datos o agregar un PDF nuevo."
        },
        {
          id: "editor",
          target: "#guideConfigForm",
          title: "Metadatos y visibilidad",
          body: "Título, categoría, fuente, versión y etiquetas mejoran la búsqueda. Desactivar conserva el archivo.",
          optional: true
        }
      ]
    },
    {
      id: "config-study-templates",
      page: "configuration",
      category: "Configuración",
      label: "Configurar plantillas anatómicas",
      summary: "Agregar imágenes propias y gestionar su procedencia y visibilidad.",
      docsHref: "/docs/manual-usuario.html#configuracion",
      prepare: { adapter: "configTab", value: "study-templates" },
      steps: [
        {
          id: "navigation",
          target: "[data-config-tab=\"study-templates\"]",
          title: "Plantillas anatómicas",
          body: "Administra las imágenes propias que estarán disponibles al cargar un estudio."
        },
        {
          id: "catalog",
          target: "#studyTemplateAdminList",
          title: "Catálogo con miniaturas",
          body: "Busque por título, categoría, autor o etiqueta. También puede mostrar y reactivar las plantillas desactivadas."
        },
        {
          id: "upload",
          target: "label[for=\"studyTemplateAdminUpload\"]",
          title: "Agregar una imagen",
          body: "Seleccione una imagen PNG, JPG, GIF o WebP. La carga se realiza de a una para documentar correctamente su origen."
        },
        {
          id: "editor",
          target: "#studyTemplateAdminForm",
          title: "Procedencia, derechos y visibilidad",
          body: "Complete título, categoría, fuente, licencia y atribución. Confirmar los derechos de uso es obligatorio antes de guardar.",
          optional: true
        }
      ]
    },
    {
      id: "config-calculators",
      page: "configuration",
      category: "Configuración",
      label: "Configurar calculadoras y scores",
      summary: "Crear herramientas sin programación.",
      docsHref: "/docs/manual-usuario.html#configuracion",
      prepare: { adapter: "configTab", value: "calculators" },
      steps: [
        {
          id: "navigation",
          target: "[data-config-tab=\"calculators\"]",
          title: "Calculadoras y scores",
          body: "Administra herramientas originales, personalizaciones y nuevas definiciones."
        },
        {
          id: "catalog",
          target: ".calculator-catalog",
          title: "Catálogo clínico",
          body: "Active, desactive o seleccione una herramienta para personalizarla."
        },
        {
          id: "variables",
          target: "#calculatorVariables",
          title: "Variables",
          body: "Agregue datos de entrada, opciones o reglas y ordénelos arrastrando.",
          optional: true
        },
        {
          id: "result",
          target: "#calculatorResultSection",
          title: "Resultado e interpretación",
          body: "Define etiqueta, unidad, decimales y significado de cada rango.",
          optional: true
        }
      ]
    },
    {
      id: "config-research",
      page: "configuration",
      category: "Configuración",
      label: "Configurar investigación",
      summary: "Diseñar formularios versionados arrastrando campos.",
      docsHref: "/docs/manual-usuario.html#configuracion",
      prepare: { adapter: "configTab", value: "research" },
      steps: [
        {
          id: "navigation",
          target: "[data-config-tab=\"research\"]",
          title: "Formularios de investigación",
          body: "Los formularios activos aparecen en la ficha del paciente."
        },
        {
          id: "palette",
          target: ".field-palette",
          title: "Elementos disponibles",
          body: "Agregue secciones, textos, números, fechas, opciones o casillas."
        },
        {
          id: "fields",
          target: "#researchFormFields",
          title: "Constructor visual",
          body: "Cambie etiquetas y códigos, marque obligatoriedad y arrastre para ordenar.",
          optional: true
        }
      ]
    },
    {
      id: "config-day-hospital",
      page: "configuration",
      category: "Configuración",
      label: "Configurar Hospital de día",
      summary: "Definir sillones, fracción mínima y jornada.",
      docsHref: "/docs/manual-usuario.html#configuracion",
      prepare: { adapter: "configTab", value: "day-hospital" },
      steps: [
        {
          id: "navigation",
          target: "[data-config-tab=\"day-hospital\"]",
          title: "Hospital de día",
          body: "Estos parámetros construyen la grilla operativa del turnero."
        },
        {
          id: "chairs",
          target: "#dayHospitalChairCount",
          title: "Cantidad de sillones",
          body: "Define la capacidad simultánea de atención."
        },
        {
          id: "slots",
          target: "#dayHospitalSlotMinutes",
          title: "Fracción mínima",
          body: "Puede trabajar con casilleros de 5, 10, 15, 20 o 30 minutos."
        },
        {
          id: "preview",
          target: ".schedule-preview",
          title: "Vista previa",
          body: "Resume la cantidad de casilleros por sillón y el horario de trabajo antes de guardar."
        }
      ]
    },
    {
      id: "config-access-control",
      page: "configuration",
      category: "Configuración",
      label: "Configurar usuarios y permisos",
      summary: "Administrar cuentas, roles, acciones autorizadas y modo de ingreso.",
      docsHref: "/docs/manual-usuario.html#config-acceso",
      prepare: { adapter: "configTab", value: "access-control" },
      steps: [
        {
          id: "navigation",
          target: "[data-config-tab=\"access-control\"]",
          title: "Usuarios y permisos",
          body: "Este módulo reúne cuentas profesionales, roles y seguridad. Cada vista exige su propio permiso administrativo."
        },
        {
          id: "views",
          target: ".access-admin-tabs",
          title: "Tres áreas protegidas",
          body: "Usuarios gestiona identidades; Roles y permisos define la matriz; Seguridad elige login o acceso automático y duración de sesión."
        },
        {
          id: "users",
          target: "#accessUsersPanel",
          title: "Usuarios activos",
          body: "Registre correo, nombre, especialidad, matrícula, clave y roles. Desactivar conserva auditoría pero impide ingresar.",
          optional: true
        },
        {
          id: "roles",
          target: "#accessRolesPanel",
          title: "Roles y acciones",
          body: "Separe lectura de edición y habilite sólo las acciones necesarias, incluidas solicitudes, suspensiones y decisiones médicas.",
          optional: true
        },
        {
          id: "security",
          target: "#accessSecurityPanel",
          title: "Política de acceso",
          body: "Puede exigir correo y clave o elegir un usuario activo para acceso automático, y definir el vencimiento de sesión. Ambos modos conservan identidad, permisos y trazabilidad. Para acceso externo use VPN o HTTPS/TLS; nunca exponga directamente el puerto 5180 por HTTP.",
          optional: true
        }
      ]
    },
    {
      id: "protocol-editor",
      page: "protocol-admin",
      category: "Configuración",
      label: "Editor de protocolos",
      summary: "Crear o modificar un protocolo completo.",
      docsHref: "/docs/manual-usuario.html#protocolos",
      steps: [
        {
          id: "catalog",
          target: ".catalog-panel",
          title: "Catálogo",
          body: "Busque protocolos activos, archivados o registros COIR. Estos últimos precargan duración, drogas y preparación disponibles al convertirlos."
        },
        {
          id: "new",
          target: "#newProtocolBtn",
          title: "Nuevo protocolo",
          body: "Prepara un formulario vacío. El recorrido no lo guarda."
        },
        {
          id: "general",
          target: ".general-section",
          prepare: { adapter: "protocolView", value: "editor" },
          title: "Datos generales",
          body: "Nombre, grupo, frecuencia del ciclo, duración operativa y vínculo COIR."
        },
        {
          id: "components",
          target: ".component-section",
          title: "Drogas y administración",
          body: "Vincule drogas existentes, defina dosis, vía, dilución, velocidad, tiempo y preparación."
        },
        {
          id: "preview",
          target: ".preview-section",
          title: "Vista previa",
          body: "Compruebe cómo se mostrará el protocolo antes de guardar."
        }
      ]
    },
    {
      id: "calculator-use",
      page: "tools",
      category: "Herramientas",
      label: "Usar una calculadora o score",
      summary: "Elegir una herramienta, completar variables e interpretar el resultado.",
      docsHref: "/docs/manual-usuario.html#herramientas",
      steps: [
        {
          id: "filters",
          target: [".filter-group", "#embedded-category"],
          title: "Filtrar herramientas",
          body: "Seleccione el área clínica para reducir el catálogo."
        },
        {
          id: "catalog",
          target: ["#tool-list", "#embedded-tool"],
          title: "Elegir una herramienta",
          body: "El listado combina calculadoras incluidas y herramientas activadas desde Configuración."
        },
        {
          id: "workspace",
          target: ".workspace",
          title: "Completar y revisar",
          body: "Ingrese las variables solicitadas y lea resultado, explicación, límites y fuente antes de usarlo clínicamente."
        }
      ]
    }
  ];

  function deepFreeze(value) {
    if (!value || typeof value !== "object" || Object.isFrozen(value)) return value;
    Object.freeze(value);
    Object.values(value).forEach(deepFreeze);
    return value;
  }

  const content = {
    version: "1.0.0",
    product: "HCOP Centro Oncológico",
    manualHref: "/docs/manual-usuario.html",
    topics
  };

  global.HcopHelpContent = deepFreeze(content);
})(typeof window !== "undefined" ? window : globalThis);
