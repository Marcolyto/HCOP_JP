(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.OncologyRulesGeneral = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const finite = (value) => Number.isFinite(Number(value));
  const n = (value) => Number(value);
  const invalid = (missing = [], message = "Faltan datos válidos para calcular.") => ({ valid: false, missing, message });

  function cockcroftGault(input = {}) {
    const age = n(input.age), weightKg = n(input.weightKg), creatinineMgDl = n(input.creatinineMgDl);
    const sex = String(input.sex || "").toLowerCase();
    const missing = [];
    if (!finite(input.age) || age < 18 || age >= 140) missing.push("edad adulta válida");
    if (!finite(input.weightKg) || weightKg <= 0) missing.push("peso usado para la fórmula");
    if (!finite(input.creatinineMgDl) || creatinineMgDl <= 0) missing.push("creatinina");
    if (!['female', 'male'].includes(sex)) missing.push("sexo");
    if (missing.length) return invalid(missing);
    const sexFactor = sex === "female" ? 0.85 : 1;
    const crcl = ((140 - age) * weightKg * sexFactor) / (72 * creatinineMgDl);
    return { valid: true, crcl, unit: "mL/min", sexFactor };
  }

  function ckdEpi2021(input = {}) {
    const age = n(input.age), creatinineMgDl = n(input.creatinineMgDl);
    const sex = String(input.sex || "").toLowerCase();
    const hasCystatin = input.cystatinCMgL !== undefined && input.cystatinCMgL !== null && input.cystatinCMgL !== "";
    const hasBsa = input.bsaM2 !== undefined && input.bsaM2 !== null && input.bsaM2 !== "";
    const cystatinCMgL = n(input.cystatinCMgL), bsaM2 = n(input.bsaM2);
    const missing = [];
    if (!finite(input.age) || age < 18 || age >= 140) missing.push("edad adulta válida");
    if (!finite(input.creatinineMgDl) || creatinineMgDl <= 0) missing.push("creatinina estandarizada");
    if (!['female', 'male'].includes(sex)) missing.push("sexo");
    if (hasCystatin && (!finite(input.cystatinCMgL) || cystatinCMgL <= 0)) missing.push("cistatina C");
    if (hasBsa && (!finite(input.bsaM2) || bsaM2 <= 0)) missing.push("superficie corporal");
    if (missing.length) return invalid(missing);
    const female = sex === "female";
    const kappa = female ? 0.7 : 0.9;
    const ratio = creatinineMgDl / kappa;
    let egfr, method;
    if (hasCystatin) {
      const alpha = female ? -0.219 : -0.144;
      const cystatinRatio = cystatinCMgL / 0.8;
      egfr = 135
        * Math.pow(Math.min(ratio, 1), alpha)
        * Math.pow(Math.max(ratio, 1), -0.544)
        * Math.pow(Math.min(cystatinRatio, 1), -0.323)
        * Math.pow(Math.max(cystatinRatio, 1), -0.778)
        * Math.pow(0.9961, age)
        * (female ? 0.963 : 1);
      method = "CKD-EPI 2021 creatinina-cistatina C";
    } else {
      const alpha = female ? -0.241 : -0.302;
      egfr = 142 * Math.pow(Math.min(ratio, 1), alpha) * Math.pow(Math.max(ratio, 1), -1.2) * Math.pow(0.9938, age) * (female ? 1.012 : 1);
      method = "CKD-EPI 2021 creatinina";
    }
    const absoluteGfr = hasBsa ? egfr * bsaM2 / 1.73 : null;
    return { valid: true, egfr, absoluteGfr, method, unit: "mL/min/1,73 m²", indexed: true };
  }

  function absoluteNeutrophilCount(input = {}) {
    const wbc = n(input.wbc10e9L), segmented = n(input.segmentedPercent), bands = n(input.bandsPercent);
    const missing = [];
    if (!finite(input.wbc10e9L) || wbc < 0) missing.push("leucocitos");
    if (!finite(input.segmentedPercent) || segmented < 0 || segmented > 100) missing.push("neutrófilos segmentados");
    if (!finite(input.bandsPercent) || bands < 0 || bands > 100) missing.push("bandas");
    if (segmented + bands > 100) missing.push("suma de segmentados y bandas ≤100%");
    if (missing.length) return invalid(missing);
    const anc = wbc * 1000 * (segmented + bands) / 100;
    const grade = anc < 100 ? 4 : anc < 500 ? 3 : anc < 1000 ? 2 : anc < 1500 ? 1 : 0;
    return { valid: true, anc, grade, unit: "células/µL", neutropenia: grade > 0 };
  }

  function khorana(input = {}) {
    const site = String(input.site || "").toLowerCase();
    const platelets = n(input.platelets10e9L), hemoglobin = n(input.hemoglobinGdl), wbc = n(input.wbc10e9L), bmi = n(input.bmi);
    const missing = [];
    if (!site) missing.push("sitio tumoral");
    if (!finite(input.platelets10e9L) || platelets < 0) missing.push("plaquetas");
    if (!finite(input.hemoglobinGdl) || hemoglobin < 0) missing.push("hemoglobina");
    if (!finite(input.wbc10e9L) || wbc < 0) missing.push("leucocitos");
    if (!finite(input.bmi) || bmi <= 0) missing.push("IMC");
    if (missing.length) return invalid(missing);
    const veryHigh = ["stomach", "pancreas"];
    const high = ["lung", "lymphoma", "gynecologic", "bladder", "testicular"];
    const sitePoints = veryHigh.includes(site) ? 2 : high.includes(site) ? 1 : 0;
    const factors = {
      site: sitePoints,
      platelets: platelets >= 350 ? 1 : 0,
      anemiaOrEsa: hemoglobin < 10 || Boolean(input.esa) ? 1 : 0,
      leukocytes: wbc > 11 ? 1 : 0,
      bmi: bmi >= 35 ? 1 : 0,
    };
    const total = Object.values(factors).reduce((sum, value) => sum + value, 0);
    const originalCategory = total === 0 ? "bajo" : total <= 2 ? "intermedio" : "alto";
    return { valid: true, total, factors, originalCategory, modernConsiderationThreshold: total >= 2 };
  }

  function mascc(input = {}) {
    const burden = n(input.burdenPoints);
    if (![0, 3, 5].includes(burden)) return invalid(["carga sintomática"]);
    const factors = {
      burden,
      noHypotension: input.noHypotension ? 5 : 0,
      noCopd: input.noCopd ? 4 : 0,
      solidOrNoPriorFungal: input.solidOrNoPriorFungal ? 4 : 0,
      noDehydration: input.noDehydration ? 3 : 0,
      outpatientOnset: input.outpatientOnset ? 3 : 0,
      ageUnder60: input.ageUnder60 ? 2 : 0,
    };
    const total = Object.values(factors).reduce((sum, value) => sum + value, 0);
    return { valid: true, total, factors, category: total >= 21 ? "bajo riesgo por MASCC" : "alto riesgo por MASCC", lowRisk: total >= 21 };
  }

  function cisne(input = {}) {
    const glucose = n(input.glucoseMgDl);
    const threshold = input.diabetesOrSteroids ? 250 : 121;
    if (!finite(input.glucoseMgDl) || glucose < 0) return invalid(["glucemia inicial"]);
    const stressHyperglycemia = glucose >= threshold;
    const factors = {
      ecog2OrMore: input.ecog2OrMore ? 2 : 0,
      stressHyperglycemia: stressHyperglycemia ? 2 : 0,
      copd: input.copd ? 1 : 0,
      cardiovascular: input.cardiovascular ? 1 : 0,
      mucositisGrade2OrMore: input.mucositisGrade2OrMore ? 1 : 0,
      monocytesUnder200: input.monocytesUnder200 ? 1 : 0,
    };
    const total = Object.values(factors).reduce((sum, value) => sum + value, 0);
    const riskClass = total === 0 ? "I · bajo" : total <= 2 ? "II · intermedio" : "III · alto";
    return { valid: true, total, factors, riskClass, classNumber: total === 0 ? 1 : total <= 2 ? 2 : 3, glucoseThreshold: threshold };
  }

  function palliativePrognosticIndex(input = {}) {
    const pps = n(input.pps);
    const oral = String(input.oralIntake || "").toLowerCase();
    if (![10,20,30,40,50,60,70,80,90,100].includes(pps)) return invalid(["PPS"]);
    if (!["normal", "moderate", "severe"].includes(oral)) return invalid(["ingesta oral"]);
    const ppsPoints = pps <= 20 ? 4 : pps <= 50 ? 2.5 : 0;
    const oralPoints = oral === "severe" ? 2.5 : oral === "moderate" ? 1 : 0;
    const factors = {
      pps: ppsPoints,
      oralIntake: oralPoints,
      edema: input.edema ? 1 : 0,
      dyspneaAtRest: input.dyspneaAtRest ? 3.5 : 0,
      delirium: input.delirium ? 4 : 0,
    };
    const total = Object.values(factors).reduce((sum, value) => sum + value, 0);
    const threshold = total > 6 ? ">6 · cohorte original: alta probabilidad de supervivencia <3 semanas" : total > 4 ? ">4 · cohorte original: alta probabilidad de supervivencia <6 semanas" : "≤4 · no cruza los puntos de corte originales";
    return { valid: true, total, factors, threshold };
  }

  function bedEqd2(input = {}) {
    const fractions = n(input.fractions), dosePerFraction = n(input.dosePerFraction), alphaBeta = n(input.alphaBeta);
    const missing = [];
    if (!finite(input.fractions) || fractions <= 0 || !Number.isInteger(fractions)) missing.push("número entero de fracciones");
    if (!finite(input.dosePerFraction) || dosePerFraction <= 0) missing.push("dosis por fracción");
    if (!finite(input.alphaBeta) || alphaBeta <= 0) missing.push("relación α/β");
    if (missing.length) return invalid(missing);
    const totalDose = fractions * dosePerFraction;
    const bed = totalDose * (1 + dosePerFraction / alphaBeta);
    const eqd2 = bed / (1 + 2 / alphaBeta);
    return { valid: true, totalDose, bed, eqd2, unit: "Gy" };
  }

  function qtcFridericia(input = {}) {
    const qtMs = n(input.qtMs), heartRate = n(input.heartRate), baselineQtcMs = n(input.baselineQtcMs);
    const sex = String(input.sex || "").toLowerCase();
    const missing = [];
    if (!finite(input.qtMs) || qtMs <= 0) missing.push("QT medido");
    if (!finite(input.heartRate) || heartRate <= 0) missing.push("frecuencia cardíaca");
    if (!['female', 'male'].includes(sex)) missing.push("sexo");
    if (input.baselineQtcMs !== undefined && input.baselineQtcMs !== "" && (!finite(input.baselineQtcMs) || baselineQtcMs <= 0)) missing.push("QTc basal");
    if (missing.length) return invalid(missing);
    const rrSeconds = 60 / heartRate;
    const qtcF = qtMs / Math.cbrt(rrSeconds);
    const upperReference = sex === 'female' ? 460 : 450;
    const delta = finite(input.baselineQtcMs) && baselineQtcMs > 0 ? qtcF - baselineQtcMs : null;
    const band = qtcF >= 500 ? "≥500 ms" : qtcF >= 480 ? "480–499 ms" : qtcF > upperReference ? `sobre referencia (${upperReference} ms)` : "dentro de referencia por sexo";
    return { valid: true, qtcF, rrSeconds, upperReference, delta, band };
  }

  function nottinghamPrognosticIndex(input = {}) {
    const sizeCm = n(input.sizeCm), grade = n(input.grade), positiveNodes = n(input.positiveNodes);
    const missing = [];
    if (!finite(input.sizeCm) || sizeCm <= 0) missing.push("tamaño invasivo");
    if (![1,2,3].includes(grade)) missing.push("grado histológico");
    if (!finite(input.positiveNodes) || positiveNodes < 0 || !Number.isInteger(positiveNodes)) missing.push("ganglios positivos");
    if (missing.length) return invalid(missing);
    const nodeScore = positiveNodes === 0 ? 1 : positiveNodes <= 3 ? 2 : 3;
    const total = 0.2 * sizeCm + grade + nodeScore;
    const group = total <= 2.4 ? "excelente" : total <= 3.4 ? "bueno" : total <= 4.4 ? "moderado I" : total <= 5.4 ? "moderado II" : total <= 6.4 ? "pobre" : "muy pobre";
    return { valid: true, total, nodeScore, group };
  }

  function residualCancerBurden(input = {}) {
    const d1Mm = n(input.d1Mm), d2Mm = n(input.d2Mm), cellularityPercent = n(input.cellularityPercent), inSituPercent = n(input.inSituPercent), positiveNodes = n(input.positiveNodes), largestMetMm = n(input.largestMetMm);
    const missing = [];
    if (!finite(input.d1Mm) || d1Mm < 0) missing.push("diámetro 1 del lecho");
    if (!finite(input.d2Mm) || d2Mm < 0) missing.push("diámetro 2 del lecho");
    if (!finite(input.cellularityPercent) || cellularityPercent < 0 || cellularityPercent > 100) missing.push("celularidad");
    if (!finite(input.inSituPercent) || inSituPercent < 0 || inSituPercent > 100) missing.push("componente in situ");
    if (inSituPercent > cellularityPercent && cellularityPercent > 0) missing.push("porcentaje in situ no mayor que la celularidad global");
    if (!finite(input.positiveNodes) || positiveNodes < 0 || !Number.isInteger(positiveNodes)) missing.push("ganglios positivos");
    if (!finite(input.largestMetMm) || largestMetMm < 0 || (positiveNodes > 0 && largestMetMm <= 0) || (positiveNodes === 0 && largestMetMm !== 0)) missing.push("diámetro de mayor metástasis ganglionar coherente");
    if (missing.length) return invalid(missing);
    const dPrim = Math.sqrt(d1Mm * d2Mm);
    const fInv = (cellularityPercent / 100) * (1 - inSituPercent / 100);
    const primaryTerm = 1.4 * Math.pow(fInv * dPrim, 0.17);
    const nodeTerm = Math.pow(4 * (1 - Math.pow(0.75, positiveNodes)) * largestMetMm, 0.17);
    const total = primaryTerm + nodeTerm;
    const rcbClass = total === 0 ? "RCB-0" : total <= 1.36 ? "RCB-I" : total <= 3.28 ? "RCB-II" : "RCB-III";
    return { valid: true, total, rcbClass, dPrim, fInv, primaryTerm, nodeTerm };
  }

  function pepi(input = {}) {
    const pt = String(input.pt || "").toLowerCase(), nodePositive = String(input.nodePositive || ""), ki67 = n(input.ki67Percent), erAllred = n(input.erAllred);
    const missing = [];
    if (!['pt1', 'pt2', 'pt3', 'pt4'].includes(pt)) missing.push("pT posendocrinoterapia");
    if (!['yes', 'no'].includes(nodePositive)) missing.push("estado ganglionar");
    if (!finite(input.ki67Percent) || ki67 < 0 || ki67 > 100) missing.push("Ki-67");
    if (!finite(input.erAllred) || erAllred < 0 || erAllred > 8 || !Number.isInteger(erAllred)) missing.push("ER Allred");
    if (missing.length) return invalid(missing);
    const factors = {
      pt: ['pt3', 'pt4'].includes(pt) ? 3 : 0,
      nodes: nodePositive === 'yes' ? 3 : 0,
      ki67: ki67 <= 2.7 ? 0 : ki67 <= 19.7 ? 1 : ki67 <= 53.1 ? 2 : 3,
      er: erAllred <= 2 ? 3 : 0,
    };
    const total = Object.values(factors).reduce((sum, value) => sum + value, 0);
    const group = total === 0 ? "PEPI 0" : total <= 3 ? "PEPI 1–3" : "PEPI ≥4";
    return { valid: true, total, factors, group };
  }

  function cts5(input = {}) {
    const age = n(input.age), sizeMm = n(input.sizeMm), grade = n(input.grade), positiveNodes = n(input.positiveNodes);
    const missing = [];
    if (!finite(input.age) || age <= 0) missing.push("edad al diagnóstico");
    if (!finite(input.sizeMm) || sizeMm <= 0) missing.push("tamaño tumoral");
    if (![1,2,3].includes(grade)) missing.push("grado");
    if (!finite(input.positiveNodes) || positiveNodes < 0 || !Number.isInteger(positiveNodes)) missing.push("ganglios positivos");
    if (missing.length) return invalid(missing);
    const nodeCategory = positiveNodes === 0 ? 0 : positiveNodes === 1 ? 1 : positiveNodes <= 3 ? 2 : positiveNodes <= 9 ? 3 : 4;
    const cappedSizeMm = Math.min(sizeMm, 30);
    const total = 0.438 * nodeCategory + 0.988 * (0.093 * cappedSizeMm - 0.001 * cappedSizeMm * cappedSizeMm + 0.375 * grade + 0.017 * age);
    const group = total < 3.13 ? "bajo" : total <= 3.86 ? "intermedio" : "alto";
    return { valid: true, total, nodeCategory, group, cappedSizeMm, sizeWasCapped: sizeMm > 30 };
  }

  function monarchE(input = {}) {
    const nodes = n(input.positiveAxillaryNodes), sizeMm = n(input.sizeMm), grade = n(input.grade);
    const missing = [];
    if (!finite(input.positiveAxillaryNodes) || nodes < 0 || !Number.isInteger(nodes)) missing.push("ganglios axilares positivos");
    if (!finite(input.sizeMm) || sizeMm <= 0) missing.push("tamaño tumoral");
    if (![1,2,3].includes(grade)) missing.push("grado");
    if (missing.length) return invalid(missing);
    const biologicScope = Boolean(input.hrPositive) && Boolean(input.her2Negative) && Boolean(input.earlyNonMetastatic);
    const highRiskAnatomy = nodes >= 4 || (nodes >= 1 && nodes <= 3 && (grade === 3 || sizeMm >= 50));
    return { valid: true, meetsCohort1: biologicScope && highRiskAnatomy, biologicScope, highRiskAnatomy, ki67Required: false };
  }

  function olympia(input = {}) {
    const scenario = String(input.scenario || "");
    const missing = [];
    if (!["neo_tnbc", "neo_hr", "adj_tnbc", "adj_hr"].includes(scenario)) missing.push("escenario clínico");
    if (missing.length) return invalid(missing);
    const baseScope = Boolean(input.germlineBrcaPathogenic) && Boolean(input.her2Negative);
    let cpsEg = null, highRisk = false;
    if (scenario === "neo_hr") {
      const cStage = String(input.clinicalStageGroup || ""), pStage = String(input.pathologicStageGroup || ""), er = String(input.erStatus || ""), grade = n(input.nuclearGrade);
      if (!['i_iia', 'iib_iiia', 'iiib_iiic'].includes(cStage)) missing.push("estadio clínico pretratamiento");
      if (!['zero_i', 'iia_iiib', 'iiic'].includes(pStage)) missing.push("estadio patológico postratamiento");
      if (!['positive', 'negative'].includes(er)) missing.push("estado ER");
      if (![1,2,3].includes(grade)) missing.push("grado nuclear");
      if (missing.length) return invalid(missing);
      const cPoints = cStage === 'i_iia' ? 0 : cStage === 'iib_iiia' ? 1 : 2;
      const pPoints = pStage === 'zero_i' ? 0 : pStage === 'iia_iiib' ? 1 : 2;
      cpsEg = cPoints + pPoints + (er === 'negative' ? 1 : 0) + (grade === 3 ? 1 : 0);
      highRisk = Boolean(input.residualInvasive) && cpsEg >= 3;
    } else if (scenario === "neo_tnbc") {
      highRisk = Boolean(input.residualInvasive);
    } else {
      const nodes = n(input.positiveNodes), sizeCm = n(input.sizeCm);
      if (!finite(input.positiveNodes) || nodes < 0 || !Number.isInteger(nodes)) missing.push("ganglios positivos");
      if (scenario === "adj_tnbc" && (!finite(input.sizeCm) || sizeCm <= 0)) missing.push("tamaño tumoral");
      if (missing.length) return invalid(missing);
      highRisk = scenario === "adj_hr" ? nodes >= 4 : nodes > 0 || (nodes === 0 && sizeCm >= 2);
    }
    return { valid: true, baseScope, highRisk, meetsTrialCriteria: baseScope && highRisk, cpsEg };
  }

  function internationalPrognosticIndex(input = {}) {
    const age = n(input.age), stage = n(input.stage), ecog = n(input.ecog), extranodalSites = n(input.extranodalSites);
    const missing = [];
    if (!finite(input.age) || age < 0) missing.push("edad");
    if (![1,2,3,4].includes(stage)) missing.push("estadio Ann Arbor");
    if (![0,1,2,3,4].includes(ecog)) missing.push("ECOG");
    if (!finite(input.extranodalSites) || extranodalSites < 0 || !Number.isInteger(extranodalSites)) missing.push("sitios extranodales");
    if (missing.length) return invalid(missing);
    const factors = { age: age > 60 ? 1 : 0, stage: stage >= 3 ? 1 : 0, ldh: input.ldhAboveUpperLimit ? 1 : 0, ecog: ecog >= 2 ? 1 : 0, extranodal: extranodalSites > 1 ? 1 : 0 };
    const total = Object.values(factors).reduce((sum, value) => sum + value, 0);
    const group = total <= 1 ? "bajo" : total === 2 ? "bajo-intermedio" : total === 3 ? "alto-intermedio" : "alto";
    return { valid: true, total, factors, group };
  }

  function r2Iss(input = {}) {
    const beta2 = n(input.beta2MgL), albumin = n(input.albuminGdl);
    const missing = [];
    if (!finite(input.beta2MgL) || beta2 <= 0) missing.push("β2-microglobulina");
    if (!finite(input.albuminGdl) || albumin <= 0) missing.push("albúmina");
    if (missing.length) return invalid(missing);
    const iss = beta2 >= 5.5 ? 3 : beta2 < 3.5 && albumin >= 3.5 ? 1 : 2;
    const factors = {
      iss: iss === 2 ? 1 : iss === 3 ? 1.5 : 0,
      del17p: input.del17p ? 1 : 0,
      highLdh: input.ldhAboveUpperLimit ? 1 : 0,
      t414: input.t414 ? 1 : 0,
      oneQGainAmp: input.oneQGainAmp ? 0.5 : 0,
    };
    const total = Object.values(factors).reduce((sum, value) => sum + value, 0);
    const stage = total === 0 ? "R2-ISS I" : total <= 1 ? "R2-ISS II" : total <= 2.5 ? "R2-ISS III" : "R2-ISS IV";
    return { valid: true, total, factors, iss, stage };
  }

  return {
    cockcroftGault, ckdEpi2021, absoluteNeutrophilCount, khorana, mascc, cisne,
    palliativePrognosticIndex, bedEqd2, qtcFridericia, nottinghamPrognosticIndex, residualCancerBurden,
    pepi, cts5, monarchE, olympia, internationalPrognosticIndex, r2Iss,
  };
});
