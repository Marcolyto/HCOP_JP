(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.OncologyRulesGyne = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const RULES = Object.freeze({
    sedlis: Object.freeze({
      id: "sedlis-gog92",
      name: "Criterios de Sedlis",
      version: "GOG-92 / tabla vigente 2025",
      sourceUrls: Object.freeze([
        "https://pubmed.ncbi.nlm.nih.gov/10329031/",
        "https://www.cancer.va.gov/CANCER/assets/pdf/clinical-pathways/cervical.pdf",
      ]),
    }),
    peters: Object.freeze({
      id: "peters-gog109",
      name: "Criterios de Peters",
      version: "GOG-109 / ESGO-ESTRO-ESP 2023",
      sourceUrls: Object.freeze([
        "https://pubmed.ncbi.nlm.nih.gov/10764420/",
        "https://pmc.ncbi.nlm.nih.gov/articles/PMC10176411/",
      ]),
    }),
    promiseEsgo2025: Object.freeze({
      id: "promise-esgo-2025",
      name: "Clasificacion molecular de endometrio ProMisE/ESGO",
      version: "ESGO-ESTRO-ESP 2025",
      sourceUrls: Object.freeze([
        "https://pubmed.ncbi.nlm.nih.gov/28061006/",
        "https://guidelines.esgo.org/media/2025/09/ESGO-ESTRO-ESP-Guidelines-for-EC_-LO-July-2025.pdf",
      ]),
    }),
    rmiI: Object.freeze({
      id: "rmi-i-nice",
      name: "Risk of Malignancy Index I",
      version: "NICE CG122, actualizacion 2026",
      sourceUrls: Object.freeze([
        "https://pubmed.ncbi.nlm.nih.gov/2223684/",
        "https://www.nice.org.uk/guidance/cg122/chapter/Appendix-Risk-of-malignancy-index-RMI-I",
      ]),
    }),
    fagotti2006: Object.freeze({
      id: "fagotti-piv-2006",
      name: "Fagotti PIV clasico",
      version: "2006, siete parametros",
      sourceUrls: Object.freeze([
        "https://doi.org/10.1245/ASO.2006.08.021",
        "https://pmc.ncbi.nlm.nih.gov/articles/PMC11130284/",
      ]),
    }),
    agoDesktopIII: Object.freeze({
      id: "ago-desktop-iii",
      name: "AGO score / DESKTOP III",
      version: "DESKTOP III 2021",
      sourceUrls: Object.freeze([
        "https://www.nejm.org/doi/full/10.1056/NEJMoa2103294",
        "https://www.esgo.org/media/2025/08/Pocket-Guidelines_Ovarian-cancer-consensus.pdf",
      ]),
    }),
  });

  const NODE_STATUSES = Object.freeze([
    "negative",
    "isolated_tumor_cells",
    "micrometastasis",
    "macrometastasis",
  ]);
  const MARGIN_STATUSES = Object.freeze(["negative", "positive"]);
  const STROMAL_THIRDS = Object.freeze(["superficial", "middle", "deep"]);

  const own = (object, key) => Object.prototype.hasOwnProperty.call(object, key);
  const objectInput = (input) => (
    input && typeof input === "object" && !Array.isArray(input) ? input : {}
  );

  function readBoolean(input, key, missing, errors, displayKey = key) {
    if (!own(input, key) || input[key] === null || input[key] === undefined) {
      missing.push(displayKey);
      return null;
    }
    if (typeof input[key] !== "boolean") {
      errors.push(`${displayKey} debe ser booleano explicito`);
      return null;
    }
    return input[key];
  }

  function readNumber(input, key, missing, errors, options = {}) {
    if (!own(input, key) || input[key] === null || input[key] === undefined || input[key] === "") {
      missing.push(key);
      return null;
    }
    const value = Number(input[key]);
    if (!Number.isFinite(value)) {
      errors.push(`${key} debe ser numerico y finito`);
      return null;
    }
    if (options.integer && !Number.isInteger(value)) errors.push(`${key} debe ser entero`);
    if (options.min !== undefined && value < options.min) errors.push(`${key} debe ser >= ${options.min}`);
    if (options.max !== undefined && value > options.max) errors.push(`${key} debe ser <= ${options.max}`);
    if (options.exclusiveMin !== undefined && value <= options.exclusiveMin) {
      errors.push(`${key} debe ser > ${options.exclusiveMin}`);
    }
    return value;
  }

  function readEnum(input, key, allowed, missing, errors) {
    if (!own(input, key) || input[key] === null || input[key] === undefined || input[key] === "") {
      missing.push(key);
      return null;
    }
    const value = String(input[key]);
    if (!allowed.includes(value)) {
      errors.push(`${key} debe ser uno de: ${allowed.join(", ")}`);
      return null;
    }
    return value;
  }

  function invalid(rule, missing, errors, extra = {}) {
    return {
      valid: false,
      applicable: null,
      classification: "not_calculable",
      missing: Array.from(new Set(missing)),
      errors,
      rule,
      ...extra,
    };
  }

  function readCervicalHighRiskContext(input, missing, errors) {
    return {
      pelvicNodeStatus: readEnum(input, "pelvicNodeStatus", NODE_STATUSES, missing, errors),
      surgicalMarginStatus: readEnum(input, "surgicalMarginStatus", MARGIN_STATUSES, missing, errors),
      parametrialInvasion: readBoolean(input, "parametrialInvasion", missing, errors),
    };
  }

  function sedlis(rawInput = {}) {
    const input = objectInput(rawInput);
    const missing = [];
    const errors = [];
    const context = readCervicalHighRiskContext(input, missing, errors);
    if (missing.length || errors.length) return invalid(RULES.sedlis, missing, errors);

    const exclusionReasons = [];
    if (["micrometastasis", "macrometastasis"].includes(context.pelvicNodeStatus)) {
      exclusionReasons.push("metastasis ganglionar pelvica");
    }
    if (context.surgicalMarginStatus === "positive") exclusionReasons.push("margen quirurgico positivo");
    if (context.parametrialInvasion) exclusionReasons.push("invasion parametrial");
    if (context.pelvicNodeStatus === "isolated_tumor_cells") {
      return {
        valid: true,
        applicable: false,
        met: null,
        classification: "not_applicable_isolated_tumor_cells",
        exclusionReasons: ["celulas tumorales aisladas: significado adyuvante incierto"],
        rule: RULES.sedlis,
      };
    }
    if (exclusionReasons.length) {
      return {
        valid: true,
        applicable: false,
        met: null,
        classification: "not_applicable_high_risk_feature",
        exclusionReasons,
        rule: RULES.sedlis,
      };
    }

    const lvsi = readBoolean(input, "lvsi", missing, errors);
    const stromalInvasion = readEnum(input, "stromalInvasion", STROMAL_THIRDS, missing, errors);
    const tumorSizeCm = readNumber(input, "tumorSizeCm", missing, errors, { exclusiveMin: 0 });
    if (missing.length || errors.length) return invalid(RULES.sedlis, missing, errors, { applicable: true });

    const matchedCriteria = [];
    if (lvsi && stromalInvasion === "deep") {
      matchedCriteria.push({
        id: "lvsi_deep_any_size",
        label: "LVSI positivo, tercio profundo, cualquier tamano",
      });
    }
    if (lvsi && stromalInvasion === "middle" && tumorSizeCm >= 2) {
      matchedCriteria.push({
        id: "lvsi_middle_ge_2cm",
        label: "LVSI positivo, tercio medio, tumor >=2 cm",
      });
    }
    if (lvsi && stromalInvasion === "superficial" && tumorSizeCm >= 5) {
      matchedCriteria.push({
        id: "lvsi_superficial_ge_5cm",
        label: "LVSI positivo, tercio superficial, tumor >=5 cm",
      });
    }
    if (!lvsi && ["middle", "deep"].includes(stromalInvasion) && tumorSizeCm >= 4) {
      matchedCriteria.push({
        id: "no_lvsi_middle_or_deep_ge_4cm",
        label: "LVSI negativo, tercio medio o profundo, tumor >=4 cm",
      });
    }

    const met = matchedCriteria.length > 0;
    const notes = [
      "La regla evalua combinaciones exactas; no cuenta simplemente dos de tres factores.",
      "El tamano de la tabla original fue determinado por palpacion clinica.",
    ];
    if (own(input, "tumorSizeMethod") && input.tumorSizeMethod !== "clinical_palpation") {
      notes.push("El metodo de medicion informado no es la palpacion clinica del modelo original.");
    }
    return {
      valid: true,
      applicable: true,
      met,
      classification: met ? "sedlis_positive" : "sedlis_negative",
      matchedCriteria,
      notes,
      rule: RULES.sedlis,
    };
  }

  function peters(rawInput = {}) {
    const input = objectInput(rawInput);
    const missing = [];
    const errors = [];
    const context = readCervicalHighRiskContext(input, missing, errors);
    if (missing.length || errors.length) return invalid(RULES.peters, missing, errors);

    const positiveFeatures = [];
    if (["micrometastasis", "macrometastasis"].includes(context.pelvicNodeStatus)) {
      positiveFeatures.push({ id: "positive_pelvic_nodes", label: "Ganglio pelvico metastasico" });
    }
    if (context.surgicalMarginStatus === "positive") {
      positiveFeatures.push({ id: "positive_margin", label: "Margen quirurgico positivo" });
    }
    if (context.parametrialInvasion) {
      positiveFeatures.push({ id: "parametrial_invasion", label: "Invasion parametrial" });
    }

    if (positiveFeatures.length) {
      return {
        valid: true,
        applicable: true,
        met: true,
        classification: "peters_positive",
        positiveFeatures,
        nodalUncertainty: context.pelvicNodeStatus === "isolated_tumor_cells",
        rule: RULES.peters,
      };
    }
    if (context.pelvicNodeStatus === "isolated_tumor_cells") {
      return {
        valid: true,
        applicable: true,
        met: null,
        classification: "indeterminate_isolated_tumor_cells",
        positiveFeatures: [],
        nodalUncertainty: true,
        rule: RULES.peters,
      };
    }
    return {
      valid: true,
      applicable: true,
      met: false,
      classification: "peters_negative",
      positiveFeatures: [],
      nodalUncertainty: false,
      rule: RULES.peters,
    };
  }

  function promiseEsgo2025(rawInput = {}) {
    const input = objectInput(rawInput);
    const missing = [];
    const errors = [];
    const poleStatus = readEnum(
      input,
      "poleStatus",
      ["pathogenic", "non_pathogenic", "vus"],
      missing,
      errors,
    );
    const mmrStatus = readEnum(input, "mmrStatus", ["deficient", "proficient"], missing, errors);
    const p53Status = readEnum(input, "p53Status", ["abnormal", "wild_type"], missing, errors);
    if (missing.length || errors.length) return invalid(RULES.promiseEsgo2025, missing, errors);

    const detectedFeatures = [];
    if (poleStatus === "pathogenic") detectedFeatures.push("POLEmut");
    if (mmrStatus === "deficient") detectedFeatures.push("MMRd");
    if (p53Status === "abnormal") detectedFeatures.push("p53abn");

    const molecularClass = poleStatus === "pathogenic"
      ? "POLEmut"
      : mmrStatus === "deficient"
        ? "MMRd"
        : p53Status === "abnormal"
          ? "p53abn"
          : "NSMP";

    const warnings = [];
    if (poleStatus === "vus") {
      warnings.push("Una variante POLE de significado incierto no se clasifica como POLEmut.");
    }

    let nsmpRefinement = { applicable: false, valid: true, subgroup: null, missing: [], errors: [] };
    if (molecularClass === "NSMP") {
      const refinementMissing = [];
      const refinementErrors = [];
      const grade = readEnum(input, "grade", ["low", "high"], refinementMissing, refinementErrors);
      const erPercent = readNumber(input, "erPercent", refinementMissing, refinementErrors, { min: 0, max: 100 });
      const refinementValid = refinementMissing.length === 0 && refinementErrors.length === 0;
      nsmpRefinement = {
        applicable: true,
        valid: refinementValid,
        subgroup: refinementValid
          ? (grade === "low" && erPercent >= 10
            ? "nsmp_low_grade_er_positive"
            : "nsmp_high_grade_or_er_negative")
          : null,
        erPositive: refinementValid ? erPercent >= 10 : null,
        missing: refinementMissing,
        errors: refinementErrors,
      };
    }

    return {
      valid: true,
      applicable: true,
      molecularClass,
      classification: molecularClass,
      detectedFeatures,
      multipleClassifier: detectedFeatures.length > 1,
      nsmpRefinement,
      warnings,
      rule: RULES.promiseEsgo2025,
    };
  }

  function rmiI(rawInput = {}) {
    const input = objectInput(rawInput);
    const missing = [];
    const errors = [];
    const ca125 = readNumber(input, "ca125", missing, errors, { min: 0 });
    const menopausalStatus = readEnum(
      input,
      "menopausalStatus",
      ["premenopausal", "postmenopausal"],
      missing,
      errors,
    );
    const ultrasound = objectInput(input.ultrasound);
    if (!own(input, "ultrasound") || input.ultrasound === null || typeof input.ultrasound !== "object" || Array.isArray(input.ultrasound)) {
      missing.push("ultrasound");
    }
    const ultrasoundFields = ["multilocular", "solidAreas", "metastases", "ascites", "bilateral"];
    const ultrasoundValues = {};
    if (!missing.includes("ultrasound")) {
      for (const field of ultrasoundFields) {
        ultrasoundValues[field] = readBoolean(ultrasound, field, missing, errors, `ultrasound.${field}`);
      }
    }
    if (missing.length || errors.length) return invalid(RULES.rmiI, missing, errors);

    const ultrasoundFeatureCount = ultrasoundFields.reduce(
      (total, field) => total + (ultrasoundValues[field] ? 1 : 0),
      0,
    );
    const ultrasoundMultiplier = ultrasoundFeatureCount === 0 ? 0 : ultrasoundFeatureCount === 1 ? 1 : 3;
    const menopausalMultiplier = menopausalStatus === "postmenopausal" ? 3 : 1;
    const score = ultrasoundMultiplier * menopausalMultiplier * ca125;
    return {
      valid: true,
      applicable: true,
      score,
      classification: score >= 250 ? "at_or_above_nice_250" : "below_nice_250",
      ultrasoundFeatureCount,
      ultrasoundMultiplier,
      menopausalMultiplier,
      thresholds: {
        nice: { value: 250, met: score >= 250 },
        historical: { value: 200, met: score >= 200 },
      },
      rule: RULES.rmiI,
    };
  }

  function fagotti2006(rawInput = {}) {
    const input = objectInput(rawInput);
    const missing = [];
    const errors = [];
    const definitions = [
      ["peritonealMassiveOrMiliary", "peritoneal_carcinomatosis", "Carcinomatosis peritoneal masiva o miliar"],
      ["diaphragmWidespreadOrConfluent", "diaphragmatic_disease", "Enfermedad diafragmatica extensa o confluente"],
      ["mesenteryLargeNodulesOrRoot", "mesenteric_disease", "Grandes nodulos mesentericos o compromiso de la raiz"],
      ["omentumToGreaterCurvature", "omental_disease", "Enfermedad omental hasta la curvatura mayor"],
      ["bowelResectionExpectedOrExtensiveSerosalDisease", "bowel_infiltration", "Reseccion intestinal prevista o enfermedad serosa extensa"],
      ["gastricWallInvolvement", "stomach_infiltration", "Compromiso evidente de la pared gastrica"],
    ];
    const values = {};
    for (const [field] of definitions) values[field] = readBoolean(input, field, missing, errors);
    const largestLiverSurfaceLesionCm = readNumber(
      input,
      "largestLiverSurfaceLesionCm",
      missing,
      errors,
      { min: 0 },
    );
    if (missing.length || errors.length) return invalid(RULES.fagotti2006, missing, errors);

    const positiveFeatures = definitions
      .filter(([field]) => values[field])
      .map(([, id, label]) => ({ id, label, points: 2 }));
    if (largestLiverSurfaceLesionCm > 2) {
      positiveFeatures.push({
        id: "liver_surface_metastasis_over_2cm",
        label: "Lesion superficial hepatica >2 cm",
        points: 2,
      });
    }
    const score = positiveFeatures.reduce((total, feature) => total + feature.points, 0);
    const legacyThresholdMet = score >= 8;
    return {
      valid: true,
      applicable: true,
      score,
      classification: legacyThresholdMet ? "piv_ge_8" : "piv_below_8",
      legacyThreshold: 8,
      legacyThresholdMet,
      positiveFeatures,
      notes: [
        "El umbral historico predice riesgo de citorreduccion suboptima segun la definicion de residuo >1 cm.",
        "El resultado no equivale a irresecabilidad ni reemplaza la evaluacion de un centro experto.",
      ],
      rule: RULES.fagotti2006,
    };
  }

  function agoDesktopIII(rawInput = {}) {
    const input = objectInput(rawInput);
    const missing = [];
    const errors = [];
    const firstRelapse = readBoolean(input, "firstRelapse", missing, errors);
    const platinumFreeIntervalMonths = readNumber(
      input,
      "platinumFreeIntervalMonths",
      missing,
      errors,
      { min: 0 },
    );
    if (missing.length || errors.length) return invalid(RULES.agoDesktopIII, missing, errors);

    if (!firstRelapse || platinumFreeIntervalMonths < 6) {
      const reasons = [];
      if (!firstRelapse) reasons.push("no corresponde a la primera recaida");
      if (platinumFreeIntervalMonths < 6) reasons.push("intervalo libre de platino <6 meses");
      return {
        valid: true,
        applicable: false,
        positive: null,
        classification: "outside_desktop_iii_population",
        reasons,
        rule: RULES.agoDesktopIII,
      };
    }

    const ecog = readNumber(input, "ecog", missing, errors, { min: 0, max: 4, integer: true });
    const ascitesMl = readNumber(input, "ascitesMl", missing, errors, { min: 0 });
    const completeResectionInitialSurgery = readBoolean(
      input,
      "completeResectionInitialSurgery",
      missing,
      errors,
    );
    if (missing.length || errors.length) return invalid(RULES.agoDesktopIII, missing, errors, { applicable: true });

    const components = {
      ecogZero: ecog === 0,
      ascitesBelow500Ml: ascitesMl < 500,
      completeResectionInitialSurgery,
    };
    const positive = components.ecogZero
      && components.ascitesBelow500Ml
      && components.completeResectionInitialSurgery;
    return {
      valid: true,
      applicable: true,
      positive,
      classification: positive ? "ago_positive" : "ago_negative",
      components,
      context: { firstRelapse, platinumFreeIntervalMonths },
      notes: [
        "Un AGO positivo identifica mayor probabilidad de reseccion completa; no garantiza resecabilidad ni beneficio individual.",
      ],
      rule: RULES.agoDesktopIII,
    };
  }

  return Object.freeze({
    RULES,
    sedlis,
    peters,
    promiseEsgo2025,
    rmiI,
    fagotti2006,
    agoDesktopIII,
  });
});
