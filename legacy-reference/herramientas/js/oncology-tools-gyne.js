(function (root, factory) {
  const createOncologyToolsGyne = factory();
  if (typeof module === "object" && module.exports) module.exports = createOncologyToolsGyne;
  if (root) root.createOncologyToolsGyne = createOncologyToolsGyne;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const FIELD_LABELS = Object.freeze({
    pelvicNodeStatus: "estado de ganglios pelvicos",
    surgicalMarginStatus: "estado de margenes quirurgicos",
    parametrialInvasion: "invasion parametrial",
    lvsi: "invasion linfovascular (LVSI)",
    stromalInvasion: "tercio de invasion estromal",
    tumorSizeCm: "tamano tumoral",
    poleStatus: "estado POLE",
    mmrStatus: "estado MMR",
    p53Status: "patron p53",
    grade: "grado histologico",
    erPercent: "receptor de estrogeno",
    ca125: "CA 125",
    menopausalStatus: "estado menopausico",
    ultrasound: "hallazgos ecograficos",
    "ultrasound.multilocular": "multilocularidad",
    "ultrasound.solidAreas": "areas solidas",
    "ultrasound.metastases": "metastasis ecograficas",
    "ultrasound.ascites": "ascitis",
    "ultrasound.bilateral": "bilateralidad",
    peritonealMassiveOrMiliary: "carcinomatosis peritoneal",
    diaphragmWidespreadOrConfluent: "enfermedad diafragmatica",
    mesenteryLargeNodulesOrRoot: "enfermedad mesenterica",
    omentumToGreaterCurvature: "enfermedad omental",
    bowelResectionExpectedOrExtensiveSerosalDisease: "infiltracion intestinal",
    gastricWallInvolvement: "infiltracion gastrica",
    largestLiverSurfaceLesionCm: "lesion superficial hepatica",
    firstRelapse: "primera recaida",
    platinumFreeIntervalMonths: "intervalo libre de platino",
    ecog: "ECOG",
    ascitesMl: "volumen de ascitis",
    completeResectionInitialSurgery: "reseccion completa inicial",
  });

  function readableField(field) {
    return FIELD_LABELS[field] || String(field).replace(/([A-Z])/g, " $1").toLowerCase();
  }

  function joinNatural(values) {
    const items = values.filter(Boolean);
    if (!items.length) return "ninguno";
    if (items.length === 1) return items[0];
    return `${items.slice(0, -1).join(", ")} y ${items[items.length - 1]}`;
  }

  function yesNo(value) {
    if (value === true || value === "yes") return true;
    if (value === false || value === "no") return false;
    return null;
  }

  function formatNumber(value, digits = 2) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return "no consignado";
    return numeric.toLocaleString("es-AR", { maximumFractionDigits: digits });
  }

  function validationResult(calculated, result, extraNotes = []) {
    const missing = (calculated.missing || []).map(readableField);
    const errors = calculated.errors || [];
    const pieces = [];
    if (missing.length) pieces.push(`Falta completar: ${missing.join(", ")}.`);
    if (errors.length) pieces.push(`Revisar: ${errors.join("; ")}.`);
    return result({
      title: "No calculable con los datos actuales",
      detail: pieces.join(" ") || "La regla no pudo aplicarse con los datos ingresados.",
      badge: "datos incompletos",
      score: 0,
      showScore: false,
      severity: "warn",
      metrics: [],
      notes: [
        "Los campos ausentes no se interpretan automaticamente como hallazgos negativos.",
        ...extraNotes,
      ],
    });
  }

  function createOncologyToolsGyne(helpers = {}) {
    const requiredHelpers = ["number", "select", "checkbox", "section", "option", "result"];
    for (const name of requiredHelpers) {
      if (typeof helpers[name] !== "function") throw new TypeError(`Falta el helper ${name}`);
    }
    const rules = helpers.rules;
    const requiredRules = ["sedlis", "peters", "promiseEsgo2025", "rmiI", "fagotti2006", "agoDesktopIII"];
    if (!rules || requiredRules.some((name) => typeof rules[name] !== "function")) {
      throw new TypeError("helpers.rules debe ser una instancia compatible de OncologyRulesGyne");
    }

    const { number, select, checkbox, section, option, result } = helpers;
    const yesNoOptions = [option("yes", "Si"), option("no", "No")];
    const nodeOptions = [
      option("negative", "Negativos"),
      option("isolated_tumor_cells", "Solo celulas tumorales aisladas"),
      option("micrometastasis", "Micrometastasis"),
      option("macrometastasis", "Macrometastasis"),
    ];
    const marginOptions = [
      option("negative", "Negativos"),
      option("positive", "Positivos"),
    ];

    return [
      {
        id: "gyne-sedlis",
        number: "GYN1",
        title: "Cuello uterino — criterios de Sedlis",
        category: "ginecologia",
        subtitle: "Combinaciones de riesgo intermedio luego de cirugía radical.",
        source: "GOG-92 / Sedlis - tabla vigente 2025",
        clinicalUse: "Reproduce las cuatro combinaciones publicadas de LVSI, profundidad estromal y tamano tumoral. Solo corresponde con ganglios, margenes y parametrios negativos.",
        fields: [
          section("sedlis_context_section", "Primero confirme el contexto posoperatorio", {
            help: "La presencia de una caracteristica de alto riesgo hace que Sedlis no sea la regla aplicable.",
          }),
          select("sedlis_node_status", "Ganglios pelvicos", nodeOptions, {
            help: "Las micrometastasis y macrometastasis son caracteristicas de alto riesgo.",
          }),
          select("sedlis_margin_status", "Margenes quirurgicos", marginOptions),
          select("sedlis_parametrium", "Invasion parametrial", yesNoOptions),
          section("sedlis_rule_section", "Variables de la tabla de Sedlis", {
            help: "Complete estas variables cuando ganglios y margenes sean negativos y no exista invasion parametrial.",
          }),
          select("sedlis_lvsi", "LVSI", yesNoOptions, { required: false }),
          select("sedlis_stromal", "Profundidad de invasion estromal", [
            option("superficial", "Tercio superficial"),
            option("middle", "Tercio medio"),
            option("deep", "Tercio profundo"),
          ], { required: false }),
          number("sedlis_size", "Tamano tumoral (cm)", {
            min: 0.01,
            step: 0.01,
            required: false,
            help: "La tabla original utilizo el tamano determinado por palpacion clinica.",
          }),
          select("sedlis_size_method", "Metodo de medicion del tamano", [
            option("", "No consignado"),
            option("clinical_palpation", "Palpacion clinica"),
            option("pathology", "Anatomia patologica"),
            option("imaging", "Imagenes"),
          ], { required: false }),
        ],
        calculate(values) {
          const calculated = rules.sedlis({
            pelvicNodeStatus: values.sedlis_node_status,
            surgicalMarginStatus: values.sedlis_margin_status,
            parametrialInvasion: yesNo(values.sedlis_parametrium),
            lvsi: yesNo(values.sedlis_lvsi),
            stromalInvasion: values.sedlis_stromal,
            tumorSizeCm: values.sedlis_size,
            ...(values.sedlis_size_method ? { tumorSizeMethod: values.sedlis_size_method } : {}),
          });
          if (!calculated.valid) return validationResult(calculated, result);
          if (!calculated.applicable) {
            return result({
              title: "Sedlis no es aplicable en este contexto",
              detail: joinNatural(calculated.exclusionReasons || []),
              badge: "fuera de la poblacion Sedlis",
              score: 0,
              showScore: false,
              severity: "warn",
              metrics: [],
              notes: [
                "La salida solo delimita la aplicabilidad de esta regla historica.",
                "Las celulas tumorales aisladas conservan significado adyuvante incierto y no se fuerzan a una categoria negativa.",
              ],
            });
          }
          return result({
            title: calculated.met ? "Cumple criterios de Sedlis" : "No cumple criterios de Sedlis",
            detail: calculated.met
              ? joinNatural(calculated.matchedCriteria.map((item) => item.label))
              : "No coincide con ninguna de las cuatro combinaciones exactas publicadas.",
            badge: calculated.met ? "Sedlis positivo" : "Sedlis negativo",
            score: 0,
            showScore: false,
            severity: calculated.met ? "warn" : "info",
            metrics: [
              { label: "LVSI", value: values.sedlis_lvsi === "yes" ? "positivo" : "negativo" },
              { label: "Invasion estromal", value: values.sedlis_stromal },
              { label: "Tamano", value: `${formatNumber(values.sedlis_size)} cm` },
            ],
            notes: [
              ...(calculated.notes || []),
              "Es una clasificacion de riesgo; no constituye por si sola una indicacion terapeutica.",
            ],
          });
        },
      },
      {
        id: "gyne-peters",
        number: "GYN2",
        title: "Cuello uterino — criterios de Peters",
        category: "ginecologia",
        subtitle: "Características de alto riesgo en la anatomía patológica posoperatoria.",
        source: "GOG-109 / Peters - ESGO 2023",
        clinicalUse: "Identifica la presencia de ganglios pelvicos metastasicos, margenes positivos o invasion parametrial luego de cirugia radical.",
        fields: [
          section("peters_section", "Anatomia patologica definitiva", {
            help: "Seleccione el estado observado; no se asumen resultados negativos por omision.",
          }),
          select("peters_node_status", "Ganglios pelvicos", nodeOptions),
          select("peters_margin_status", "Margenes quirurgicos", marginOptions),
          select("peters_parametrium", "Invasion parametrial", yesNoOptions),
        ],
        calculate(values) {
          const calculated = rules.peters({
            pelvicNodeStatus: values.peters_node_status,
            surgicalMarginStatus: values.peters_margin_status,
            parametrialInvasion: yesNo(values.peters_parametrium),
          });
          if (!calculated.valid) return validationResult(calculated, result);
          if (calculated.met === null) {
            return result({
              title: "Resultado indeterminado por celulas tumorales aisladas",
              detail: "No hay otra caracteristica Peters positiva, pero las celulas tumorales aisladas no deben tratarse como ganglios completamente negativos.",
              badge: "incertidumbre nodal",
              score: 0,
              showScore: false,
              severity: "warn",
              metrics: [{ label: "Ganglios", value: "ITC solamente" }],
              notes: ["La salida no asigna una conducta terapeutica automatica."],
            });
          }
          return result({
            title: calculated.met ? "Cumple criterios de Peters" : "No cumple criterios de Peters",
            detail: calculated.met
              ? joinNatural(calculated.positiveFeatures.map((item) => item.label))
              : "No se identificaron ganglios pelvicos metastasicos, margenes positivos ni invasion parametrial.",
            badge: calculated.met ? "Peters positivo" : "Peters negativo",
            score: 0,
            showScore: false,
            severity: calculated.met ? "warn" : "info",
            metrics: [
              { label: "Ganglios", value: values.peters_node_status },
              { label: "Margenes", value: values.peters_margin_status },
              { label: "Parametrio", value: values.peters_parametrium === "yes" ? "invadido" : "sin invasion" },
            ],
            notes: [
              calculated.nodalUncertainty ? "Hay incertidumbre adicional por celulas tumorales aisladas." : "La clasificacion se obtuvo con los tres datos requeridos.",
              "El resultado describe riesgo patologico y no prescribe un esquema adyuvante.",
            ],
          });
        },
      },
      {
        id: "gyne-promise",
        number: "GYN3",
        title: "Endometrio — ProMisE / ESGO 2025",
        category: "ginecologia",
        subtitle: "Clasificación molecular TCGA subrogada y refinamiento NSMP.",
        source: "ProMisE - ESGO/ESTRO/ESP 2025",
        clinicalUse: "Integra POLE, MMR y p53 con la jerarquia actual. Para tumores NSMP puede agregar el refinamiento por grado y receptor de estrogeno.",
        fields: [
          section("promise_core_section", "Clasificadores moleculares obligatorios", {
            help: "Use la interpretacion validada del informe molecular o inmunohistoquimico; no infiera p53 desde un porcentaje aislado.",
          }),
          select("promise_pole", "POLE - dominio exonucleasa", [
            option("pathogenic", "Mutacion patogenica"),
            option("non_pathogenic", "Sin mutacion patogenica"),
            option("vus", "Variante de significado incierto (VUS)"),
          ]),
          select("promise_mmr", "MMR", [
            option("deficient", "Deficiente (MMRd)"),
            option("proficient", "Competente (MMRp)"),
          ]),
          select("promise_p53", "p53 / TP53", [
            option("abnormal", "Anormal / mutante"),
            option("wild_type", "Patron wild type"),
          ]),
          section("promise_nsmp_section", "Refinamiento ESGO 2025 para NSMP", {
            help: "Opcional para obtener la clase molecular; necesario para subdividir NSMP.",
          }),
          select("promise_grade", "Grado histologico", [
            option("", "No consignado"),
            option("low", "Bajo grado"),
            option("high", "Alto grado"),
          ], { required: false }),
          number("promise_er", "Receptor de estrogeno (%)", {
            min: 0,
            max: 100,
            step: 0.1,
            required: false,
            help: "ESGO 2025 propone 10% como punto de corte para el refinamiento NSMP.",
          }),
        ],
        calculate(values) {
          const calculated = rules.promiseEsgo2025({
            poleStatus: values.promise_pole,
            mmrStatus: values.promise_mmr,
            p53Status: values.promise_p53,
            grade: values.promise_grade,
            erPercent: values.promise_er,
          });
          if (!calculated.valid) return validationResult(calculated, result);
          const refinement = calculated.nsmpRefinement;
          const refinementLabel = !refinement.applicable
            ? "No corresponde"
            : !refinement.valid
              ? "Pendiente"
              : refinement.subgroup === "nsmp_low_grade_er_positive"
                ? "NSMP bajo grado y ER positivo"
                : "NSMP alto grado o ER negativo";
          const refinementNotes = refinement.applicable && !refinement.valid
            ? [`Para completar el refinamiento NSMP falta: ${refinement.missing.map(readableField).join(", ")}.`]
            : [];
          return result({
            title: `Clase molecular: ${calculated.molecularClass}`,
            detail: calculated.multipleClassifier
              ? `Clasificador multiple resuelto por jerarquia: ${calculated.detectedFeatures.join(" + ")}.`
              : "Clasificacion obtenida mediante la jerarquia POLEmut, MMRd, p53abn y NSMP.",
            badge: calculated.molecularClass,
            score: 0,
            showScore: false,
            severity: "info",
            metrics: [
              { label: "Clase", value: calculated.molecularClass },
              { label: "Rasgos detectados", value: calculated.detectedFeatures.join(" + ") || "ninguno de los tres" },
              { label: "Clasificador multiple", value: calculated.multipleClassifier ? "si" : "no" },
              { label: "Refinamiento NSMP", value: refinementLabel },
            ],
            notes: [
              ...(calculated.warnings || []),
              ...refinementNotes,
              "La clase molecular no reemplaza el estadio FIGO ni define por si sola un tratamiento.",
            ],
          });
        },
      },
      {
        id: "gyne-rmi-i",
        number: "GYN4",
        title: "Masa anexial — RMI I",
        category: "ginecologia",
        subtitle: "CA 125, menopausia y cinco hallazgos ecográficos.",
        source: "Jacobs RMI I - NICE CG122 actualizado 2026",
        clinicalUse: "Calcula el Risk of Malignancy Index I para triage preoperatorio de una masa anexial. Muestra por separado el umbral NICE 250 y el umbral historico 200.",
        fields: [
          number("rmi_ca125", "CA 125 (IU/ml)", { min: 0, step: 0.1 }),
          select("rmi_menopause", "Estado menopausico", [
            option("premenopausal", "Premenopausia"),
            option("postmenopausal", "Posmenopausia"),
          ], { help: "NICE: mas de un ano sin menstruacion o mayor de 50 anos luego de histerectomia." }),
          section("rmi_ultrasound_section", "Ecografia - marque cada hallazgo presente", {
            help: "Una casilla sin marcar se registra como hallazgo ausente. U=0 sin hallazgos, U=1 con uno y U=3 con dos o mas.",
          }),
          checkbox("rmi_multilocular", "Quiste multilocular"),
          checkbox("rmi_solid", "Areas solidas"),
          checkbox("rmi_metastases", "Metastasis"),
          checkbox("rmi_ascites", "Ascitis"),
          checkbox("rmi_bilateral", "Lesiones bilaterales"),
        ],
        calculate(values) {
          const calculated = rules.rmiI({
            ca125: values.rmi_ca125,
            menopausalStatus: values.rmi_menopause,
            ultrasound: {
              multilocular: values.rmi_multilocular,
              solidAreas: values.rmi_solid,
              metastases: values.rmi_metastases,
              ascites: values.rmi_ascites,
              bilateral: values.rmi_bilateral,
            },
          });
          if (!calculated.valid) return validationResult(calculated, result);
          return result({
            title: `RMI I: ${formatNumber(calculated.score)}`,
            detail: `U ${calculated.ultrasoundMultiplier} x M ${calculated.menopausalMultiplier} x CA 125 ${formatNumber(values.rmi_ca125)}.`,
            badge: calculated.thresholds.nice.met ? "en o sobre umbral NICE 250" : "debajo de umbral NICE 250",
            score: 0,
            showScore: false,
            severity: calculated.thresholds.nice.met ? "warn" : "info",
            metrics: [
              { label: "RMI I", value: formatNumber(calculated.score) },
              { label: "Hallazgos ecograficos", value: calculated.ultrasoundFeatureCount },
              { label: "Umbral NICE 250", value: calculated.thresholds.nice.met ? "alcanzado" : "no alcanzado" },
              { label: "Umbral historico 200", value: calculated.thresholds.historical.met ? "alcanzado" : "no alcanzado" },
            ],
            notes: [
              "El umbral vigente mostrado es el de NICE; otros sistemas pueden utilizar un punto de corte distinto.",
              "RMI I es una herramienta de triage preoperatorio y no confirma ni excluye malignidad.",
            ],
          });
        },
      },
      {
        id: "gyne-fagotti",
        number: "GYN5",
        title: "Ovario — Fagotti PIV clásico",
        category: "ginecologia",
        subtitle: "Siete parámetros laparoscópicos del modelo de 2006.",
        source: "Fagotti 2006 - PIV clasico",
        clinicalUse: "Suma dos puntos por cada definicion laparoscopica presente. Mantiene separado el modelo clasico de las versiones modificadas posteriores.",
        fields: [
          section("fagotti_section", "Evaluacion laparoscopica", {
            help: "Marque solamente cuando se cumpla la definicion completa de 2 puntos. Sin marcar equivale a 0 puntos.",
          }),
          checkbox("fagotti_peritoneal", "Peritoneo: compromiso masivo o patron miliar", {
            help: "No marcar para enfermedad limitada removible por peritonectomia.",
          }),
          checkbox("fagotti_diaphragm", "Diafragma: infiltracion extensa o nodulos confluentes", {
            help: "Debe comprometer la mayor parte de la superficie diafragmatica.",
          }),
          checkbox("fagotti_mesentery", "Mesenterio: grandes nodulos o raiz comprometida", {
            help: "Incluye limitacion de la movilidad de segmentos intestinales.",
          }),
          checkbox("fagotti_omentum", "Omento: enfermedad hasta la curvatura mayor gastrica"),
          checkbox("fagotti_bowel", "Intestino: reseccion prevista o enfermedad serosa extensa"),
          checkbox("fagotti_stomach", "Estomago: compromiso evidente de la pared"),
          number("fagotti_liver", "Mayor lesion superficial hepatica (cm)", {
            min: 0,
            step: 0.1,
            help: "Ingrese 0 si no existe. En el modelo clasico suma 2 solamente si es mayor de 2 cm.",
          }),
        ],
        calculate(values) {
          const calculated = rules.fagotti2006({
            peritonealMassiveOrMiliary: values.fagotti_peritoneal,
            diaphragmWidespreadOrConfluent: values.fagotti_diaphragm,
            mesenteryLargeNodulesOrRoot: values.fagotti_mesentery,
            omentumToGreaterCurvature: values.fagotti_omentum,
            bowelResectionExpectedOrExtensiveSerosalDisease: values.fagotti_bowel,
            gastricWallInvolvement: values.fagotti_stomach,
            largestLiverSurfaceLesionCm: values.fagotti_liver,
          });
          if (!calculated.valid) return validationResult(calculated, result);
          return result({
            title: `Fagotti PIV: ${calculated.score} / 14`,
            detail: calculated.legacyThresholdMet
              ? "Alcanza el umbral historico PIV >=8."
              : "No alcanza el umbral historico PIV >=8.",
            badge: calculated.legacyThresholdMet ? "PIV >=8" : "PIV <8",
            score: 0,
            showScore: false,
            severity: calculated.legacyThresholdMet ? "warn" : "info",
            metrics: [
              { label: "Puntaje", value: `${calculated.score} / 14` },
              { label: "Parametros con 2 puntos", value: calculated.positiveFeatures.length },
              { label: "Lesion hepatica", value: `${formatNumber(values.fagotti_liver)} cm` },
              { label: "Umbral clasico", value: calculated.legacyThresholdMet ? "alcanzado" : "no alcanzado" },
            ],
            notes: calculated.notes,
          });
        },
      },
      {
        id: "gyne-ago-desktop",
        number: "GYN6",
        title: "Ovario recurrente — AGO / DESKTOP III",
        category: "ginecologia",
        subtitle: "Selección reproducible de la población del ensayo DESKTOP III.",
        source: "AGO score - DESKTOP III",
        clinicalUse: "Evalua el contexto de primera recaida con intervalo libre de platino de al menos seis meses y los tres componentes del AGO score.",
        fields: [
          section("ago_population_section", "Poblacion de DESKTOP III", {
            help: "Primero confirme primera recaida e intervalo libre de platino >=6 meses.",
          }),
          select("ago_first_relapse", "Es la primera recaida", yesNoOptions),
          number("ago_platinum_interval", "Intervalo libre de platino (meses)", { min: 0, step: 0.1 }),
          section("ago_score_section", "Tres componentes del AGO score", {
            help: "AGO positivo requiere simultaneamente ECOG 0, ascitis <500 ml y reseccion macroscópica completa inicial.",
          }),
          select("ago_ecog", "ECOG actual", [
            option("", "No consignado"),
            option("0", "ECOG 0"),
            option("1", "ECOG 1"),
            option("2", "ECOG 2"),
            option("3", "ECOG 3"),
            option("4", "ECOG 4"),
          ], { required: false }),
          number("ago_ascites", "Ascitis (ml)", { min: 0, step: 1, required: false }),
          select("ago_initial_resection", "Reseccion macroscópica completa en cirugia inicial", [
            option("", "No consignado"),
            ...yesNoOptions,
          ], { required: false }),
        ],
        calculate(values) {
          const calculated = rules.agoDesktopIII({
            firstRelapse: yesNo(values.ago_first_relapse),
            platinumFreeIntervalMonths: values.ago_platinum_interval,
            ecog: values.ago_ecog,
            ascitesMl: values.ago_ascites,
            completeResectionInitialSurgery: yesNo(values.ago_initial_resection),
          });
          if (!calculated.valid) return validationResult(calculated, result);
          if (!calculated.applicable) {
            return result({
              title: "Fuera de la poblacion DESKTOP III",
              detail: joinNatural(calculated.reasons || []),
              badge: "AGO no aplicable",
              score: 0,
              showScore: false,
              severity: "warn",
              metrics: [],
              notes: ["No se extrapola el AGO score fuera del contexto en que se valido."],
            });
          }
          return result({
            title: calculated.positive ? "AGO score positivo" : "AGO score negativo",
            detail: calculated.positive
              ? "Se cumplen simultaneamente los tres componentes publicados."
              : "No se cumplen simultaneamente los tres componentes publicados.",
            badge: calculated.positive ? "AGO positivo" : "AGO negativo",
            score: 0,
            showScore: false,
            severity: calculated.positive ? "info" : "warn",
            metrics: [
              { label: "ECOG 0", value: calculated.components.ecogZero ? "si" : "no" },
              { label: "Ascitis <500 ml", value: calculated.components.ascitesBelow500Ml ? "si" : "no" },
              { label: "Reseccion inicial completa", value: calculated.components.completeResectionInitialSurgery ? "si" : "no" },
              { label: "Intervalo libre de platino", value: `${formatNumber(calculated.context.platinumFreeIntervalMonths)} meses` },
            ],
            notes: [
              ...(calculated.notes || []),
              "El score no indica automaticamente cirugia ni sustituye imagenes, resecabilidad tecnica y evaluacion multidisciplinaria.",
            ],
          });
        },
      },
    ];
  }

  return createOncologyToolsGyne;
});
