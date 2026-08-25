(function (root, factory) {
  const createRadiotherapyTools = factory();
  if (typeof module === "object" && module.exports) module.exports = createRadiotherapyTools;
  if (root) root.createRadiotherapyTools = createRadiotherapyTools;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  function createRadiotherapyTools(helpers = {}) {
    const { number, select, section, option, result, rules } = helpers;
    const requiredHelpers = { number, select, section, option, result };
    const missingHelpers = Object.entries(requiredHelpers)
      .filter(([, value]) => typeof value !== "function")
      .map(([name]) => name);
    if (missingHelpers.length) throw new TypeError(`Faltan helpers de interfaz: ${missingHelpers.join(", ")}.`);
    if (!rules || typeof rules !== "object") throw new TypeError("Falta helpers.rules (RadiotherapyRules).");

    const requiredRules = ["dosePerFractionForTarget", "fractionsForTarget", "simultaneousFractionation"];
    const missingRules = requiredRules.filter((name) => typeof rules[name] !== "function");
    if (missingRules.length) throw new TypeError(`Faltan reglas de radioterapia: ${missingRules.join(", ")}.`);

    const targetOptions = [
      option("eqd2", "EQD2 objetivo"),
      option("bed", "BED objetivo"),
    ];
    const simultaneousTargetOptions = [
      option("physical", "Dosis física total"),
      option("eqd2", "EQD2"),
    ];
    const resolutionOptions = [
      option("0.01", "0,01 Gy"),
      option("0.05", "0,05 Gy"),
      option("0.1", "0,10 Gy"),
    ];
    const fmt = (value, digits = 2) => Number(value).toFixed(digits).replace(".", ",");
    const signed = (value, digits = 2) => `${Number(value) >= 0 ? "+" : "−"}${fmt(Math.abs(value), digits)}`;
    const metricName = (targetType) => targetType === "bed" ? "BED" : targetType === "eqd2" ? "EQD2" : "dosis física";
    const gyAlphaBeta = (alphaBeta) => `Gy (α/β ${fmt(alphaBeta, 1)})`;

    function invalidRuleResult(label, evaluation) {
      return result({
        title: "Datos incompletos o incompatibles",
        detail: evaluation?.missing?.length
          ? `Revisar: ${evaluation.missing.join(", ")}.`
          : evaluation?.message || "No fue posible completar el cálculo.",
        badge: label,
        score: 0,
        showScore: false,
        severity: "warn",
        metrics: [],
        notes: ["Corregí las variables antes de interpretar el resultado."],
      });
    }

    function baseResult(config) {
      return result({ score: 0, showScore: false, severity: "info", metrics: [], notes: [], ...config });
    }

    function lqLimitations(highDosePerFraction = false) {
      return [
        highDosePerFraction
          ? "Dosis por fracción >5 Gy: el modelo LQ sigue siendo una estimación y su extrapolación es especialmente incierta en hipofraccionamiento extremo."
          : "El modelo LQ es una aproximación; la incertidumbre aumenta al alejarse del fraccionamiento convencional.",
        "No incorpora tiempo total, repoblación, reparación incompleta, heterogeneidad de dosis, recuperación tisular ni reirradiación.",
        "Usar la dosis realmente recibida por el tejido analizado. El resultado no constituye una prescripción ni un límite automático de órgano a riesgo.",
      ];
    }

    function integerCandidateTable(calculated) {
      const label = metricName(calculated.targetType);
      return `
        <div class="rt-candidate-table-wrap" role="region" aria-label="Comparación de fracciones enteras" tabindex="0">
          <table class="rt-candidate-table">
            <thead><tr><th>Fracciones</th><th>Dosis total</th><th>BED</th><th>EQD2</th><th>Δ ${label}</th></tr></thead>
            <tbody>
              ${calculated.candidates.map((candidate) => `
                <tr>
                  <td><strong>${candidate.fractions}</strong></td>
                  <td>${fmt(candidate.totalDose)} Gy</td>
                  <td>${fmt(candidate.bed)} ${gyAlphaBeta(calculated.alphaBeta)}</td>
                  <td>${fmt(candidate.eqd2)} ${gyAlphaBeta(calculated.alphaBeta)}</td>
                  <td class="${Math.abs(candidate.deviation) < 0.005 ? "rt-delta--exact" : ""}">${signed(candidate.deviation)}</td>
                </tr>
              `).join("")}
            </tbody>
          </table>
        </div>`;
    }

    function simultaneousCandidateTable(calculated, volumeLabels) {
      const visible = calculated.candidates.slice(0, 12);
      const label = calculated.targetType === "physical" ? "Dosis física" : "EQD2";
      const volumeTones = volumeLabels.length === 2 ? ["high", "low"] : ["high", "medium", "low"];
      return `
        <div class="rt-candidate-table-wrap" role="region" aria-label="Esquemas simultáneos candidatos" tabindex="0">
          <table class="rt-candidate-table rt-candidate-table--sib">
            <thead>
              <tr>
                <th>Fracciones</th>
                ${volumeLabels.map((volumeLabel, index) => `<th><span class="rt-volume-badge rt-volume-badge--${volumeTones[index]}">${volumeLabel}</span></th>`).join("")}
              </tr>
            </thead>
            <tbody>
              ${visible.map((candidate) => `
                <tr>
                  <td><strong>${candidate.fractions}</strong><small>fracciones comunes</small></td>
                  ${candidate.volumes.map((volume) => `
                    <td>
                      <strong>${fmt(volume.dosePerFraction)} Gy/fracción</strong>
                      <small>D ${fmt(volume.totalDose)} Gy · EQD2 ${fmt(volume.eqd2)} · Δ ${signed(volume.deviation)}</small>
                    </td>
                  `).join("")}
                </tr>
              `).join("")}
            </tbody>
          </table>
        </div>
        <div class="rt-table-caption">${label} objetivo · se muestran ${visible.length} de ${calculated.candidates.length} candidatos.</div>`;
    }

    function simultaneousTool(volumeCount) {
      const prefix = `rt_sib${volumeCount}`;
      const volumeLabels = volumeCount === 2 ? ["Volumen alto", "Volumen bajo"] : ["Volumen alto", "Volumen medio", "Volumen bajo"];
      const targetFields = volumeLabels.flatMap((label, index) => [
        number(`${prefix}_target_${index + 1}`, `Objetivo · ${label}`, {
          value: volumeCount === 2 ? [70, 56][index] : [70, 63, 56][index],
          min: 0.01,
          step: 0.01,
          help: "Se interpreta como Gy físicos o EQD2 según la magnitud seleccionada.",
        }),
        number(`${prefix}_tolerance_${index + 1}`, `Tolerancia · ${label} (Gy)`, {
          value: 0.1,
          min: 0,
          step: 0.01,
        }),
      ]);
      return {
        id: `rt-simultaneous-${volumeCount}-volumes`,
        number: `RT${volumeCount + 2}`,
        title: `Fraccionamiento simultáneo · ${volumeCount} volúmenes`,
        shortTitle: `SIB · ${volumeCount} volúmenes`,
        category: "radioterapia",
        subtitle: `Esquemas con un número común de fracciones para ${volumeCount} niveles de dosis.`,
        source: "Modelo LQ · Pangea",
        clinicalUse: `Explora esquemas matemáticos de boost simultáneo para ${volumeCount} volúmenes, con objetivos en dosis física o EQD2. Reemplaza la búsqueda por fuerza bruta original por una enumeración reproducible de fracciones enteras.`,
        fields: [
          section(`${prefix}_scope`, "Definí la magnitud y los límites", {
            help: "Cada fila candidata usa el mismo número de fracciones en todos los volúmenes. La tabla se ordena por menor desviación numérica, no por preferencia clínica.",
          }),
          select("scenario", "Magnitud de los objetivos", simultaneousTargetOptions, {
            value: "physical",
            wide: true,
            help: "Dosis física reproduce el planificador directo; EQD2 reproduce su variante radiobiológica.",
          }),
          ...targetFields,
          section(`${prefix}_delivery`, "Resolución y rango de entrega", {
            help: "La resolución redondea cada dosis por fracción antes de comprobar la tolerancia.",
          }),
          number(`${prefix}_alpha_beta`, "Relación α/β (Gy)", { value: 10, min: 0.1, step: 0.1 }),
          number(`${prefix}_min_dose`, "Dosis mínima por fracción (Gy)", { value: 1.5, min: 0.01, step: 0.01 }),
          number(`${prefix}_max_dose`, "Dosis máxima por fracción (Gy)", { value: 3, min: 0.01, step: 0.01 }),
          select(`${prefix}_resolution`, "Resolución de dosis por fracción", resolutionOptions, { value: "0.01", keepDefault: true }),
        ],
        calculate(values) {
          const calculated = rules.simultaneousFractionation({
            targetType: values.scenario,
            targets: volumeLabels.map((_, index) => values[`${prefix}_target_${index + 1}`]),
            tolerances: volumeLabels.map((_, index) => values[`${prefix}_tolerance_${index + 1}`]),
            alphaBeta: values[`${prefix}_alpha_beta`],
            minDosePerFraction: values[`${prefix}_min_dose`],
            maxDosePerFraction: values[`${prefix}_max_dose`],
            resolution: values[`${prefix}_resolution`],
          });
          if (!calculated.valid) return invalidRuleResult(`SIB ${volumeCount} volúmenes`, calculated);
          if (!calculated.candidates.length) {
            return baseResult({
              title: "No hay esquemas dentro de esos límites",
              detail: "La combinación de objetivos, tolerancias, resolución y rango de dosis por fracción no produjo candidatos.",
              badge: `SIB ${volumeCount} volúmenes`,
              severity: "warn",
              metrics: [
                { label: "Fracciones exploradas", value: `1–${calculated.searchedFractions}` },
                { label: "Resolución", value: `${fmt(calculated.resolution)} Gy` },
                { label: "α/β", value: `${fmt(calculated.alphaBeta, 1)} Gy` },
              ],
              notes: [
                "Revisá que los objetivos correspondan a la magnitud seleccionada y que la dosis mínima no supere la máxima.",
                "Si corresponde clínicamente, podés ampliar la tolerancia o el rango de dosis por fracción y volver a calcular.",
                ...lqLimitations(false),
              ],
            });
          }
          const fractionValues = calculated.candidates.map((candidate) => candidate.fractions);
          return baseResult({
            title: calculated.candidates.length === 1
              ? "1 esquema matemático compatible"
              : `${calculated.candidates.length} esquemas matemáticos compatibles`,
            detail: `${metricName(calculated.targetType).replace(/^./, (letter) => letter.toUpperCase())} como objetivo, resolución ${fmt(calculated.resolution)} Gy y α/β ${fmt(calculated.alphaBeta, 1)} Gy.`,
            badge: `SIB ${volumeCount} volúmenes`,
            severity: calculated.highDoseCandidates ? "warn" : "info",
            metrics: [
              { label: "Candidatos", value: calculated.candidates.length },
              { label: "Rango de fracciones", value: `${Math.min(...fractionValues)}–${Math.max(...fractionValues)}` },
              { label: "Resolución", value: `${fmt(calculated.resolution)} Gy` },
              { label: "α/β", value: `${fmt(calculated.alphaBeta, 1)} Gy` },
            ],
            notes: [
              simultaneousCandidateTable(calculated, volumeLabels),
              "Los candidatos están ordenados por precisión matemática. La selección final requiere objetivos clínicos, DVH, restricciones de órganos a riesgo, técnica y control de calidad.",
              ...lqLimitations(calculated.highDoseCandidates),
            ],
          });
        },
      };
    }

    const tools = [
      {
        id: "rt-dose-per-fraction-target",
        number: "RT2",
        title: "Dosis por fracción desde BED o EQD2",
        shortTitle: "Dosis/fracción · BED o EQD2",
        category: "radioterapia",
        subtitle: "Resuelve la dosis por fracción para un efecto biológico objetivo.",
        source: "Modelo LQ · Pangea",
        clinicalUse: "Calcula la raíz positiva de la ecuación LQ cuando se conocen BED o EQD2 objetivo, número entero de fracciones y α/β.",
        fields: [
          section("rt_dpf_scope", "Conversión inversa del modelo LQ", {
            help: "Elegí BED o EQD2; la herramienta informa también dosis física total y la otra magnitud biológica.",
          }),
          select("scenario", "Magnitud objetivo", targetOptions, { value: "eqd2", wide: true }),
          number("rt_dpf_target", "Valor objetivo", { value: 60, min: 0.01, step: 0.01 }),
          number("rt_dpf_fractions", "Número de fracciones", { value: 30, min: 1, step: 1 }),
          number("rt_dpf_alpha_beta", "Relación α/β (Gy)", { value: 10, min: 0.1, step: 0.1 }),
        ],
        calculate(values) {
          const calculated = rules.dosePerFractionForTarget({
            targetType: values.scenario,
            targetValue: values.rt_dpf_target,
            fractions: values.rt_dpf_fractions,
            alphaBeta: values.rt_dpf_alpha_beta,
          });
          if (!calculated.valid) return invalidRuleResult("Conversión LQ", calculated);
          return baseResult({
            title: `${fmt(calculated.dosePerFraction, 3)} Gy por fracción`,
            detail: `${calculated.fractions} fracciones entregan ${fmt(calculated.totalDose)} Gy físicos.`,
            badge: `${metricName(calculated.targetType)} objetivo`,
            severity: calculated.highDosePerFraction ? "warn" : "info",
            metrics: [
              { label: "Dosis por fracción", value: `${fmt(calculated.dosePerFraction, 3)} Gy` },
              { label: "Dosis total", value: `${fmt(calculated.totalDose)} Gy` },
              { label: "BED", value: `${fmt(calculated.bed)} ${gyAlphaBeta(calculated.alphaBeta)}` },
              { label: "EQD2", value: `${fmt(calculated.eqd2)} ${gyAlphaBeta(calculated.alphaBeta)}` },
            ],
            notes: lqLimitations(calculated.highDosePerFraction),
          });
        },
      },
      {
        id: "rt-fractions-target",
        number: "RT3",
        title: "Número de fracciones desde BED o EQD2",
        shortTitle: "N.º de fracciones · BED o EQD2",
        category: "radioterapia",
        subtitle: "Muestra el resultado teórico y recalcula los enteros vecinos.",
        source: "Modelo LQ · Pangea",
        clinicalUse: "Obtiene el número teórico de fracciones para una dosis por fracción dada. Si no es entero, compara ambos enteros adyacentes sin redondear silenciosamente.",
        fields: [
          section("rt_n_scope", "Fracciones administrables", {
            help: "Un resultado decimal es sólo algebraico. La tabla recalcula BED y EQD2 para los números enteros inferior y superior.",
          }),
          select("scenario", "Magnitud objetivo", targetOptions, { value: "eqd2", wide: true }),
          number("rt_n_target", "Valor objetivo", { value: 60, min: 0.01, step: 0.01 }),
          number("rt_n_dose", "Dosis por fracción (Gy)", { value: 3, min: 0.01, step: 0.01 }),
          number("rt_n_alpha_beta", "Relación α/β (Gy)", { value: 10, min: 0.1, step: 0.1 }),
        ],
        calculate(values) {
          const calculated = rules.fractionsForTarget({
            targetType: values.scenario,
            targetValue: values.rt_n_target,
            dosePerFraction: values.rt_n_dose,
            alphaBeta: values.rt_n_alpha_beta,
          });
          if (!calculated.valid) return invalidRuleResult("Conversión LQ", calculated);
          return baseResult({
            title: calculated.isInteger
              ? `${calculated.candidates[0].fractions} fracciones`
              : `${fmt(calculated.theoreticalFractions, 3)} fracciones teóricas`,
            detail: calculated.isInteger
              ? "El resultado algebraico ya es un número entero."
              : "Las fracciones deben ser enteras; compará el efecto de ambos esquemas adyacentes.",
            badge: `${metricName(calculated.targetType)} objetivo`,
            severity: calculated.highDosePerFraction || !calculated.isInteger ? "warn" : "info",
            metrics: [
              { label: "Fracciones teóricas", value: fmt(calculated.theoreticalFractions, 3) },
              { label: "Dosis por fracción", value: `${fmt(calculated.dosePerFraction)} Gy` },
              { label: "Objetivo", value: `${fmt(calculated.targetValue)} ${gyAlphaBeta(calculated.alphaBeta)}` },
              { label: "α/β", value: `${fmt(calculated.alphaBeta, 1)} Gy` },
            ],
            notes: [
              integerCandidateTable(calculated),
              "La tabla compara los enteros matemáticamente adyacentes; no señala uno como preferido.",
              ...lqLimitations(calculated.highDosePerFraction),
            ],
          });
        },
      },
      simultaneousTool(2),
      simultaneousTool(3),
    ];

    if (tools.length !== 4) throw new Error(`createRadiotherapyTools debe devolver 4 entradas y generó ${tools.length}.`);
    return tools;
  }

  return createRadiotherapyTools;
});
