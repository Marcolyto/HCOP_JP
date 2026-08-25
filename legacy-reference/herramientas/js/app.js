(function () {
  if (new URLSearchParams(window.location.search).get("embedded") === "1") {
    document.body.classList.add("embedded-tools");
  }

  const $ = (id) => document.getElementById(id);
  const rules = window.ClinicalRules;
  if (!rules) throw new Error("No se pudo cargar el motor local de reglas clinicas.");
  const clamp = (value, min = 0, max = 100) => Math.max(min, Math.min(max, value));
  const pct = (value, digits = 0) => `${Number(value).toFixed(digits)}%`;
  const num = (value, fallback = 0) => {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  };
  const severity = (score, low = 34, high = 67) => score < low ? "good" : score < high ? "warn" : "bad";
  const escapeAttribute = (value) => String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");

  function ecogDescription(ecog) {
    const descriptions = {
      0: "Actividad plena, sin restricción.",
      1: "Restricción para actividad física intensa; ambulatorio y capaz de trabajo liviano o sedentario.",
      2: "Ambulatorio y capaz de autocuidado; no puede trabajar; en cama o silla menos del 50% del día.",
      3: "Capaz de autocuidado limitado; en cama o silla más del 50% del día.",
      4: "Completamente dependiente; confinado a cama o silla.",
      5: "Fallecido.",
    };
    return descriptions[ecog] || "Seleccioná un valor ECOG.";
  }

  function kpsDescription(kps) {
    const descriptions = {
      100: "Normal, sin síntomas ni signos de enfermedad.",
      90: "Actividad normal; síntomas o signos mínimos.",
      80: "Actividad normal con esfuerzo; algunos síntomas o signos.",
      70: "Se cuida solo; no puede realizar actividad normal o trabajo activo.",
      60: "Requiere ayuda ocasional, pero puede cubrir la mayoría de sus necesidades.",
      50: "Requiere ayuda considerable y cuidados médicos frecuentes.",
      40: "Incapacitado; requiere cuidados especiales.",
      30: "Severamente incapacitado; internación indicada aunque no haya muerte inminente.",
      20: "Muy enfermo; requiere internación y tratamiento de soporte activo.",
      10: "Moribundo; proceso fatal progresivo.",
      0: "Fallecido.",
    };
    return descriptions[kps] || "Seleccioná un valor Karnofsky.";
  }

  const option = (value, label) => ({ value, label });
  const field = (id, label, type, extra = {}) => ({ id, label, type, ...extra });
  const text = (id, label, extra = {}) => field(id, label, "text", extra);
  const textarea = (id, label, extra = {}) => field(id, label, "textarea", extra);
  const number = (id, label, extra = {}) => field(id, label, "number", extra);
  const select = (id, label, options, extra = {}) => field(id, label, "select", { options, ...extra });
  const checkbox = (id, label, extra = {}) => field(id, label, "checkbox", extra);
  const section = (id, label, extra = {}) => field(id, label, "section", { wide: true, ...extra });

  const cTOptions = [
    option("t1", "cT1"),
    option("t2a", "cT2a"),
    option("t2b", "cT2b"),
    option("t2c", "cT2c"),
    option("t3", "cT3"),
    option("t4", "cT4"),
  ];
  const ggOptions = [1, 2, 3, 4, 5].map((v) => option(String(v), `GG ${v}`));
  const ipssFrequencyOptions = [
    option("0", "0 · Nunca"),
    option("1", "1 · Menos de 1 de cada 5 veces"),
    option("2", "2 · Menos de la mitad de las veces"),
    option("3", "3 · Aproximadamente la mitad"),
    option("4", "4 · Más de la mitad de las veces"),
    option("5", "5 · Casi siempre"),
  ];
  const ipssNocturiaOptions = [0,1,2,3,4,5].map((value) => option(String(value), `${value} · ${value === 0 ? "Ninguna" : value === 1 ? "Una vez" : value === 5 ? "Cinco o más veces" : `${value} veces`}`));
  const shimFrequencyOptions = [
    option("1", "1 · Casi nunca o nunca"), option("2", "2 · Pocas veces"), option("3", "3 · A veces"), option("4", "4 · La mayoría de las veces"), option("5", "5 · Casi siempre o siempre"),
  ];
  const shimConfidenceOptions = [
    option("1", "1 · Muy baja"), option("2", "2 · Baja"), option("3", "3 · Moderada"), option("4", "4 · Alta"), option("5", "5 · Muy alta"),
  ];
  const shimDifficultyOptions = [
    option("1", "1 · Extremadamente difícil"), option("2", "2 · Muy difícil"), option("3", "3 · Difícil"), option("4", "4 · Algo difícil"), option("5", "5 · No fue difícil"),
  ];

  function result({ title, detail, badge = "Resultado", score = 0, severity: sev = "info", metrics = [], notes = [], scoreName = "Señal integrada", showScore = true }) {
    return { title, detail, badge, score: clamp(score), severity: sev, metrics, notes, scoreName, showScore };
  }

  const MSKCC_PROSTATE_NOMOGRAMS = {
    pre: {
      label: "Pre-prostatectomia radical",
      href: "https://www.mskcc.org/nomograms/prostate/pre_op",
      question: "Cancer de prostata diagnosticado y sin tratamiento iniciado; estima extension y resultados luego de prostatectomia radical.",
      required: [
        ["msk_pre_no_hormone", "Hormonoterapia perioperatoria: documentar si/no"],
        ["msk_pre_no_radiation", "Radioterapia perioperatoria: documentar si/no"],
        ["msk_pre_age", "Edad"],
        ["msk_pre_psa", "PSA previo a la biopsia que diagnostico el cancer"],
        ["msk_pre_gleason_primary", "Patron Gleason primario en biopsia"],
        ["msk_pre_gleason_secondary", "Patron Gleason secundario en biopsia"],
        ["msk_pre_stage", "Estadio clinico T por tacto rectal, AJCC 7/2010"],
      ],
      optional: [
        ["msk_pre_positive_cores", "Numero de cilindros positivos"],
        ["msk_pre_negative_cores", "Numero de cilindros negativos"],
      ],
    },
    post: {
      label: "Post-prostatectomia radical",
      href: "https://www.mskcc.org/nomograms/prostate/post_op",
      question: "Luego de prostatectomia radical, con PSA indetectable; estima probabilidad de permanecer libre de recurrencia.",
      required: [
        ["msk_post_no_hormone", "Hormonoterapia recibida o planificada: documentar si/no"],
        ["msk_post_no_radiation", "Radioterapia recibida o planificada: documentar si/no"],
        ["msk_post_preop_psa", "PSA preoperatorio"],
        ["msk_post_age_surgery", "Edad al momento de la cirugia"],
        ["msk_post_months_undetectable", "Meses desde cirugia sin PSA detectable ni ascenso"],
        ["msk_post_gleason_primary", "Patron Gleason primario en pieza"],
        ["msk_post_gleason_secondary", "Patron Gleason secundario en pieza"],
        ["msk_post_margins", "Margenes quirurgicos positivos: si/no"],
        ["msk_post_ece", "Extension extracapsular: si/no"],
        ["msk_post_svi", "Invasion de vesiculas seminales: si/no"],
        ["msk_post_nodes", "Ganglios pelvicos positivos: si/no"],
      ],
      optional: [
        ["msk_post_clinical_stage", "Estadio clinico T preoperatorio"],
        ["msk_post_biopsy_gleason_primary", "Patron Gleason primario en biopsia"],
        ["msk_post_biopsy_gleason_secondary", "Patron Gleason secundario en biopsia"],
      ],
    },
    salvage: {
      label: "PSA en ascenso post-prostatectomia",
      href: "https://www.mskcc.org/nomograms/prostate/biochemical_recurrence",
      question: "Recaida bioquimica luego de prostatectomia radical; estima riesgo de muerte por cancer de prostata desde el inicio del ascenso de PSA.",
      required: [
        ["msk_salvage_no_preop_hormone", "Hormonoterapia pre-RP: documentar si/no"],
        ["msk_salvage_no_preop_radiation", "Radioterapia pre-RP: documentar si/no"],
        ["msk_salvage_no_postop_hormone", "Hormonoterapia post-RP antes del ascenso de PSA: documentar si/no"],
        ["msk_salvage_no_postop_radiation", "Radioterapia post-RP antes del ascenso de PSA: documentar si/no"],
        ["msk_salvage_preop_psa", "PSA preoperatorio"],
        ["msk_salvage_ece", "Extension extracapsular en pieza: si/no"],
        ["msk_salvage_nodes", "Ganglios pelvicos positivos: si/no"],
        ["msk_salvage_svi", "Invasion de vesiculas seminales: si/no"],
        ["msk_salvage_gleason_primary", "Patron Gleason primario en pieza"],
        ["msk_salvage_gleason_secondary", "Patron Gleason secundario en pieza"],
        ["msk_salvage_margins", "Margenes positivos: si/no"],
        ["msk_salvage_age_bcr", "Edad al detectar recaida bioquimica"],
        ["msk_salvage_months_to_bcr", "Meses desde cirugia hasta recaida bioquimica"],
        ["msk_salvage_psa_bcr", "PSA al momento de recaida bioquimica"],
      ],
      optional: [
        ["msk_salvage_psadt", "PSA doubling time en meses"],
      ],
    },
    biopsy: {
      label: "Riesgo de cancer de alto grado en biopsia",
      href: "https://www.mskcc.org/nomograms/prostate/biopsy_risk_dynamic",
      question: "Hombre evaluado por urologo y considerado candidato a biopsia; estima probabilidad de cancer de alto grado.",
      required: [
        ["msk_biopsy_psa", "PSA mas reciente"],
        ["msk_biopsy_age", "Edad"],
        ["msk_biopsy_african_ancestry", "Ascendencia africana: si/no"],
        ["msk_biopsy_dre", "Tacto rectal sospechoso: si/no/no seguro"],
        ["msk_biopsy_prior_negative", "Biopsia previa negativa: si/no/no seguro"],
        ["msk_biopsy_family", "Familiar de primer grado con cancer de prostata: si/no/no seguro"],
      ],
    },
    psadt: {
      label: "PSA doubling time",
      href: "https://www.mskcc.org/nomograms/prostate/psa_doubling_time",
      question: "Calcula velocidad de ascenso del PSA y tiempo de duplicacion a partir de valores seriados con fecha.",
      required: [
        ["msk_psadt_dates", "Fechas de cada laboratorio en formato mes/dia/anio"],
        ["msk_psadt_values", "Valores de PSA correspondientes, uno por fecha"],
        ["msk_psadt_minimum_series", "Al menos dos mediciones comparables; idealmente tres o mas"],
      ],
    },
    life: {
      label: "Expectativa de vida masculina",
      href: "https://www.mskcc.org/nomograms/prostate/male_life_expectancy",
      question: "Antes de tratamiento, integra cancer de prostata y salud general para discutir beneficio esperado y riesgo competitivo a 15 anios.",
      required: [
        ["msk_life_no_hormone", "Hormonoterapia recibida o planificada: documentar si/no"],
        ["msk_life_no_radiation", "Radioterapia recibida o planificada: documentar si/no"],
        ["msk_life_age", "Edad"],
        ["msk_life_psa", "PSA mas reciente"],
        ["msk_life_grade", "Grado/Gleason: 6, 3+4, 4+3, 8, 9 o 10"],
        ["msk_life_t_stage", "Estadio T clinico"],
        ["msk_life_m_stage", "Estadio M: M0 o M1"],
        ["msk_life_angina", "Angina/dolor toracico: si/no"],
        ["msk_life_mi", "Infarto: si/no"],
        ["msk_life_chf", "Insuficiencia cardiaca: si/no"],
        ["msk_life_valve", "Valvulopatia significativa: si/no"],
        ["msk_life_afib", "Fibrilacion auricular/arritmia: si/no"],
        ["msk_life_aaa", "Aneurisma de aorta abdominal: si/no"],
        ["msk_life_diabetes", "Diabetes: si/no"],
        ["msk_life_diabetes_duration", "Duracion de diabetes si corresponde"],
        ["msk_life_pvd", "Enfermedad vascular periferica/claudicacion: si/no"],
        ["msk_life_dvt", "Trombosis venosa profunda: si/no"],
        ["msk_life_pe", "Tromboembolismo pulmonar: si/no"],
        ["msk_life_tia", "TIA/ministroke: si/no"],
        ["msk_life_stroke", "ACV: si/no"],
        ["msk_life_stroke_type", "Tipo de ACV si corresponde: hemorragia/coagulo/no seguro"],
        ["msk_life_asthma", "Asma: si/no"],
        ["msk_life_asthma_impact", "Impacto funcional del asma si corresponde"],
        ["msk_life_total_cholesterol", "Colesterol total o categoria"],
        ["msk_life_hdl", "HDL o categoria"],
        ["msk_life_systolic_bp", "Presion sistolica o categoria"],
        ["msk_life_diastolic_bp", "Presion diastolica o categoria"],
        ["msk_life_smoking_ever", "Antecedente de >100 cigarrillos en la vida: si/no"],
        ["msk_life_smoking_current", "Fumo en los ultimos 30 dias: si/no"],
      ],
    },
    volume: {
      label: "Volumen, dimensiones y densidad",
      href: "https://www.mskcc.org/nomograms/prostate/volume",
      question: "Calcula volumen prostatico y densidad de PSA a partir de medidas glandulares y PSA.",
      required: [
        ["msk_volume_length", "Longitud prostatica en cm"],
        ["msk_volume_width", "Ancho/transverso prostatico en cm"],
        ["msk_volume_height", "Altura prostatica en cm"],
        ["msk_volume_psa", "PSA antes de hormonoterapia"],
      ],
    },
  };

  const yesNoOptions = [option("no", "No"), option("yes", "Si")];
  const yesNoUnsureOptions = [option("no", "No"), option("yes", "Si"), option("unsure", "No seguro")];
  const gleasonPatternOptions = [3, 4, 5].map((value) => option(String(value), `Patron ${value}`));
  const gleasonGradeOptions = [
    option("6", "Gleason 6 / GG1"),
    option("3+4", "Gleason 3+4 / GG2"),
    option("4+3", "Gleason 4+3 / GG3"),
    option("8", "Gleason 8 / GG4"),
    option("9", "Gleason 9-10 / GG5"),
  ];

  function withScenario(key, items) {
    return items.map((item) => ({ ...item, scenario: key }));
  }

  function mskccInputFields(key) {
    const fields = {
      pre: [
        select("msk_pre_no_hormone", "Hormonoterapia perioperatoria", yesNoOptions, { value: "no", help: "MSKCC pregunta si recibio o recibira hormonoterapia alrededor de la cirugia." }),
        select("msk_pre_no_radiation", "Radioterapia perioperatoria", yesNoOptions, { value: "no", help: "MSKCC pregunta si recibio o recibira radioterapia alrededor de la cirugia." }),
        number("msk_pre_age", "Edad", { value: 65, min: 35, max: 95 }),
        number("msk_pre_psa", "PSA pre-biopsia", { value: 8, min: 0.01, step: 0.1 }),
        select("msk_pre_gleason_primary", "Gleason primario biopsia", gleasonPatternOptions, { value: "3" }),
        select("msk_pre_gleason_secondary", "Gleason secundario biopsia", gleasonPatternOptions, { value: "4" }),
        select("msk_pre_stage", "Estadio clinico T", cTOptions, { value: "t2a" }),
        number("msk_pre_positive_cores", "Cilindros positivos", { value: 3, min: 0 }),
        number("msk_pre_negative_cores", "Cilindros negativos", { value: 9, min: 0 }),
      ],
      post: [
        select("msk_post_no_hormone", "Hormonoterapia recibida o planificada", yesNoOptions, { value: "no" }),
        select("msk_post_no_radiation", "Radioterapia recibida o planificada", yesNoOptions, { value: "no" }),
        number("msk_post_preop_psa", "PSA preoperatorio", { value: 8, min: 0.01, step: 0.1 }),
        number("msk_post_age_surgery", "Edad al momento de cirugia", { value: 65, min: 35, max: 95 }),
        number("msk_post_months_undetectable", "Meses con PSA indetectable", { value: 6, min: 0, step: 1 }),
        select("msk_post_gleason_primary", "Gleason primario en pieza", gleasonPatternOptions, { value: "3" }),
        select("msk_post_gleason_secondary", "Gleason secundario en pieza", gleasonPatternOptions, { value: "4" }),
        select("msk_post_margins", "Margenes positivos", yesNoOptions, { value: "no" }),
        select("msk_post_ece", "Extension extracapsular", yesNoOptions, { value: "no" }),
        select("msk_post_svi", "Invasion vesiculas seminales", yesNoOptions, { value: "no" }),
        select("msk_post_nodes", "Ganglios pelvicos positivos", yesNoOptions, { value: "no" }),
        select("msk_post_clinical_stage", "Estadio clinico T preoperatorio", cTOptions, { value: "t2a" }),
        select("msk_post_biopsy_gleason_primary", "Gleason primario biopsia", gleasonPatternOptions, { value: "3" }),
        select("msk_post_biopsy_gleason_secondary", "Gleason secundario biopsia", gleasonPatternOptions, { value: "4" }),
      ],
      salvage: [
        select("msk_salvage_no_preop_hormone", "Hormonoterapia pre-RP", yesNoOptions, { value: "no" }),
        select("msk_salvage_no_preop_radiation", "Radioterapia pre-RP", yesNoOptions, { value: "no" }),
        select("msk_salvage_no_postop_hormone", "Hormonoterapia post-RP antes del ascenso PSA", yesNoOptions, { value: "no" }),
        select("msk_salvage_no_postop_radiation", "Radioterapia post-RP antes del ascenso PSA", yesNoOptions, { value: "no" }),
        number("msk_salvage_preop_psa", "PSA preoperatorio", { value: 8, min: 0.01, step: 0.1 }),
        select("msk_salvage_ece", "Extension extracapsular", yesNoOptions, { value: "no" }),
        select("msk_salvage_nodes", "Ganglios positivos", yesNoOptions, { value: "no" }),
        select("msk_salvage_svi", "Vesiculas seminales invadidas", yesNoOptions, { value: "no" }),
        select("msk_salvage_gleason_primary", "Gleason primario pieza", gleasonPatternOptions, { value: "4" }),
        select("msk_salvage_gleason_secondary", "Gleason secundario pieza", gleasonPatternOptions, { value: "3" }),
        select("msk_salvage_margins", "Margenes positivos", yesNoOptions, { value: "yes" }),
        number("msk_salvage_age_bcr", "Edad al detectar BCR", { value: 68, min: 35, max: 100 }),
        number("msk_salvage_months_to_bcr", "Meses desde RP hasta BCR", { value: 36, min: 0, step: 1 }),
        number("msk_salvage_psa_bcr", "PSA al momento de BCR", { value: 0.4, min: 0.01, step: 0.01 }),
        number("msk_salvage_psadt", "PSA doubling time meses", { value: 10, min: 0.1, step: 0.1 }),
      ],
      biopsy: [
        number("msk_biopsy_psa", "PSA mas reciente", { value: 7, min: 0.01, step: 0.1 }),
        number("msk_biopsy_age", "Edad", { value: 65, min: 35, max: 100 }),
        select("msk_biopsy_african_ancestry", "Ascendencia africana", yesNoOptions, { value: "no" }),
        select("msk_biopsy_dre", "Tacto rectal sospechoso", yesNoUnsureOptions, { value: "no" }),
        select("msk_biopsy_prior_negative", "Biopsia previa negativa", yesNoUnsureOptions, { value: "no" }),
        select("msk_biopsy_family", "Familiar de primer grado con cancer de prostata", yesNoUnsureOptions, { value: "no" }),
      ],
      psadt: [
        text("msk_psadt_dates", "Fechas de PSA", { value: "01/01/2025, 01/01/2026", wide: true, help: "Formato libre separado por comas. Se conserva para copiar al MSKCC oficial." }),
        text("msk_psadt_values", "Valores de PSA", { value: "2.5, 5.0", wide: true, help: "Valores separados por comas en el mismo orden que las fechas." }),
        select("msk_psadt_minimum_series", "Cantidad de mediciones comparables", [option("2", "2 mediciones"), option("3", "3 o mas mediciones")], { value: "2" }),
        number("msk_psadt_month_span", "Meses entre primer y ultimo PSA", { value: 12, min: 0.1, step: 0.1 }),
      ],
      life: [
        select("msk_life_no_hormone", "Hormonoterapia recibida o planificada", yesNoOptions, { value: "no" }),
        select("msk_life_no_radiation", "Radioterapia recibida o planificada", yesNoOptions, { value: "no" }),
        number("msk_life_age", "Edad", { value: 72, min: 35, max: 100 }),
        number("msk_life_psa", "PSA mas reciente", { value: 8, min: 0.01, step: 0.1 }),
        select("msk_life_grade", "Grado/Gleason", gleasonGradeOptions, { value: "3+4" }),
        select("msk_life_t_stage", "Estadio T clinico", cTOptions, { value: "t2a" }),
        select("msk_life_m_stage", "Estadio M", [option("m0", "M0"), option("m1", "M1")], { value: "m0" }),
        select("msk_life_angina", "Angina/dolor toracico", yesNoOptions, { value: "no" }),
        select("msk_life_mi", "Infarto", yesNoOptions, { value: "no" }),
        select("msk_life_chf", "Insuficiencia cardiaca", yesNoOptions, { value: "no" }),
        select("msk_life_valve", "Valvulopatia significativa", yesNoOptions, { value: "no" }),
        select("msk_life_afib", "Fibrilacion auricular/arritmia", yesNoOptions, { value: "no" }),
        select("msk_life_aaa", "Aneurisma aorta abdominal", yesNoOptions, { value: "no" }),
        select("msk_life_diabetes", "Diabetes", yesNoOptions, { value: "no" }),
        number("msk_life_diabetes_duration", "Duracion diabetes en anios", { value: 0, min: 0, step: 1 }),
        select("msk_life_pvd", "Enfermedad vascular periferica", yesNoOptions, { value: "no" }),
        select("msk_life_dvt", "Trombosis venosa profunda", yesNoOptions, { value: "no" }),
        select("msk_life_pe", "Tromboembolismo pulmonar", yesNoOptions, { value: "no" }),
        select("msk_life_tia", "TIA/ministroke", yesNoOptions, { value: "no" }),
        select("msk_life_stroke", "ACV", yesNoOptions, { value: "no" }),
        select("msk_life_stroke_type", "Tipo de ACV", [option("none", "No aplica"), option("hemorrhage", "Hemorragia"), option("clot", "Coagulo"), option("unsure", "No seguro")], { value: "none" }),
        select("msk_life_asthma", "Asma", yesNoOptions, { value: "no" }),
        select("msk_life_asthma_impact", "Impacto funcional del asma", [option("none", "No aplica"), option("mild", "Leve"), option("limited", "Limita actividad"), option("severe", "Severo")], { value: "none" }),
        number("msk_life_total_cholesterol", "Colesterol total mg/dl", { value: 190, min: 80, max: 400 }),
        number("msk_life_hdl", "HDL mg/dl", { value: 45, min: 10, max: 150 }),
        number("msk_life_systolic_bp", "Presion sistolica", { value: 130, min: 70, max: 240 }),
        number("msk_life_diastolic_bp", "Presion diastolica", { value: 80, min: 40, max: 140 }),
        select("msk_life_smoking_ever", ">100 cigarrillos en la vida", yesNoOptions, { value: "no" }),
        select("msk_life_smoking_current", "Fumo en ultimos 30 dias", yesNoOptions, { value: "no" }),
      ],
      volume: [
        number("msk_volume_length", "Longitud prostatica cm", { value: 4.5, min: 0.1, step: 0.1 }),
        number("msk_volume_width", "Ancho/transverso prostatico cm", { value: 4.0, min: 0.1, step: 0.1 }),
        number("msk_volume_height", "Altura prostatica cm", { value: 3.5, min: 0.1, step: 0.1 }),
        number("msk_volume_psa", "PSA antes de hormonoterapia", { value: 7, min: 0.01, step: 0.1 }),
      ],
    };
    return withScenario(key, fields[key] || []);
  }

  function parsePsaSeries(textValue) {
    return String(textValue || "").split(/\n+/).map((line) => {
      const parts = line.trim().split(/\s*[;\t]\s*/, 2).map((item)=>item.trim());
      if(parts.length<2)return null;
      let date=parts[0];
      const match=date.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
      if(match)date=`${match[3]}-${match[2].padStart(2,"0")}-${match[1].padStart(2,"0")}`;
      return {date,value:Number(parts[1].replace(",","."))};
    }).filter(Boolean);
  }

  function mskccChecklistFields() {
    const fields = [];
    Object.entries(MSKCC_PROSTATE_NOMOGRAMS).forEach(([key, nomogram]) => {
      fields.push(section(`mskcc_${key}`, nomogram.label, { help: nomogram.question }));
      fields[fields.length - 1].scenario = key;
      const requiredIds = new Set((nomogram.required || []).map(([id]) => id));
      const scenarioFields = mskccInputFields(key).map((item) => ({ ...item, required: requiredIds.has(item.id) }));
      fields.push(...scenarioFields);
    });
    return fields;
  }

  function mskccCompletion(values, nomogram) {
    const required = nomogram.required || [];
    const optional = nomogram.optional || [];
    const hasValue = (id) => values[id] !== "" && values[id] !== null && values[id] !== undefined;
    const doneRequired = required.filter(([id]) => hasValue(id));
    const doneOptional = optional.filter(([id]) => hasValue(id));
    const missingRequired = required.filter(([id]) => !hasValue(id));
    const requiredPct = required.length ? doneRequired.length / required.length * 100 : 100;
    return { required, optional, doneRequired, doneOptional, missingRequired, requiredPct };
  }

  function mskccChecklistHtml(title, items, values, emptyText = "Sin items.") {
    const list = items || [];
    if (!list.length) return `<div class="mskcc-checklist"><strong>${title}</strong><p>${emptyText}</p></div>`;
    return `
      <div class="mskcc-checklist">
        <strong>${title}</strong>
        <ul>
          ${list.map(([id, label]) => {const complete=values[id]!==""&&values[id]!==null&&values[id]!==undefined;return `<li class="${complete ? "done" : "missing"}"><span>${complete ? "completo" : "falta"}</span>${label}</li>`}).join("")}
        </ul>
      </div>
    `;
  }

  function mskccOverviewHtml(values) {
    const rows = Object.entries(MSKCC_PROSTATE_NOMOGRAMS).map(([, nomogram]) => {
      const state = mskccCompletion(values, nomogram);
      return `
        <tr>
          <td>${nomogram.label}</td>
          <td>${Math.round(state.requiredPct)}%</td>
          <td>${state.missingRequired.length ? state.missingRequired.slice(0, 4).map(([, label]) => label).join("; ") : "Listo"}</td>
          <td><a href="${nomogram.href}" target="_blank" rel="noopener">abrir</a></td>
        </tr>
      `;
    }).join("");
    return `
      <div class="mskcc-table-wrap">
        <strong>Matriz MSKCC completa</strong>
        <table class="mskcc-table">
          <thead><tr><th>Nomograma</th><th>Completitud</th><th>Faltante principal</th><th>MSKCC</th></tr></thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
    `;
  }

  const TOOLS = [
    {
      id: "bsa",
      number: "G1",
      title: "Superficie corporal — Mosteller",
      category: "general",
      subtitle: "Formula de Mosteller.",
      source: "Mosteller",
      clinicalUse: "Calcula la superficie corporal a partir del peso y la altura. Es una referencia habitual para dosificacion y documentacion oncologica.",
      fields: [
        number("bsa_weight", "Peso (kg)", { value: 70, min: 1, step: 0.1 }),
        number("bsa_height", "Altura (cm)", { value: 170, min: 30, step: 0.1 }),
      ],
      calculate(values) {
        const bsa = rules.bodySurfaceArea(values.bsa_weight, values.bsa_height);
        return result({
          title: bsa ? `${bsa.toFixed(2)} m²` : "Complete peso y altura",
          detail: bsa ? "Superficie corporal estimada mediante la formula de Mosteller." : "Ambos valores deben ser mayores que cero.",
          badge: "Mosteller",
          score: 0,
          showScore: false,
          metrics: bsa ? [{ label: "SC", value: `${bsa.toFixed(2)} m²` }] : [],
          severity: "info",
          notes: ["Verificar peso y altura actuales. La superficie corporal no define por sí sola una dosis ni un tope de dosificación."],
        });
      },
    },
    {
      id: "bmi",
      number: "G2",
      title: "Índice de masa corporal",
      category: "general",
      subtitle: "Relacion entre peso y altura.",
      source: "IMC",
      clinicalUse: "Calcula el indice de masa corporal y muestra su categoria descriptiva como dato general del paciente.",
      fields: [
        number("bmi_weight", "Peso (kg)", { value: 70, min: 1, step: 0.1 }),
        number("bmi_height", "Altura (cm)", { value: 170, min: 30, step: 0.1 }),
      ],
      calculate(values) {
        const calculated = rules.bodyMassIndex(values.bmi_weight, values.bmi_height);
        const bmi = calculated?.value || 0;
        const category = calculated?.category || "";
        return result({
          title: bmi ? `${bmi.toFixed(1)} kg/m²` : "Complete peso y altura",
          detail: category || "Ambos valores deben ser mayores que cero.",
          badge: "IMC adulto",
          score: 0,
          showScore: false,
          severity: "info",
          metrics: bmi ? [{ label: "IMC", value: bmi.toFixed(1) }, { label: "Categoria", value: category }] : [],
          notes: ["Interpretar junto con composicion corporal, estado nutricional y contexto clinico."],
        });
      },
    },
    {
      id: "calvert",
      number: "G3",
      title: "Carboplatino - formula de Calvert",
      category: "general",
      subtitle: "Dosis total por AUC y filtrado glomerular.",
      source: "Formula de Calvert",
      clinicalUse: "Estima la dosis total de carboplatino mediante AUC objetivo por filtrado glomerular mas 25.",
      fields: [
        select("calvert_method", "Método de función renal", [
          option("measured", "GFR medida / absoluta"),
          option("crcl", "Clearance de creatinina medido o calculado"),
          option("indexed", "eGFR indexado a 1,73 m²"),
        ], { value: "measured", wide: true, help: "La fórmula original utiliza GFR absoluta en ml/min. Identificar siempre el método." }),
        number("calvert_auc", "AUC objetivo", { value: 5, min: 0.1, step: 0.1 }),
        number("calvert_gfr", "Función renal informada", { value: 80, min: 0.1, step: 0.1, help: "ml/min, salvo eGFR indexado: ml/min/1,73 m²." }),
        number("calvert_bsa", "Superficie corporal (m²)", { value: 1.8, min: 0.5, max: 3.5, step: 0.01, required: false, help: "Obligatoria sólo para desindexar eGFR." }),
        checkbox("calvert_cap", "Aplicar tope de 125 ml/min", { help: "Sólo si el protocolo vigente lo indica; nunca se aplica en silencio." }),
      ],
      calculate(values) {
        const auc = num(values.calvert_auc);
        const reported = num(values.calvert_gfr);
        const indexed = values.calvert_method === "indexed";
        if (indexed && num(values.calvert_bsa) <= 0) return result({ title: "Falta la superficie corporal", detail: "Para eGFR indexado se necesita desindexar: eGFR × SC / 1,73.", badge: "no calculable", score: 0, showScore: false, severity: "warn", metrics: [], notes: [] });
        const absolute = indexed ? reported * num(values.calvert_bsa) / 1.73 : reported;
        const filtration = values.calvert_cap ? Math.min(absolute, 125) : absolute;
        const dose = rules.calvertDose(auc, filtration);
        return result({
          title: dose ? `${Math.round(dose)} mg` : "Complete AUC y función renal",
          detail: dose ? `Dosis calculada sin redondear: ${dose.toFixed(2)} mg.` : "AUC y función renal deben ser mayores que cero.",
          badge: "formula de Calvert",
          score: 0,
          showScore: false,
          severity: "info",
          metrics: dose ? [{ label: "Dosis redondeada", value: `${Math.round(dose)} mg` }, { label: "GFR absoluta usada", value: `${filtration.toFixed(2)} ml/min` }, { label: "AUC", value: auc }, { label: "Método", value: indexed ? "eGFR desindexado" : values.calvert_method === "crcl" ? "CrCl aproximado" : "GFR medida" }] : [],
          notes: [
            indexed ? `eGFR ${reported} × SC ${num(values.calvert_bsa).toFixed(2)} / 1,73 = ${absolute.toFixed(2)} ml/min.` : "La función renal ingresada se utilizó como valor absoluto.",
            absolute > 125 && !values.calvert_cap ? "El valor supera 125 ml/min. Revisar el protocolo antes de decidir si corresponde un tope." : values.calvert_cap && absolute > 125 ? "Se aplicó el tope de 125 ml/min solicitado." : "No se aplicó un tope adicional.",
            "La diálisis y situaciones de función renal inestable requieren un planteo específico.",
          ],
        });
      },
    },
    {
      id: "ecog",
      number: "01",
      title: "ECOG / Karnofsky",
      category: "general",
      subtitle: "Estado funcional y aptitud basal.",
      source: "ECOG, Karnofsky",
      clinicalUse: "ECOG y Karnofsky describen el estado funcional basal: cuánto puede moverse, cuidarse y sostener actividad cotidiana. Se usan para documentar performance status, definir elegibilidad en ensayos/tratamientos y anticipar tolerancia clínica.",
      fields: [
        select("ecog", "ECOG", [0, 1, 2, 3, 4, 5].map((v) => option(String(v), `ECOG ${v}`)), { value: "1" }),
        select("kps", "Karnofsky", [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0].map((v) => option(String(v), `${v}%`)), { value: "80" }),
      ],
      calculate(values) {
        const ecog = num(values.ecog);
        const kps = num(values.kps);
        const ecogText = ecogDescription(ecog);
        const kpsText = kpsDescription(kps);
        return result({
          title: "ECOG y Karnofsky",
          detail: "Son dos escalas distintas de estado funcional. Usá la que corresponda al protocolo, historia clínica o reporte que estés completando.",
          badge: "escalas separadas",
          score: 0,
          showScore: false,
          severity: "info",
          metrics: [
            { label: `ECOG ${ecog}`, value: ecogText },
            { label: `Karnofsky ${kps}%`, value: kpsText },
          ],
          notes: [
            "ECOG se usa mucho en oncología clínica y ensayos para definir performance status y elegibilidad terapéutica.",
            "Karnofsky ofrece una escala porcentual más granular para funcionalidad, dependencia y necesidad de asistencia.",
            "No se cruzan ni se convierten entre sí: registrar la escala usada y su valor exacto.",
          ],
        });
      },
    },
    {
      id: "charlson",
      number: "02",
      title: "Charlson comorbidity index",
      category: "general",
      subtitle: "Carga comórbida ajustada por edad.",
      source: "Charlson Comorbidity Index",
      clinicalUse: "Charlson mide carga de comorbilidad y la ajusta por edad para estimar riesgo de mortalidad atribuible a enfermedades no oncológicas. En uro-oncología ayuda a ponderar expectativa de vida, intensidad terapéutica, riesgo perioperatorio global y pertinencia de tratamientos con beneficio a largo plazo.",
      fields: [
        number("age", "Edad", { value: 68, min: 18, max: 100, wide: true }),
        checkbox("mi", "Infarto previo", { weight: 1 }),
        checkbox("chf", "Insuficiencia cardíaca", { weight: 1 }),
        checkbox("pvd", "Enfermedad vascular periférica", { weight: 1 }),
        checkbox("cva", "Enfermedad cerebrovascular", { weight: 1 }),
        checkbox("dementia", "Demencia", { weight: 1 }),
        checkbox("copd", "EPOC", { weight: 1 }),
        checkbox("connective", "Enfermedad del tejido conectivo", { weight: 1 }),
        checkbox("ulcer", "Enfermedad ulcerosa", { weight: 1 }),
        checkbox("liverMild", "Hepatopatía leve", { weight: 1 }),
        checkbox("liverSevere", "Hepatopatía moderada/severa", { weight: 3 }),
        checkbox("diabetes", "Diabetes sin daño de órgano", { weight: 1 }),
        checkbox("diabetesComplicated", "Diabetes con daño de órgano", { weight: 2 }),
        checkbox("hemiplegia", "Hemiplejia", { weight: 2 }),
        checkbox("renal", "Enfermedad renal moderada/severa", { weight: 2 }),
        checkbox("solidTumor", "Tumor sólido", { weight: 2 }),
        checkbox("metastaticTumor", "Tumor sólido metastásico", { weight: 6 }),
        checkbox("leukemia", "Leucemia", { weight: 2 }),
        checkbox("lymphoma", "Linfoma", { weight: 2 }),
        checkbox("aids", "SIDA", { weight: 6 }),
      ],
      calculate(values) {
        const calculated = rules.charlson(values);
        const total = calculated.total;
        return result({
          title: `CCI ajustado: ${total}`,
          detail: `Comorbilidad ${calculated.comorbidityPoints} + edad ${calculated.agePoints}.`,
          badge: "CCI ajustado por edad",
          score: 0,
          showScore: false,
          severity: "info",
          metrics: [{ label: "CCI ajustado", value: total }, { label: "Comorbilidad", value: calculated.comorbidityPoints }, { label: "Edad", value: calculated.agePoints }],
          notes: [
            "Las categorías excluyentes —diabetes, hepatopatía y tumor sólido— no se suman dos veces.",
            "Definir antes del cálculo si el tumor índice se incluye o se excluye y documentar ese criterio.",
            "Se retiró la conversión histórica a supervivencia de 10 años porque no está calibrada para oncología contemporánea.",
          ],
        });
      },
    },
    {
      id: "g8-carg",
      number: "03",
      title: "G8 / CARG",
      category: "general",
      subtitle: "Screening geriátrico y toxicidad de quimioterapia.",
      source: "G8, CARG toxicity score",
      clinicalUse: "G8 y CARG son complementarias en adultos mayores con cáncer. G8 es un screening geriátrico rápido: si está alterado, sugiere vulnerabilidad y necesidad de evaluación geriátrica integral. CARG estima riesgo de toxicidad severa por quimioterapia usando factores del paciente, laboratorio, función y características del tratamiento.",
      fields: [
        section("g8_section", "G8 screening geriátrico", {
          help: "Completá los 8 ítems. Total máximo 17; menor puntaje indica mayor vulnerabilidad. Un total ≤14 suele considerarse alterado.",
        }),
        select("g8_food", "Ingesta alimentaria en 3 meses", [
          option("0", "Disminución severa"),
          option("1", "Disminución moderada"),
          option("2", "Sin disminución"),
        ], { value: "2" }),
        select("g8_weight", "Pérdida de peso en 3 meses", [
          option("0", ">3 kg"),
          option("1", "No sabe"),
          option("2", "1-3 kg"),
          option("3", "Sin pérdida"),
        ], { value: "3" }),
        select("g8_mobility", "Movilidad", [
          option("0", "Cama/silla"),
          option("1", "Se levanta, no sale"),
          option("2", "Sale del domicilio"),
        ], { value: "2" }),
        select("g8_neuro", "Neuropsicológico", [
          option("0", "Demencia/depresión severa"),
          option("1", "Demencia/depresión leve"),
          option("2", "Sin problemas"),
        ], { value: "2" }),
        select("g8_bmi", "Índice de masa corporal", [
          option("0", "<19"),
          option("1", "19 a <21"),
          option("2", "21 a <23"),
          option("3", "≥23"),
        ], { value: "3" }),
        select("g8_meds", "Más de 3 fármacos diarios", [
          option("0", "Sí"),
          option("1", "No"),
        ], { value: "1" }),
        select("g8_health", "Salud vs pares de edad", [
          option("0", "Peor"),
          option("0.5", "No sabe"),
          option("1", "Igual"),
          option("2", "Mejor"),
        ], { value: "1" }),
        select("g8_age", "Edad", [
          option("0", ">85 años"),
          option("1", "80-85 años"),
          option("2", "<80 años"),
        ], { value: "2" }),
        section("carg_section", "CARG toxicidad por quimioterapia", {
          help: "Marcá los factores presentes. Total máximo 23; bajo 0-5, intermedio 6-9, alto ≥10.",
        }),
        checkbox("carg_age72", "Edad ≥72 años", { weight: 2 }),
        checkbox("carg_gigu", "Tumor gastrointestinal o genitourinario", { weight: 2 }),
        checkbox("carg_standard", "Quimioterapia a dosis estándar", { weight: 2 }),
        checkbox("carg_poly", "Poliquimioterapia", { weight: 2 }),
        checkbox("carg_hb", "Hemoglobina baja según sexo", { weight: 3 }),
        checkbox("carg_crcl", "Clearance de creatinina <34 ml/min", { weight: 3 }),
        checkbox("carg_hearing", "Audición regular o mala", { weight: 2 }),
        checkbox("carg_falls", "Caídas en los últimos 6 meses", { weight: 3 }),
        checkbox("carg_meds_help", "Necesita ayuda para tomar medicación", { weight: 1 }),
        checkbox("carg_walk", "Limitación para caminar una cuadra", { weight: 2 }),
        checkbox("carg_social", "Actividad social limitada por salud", { weight: 1 }),
      ],
      calculate(values, tool) {
        const g8Result = rules.g8({food:values.g8_food,weight:values.g8_weight,mobility:values.g8_mobility,neuro:values.g8_neuro,bmi:values.g8_bmi,meds:values.g8_meds,health:values.g8_health,age:values.g8_age});
        const cargResult = rules.carg({age72:values.carg_age72,gigu:values.carg_gigu,standard:values.carg_standard,poly:values.carg_poly,hb:values.carg_hb,crcl:values.carg_crcl,hearing:values.carg_hearing,falls:values.carg_falls,medsHelp:values.carg_meds_help,walk:values.carg_walk,social:values.carg_social});
        return result({
          title: `${g8Result.altered ? "G8 alterado" : "G8 conservado"} · CARG ${cargResult.category}`,
          detail: "G8 y CARG se informan por separado: no existe un puntaje combinado validado.",
          badge: g8Result.altered ? "evaluación geriátrica" : cargResult.category,
          score: 0,
          showScore: false,
          severity: g8Result.altered || cargResult.category === "alto" ? "bad" : cargResult.category === "intermedio" ? "warn" : "good",
          metrics: [
            { label: "G8 total", value: `${g8Result.total.toFixed(1)} / 17` },
            { label: "Lectura G8", value: g8Result.altered ? "screening alterado" : "screening conservado" },
            { label: "CARG total", value: cargResult.total },
            { label: "Toxicidad G3-5", value: `${cargResult.toxicity}% (cohorte original)` },
          ],
          notes: [
            "G8 no mide toxicidad de quimioterapia: identifica vulnerabilidad geriátrica y necesidad de evaluación más completa.",
            "CARG no mide fragilidad global: estima probabilidad de toxicidad severa con quimioterapia.",
            "Si G8 está alterado o CARG es alto, considerar geriatría, soporte, ajuste de esquema/dosis o alternativa terapéutica.",
          ],
        });
      },
    },
    {
      id: "ipss-shim",
      number: "04",
      title: "IPSS / SHIM",
      category: "prostata",
      subtitle: "Síntomas urinarios y función sexual basal.",
      source: "IPSS, SHIM, EPIC-26 como complemento",
      clinicalUse: "IPSS cuantifica síntomas urinarios bajos y su impacto en calidad de vida; es útil antes de cirugía, radioterapia, braquiterapia o vigilancia para documentar basal y anticipar toxicidad urinaria. SHIM evalúa función eréctil basal y ayuda a discutir efectos esperados de cirugía, radioterapia, hormonoterapia y preservación funcional.",
      fields: [
        section("ipss_section", "IPSS síntomas urinarios", {
          help: "Cada ítem va de 0 a 5 según frecuencia. Total 0-35: leve 0-7, moderado 8-19, severo 20-35.",
        }),
        select("ipss_emptying", "Vaciado incompleto", ipssFrequencyOptions, { value: "1" }),
        select("ipss_frequency", "Frecuencia", ipssFrequencyOptions, { value: "1" }),
        select("ipss_intermittency", "Intermitencia", ipssFrequencyOptions, { value: "1" }),
        select("ipss_urgency", "Urgencia", ipssFrequencyOptions, { value: "1" }),
        select("ipss_stream", "Chorro débil", ipssFrequencyOptions, { value: "1" }),
        select("ipss_straining", "Esfuerzo miccional", ipssFrequencyOptions, { value: "1" }),
        select("ipss_nocturia", "Nocturia", ipssNocturiaOptions, { value: "2" }),
        select("ipss_qol", "Calidad de vida urinaria", [
          option("0", "0 encantado"),
          option("1", "1 satisfecho"),
          option("2", "2 mayormente satisfecho"),
          option("3", "3 mixto"),
          option("4", "4 mayormente insatisfecho"),
          option("5", "5 infeliz"),
          option("6", "6 terrible"),
        ], { value: "2", wide: true }),
        section("shim_section", "SHIM función eréctil", {
          help: "Cada ítem va de 1 a 5. Total 5-25; menor puntaje indica mayor disfunción eréctil. Si no hubo actividad sexual suficiente, registrar no evaluable fuera de la escala.",
        }),
        checkbox("shim_not_evaluable", "Sin actividad sexual suficiente en los últimos 6 meses", { help: "SHIM no genera un puntaje válido en este contexto." }),
        select("shim_confidence", "Confianza para lograr/mantener erección", shimConfidenceOptions, { value: "4" }),
        select("shim_hardness", "Erección suficiente para penetración", shimFrequencyOptions, { value: "4" }),
        select("shim_maintenance", "Mantener erección luego de penetrar", shimFrequencyOptions, { value: "4" }),
        select("shim_completion", "Dificultad para mantenerla hasta completar", shimDifficultyOptions, { value: "3" }),
        select("shim_satisfaction", "Relaciones sexuales satisfactorias", shimFrequencyOptions, { value: "3" }),
      ],
      calculate(values) {
        const ipssKeys = ["ipss_emptying", "ipss_frequency", "ipss_intermittency", "ipss_urgency", "ipss_stream", "ipss_straining", "ipss_nocturia"];
        const shimKeys = ["shim_confidence", "shim_hardness", "shim_maintenance", "shim_completion", "shim_satisfaction"];
        const ipssResult = rules.ipss(ipssKeys.map((key)=>values[key]));
        const shimResult = values.shim_not_evaluable ? null : rules.shim(shimKeys.map((key)=>values[key]));
        const ipss = ipssResult.total, shim = shimResult?.total;
        const ipssLabel = ipssResult.category, shimLabel = shimResult?.category || "no evaluable";
        return result({
          title: `IPSS ${ipss} (${ipssLabel})`,
          detail: `QoL urinaria ${values.ipss_qol}/6. ${shimResult ? `SHIM ${shim}: ${shimLabel}.` : "SHIM no evaluable por ausencia de actividad sexual suficiente."}`,
          badge: ipssLabel,
          score: clamp(ipss / 35 * 100),
          scoreName: "Carga de síntomas urinarios",
          severity: ipss <= 7 ? "good" : ipss <= 19 ? "warn" : "bad",
          metrics: [
            { label: "IPSS total", value: `${ipss} / 35` },
            { label: "Severidad IPSS", value: ipssLabel },
            { label: "QoL urinaria", value: `${values.ipss_qol} / 6` },
            { label: "SHIM total", value: shimResult ? `${shim} / 25` : "no evaluable" },
            { label: "Lectura SHIM", value: shimLabel },
          ],
          notes: [
            "IPSS alto antes de RT, braquiterapia o cirugía requiere optimización urinaria y discusión de toxicidad.",
            "SHIM bajo documenta función sexual basal y ayuda a anticipar recuperación o preservación funcional.",
            "EPIC-26 completo es mejor para calidad de vida multidominio cuando se necesita evaluación más amplia.",
          ],
        });
      },
    },
    {
      id: "damico",
      number: "05",
      title: "EAU 2026 — riesgo prostático",
      category: "prostata",
      subtitle: "Grupo clínico EAU para enfermedad localizada o localmente avanzada.",
      source: "EAU Prostate Cancer Guidelines 2026 · tabla 4.3",
      clinicalUse: "Clasifica con PSA, Grade Group, cT basado en tacto rectal y estado ganglionar clínico. No mezcla las categorías D'Amico o NCCN.",
      fields: [
        section("damico_data", "Datos de riesgo clínico", {
          help: "Usá PSA pretratamiento, mayor Grade Group de biopsia y cT por tacto rectal. La EAU 2026 no usa la RM para asignar este cT.",
        }),
        number("psa", "PSA pretratamiento", { value: 9, min: 0, step: 0.1, help: "ng/ml. En rangos frontera conviene confirmar tendencia y contexto de prostatitis/instrumentación." }),
        select("gg", "Grade Group máximo", ggOptions, { value: "1", help: "Usar el mayor ISUP/Grade Group informado en biopsia." }),
        select("ct", "Estadio clínico", cTOptions, { value: "t1", help: "Registrar el cT que se usará para decisión; RM puede reclasificar extensión local." }),
        select("n", "Ganglios clínicos", [option("nx", "No confirmado"), option("n0", "cN0"), option("n1", "cN+")], { value: "nx", help: "cN+ se considera localmente avanzado en esta clasificación." }),
        select("m", "Metástasis", [option("mx", "No confirmada"), option("m0", "M0"), option("m1", "M1")], { value: "mx", help: "La tabla de riesgo requiere M0; M1 queda fuera de alcance." }),
      ],
      calculate(values) {
        const psa = num(values.psa);
        const gg = num(values.gg);
        const classified = rules.eauProstateRisk(values);
        const label = classified.label;
        const severityClass = classified.key === "low" ? "good" : ["favorable_intermediate","unfavorable_intermediate","unclassified","metastatic"].includes(classified.key) ? "warn" : "bad";
        return result({
          title: label,
          detail: `PSA ${psa}, GG${gg}, c${String(values.ct).toUpperCase()}, ${String(values.n).toUpperCase()}, ${String(values.m).toUpperCase()}.`,
          badge: label,
          score: 0,
          showScore: false,
          severity: severityClass,
          metrics: [
            { label: "PSA", value: psa },
            { label: "GG", value: gg },
            { label: "cT", value: String(values.ct).toUpperCase() },
            { label: "cN", value: values.n === "n1" ? "positivo" : values.n === "n0" ? "negativo" : "no confirmado" },
            { label: "M", value: String(values.m).toUpperCase() },
          ],
          notes: [
            "Resultado determinístico de la tabla EAU 2026; no equivale automáticamente a una recomendación terapéutica.",
            "La clasificación exige enfermedad M0; M1 debe evaluarse con herramientas para enfermedad metastásica.",
          ],
        });
      },
    },
    {
      id: "capra",
      number: "06",
      title: "CAPRA / CAPRA-S",
      category: "prostata",
      subtitle: "Riesgo pretratamiento y post-prostatectomía.",
      source: "UCSF CAPRA, CAPRA-S",
      clinicalUse: "CAPRA cuantifica riesgo pretratamiento y CAPRA-S lo hace después de prostatectomía con variables patológicas. Ayudan a estimar recurrencia, comparar riesgo entre pacientes, planificar seguimiento y discutir adyuvancia o rescate.",
      fields: [
        select("scenario", "Escala", [option("pre", "CAPRA pretratamiento"), option("post", "CAPRA-S postoperatorio")], { value: "pre", wide: true, help: "Son dos escalas distintas y usan formularios separados." }),
        section("capra_clinical", "Variables clínicas", {
          help: "CAPRA pretratamiento: datos de la biopsia y del diagnóstico.", scenario: "pre",
        }),
        number("age", "Edad al diagnóstico", { value: 64, min: 18, max: 100, scenario: "pre" }),
        number("psa", "PSA al diagnóstico", { value: 8, min: 0, step: 0.1, scenario: "pre" }),
        select("capraPrimary", "Gleason primario", gleasonPatternOptions, { value: "3", scenario: "pre" }),
        select("capraSecondary", "Gleason secundario", gleasonPatternOptions, { value: "4", scenario: "pre" }),
        select("ct", "cT", [option("t1", "cT1/cT1c"), option("t2a", "cT2a"), option("t2b", "cT2b"), option("t2c", "cT2c"), option("t3a", "cT3a"), option("t3b", "cT3b (fuera de modelo)"), option("t4", "cT4 (fuera de modelo)")], { value: "t2a", scenario: "pre", help: "CAPRA suma un punto sólo para cT3a; cT3b/T4 quedan fuera del modelo original." }),
        number("positiveCores", "Cilindros positivos", { value: 3, min: 0, scenario: "pre" }),
        number("totalCores", "Cilindros totales", { value: 12, min: 1, scenario: "pre" }),
        section("capra_path", "Variables patológicas para CAPRA-S", {
          help: "CAPRA-S utiliza PSA preoperatorio y anatomía patológica final; no suma edad, cT ni cilindros.", scenario: "post",
        }),
        number("capraSpsa", "PSA preoperatorio", { value: 8, min: 0, step: 0.1, scenario: "post" }),
        select("capraSPrimary", "Gleason patológico primario", gleasonPatternOptions, { value: "3", scenario: "post" }),
        select("capraSSecondary", "Gleason patológico secundario", gleasonPatternOptions, { value: "4", scenario: "post" }),
        checkbox("margin", "Margen quirúrgico positivo", { scenario: "post" }),
        checkbox("ece", "Extensión extraprostática", { scenario: "post" }),
        checkbox("svi", "Invasión de vesículas seminales", { scenario: "post" }),
        checkbox("lni", "Ganglios positivos", { scenario: "post" }),
      ],
      calculate(values) {
        const post = values.scenario === "post";
        const calculated = post
          ? rules.capraS({psa:values.capraSpsa,primary:values.capraSPrimary,secondary:values.capraSSecondary,margin:values.margin,ece:values.ece,svi:values.svi,lni:values.lni})
          : rules.capra({age:values.age,psa:values.psa,primary:values.capraPrimary,secondary:values.capraSecondary,ct:values.ct,positiveCores:values.positiveCores,totalCores:values.totalCores});
        if (!post && calculated.valid === false) return result({ title: "CAPRA no calculable", detail: calculated.reason, badge: "fuera de modelo", score: 0, showScore: false, severity: "warn", metrics: [], notes: ["No se extrapola la escala fuera de su definición original."] });
        const capped = calculated.total, label = calculated.category;
        return result({
          title: `${post ? "CAPRA-S" : "CAPRA"} ${capped}`,
          detail: `Grupo de riesgo ${label}; puntaje determinístico publicado.`,
          badge: label,
          score: 0,
          showScore: false,
          severity: label === "bajo" ? "good" : label === "intermedio" ? "warn" : "bad",
          metrics: [{ label: "Puntaje", value: capped }, { label: "Escala", value: post ? "CAPRA-S" : "CAPRA" }, ...(post ? [] : [{label:"Cilindros positivos",value:`${calculated.corePercent.toFixed(1)}%`}])],
          notes: [
            post ? "CAPRA-S: PSA preoperatorio, Gleason patológico, margen, ECE, SVI y ganglios." : "CAPRA: edad, PSA, Gleason de biopsia, cT y proporción de cilindros positivos.",
            "La escala estratifica riesgo; no indica por sí sola un tratamiento.",
          ],
        });
      },
    },
    {
      id: "partin",
      number: "07",
      title: "Partin tables",
      category: "prostata",
      subtitle: "Estimación patológica pre-prostatectomía.",
      source: "Partin tables",
      clinicalUse: "Estima la probabilidad de hallazgos patológicos en prostatectomía, como enfermedad órgano confinada, extensión extraprostática, compromiso vesicular y ganglionar. Es útil para consejería prequirúrgica y discusión de linfadenectomía.",
      fields: [
        section("partin_data", "Datos preoperatorios", {
          help: "Usar solo variables disponibles antes de prostatectomía. La salida es orientativa y debe contrastarse con tablas/nomogramas oficiales si define conducta.",
        }),
        select("psaCat", "PSA", [option("lt4", "<4"), option("4to10", "4-10"), option("10to20", "10-20"), option("gt20", ">20")], { value: "4to10", help: "Categoría de PSA preoperatorio." }),
        select("gg", "Grade Group", ggOptions, { value: "2", help: "Mayor Grade Group en biopsia." }),
        select("ct", "Estadio clínico", cTOptions, { value: "t2a", help: "Estadio clínico usado para consejería quirúrgica." }),
      ],
      calculate(values) {
        const gg = num(values.gg);
        return result({
          title: "Consulta de tablas Partin oficiales",
          detail: `Perfil preparado: PSA ${values.psaCat}, ${String(values.ct).toUpperCase()}, GG${gg}.`,
          badge: "sin estimación local",
          score: 0,
          showScore: false,
          severity: "info",
          metrics: [{label:"PSA",value:values.psaCat},{label:"cT",value:String(values.ct).toUpperCase()},{label:"Grade Group",value:gg}],
          notes: [
            "Se retiraron los porcentajes locales porque no correspondían a las tablas Partin publicadas.",
            '<a class="tool-inline-link" href="https://www.hopkinsmedicine.org/brady-urology-institute/conditions-and-treatments/prostate-cancer/risk-assessment-tools/partin-tables" target="_blank" rel="noopener">Abrir tablas Partin de Johns Hopkins</a>',
          ],
        });
      },
    },
    {
      id: "nodal-risk",
      number: "08",
      title: "Roach nodal / Briganti oficial",
      category: "prostata",
      subtitle: "Riesgo ganglionar orientativo.",
      source: "Roach III; Briganti/MSKCC sólo mediante modelo oficial",
      clinicalUse: "Estima riesgo de compromiso ganglionar en próstata localizada o de alto riesgo. Ayuda a decidir linfadenectomía extendida, irradiación pélvica y necesidad de estadificación avanzada, idealmente validando con nomogramas oficiales.",
      fields: [
        section("nodal_data", "Estimación ganglionar", {
          help: "Calcula sólo la fórmula histórica de Roach. Para decidir ePLND o RT pélvica, validar con Briganti/MSKCC oficial.",
        }),
        number("psa", "PSA", { value: 12, min: 0, step: 0.1, help: "PSA pretratamiento." }),
        select("gleason", "Gleason total", [6,7,8,9,10].map((value)=>option(String(value),`Gleason ${value}`)), { value: "7", help: "La fórmula Roach requiere el Gleason total exacto." }),
        section("briganti_reference", "Briganti / MSKCC", { help: "No se calcula un porcentaje local. Briganti 2012/2019 necesita sus variables exactas; PI-RADS aislado no sustituye el nomograma." }),
      ],
      calculate(values) {
        const psa = num(values.psa);
        const gleason = num(values.gleason);
        const roachRaw = (2 / 3) * psa + 10 * (gleason - 6);
        const interpretable = roachRaw >= 0 && roachRaw <= 100;
        return result({
          title: interpretable ? `Roach: ${pct(roachRaw, 1)}` : "Roach fuera del rango interpretable",
          detail: interpretable ? "La fórmula histórica Roach se informa de manera independiente; no se promedia con Briganti." : `La fórmula produjo ${pct(roachRaw, 1)}. No se recorta silenciosamente a 0–100%.`,
          badge: "fórmula histórica",
          score: 0,
          showScore: false,
          severity: interpretable ? "info" : "warn",
          metrics: [{ label: "Roach", value: pct(roachRaw, 1) }, { label: "PSA", value: psa }, { label: "Gleason total", value: gleason }],
          notes: [
            "Se eliminó el cálculo Briganti-like porque no correspondía a un nomograma validado.",
            '<a class="tool-inline-link" href="https://www.mskcc.org/nomograms/prostate/pre_op" target="_blank" rel="noopener">Abrir nomograma validado</a>',
          ],
        });
      },
    },
    {
      id: "mskcc-prostate",
      number: "09",
      title: "Nomogramas MSKCC próstata",
      category: "prostata",
      subtitle: "Aplicabilidad, datos requeridos y acceso al modelo oficial.",
      source: "Memorial Sloan Kettering Cancer Center",
      clinicalUse: "Prepara los datos para el nomograma dinámico oficial. No reproduce localmente sus coeficientes ni muestra porcentajes aproximados.",
      fields: [
        section("mskcc_scope", "Escenario de uso", {
          help: "Primero definí el momento clínico. El mayor error con nomogramas suele ser usar el modelo correcto en el paciente incorrecto.",
        }),
        select("scenario", "Escenario", [
          option("pre", "Antes de prostatectomía"),
          option("post", "Luego de prostatectomía"),
          option("salvage", "PSA en ascenso post-RP"),
          option("biopsy", "Riesgo en biopsia"),
          option("psadt", "PSA doubling time"),
          option("volume", "Volumen / densidad"),
          option("life", "Expectativa de vida"),
        ], { value: "pre", wide: true, help: "Seleccioná el nomograma que coincide con la decisión clínica." }),
        section("mskcc_inputs", "Matriz completa MSKCC", {
          help: "Marcá los datos disponibles. El panel de resultado muestra faltantes del nomograma elegido y una vista global de los demás escenarios.",
        }),
        ...mskccChecklistFields(),
      ],
      calculate(values) {
        const selectedNomogram = MSKCC_PROSTATE_NOMOGRAMS[values.scenario] || MSKCC_PROSTATE_NOMOGRAMS.pre;
        const selectedState = mskccCompletion(values, selectedNomogram);
        const selectedOptionalPct = selectedState.optional.length ? selectedState.doneOptional.length / selectedState.optional.length * 100 : 100;
        const selectedMissingCount = selectedState.missingRequired.length;
        return result({
          title: selectedMissingCount ? `${selectedMissingCount} datos obligatorios pendientes` : "Datos listos para MSKCC",
          detail: `Escenario: ${selectedNomogram.label}. El resultado numérico se obtiene únicamente en el nomograma oficial.`,
          badge: selectedMissingCount ? "incompleto" : "listo",
          score: 0,
          showScore: false,
          severity: selectedMissingCount ? "warn" : "good",
          metrics: [
            { label: "Obligatorios", value: `${selectedState.doneRequired.length}/${selectedState.required.length}` },
            { label: "Opcionales", value: selectedState.optional.length ? `${selectedState.doneOptional.length}/${selectedState.optional.length}` : "no aplica" },
            { label: "Faltantes", value: selectedMissingCount },
            { label: "Opcional", value: selectedState.optional.length ? pct(selectedOptionalPct) : "no aplica" },
          ],
          notes: [
            `<a class="tool-inline-link" href="${selectedNomogram.href}" target="_blank" rel="noopener">Abrir nomograma interactivo MSKCC: ${selectedNomogram.label}</a>`,
            mskccChecklistHtml("Datos obligatorios del escenario seleccionado", selectedState.required, values),
            mskccChecklistHtml("Datos opcionales del escenario seleccionado", selectedState.optional, values, "Este nomograma no declara campos opcionales en el formulario MSKCC."),
            mskccOverviewHtml(values),
            "Se retiraron todas las regresiones locales no validadas y sus porcentajes.",
            "No comparar resultados entre nomogramas distintos: cada herramienta responde una pregunta clinica diferente.",
          ],
        });
      },
    },
    {
      id: "biopsy-risk",
      number: "10",
      title: "PBCG — riesgo antes de biopsia",
      category: "prostata",
      subtitle: "Modelo público validado para cáncer de bajo y alto grado.",
      source: "Prostate Biopsy Collaborative Group",
      clinicalUse: "Integra edad, PSA, ascendencia africana, tacto rectal, antecedente familiar y biopsia previa para estimar probabilidades PBCG de ausencia de cáncer, bajo grado y alto grado.",
      fields: [
        section("biopsy_data", "Riesgo antes de biopsia", {
          help: "Pensado para decidir biopsia inicial o repetida, integrando PSA, volumen, RM y factores clínicos.",
        }),
        number("psa", "PSA", { value: 7, min: 2, max: 50, step: 0.1, help: "Rango validado PBCG: 2-50 ng/ml." }),
        number("age", "Edad", { value: 65, min: 40, max: 90, help: "Rango validado PBCG: 40-90 años." }),
        checkbox("dre", "Tacto sospechoso", { help: "Nódulo, induración, asimetría sospechosa o fijación." }),
        checkbox("family", "Antecedente familiar", { help: "Familiar de primer grado o historia genética relevante." }),
        checkbox("african", "Ascendencia africana", { help: "Factor incluido en modelos de riesgo prebiopsia como PBCG." }),
        checkbox("priorNegative", "Biopsia previa negativa", { help: "Reduce probabilidad estimada, pero no descarta lesión por RM/PSA-D." }),
      ],
      calculate(values) {
        const calculated = rules.pbcg(values);
        if(!calculated.valid)return result({title:"Fuera del rango validado",detail:calculated.reason,badge:"no calculable",score:0,showScore:false,severity:"warn",metrics:[],notes:["No se extrapola PBCG fuera de su población validada."]});
        return result({
          title: `PBCG: ${pct(calculated.highGrade,1)} de alto grado`,
          detail: "Probabilidades mutuamente excluyentes calculadas con los coeficientes públicos PBCG.",
          badge: "PBCG",
          score: 0,
          showScore: false,
          severity: "info",
          metrics: [{ label: "Sin cáncer", value: pct(calculated.noCancer,1) }, { label: "Bajo grado", value: pct(calculated.lowGrade,1) }, { label: "Alto grado", value: pct(calculated.highGrade,1) }],
          notes: [
            "Aplicabilidad: edad 40-90 años y PSA 2-50 ng/ml; la RM y PSA-D se interpretan aparte.",
            "Coeficientes públicos Cleveland Clinic PBCG; uso sujeto a PolyForm Noncommercial 1.0.0. Atribución completa en herramientas/NOTICE.md.",
            '<a class="tool-inline-link" href="https://riskcalc.org/PBCG/" target="_blank" rel="noopener">Comparar con PBCG oficial</a>',
          ],
        });
      },
    },
    {
      id: "psa-kinetics",
      number: "11",
      title: "PSA-D / PSA doubling time / BCR",
      category: "prostata",
      subtitle: "Densidad, cinética y criterios de recaída.",
      source: "PSA density, PSA-DT, Phoenix/AUA BCR",
      clinicalUse: "Calcula densidad de PSA, tiempo de duplicación y criterios de recaída bioquímica cuando corresponden. Es útil en vigilancia activa, sospecha de recaída, priorización de imágenes y planificación de rescate.",
      fields: [
        section("psa_density", "PSA density", {
          help: "Útil sobre todo en sospecha diagnóstica, vigilancia activa y lectura junto a RM.",
        }),
        number("psa", "PSA actual", { value: 6, min: 0, step: 0.01, help: "PSA más reciente." }),
        number("volume", "Volumen prostático (cc)", { value: 40, min: 1, help: "Volumen por RM o ecografía." }),
        section("psa_dt", "Cinética y recaída", {
          help: "Ingresá al menos tres mediciones con fecha; el cálculo usa regresión de ln(PSA) contra tiempo real.",
        }),
        textarea("psaSeries", "Serie fecha; PSA", { value: "01/01/2025; 1,0\n01/07/2025; 2,0\n01/01/2026; 4,0", wide: true, required: false, help: "Una medición por línea. Formato DD/MM/AAAA; valor. Para PSA-DT se recomiendan al menos tres mediciones." }),
        select("context", "Contexto clínico", [
          option("intact", "Próstata intacta / diagnóstico"),
          option("post_rp", "Post-prostatectomía"),
          option("post_rt", "Post-radioterapia"),
          option("crpc", "CRPC / enfermedad avanzada"),
        ], { value: "intact", help: "Evita aplicar criterios de recaída del contexto equivocado." }),
        number("nadir", "Nadir post-RT si aplica", { value: 0.4, min: 0, step: 0.01, required: false, help: "Sólo es necesario en el contexto post-radioterapia." }),
        checkbox("confirmed", "Segundo PSA confirmatorio post-prostatectomía", { help: "Marcar sólo si un valor posterior confirma PSA >0,2 ng/ml." }),
      ],
      calculate(values) {
        if (values.context === "post_rt" && values.nadir === "") return result({ title: "Falta el nadir post-radioterapia", detail: "Phoenix requiere comparar el PSA actual con nadir + 2 ng/ml.", badge: "no calculable", score: 0, showScore: false, severity: "warn", metrics: [], notes: [] });
        const psad = rules.psaDensity(values.psa,values.volume);
        const kinetics = rules.psaKinetics(parsePsaSeries(values.psaSeries));
        const recurrence = rules.biochemicalRecurrence({context:values.context,psa:values.psa,nadir:values.nadir,confirmed:values.confirmed});
        const dt=kinetics.doublingTimeMonths, velocity=kinetics.velocityPerYear;
        return result({
          title: `PSA-D ${psad===null?"ND":psad.toFixed(3)} · PSA-DT ${dt===null ? "sin duplicación calculable" : dt.toFixed(1)+" meses"}`,
          detail: recurrence.pendingConfirmation ? "Umbral post-RP alcanzado; falta un PSA confirmatorio posterior." : recurrence.met ? `Cumple ${recurrence.label}.` : recurrence.label,
          badge: recurrence.met ? "criterio cumplido" : recurrence.pendingConfirmation ? "pendiente" : "sin criterio",
          score: 0,
          showScore: false,
          severity: recurrence.met ? "bad" : recurrence.pendingConfirmation ? "warn" : "info",
          metrics: [{ label: "PSA-D", value: psad===null?"ND":psad.toFixed(3) }, { label: "PSA-DT", value: dt===null?"ND":`${dt.toFixed(1)} meses` }, { label: "Velocidad", value: velocity===null?"ND":`${velocity.toFixed(2)}/año` }, {label:"Mediciones",value:kinetics.count}],
          notes: [
            kinetics.count<3?"Con menos de tres mediciones el PSA-DT es frágil; agregar una tercera determinación.":"PSA-DT calculado por regresión logarítmica de toda la serie.",
            kinetics.count<2?"No hay una serie suficiente para evaluar intervalos temporales.":kinetics.minimumGapDays<28?"Hay determinaciones separadas por menos de cuatro semanas; interpretar la cinética con cautela.":!kinetics.withinTwelveMonths?"La serie abarca más de 12 meses; revisar si conviene usar una ventana clínica más reciente.":"La separación temporal de la serie es adecuada para el cálculo.",
            "Se eliminó el score compuesto local: PSA-D, PSA-DT y BCR son resultados diferentes.",
          ],
        });
      },
    },
    {
      id: "chaarted-latitude",
      number: "12",
      title: "CHAARTED / LATITUDE",
      category: "prostata",
      subtitle: "Volumen y riesgo en cáncer de próstata metastásico sensible.",
      source: "CHAARTED, LATITUDE",
      clinicalUse: "Clasifica volumen y riesgo en cáncer de próstata metastásico sensible a la castración. Ayuda a ordenar evidencia de ensayos, decidir intensificación sistémica y discutir radioterapia a próstata en enfermedad de bajo volumen.",
      fields: [
        section("mHSPC_volume", "Volumen CHAARTED", {
          help: "Alto volumen: metástasis visceral o ≥4 lesiones óseas con al menos una fuera de columna/pelvis.",
        }),
        checkbox("visceral", "Metástasis visceral", { help: "Hígado, pulmón u otra visceral; no incluye ganglios." }),
        number("bone", "Número de metástasis óseas", { value: 3, min: 0, help: "Número total de lesiones óseas documentadas." }),
        checkbox("outsideAxial", "Al menos una fuera de columna/pelvis", { help: "Relevante si hay ≥4 metástasis óseas." }),
        section("mHSPC_risk", "Riesgo LATITUDE", {
          help: "Alto riesgo LATITUDE: al menos 2 de 3 factores: Gleason ≥8, ≥3 metástasis óseas, metástasis visceral.",
        }),
        checkbox("gleasonHigh", "Gleason ≥8", { help: "Patrón de alto grado en biopsia/pieza." }),
      ],
      calculate(values) {
        const classified = rules.metastaticProstate(values);
        return result({
          title: `CHAARTED ${classified.chaartedHigh ? "alto volumen" : "bajo volumen"} · LATITUDE ${classified.latitudeHigh ? "alto riesgo" : "no alto riesgo"}`,
          detail: `LATITUDE suma ${classified.latitudeFactors}/3 factores. Clasificación basada en imagen convencional.`,
          badge: "clasificación pronóstica",
          score: 0,
          showScore: false,
          severity: "info",
          metrics: [{ label: "CHAARTED", value: classified.chaartedHigh ? "alto" : "bajo" }, { label: "LATITUDE", value: `${classified.latitudeFactors}/3` }],
          notes: [
            "En 2026 estas categorías no determinan por sí solas si corresponde combinación sistémica.",
            "CHAARTED bajo volumen de novo puede abrir discusión de radioterapia a próstata según guía y contexto.",
          ],
        });
      },
    },
    {
      id: "nmibc",
      number: "13",
      title: "NMIBC EAU / EORTC / CUETO",
      category: "vejiga",
      subtitle: "Recurrencia, progresión y grupo práctico.",
      source: "EORTC, CUETO, EAU NMIBC",
      clinicalUse: "Estratifica recurrencia y progresión en cáncer de vejiga no músculo invasivo. Orienta intensidad de resección, instilaciones, BCG, vigilancia y discusión de cistectomía temprana en escenarios de muy alto riesgo.",
      fields: [
        select("scenario", "Modelo", [option("eau", "EAU 2021/2026 — grupo de progresión"),option("eortc", "EORTC 2006 — sin BCG contemporáneo"),option("cueto", "CUETO 2009 — tratados con BCG")], {value:"eau",wide:true,help:"Cada modelo usa su propia población y no se combinan resultados."}),
        section("eau_nmibc", "EAU — datos obligatorios", {scenario:"eau",help:"Modelo actual para agrupar progresión; los factores especiales llevan a muy alto riesgo sin aplicar probabilidades de la tabla."}),
        select("eauPrimary","Presentación",[option("yes","Primario"),option("no","Recurrente")],{value:"yes",scenario:"eau"}),
        number("eauAge","Edad",{value:70,min:18,max:110,scenario:"eau"}),
        number("eauCount","Número de tumores",{value:1,min:1,scenario:"eau"}),
        number("eauSize","Diámetro máximo (cm)",{value:2,min:0,step:0.1,scenario:"eau"}),
        select("eauStage","Estadio",[option("ta","Ta"),option("t1","T1")],{value:"ta",scenario:"eau"}),
        checkbox("eauCis","CIS concomitante",{scenario:"eau"}),
        checkbox("eauPureCis","CIS puro primario",{scenario:"eau",help:"Se clasifica como alto riesgo, pero queda fuera de las probabilidades de la tabla."}),
        select("eauSystem","Sistema de grado",[option("who2004","WHO 2004/2022"),option("who1973","WHO 1973")],{value:"who2004",scenario:"eau"}),
        select("eauGrade","Grado",[option("low","Low grade (WHO 2004/2022)"),option("high","High grade (WHO 2004/2022)"),option("g1","G1 (WHO 1973)"),option("g2","G2 (WHO 1973)"),option("g3","G3 (WHO 1973)")],{value:"low",scenario:"eau"}),
        checkbox("eauLvi","Invasión linfovascular",{scenario:"eau"}),
        checkbox("eauProstaticCis","CIS en uretra prostática",{scenario:"eau"}),
        checkbox("eauVariant","Subtipo histológico agresivo",{scenario:"eau"}),
        section("eortc_nmibc", "EORTC 2006", {scenario:"eortc",help:"Usa grado WHO 1973; cohorte histórica con uso limitado de BCG."}),
        select("number", "Número de tumores", [option("0", "Único"), option("3", "2-7"), option("6", "≥8")], { value: "0", scenario:"eortc" }),
        select("size", "Tamaño", [option("0", "<3 cm"), option("3", "≥3 cm")], { value: "0", scenario:"eortc" }),
        select("prior", "Recurrencias previas", [option("0", "Primario"), option("2", "≤1/año"), option("4", ">1/año")], { value: "0", scenario:"eortc" }),
        checkbox("t1", "T1", {scenario:"eortc"}), checkbox("cis", "CIS", {scenario:"eortc"}),
        select("grade", "Grado WHO 1973", [option("0", "G1"), option("1", "G2"), option("2", "G3")], { value: "0", scenario:"eortc" }),
        section("cueto_nmibc", "CUETO 2009", {scenario:"cueto",help:"Aplicable a pacientes tratados con 12 instilaciones de BCG durante 5-6 meses."}),
        select("cuetoSex","Sexo",[option("male","Varón"),option("female","Mujer")],{value:"male",scenario:"cueto"}), number("cuetoAge","Edad",{value:65,min:18,max:110,scenario:"cueto"}),
        checkbox("cuetoMoreThree",">3 tumores",{scenario:"cueto"}), checkbox("cuetoRecurrent","Tumor recurrente",{scenario:"cueto"}),
        checkbox("cuetoT1","T1",{scenario:"cueto"}), checkbox("cuetoCis","CIS",{scenario:"cueto"}),
        select("cuetoGrade","Grado WHO 1973",[option("g1","G1"),option("g2","G2"),option("g3","G3")],{value:"g1",scenario:"cueto"}),
        checkbox("cuetoConfirmed","Confirmo cohorte CUETO con BCG 5–6 meses",{scenario:"cueto",help:"Sin esta condición no se muestran las probabilidades CUETO."}),
      ],
      calculate(values) {
        if(values.scenario==="eau"){
          const calculated=rules.eauNmibc({system:values.eauSystem,primary:values.eauPrimary==="yes",age:values.eauAge,multiple:num(values.eauCount)>1,size:values.eauSize,stage:values.eauStage,cis:values.eauCis,pureCis:values.eauPureCis,grade:values.eauGrade,lvi:values.eauLvi,prostaticCis:values.eauProstaticCis,variant:values.eauVariant});
          if(calculated.valid===false)return result({title:"EAU NMIBC no calculable",detail:calculated.reason,badge:"revisar datos",score:0,showScore:false,severity:"warn",metrics:[],notes:["WHO 2004/2022 requiere low/high grade; WHO 1973 requiere G1/G2/G3."]});
          const metrics=[{label:"Grupo EAU",value:calculated.group},{label:"Factores clínicos",value:`${calculated.factors}/3`}];
          if(calculated.probabilities)metrics.push({label:"Progresión 1 año",value:pct(calculated.probabilities[0],2)},{label:"Progresión 5 años",value:pct(calculated.probabilities[1],1)},{label:"Progresión 10 años",value:pct(calculated.probabilities[2],1)});
          return result({title:`EAU: riesgo ${calculated.group}`,detail:calculated.probabilities?"Probabilidades poblacionales para tumores primarios incluidos en el modelo.":"Grupo asignado; la tabla no ofrece probabilidades válidas para este contexto.",badge:"EAU 2021/2026",score:0,showScore:false,severity:calculated.group==="bajo"?"good":calculated.group==="intermedio"?"warn":"bad",metrics,notes:["No combinar este grupo con EORTC o CUETO."]});
        }
        if(values.scenario==="cueto"){
          if(!values.cuetoConfirmed)return result({title:"Confirmar aplicabilidad CUETO",detail:"Las probabilidades sólo corresponden a la cohorte tratada con 12 instilaciones de BCG durante 5–6 meses.",badge:"sin cálculo",score:0,showScore:false,severity:"warn",metrics:[],notes:[]});
          const calculated=rules.cuetoNmibc({sex:values.cuetoSex,age:values.cuetoAge,moreThanThree:values.cuetoMoreThree,recurrent:values.cuetoRecurrent,t1:values.cuetoT1,cis:values.cuetoCis,grade:values.cuetoGrade});
          if(calculated.valid===false)return result({title:"CUETO no calculable",detail:calculated.reason,badge:"revisar datos",score:0,showScore:false,severity:"warn",metrics:[],notes:[]});
          return result({title:"CUETO — cohorte tratada con BCG",detail:`Recurrencia ${calculated.recurrenceScore}; progresión ${calculated.progressionScore}.`,badge:"CUETO 2009",score:0,showScore:false,severity:"info",metrics:[{label:"Recurrencia 1/5 años",value:`${calculated.recurrence[0]}% / ${calculated.recurrence[1]}%`},{label:"Progresión 1/5 años",value:`${calculated.progression[0]}% / ${calculated.progression[1]}%`}],notes:["Aplicar sólo al esquema CUETO de BCG durante 5-6 meses."]});
        }
        const calculated=rules.eortcNmibc(values);
        if(calculated.valid===false)return result({title:"EORTC no calculable",detail:calculated.reason,badge:"revisar datos",score:0,showScore:false,severity:"warn",metrics:[],notes:[]});
        return result({title:"EORTC 2006",detail:`Recurrencia ${calculated.recurrenceScore}; progresión ${calculated.progressionScore}.`,badge:"cohorte histórica",score:0,showScore:false,severity:"info",metrics:[{label:"Recurrencia 1/5 años",value:`${calculated.recurrence1y}% / ${calculated.recurrence5y}%`},{label:"Progresión 1/5 años",value:`${calculated.progression1y}% / ${calculated.progression5y}%`}],notes:["Puede sobreestimar riesgo con BCG contemporáneo; no equivale al grupo EAU actual."]});
      },
    },
    {
      id: "cystectomy",
      number: "14",
      title: "Post-cistectomía",
      category: "vejiga",
      subtitle: "Disparadores EAU 2026 para tratamiento adyuvante.",
      source: "EAU MIBC Guidelines 2026",
      clinicalUse: "Ordena criterios publicados para quimioterapia, nivolumab y consideración de radioterapia después de cistectomía; no inventa una probabilidad de recurrencia.",
      fields: [
        section("cystectomy_path", "Patología post-cistectomía", {
          help: "Usar informe patológico final. pT, pN y márgenes son los ejes principales de riesgo y adyuvancia.",
        }),
        select("m", "Metástasis", [option("mx", "No confirmada"), option("m0", "M0"), option("m1", "M1")], {value:"mx"}),
        select("pt", "p/ypT", [option("0", "pT0/Tis/Ta/T1"),option("2", "pT2 / ypT2"),option("3", "pT3a"),option("3.5", "pT3b"),option("4", "pT4")], { value: "2" }),
        select("pn", "pN", [option("nx", "pNx"), option("n0", "pN0"), option("nplus", "pN+")], { value: "nx" }),
        checkbox("margin", "Margen positivo", { help: "Margen quirúrgico comprometido." }),
        select("perioperative", "Tratamiento perioperatorio previo", [option("none", "Sin tratamiento perioperatorio"), option("nac", "Neoadyuvancia convencional"), option("modern", "Esquema perioperatorio moderno")], { value: "none", help: "Con un esquema moderno prevalece el protocolo específico; no se apilan adyuvancias automáticamente." }),
        select("cisStatus", "Aptitud/decisión sobre cisplatino", [option("unknown", "No evaluada"), option("eligible", "Apto y acepta"), option("declined", "Apto pero rechaza"), option("ineligible", "No apto")], { value: "unknown" }),
      ],
      calculate(values) {
        const cisEligible=values.cisStatus==="eligible"||values.cisStatus==="declined"?true:values.cisStatus==="ineligible"?false:undefined;
        const calculated=rules.postCystectomy({metastatic:values.m==="m1",mKnown:values.m!=="mx",t:values.pt,nodeKnown:values.pn!=="nx",nodePositive:values.pn==="nplus",marginPositive:values.margin,nac:values.perioperative==="nac",modernPerioperative:values.perioperative==="modern",cisplatinEligible:cisEligible,cisplatinDeclined:values.cisStatus==="declined"});
        return result({
          title: calculated.inScope ? "Revisión adyuvante post-cistectomía" : calculated.incomplete ? "Datos incompletos" : "Fuera de alcance adyuvante",
          detail: calculated.recommendations.join(" · "),
          badge: "EAU 2026",
          score: 0,
          showScore: false,
          severity: calculated.inScope ? "info" : "warn",
          metrics: calculated.recommendations.map((value,index)=>({label:`Punto ${index+1}`,value})),
          notes: [
            "Si recibió un esquema perioperatorio moderno, debe prevalecer el protocolo específico y la discusión multidisciplinaria.",
            "La radioterapia adyuvante puede mejorar control locorregional, pero no demostró beneficio en supervivencia global.",
          ],
        });
      },
    },
    {
      id: "cisplatin",
      number: "15",
      title: "Aptitud para cisplatino y platinum",
      category: "vejiga",
      subtitle: "Criterios EAU 2026 y zona renal limítrofe.",
      source: "EAU MIBC 2026 · consenso de Galsky",
      clinicalUse: "Distingue aptitud probable para cisplatino convencional, posible carboplatino e inelegibilidad para todo platinum. No selecciona por sí sola el tratamiento sistémico actual.",
      fields: [
        section("cisplatin_patient", "Criterios de aptitud", {
          help: "Edad sola no contraindica cisplatino. En casos renales equívocos conviene medir formalmente GFR.",
        }),
        select("ecog", "ECOG", [0, 1, 2, 3, 4].map((v) => option(String(v), `ECOG ${v}`)), { value: "1", help: "ECOG >1 es criterio de no aptitud para cisplatino convencional." }),
        select("renalMethod", "Método de función renal", [option("measured_gfr", "GFR medida"), option("measured_crcl", "CrCl medido"), option("calculated_crcl", "CrCl calculado"), option("egfr", "eGFR")], { value: "measured_gfr", help: "No mezclar métodos sin documentarlos." }),
        number("gfr", "Valor renal (ml/min)", { value: 65, min: 0.1, step: 0.1, help: "Exactamente 60 se considera no apto para cisplatino convencional en este criterio conservador." }),
        select("hearing", "Hipoacusia CTCAE", [0, 1, 2, 3].map((v) => option(String(v), `G${v}`)), { value: "0", help: "Grado ≥2 pesa contra cisplatino pleno." }),
        select("neuro", "Neuropatía CTCAE", [0, 1, 2, 3].map((v) => option(String(v), `G${v}`)), { value: "0", help: "Grado ≥2 pesa contra cisplatino pleno." }),
        select("nyha", "NYHA", [1, 2, 3, 4].map((v) => option(String(v), `NYHA ${v}`)), { value: "1", help: "NYHA III/IV es criterio de inelegibilidad." }),
        checkbox("severeComorbidity", "Comorbilidad severa >G2", { help: "Puede volver al paciente no apto para cualquier platinum." }),
      ],
      calculate(values) {
        const classified = rules.cisplatinEligibility({ ecog: values.ecog, gfr: values.gfr, hearing: values.hearing, neuropathy: values.neuro, nyha: values.nyha, severeComorbidity: values.severeComorbidity });
        const title = classified.platinumIneligible ? "No apto para platinum" : classified.eligible ? "Apto probable para cisplatino convencional" : "No apto para cisplatino convencional; posible carboplatino";
        return result({
          title,
          detail: classified.reasons.length ? `Criterios presentes: ${classified.reasons.join(", ")}.` : "No se detectan criterios conservadores de no aptitud.",
          badge: classified.platinumIneligible ? "no platinum" : classified.eligible ? "cisplatino probable" : "posible carboplatino",
          score: 0,
          showScore: false,
          severity: classified.platinumIneligible ? "bad" : classified.eligible ? "good" : "warn",
          metrics: [{ label: "Función renal", value: `${num(values.gfr)} ml/min` }, { label: "Método", value: values.renalMethod }, { label: "Criterios", value: classified.reasons.length }],
          notes: [
            "Edad sola no contraindica cisplatino.",
            classified.borderlineRenal ? "GFR 40–60: zona renal limítrofe; considerar medición isotópica/formal. Split-dose no se recomienda automáticamente." : "Aplicar la ficha y el protocolo específicos del régimen elegido.",
            "Regímenes perioperatorios modernos pueden tener umbrales propios; esos criterios prevalecen.",
          ],
        });
      },
    },
    {
      id: "utuc",
      number: "16",
      title: "UTUC — riesgo EAU 2026",
      category: "vejiga",
      subtitle: "Bajo vs alto riesgo en urotelio superior.",
      source: "EAU UTUC risk stratification",
      clinicalUse: "Diferencia bajo y alto riesgo en carcinoma urotelial del tracto superior. Orienta manejo conservador renal frente a nefroureterectomía y marca cuándo conviene confirmar grado, citología e imágenes.",
      fields: [
        select("utucM", "Enfermedad metastásica", [option("m0", "No / M0"), option("m1", "Sí / M1")], { value: "m0", wide: true, help: "M1 queda fuera de este módulo de estratificación local." }),
        section("utuc_low", "Datos para estratificar", { help: "Los criterios fuertes y débiles se informan por separado. Un criterio débil aislado no convierte una lesión low-grade en alto riesgo." }),
        number("size", "Tamaño tumoral (cm)", { value: 1.5, min: 0, step: 0.1, help: "Umbral práctico: ≥2 cm aumenta riesgo." }),
        select("focality", "Focalidad", [option("missing", "No evaluada"), option("unifocal", "Unifocal"), option("multifocal", "Multifocal")], { value: "missing", help: "La multifocalidad es un factor débil." }),
        select("cytology", "Citología", [option("missing", "No disponible"), option("negative", "Negativa para high-grade"), option("high", "High-grade")], { value: "missing", help: "Una citología high-grade es criterio fuerte." }),
        select("biopsy", "Biopsia URS", [option("missing", "No disponible"), option("low", "Low-grade"), option("high", "High-grade"), option("nondiagnostic", "No diagnóstica")], { value: "missing", help: "No diagnóstica no equivale a high-grade." }),
        select("ctAssessment", "TC: invasión local", [option("missing", "No evaluada"), option("noninvasive", "Sin aspecto invasivo"), option("invasive", "Invasión local")], { value: "missing", help: "La invasión local en TC es criterio fuerte." }),
        section("utuc_high", "Señales de alto riesgo", {
          help: "Subtipo histológico agresivo es un criterio fuerte; hidroureteronefrosis es un criterio débil.",
        }),
        checkbox("hydro", "Hidroureteronefrosis", { help: "Factor débil si los datos restantes son low-grade/no invasivos." }),
        checkbox("variant", "Histología variante", { help: "Variante agresiva o diferenciación divergente." }),
      ],
      calculate(values) {
        const classified = rules.utucRisk({ metastatic: values.utucM === "m1", size: values.size, focality: values.focality, cytology: values.cytology, biopsy: values.biopsy, ctAssessment: values.ctAssessment, hydronephrosis: values.hydro, variant: values.variant });
        const details = classified.key === "high" ? `Criterios fuertes: ${classified.strong.join(", ")}.`
          : classified.key === "uncertain" ? `Faltan: ${(classified.missing || []).join(", ")}.`
            : classified.key === "low_with_weak" ? `Factores débiles: ${classified.weak.join(", ")}. Decisión compartida.`
              : classified.key === "low" ? "Unifocal, <2 cm, citología negativa para high-grade, biopsia low-grade y TC no invasiva." : classified.label;
        return result({
          title: classified.label,
          detail: details,
          badge: classified.key === "high" ? "criterio fuerte" : classified.key === "low" ? "bajo riesgo" : classified.key === "out_of_scope" ? "fuera de alcance" : "revisar datos",
          score: 0,
          showScore: false,
          severity: classified.key === "high" ? "bad" : classified.key === "low" ? "good" : "warn",
          metrics: [{ label: "Tamaño", value: `${values.size} cm` }, { label: "Biopsia", value: values.biopsy }, { label: "Criterios fuertes", value: classified.strong.length }, { label: "Factores débiles", value: classified.weak.length }],
          notes: [
            "Los criterios débiles —tamaño ≥2 cm, multifocalidad e hidroureteronefrosis— no demuestran invasión por sí solos en enfermedad low-grade.",
            "La decisión entre preservación renal y nefroureterectomía requiere función renal, factibilidad técnica y discusión multidisciplinaria.",
          ],
        });
      },
    },
    {
      id: "renal-complexity",
      number: "17",
      title: "RENAL / PADUA",
      category: "renal",
      subtitle: "Complejidad anatómica, con escalas separadas.",
      source: "RENAL nephrometry 2009 · PADUA 2009",
      clinicalUse: "Describe complejidad anatómica de una masa renal. No estima malignidad ni indica por sí sola cirugía, ablación o vigilancia.",
      fields: [
        select("scenario", "Escala", [option("renal", "RENAL nephrometry"), option("padua", "PADUA")], { value: "renal", wide: true, help: "Las definiciones y los grupos no se mezclan." }),
        section("renal_anatomy", "RENAL nephrometry", { scenario: "renal", help: "R + E + N + L, descriptor anterior/posterior y sufijo hiliar." }),
        number("renalSize", "Radio: diámetro máximo (cm)", { value: 3.2, min: 0.1, step: 0.1, scenario: "renal" }),
        select("renalExo", "Exofítico/endofítico", [option("1", "≥50% exofítico"), option("2", "<50% exofítico"), option("3", "Completamente endofítico")], { value: "1", scenario: "renal" }),
        select("renalNear", "Distancia a seno/sistema colector", [option("1", "≥7 mm"), option("2", ">4 y <7 mm"), option("3", "≤4 mm")], { value: "1", scenario: "renal" }),
        select("renalAp", "Cara", [option("a", "Anterior (a)"), option("p", "Posterior (p)"), option("x", "Indeterminada (x)")], { value: "x", scenario: "renal", help: "Descriptor, sin puntos." }),
        select("renalLocation", "Relación con líneas polares", [option("1", "Completamente polar"), option("2", "Cruza una línea polar"), option("3", "Central / >50% más allá / cruza línea media")], { value: "1", scenario: "renal" }),
        checkbox("renalHilar", "Toca arteria o vena renal principal (h)", { scenario: "renal", help: "Se informa como sufijo; no suma puntos." }),
        section("padua_anatomy", "PADUA", { scenario: "padua", help: "Seis componentes puntuados y descriptor anterior/posterior." }),
        number("paduaSize", "Diámetro máximo (cm)", { value: 3.2, min: 0.1, step: 0.1, scenario: "padua" }),
        select("paduaLong", "Ubicación longitudinal", [option("1", "Polar o cruza <50% línea sinusal"), option("2", "Central o cruza >50%")], { value: "1", scenario: "padua" }),
        select("paduaExo", "Exofítico/endofítico", [option("1", "≥50% exofítico"), option("2", "<50% exofítico"), option("3", "Completamente endofítico")], { value: "1", scenario: "padua" }),
        select("paduaRim", "Borde renal", [option("1", "Lateral"), option("2", "Medial")], { value: "1", scenario: "padua" }),
        select("paduaSinus", "Seno renal", [option("1", "No involucrado"), option("2", "Involucrado")], { value: "1", scenario: "padua" }),
        select("paduaCollecting", "Sistema colector", [option("1", "No involucrado"), option("2", "Desplazado o infiltrado")], { value: "1", scenario: "padua" }),
        select("paduaAp", "Cara", [option("a", "Anterior (a)"), option("p", "Posterior (p)"), option("x", "Indeterminada (x)")], { value: "x", scenario: "padua", help: "Descriptor, sin puntos." }),
      ],
      calculate(values) {
        const isPadua = values.scenario === "padua";
        const calculated = isPadua
          ? rules.paduaNephrometry({ size: values.paduaSize, longitudinal: values.paduaLong, exophytic: values.paduaExo, rim: values.paduaRim, sinus: values.paduaSinus, collecting: values.paduaCollecting, anteriorPosterior: values.paduaAp })
          : rules.renalNephrometry({ size: values.renalSize, exophytic: values.renalExo, nearness: values.renalNear, location: values.renalLocation, anteriorPosterior: values.renalAp, hilar: values.renalHilar });
        const name = isPadua ? "PADUA" : "RENAL";
        return result({
          title: `${name} ${calculated.total}${calculated.suffix}: complejidad ${calculated.complexity}`,
          detail: "Resultado anatómico de la escala seleccionada; no es una probabilidad de malignidad ni de complicaciones.",
          badge: name,
          score: 0,
          showScore: false,
          severity: "info",
          metrics: [{ label: name, value: `${calculated.total}${calculated.suffix}` }, { label: "Complejidad", value: calculated.complexity }, { label: "Tamaño", value: `${isPadua ? values.paduaSize : values.renalSize} cm` }],
          notes: [
            isPadua ? "PADUA total 6–14: 6–7 baja, 8–9 moderada, ≥10 alta." : "RENAL total 4–12: 4–6 baja, 7–9 moderada, 10–12 alta.",
            "Corroborar cada descriptor en imágenes multiplanares con contraste cuando sea posible.",
          ],
        });
      },
    },
    {
      id: "leibovich",
      number: "18",
      title: "Leibovich 2003 / UISS localizado",
      category: "renal",
      subtitle: "Modelos posnefrectomía separados y sin porcentajes locales.",
      source: "EAU RCC 2026 · Leibovich 2003 · UISS",
      clinicalUse: "Leibovich 2003 estratifica recurrencia en ccRCC M0 operado. UISS localizado resume estadio, grado y ECOG, especialmente útil como referencia en no-ccRCC. SSIGN se retiró por no ser equivalente con TNM moderno.",
      fields: [
        select("scenario", "Modelo", [option("leibovich", "Leibovich 2003 — ccRCC M0"), option("uiss", "UISS — enfermedad localizada")], { value: "leibovich", wide: true, help: "No combinar las dos escalas." }),
        section("leibovich_path", "Leibovich 2003", { scenario: "leibovich", help: "ccRCC esporádico, unilateral, operado, pT1–4, N0/N+, M0." }),
        select("leibPt", "pT", [option("pt1a", "pT1a"), option("pt1b", "pT1b"), option("pt2", "pT2"), option("pt3", "pT3"), option("pt4", "pT4")], { value: "pt1a", scenario: "leibovich" }),
        checkbox("leibPn", "pN+", { scenario: "leibovich" }),
        number("leibSize", "Tamaño patológico (cm)", { value: 5, min: 0.1, step: 0.1, scenario: "leibovich" }),
        select("leibGrade", "Grado", [option("1", "G1"), option("2", "G2"), option("3", "G3"), option("4", "G4")], { value: "2", scenario: "leibovich" }),
        checkbox("leibNecrosis", "Necrosis tumoral", { scenario: "leibovich" }),
        section("uiss_path", "UISS localizado", { scenario: "uiss", help: "La versión resumida sólo clasifica N0 M0." }),
        select("uissPt", "pT", [option("pt1a", "pT1a"), option("pt1b", "pT1b"), option("pt2", "pT2"), option("pt3", "pT3"), option("pt4", "pT4")], { value: "pt1a", scenario: "uiss" }),
        select("uissN", "Ganglios", [option("n0", "N0"), option("nplus", "N+")], { value: "n0", scenario: "uiss" }),
        select("uissM", "Metástasis", [option("m0", "M0"), option("m1", "M1")], { value: "m0", scenario: "uiss" }),
        select("uissGrade", "Grado", [option("1", "G1"), option("2", "G2"), option("3", "G3"), option("4", "G4")], { value: "2", scenario: "uiss" }),
        select("uissEcog", "ECOG", [0, 1, 2, 3, 4].map((v) => option(String(v), `ECOG ${v}`)), { value: "0", scenario: "uiss" }),
      ],
      calculate(values) {
        const isUiss = values.scenario === "uiss";
        if (isUiss) {
          const classified = rules.uissLocalized({ pt: values.uissPt, nodePositive: values.uissN === "nplus", metastatic: values.uissM === "m1", grade: values.uissGrade, ecog: values.uissEcog });
          return result({ title: `UISS: ${classified.label}`, detail: classified.key === "not_localized" ? "Esta versión resumida no clasifica N+ o M1 como enfermedad localizada." : "Grupo UISS localizado calculado sin combinarlo con Leibovich.", badge: "UISS", score: 0, showScore: false, severity: "info", metrics: [{ label: "Grupo", value: classified.label }, { label: "pT", value: String(values.uissPt).toUpperCase() }, { label: "ECOG", value: values.uissEcog }], notes: ["No se muestran porcentajes locales no calibrados."] });
        }
        const calculated = rules.leibovich2003({ pt: values.leibPt, nodePositive: values.leibPn, size: values.leibSize, grade: values.leibGrade, necrosis: values.leibNecrosis });
        return result({
          title: `Leibovich ${calculated.total}: riesgo ${calculated.category}`,
          detail: "Puntaje determinístico publicado para ccRCC M0 operado.",
          badge: "Leibovich 2003",
          score: 0,
          showScore: false,
          severity: calculated.category === "bajo" ? "good" : calculated.category === "intermedio" ? "warn" : "bad",
          metrics: [{ label: "Puntaje", value: calculated.total }, { label: "Grupo", value: calculated.category }],
          notes: [
            "Aplicar sólo a carcinoma renal de células claras, M0, después de cirugía.",
            "El grupo estratifica recurrencia; no indica por sí solo adyuvancia.",
          ],
        });
      },
    },
    {
      id: "imdc",
      number: "19",
      title: "IMDC — carcinoma renal metastásico",
      category: "renal",
      subtitle: "Pronóstico en carcinoma renal metastásico.",
      source: "EAU RCC 2026 · IMDC",
      clinicalUse: "Clasifica pronóstico en carcinoma renal metastásico con factores clínicos y de laboratorio. Ayuda a estratificar riesgo, conversar pronóstico y contextualizar evidencia de tratamientos sistémicos.",
      fields: [
        section("imdc_factors", "Factores IMDC", {
          help: "Marcar cada factor adverso presente al inicio de tratamiento sistémico para carcinoma renal metastásico.",
        }),
        checkbox("kps", "KPS <80%", { help: "Performance status disminuido." }),
        checkbox("time", "Tiempo diagnóstico-tratamiento <1 año", { help: "Menos de 12 meses desde diagnóstico inicial a inicio de terapia sistémica." }),
        checkbox("hb", "Hemoglobina baja", { help: "Por debajo del límite inferior normal del laboratorio." }),
        checkbox("calcium", "Calcio corregido alto", { help: "Por encima del límite superior normal." }),
        checkbox("neut", "Neutrófilos altos", { help: "Por encima del límite superior normal." }),
        checkbox("platelets", "Plaquetas altas", { help: "Por encima del límite superior normal." }),
      ],
      calculate(values) {
        const calculated = rules.imdc({ kps: values.kps, time: values.time, hb: values.hb, calcium: values.calcium, neutrophils: values.neut, platelets: values.platelets });
        return result({
          title: `IMDC ${calculated.category}`,
          detail: `${calculated.total} de 6 factores adversos.`,
          badge: "IMDC",
          score: 0,
          showScore: false,
          severity: calculated.category === "favorable" ? "good" : calculated.category === "intermedio" ? "warn" : "bad",
          metrics: [{ label: "Factores", value: `${calculated.total} / 6` }, { label: "Grupo", value: calculated.category }],
          notes: [
            "IMDC estratifica pronóstico, no selecciona por sí solo el régimen.",
            "No se muestran medianas históricas como si fueran supervivencia individual con tratamientos actuales.",
          ],
        });
      },
    },
    {
      id: "igcccg",
      number: "20",
      title: "IGCCCG testículo",
      category: "testiculo",
      subtitle: "Riesgo en tumores germinales metastásicos.",
      source: "EAU Testicular Cancer 2026 · IGCCCG Update",
      clinicalUse: "Clasifica pronóstico en tumores germinales metastásicos según histología, marcadores, sitio primario y metástasis viscerales. Guía intensidad de quimioterapia, seguimiento y comunicación pronóstica.",
      fields: [
        section("igcccg_context", "Clasificación pronóstica inicial", {
          help: "Usar antes de quimioterapia de primera línea en enfermedad metastásica, con marcadores séricos pretratamiento.",
        }),
        select("histology", "Histología", [option("seminoma", "Seminoma"), option("nonseminoma", "No seminoma")], { value: "nonseminoma", help: "Separar seminoma de no seminoma cambia la clasificación." }),
        select("primary", "Sitio primario", [option("testis", "Testicular"), option("retroperitoneal", "Retroperitoneal"), option("mediastinal", "Mediastinal"), option("other", "Otro / no clasificable")], { value: "testis", help: "El primario mediastinal es desfavorable en no seminoma." }),
        checkbox("nonPulmonary", "Metástasis visceral no pulmonar", { help: "Hígado, cerebro, hueso u otra visceral no pulmonar." }),
        number("afp", "AFP ng/ml", { value: 120, min: 0, step: 1, help: "Usar marcador pretratamiento." }),
        number("afpUpperLimit", "Límite superior normal de AFP", { value: 10, min: 0.1, step: 0.1, help: "En seminoma la AFP debe permanecer normal." }),
        number("hcg", "hCG IU/L", { value: 800, min: 0, step: 1, help: "Usar hCG pretratamiento. Si el laboratorio informa otra unidad, normalizar antes." }),
        number("ldhRatio", "LDH x límite superior normal", { value: 1.1, min: 0, step: 0.1, help: "Ejemplo: LDH 2 veces el LSN = 2." }),
      ],
      calculate(values) {
        const classified = rules.igcccg(values);
        if (!classified.valid) return result({ title: classified.label, detail: values.histology === "seminoma" && num(values.afp) > num(values.afpUpperLimit) ? "Revisar histología, componente no seminomatoso y otras causas de AFP elevada." : "El perfil no entra en una categoría clásica sin aclarar el sitio primario.", badge: "no clasificable", score: 0, showScore: false, severity: "warn", metrics: [{ label: "AFP", value: num(values.afp) }, { label: "LSN AFP", value: num(values.afpUpperLimit) }], notes: [] });
        return result({
          title: `IGCCCG: ${classified.label}`,
          detail: `${values.histology === "seminoma" ? "Seminoma" : "No seminoma"}, grupo clásico ${classified.markerGroup}.`,
          badge: "IGCCCG",
          score: 0,
          showScore: false,
          severity: classified.category === "bueno" ? "good" : classified.category === "intermedio" ? "warn" : "bad",
          metrics: [{ label: "S", value: classified.markerGroup }, { label: "PFS 5 años", value: `${classified.pfs5y}% poblacional` }, { label: "Supervivencia 5 años", value: `${classified.os5y}% poblacional` }, { label: "Sitio primario", value: values.primary }],
          notes: [
            "Clasificar antes de iniciar quimioterapia.",
            classified.ldhWarning ? "Seminoma de buen grupo clásico con LDH >2,5× LSN: la actualización IGCCCG señala peor PFS, sin cambiar el grupo clásico." : "Los porcentajes son resultados de grupos poblacionales contemporáneos, no una predicción individual.",
            "Confirmar LDH, AFP, hCG, sitio primario y metástasis viscerales inmediatamente antes de primera línea.",
          ],
        });
      },
    },
  ];

  const oncologyExtensions = [
    [window.createOncologyToolsGeneral, window.OncologyRulesGeneral, "general/mama/hematología"],
    [window.createOncologyToolsGyne, window.OncologyRulesGyne, "ginecología"],
    [window.createOncologyToolsGiThorax, window.OncologyRulesGiThorax, "pulmón/digestivo"],
  ];
  const addedTools = oncologyExtensions.flatMap(([factory, extensionRules, label]) => {
    if (typeof factory !== "function" || !extensionRules) throw new Error(`No se pudo cargar el módulo de herramientas de ${label}.`);
    return factory({ number, select, checkbox, section, option, result, rules: extensionRules });
  });
  if (addedTools.length !== 30) throw new Error(`La ampliación oncológica debe contener 30 herramientas; se cargaron ${addedTools.length}.`);
  if (typeof window.createRadiotherapyTools !== "function" || !window.RadiotherapyRules) {
    throw new Error("No se pudo cargar el módulo de herramientas de radioterapia.");
  }
  const radiotherapyTools = window.createRadiotherapyTools({
    number,
    select,
    section,
    option,
    result,
    rules: window.RadiotherapyRules,
  });
  if (radiotherapyTools.length !== 4) {
    throw new Error(`La ampliación de radioterapia debe contener 4 herramientas; se cargaron ${radiotherapyTools.length}.`);
  }
  TOOLS.push(...addedTools, ...radiotherapyTools);
  TOOLS.forEach((tool, index) => { tool.number = String(index + 1).padStart(2, "0"); });

  function repairedText(value) {
    const text = String(value || "");
    if (!/[ÃÂâ]/.test(text)) return text;
    try { return decodeURIComponent(escape(text)); } catch { return text; }
  }

  function toolConfigurationKey(value) {
    return repairedText(value).normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase()
      .replace(/[^a-z0-9_]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 80);
  }

  function builtInBlueprint(tool) {
    return {
      id: tool.id,
      key: toolConfigurationKey(tool.title),
      title: repairedText(tool.title),
      shortTitle: repairedText(tool.shortTitle || tool.title),
      category: tool.category || "general",
      subtitle: repairedText(tool.subtitle || ""),
      source: repairedText(tool.source || ""),
      clinicalUse: repairedText(tool.clinicalUse || ""),
      fields: (tool.fields || []).map((fieldDefinition) => ({
        key: fieldDefinition.id,
        label: repairedText(fieldDefinition.label || ""),
        type: fieldDefinition.type,
        required: fieldDefinition.required === true,
        min: fieldDefinition.min ?? null,
        max: fieldDefinition.max ?? null,
        step: fieldDefinition.step ?? null,
        unit: repairedText(fieldDefinition.unit || ""),
        help: repairedText(fieldDefinition.help || ""),
        placeholder: repairedText(fieldDefinition.placeholder || ""),
        value: fieldDefinition.value ?? null,
        checkedPoints: Number(fieldDefinition.checkedPoints ?? fieldDefinition.weight ?? 0) || 0,
        options: (fieldDefinition.options || []).map((entry) => ({
          value: entry.value,
          label: repairedText(entry.label || entry.value),
          ...(Number.isFinite(Number(entry.points)) ? { points: Number(entry.points) } : {}),
        })),
        scoreRules: Array.isArray(fieldDefinition.scoreRules) ? fieldDefinition.scoreRules : [],
      })),
    };
  }

  window.HCOP_BUILTIN_TOOL_BLUEPRINTS = TOOLS.map(builtInBlueprint);
  if (new URLSearchParams(window.location.search).get("catalog") === "1") {
    window.parent?.postMessage({ type: "hcop-tool-blueprints", tools: window.HCOP_BUILTIN_TOOL_BLUEPRINTS }, window.location.origin);
    return;
  }

  function configuredFields(definition, originalTool = null) {
    if (definition.mode === "builtin" && originalTool) {
      const customByKey = new Map((definition.fields || []).map((fieldDefinition) => [fieldDefinition.key, fieldDefinition]));
      return (originalTool.fields || []).map((sourceField) => {
        const custom = customByKey.get(sourceField.id) || {};
        const customOptions = new Map((custom.options || []).map((entry) => [String(entry.value), entry]));
        return {
          ...sourceField,
          label: custom.label || sourceField.label,
          help: custom.help || sourceField.help,
          unit: custom.unit || sourceField.unit,
          options: (sourceField.options || []).map((entry) => ({ ...entry, label: customOptions.get(String(entry.value))?.label || entry.label })),
        };
      });
    }
    return (definition.fields || []).map((fieldDefinition) => ({
      id: fieldDefinition.key,
      label: fieldDefinition.label,
      type: fieldDefinition.type,
      required: fieldDefinition.required,
      min: fieldDefinition.min,
      max: fieldDefinition.max,
      step: fieldDefinition.step || "any",
      unit: fieldDefinition.unit,
      help: fieldDefinition.help,
      value: fieldDefinition.value,
      options: fieldDefinition.options || [],
    }));
  }

  function configuredTool(item, originalTool = null) {
    const definition = item.definition || {};
    if (definition.mode === "builtin" && originalTool) {
      return {
        ...originalTool,
        title: item.name || originalTool.title,
        shortTitle: item.name || originalTool.shortTitle || originalTool.title,
        subtitle: item.description || definition.clinicalUse || originalTool.subtitle,
        category: definition.category || originalTool.category,
        source: `${definition.source || originalTool.source || "Motor clinico original"} · personalizacion local v${item.revision}`,
        clinicalUse: definition.clinicalUse || item.description || originalTool.clinicalUse,
        fields: configuredFields(definition, originalTool),
      };
    }
    return {
      id: originalTool?.id || `config-${item.id}`,
      title: item.name,
      shortTitle: item.name,
      subtitle: item.description || definition.clinicalUse || "Calculadora configurada localmente.",
      category: definition.category || "general",
      source: `${definition.source || "Definicion local"} · version ${item.revision}`,
      clinicalUse: definition.clinicalUse || item.description || "Herramienta configurada localmente. Verifique la definicion y la fuente antes de usar el resultado.",
      fields: configuredFields(definition),
      calculate(values) {
        const evaluated = window.CalculatorEngine.evaluate(definition, values);
        const decimals = Math.max(0, Math.min(6, Number(definition.decimals) || 0));
        const formatted = Number(evaluated.value).toFixed(decimals);
        const unit = definition.resultUnit ? ` ${definition.resultUnit}` : "";
        const contributions = definition.mode === "score" ? evaluated.contributions.filter((entry) => entry.points !== 0).slice(0, 8) : [];
        return result({
          title: `${definition.resultLabel || "Resultado"}: ${formatted}${unit}`,
          detail: evaluated.range?.label || "Resultado calculado con la definicion local activa.",
          badge: definition.mode === "score" ? "score configurable" : "calculadora configurable",
          score: 0,
          showScore: false,
          severity: evaluated.range?.severity || "info",
          metrics: [{ label: definition.resultLabel || "Resultado", value: `${formatted}${unit}` }, ...contributions.map((entry) => ({ label: entry.label, value: `${entry.points > 0 ? "+" : ""}${entry.points}` }))],
          notes: [...(definition.notes || []), definition.mode === "score" ? "Puntajes y reglas guardados en la version activa." : `Formula versionada: ${definition.expression}`],
        });
      },
    };
  }

  async function loadConfiguredCalculators() {
    if (!window.SafeExpression || !window.CalculatorEngine) return;
    try {
      const [calculatorResponse, settingsResponse] = await Promise.all([
        fetch("/api/clinical/configuration/calculator", { cache: "no-store" }),
        fetch("/api/clinical/configuration/tool-settings", { cache: "no-store" }),
      ]);
      const payload = await calculatorResponse.json();
      const settingsPayload = await settingsResponse.json();
      if (!calculatorResponse.ok) throw new Error(payload.error || "No se pudieron cargar las calculadoras configurables.");
      const disabled = new Set(settingsResponse.ok ? settingsPayload.items?.[0]?.definition?.disabledBuiltInKeys || [] : []);
      const originalBuiltIns = [...TOOLS];
      const activeDefinitions = (payload.items || []).filter((item) => item.active);
      const replacements = new Map(activeDefinitions.filter((item) => item.definition?.replacesBuiltInKey)
        .map((item) => [item.definition.replacesBuiltInKey, item]));
      const enabledBuiltIns = originalBuiltIns.filter((tool) => !disabled.has(toolConfigurationKey(tool.title))).map((tool) => {
        const replacement = replacements.get(toolConfigurationKey(tool.title));
        return replacement ? configuredTool(replacement, tool) : tool;
      });
      TOOLS.splice(0, TOOLS.length, ...enabledBuiltIns);
      const custom = activeDefinitions.filter((item) => !item.definition?.replacesBuiltInKey).map((item) => configuredTool(item));
      TOOLS.push(...custom);
      if (!TOOLS.length && originalBuiltIns.length) TOOLS.push(originalBuiltIns[0]);
      TOOLS.forEach((tool, index) => { tool.number = String(index + 1).padStart(2, "0"); });
    } catch (error) {
      console.warn("Calculadoras configurables no disponibles:", error.message);
    }
  }

  const embeddedMode = document.body.classList.contains("embedded-tools");
  let activeFilter = embeddedMode ? "general" : "all";
  let activeToolId = (TOOLS.find((tool) => activeFilter === "all" || tool.category === activeFilter) || TOOLS[0]).id;

  function syncEmbeddedSelectors() {
    const categorySelect = $("embedded-category");
    const toolSelect = $("embedded-tool");
    if (!categorySelect || !toolSelect) return;
    categorySelect.value = activeFilter;
    const available = TOOLS.filter((tool) => activeFilter === "all" || tool.category === activeFilter);
    if (!available.some((tool) => tool.id === activeToolId)) activeToolId = (available[0] || TOOLS[0]).id;
    toolSelect.innerHTML = available.map((tool) => `<option value="${tool.id}"${tool.id === activeToolId ? " selected" : ""}>${tool.shortTitle || tool.title}</option>`).join("");
  }

  function renderToolList() {
    const list = $("tool-list");
    const search = ($("tool-search").value || "").trim().toLowerCase();
    list.innerHTML = "";
    TOOLS.filter((tool) => {
      const filterOk = activeFilter === "all" || tool.category === activeFilter;
      const searchText = `${tool.title} ${tool.subtitle} ${tool.source} ${tool.category}`.toLowerCase();
      return filterOk && (!search || searchText.includes(search));
    }).forEach((tool) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = `tool-button${tool.id === activeToolId ? " active" : ""}`;
      button.dataset.toolId = tool.id;
      button.dataset.category = tool.category;
      button.innerHTML = `
        <span class="tool-number">${tool.number}</span>
        <span>
          <strong>${tool.title}</strong>
          <span>${tool.subtitle}</span>
        </span>
      `;
      button.addEventListener("click", () => {
        activeToolId = tool.id;
        render();
      });
      list.appendChild(button);
    });
  }

  function updateConditionalFields() {
    const scenario = $("scenario")?.value || "";
    document.querySelectorAll("[data-scenario]").forEach((node) => {
      node.hidden = Boolean(scenario) && node.dataset.scenario !== scenario;
    });
  }

  function renderFields(tool) {
    const fields = $("fields");
    fields.innerHTML = "";
    tool.fields.forEach((item) => {
      const wrap = document.createElement("div");
      wrap.className = `field${item.wide ? " wide" : ""}${item.type === "checkbox" ? " checkbox-field" : ""}`;
      if (item.scenario) wrap.dataset.scenario = item.scenario;
      if (item.type === "section") {
        wrap.classList.add("section-field");
        wrap.innerHTML = `
          <strong>${item.label}</strong>
          ${item.help ? `<small>${item.help}</small>` : ""}
        `;
      } else if (item.type === "checkbox") {
        wrap.innerHTML = `
          <label>
            <input id="${item.id}" type="checkbox" ${item.checked ? "checked" : ""}>
            <span>
              <strong>${item.label}</strong>
              ${item.help ? `<small>${item.help}</small>` : ""}
            </span>
          </label>
        `;
      } else if (item.type === "select") {
        const keepDefault = item.id === "scenario" || item.keepDefault === true;
        const hasEmptyOption = item.options.some((opt) => String(opt.value) === "");
        const prompt = !keepDefault && !hasEmptyOption ? '<option value="" selected disabled>Seleccioná una opción</option>' : "";
        const options = item.options.map((opt) => `<option value="${opt.value}" ${(keepDefault && String(item.value ?? "") === String(opt.value)) || (!keepDefault && String(opt.value) === "") ? "selected" : ""}>${opt.label}</option>`).join("");
        wrap.innerHTML = `
          <label>
            <span>${item.label}</span>
            <select id="${item.id}">${prompt}${options}</select>
            ${item.help ? `<small>${item.help}</small>` : ""}
          </label>
        `;
      } else if (item.type === "textarea") {
        const placeholder = item.placeholder ?? item.value ?? "";
        wrap.innerHTML = `
          <label>
            <span>${item.label}</span>
            <textarea id="${item.id}" rows="4" placeholder="${escapeAttribute(placeholder)}"></textarea>
            ${item.help ? `<small>${item.help}</small>` : ""}
          </label>
        `;
      } else {
        const placeholder = item.placeholder ?? (item.value !== undefined && item.value !== "" ? `Ej.: ${item.value}` : "");
        wrap.innerHTML = `
          <label>
            <span>${item.label}</span>
            <input id="${item.id}" type="${item.type}" value="" placeholder="${escapeAttribute(placeholder)}" min="${item.min ?? ""}" max="${item.max ?? ""}" step="${item.step ?? "any"}">
            ${item.help ? `<small>${item.help}</small>` : ""}
          </label>
        `;
      }
      fields.appendChild(wrap);
    });
    const actions = document.createElement("div");
    actions.className = "calculate-actions wide";
    actions.innerHTML = `<small>Los valores grises son ejemplos; no se usan hasta que escribas los datos reales.</small><button type="submit">Calcular</button>`;
    fields.appendChild(actions);
    updateConditionalFields();
  }

  function readValues(tool) {
    return Object.fromEntries(tool.fields.map((item) => {
      const element = $(item.id);
      if (!element) return [item.id, item.type === "checkbox" ? false : ""];
      if (item.type === "checkbox") return [item.id, element.checked];
      if (item.type === "number") return [item.id, element.value === "" ? "" : num(element.value)];
      return [item.id, element.value];
    }));
  }

  function idleResult() {
    return result({
      title: "Listo para calcular",
      detail: "Completá los datos clínicos y presioná Calcular. No se usan valores ficticios ni se actualiza el resultado mientras escribís.",
      badge: "sin resultado",
      score: 0,
      showScore: false,
      severity: "info",
      metrics: [],
      notes: [],
    });
  }

  function renderResult(output) {
    const panel = document.querySelector(".result-panel");
    panel.classList.remove("good", "warn", "bad", "info");
    panel.classList.add(output.severity || "info");
    $("result-badge").textContent = output.badge || "Resultado";
    $("result-title").textContent = output.title;
    $("result-detail").textContent = output.detail;
    const scoreBlock = $("score-block");
    if (scoreBlock) scoreBlock.hidden = output.showScore === false;
    $("score-name").textContent = output.scoreName || "Señal integrada";
    $("score-value").textContent = pct(output.score || 0);
    $("score-fill").style.width = `${clamp(output.score || 0)}%`;
    $("metric-grid").innerHTML = (output.metrics || []).map((metric) => `
      <div class="metric">
        <span>${metric.label}</span>
        <strong>${metric.value}</strong>
      </div>
    `).join("");
    $("result-notes").innerHTML = (output.notes || []).map((note) => `<div class="note">${note}</div>`).join("");
  }

  function calculateActive() {
    const tool = TOOLS.find((item) => item.id === activeToolId) || TOOLS[0];
    updateConditionalFields();
    const missing = tool.fields.filter((item) => {
      if (!["number", "text", "textarea", "select"].includes(item.type) || item.required === false || item.id === "scenario") return false;
      if ($("shim_not_evaluable")?.checked && item.id.startsWith("shim_") && item.id !== "shim_not_evaluable") return false;
      const element = $(item.id);
      const fieldNode = element?.closest(".field");
      return element && !fieldNode?.hidden && String(element.value).trim() === "";
    });
    if (missing.length) {
      renderResult(result({ title: "Faltan datos para calcular", detail: `Completá: ${missing.map((item) => item.label).join(", ")}.`, badge: "datos incompletos", score: 0, showScore: false, severity: "warn", metrics: [], notes: [] }));
      return;
    }
    const invalid = tool.fields.filter((item) => {
      const element = $(item.id);
      return element && !element.closest(".field")?.hidden && typeof element.checkValidity === "function" && !element.checkValidity();
    });
    if (invalid.length) {
      renderResult(result({ title: "Revisá los valores ingresados", detail: invalid.map((item) => item.label).join(", "), badge: "valor inválido", score: 0, showScore: false, severity: "warn", metrics: [], notes: [] }));
      return;
    }
    const values = readValues(tool);
    renderResult(tool.calculate(values, tool));
  }

  function render() {
    const tool = TOOLS.find((item) => item.id === activeToolId) || TOOLS[0];
    document.body.dataset.toolCategory = tool.category || "general";
    syncEmbeddedSelectors();
    $("tool-title").textContent = tool.title;
    $("tool-subtitle").textContent = tool.subtitle;
    $("tool-source").textContent = tool.source;
    const clinicalUse = $("tool-clinical-use");
    if (clinicalUse) {
      clinicalUse.textContent = tool.clinicalUse || "Estas herramientas ayudan a ordenar variables clínicas y estimar riesgo, elegibilidad o clasificación de forma reproducible. Usarlas como apoyo a la decisión, junto con guías vigentes y juicio clínico.";
    }
    renderToolList();
    renderFields(tool);
    $("tool-form").oninput = null;
    $("tool-form").onchange = (event) => {
      updateConditionalFields();
      if (event.target?.id === "scenario") renderResult(idleResult());
    };
    $("tool-form").onsubmit = (event) => {
      event.preventDefault();
      calculateActive();
    };
    renderResult(idleResult());
  }

  document.querySelectorAll(".filter").forEach((button) => {
    button.addEventListener("click", () => {
      document.querySelectorAll(".filter").forEach((item) => item.classList.remove("active"));
      button.classList.add("active");
      activeFilter = button.dataset.filter || "all";
      renderToolList();
    });
  });
  $("tool-search").addEventListener("input", renderToolList);
  $("embedded-category")?.addEventListener("change", (event) => {
    activeFilter = event.target.value || "general";
    const available = TOOLS.filter((tool) => activeFilter === "all" || tool.category === activeFilter);
    activeToolId = (available[0] || TOOLS[0]).id;
    render();
  });
  $("embedded-tool")?.addEventListener("change", (event) => {
    activeToolId = event.target.value || activeToolId;
    render();
  });

  loadConfiguredCalculators().finally(render);
})();
