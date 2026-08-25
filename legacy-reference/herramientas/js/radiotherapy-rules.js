(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.RadiotherapyRules = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const EPSILON = 1e-9;
  const MAX_FRACTIONS = 200;
  const ALLOWED_TARGETS = new Set(["bed", "eqd2", "physical"]);
  const ALLOWED_RESOLUTIONS = new Set([0.01, 0.05, 0.1]);

  const finite = (value) => Number.isFinite(Number(value));
  const numeric = (value) => Number(value);
  const invalid = (missing = [], message = "Faltan datos válidos para calcular.") => ({
    valid: false,
    missing,
    message,
  });

  function scheduleMetrics(fractions, dosePerFraction, alphaBeta) {
    const totalDose = fractions * dosePerFraction;
    const bed = totalDose * (1 + dosePerFraction / alphaBeta);
    const eqd2 = bed / (1 + 2 / alphaBeta);
    return { fractions, dosePerFraction, totalDose, bed, eqd2 };
  }

  function targetMetric(metrics, targetType) {
    if (targetType === "bed") return metrics.bed;
    if (targetType === "eqd2") return metrics.eqd2;
    return metrics.totalDose;
  }

  function validateTargetInput(input, { requireFractions = false, requireDose = false } = {}) {
    const targetType = String(input.targetType || "").toLowerCase();
    const targetValue = numeric(input.targetValue);
    const alphaBeta = numeric(input.alphaBeta);
    const fractions = numeric(input.fractions);
    const dosePerFraction = numeric(input.dosePerFraction);
    const missing = [];

    if (!["bed", "eqd2"].includes(targetType)) missing.push("magnitud objetivo BED o EQD2");
    if (!finite(input.targetValue) || targetValue <= 0) missing.push("valor objetivo mayor que cero");
    if (!finite(input.alphaBeta) || alphaBeta <= 0) missing.push("relación α/β mayor que cero");
    if (requireFractions && (!finite(input.fractions) || fractions <= 0 || !Number.isInteger(fractions))) {
      missing.push("número entero de fracciones");
    }
    if (requireDose && (!finite(input.dosePerFraction) || dosePerFraction <= 0)) {
      missing.push("dosis por fracción mayor que cero");
    }

    return missing.length
      ? { error: invalid(missing) }
      : { targetType, targetValue, alphaBeta, fractions, dosePerFraction };
  }

  function dosePerFractionForTarget(input = {}) {
    const values = validateTargetInput(input, { requireFractions: true });
    if (values.error) return values.error;
    const { targetType, targetValue, alphaBeta, fractions } = values;

    const discriminant = targetType === "bed"
      ? alphaBeta * alphaBeta + (4 * alphaBeta * targetValue) / fractions
      : alphaBeta * alphaBeta + (4 * targetValue * (2 + alphaBeta)) / fractions;
    if (!Number.isFinite(discriminant) || discriminant <= 0) {
      return invalid(["combinación con solución positiva"], "No existe una solución física positiva para esos datos.");
    }

    const dosePerFraction = (-alphaBeta + Math.sqrt(discriminant)) / 2;
    if (!Number.isFinite(dosePerFraction) || dosePerFraction <= 0) {
      return invalid(["combinación con solución positiva"], "No existe una dosis por fracción positiva para esos datos.");
    }

    const metrics = scheduleMetrics(fractions, dosePerFraction, alphaBeta);
    return {
      valid: true,
      targetType,
      targetValue,
      alphaBeta,
      ...metrics,
      achievedTarget: targetMetric(metrics, targetType),
      highDosePerFraction: dosePerFraction > 5,
    };
  }

  function fractionsForTarget(input = {}) {
    const values = validateTargetInput(input, { requireDose: true });
    if (values.error) return values.error;
    const { targetType, targetValue, alphaBeta, dosePerFraction } = values;

    const effectPerFraction = targetType === "bed"
      ? dosePerFraction * (1 + dosePerFraction / alphaBeta)
      : dosePerFraction * (dosePerFraction + alphaBeta) / (2 + alphaBeta);
    const theoreticalFractions = targetValue / effectPerFraction;
    if (!Number.isFinite(theoreticalFractions) || theoreticalFractions <= 0) {
      return invalid(["combinación con solución positiva"], "No existe un número de fracciones positivo para esos datos.");
    }

    const roundedInteger = Math.round(theoreticalFractions);
    const isInteger = Math.abs(theoreticalFractions - roundedInteger) < EPSILON;
    const neighborValues = isInteger
      ? [Math.max(1, roundedInteger)]
      : [Math.floor(theoreticalFractions), Math.ceil(theoreticalFractions)].filter((value) => value >= 1);
    const candidates = [...new Set(neighborValues)].map((fractions) => {
      const metrics = scheduleMetrics(fractions, dosePerFraction, alphaBeta);
      const achievedTarget = targetMetric(metrics, targetType);
      return {
        ...metrics,
        achievedTarget,
        deviation: achievedTarget - targetValue,
      };
    });

    return {
      valid: true,
      targetType,
      targetValue,
      alphaBeta,
      dosePerFraction,
      theoreticalFractions,
      isInteger,
      candidates,
      highDosePerFraction: dosePerFraction > 5,
    };
  }

  function roundedToResolution(value, resolution) {
    const rounded = Math.round(value / resolution) * resolution;
    return Number(rounded.toFixed(6));
  }

  function idealDosePerFraction(targetType, targetValue, fractions, alphaBeta) {
    if (targetType === "physical") return targetValue / fractions;
    const solved = dosePerFractionForTarget({ targetType, targetValue, fractions, alphaBeta });
    return solved.valid ? solved.dosePerFraction : Number.NaN;
  }

  function simultaneousFractionation(input = {}) {
    const targetType = String(input.targetType || "").toLowerCase();
    const rawTargets = Array.isArray(input.targets) ? input.targets : [];
    const rawTolerances = Array.isArray(input.tolerances) ? input.tolerances : [];
    const alphaBeta = numeric(input.alphaBeta);
    const minDosePerFraction = numeric(input.minDosePerFraction);
    const maxDosePerFraction = numeric(input.maxDosePerFraction);
    const resolution = numeric(input.resolution);
    const missing = [];

    if (!ALLOWED_TARGETS.has(targetType)) missing.push("tipo de objetivo");
    if (![2, 3].includes(rawTargets.length)) missing.push("dos o tres volúmenes");
    if (rawTolerances.length !== rawTargets.length) missing.push("tolerancia para cada volumen");
    const targets = rawTargets.map(numeric);
    const tolerances = rawTolerances.map(numeric);
    targets.forEach((value, index) => {
      if (!Number.isFinite(value) || value <= 0) missing.push(`objetivo del volumen ${index + 1}`);
    });
    tolerances.forEach((value, index) => {
      if (!Number.isFinite(value) || value < 0) missing.push(`tolerancia del volumen ${index + 1}`);
    });
    if (!finite(input.alphaBeta) || alphaBeta <= 0) missing.push("relación α/β mayor que cero");
    if (!finite(input.minDosePerFraction) || minDosePerFraction <= 0) missing.push("dosis mínima por fracción");
    if (!finite(input.maxDosePerFraction) || maxDosePerFraction <= 0) missing.push("dosis máxima por fracción");
    if (Number.isFinite(minDosePerFraction) && Number.isFinite(maxDosePerFraction) && minDosePerFraction > maxDosePerFraction) {
      missing.push("rango de dosis por fracción ordenado");
    }
    if (!ALLOWED_RESOLUTIONS.has(resolution)) missing.push("resolución de 0,01, 0,05 o 0,10 Gy");
    if (missing.length) return invalid([...new Set(missing)]);

    const candidates = [];
    for (let fractions = 1; fractions <= MAX_FRACTIONS; fractions += 1) {
      const volumes = targets.map((target, index) => {
        const idealDose = idealDosePerFraction(targetType, target, fractions, alphaBeta);
        const dosePerFraction = roundedToResolution(idealDose, resolution);
        const metrics = scheduleMetrics(fractions, dosePerFraction, alphaBeta);
        const achieved = targetMetric(metrics, targetType);
        return {
          index: index + 1,
          target,
          tolerance: tolerances[index],
          idealDosePerFraction: idealDose,
          ...metrics,
          achieved,
          deviation: achieved - target,
          absoluteDeviation: Math.abs(achieved - target),
        };
      });

      const dosesInRange = volumes.every((volume) => Number.isFinite(volume.dosePerFraction)
        && volume.dosePerFraction + EPSILON >= minDosePerFraction
        && volume.dosePerFraction - EPSILON <= maxDosePerFraction);
      const withinTolerance = volumes.every((volume) => volume.absoluteDeviation <= volume.tolerance + EPSILON);
      if (!dosesInRange || !withinTolerance) continue;

      const score = volumes.reduce((sum, volume) => {
        const denominator = volume.tolerance > 0 ? volume.tolerance : resolution;
        return sum + volume.absoluteDeviation / denominator;
      }, 0);
      candidates.push({
        fractions,
        volumes,
        score,
        maxDeviation: Math.max(...volumes.map((volume) => volume.absoluteDeviation)),
      });
    }

    candidates.sort((left, right) => left.score - right.score
      || left.maxDeviation - right.maxDeviation
      || left.fractions - right.fractions);

    return {
      valid: true,
      targetType,
      alphaBeta,
      minDosePerFraction,
      maxDosePerFraction,
      resolution,
      candidates,
      searchedFractions: MAX_FRACTIONS,
      highDoseCandidates: candidates.some((candidate) => candidate.volumes.some((volume) => volume.dosePerFraction > 5)),
    };
  }

  return {
    scheduleMetrics,
    dosePerFractionForTarget,
    fractionsForTarget,
    simultaneousFractionation,
  };
});
