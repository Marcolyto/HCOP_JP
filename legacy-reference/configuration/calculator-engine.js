(function (root) {
  "use strict";

  const OPERATORS = new Set(["lt", "lte", "eq", "gte", "gt", "between"]);

  function finiteNumber(value, fallback = 0) {
    const number = Number(value);
    return Number.isFinite(number) ? number : fallback;
  }

  function isChecked(value) {
    return value === true || value === 1 || value === "1" || value === "true" || value === "on";
  }

  function ruleMatches(rule, rawValue) {
    const value = Number(rawValue);
    if (!Number.isFinite(value)) return false;
    const operator = OPERATORS.has(rule?.operator) ? rule.operator : "eq";
    const first = Number(rule?.value);
    const second = Number(rule?.max);
    if (!Number.isFinite(first)) return false;
    if (operator === "lt") return value < first;
    if (operator === "lte") return value <= first;
    if (operator === "gte") return value >= first;
    if (operator === "gt") return value > first;
    if (operator === "between") return Number.isFinite(second) && value >= first && value <= second;
    return value === first;
  }

  function matchingRange(ranges, value) {
    return (Array.isArray(ranges) ? ranges : []).find((range) =>
      (range.min == null || value >= Number(range.min)) &&
      (range.max == null || value <= Number(range.max))) || null;
  }

  function evaluateScore(definition, values) {
    let value = finiteNumber(definition.basePoints, 0);
    const contributions = [];
    for (const field of definition.fields || []) {
      if (!field || field.type === "section") continue;
      let points = 0;
      let detail = "Sin puntaje";
      const rawValue = values?.[field.key];
      if (field.type === "checkbox") {
        if (isChecked(rawValue)) points = finiteNumber(field.checkedPoints, 0);
        detail = isChecked(rawValue) ? "Marcado" : "No marcado";
      } else if (field.type === "select") {
        const selected = (field.options || []).find((option) => String(option.value) === String(rawValue));
        points = finiteNumber(selected?.points, 0);
        detail = selected?.label || "Sin seleccion";
      } else if (field.type === "number") {
        const rule = (field.scoreRules || []).find((candidate) => ruleMatches(candidate, rawValue));
        points = finiteNumber(rule?.points, 0);
        detail = rule?.label || (rule ? "Regla aplicada" : "Sin regla coincidente");
      }
      value += points;
      contributions.push({ key: field.key, label: field.label, points, detail, rawValue });
    }
    return { value, range: matchingRange(definition.ranges, value), contributions };
  }

  function evaluate(definition, values = {}) {
    const mode = definition?.mode || "formula";
    if (mode === "builtin") throw new Error("El motor clinico original se ejecuta desde Herramientas.");
    if (mode === "score") return evaluateScore(definition, values);
    if (!root.SafeExpression?.evaluate) throw new Error("El motor de formulas no esta disponible.");
    const value = root.SafeExpression.evaluate(definition?.expression || "", values);
    return { value, range: matchingRange(definition?.ranges, value), contributions: [] };
  }

  root.CalculatorEngine = Object.freeze({ evaluate, evaluateScore, matchingRange, ruleMatches });
})(typeof window !== "undefined" ? window : globalThis);
