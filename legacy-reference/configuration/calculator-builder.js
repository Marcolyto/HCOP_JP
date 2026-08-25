(function () {
  "use strict";

  function notifyCalculatorConfigurationUpdated() {
    const updatedAt = String(Date.now());
    localStorage.setItem("hcop-configuration-updated", updatedAt);
    localStorage.setItem("hcop-calculator-configuration-updated", updatedAt);
  }

  function renderCalculatorListVisual() {
    const query = $("#calculatorConfigSearch").value.trim().toLowerCase();
    const showInactive = $("#showInactiveCalculators").checked;
    const rows = state.calculators.filter((item) => (showInactive || item.active) && (!query || `${item.name} ${item.description} ${item.definition?.category}`.toLowerCase().includes(query)));
    $("#calculatorConfigList").innerHTML = rows.map((item) => {
      const mode = item.definition?.mode === "score" ? "Score" : item.definition?.mode === "builtin" ? "Motor original" : "Fórmula";
      return catalogItem(item, item.id === state.selectedCalculator?.id, `${mode} · ${item.definition?.category || "General"} · v${item.revision}`);
    }).join("") || `<div class="catalog-empty">Todavía no hay personalizaciones ni herramientas nuevas.</div>`;
  }

  function resetEditor() {
    state.selectedCalculator = null; state.selectedBuiltInKey = "";
    $("#calculatorEmpty").hidden = true; $("#calculatorConfigForm").hidden = false; $("#calculatorConfigForm").reset();
    $("#calculatorConfigId").value = ""; $("#calculatorConfigRevision").value = ""; $("#calculatorBaseToolKey").value = "";
    $("#calculatorActive").checked = true; $("#calculatorDecimals").value = "2"; $("#calculatorBasePoints").value = "0";
    $("#calculatorResultLabel").value = "Resultado"; $("#calculatorVariables").innerHTML = ""; $("#calculatorRangeList").innerHTML = "";
    $("#calculatorExpression").readOnly = true; $("#archiveCalculatorBtn").hidden = true;
  }

  function newCalculatorVisual(mode = "formula") {
    resetEditor(); $("#calculatorMode").value = mode;
    $("#calculatorEditorTitle").textContent = mode === "score" ? "Nuevo score" : "Nueva calculadora";
    $("#calculatorRevisionLabel").textContent = "Sin guardar";
    if (mode === "score") {
      $("#calculatorName").value = "Nuevo score"; $("#calculatorResultLabel").value = "Puntaje total";
      $("#calculatorResultUnit").value = "puntos"; $("#calculatorDecimals").value = "0";
      addVariable({ label: "Criterio", key: "criterio", type: "select", required: true, options: [{ value: "no", label: "No", points: 0 }, { value: "si", label: "Sí", points: 1 }] });
    } else {
      $("#calculatorName").value = "Nueva calculadora";
      addVariable({ label: "Peso", key: "peso", type: "number", unit: "kg", required: true });
      addVariable({ label: "Altura", key: "altura", type: "number", unit: "cm", required: true });
      $("#calculatorExpression").value = "sqrt(peso * altura / 3600)";
    }
    syncMode(); renderCalculatorListVisual(); renderBuiltInTools();
  }

  function editBuiltInToolVisual(tool) {
    if (!tool) return;
    const override = state.calculators.find((item) => item.definition?.replacesBuiltInKey === tool.key);
    if (override) return editCalculatorVisual(override);
    editCalculatorVisual({
      id: "", revision: 0, active: !state.disabledBuiltInKeys.includes(tool.key), name: tool.title,
      description: tool.subtitle || tool.clinicalUse || "",
      definition: { mode: "builtin", replacesBuiltInKey: tool.key, category: tool.category || "general", source: tool.source || "Motor clínico original", clinicalUse: tool.clinicalUse || tool.subtitle || "", fields: tool.fields || [], resultLabel: "Resultado", decimals: 2, ranges: [] },
    });
  }

  function editCalculatorVisual(item) {
    resetEditor();
    const definition = item.definition || {}, mode = definition.mode || "formula", baseKey = definition.replacesBuiltInKey || "";
    state.selectedCalculator = item.id ? item : null; state.selectedBuiltInKey = baseKey;
    $("#calculatorConfigId").value = item.id || ""; $("#calculatorConfigRevision").value = item.revision || ""; $("#calculatorBaseToolKey").value = baseKey;
    $("#calculatorBuiltinModeOption").hidden = !baseKey; $("#calculatorMode").value = mode; $("#calculatorName").value = item.name || "";
    $("#calculatorDescription").value = item.description || definition.clinicalUse || "";
    $("#calculatorCategory").value = [...$("#calculatorCategory").options].some((option) => option.value === definition.category) ? definition.category : "general";
    $("#calculatorSource").value = definition.source || ""; $("#calculatorActive").checked = item.active !== false;
    $("#calculatorExpression").value = definition.expression || ""; $("#calculatorBasePoints").value = definition.basePoints ?? 0;
    $("#calculatorResultLabel").value = definition.resultLabel || "Resultado"; $("#calculatorResultUnit").value = definition.resultUnit || ""; $("#calculatorDecimals").value = definition.decimals ?? 2;
    $("#calculatorEditorTitle").textContent = item.name; $("#calculatorRevisionLabel").textContent = item.id ? `Versión ${item.revision} · ${item.active ? "activa" : "desactivada"}` : "Motor original · personalización sin guardar";
    $("#archiveCalculatorBtn").hidden = !item.id; $("#archiveCalculatorBtn").innerHTML = `<i data-lucide="${item.active ? "archive" : "rotate-ccw"}"></i>${item.active ? "Desactivar" : "Reactivar"}`;
    (definition.fields || []).forEach(addVariable); (definition.ranges || []).forEach(addRange);
    syncMode(); renderCalculatorListVisual(); renderBuiltInTools(); icons($("#calculatorConfigForm"));
  }

  function addOption(card, option = {}) {
    const row = $("#calculatorOptionTemplate").content.firstElementChild.cloneNode(true);
    $(".option-label", row).value = option.label ?? "Nueva opción"; $(".option-value", row).value = option.value ?? slug(option.label || "opcion");
    $(".option-points", row).value = Number.isFinite(Number(option.points)) ? Number(option.points) : Number.isFinite(Number(option.value)) ? Number(option.value) : 0;
    $$('input,select', row).forEach((control) => control.addEventListener("input", renderPreview));
    $(".remove-option", row).addEventListener("click", () => { row.remove(); renderPreview(); });
    $(".builder-option-list", card).append(row); icons(row);
  }

  function addRule(card, rule = {}) {
    const row = $("#calculatorRuleTemplate").content.firstElementChild.cloneNode(true);
    $(".rule-operator", row).value = rule.operator || "eq"; $(".rule-value", row).value = rule.value ?? ""; $(".rule-max", row).value = rule.max ?? "";
    $(".rule-points", row).value = rule.points ?? 0; $(".rule-label", row).value = rule.label || "";
    const sync = () => { $(".rule-max", row).hidden = $(".rule-operator", row).value !== "between"; renderPreview(); };
    $$('input,select', row).forEach((control) => control.addEventListener("input", sync));
    $(".remove-rule", row).addEventListener("click", () => { row.remove(); renderPreview(); });
    $(".builder-score-rule-list", card).append(row); sync(); icons(row);
  }

  function addRange(range = {}) {
    const row = $("#calculatorRangeTemplate").content.firstElementChild.cloneNode(true);
    $(".range-min", row).value = range.min ?? ""; $(".range-max", row).value = range.max ?? ""; $(".range-label", row).value = range.label || ""; $(".range-severity", row).value = range.severity || "info";
    $$('input,select', row).forEach((control) => control.addEventListener("input", renderPreview));
    $(".remove-range", row).addEventListener("click", () => { row.remove(); renderPreview(); });
    $("#calculatorRangeList").append(row); icons(row);
  }

  function addVariable(field = {}) {
    const card = $("#calculatorVariableTemplate").content.firstElementChild.cloneNode(true);
    card.dataset.type = field.type || "number"; card.dataset.defaultValue = JSON.stringify(field.value ?? null);
    $(".builder-label", card).value = field.label || "Nueva variable"; $(".builder-key", card).value = field.key || slug(field.label || "variable");
    $(".builder-type", card).value = field.type || "number"; $(".builder-unit", card).value = field.unit || ""; $(".builder-min", card).value = field.min ?? ""; $(".builder-max", card).value = field.max ?? "";
    $(".builder-help", card).value = field.help || field.placeholder || ""; $(".builder-required", card).checked = field.type !== "section" && field.required !== false; $(".builder-checked-points", card).value = field.checkedPoints ?? field.weight ?? 0;
    (field.options || []).forEach((option) => addOption(card, option)); (field.scoreRules || []).forEach((rule) => addRule(card, rule));
    $(".add-option", card).addEventListener("click", () => { addOption(card); syncCard(card); renderPreview(); });
    $(".add-score-rule", card).addEventListener("click", () => { addRule(card); syncCard(card); renderPreview(); });
    $("#calculatorVariables").append(card); wireBuilderCard(card, () => { syncCard(card); renderPreview(); }); syncCard(card); icons(card); renderPreview();
  }

  function syncCard(card) {
    const type = $(".builder-type", card).value, mode = $("#calculatorMode").value, locked = mode === "builtin"; card.dataset.type = type;
    $(".builder-option-editor", card).hidden = type !== "select"; $(".builder-score-checkbox", card).hidden = !(mode === "score" && type === "checkbox"); $(".builder-score-number", card).hidden = !(mode === "score" && type === "number");
    $(".builder-unit-field", card).hidden = type !== "number"; $(".builder-min-field", card).hidden = type !== "number"; $(".builder-max-field", card).hidden = type !== "number"; $(".builder-required-field", card).hidden = type === "section";
    $$(".option-points", card).forEach((input) => { input.hidden = mode !== "score"; }); card.draggable = !locked;
    [".builder-key", ".builder-type", ".builder-unit", ".builder-min", ".builder-max", ".builder-required", ".builder-checked-points", ".add-option", ".add-score-rule", ".remove"].forEach((selector) => { const control = $(selector, card); if (control) control.disabled = locked; });
    $$(".option-value,.option-points,.remove-option,.rule-operator,.rule-value,.rule-max,.rule-points,.rule-label,.remove-rule", card).forEach((control) => { control.disabled = locked; });
  }

  function syncMode() {
    const hasBase = Boolean($("#calculatorBaseToolKey").value); $("#calculatorBuiltinModeOption").hidden = !hasBase;
    if ($("#calculatorMode").value === "builtin" && !hasBase) $("#calculatorMode").value = "formula";
    const mode = $("#calculatorMode").value; $("#calculatorFormulaSection").hidden = mode !== "formula"; $("#calculatorScoreSection").hidden = mode !== "score"; $("#calculatorResultSection").hidden = mode === "builtin"; $("#addCalculatorVariableBtn").hidden = mode === "builtin";
    $("#calculatorVariablesHelp").textContent = mode === "builtin" ? "Puede cambiar textos y ayudas; códigos, tipos y valores permanecen protegidos." : mode === "score" ? "Cada tipo muestra su editor de puntos correspondiente." : "Agregue las variables que necesita la fórmula y ordénelas arrastrando.";
    $("#calculatorSafetyNotice").className = `calculator-safety-notice ${mode}`;
    $("#calculatorSafetyNotice").innerHTML = mode === "builtin" ? `<i data-lucide="shield-check"></i><span><b>Motor clínico protegido.</b> Puede adaptar nombres, explicaciones y etiquetas sin alterar el cálculo validado.</span>` : hasBase ? `<i data-lucide="replace"></i><span><b>Reemplazo local explícito.</b> Esta versión sustituirá a la original; puede desactivarla para recuperarla.</span>` : `<i data-lucide="blocks"></i><span><b>Definición visual versionada.</b> Variables, reglas y rangos se guardan en la base de datos.</span>`;
    $$(".calculator-variable-card", $("#calculatorVariables")).forEach(syncCard); renderFormulaPicker(); renderPreview(); icons($("#calculatorConfigForm"));
  }

  function fields() {
    return $$(".calculator-variable-card", $("#calculatorVariables")).map((card) => {
      const type = $(".builder-type", card).value;
      const options = $$(".calculator-option-row", card).map((row) => ({ label: $(".option-label", row).value.trim(), value: $(".option-value", row).value.trim(), points: Number($(".option-points", row).value || 0) }));
      const scoreRules = $$(".calculator-rule-row", card).map((row) => ({ operator: $(".rule-operator", row).value, value: $(".rule-value", row).value, max: $(".rule-max", row).value || null, points: Number($(".rule-points", row).value || 0), label: $(".rule-label", row).value.trim() }));
      let value = null; try { value = JSON.parse(card.dataset.defaultValue || "null"); } catch { value = null; }
      return { label: $(".builder-label", card).value.trim(), key: $(".builder-key", card).value.trim(), type, unit: $(".builder-unit", card).value.trim(), help: $(".builder-help", card).value.trim(), min: $(".builder-min", card).value || null, max: $(".builder-max", card).value || null, required: type !== "section" && $(".builder-required", card).checked, value, options, checkedPoints: Number($(".builder-checked-points", card).value || 0), scoreRules };
    });
  }

  function ranges() { return $$(".calculator-range-row", $("#calculatorRangeList")).map((row) => ({ min: $(".range-min", row).value === "" ? null : Number($(".range-min", row).value), max: $(".range-max", row).value === "" ? null : Number($(".range-max", row).value), label: $(".range-label", row).value.trim(), severity: $(".range-severity", row).value })).filter((range) => range.label); }
  function draft(active = $("#calculatorActive").checked) { const baseKey = $("#calculatorBaseToolKey").value; return { key: baseKey ? `calculator-override:${baseKey}` : `calculator:${slug($("#calculatorName").value)}`, name: $("#calculatorName").value.trim(), description: $("#calculatorDescription").value.trim(), active, expectedRevision: $("#calculatorConfigRevision").value || undefined, definition: { mode: $("#calculatorMode").value, replacesBuiltInKey: baseKey, category: $("#calculatorCategory").value, source: $("#calculatorSource").value.trim(), clinicalUse: $("#calculatorDescription").value.trim(), fields: fields(), expression: $("#calculatorExpression").value.trim(), basePoints: Number($("#calculatorBasePoints").value || 0), resultLabel: $("#calculatorResultLabel").value.trim(), resultUnit: $("#calculatorResultUnit").value.trim(), decimals: Number($("#calculatorDecimals").value), ranges: ranges() } }; }

  async function saveVisual(event, forcedActive) {
    event?.preventDefault();
    try {
      const payloadDraft = draft(forcedActive ?? $("#calculatorActive").checked);
      if (!payloadDraft.name) throw new Error("Escriba el nombre de la herramienta.");
      if (!payloadDraft.definition.fields.some((field) => field.type !== "section")) throw new Error("Agregue al menos una variable.");
      if (payloadDraft.definition.mode !== "builtin") window.CalculatorEngine.evaluate(payloadDraft.definition, Object.fromEntries(payloadDraft.definition.fields.filter((field) => ["number", "select", "checkbox"].includes(field.type)).map((field) => [field.key, field.type === "checkbox" ? false : field.type === "select" ? field.options[0]?.value : 1])));
      const id = $("#calculatorConfigId").value;
      const response = id ? await api(`/api/clinical/configuration/calculator/${id}`, { method: "PUT", body: JSON.stringify(payloadDraft) }) : await api("/api/clinical/configuration/calculator", { method: "POST", body: JSON.stringify(payloadDraft) });
      toast(id ? "Herramienta actualizada" : "Herramienta creada"); notifyCalculatorConfigurationUpdated(); await loadCalculatorCenter(response.item.id);
    } catch (error) { toast(error.message, "error"); }
  }

  function renderFormulaPicker() {
    const picker = $("#calculatorFormulaVariable"); if (!picker) return; const selected = picker.value;
    const available = fields().filter((field) => ["number", "select", "checkbox"].includes(field.type)); picker.innerHTML = available.map((field) => `<option value="${escapeHtml(field.key)}">${escapeHtml(field.label)} (${escapeHtml(field.key)})</option>`).join("");
    if (available.some((field) => field.key === selected)) picker.value = selected;
  }

  function appendToken(token) { const input = $("#calculatorExpression"), start = input.selectionStart ?? input.value.length, end = input.selectionEnd ?? start; input.value = `${input.value.slice(0, start)}${token}${input.value.slice(end)}`; input.focus(); input.setSelectionRange(start + token.length, start + token.length); renderPreview(); }

  function renderPreview() {
    const preview = $("#calculatorPreview"); if (!preview || $("#calculatorMode").value === "builtin") return; renderFormulaPicker();
    const mode = $("#calculatorMode").value;
    const controls = fields().filter((field) => field.type !== "section").map((field) => field.type === "select" ? `<label><small>${escapeHtml(field.label)}</small><select data-preview-key="${escapeHtml(field.key)}">${field.options.map((option) => `<option value="${escapeHtml(option.value)}">${escapeHtml(option.label)}${mode === "score" ? ` (${option.points >= 0 ? "+" : ""}${option.points})` : ""}</option>`).join("")}</select></label>` : field.type === "checkbox" ? `<label class="preview-check"><input data-preview-key="${escapeHtml(field.key)}" type="checkbox"><small>${escapeHtml(field.label)}</small></label>` : ["text", "textarea"].includes(field.type) ? `<label><small>${escapeHtml(field.label)}</small><input data-preview-key="${escapeHtml(field.key)}" type="text" value="${escapeHtml(field.value ?? "")}"></label>` : `<label><small>${escapeHtml(field.label)}</small><input data-preview-key="${escapeHtml(field.key)}" type="number" value="${escapeHtml(field.value ?? 1)}" step="any"></label>`).join("");
    preview.innerHTML = `<span>Vista previa en vivo</span><div class="field-grid">${controls}</div><strong data-preview-result>Complete la definición.</strong><small data-preview-detail></small>`;
    $$('[data-preview-key]', preview).forEach((input) => input.addEventListener("input", calculatePreviewVisual)); calculatePreviewVisual();
  }

  function calculatePreviewVisual() {
    const preview = $("#calculatorPreview"), resultNode = $("[data-preview-result]", preview), detail = $("[data-preview-detail]", preview); if (!resultNode) return;
    try {
      const values = Object.fromEntries($$("[data-preview-key]", preview).map((input) => [input.dataset.previewKey, input.type === "checkbox" ? input.checked : input.value]));
      const evaluated = window.CalculatorEngine.evaluate(draft().definition, values), decimals = Math.max(0, Math.min(6, Number($("#calculatorDecimals").value) || 0));
      resultNode.textContent = `${$("#calculatorResultLabel").value || "Resultado"}: ${evaluated.value.toFixed(decimals)} ${$("#calculatorResultUnit").value}`.trim();
      detail.textContent = [evaluated.range?.label, ...(evaluated.contributions || []).filter((entry) => entry.points !== 0).map((entry) => `${entry.label}: ${entry.points > 0 ? "+" : ""}${entry.points}`)].filter(Boolean).join(" · ");
    } catch (error) { resultNode.textContent = "Definición pendiente"; detail.textContent = error.message; }
  }

  Object.assign(window, { renderCalculatorList: renderCalculatorListVisual, newCalculator: newCalculatorVisual, editCalculator: editCalculatorVisual, editBuiltInTool: editBuiltInToolVisual, addCalculatorVariable: addVariable, calculatorFields: fields, parseRanges: ranges, calculatorDraft: draft, saveCalculator: saveVisual, renderCalculatorPreview: renderPreview, calculatePreview: calculatePreviewVisual });

  document.addEventListener("DOMContentLoaded", () => {
    $("#calculatorMode").addEventListener("change", syncMode); $("#addCalculatorRangeBtn").addEventListener("click", () => addRange());
    $("#insertFormulaVariableBtn").addEventListener("click", () => appendToken($("#calculatorFormulaVariable").value));
    $$('[data-formula-token]').forEach((button) => button.addEventListener("click", () => appendToken(button.dataset.formulaToken)));
    $$('[data-formula-function]').forEach((button) => button.addEventListener("click", () => appendToken(`${button.dataset.formulaFunction}(${$("#calculatorFormulaVariable").value})`)));
    $("#toggleAdvancedFormulaBtn").addEventListener("click", () => { const input = $("#calculatorExpression"); input.readOnly = !input.readOnly; $("#calculatorFormulaHint").textContent = input.readOnly ? "Se arma con los botones superiores. La edición manual permanece bloqueada por seguridad." : "Edición avanzada habilitada. La fórmula se validará antes de guardar."; });
  });
})();
