(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory;
  root.createOncologyToolsGiThorax = factory;
})(typeof globalThis !== "undefined" ? globalThis : this, function createOncologyToolsGiThorax(helpers) {
  "use strict";

  if (!helpers || typeof helpers !== "object") {
    throw new TypeError("createOncologyToolsGiThorax requiere un objeto helpers");
  }

  const { number, select, checkbox, section, option, result, rules } = helpers;
  const helperFunctions = { number, select, checkbox, section, option, result };
  for (const [name, helper] of Object.entries(helperFunctions)) {
    if (typeof helper !== "function") throw new TypeError(`Falta helpers.${name}`);
  }
  if (!rules || typeof rules !== "object") throw new TypeError("Falta helpers.rules");

  const requiredRules = [
    "brock",
    "mayoHerder",
    "lungGpa2022",
    "lipi",
    "albi",
    "frenchAfpHcc",
    "game",
    "pci",
  ];
  for (const name of requiredRules) {
    if (typeof rules[name] !== "function") throw new TypeError(`Falta helpers.rules.${name}`);
  }

  const yesNoOptions = [option("no", "No"), option("yes", "Si")];
  const biomarkerStatusOptions = [
    option("positive", "Positivo"),
    option("negative", "Negativo"),
    option("unknown", "Desconocido / no estudiado"),
  ];
  const kpsOptions = [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0]
    .map((value) => option(String(value), `KPS ${value}`));

  const asNumber = (value) => {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : Number.NaN;
  };
  const isYes = (value) => value === "yes";
  const fixed = (value, digits = 1) => Number(value).toFixed(digits);
  const percent = (value, digits = 1) => `${fixed(value, digits)}%`;

  function invalidOutput(label, calculated) {
    const messages = Array.isArray(calculated?.errors)
      ? calculated.errors.map((item) => item.message || item.field).filter(Boolean)
      : [];
    return result({
      title: `${label}: no calculable`,
      detail: messages.length ? messages.join("; ") : "Revisar los datos ingresados.",
      badge: "datos invalidos",
      score: 0,
      showScore: false,
      severity: "warn",
      metrics: [],
      notes: Array.isArray(calculated?.warnings) ? calculated.warnings : [],
    });
  }

  function appendWarnings(notes, calculated) {
    return [...notes, ...(Array.isArray(calculated?.warnings) ? calculated.warnings : [])];
  }

  function prognosticSeverity(category) {
    return category === "good" || category === "low" ? "good"
      : category === "intermediate" ? "warn"
        : category === "poor" || category === "high" ? "bad"
          : "info";
  }

  function gpaHistologyLabel(histology) {
    return histology === "adenocarcinoma" ? "NSCLC adenocarcinoma"
      : histology === "non_adenocarcinoma" ? "NSCLC no adenocarcinoma"
        : "SCLC";
  }

  function gpaIqrLabel(values) {
    if (!Array.isArray(values) || values.length !== 2) return "No informado";
    return `${values[0]}-${values[1] === null ? "no alcanzado" : values[1]} meses`;
  }

  const pciNames = [
    "0 - Central",
    "1 - Superior derecha",
    "2 - Epigastrio",
    "3 - Superior izquierda",
    "4 - Flanco izquierdo",
    "5 - Inferior izquierda",
    "6 - Pelvis",
    "7 - Inferior derecha",
    "8 - Flanco derecho",
    "9 - Yeyuno superior",
    "10 - Yeyuno inferior",
    "11 - Ileon superior",
    "12 - Ileon inferior",
  ];
  const pciOptions = [
    option("0", "LS0 - sin tumor visible"),
    option("1", "LS1 - implante de hasta 0,5 cm"),
    option("2", "LS2 - mayor de 0,5 y hasta 5 cm"),
    option("3", "LS3 - mayor de 5 cm o confluente"),
  ];

  return [
    {
      id: "thorax_brock",
      number: "P1",
      title: "Brock / PanCan — nódulo pulmonar",
      category: "pulmon",
      subtitle: "Probabilidad de malignidad por datos clínicos y TC.",
      source: "McWilliams et al., NEJM 2013 - modelo Brock completo",
      clinicalUse: "Estima la probabilidad de malignidad de un nodulo pulmonar detectado por TC mediante la version completa de Brock/PanCan, incluida la espiculacion.",
      fields: [
        section("brock_patient_section", "Paciente", { help: "Completar cada dato de forma explicita." }),
        number("brock_age", "Edad (anos)", { value: 62, min: 18, max: 120, step: 1 }),
        select("brock_sex", "Sexo", [option("male", "Masculino"), option("female", "Femenino")]),
        select("brock_family_history", "Antecedente familiar de cancer pulmonar", yesNoOptions),
        select("brock_emphysema", "Enfisema en TC", yesNoOptions),
        section("brock_nodule_section", "Nodulo y TC", { help: "Diametro maximo y caracteristicas del estudio basal." }),
        number("brock_diameter", "Diametro maximo (mm)", { value: 8, min: 0.1, max: 30, step: 0.1 }),
        select("brock_type", "Tipo de nodulo", [
          option("solid", "Solido"),
          option("part_solid", "Parcialmente solido"),
          option("ground_glass", "No solido / vidrio esmerilado"),
        ]),
        select("brock_upper_lobe", "Ubicacion en lobulo superior", yesNoOptions),
        number("brock_nodule_count", "Numero total de nodulos", { value: 1, min: 1, step: 1 }),
        select("brock_spiculation", "Espiculacion", yesNoOptions),
      ],
      calculate(values) {
        const calculated = rules.brock({
          ageYears: asNumber(values.brock_age),
          female: values.brock_sex === "female",
          familyHistoryLungCancer: isYes(values.brock_family_history),
          emphysema: isYes(values.brock_emphysema),
          diameterMm: asNumber(values.brock_diameter),
          noduleType: values.brock_type,
          upperLobe: isYes(values.brock_upper_lobe),
          noduleCount: asNumber(values.brock_nodule_count),
          spiculation: isYes(values.brock_spiculation),
        });
        if (!calculated.valid) return invalidOutput("Brock", calculated);
        return result({
          title: percent(calculated.probabilityPercent, 1),
          detail: "Probabilidad modelada de malignidad para el nodulo evaluado.",
          badge: "Brock completo",
          score: 0,
          showScore: false,
          severity: "info",
          metrics: [
            { label: "Probabilidad", value: percent(calculated.probabilityPercent, 2) },
            { label: "Predictor lineal", value: fixed(calculated.linearPredictor, 4) },
            { label: "Diametro", value: `${fixed(calculated.inputs.diameterMm, 1)} mm` },
            { label: "Tipo", value: calculated.inputs.noduleType.replace(/_/g, " ") },
          ],
          notes: appendWarnings([
            "Modelo de screening: su calibracion puede cambiar en nodulos incidentales o poblaciones con otra prevalencia.",
            "La probabilidad no equivale a confirmacion histologica ni compara alternativas de manejo.",
          ], calculated),
        });
      },
    },
    {
      id: "thorax_mayo_herder",
      number: "P2",
      title: "Mayo-Herder con PET-FDG",
      category: "pulmon",
      subtitle: "Probabilidad pretest Mayo refinada por captación PET.",
      source: "Swensen 1997; Herder et al., Chest 2005",
      clinicalUse: "Calcula primero la probabilidad Mayo de un nodulo pulmonar solitario y luego la actualiza con la categoria ordinal de captacion de FDG del modelo Herder.",
      fields: [
        section("herder_patient_section", "Paciente y antecedentes"),
        number("herder_age", "Edad (anos)", { value: 65, min: 18, max: 120, step: 1 }),
        select("herder_smoker", "Fumador actual o previo", yesNoOptions),
        select("herder_prior_cancer", "Cancer extratoracico diagnosticado hace mas de 5 anos", yesNoOptions),
        section("herder_nodule_section", "Nodulo y PET-FDG"),
        number("herder_diameter", "Diametro maximo (mm)", { value: 12, min: 4, max: 30, step: 0.1 }),
        select("herder_spiculation", "Espiculacion", yesNoOptions),
        select("herder_upper_lobe", "Ubicacion en lobulo superior", yesNoOptions),
        select("herder_pet", "Captacion FDG", [
          option("absent", "Ausente - indistinguible del pulmon de fondo"),
          option("faint", "Tenue - menor o igual al pool mediastinal"),
          option("moderate", "Moderada - mayor al pool mediastinal"),
          option("intense", "Intensa - marcadamente mayor al pool mediastinal"),
        ], { wide: true }),
      ],
      calculate(values) {
        const calculated = rules.mayoHerder({
          ageYears: asNumber(values.herder_age),
          currentOrFormerSmoker: isYes(values.herder_smoker),
          extrathoracicCancerMoreThan5Years: isYes(values.herder_prior_cancer),
          diameterMm: asNumber(values.herder_diameter),
          spiculation: isYes(values.herder_spiculation),
          upperLobe: isYes(values.herder_upper_lobe),
          petUptake: values.herder_pet,
        });
        if (!calculated.valid) return invalidOutput("Mayo-Herder", calculated);
        return result({
          title: percent(calculated.herder.probabilityPercent, 1),
          detail: "Probabilidad Herder posterior a incorporar la categoria visual de PET-FDG.",
          badge: "Mayo-Herder",
          score: 0,
          showScore: false,
          severity: "info",
          metrics: [
            { label: "Mayo pretest", value: percent(calculated.mayo.probabilityPercent, 2) },
            { label: "Herder con PET", value: percent(calculated.herder.probabilityPercent, 2) },
            { label: "Captacion", value: calculated.inputs.petUptake },
            { label: "Coeficiente PET", value: fixed(calculated.herder.petCoefficient, 3) },
          ],
          notes: appendWarnings([
            "Herder utiliza la probabilidad Mayo como decimal entre 0 y 1, no como porcentaje.",
            "La escala PET es visual; procesos inflamatorios y granulomatosos pueden alterar la especificidad.",
            "Resultado probabilistico, no diagnostico ni recomendacion de tratamiento.",
          ], calculated),
        });
      },
    },
    {
      id: "thorax_lung_gpa_2022",
      number: "P3",
      title: "Lung GPA 2022",
      category: "pulmon",
      subtitle: "Pronóstico en metástasis cerebrales de cáncer pulmonar.",
      source: "Sperduto et al., Int J Radiat Oncol Biol Phys 2022",
      clinicalUse: "Estratifica el pronostico desde el diagnostico inicial de metastasis cerebrales mediante una hoja especifica para adenocarcinoma, otros NSCLC o SCLC.",
      fields: [
        select("scenario", "Histologia", [
          option("adenocarcinoma", "NSCLC adenocarcinoma"),
          option("non_adenocarcinoma", "NSCLC no adenocarcinoma"),
          option("sclc", "Cancer pulmonar de celulas pequenas (SCLC)"),
        ], { value: "adenocarcinoma", wide: true, help: "Cada histologia utiliza ponderaciones diferentes." }),
        section("lung_gpa_common_section", "Datos al diagnostico de metastasis cerebrales"),
        number("lung_gpa_age", "Edad (anos)", { value: 65, min: 18, max: 120, step: 1 }),
        select("lung_gpa_kps", "Karnofsky (KPS)", kpsOptions),
        number("lung_gpa_brain_count", "Numero de metastasis cerebrales", { value: 1, min: 1, step: 1 }),
        select("lung_gpa_ecm", "Metastasis extracraneales presentes", yesNoOptions),
        section("lung_gpa_biomarker_section", "Biomarcadores del adenocarcinoma", {
          scenario: "adenocarcinoma",
          help: "Desconocido puntua 0 en la definicion publicada; debe registrarse de forma explicita.",
        }),
        select("lung_gpa_egfr", "EGFR", biomarkerStatusOptions, { scenario: "adenocarcinoma" }),
        select("lung_gpa_alk", "ALK", biomarkerStatusOptions, { scenario: "adenocarcinoma" }),
        select("lung_gpa_pdl1", "PD-L1 (positivo si es mayor o igual a 1%)", biomarkerStatusOptions, { scenario: "adenocarcinoma" }),
      ],
      calculate(values) {
        const histology = values.scenario;
        const calculated = rules.lungGpa2022({
          histology,
          ageYears: asNumber(values.lung_gpa_age),
          kps: asNumber(values.lung_gpa_kps),
          brainMetastases: asNumber(values.lung_gpa_brain_count),
          extracranialMetastases: isYes(values.lung_gpa_ecm),
          ...(histology === "adenocarcinoma" ? {
            egfrStatus: values.lung_gpa_egfr,
            alkStatus: values.lung_gpa_alk,
            pdl1Status: values.lung_gpa_pdl1,
          } : {}),
        });
        if (!calculated.valid) return invalidOutput("Lung GPA 2022", calculated);
        const severity = calculated.total <= 1 ? "bad" : calculated.total <= 2 ? "warn" : "good";
        return result({
          title: `Lung GPA ${fixed(calculated.total, 1)}`,
          detail: `${gpaHistologyLabel(calculated.histology)} - banda ${calculated.prognosticBand}.`,
          badge: "pronostico 2022",
          score: 0,
          showScore: false,
          severity,
          metrics: [
            { label: "Puntaje", value: fixed(calculated.total, 1) },
            { label: "Banda", value: calculated.prognosticBand },
            { label: "Mediana OS de cohorte", value: `${calculated.medianOverallSurvivalMonths} meses` },
            { label: "Rango intercuartil", value: gpaIqrLabel(calculated.interquartileRangeMonths) },
          ],
          notes: appendWarnings([
            "Las supervivencias corresponden a cohortes y no son una prediccion individual exacta.",
            "Aplicable al diagnostico inicial de metastasis cerebrales; la cohorte excluyo recurrencia cerebral y carcinomatosis leptomeningea.",
            "Es un indice pronostico y no compara eficacia entre tratamientos.",
          ], calculated),
        });
      },
    },
    {
      id: "thorax_lipi",
      number: "P4",
      title: "LIPI",
      category: "pulmon",
      subtitle: "Índice pronóstico pulmonar por dNLR y LDH.",
      source: "Mezquita et al., JAMA Oncology 2018",
      clinicalUse: "Calcula el Lung Immune Prognostic Index basal en NSCLC avanzado a partir de hemograma y LDH previos al tratamiento.",
      fields: [
        section("lipi_labs_section", "Laboratorio basal", { help: "Leucocitos y neutrofilos deben usar las mismas unidades." }),
        number("lipi_wbc", "Leucocitos totales", { value: 7, min: 0.001, step: 0.01 }),
        number("lipi_anc", "Neutrofilos absolutos", { value: 4, min: 0, step: 0.01 }),
        number("lipi_ldh", "LDH", { value: 200, min: 0.001, step: 0.1 }),
        number("lipi_ldh_uln", "Limite superior normal de LDH", { value: 250, min: 0.001, step: 0.1 }),
      ],
      calculate(values) {
        const calculated = rules.lipi({
          whiteBloodCells: asNumber(values.lipi_wbc),
          absoluteNeutrophils: asNumber(values.lipi_anc),
          ldh: asNumber(values.lipi_ldh),
          ldhUpperLimitNormal: asNumber(values.lipi_ldh_uln),
        });
        if (!calculated.valid) return invalidOutput("LIPI", calculated);
        const labels = { good: "bueno", intermediate: "intermedio", poor: "pobre" };
        return result({
          title: `LIPI ${calculated.total} - ${labels[calculated.category]}`,
          detail: "Indice pronostico compuesto por dNLR mayor de 3 y LDH por encima del limite normal.",
          badge: "LIPI",
          score: 0,
          showScore: false,
          severity: prognosticSeverity(calculated.category),
          metrics: [
            { label: "Puntaje", value: calculated.total },
            { label: "dNLR", value: fixed(calculated.derivedNeutrophilLymphocyteRatio, 2) },
            { label: "dNLR >3", value: calculated.components.dnlrAbove3 ? "Si" : "No" },
            { label: "LDH >LSN", value: calculated.components.ldhAboveUpperLimitNormal ? "Si" : "No" },
          ],
          notes: appendWarnings([
            "Infeccion, inflamacion aguda, corticoides o factores estimulantes pueden modificar los componentes.",
            "No usar el LIPI como prueba aislada de respuesta ni como selector de tratamiento.",
          ], calculated),
        });
      },
    },
    {
      id: "digestive_albi",
      number: "D1",
      title: "ALBI / mALBI",
      category: "digestivo",
      subtitle: "Reserva hepática objetiva por albúmina y bilirrubina.",
      source: "Johnson et al., Journal of Clinical Oncology 2015",
      clinicalUse: "Cuantifica funcion hepatica en pacientes con hepatocarcinoma mediante albumina y bilirrubina, sin variables clinicas subjetivas.",
      fields: [
        section("albi_labs_section", "Laboratorio", { help: "Seleccionar las unidades exactas del informe." }),
        number("albi_bilirubin", "Bilirrubina total", { value: 1, min: 0.001, step: 0.01 }),
        select("albi_bilirubin_unit", "Unidad de bilirrubina", [
          option("mg/dL", "mg/dL"),
          option("umol/L", "umol/L"),
        ], { value: "mg/dL", keepDefault: true }),
        number("albi_albumin", "Albumina", { value: 4, min: 0.001, step: 0.01 }),
        select("albi_albumin_unit", "Unidad de albumina", [
          option("g/dL", "g/dL"),
          option("g/L", "g/L"),
        ], { value: "g/dL", keepDefault: true }),
      ],
      calculate(values) {
        const calculated = rules.albi({
          bilirubin: asNumber(values.albi_bilirubin),
          bilirubinUnit: values.albi_bilirubin_unit,
          albumin: asNumber(values.albi_albumin),
          albuminUnit: values.albi_albumin_unit,
        });
        if (!calculated.valid) return invalidOutput("ALBI", calculated);
        return result({
          title: `ALBI grado ${calculated.grade}`,
          detail: `Puntaje continuo ${fixed(calculated.score, 3)}.`,
          badge: "funcion hepatica",
          score: 0,
          showScore: false,
          severity: calculated.grade === 1 ? "good" : calculated.grade === 2 ? "warn" : "bad",
          metrics: [
            { label: "ALBI", value: fixed(calculated.score, 3) },
            { label: "Grado", value: calculated.grade },
            { label: "mALBI", value: calculated.modifiedGrade },
            { label: "Bilirrubina usada", value: `${fixed(calculated.inputs.bilirubinMicromolL, 2)} umol/L` },
            { label: "Albumina usada", value: `${fixed(calculated.inputs.albuminGL, 2)} g/L` },
          ],
          notes: appendWarnings([
            "ALBI: grado 1 ≤-2,60; grado 2 >-2,60 a ≤-1,39; grado 3 >-1,39. mALBI divide grado 2 en 2a ≤-2,27 y 2b >-2,27.",
            "No incorpora ascitis, encefalopatia, hipertension portal ni volumen hepatico remanente.",
            "Describe reserva hepatica; no determina por si solo una conducta oncologica.",
          ], calculated),
        });
      },
    },
    {
      id: "digestive_french_afp_hcc",
      number: "D2",
      title: "AFP francés para trasplante en HCC",
      category: "digestivo",
      subtitle: "Carga tumoral y AFP en candidatos con hepatocarcinoma.",
      source: "Duvoux et al., Gastroenterology 2012",
      clinicalUse: "Estratifica el riesgo de recurrencia postrasplante en hepatocarcinoma con diametro tumoral, numero de nodulos y AFP.",
      fields: [
        section("afp_hcc_section", "Evaluacion pretrasplante", { help: "Usar imagen y AFP de la misma evaluacion clinica." }),
        number("afp_hcc_diameter", "Mayor diametro tumoral (cm)", { value: 3, min: 0.01, step: 0.1 }),
        number("afp_hcc_nodules", "Numero de nodulos HCC", { value: 1, min: 1, step: 1 }),
        number("afp_hcc_value", "AFP (ng/mL)", { value: 100, min: 0, step: 0.1 }),
      ],
      calculate(values) {
        const calculated = rules.frenchAfpHcc({
          largestTumorDiameterCm: asNumber(values.afp_hcc_diameter),
          noduleCount: asNumber(values.afp_hcc_nodules),
          afpNgMl: asNumber(values.afp_hcc_value),
        });
        if (!calculated.valid) return invalidOutput("AFP frances HCC", calculated);
        const label = calculated.category === "low" ? "menor riesgo segun el modelo" : "mayor riesgo segun el modelo";
        return result({
          title: `AFP score ${calculated.total}`,
          detail: label,
          badge: "HCC pretrasplante",
          score: 0,
          showScore: false,
          severity: calculated.category === "low" ? "good" : "bad",
          metrics: [
            { label: "Puntaje total", value: calculated.total },
            { label: "Diametro", value: calculated.components.diameterPoints },
            { label: "Numero de nodulos", value: calculated.components.nodulePoints },
            { label: "AFP", value: calculated.components.afpPoints },
          ],
          notes: appendWarnings([
            "El umbral publicado separa puntaje menor o igual a 2 de puntaje mayor de 2.",
            "Es un modelo de recurrencia postrasplante y no una estadificacion general del HCC.",
            "No incorpora por si solo invasion macrovascular, enfermedad extrahepatica ni criterios administrativos locales.",
          ], calculated),
        });
      },
    },
    {
      id: "digestive_game",
      number: "D3",
      title: "GAME — metástasis hepáticas colorrectales",
      category: "digestivo",
      subtitle: "Evaluación genética y morfológica preoperatoria.",
      source: "Margonis, Sasaki et al., British Journal of Surgery 2018",
      clinicalUse: "Estratifica el pronostico preoperatorio de metastasis hepaticas colorrectales con KRAS, CEA, ganglios del primario, carga hepatica y enfermedad extrahepatica.",
      fields: [
        section("game_biology_section", "Biologia y tumor primario"),
        select("game_kras", "KRAS", [option("wild_type", "Wild-type"), option("mutated", "Mutado")]),
        number("game_cea", "CEA preoperatorio (ng/mL)", { value: 10, min: 0, step: 0.1 }),
        select("game_node_positive", "Primario con ganglios positivos", yesNoOptions),
        section("game_burden_section", "Carga de metastasis", { help: "TBS = raiz cuadrada de diametro maximo al cuadrado mas numero de metastasis al cuadrado." }),
        number("game_largest_met", "Mayor metastasis hepatica (cm)", { value: 2, min: 0.01, step: 0.1 }),
        number("game_met_count", "Numero de metastasis hepaticas", { value: 1, min: 1, step: 1 }),
        select("game_extrahepatic", "Enfermedad extrahepatica", yesNoOptions),
      ],
      calculate(values) {
        const calculated = rules.game({
          krasStatus: values.game_kras,
          ceaNgMl: asNumber(values.game_cea),
          primaryNodePositive: isYes(values.game_node_positive),
          largestLiverMetastasisCm: asNumber(values.game_largest_met),
          liverMetastasisCount: asNumber(values.game_met_count),
          extrahepaticDisease: isYes(values.game_extrahepatic),
        });
        if (!calculated.valid) return invalidOutput("GAME", calculated);
        const labels = { low: "bajo", intermediate: "intermedio", high: "alto" };
        return result({
          title: `GAME ${calculated.total} - ${labels[calculated.category]}`,
          detail: "Estrato pronostico preoperatorio del modelo GAME.",
          badge: "CRLM",
          score: 0,
          showScore: false,
          severity: prognosticSeverity(calculated.category),
          metrics: [
            { label: "Puntaje", value: calculated.total },
            { label: "TBS", value: fixed(calculated.tumorBurdenScore, 2) },
            { label: "KRAS", value: calculated.components.krasPoints },
            { label: "Extrahepatica", value: calculated.components.extrahepaticPoints },
          ],
          notes: appendWarnings([
            "Grupos publicados: 0-1 bajo, 2-3 intermedio y 4 o mas alto.",
            "El modelo utiliza KRAS especificamente; no sustituirlo automaticamente por un resultado RAS agregado.",
            "El puntaje describe pronostico y no define resecabilidad ni tratamiento.",
          ], calculated),
        });
      },
    },
    {
      id: "digestive_pci",
      number: "D4",
      title: "Índice de cáncer peritoneal (PCI)",
      category: "digestivo",
      subtitle: "Carga peritoneal por 13 regiones de Sugarbaker.",
      source: "Jacquet y Sugarbaker, 1996",
      clinicalUse: "Cuantifica la distribucion y el tamano de implantes peritoneales en nueve regiones abdominopelvicas y cuatro segmentos de intestino delgado.",
      fields: [
        section("pci_abdominopelvic_section", "Regiones abdominopelvicas 0-8", {
          help: "Seleccionar el implante mayor de cada region. LS3 tambien incluye enfermedad confluente.",
        }),
        ...pciNames.slice(0, 9).map((label, index) => select(`pci_region_${index}`, label, pciOptions)),
        section("pci_small_bowel_section", "Intestino delgado 9-12"),
        ...pciNames.slice(9).map((label, offset) => select(`pci_region_${offset + 9}`, label, pciOptions)),
      ],
      calculate(values) {
        const regions = pciNames.map((_, index) => ({
          lesionSizeScore: asNumber(values[`pci_region_${index}`]),
        }));
        const calculated = rules.pci({ regions });
        if (!calculated.valid) return invalidOutput("PCI", calculated);
        const involved = calculated.regions.filter((item) => item.lesionSizeScore > 0);
        const highBurdenRegions = calculated.regions.filter((item) => item.lesionSizeScore === 3);
        return result({
          title: `PCI ${calculated.total} / 39`,
          detail: "Suma de los puntajes LS0-LS3 de las 13 regiones.",
          badge: "Sugarbaker PCI",
          score: 0,
          showScore: false,
          severity: "info",
          metrics: [
            { label: "PCI", value: `${calculated.total}/39` },
            { label: "Regiones comprometidas", value: involved.length },
            { label: "Regiones LS3", value: highBurdenRegions.length },
            { label: "Regiones evaluadas", value: calculated.regions.length },
          ],
          notes: [
            involved.length
              ? `Compromiso registrado: ${involved.map((item) => `${item.id} ${item.label} (LS${item.lesionSizeScore})`).join("; ")}.`
              : "No se registraron implantes visibles en las 13 regiones.",
            "El PCI cuantifica carga peritoneal; no existe un corte universal aplicable a todas las histologias o centros.",
            "La estimacion radiologica puede diferir de la evaluacion laparoscopica o intraoperatoria.",
          ],
        });
      },
    },
  ];
});
