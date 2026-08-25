(function (root, factory) {
  const createOncologyToolsGeneral = factory();
  if (typeof module === "object" && module.exports) module.exports = createOncologyToolsGeneral;
  if (root) root.createOncologyToolsGeneral = createOncologyToolsGeneral;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  function createOncologyToolsGeneral(helpers = {}) {
    const { number, select, checkbox, section, option, result, rules } = helpers;
    const helperFunctions = { number, select, checkbox, section, option, result };
    const missingHelpers = Object.entries(helperFunctions)
      .filter(([, value]) => typeof value !== "function")
      .map(([name]) => name);

    if (missingHelpers.length) {
      throw new TypeError(`Faltan helpers de interfaz: ${missingHelpers.join(", ")}.`);
    }
    if (!rules || typeof rules !== "object") {
      throw new TypeError("Falta helpers.rules (OncologyRulesGeneral).");
    }

    const requiredRuleNames = [
      "cockcroftGault", "ckdEpi2021", "absoluteNeutrophilCount", "khorana",
      "mascc", "cisne", "palliativePrognosticIndex", "bedEqd2",
      "qtcFridericia", "nottinghamPrognosticIndex", "residualCancerBurden",
      "pepi", "cts5", "monarchE", "olympia", "internationalPrognosticIndex",
      "r2Iss",
    ];
    const missingRules = requiredRuleNames.filter((name) => typeof rules[name] !== "function");
    if (missingRules.length) {
      throw new TypeError(`Faltan reglas clínicas: ${missingRules.join(", ")}.`);
    }

    const yesNoOptions = [option("no", "No"), option("yes", "Sí")];
    const sexOptions = [option("female", "Mujer"), option("male", "Varón")];
    const gradeOptions = [1, 2, 3].map((value) => option(String(value), `Grado ${value}`));
    const ecogOptions = [0, 1, 2, 3, 4].map((value) => option(String(value), `ECOG ${value}`));
    const bool = (value) => value === true || value === "true" || value === "yes" || value === "1" || value === 1;
    const finite = (value) => Number.isFinite(Number(value));
    const fmt = (value, digits = 1) => Number(value).toFixed(digits);
    const unique = (values) => [...new Set(values.filter(Boolean))];

    function invalidRuleResult(label, ...evaluations) {
      const failed = evaluations.filter((evaluation) => !evaluation || evaluation.valid === false);
      const missing = unique(failed.flatMap((evaluation) => evaluation?.missing || []));
      const messages = unique(failed.map((evaluation) => evaluation?.message));
      return result({
        title: "Datos incompletos",
        detail: missing.length
          ? `Revisar: ${missing.join(", ")}.`
          : messages.join(" ") || "No fue posible completar el cálculo.",
        badge: label,
        score: 0,
        showScore: false,
        severity: "warn",
        metrics: [],
        notes: ["Corregir los datos antes de interpretar el resultado."],
      });
    }

    function baseResult(config) {
      return result({ score: 0, showScore: false, severity: "info", metrics: [], notes: [], ...config });
    }

    const tools = [
      {
        id: "renal-function-oncology",
        number: "G4",
        title: "Función renal: Cockcroft–Gault y CKD-EPI 2021",
        category: "general",
        subtitle: "Dos estimaciones en paralelo, con método y unidades visibles.",
        source: "Cockcroft–Gault; CKD-EPI 2021",
        clinicalUse: "Compara clearance de creatinina estimado y eGFR indexado para documentar función renal antes de decisiones oncológicas dependientes del método.",
        fields: [
          section("renal_scope", "Ingresar creatinina estable. El peso corresponde al peso elegido explícitamente para Cockcroft–Gault; la herramienta no decide entre peso real, ideal o ajustado."),
          number("renal_age", "Edad (años)", { value: 65, min: 18, max: 139, step: 1 }),
          select("renal_sex", "Sexo usado por las ecuaciones", sexOptions, { value: "female" }),
          number("renal_weight", "Peso usado en Cockcroft–Gault (kg)", { value: 65, min: 1, step: 0.1 }),
          number("renal_creatinine", "Creatinina sérica (mg/dL)", { value: 1, min: 0.01, step: 0.01 }),
          number("renal_cystatin", "Cistatina C (mg/L, opcional)", { value: "", min: 0.01, step: 0.01, required: false, help: "Si se informa, CKD-EPI usa la ecuación combinada creatinina–cistatina C." }),
          number("renal_bsa", "Superficie corporal (m², opcional)", { value: "", min: 0.1, max: 4, step: 0.01, required: false, help: "Si se informa, se muestra también GFR absoluta desindexada." }),
        ],
        calculate(values) {
          const input = {
            age: values.renal_age,
            sex: values.renal_sex,
            weightKg: values.renal_weight,
            creatinineMgDl: values.renal_creatinine,
            cystatinCMgL: values.renal_cystatin,
            bsaM2: values.renal_bsa,
          };
          const cg = rules.cockcroftGault(input);
          const ckd = rules.ckdEpi2021(input);
          if (!cg.valid || !ckd.valid) return invalidRuleResult("Función renal", cg, ckd);
          return baseResult({
            title: `CrCl ${fmt(cg.crcl)} mL/min · eGFR ${fmt(ckd.egfr)} mL/min/1,73 m²`,
            detail: "Los resultados no son intercambiables: identificar qué estimación exige el protocolo o el prospecto del fármaco.",
            badge: "función renal",
            metrics: [
              { label: "Cockcroft–Gault", value: `${fmt(cg.crcl)} mL/min` },
              { label: ckd.method, value: `${fmt(ckd.egfr)} mL/min/1,73 m²` },
              ...(ckd.absoluteGfr === null ? [] : [{ label: "GFR absoluta desindexada", value: `${fmt(ckd.absoluteGfr)} mL/min` }]),
              { label: "Peso usado en CG", value: `${fmt(values.renal_weight)} kg` },
            ],
            notes: [
              ckd.absoluteGfr === null
                ? "CKD-EPI está indexado a 1,73 m²; para una dosis que requiera GFR absoluta debe informarse la superficie corporal y desindexarse."
                : "La GFR absoluta se obtuvo como eGFR × superficie corporal / 1,73.",
              "Creatinina no estable, sarcopenia, caquexia, amputaciones o tamaño corporal extremo pueden volver imprecisas ambas estimaciones.",
              "Cerca de un punto de corte clínico, considerar cistatina C o GFR medida según disponibilidad y protocolo.",
            ],
          });
        },
      },
      {
        id: "anc-ctcae-v6",
        number: "G5",
        title: "Recuento absoluto de neutrófilos — CTCAE v6",
        category: "general",
        subtitle: "ANC calculado y grado de neutrófilos disminuidos.",
        source: "NCI CTCAE v6.0 (2025)",
        clinicalUse: "Calcula ANC a partir del hemograma diferencial y lo clasifica con los límites de CTCAE v6.0.",
        fields: [
          number("anc_wbc", "Leucocitos (×10⁹/L)", { value: 3, min: 0, step: 0.01 }),
          number("anc_segmented", "Neutrófilos segmentados (%)", { value: 40, min: 0, max: 100, step: 0.1 }),
          number("anc_bands", "Bandas (%)", { value: 0, min: 0, max: 100, step: 0.1 }),
        ],
        calculate(values) {
          const calculated = rules.absoluteNeutrophilCount({
            wbc10e9L: values.anc_wbc,
            segmentedPercent: values.anc_segmented,
            bandsPercent: values.anc_bands,
          });
          if (!calculated.valid) return invalidRuleResult("ANC / CTCAE v6", calculated);
          const gradeLabel = calculated.grade === 0 ? "Sin grado CTCAE" : `CTCAE grado ${calculated.grade}`;
          return baseResult({
            title: `ANC ${Math.round(calculated.anc)} células/µL`,
            detail: gradeLabel,
            badge: "CTCAE v6",
            severity: calculated.grade >= 3 ? "bad" : calculated.grade > 0 ? "warn" : "good",
            metrics: [
              { label: "ANC", value: `${Math.round(calculated.anc)} células/µL` },
              { label: "Grado", value: gradeLabel },
            ],
            notes: [
              "Usar el ANC directo del laboratorio cuando esté informado; esta fórmula es una estimación a partir del diferencial.",
              "La neutropenia febril es un evento clínico separado y no puede inferirse únicamente con este valor.",
              "Los límites de administración o modificación de un tratamiento dependen del esquema y del protocolo vigente.",
            ],
          });
        },
      },
      {
        id: "khorana-vte",
        number: "G6",
        title: "Khorana — riesgo de VTE",
        category: "general",
        subtitle: "Estratificación basal antes de tratamiento sistémico ambulatorio.",
        source: "Khorana et al.",
        clinicalUse: "Suma sitio tumoral, hemograma basal, uso de estimulantes eritropoyéticos e IMC para clasificar riesgo tromboembólico.",
        fields: [
          select("khorana_site", "Sitio tumoral", [
            option("stomach", "Estómago"),
            option("pancreas", "Páncreas"),
            option("lung", "Pulmón"),
            option("lymphoma", "Linfoma"),
            option("gynecologic", "Ginecológico"),
            option("bladder", "Vejiga"),
            option("testicular", "Testículo"),
            option("other", "Otro sitio"),
          ], { value: "other", wide: true }),
          number("khorana_platelets", "Plaquetas (×10⁹/L)", { value: 250, min: 0, step: 1 }),
          number("khorana_hgb", "Hemoglobina (g/dL)", { value: 12, min: 0, step: 0.1 }),
          number("khorana_wbc", "Leucocitos (×10⁹/L)", { value: 7, min: 0, step: 0.1 }),
          number("khorana_bmi", "IMC (kg/m²)", { value: 25, min: 1, step: 0.1 }),
          checkbox("khorana_esa", "Uso de agente estimulante de eritropoyesis"),
        ],
        calculate(values) {
          const calculated = rules.khorana({
            site: values.khorana_site,
            platelets10e9L: values.khorana_platelets,
            hemoglobinGdl: values.khorana_hgb,
            wbc10e9L: values.khorana_wbc,
            bmi: values.khorana_bmi,
            esa: bool(values.khorana_esa),
          });
          if (!calculated.valid) return invalidRuleResult("Khorana", calculated);
          const factors = Object.entries(calculated.factors).filter(([, points]) => points > 0).length;
          return baseResult({
            title: `Khorana ${calculated.total} · riesgo ${calculated.originalCategory}`,
            detail: "Clasificación original: 0 bajo, 1–2 intermedio y ≥3 alto.",
            badge: "VTE ambulatorio",
            severity: calculated.total >= 3 ? "bad" : calculated.total >= 1 ? "warn" : "good",
            metrics: [
              { label: "Puntaje", value: calculated.total },
              { label: "Categoría original", value: calculated.originalCategory },
              { label: "Componentes con puntos", value: factors },
            ],
            notes: [
              "Población: pacientes ambulatorios con cáncer antes de comenzar quimioterapia sistémica.",
              "El umbral moderno ≥2 abre una evaluación clínica individual; no indica anticoagulación automática.",
              "Valorar por separado hemorragia, interacciones, función renal, tipo de cáncer y situación clínica.",
            ],
          });
        },
      },
      {
        id: "mascc-febrile-neutropenia",
        number: "G7",
        title: "MASCC — neutropenia febril",
        category: "general",
        subtitle: "Riesgo de complicaciones una vez presente la neutropenia febril.",
        source: "MASCC Risk Index",
        clinicalUse: "Integra carga sintomática y condiciones clínicas al inicio de la fiebre para estratificar complicaciones.",
        fields: [
          section("mascc_scope", "Aplicar después de identificar neutropenia febril. La impresión de inestabilidad clínica prevalece sobre el puntaje."),
          select("mascc_burden", "Carga de enfermedad/síntomas", [
            option("5", "Ninguna o leve · 5 puntos"),
            option("3", "Moderada · 3 puntos"),
            option("0", "Grave o moribundo · 0 puntos"),
          ], { value: "5", wide: true }),
          checkbox("mascc_no_hypotension", "Sin hipotensión (PAS >90 mmHg)"),
          checkbox("mascc_no_copd", "Sin EPOC"),
          checkbox("mascc_tumor_fungal", "Tumor sólido o neoplasia hematológica sin infección fúngica invasiva previa", { wide: true }),
          checkbox("mascc_no_dehydration", "Sin deshidratación que requiera fluidos IV"),
          checkbox("mascc_outpatient", "Ambulatorio al comienzo de la fiebre"),
          checkbox("mascc_age_under_60", "Edad menor de 60 años"),
        ],
        calculate(values) {
          const calculated = rules.mascc({
            burdenPoints: values.mascc_burden,
            noHypotension: bool(values.mascc_no_hypotension),
            noCopd: bool(values.mascc_no_copd),
            solidOrNoPriorFungal: bool(values.mascc_tumor_fungal),
            noDehydration: bool(values.mascc_no_dehydration),
            outpatientOnset: bool(values.mascc_outpatient),
            ageUnder60: bool(values.mascc_age_under_60),
          });
          if (!calculated.valid) return invalidRuleResult("MASCC", calculated);
          return baseResult({
            title: `MASCC ${calculated.total}/26`,
            detail: calculated.category,
            badge: "neutropenia febril",
            severity: calculated.lowRisk ? "good" : "bad",
            metrics: [
              { label: "Puntaje", value: `${calculated.total}/26` },
              { label: "Umbral", value: calculated.lowRisk ? "≥21" : "<21" },
            ],
            notes: [
              "Un resultado de bajo riesgo no reemplaza estabilidad, examen, foco infeccioso, comorbilidades ni condiciones para seguimiento.",
              "No usar como predictor de neutropenia antes de la quimioterapia.",
              "No define por sí solo internación, vía antibiótica ni alta.",
            ],
          });
        },
      },
      {
        id: "cisne-febrile-neutropenia",
        number: "G8",
        title: "CISNE — neutropenia febril estable",
        category: "general",
        subtitle: "Riesgo oculto de complicaciones en tumor sólido aparentemente estable.",
        source: "FINITE / CISNE",
        clinicalUse: "Estratifica complicaciones graves en adultos con tumor sólido, neutropenia febril y estabilidad clínica inicial.",
        fields: [
          section("cisne_scope", "Usar solamente si el paciente con tumor sólido parece estable, sin disfunción orgánica, alteraciones vitales ni infección mayor evidente."),
          number("cisne_glucose", "Glucemia inicial (mg/dL)", { value: 100, min: 0, step: 1 }),
          checkbox("cisne_diabetes_steroids", "Diabetes o uso de corticoides"),
          checkbox("cisne_ecog", "ECOG ≥2"),
          checkbox("cisne_copd", "EPOC"),
          checkbox("cisne_cardiovascular", "Enfermedad cardiovascular crónica"),
          checkbox("cisne_mucositis", "Mucositis NCI grado ≥2"),
          checkbox("cisne_monocytes", "Monocitos <200/µL"),
        ],
        calculate(values) {
          const calculated = rules.cisne({
            glucoseMgDl: values.cisne_glucose,
            diabetesOrSteroids: bool(values.cisne_diabetes_steroids),
            ecog2OrMore: bool(values.cisne_ecog),
            copd: bool(values.cisne_copd),
            cardiovascular: bool(values.cisne_cardiovascular),
            mucositisGrade2OrMore: bool(values.cisne_mucositis),
            monocytesUnder200: bool(values.cisne_monocytes),
          });
          if (!calculated.valid) return invalidRuleResult("CISNE", calculated);
          return baseResult({
            title: `CISNE ${calculated.total} · clase ${calculated.riskClass}`,
            detail: `Umbral de hiperglucemia aplicado: ${calculated.glucoseThreshold} mg/dL.`,
            badge: "FN estable",
            severity: calculated.classNumber >= 3 ? "bad" : calculated.classNumber === 2 ? "warn" : "good",
            metrics: [
              { label: "Puntaje", value: calculated.total },
              { label: "Clase", value: calculated.riskClass },
              { label: "Glucemia umbral", value: `${calculated.glucoseThreshold} mg/dL` },
            ],
            notes: [
              "No aplicar a pacientes inestables, neoplasias hematológicas, trasplante o quimioterapia de alta intensidad.",
              "CISNE busca evitar una falsa clasificación de bajo riesgo; no debe retrasar antibióticos.",
              "El resultado no define automáticamente manejo ambulatorio o internación.",
            ],
          });
        },
      },
      {
        id: "palliative-prognostic-index",
        number: "G9",
        title: "Palliative Prognostic Index — PPI",
        category: "general",
        subtitle: "Señal pronóstica en cuidados paliativos avanzados.",
        source: "Palliative Prognostic Index",
        clinicalUse: "Integra PPS, ingesta, edema, disnea de reposo y delirium para contextualizar pronóstico poblacional.",
        fields: [
          select("ppi_pps", "Palliative Performance Scale (PPS)", [100, 90, 80, 70, 60, 50, 40, 30, 20, 10].map((value) => option(String(value), `${value}%`)), { value: "50" }),
          select("ppi_oral", "Ingesta oral", [
            option("normal", "Normal"),
            option("moderate", "Moderadamente reducida"),
            option("severe", "Severamente reducida"),
          ], { value: "normal" }),
          checkbox("ppi_edema", "Edema"),
          checkbox("ppi_dyspnea", "Disnea en reposo"),
          checkbox("ppi_delirium", "Delirium"),
        ],
        calculate(values) {
          const calculated = rules.palliativePrognosticIndex({
            pps: values.ppi_pps,
            oralIntake: values.ppi_oral,
            edema: bool(values.ppi_edema),
            dyspneaAtRest: bool(values.ppi_dyspnea),
            delirium: bool(values.ppi_delirium),
          });
          if (!calculated.valid) return invalidRuleResult("PPI", calculated);
          return baseResult({
            title: `PPI ${fmt(calculated.total, 1)}`,
            detail: calculated.threshold,
            badge: "pronóstico paliativo",
            severity: calculated.total > 6 ? "bad" : calculated.total > 4 ? "warn" : "info",
            metrics: [
              { label: "Puntaje", value: fmt(calculated.total, 1) },
              { label: "Lectura", value: calculated.threshold },
            ],
            notes: [
              "Los puntos de corte describen probabilidades observadas en cohortes; no predicen una fecha individual.",
              "Delirium potencialmente reversible por medicación, infección o trastorno metabólico debe interpretarse con cautela.",
              "No usar el PPI de forma aislada para limitar estudios, hidratación, derivación o tratamientos.",
            ],
          });
        },
      },
      {
        id: "bed-eqd2",
        number: "G10",
        title: "BED y EQD2 del fraccionamiento",
        shortTitle: "BED y EQD2",
        category: "radioterapia",
        subtitle: "Dosis física y equivalencia biológica con el modelo lineal-cuadrático.",
        source: "Modelo LQ · Pangea",
        clinicalUse: "Calcula dosis biológicamente efectiva y dosis equiefectiva en fracciones de 2 Gy para un α/β elegido explícitamente. Reúne las calculadoras BED y EQD2 del módulo original.",
        fields: [
          section("bed_scope", "Fraccionamiento administrado", {
            help: "Ingresá el número de fracciones, la dosis por fracción y el α/β correspondiente al tejido u objetivo que se analiza.",
          }),
          number("bed_fractions", "Número de fracciones", { value: 25, min: 1, step: 1 }),
          number("bed_dose_fraction", "Dosis por fracción (Gy)", { value: 2, min: 0.01, step: 0.01 }),
          number("bed_alpha_beta", "Relación α/β (Gy)", { value: 10, min: 0.1, step: 0.1 }),
        ],
        calculate(values) {
          const calculated = rules.bedEqd2({
            fractions: values.bed_fractions,
            dosePerFraction: values.bed_dose_fraction,
            alphaBeta: values.bed_alpha_beta,
          });
          if (!calculated.valid) return invalidRuleResult("BED / EQD2", calculated);
          return baseResult({
            title: `BED ${fmt(calculated.bed, 2)} · EQD2 ${fmt(calculated.eqd2, 2)}`,
            detail: `Dosis física total: ${fmt(calculated.totalDose, 2)} Gy.`,
            badge: "lineal-cuadrático",
            severity: values.bed_dose_fraction > 5 ? "warn" : "info",
            metrics: [
              { label: "Dosis total", value: `${fmt(calculated.totalDose, 2)} Gy` },
              { label: "BED", value: `${fmt(calculated.bed, 2)} Gy (α/β ${fmt(values.bed_alpha_beta, 1)})` },
              { label: "EQD2", value: `${fmt(calculated.eqd2, 2)} Gy (α/β ${fmt(values.bed_alpha_beta, 1)})` },
              { label: "α/β", value: `${fmt(values.bed_alpha_beta, 1)} Gy` },
            ],
            notes: [
              "El resultado depende por completo del α/β seleccionado y del tejido o objetivo analizado.",
              values.bed_dose_fraction > 5
                ? "Dosis por fracción >5 Gy: el modelo LQ sigue siendo una estimación y su extrapolación es especialmente incierta en hipofraccionamiento extremo."
                : "El modelo LQ es una aproximación; la incertidumbre aumenta al alejarse del fraccionamiento convencional.",
              "No incorpora repoblación, reparación incompleta, tiempo total, heterogeneidad de dosis, recuperación tisular ni reirradiación.",
              "No constituye una prescripción ni un límite automático de órgano a riesgo.",
            ],
          });
        },
      },
      {
        id: "qtc-fridericia",
        number: "G11",
        title: "QT corregido — Fridericia",
        category: "general",
        subtitle: "QTcF para vigilancia cardio-oncológica.",
        source: "ESC Cardio-Oncology / Fridericia",
        clinicalUse: "Corrige el QT medido por frecuencia cardíaca y lo contextualiza con límites de referencia y cambio desde basal.",
        fields: [
          number("qtc_qt", "QT medido (ms)", { value: 400, min: 1, step: 1 }),
          number("qtc_hr", "Frecuencia cardíaca (lpm)", { value: 70, min: 1, step: 1 }),
          select("qtc_sex", "Sexo para límite de referencia", sexOptions, { value: "female" }),
          number("qtc_baseline", "QTcF basal (ms, opcional)", { value: "", min: 1, step: 1, required: false }),
        ],
        calculate(values) {
          const calculated = rules.qtcFridericia({
            qtMs: values.qtc_qt,
            heartRate: values.qtc_hr,
            sex: values.qtc_sex,
            baselineQtcMs: values.qtc_baseline,
          });
          if (!calculated.valid) return invalidRuleResult("QTcF", calculated);
          const deltaText = calculated.delta === null ? "No informado" : `${calculated.delta >= 0 ? "+" : ""}${fmt(calculated.delta, 0)} ms`;
          return baseResult({
            title: `QTcF ${fmt(calculated.qtcF, 0)} ms`,
            detail: calculated.band,
            badge: "Fridericia",
            severity: calculated.qtcF >= 500 ? "bad" : calculated.qtcF >= 480 ? "warn" : "info",
            metrics: [
              { label: "QTcF", value: `${fmt(calculated.qtcF, 0)} ms` },
              { label: "RR", value: `${fmt(calculated.rrSeconds, 3)} s` },
              { label: "Límite por sexo", value: `${calculated.upperReference} ms` },
              { label: "Cambio desde basal", value: deltaText },
            ],
            notes: [
              "QTcF 480–499 ms requiere revisar causas reversibles y monitorización; ≥500 ms se asocia con mayor riesgo de torsade.",
              "QRS ancho, marcapasos, fibrilación auricular o trazado dudoso requieren medición e interpretación especializada.",
              "El prospecto del fármaco y la evaluación clínica determinan la conducta; el cálculo no indica suspensión automática.",
            ],
          });
        },
      },
      {
        id: "nottingham-prognostic-index",
        number: "M1",
        title: "Nottingham Prognostic Index — NPI",
        category: "mama",
        subtitle: "Índice anatomopatológico clásico en cáncer de mama invasivo.",
        source: "Nottingham Prognostic Index",
        clinicalUse: "Integra tamaño invasivo, grado histológico y carga ganglionar para asignar un grupo pronóstico histórico.",
        fields: [
          number("npi_size", "Tamaño invasivo (cm)", { value: 2, min: 0.01, step: 0.1 }),
          select("npi_grade", "Grado histológico", gradeOptions, { value: "2" }),
          number("npi_nodes", "Ganglios positivos", { value: 0, min: 0, step: 1 }),
        ],
        calculate(values) {
          const calculated = rules.nottinghamPrognosticIndex({
            sizeCm: values.npi_size,
            grade: values.npi_grade,
            positiveNodes: values.npi_nodes,
          });
          if (!calculated.valid) return invalidRuleResult("NPI", calculated);
          return baseResult({
            title: `NPI ${fmt(calculated.total, 2)} · ${calculated.group}`,
            detail: "Índice = 0,2 × tamaño en cm + grado + categoría ganglionar.",
            badge: "mama invasiva",
            metrics: [
              { label: "NPI", value: fmt(calculated.total, 2) },
              { label: "Grupo", value: calculated.group },
              { label: "Categoría ganglionar", value: calculated.nodeScore },
            ],
            notes: [
              "Es un modelo pronóstico histórico; no incorpora ER, HER2, Ki-67, genómica ni tratamientos contemporáneos.",
              "Usar tamaño del componente invasivo y grado histológico definitivo.",
              "No determina por sí solo indicación o intensidad de tratamiento.",
            ],
          });
        },
      },
      {
        id: "residual-cancer-burden-experimental",
        number: "M2",
        title: "Residual Cancer Burden — RCB experimental",
        category: "mama",
        subtitle: "Cálculo local experimental de enfermedad residual posneoadyuvancia.",
        source: "RCB / MD Anderson",
        clinicalUse: "Integra lecho tumoral, celularidad invasiva y enfermedad ganglionar residual después de tratamiento neoadyuvante.",
        fields: [
          section("rcb_warning", "IMPLEMENTACIÓN EXPERIMENTAL. Confirmar siempre el valor y la clase con la calculadora oficial de MD Anderson antes de documentarlos o utilizarlos."),
          number("rcb_d1", "Diámetro 1 del lecho tumoral (mm)", { value: 20, min: 0, step: 0.1 }),
          number("rcb_d2", "Diámetro 2 del lecho tumoral (mm)", { value: 15, min: 0, step: 0.1 }),
          number("rcb_cellularity", "Celularidad global del lecho (%)", { value: 10, min: 0, max: 100, step: 0.1 }),
          number("rcb_in_situ", "Componente in situ dentro de la celularidad (%)", { value: 0, min: 0, max: 100, step: 0.1 }),
          number("rcb_nodes", "Ganglios positivos", { value: 0, min: 0, step: 1 }),
          number("rcb_largest_met", "Mayor metástasis ganglionar (mm; 0 si N0)", { value: 0, min: 0, step: 0.1, wide: true }),
        ],
        calculate(values) {
          const calculated = rules.residualCancerBurden({
            d1Mm: values.rcb_d1,
            d2Mm: values.rcb_d2,
            cellularityPercent: values.rcb_cellularity,
            inSituPercent: values.rcb_in_situ,
            positiveNodes: values.rcb_nodes,
            largestMetMm: values.rcb_largest_met,
          });
          if (!calculated.valid) return invalidRuleResult("RCB experimental", calculated);
          return baseResult({
            title: `${calculated.rcbClass} · índice experimental ${fmt(calculated.total, 3)}`,
            detail: "Resultado local para control técnico; requiere confirmación externa.",
            badge: "experimental",
            severity: "warn",
            metrics: [
              { label: "Clase calculada", value: calculated.rcbClass },
              { label: "Índice", value: fmt(calculated.total, 3) },
              { label: "Diámetro geométrico", value: `${fmt(calculated.dPrim, 2)} mm` },
              { label: "Fracción invasiva", value: fmt(calculated.fInv, 4) },
            ],
            notes: [
              "Confirmar con la <a href=\"https://www3.mdanderson.org/app/medcalc/index.cfm?pagename=jsconvert3\" target=\"_blank\" rel=\"noopener\">calculadora oficial de MD Anderson</a>.",
              "La medición debe provenir de evaluación anatomopatológica estandarizada del lecho posneoadyuvancia.",
              "No usar esta implementación experimental para indicar un tratamiento automático.",
            ],
          });
        },
      },
      {
        id: "pepi-breast",
        number: "M3",
        title: "PEPI — Preoperative Endocrine Prognostic Index",
        category: "mama",
        subtitle: "Respuesta anatomopatológica después de endocrinoterapia neoadyuvante.",
        source: "PEPI",
        clinicalUse: "Combina pT, ganglios, Ki-67 y ER Allred residuales después de endocrinoterapia neoadyuvante.",
        fields: [
          section("pepi_scope", "Aplicar a cáncer de mama ER positivo tratado con endocrinoterapia neoadyuvante y con evaluación quirúrgica residual."),
          select("pepi_pt", "pT posendocrinoterapia", [1, 2, 3, 4].map((value) => option(`pt${value}`, `pT${value}`)), { value: "pt1" }),
          select("pepi_nodes", "Ganglios residuales positivos", yesNoOptions, { value: "no" }),
          number("pepi_ki67", "Ki-67 residual (%)", { value: 2, min: 0, max: 100, step: 0.1 }),
          number("pepi_er_allred", "ER Allred residual (0–8)", { value: 8, min: 0, max: 8, step: 1 }),
        ],
        calculate(values) {
          const calculated = rules.pepi({
            pt: values.pepi_pt,
            nodePositive: values.pepi_nodes,
            ki67Percent: values.pepi_ki67,
            erAllred: values.pepi_er_allred,
          });
          if (!calculated.valid) return invalidRuleResult("PEPI", calculated);
          return baseResult({
            title: `${calculated.group} · ${calculated.total} puntos`,
            detail: "Puntaje posendocrinoterapia basado en la pieza quirúrgica y biomarcadores residuales.",
            badge: "endocrino neoadyuvante",
            severity: calculated.total === 0 ? "good" : calculated.total <= 3 ? "warn" : "bad",
            metrics: [
              { label: "PEPI", value: calculated.total },
              { label: "Grupo", value: calculated.group },
              { label: "Puntos pT", value: calculated.factors.pt },
              { label: "Puntos ganglios", value: calculated.factors.nodes },
              { label: "Puntos Ki-67", value: calculated.factors.ki67 },
              { label: "Puntos ER", value: calculated.factors.er },
            ],
            notes: [
              "No aplicar al diagnóstico basal, después de quimioterapia neoadyuvante ni fuera de enfermedad hormonosensible.",
              "Ki-67 requiere una medición anatomopatológica fiable y comparable.",
              "PEPI es pronóstico y no determina por sí solo una conducta adyuvante.",
            ],
          });
        },
      },
      {
        id: "cts5-breast",
        number: "M4",
        title: "CTS5 — recurrencia tardía",
        category: "mama",
        subtitle: "Riesgo residual entre los años 5 y 10.",
        source: "CTS5, ATAC / BIG 1-98",
        clinicalUse: "Calcula el score clínico post-5 años en cáncer de mama ER positivo libre de recurrencia después de endocrinoterapia.",
        fields: [
          section("cts5_scope", "Uso principal validado: mujer posmenopáusica con cáncer de mama ER positivo, sin recurrencia después de 5 años de endocrinoterapia."),
          number("cts5_age", "Edad al diagnóstico (años)", { value: 60, min: 18, step: 1 }),
          number("cts5_size", "Tamaño tumoral (mm)", { value: 20, min: 0.1, step: 0.1 }),
          select("cts5_grade", "Grado histológico", gradeOptions, { value: "2" }),
          number("cts5_nodes", "Ganglios positivos", { value: 0, min: 0, step: 1 }),
        ],
        calculate(values) {
          const calculated = rules.cts5({
            age: values.cts5_age,
            sizeMm: values.cts5_size,
            grade: values.cts5_grade,
            positiveNodes: values.cts5_nodes,
          });
          if (!calculated.valid) return invalidRuleResult("CTS5", calculated);
          const riskBand = calculated.group === "bajo" ? "<5%" : calculated.group === "intermedio" ? "5–10%" : ">10%";
          return baseResult({
            title: `CTS5 ${fmt(calculated.total, 2)} · ${calculated.group}`,
            detail: `Banda publicada de recurrencia distante en años 5–10: ${riskBand}.`,
            badge: "recurrencia tardía",
            severity: calculated.group === "alto" ? "bad" : calculated.group === "intermedio" ? "warn" : "good",
            metrics: [
              { label: "Score", value: fmt(calculated.total, 2) },
              { label: "Grupo", value: calculated.group },
              { label: "Categoría ganglionar", value: calculated.nodeCategory },
              { label: "Tamaño usado", value: `${fmt(calculated.cappedSizeMm, 1)} mm` },
            ],
            notes: [
              calculated.sizeWasCapped ? "El tamaño se limitó a 30 mm, como especifica el modelo." : "El tamaño ingresado no requirió el tope de 30 mm.",
              "CTS5 es pronóstico; no predice directamente el beneficio de prolongar endocrinoterapia.",
              "Puede requerir recalibración fuera de las poblaciones originales y debe usarse con cautela en premenopausia o HER2 positivo.",
            ],
          });
        },
      },
      {
        id: "monarche-cohort-1",
        number: "M5",
        title: "monarchE — criterios de cohorte 1",
        category: "mama",
        subtitle: "Reconstrucción de criterios clínico-patológicos del ensayo.",
        source: "monarchE cohort 1",
        clinicalUse: "Comprueba alcance biológico y anatomía de alto riesgo utilizados para la cohorte 1 de monarchE.",
        fields: [
          checkbox("monarche_hr_positive", "Receptores hormonales positivos"),
          checkbox("monarche_her2_negative", "HER2 negativo"),
          checkbox("monarche_early", "Enfermedad temprana no metastásica"),
          number("monarche_nodes", "Ganglios axilares positivos", { value: 1, min: 0, step: 1 }),
          number("monarche_size", "Tamaño tumoral (mm)", { value: 30, min: 0.1, step: 0.1 }),
          select("monarche_grade", "Grado histológico", gradeOptions, { value: "2" }),
        ],
        calculate(values) {
          const calculated = rules.monarchE({
            hrPositive: bool(values.monarche_hr_positive),
            her2Negative: bool(values.monarche_her2_negative),
            earlyNonMetastatic: bool(values.monarche_early),
            positiveAxillaryNodes: values.monarche_nodes,
            sizeMm: values.monarche_size,
            grade: values.monarche_grade,
          });
          if (!calculated.valid) return invalidRuleResult("monarchE cohorte 1", calculated);
          return baseResult({
            title: calculated.meetsCohort1 ? "Coincide con cohorte 1" : "No coincide con cohorte 1",
            detail: calculated.meetsCohort1
              ? "Cumple el alcance biológico y la definición anatómica reconstruida del ensayo."
              : "Falta alcance biológico, anatomía de alto riesgo o ambos.",
            badge: "criterios de ensayo",
            severity: calculated.meetsCohort1 ? "info" : "warn",
            metrics: [
              { label: "Alcance biológico", value: calculated.biologicScope ? "Sí" : "No" },
              { label: "Anatomía de alto riesgo", value: calculated.highRiskAnatomy ? "Sí" : "No" },
              { label: "Ki-67 requerido por cohorte 1", value: calculated.ki67Required ? "Sí" : "No" },
            ],
            notes: [
              "Criterio anatómico: ≥4 ganglios, o 1–3 ganglios con grado 3 o tumor ≥50 mm.",
              "Esta pantalla reproduce criterios de cohorte, no toda la elegibilidad regulatoria, temporal o clínica.",
              "Coincidir con el ensayo no constituye una indicación automática de tratamiento.",
            ],
          });
        },
      },
      {
        id: "olympia-cpseg",
        number: "M6",
        title: "OlympiA y CPS+EG",
        category: "mama",
        subtitle: "Criterios de alto riesgo según escenario neoadyuvante o adyuvante.",
        source: "OlympiA / CPS+EG",
        clinicalUse: "Reconstruye el alcance basal y los criterios de alto riesgo usados en OlympiA; calcula CPS+EG en el escenario neoadyuvante HR positivo.",
        fields: [
          section("olympia_scope", "Seleccionar el escenario correcto. Los campos no utilizados por ese escenario se ignoran, pero la elegibilidad completa debe verificarse externamente."),
          select("scenario", "Escenario", [
            option("neo_tnbc", "Neoadyuvancia · triple negativo"),
            option("neo_hr", "Neoadyuvancia · HR positivo"),
            option("adj_tnbc", "Cirugía inicial/adyuvancia · triple negativo"),
            option("adj_hr", "Cirugía inicial/adyuvancia · HR positivo"),
          ], { value: "neo_hr", wide: true, keepDefault: true }),
          checkbox("olympia_gbrca", "Variante germinal patogénica/probablemente patogénica BRCA1/2", { wide: true }),
          checkbox("olympia_her2_negative", "HER2 negativo"),
          checkbox("olympia_residual", "Enfermedad invasiva residual posneoadyuvancia"),
          select("olympia_c_stage", "Grupo clínico pretratamiento (para CPS+EG)", [
            option("i_iia", "I–IIA · 0 puntos"),
            option("iib_iiia", "IIB–IIIA · 1 punto"),
            option("iiib_iiic", "IIIB–IIIC · 2 puntos"),
          ], { value: "i_iia", scenario: "neo_hr" }),
          select("olympia_p_stage", "Grupo patológico postratamiento (para CPS+EG)", [
            option("zero_i", "0–I · 0 puntos"),
            option("iia_iiib", "IIA–IIIB · 1 punto"),
            option("iiic", "IIIC · 2 puntos"),
          ], { value: "zero_i", scenario: "neo_hr" }),
          select("olympia_er", "Estado ER para CPS+EG", [option("positive", "ER positivo"), option("negative", "ER negativo")], { value: "positive", scenario: "neo_hr" }),
          select("olympia_nuclear_grade", "Grado nuclear para CPS+EG", gradeOptions, { value: "2", scenario: "neo_hr" }),
          number("olympia_nodes_tnbc", "Ganglios positivos", { value: 0, min: 0, step: 1, scenario: "adj_tnbc" }),
          number("olympia_size", "Tamaño tumoral (cm)", { value: 2, min: 0.01, step: 0.1, scenario: "adj_tnbc" }),
          number("olympia_nodes_hr", "Ganglios positivos", { value: 4, min: 0, step: 1, scenario: "adj_hr" }),
        ],
        calculate(values) {
          const scenario = values.scenario;
          const calculated = rules.olympia({
            scenario,
            germlineBrcaPathogenic: bool(values.olympia_gbrca),
            her2Negative: bool(values.olympia_her2_negative),
            residualInvasive: bool(values.olympia_residual),
            clinicalStageGroup: values.olympia_c_stage,
            pathologicStageGroup: values.olympia_p_stage,
            erStatus: values.olympia_er,
            nuclearGrade: values.olympia_nuclear_grade,
            positiveNodes: scenario === "adj_tnbc" ? values.olympia_nodes_tnbc : values.olympia_nodes_hr,
            sizeCm: values.olympia_size,
          });
          if (!calculated.valid) return invalidRuleResult("OlympiA / CPS+EG", calculated);
          const cpsMetric = calculated.cpsEg === null ? "No aplica" : calculated.cpsEg;
          return baseResult({
            title: calculated.meetsTrialCriteria ? "Coincide con criterios reconstruidos" : "No coincide con criterios reconstruidos",
            detail: calculated.cpsEg === null ? "El escenario seleccionado no utiliza CPS+EG." : `CPS+EG calculado: ${calculated.cpsEg}.`,
            badge: "criterios de ensayo",
            severity: calculated.meetsTrialCriteria ? "info" : "warn",
            metrics: [
              { label: "gBRCA + HER2 negativo", value: calculated.baseScope ? "Sí" : "No" },
              { label: "Criterio de alto riesgo", value: calculated.highRisk ? "Sí" : "No" },
              { label: "CPS+EG", value: cpsMetric },
            ],
            notes: [
              "Neoadyuvancia HR positiva: requiere enfermedad invasiva residual y CPS+EG ≥3 en la reconstrucción del ensayo.",
              "Los otros escenarios utilizan definiciones anatómicas específicas; verificar subtipo, estadio, tratamiento previo, temporalidad y genética.",
              "Coincidir con criterios históricos del ensayo no constituye una indicación automática de tratamiento.",
            ],
          });
        },
      },
      {
        id: "international-prognostic-index",
        number: "H1",
        title: "International Prognostic Index — IPI",
        category: "hematologia",
        subtitle: "Índice clínico clásico para linfomas agresivos.",
        source: "International Prognostic Index",
        clinicalUse: "Suma edad, estadio, LDH, ECOG y sitios extranodales para asignar el grupo IPI clásico.",
        fields: [
          number("ipi_age", "Edad (años)", { value: 60, min: 0, step: 1 }),
          select("ipi_stage", "Estadio Ann Arbor", [1, 2, 3, 4].map((value) => option(String(value), `Estadio ${value}`)), { value: "2" }),
          checkbox("ipi_ldh", "LDH por encima del límite superior normal"),
          select("ipi_ecog", "ECOG", ecogOptions, { value: "1" }),
          number("ipi_extranodal", "Número de sitios extranodales", { value: 0, min: 0, step: 1 }),
        ],
        calculate(values) {
          const calculated = rules.internationalPrognosticIndex({
            age: values.ipi_age,
            stage: values.ipi_stage,
            ldhAboveUpperLimit: bool(values.ipi_ldh),
            ecog: values.ipi_ecog,
            extranodalSites: values.ipi_extranodal,
          });
          if (!calculated.valid) return invalidRuleResult("IPI", calculated);
          return baseResult({
            title: `IPI ${calculated.total}/5 · ${calculated.group}`,
            detail: "Grupo del IPI internacional clásico.",
            badge: "linfoma agresivo",
            severity: calculated.total >= 4 ? "bad" : calculated.total >= 2 ? "warn" : "good",
            metrics: [
              { label: "Puntaje", value: `${calculated.total}/5` },
              { label: "Grupo", value: calculated.group },
              { label: "Factores presentes", value: Object.values(calculated.factors).filter(Boolean).length },
            ],
            notes: [
              "Población original: linfomas no Hodgkin agresivos; la calibración absoluta cambia con subtipo y era terapéutica.",
              "No reemplaza índices específicos como NCCN-IPI, CNS-IPI ni la clasificación biológica del linfoma.",
              "El IPI es pronóstico y no selecciona automáticamente un régimen.",
            ],
          });
        },
      },
      {
        id: "r2-iss-myeloma",
        number: "H2",
        title: "R2-ISS — mieloma múltiple",
        category: "hematologia",
        subtitle: "Revised 2nd International Staging System.",
        source: "European Myeloma Network R2-ISS",
        clinicalUse: "Integra ISS, LDH y alteraciones citogenéticas para estratificación pronóstica del mieloma múltiple recién diagnosticado.",
        fields: [
          number("r2iss_beta2", "β2-microglobulina (mg/L)", { value: 3, min: 0.01, step: 0.01 }),
          number("r2iss_albumin", "Albúmina (g/dL)", { value: 4, min: 0.01, step: 0.01 }),
          checkbox("r2iss_del17p", "del(17p)"),
          checkbox("r2iss_high_ldh", "LDH por encima del límite superior normal"),
          checkbox("r2iss_t414", "t(4;14)"),
          checkbox("r2iss_1q", "Ganancia o amplificación 1q"),
        ],
        calculate(values) {
          const calculated = rules.r2Iss({
            beta2MgL: values.r2iss_beta2,
            albuminGdl: values.r2iss_albumin,
            del17p: bool(values.r2iss_del17p),
            ldhAboveUpperLimit: bool(values.r2iss_high_ldh),
            t414: bool(values.r2iss_t414),
            oneQGainAmp: bool(values.r2iss_1q),
          });
          if (!calculated.valid) return invalidRuleResult("R2-ISS", calculated);
          return baseResult({
            title: `${calculated.stage} · ${fmt(calculated.total, 1)} puntos`,
            detail: `ISS basal derivado: estadio ${calculated.iss}.`,
            badge: "mieloma múltiple",
            severity: calculated.stage === "R2-ISS IV" ? "bad" : calculated.stage === "R2-ISS III" ? "warn" : "info",
            metrics: [
              { label: "R2-ISS", value: calculated.stage },
              { label: "Puntaje", value: fmt(calculated.total, 1) },
              { label: "ISS derivado", value: calculated.iss },
            ],
            notes: [
              "Población: mieloma múltiple recién diagnosticado con estudios citogenéticos adecuados.",
              "La calidad y sensibilidad de FISH, el umbral de del(17p) y la disponibilidad de 1q deben documentarse.",
              "R2-ISS es pronóstico poblacional; no define por sí solo tratamiento, trasplante ni mantenimiento.",
            ],
          });
        },
      },
    ];

    if (tools.length !== 16) {
      throw new Error(`createOncologyToolsGeneral debe devolver 16 entradas y generó ${tools.length}.`);
    }
    return tools;
  }

  return createOncologyToolsGeneral;
});
