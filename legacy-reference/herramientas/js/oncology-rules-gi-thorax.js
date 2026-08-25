(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.OncologyRulesGiThorax = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const VERSION = "1.0.0";

  const PCI_REGIONS = Object.freeze([
    Object.freeze({ id: 0, key: "central", label: "Central" }),
    Object.freeze({ id: 1, key: "right_upper", label: "Superior derecha" }),
    Object.freeze({ id: 2, key: "epigastrium", label: "Epigastrio" }),
    Object.freeze({ id: 3, key: "left_upper", label: "Superior izquierda" }),
    Object.freeze({ id: 4, key: "left_flank", label: "Flanco izquierdo" }),
    Object.freeze({ id: 5, key: "left_lower", label: "Inferior izquierda" }),
    Object.freeze({ id: 6, key: "pelvis", label: "Pelvis" }),
    Object.freeze({ id: 7, key: "right_lower", label: "Inferior derecha" }),
    Object.freeze({ id: 8, key: "right_flank", label: "Flanco derecho" }),
    Object.freeze({ id: 9, key: "upper_jejunum", label: "Yeyuno superior" }),
    Object.freeze({ id: 10, key: "lower_jejunum", label: "Yeyuno inferior" }),
    Object.freeze({ id: 11, key: "upper_ileum", label: "Ileon superior" }),
    Object.freeze({ id: 12, key: "lower_ileum", label: "Ileon inferior" }),
  ]);

  function error(field, code, message) {
    return { field, code, message };
  }

  function invalid(model, errors, warnings = []) {
    return { model, valid: false, errors, warnings };
  }

  function numeric(value) {
    if (typeof value === "number") return Number.isFinite(value) ? value : null;
    if (typeof value !== "string" || value.trim() === "") return null;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  function readNumber(input, field, errors, options = {}) {
    const value = numeric(input[field]);
    if (value === null) {
      errors.push(error(field, "required_number", `${field} debe ser un numero finito`));
      return null;
    }
    if (options.integer && !Number.isInteger(value)) {
      errors.push(error(field, "integer_required", `${field} debe ser un numero entero`));
    }
    if (options.min !== undefined && value < options.min) {
      errors.push(error(field, "below_minimum", `${field} no puede ser menor que ${options.min}`));
    }
    if (options.exclusiveMin !== undefined && value <= options.exclusiveMin) {
      errors.push(error(field, "below_or_equal_minimum", `${field} debe ser mayor que ${options.exclusiveMin}`));
    }
    if (options.max !== undefined && value > options.max) {
      errors.push(error(field, "above_maximum", `${field} no puede ser mayor que ${options.max}`));
    }
    return value;
  }

  function readBoolean(input, field, errors) {
    if (typeof input[field] !== "boolean") {
      errors.push(error(field, "required_boolean", `${field} debe ser true o false`));
      return null;
    }
    return input[field];
  }

  function logistic(value) {
    if (value >= 0) {
      const expNegative = Math.exp(-value);
      return 1 / (1 + expNegative);
    }
    const expPositive = Math.exp(value);
    return expPositive / (1 + expPositive);
  }

  function normalizeToken(value) {
    return String(value === undefined || value === null ? "" : value)
      .trim()
      .toLowerCase()
      .replace(/[\s-]+/g, "_");
  }

  function readEnum(input, field, errors, aliases) {
    const token = normalizeToken(input[field]);
    const normalized = aliases[token];
    if (!normalized) {
      const allowed = [...new Set(Object.values(aliases))].join(", ");
      errors.push(error(field, "invalid_category", `${field} debe ser uno de: ${allowed}`));
      return null;
    }
    return normalized;
  }

  function brock(input = {}) {
    const model = "brock_full_2013";
    const errors = [];
    const warnings = [];
    const ageYears = readNumber(input, "ageYears", errors, { exclusiveMin: 0, max: 120 });
    const female = readBoolean(input, "female", errors);
    const familyHistoryLungCancer = readBoolean(input, "familyHistoryLungCancer", errors);
    const emphysema = readBoolean(input, "emphysema", errors);
    const diameterMm = readNumber(input, "diameterMm", errors, { exclusiveMin: 0, max: 30 });
    const noduleType = readEnum(input, "noduleType", errors, {
      solid: "solid",
      ground_glass: "ground_glass",
      groundglass: "ground_glass",
      nonsolid: "ground_glass",
      non_solid: "ground_glass",
      part_solid: "part_solid",
      partsolid: "part_solid",
      semisolid: "part_solid",
      semi_solid: "part_solid",
    });
    const upperLobe = readBoolean(input, "upperLobe", errors);
    const noduleCount = readNumber(input, "noduleCount", errors, { integer: true, min: 1 });
    const spiculation = readBoolean(input, "spiculation", errors);

    if (errors.length) return invalid(model, errors, warnings);
    if (ageYears < 50 || ageYears > 75) {
      warnings.push("Edad fuera del rango 50-75 anos de la cohorte de desarrollo PanCan");
    }

    const typeCoefficient = noduleType === "ground_glass" ? -0.1276
      : noduleType === "part_solid" ? 0.377
        : 0;
    const sizeTransform = Math.pow(diameterMm / 10, -0.5) - 1.58113883;
    const linearPredictor = -6.7892
      + 0.0287 * (ageYears - 62)
      + (female ? 0.6011 : 0)
      + (familyHistoryLungCancer ? 0.2961 : 0)
      + (emphysema ? 0.2953 : 0)
      - 5.3854 * sizeTransform
      + typeCoefficient
      + (upperLobe ? 0.6581 : 0)
      - 0.0824 * (noduleCount - 4)
      + (spiculation ? 0.7729 : 0);
    const probability = logistic(linearPredictor);

    return {
      model,
      valid: true,
      probability,
      probabilityPercent: probability * 100,
      linearPredictor,
      inputs: {
        ageYears,
        female,
        familyHistoryLungCancer,
        emphysema,
        diameterMm,
        noduleType,
        upperLobe,
        noduleCount,
        spiculation,
      },
      warnings,
    };
  }

  function mayoHerder(input = {}) {
    const model = "mayo_herder_2005";
    const errors = [];
    const warnings = [];
    const ageYears = readNumber(input, "ageYears", errors, { exclusiveMin: 0, max: 120 });
    const currentOrFormerSmoker = readBoolean(input, "currentOrFormerSmoker", errors);
    const extrathoracicCancerMoreThan5Years = readBoolean(input, "extrathoracicCancerMoreThan5Years", errors);
    const diameterMm = readNumber(input, "diameterMm", errors, { min: 4, max: 30 });
    const spiculation = readBoolean(input, "spiculation", errors);
    const upperLobe = readBoolean(input, "upperLobe", errors);
    const petUptake = readEnum(input, "petUptake", errors, {
      absent: "absent",
      none: "absent",
      no_uptake: "absent",
      faint: "faint",
      discrete: "faint",
      moderate: "moderate",
      intense: "intense",
      high: "intense",
    });

    if (errors.length) return invalid(model, errors, warnings);
    if (diameterMm <= 10) {
      warnings.push("La sensibilidad de PET-FDG puede ser menor en nodulos de 10 mm o menos");
    }

    const mayoLinearPredictor = -6.8272
      + 0.0391 * ageYears
      + (currentOrFormerSmoker ? 0.7917 : 0)
      + (extrathoracicCancerMoreThan5Years ? 1.3388 : 0)
      + 0.1274 * diameterMm
      + (spiculation ? 1.0407 : 0)
      + (upperLobe ? 0.7838 : 0);
    const mayoProbability = logistic(mayoLinearPredictor);
    const petCoefficient = petUptake === "faint" ? 2.322
      : petUptake === "moderate" ? 4.617
        : petUptake === "intense" ? 4.771
          : 0;
    const herderLinearPredictor = -4.739 + 3.691 * mayoProbability + petCoefficient;
    const herderProbability = logistic(herderLinearPredictor);

    return {
      model,
      valid: true,
      mayo: {
        linearPredictor: mayoLinearPredictor,
        probability: mayoProbability,
        probabilityPercent: mayoProbability * 100,
      },
      herder: {
        linearPredictor: herderLinearPredictor,
        probability: herderProbability,
        probabilityPercent: herderProbability * 100,
        petCoefficient,
      },
      inputs: {
        ageYears,
        currentOrFormerSmoker,
        extrathoracicCancerMoreThan5Years,
        diameterMm,
        spiculation,
        upperLobe,
        petUptake,
      },
      warnings,
    };
  }

  function normalizeBiomarkerStatus(value, field, errors) {
    if (typeof value === "boolean") return value ? "positive" : "negative";
    const token = normalizeToken(value);
    const aliases = {
      positive: "positive",
      pos: "positive",
      mutated: "positive",
      negative: "negative",
      neg: "negative",
      wild_type: "negative",
      wildtype: "negative",
      unknown: "unknown",
      not_tested: "unknown",
    };
    if (!aliases[token]) {
      errors.push(error(field, "invalid_status", `${field} debe ser positive, negative o unknown`));
      return null;
    }
    return aliases[token];
  }

  function lungGpaBand(histology, total) {
    const index = total <= 1 ? 0 : total <= 2 ? 1 : total <= 3 ? 2 : 3;
    const bands = ["0-1.0", "1.5-2.0", "2.5-3.0", "3.5-4.0"];
    const values = {
      adenocarcinoma: [
        { median: 6, iqr: [2, 13] },
        { median: 15, iqr: [5, 38] },
        { median: 30, iqr: [12, null] },
        { median: 52, iqr: [25, 69] },
      ],
      non_adenocarcinoma: [
        { median: 2, iqr: [1, 4] },
        { median: 5, iqr: [3, 12] },
        { median: 10, iqr: [4, 21] },
        { median: 19, iqr: [8, 33] },
      ],
      sclc: [
        { median: 4, iqr: [2, 8] },
        { median: 8, iqr: [4, 15] },
        { median: 13, iqr: [7, 23] },
        { median: 23, iqr: [11, null] },
      ],
    };
    return { band: bands[index], ...values[histology][index] };
  }

  function lungGpa2022(input = {}) {
    const model = "lung_gpa_2022";
    const errors = [];
    const warnings = [];
    const histology = readEnum(input, "histology", errors, {
      adenocarcinoma: "adenocarcinoma",
      adeno: "adenocarcinoma",
      nsclc_adenocarcinoma: "adenocarcinoma",
      non_adenocarcinoma: "non_adenocarcinoma",
      nonadenocarcinoma: "non_adenocarcinoma",
      non_adeno: "non_adenocarcinoma",
      nsclc_non_adenocarcinoma: "non_adenocarcinoma",
      sclc: "sclc",
      small_cell: "sclc",
      small_cell_lung_cancer: "sclc",
    });
    const ageYears = readNumber(input, "ageYears", errors, { min: 18, max: 120 });
    const kps = readNumber(input, "kps", errors, { integer: true, min: 0, max: 100 });
    if (kps !== null && (kps % 10 !== 0)) {
      errors.push(error("kps", "invalid_kps_increment", "kps debe expresarse en incrementos de 10"));
    }
    const brainMetastases = readNumber(input, "brainMetastases", errors, { integer: true, min: 1 });
    const extracranialMetastases = readBoolean(input, "extracranialMetastases", errors);

    let egfrStatus = null;
    let alkStatus = null;
    let pdl1Status = null;
    if (histology === "adenocarcinoma") {
      egfrStatus = normalizeBiomarkerStatus(input.egfrStatus, "egfrStatus", errors);
      alkStatus = normalizeBiomarkerStatus(input.alkStatus, "alkStatus", errors);
      if (input.pdl1Percent !== undefined && input.pdl1Percent !== null && input.pdl1Percent !== "") {
        const pdl1Percent = readNumber(input, "pdl1Percent", errors, { min: 0, max: 100 });
        if (pdl1Percent !== null) pdl1Status = pdl1Percent >= 1 ? "positive" : "negative";
      } else {
        pdl1Status = normalizeBiomarkerStatus(input.pdl1Status, "pdl1Status", errors);
      }
    }

    if (errors.length) return invalid(model, errors, warnings);

    let components;
    if (histology === "adenocarcinoma") {
      components = {
        kps: kps <= 70 ? 0 : kps === 80 ? 0.5 : 1,
        age: ageYears < 70 ? 0.5 : 0,
        brainMetastases: brainMetastases <= 4 ? 0.5 : 0,
        extracranialMetastases: extracranialMetastases ? 0 : 1,
        egfrOrAlk: egfrStatus === "positive" || alkStatus === "positive" ? 0.5 : 0,
        pdl1: pdl1Status === "positive" ? 0.5 : 0,
      };
    } else if (histology === "non_adenocarcinoma") {
      components = {
        kps: kps <= 60 ? 0 : kps === 70 ? 1 : kps === 80 ? 1.5 : 2,
        age: ageYears < 70 ? 0.5 : 0,
        brainMetastases: brainMetastases <= 4 ? 0.5 : 0,
        extracranialMetastases: extracranialMetastases ? 0 : 1,
      };
    } else {
      components = {
        kps: kps <= 60 ? 0 : kps === 70 ? 0.5 : kps === 80 ? 1 : kps === 90 ? 1.5 : 2,
        age: ageYears < 75 ? 0.5 : 0,
        brainMetastases: brainMetastases <= 3 ? 1 : brainMetastases <= 7 ? 0.5 : 0,
        extracranialMetastases: extracranialMetastases ? 0 : 0.5,
      };
    }

    const total = Object.values(components).reduce((sum, value) => sum + value, 0);
    const survival = lungGpaBand(histology, total);
    return {
      model,
      valid: true,
      histology,
      total,
      components,
      prognosticBand: survival.band,
      medianOverallSurvivalMonths: survival.median,
      interquartileRangeMonths: survival.iqr,
      inputs: {
        ageYears,
        kps,
        brainMetastases,
        extracranialMetastases,
        ...(histology === "adenocarcinoma" ? { egfrStatus, alkStatus, pdl1Status } : {}),
      },
      warnings,
    };
  }

  function lipi(input = {}) {
    const model = "lipi_2018";
    const errors = [];
    const warnings = [];
    const whiteBloodCells = readNumber(input, "whiteBloodCells", errors, { exclusiveMin: 0 });
    const absoluteNeutrophils = readNumber(input, "absoluteNeutrophils", errors, { min: 0 });
    const ldh = readNumber(input, "ldh", errors, { exclusiveMin: 0 });
    const ldhUpperLimitNormal = readNumber(input, "ldhUpperLimitNormal", errors, { exclusiveMin: 0 });

    if (whiteBloodCells !== null && absoluteNeutrophils !== null && absoluteNeutrophils >= whiteBloodCells) {
      errors.push(error(
        "absoluteNeutrophils",
        "invalid_dnlr_denominator",
        "absoluteNeutrophils debe ser menor que whiteBloodCells",
      ));
    }
    if (errors.length) return invalid(model, errors, warnings);

    const derivedNeutrophilLymphocyteRatio = absoluteNeutrophils / (whiteBloodCells - absoluteNeutrophils);
    const dnlrPoint = derivedNeutrophilLymphocyteRatio > 3 ? 1 : 0;
    const ldhPoint = ldh > ldhUpperLimitNormal ? 1 : 0;
    const total = dnlrPoint + ldhPoint;
    const category = total === 0 ? "good" : total === 1 ? "intermediate" : "poor";

    warnings.push("Indice pronostico; no predice por si solo el beneficio de un tratamiento");
    return {
      model,
      valid: true,
      total,
      category,
      derivedNeutrophilLymphocyteRatio,
      components: {
        dnlrAbove3: dnlrPoint,
        ldhAboveUpperLimitNormal: ldhPoint,
      },
      inputs: { whiteBloodCells, absoluteNeutrophils, ldh, ldhUpperLimitNormal },
      warnings,
    };
  }

  function normalizeUnit(value) {
    return String(value === undefined || value === null ? "" : value)
      .trim()
      .toLowerCase()
      .replace(/\u00b5|\u03bc/g, "u")
      .replace(/\s+/g, "")
      .replace(/\//g, "_");
  }

  function albi(input = {}) {
    const model = "albi_grade_2015";
    const errors = [];
    const warnings = [];
    const bilirubin = readNumber(input, "bilirubin", errors, { exclusiveMin: 0 });
    const albumin = readNumber(input, "albumin", errors, { exclusiveMin: 0 });
    const bilirubinUnit = normalizeUnit(input.bilirubinUnit);
    const albuminUnit = normalizeUnit(input.albuminUnit);
    const bilirubinUnits = ["umol_l", "micromol_l", "mg_dl"];
    const albuminUnits = ["g_l", "g_dl"];
    if (!bilirubinUnits.includes(bilirubinUnit)) {
      errors.push(error("bilirubinUnit", "invalid_unit", "bilirubinUnit debe ser umol/L o mg/dL"));
    }
    if (!albuminUnits.includes(albuminUnit)) {
      errors.push(error("albuminUnit", "invalid_unit", "albuminUnit debe ser g/L o g/dL"));
    }
    if (errors.length) return invalid(model, errors, warnings);

    const bilirubinMicromolL = bilirubinUnit === "mg_dl" ? bilirubin * 17.1 : bilirubin;
    const albuminGL = albuminUnit === "g_dl" ? albumin * 10 : albumin;
    const score = 0.66 * Math.log10(bilirubinMicromolL) - 0.085 * albuminGL;
    // Decimal inputs that mathematically land on a published cutoff can differ
    // by a few ULP after log10; the tolerance preserves the inclusive limits.
    const cutoffTolerance = 1e-12;
    const grade = score <= -2.60 + cutoffTolerance ? 1
      : score <= -1.39 + cutoffTolerance ? 2
        : 3;
    const modifiedGrade = grade === 1 ? "1"
      : score <= -2.27 + cutoffTolerance ? "2a"
        : grade === 2 ? "2b" : "3";

    return {
      model,
      valid: true,
      score,
      grade,
      modifiedGrade,
      inputs: {
        bilirubin,
        bilirubinUnit,
        albumin,
        albuminUnit,
        bilirubinMicromolL,
        albuminGL,
      },
      warnings,
    };
  }

  function frenchAfpHcc(input = {}) {
    const model = "french_afp_hcc_2012";
    const errors = [];
    const warnings = [];
    const largestTumorDiameterCm = readNumber(input, "largestTumorDiameterCm", errors, { exclusiveMin: 0 });
    const noduleCount = readNumber(input, "noduleCount", errors, { integer: true, min: 1 });
    const afpNgMl = readNumber(input, "afpNgMl", errors, { min: 0 });
    if (errors.length) return invalid(model, errors, warnings);

    const diameterPoints = largestTumorDiameterCm <= 3 ? 0 : largestTumorDiameterCm <= 6 ? 1 : 4;
    const nodulePoints = noduleCount <= 3 ? 0 : 2;
    const afpPoints = afpNgMl <= 100 ? 0 : afpNgMl <= 1000 ? 2 : 3;
    const total = diameterPoints + nodulePoints + afpPoints;

    return {
      model,
      valid: true,
      total,
      category: total <= 2 ? "low" : "high",
      components: { diameterPoints, nodulePoints, afpPoints },
      inputs: { largestTumorDiameterCm, noduleCount, afpNgMl },
      warnings,
    };
  }

  function game(input = {}) {
    const model = "game_crlm_2018";
    const errors = [];
    const warnings = [];
    const krasStatus = readEnum(input, "krasStatus", errors, {
      mutated: "mutated",
      mutant: "mutated",
      positive: "mutated",
      wild_type: "wild_type",
      wildtype: "wild_type",
      negative: "wild_type",
    });
    const ceaNgMl = readNumber(input, "ceaNgMl", errors, { min: 0 });
    const primaryNodePositive = readBoolean(input, "primaryNodePositive", errors);
    const largestLiverMetastasisCm = readNumber(input, "largestLiverMetastasisCm", errors, { exclusiveMin: 0 });
    const liverMetastasisCount = readNumber(input, "liverMetastasisCount", errors, { integer: true, min: 1 });
    const extrahepaticDisease = readBoolean(input, "extrahepaticDisease", errors);
    if (errors.length) return invalid(model, errors, warnings);

    const tumorBurdenScore = Math.sqrt(
      largestLiverMetastasisCm * largestLiverMetastasisCm
      + liverMetastasisCount * liverMetastasisCount,
    );
    const krasPoints = krasStatus === "mutated" ? 1 : 0;
    const ceaPoints = ceaNgMl >= 20 ? 1 : 0;
    const nodePoints = primaryNodePositive ? 1 : 0;
    const tumorBurdenPoints = tumorBurdenScore < 3 ? 0 : tumorBurdenScore < 9 ? 1 : 2;
    const extrahepaticPoints = extrahepaticDisease ? 2 : 0;
    const total = krasPoints + ceaPoints + nodePoints + tumorBurdenPoints + extrahepaticPoints;
    const category = total <= 1 ? "low" : total <= 3 ? "intermediate" : "high";

    return {
      model,
      valid: true,
      total,
      category,
      tumorBurdenScore,
      components: {
        krasPoints,
        ceaPoints,
        nodePoints,
        tumorBurdenPoints,
        extrahepaticPoints,
      },
      inputs: {
        krasStatus,
        ceaNgMl,
        primaryNodePositive,
        largestLiverMetastasisCm,
        liverMetastasisCount,
        extrahepaticDisease,
      },
      warnings,
    };
  }

  function pciRegionResult(item, region, errors) {
    const field = `regions[${region.id}]`;
    if (item === null) {
      return { ...region, lesionSizeScore: 0, largestLesionCm: null, confluent: false, source: "no_visible_tumor" };
    }
    if (!item || typeof item !== "object" || Array.isArray(item)) {
      errors.push(error(field, "invalid_region", `${field} debe ser null o un objeto de region`));
      return null;
    }
    if (Object.prototype.hasOwnProperty.call(item, "lesionSizeScore")) {
      const lesionSizeScore = numeric(item.lesionSizeScore);
      if (lesionSizeScore === null || !Number.isInteger(lesionSizeScore) || lesionSizeScore < 0 || lesionSizeScore > 3) {
        errors.push(error(`${field}.lesionSizeScore`, "invalid_ls_score", "lesionSizeScore debe ser un entero de 0 a 3"));
        return null;
      }
      return { ...region, lesionSizeScore, largestLesionCm: null, confluent: lesionSizeScore === 3 && Boolean(item.confluent), source: "preclassified" };
    }

    const confluent = item.confluent === undefined ? false : item.confluent;
    if (typeof confluent !== "boolean") {
      errors.push(error(`${field}.confluent`, "required_boolean", "confluent debe ser true o false"));
      return null;
    }
    const largestLesionCm = numeric(item.largestLesionCm);
    if (confluent && largestLesionCm === null) {
      return { ...region, lesionSizeScore: 3, largestLesionCm: null, confluent: true, source: "measured" };
    }
    if (largestLesionCm === null || largestLesionCm <= 0) {
      errors.push(error(`${field}.largestLesionCm`, "positive_size_required", "largestLesionCm debe ser mayor que 0, o usar null para ausencia de tumor"));
      return null;
    }
    const lesionSizeScore = confluent || largestLesionCm > 5 ? 3 : largestLesionCm > 0.5 ? 2 : 1;
    return { ...region, lesionSizeScore, largestLesionCm, confluent, source: "measured" };
  }

  function pci(input = {}) {
    const model = "peritoneal_cancer_index_sugarbaker";
    const errors = [];
    const warnings = [];
    if (!Array.isArray(input.regions) || input.regions.length !== PCI_REGIONS.length) {
      return invalid(model, [error("regions", "invalid_region_count", "regions debe contener exactamente 13 regiones en orden PCI")], warnings);
    }

    const regions = input.regions.map((item, index) => pciRegionResult(item, PCI_REGIONS[index], errors));
    if (errors.length) return invalid(model, errors, warnings);
    const total = regions.reduce((sum, region) => sum + region.lesionSizeScore, 0);

    return {
      model,
      valid: true,
      total,
      minimum: 0,
      maximum: 39,
      regions,
      warnings,
    };
  }

  return Object.freeze({
    version: VERSION,
    brock,
    mayoHerder,
    lungGpa2022,
    lipi,
    albi,
    frenchAfpHcc,
    game,
    pci,
  });
});
