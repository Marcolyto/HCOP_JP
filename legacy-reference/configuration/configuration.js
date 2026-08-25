"use strict";

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const DIAGNOSIS_EQUIVALENCE_SYSTEMS = Object.freeze(["snomed", "cie10", "ajcc"]);
const DIAGNOSIS_EQUIVALENCE_LABELS = Object.freeze({
  snomed: "SNOMED CT",
  cie10: "CIE-10",
  ajcc: "AJCC"
});
const LLM_PRESETS = Object.freeze({
  gemini: {
    provider: "gemini",
    baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai",
    model: "gemini-3.5-flash"
  },
  "lm-studio": {
    provider: "lm-studio",
    baseUrl: "http://127.0.0.1:1234/v1",
    model: "local-model"
  },
  ollama: {
    provider: "ollama",
    baseUrl: "http://127.0.0.1:11434/v1",
    model: "llama3.2"
  }
});
const ACCESS_PERMISSIONS = Object.freeze({
  configuration: "section.configuration.view",
  users: "admin.manage-users",
  roles: "admin.manage-roles",
  security: "admin.manage-security",
});
const CONFIGURATION_TABS = Object.freeze([
  "protocols",
  "diagnosis-equivalences",
  "guides",
  "study-templates",
  "calculators",
  "research",
  "day-hospital",
  "artificial-intelligence",
  "access-control"
]);
const CONFIGURATION_TAB_LABELS = Object.freeze({
  protocols: "Protocolos",
  "diagnosis-equivalences": "Equivalencias diagnosticas",
  guides: "Guias",
  "study-templates": "Plantillas anatomicas",
  calculators: "Calculadoras y scores",
  research: "Formularios de investigacion",
  "day-hospital": "Hospital de dia",
  "artificial-intelligence": "Inteligencia artificial",
  "access-control": "Usuarios y permisos"
});
const DIRTY_TRACKED_CONFIGURATION_FORMS = new Set([
  "guideConfigForm",
  "studyTemplateAdminForm",
  "diagnosisEquivalenceConfigForm",
  "calculatorConfigForm",
  "researchConfigForm",
  "dayHospitalSettingsForm",
  "llmConfigurationForm",
  "adminUserForm",
  "adminRoleForm",
  "adminSecuritySettingsForm"
]);
const state = {
  activeTab: "protocols",
  dirtyTabs: new Set(),
  guides: [], studyTemplates: [], diagnosisEquivalences: [], calculators: [], researchForms: [], builtInTools: [], disabledBuiltInKeys: [],
  selectedGuide: null, selectedStudyTemplate: null, selectedDiagnosisEquivalence: null, selectedCalculator: null, selectedBuiltInKey: "", selectedResearch: null,
  diagnosisEquivalenceResults: { snomed: [], cie10: [], ajcc: [] },
  diagnosisEquivalenceSearchRequest: { snomed: 0, cie10: 0, ajcc: 0 },
  diagnosisEquivalenceSearchMeta: { snomed: {}, cie10: {}, ajcc: {} },
  pendingStudyTemplateFile: null,
  diagnosisSettingItem: null, toolSettingsItem: null, dayHospitalSettingsItem: null,
  llmConfig: null,
  access: {
    me: null,
    users: [],
    roles: [],
    permissionCatalog: [],
    security: null,
    selectedUserId: "",
    selectedRoleId: "",
    activeView: "users",
    loaded: false,
  },
};
let builtInBlueprintPromise = null;
let studyTemplatePreviewObjectUrl = "";
let configurationAuthenticationRedirecting = false;
const diagnosisEquivalenceSearchTimers = { snomed: null, cie10: null, ajcc: null };
const configurationNativeFetch = window.fetch.bind(window);

window.fetch = async (...args) => {
  const response = await configurationNativeFetch(...args);
  try {
    const input = args[0];
    const requestUrl = typeof input === "string" || input instanceof URL ? String(input) : String(input?.url || "");
    const url = new URL(requestUrl, window.location.href);
    if (response.status === 401 && url.origin === window.location.origin && url.pathname.startsWith("/api/")) {
      window.queueMicrotask(() => handleConfigurationAuthenticationFailure());
    }
  } catch {
    // Se conserva la respuesta para el flujo que originó la llamada.
  }
  return response;
};

function icons(root = document) { window.lucide?.createIcons?.({ root }); }
function escapeHtml(value) { return String(value ?? "").replace(/[&<>"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[char]); }
function slug(value) { return String(value || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[^a-z0-9_]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 80); }
function repairText(value) { const text = String(value || ""); if (!/[ÃÂâ]/.test(text)) return text; try { return decodeURIComponent(escape(text)); } catch { return text; } }
function formatBytes(value) { const bytes = Number(value) || 0; return bytes > 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`; }
function commaList(value) { return String(value || "").split(",").map((item) => item.trim()).filter(Boolean); }
function notifyConfigurationUpdated() { localStorage.setItem("hcop-configuration-updated", String(Date.now())); }
function notifyCalculatorConfigurationUpdated() { const updatedAt = String(Date.now()); localStorage.setItem("hcop-configuration-updated", updatedAt); localStorage.setItem("hcop-calculator-configuration-updated", updatedAt); }
function isPersistedConfigurationItem(item) { return Boolean(String(item?.id ?? "").trim()); }
function toast(message, type = "success") { const node = document.createElement("div"); node.className = `toast${type === "error" ? " error" : ""}`; node.innerHTML = `<i data-lucide="${type === "error" ? "triangle-alert" : "circle-check"}"></i><span>${escapeHtml(message)}</span>`; $("#toastRegion").append(node); icons(node); setTimeout(() => node.remove(), 4200); }

function configurationTabForElement(element) {
  return element?.closest?.("[data-config-panel]")?.dataset.configPanel || state.activeTab;
}

function markConfigurationPanelDirty(elementOrTab = state.activeTab) {
  const tab = typeof elementOrTab === "string" ? elementOrTab : configurationTabForElement(elementOrTab);
  if (CONFIGURATION_TABS.includes(tab)) state.dirtyTabs.add(tab);
}

function markConfigurationPanelClean(tab = state.activeTab) {
  state.dirtyTabs.delete(tab);
}

function confirmConfigurationTabChange(nextTab) {
  if (nextTab === state.activeTab || !state.dirtyTabs.has(state.activeTab)) return true;
  const section = CONFIGURATION_TAB_LABELS[state.activeTab] || "esta seccion";
  if (!window.confirm(`Hay cambios sin guardar en ${section}. Si cambia de seccion, se perderan. ¿Desea continuar?`)) {
    return false;
  }
  markConfigurationPanelClean(state.activeTab);
  return true;
}

function handleConfigurationAuthenticationFailure() {
  if (configurationAuthenticationRedirecting) return;
  configurationAuthenticationRedirecting = true;
  state.access.me = null;
  state.access.loaded = false;
  setAccessControlStatus?.("La sesión venció. Volviendo al ingreso seguro...", "error");
  toast("La sesión venció. Ingrese nuevamente.", "error");
  window.setTimeout(() => window.location.replace("/"), 900);
}

async function api(path, options = {}) {
  const response = await fetch(path, { cache: "no-store", headers: { Accept: "application/json", ...(options.body ? { "Content-Type": "application/json" } : {}) }, ...options });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401) handleConfigurationAuthenticationFailure();
    const error = new Error(payload.error || "No se pudo completar la operacion.");
    error.status = response.status;
    error.code = payload.code || "";
    throw error;
  }
  return payload;
}

async function checkStatus() {
  const status = $("#configServiceStatus");
  try {
    await api("/api/clinical/status");
    status.className = "service ready";
    status.innerHTML = `<i data-lucide="database"></i><span>Base clínica PostgreSQL disponible</span>`;
  }
  catch { status.className = "service error"; status.innerHTML = `<i data-lucide="database-zap"></i><span>No se pudo abrir PostgreSQL</span>`; }
  icons(status);
}

function setTab(tab) {
  const nextTab = CONFIGURATION_TABS.includes(tab) ? tab : "protocols";
  if (!confirmConfigurationTabChange(nextTab)) return false;
  state.activeTab = nextTab;
  $$('[data-config-tab]').forEach((button) => button.classList.toggle("active", button.dataset.configTab === state.activeTab));
  $$('[data-config-panel]').forEach((panel) => panel.classList.toggle("active", panel.dataset.configPanel === state.activeTab));
  history.replaceState(null, "", `#${state.activeTab}`);
  if (state.activeTab === "diagnosis-equivalences") {
    loadDiagnosisDisplaySetting();
    loadDiagnosisEquivalences();
  }
  if (state.activeTab === "guides") loadGuides();
  if (state.activeTab === "study-templates") loadStudyTemplateAdmin();
  if (state.activeTab === "calculators") loadCalculatorCenter();
  if (state.activeTab === "research") loadResearchForms();
  if (state.activeTab === "day-hospital") loadDayHospitalSettings();
  if (state.activeTab === "artificial-intelligence") loadLlmConfiguration();
  if (state.activeTab === "access-control") loadAccessControl();
  return true;
}

window.HcopConfigurationHelpNavigation = Object.freeze({
  activateTab(tab) {
    setTab(tab);
  },
  currentTab() {
    return state.activeTab;
  }
});

function loading(container) { container.innerHTML = `<div class="loading-row"><i data-lucide="loader-circle"></i><span>Cargando…</span></div>`; icons(container); }
function catalogItem(item, selected, subtitle) { return `<button class="item-button${selected ? " active" : ""}${item.active === false ? " inactive" : ""}" type="button" data-item-id="${escapeHtml(item.id || item.name)}"><span><strong>${escapeHtml(item.name || item.title)}</strong><small>${escapeHtml(subtitle || "")}</small></span><em>${item.active === false ? "Inactiva" : "Activa"}</em></button>`; }

async function loadGuides(selectName = "") {
  const list = $("#guideConfigList"); loading(list);
  try {
    const payload = await api(`/api/guides?includeInactive=1&t=${Date.now()}`);
    state.guides = payload.guides || [];
    renderGuideList();
    const selected = state.guides.find((item) => item.name === selectName) || (state.selectedGuide && state.guides.find((item) => item.name === state.selectedGuide.name));
    if (selected) selectGuide(selected.name);
  } catch (error) { list.innerHTML = `<div class="catalog-empty">${escapeHtml(error.message)}</div>`; }
}

function renderGuideList() {
  const query = $("#guideConfigSearch").value.trim().toLowerCase();
  const rows = state.guides.filter((item) => !query || `${item.title} ${item.name} ${item.site} ${item.source}`.toLowerCase().includes(query));
  $("#guideConfigList").innerHTML = rows.map((item) => catalogItem({ ...item, id: item.name }, state.selectedGuide?.name === item.name, `${item.site} · ${item.source}`)).join("") || `<div class="catalog-empty">No hay guias que coincidan.</div>`;
}

function selectGuide(name) {
  const guide = state.guides.find((item) => item.name === name); if (!guide) return;
  state.selectedGuide = guide; renderGuideList(); $("#guideEmpty").hidden = true; $("#guideConfigForm").hidden = false;
  $("#guideConfigId").value = guide.configurationId || ""; $("#guideConfigRevision").value = guide.configurationRevision || ""; $("#guideFileName").value = guide.name;
  $("#guideEditorTitle").textContent = guide.title; $("#guideFileMeta").textContent = `${guide.name} · ${formatBytes(guide.size)} · actualizado ${new Date(guide.updatedAt).toLocaleDateString("es-AR")}`;
  $("#guideTitle").value = guide.title || ""; $("#guideCategory").value = guide.site || ""; $("#guideAudience").value = guide.audience || ""; $("#guideSource").value = guide.source || ""; $("#guideVersion").value = guide.version || ""; $("#guideTags").value = (guide.tags || []).join(", "); $("#guideDescription").value = guide.description || ""; $("#guideActive").checked = guide.active !== false;
  $("#archiveGuideBtn").textContent = guide.active === false ? "Reactivar" : "Desactivar"; icons($("#guideConfigForm"));
}

function guideDraft(active = $("#guideActive").checked) {
  return { key: `guide:${slug($("#guideFileName").value)}`, name: $("#guideTitle").value.trim(), description: $("#guideDescription").value.trim(), active, expectedRevision: $("#guideConfigRevision").value || undefined, definition: { fileName: $("#guideFileName").value, category: $("#guideCategory").value.trim(), audience: $("#guideAudience").value.trim(), source: $("#guideSource").value.trim(), version: $("#guideVersion").value.trim(), tags: $("#guideTags").value.split(",").map((item) => item.trim()).filter(Boolean) } };
}

async function saveGuide(event, forcedActive) {
  event?.preventDefault();
  try { const draft = guideDraft(forcedActive ?? $("#guideActive").checked); if (!draft.name) throw new Error("Escriba el titulo de la guia."); const id = $("#guideConfigId").value; const payload = id ? await api(`/api/clinical/configuration/guide/${id}`, { method: "PUT", body: JSON.stringify(draft) }) : await api("/api/clinical/configuration/guide", { method: "POST", body: JSON.stringify(draft) }); markConfigurationPanelClean("guides"); toast(id ? "Guia actualizada" : "Guia incorporada"); await loadGuides(draft.definition.fileName); return payload.item; }
  catch (error) { toast(error.message, "error"); return null; }
}

async function uploadGuides(files) {
  for (const file of files) {
    try { if (!file.name.toLowerCase().endsWith(".pdf")) throw new Error(`${file.name}: debe ser PDF.`); const response = await fetch(`/api/guides/import?name=${encodeURIComponent(file.name)}`, { method: "PUT", headers: { "Content-Type": "application/pdf" }, body: file }); const payload = await response.json(); if (!response.ok) throw new Error(payload.error || "No se pudo subir el PDF."); toast(`${file.name} agregado`); }
    catch (error) { toast(error.message, "error"); }
  }
  $("#guideUpload").value = ""; await loadGuides(files[0]?.name || "");
}

function studyTemplateField(template, name, fallback = "") {
  const value = template?.[name] ?? template?.definition?.[name];
  return value == null ? fallback : value;
}

function studyTemplateCategoryLabel(value) {
  const labels = {
    "cuerpo-completo": "Cuerpo completo",
    ginecologia: "Ginecología",
    urologia: "Urología",
    torax: "Tórax",
    abdomen: "Abdomen",
    "cabeza-cuello": "Cabeza y cuello",
    extremidades: "Extremidades",
    "organos-individuales": "Órganos individuales"
  };
  if (labels[value]) return labels[value];
  const text = repairText(value || "Sin categoría").replace(/[-_]+/g, " ").trim();
  return text ? `${text.charAt(0).toLocaleUpperCase("es-AR")}${text.slice(1)}` : "Sin categoría";
}

function safeStudyTemplateAssetUrl(value) {
  const source = String(value || "").trim();
  if (!source) return "";
  if (source.startsWith("blob:")) return source;
  try {
    const normalized = source.startsWith("assets/") ? `/${source}` : source;
    const parsed = new URL(normalized, location.origin);
    if (parsed.origin !== location.origin || !["http:", "https:"].includes(parsed.protocol)) return "";
    return parsed.href;
  } catch {
    return "";
  }
}

function studyTemplateImageUrl(template) {
  return safeStudyTemplateAssetUrl(
    template?.thumbnail
    || template?.definition?.thumbnailUrl
    || template?.file
    || template?.definition?.fileUrl
  );
}

function studyTemplateAvailabilityLabel(template) {
  if (template?.available !== false) return "";
  const reasons = {
    descriptor_invalid: "descriptor inválido",
    file_missing: "archivo ausente",
    file_unreadable: "archivo inaccesible",
    not_a_file: "ruta inválida",
    size_mismatch: "tamaño alterado",
    hash_mismatch: "integridad alterada",
    content_invalid: "imagen dañada"
  };
  return reasons[template.availabilityReason] || "archivo no disponible";
}

function findStudyTemplate(identifier) {
  const value = String(identifier ?? "");
  return state.studyTemplates.find((item) =>
    String(item.id) === value || String(item.configurationId) === value
  ) || null;
}

function renderStudyTemplateCategoryFilter() {
  const select = $("#studyTemplateAdminCategoryFilter");
  const selected = select.value;
  const categories = [...new Set(state.studyTemplates.map((item) => String(studyTemplateField(item, "category")).trim()).filter(Boolean))]
    .sort((left, right) => studyTemplateCategoryLabel(left).localeCompare(studyTemplateCategoryLabel(right), "es"));
  select.innerHTML = `<option value="">Todas las categorías</option>${categories.map((category) =>
    `<option value="${escapeHtml(category)}">${escapeHtml(studyTemplateCategoryLabel(category))}</option>`
  ).join("")}`;
  if (categories.includes(selected)) select.value = selected;
}

function renderStudyTemplateAdminList() {
  const list = $("#studyTemplateAdminList");
  const query = $("#studyTemplateAdminSearch").value.trim().toLocaleLowerCase("es-AR");
  const category = $("#studyTemplateAdminCategoryFilter").value;
  const showInactive = $("#showInactiveStudyTemplates").checked;
  const rows = state.studyTemplates.filter((item) => {
    if (!showInactive && item.active === false) return false;
    if (category && String(studyTemplateField(item, "category")) !== category) return false;
    const searchable = [
      studyTemplateField(item, "title"),
      studyTemplateField(item, "description"),
      studyTemplateField(item, "category"),
      studyTemplateField(item, "author"),
      ...(Array.isArray(studyTemplateField(item, "tags", [])) ? studyTemplateField(item, "tags", []) : commaList(studyTemplateField(item, "tags")))
    ].join(" ").toLocaleLowerCase("es-AR");
    return !query || searchable.includes(query);
  });
  list.innerHTML = rows.map((item) => {
    const title = studyTemplateField(item, "title", "Plantilla sin título");
    const thumbnail = studyTemplateImageUrl(item);
    const selected = String(state.selectedStudyTemplate?.id) === String(item.id);
    const unavailable = item.available === false;
    const tags = Array.isArray(studyTemplateField(item, "tags", [])) ? studyTemplateField(item, "tags", []) : commaList(studyTemplateField(item, "tags"));
    const detail = [
      studyTemplateCategoryLabel(studyTemplateField(item, "category")),
      tags.slice(0, 2).join(", "),
      studyTemplateAvailabilityLabel(item)
    ].filter(Boolean).join(" · ");
    const stateLabel = item.origin === "bundled"
      ? "Incluida"
      : unavailable
        ? "Revisar"
        : item.active === false
          ? "Inactiva"
          : "Propia";
    return `<div role="listitem"><button class="study-template-admin-item${selected ? " active" : ""}${item.active === false ? " inactive" : ""}${unavailable ? " is-unavailable" : ""}" type="button" data-study-template-id="${escapeHtml(item.id)}" aria-pressed="${selected ? "true" : "false"}"><span class="study-template-admin-thumb">${thumbnail && !unavailable ? `<img src="${escapeHtml(thumbnail)}" alt="" loading="lazy">` : `<i data-lucide="${unavailable ? "image-off" : "image"}"></i>`}</span><span class="study-template-admin-copy"><strong>${escapeHtml(title)}</strong><small>${escapeHtml(detail)}</small></span><em>${stateLabel}</em></button></div>`;
  }).join("") || `<div class="catalog-empty">${state.studyTemplates.length ? "No hay plantillas que coincidan." : "No hay plantillas disponibles."}</div>`;
  icons(list);
}

function clearPendingStudyTemplateFile() {
  if (studyTemplatePreviewObjectUrl) URL.revokeObjectURL(studyTemplatePreviewObjectUrl);
  studyTemplatePreviewObjectUrl = "";
  state.pendingStudyTemplateFile = null;
}

function showStudyTemplatePreview(source, title) {
  const image = $("#studyTemplateAdminPreview");
  const empty = $("#studyTemplateAdminPreviewEmpty");
  const url = safeStudyTemplateAssetUrl(source);
  image.onerror = () => {
    image.hidden = true;
    empty.hidden = false;
    empty.innerHTML = `<i data-lucide="image-off"></i><span>No se pudo abrir la vista previa</span>`;
    icons(empty);
  };
  if (!url) {
    image.removeAttribute("src");
    image.hidden = true;
    empty.hidden = false;
    empty.innerHTML = `<i data-lucide="image"></i><span>Seleccione una imagen</span>`;
    icons(empty);
    return;
  }
  image.src = url;
  image.alt = `Vista previa de ${title || "la plantilla anatómica"}`;
  image.hidden = false;
  empty.hidden = true;
}

function studyTemplateMime(file) {
  const allowed = new Set(["image/png", "image/jpeg", "image/gif", "image/webp"]);
  if (allowed.has(file?.type)) return file.type;
  const extension = String(file?.name || "").toLowerCase().split(".").pop();
  return ({ png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", gif: "image/gif", webp: "image/webp" })[extension] || "";
}

function beginStudyTemplateUpload(file) {
  const mime = studyTemplateMime(file);
  if (!file || !file.size || !mime) {
    toast("Seleccione una imagen PNG, JPG, GIF o WebP válida.", "error");
    return;
  }
  if (file.size > 15 * 1024 * 1024) {
    toast("La plantilla no puede superar los 15 MB.", "error");
    return;
  }
  markConfigurationPanelDirty("study-templates");
  clearPendingStudyTemplateFile();
  state.pendingStudyTemplateFile = file;
  studyTemplatePreviewObjectUrl = URL.createObjectURL(file);
  state.selectedStudyTemplate = null;
  renderStudyTemplateAdminList();

  const form = $("#studyTemplateAdminForm");
  setStudyTemplateEditorReadOnly(false);
  form.reset();
  form.hidden = false;
  $("#studyTemplateAdminEmpty").hidden = true;
  $("#studyTemplateAdminId").value = "";
  $("#studyTemplateAdminRevision").value = "";
  const plainName = file.name.replace(/\.[^.]+$/, "").replace(/[-_]+/g, " ").trim();
  $("#studyTemplateAdminTitle").value = plainName ? `${plainName.charAt(0).toLocaleUpperCase("es-AR")}${plainName.slice(1)}` : "";
  $("#studyTemplateAdminActive").checked = true;
  $("#studyTemplateAdminActive").disabled = true;
  $("#studyTemplateAdminRightsConfirmed").checked = false;
  $("#studyTemplateAdminEditorTitle").textContent = "Nueva plantilla";
  $("#studyTemplateAdminFileMeta").textContent = `${file.name} · ${formatBytes(file.size)} · ${mime}`;
  $("#studyTemplatePreviewTitle").textContent = file.name;
  $("#cancelStudyTemplateUploadBtn").hidden = false;
  $("#archiveStudyTemplateBtn").hidden = true;
  $("#saveStudyTemplateBtn").hidden = false;
  $("#saveStudyTemplateBtn span").textContent = "Agregar plantilla";
  showStudyTemplatePreview(studyTemplatePreviewObjectUrl, plainName);
  requestAnimationFrame(() => $("#studyTemplateAdminTitle").focus());
}

function setStudyTemplateEditorReadOnly(readOnly) {
  const form = $("#studyTemplateAdminForm");
  $$("input:not([type='hidden']), select, textarea", form).forEach((control) => {
    control.disabled = readOnly;
  });
}

function editStudyTemplate(template) {
  if (!template) return;
  clearPendingStudyTemplateFile();
  state.selectedStudyTemplate = template;
  renderStudyTemplateAdminList();

  const form = $("#studyTemplateAdminForm");
  const bundled = template.origin === "bundled";
  setStudyTemplateEditorReadOnly(false);
  const definition = template.definition || {};
  const title = studyTemplateField(template, "title", "Plantilla anatómica");
  const originalFileName = studyTemplateField(template, "originalFileName", definition.fileName || "");
  const bytes = Number(studyTemplateField(template, "bytes", 0));
  const mime = studyTemplateField(template, "mime");
  form.reset();
  form.hidden = false;
  $("#studyTemplateAdminEmpty").hidden = true;
  $("#studyTemplateAdminId").value = template.configurationId || "";
  $("#studyTemplateAdminRevision").value = template.revision || "";
  $("#studyTemplateAdminTitle").value = title;
  $("#studyTemplateAdminCategory").value = studyTemplateField(template, "category");
  $("#studyTemplateAdminTags").value = (Array.isArray(studyTemplateField(template, "tags", [])) ? studyTemplateField(template, "tags", []) : commaList(studyTemplateField(template, "tags"))).join(", ");
  $("#studyTemplateAdminAuthor").value = studyTemplateField(template, "author");
  $("#studyTemplateAdminAttribution").value = studyTemplateField(template, "attribution");
  $("#studyTemplateAdminSourceUrl").value = studyTemplateField(template, "sourceUrl");
  $("#studyTemplateAdminLicense").value = studyTemplateField(template, "license");
  $("#studyTemplateAdminLicenseUrl").value = studyTemplateField(template, "licenseUrl");
  $("#studyTemplateAdminDescription").value = studyTemplateField(template, "description");
  $("#studyTemplateAdminRightsConfirmed").checked = bundled || Boolean(studyTemplateField(template, "rightsConfirmed", false));
  $("#studyTemplateAdminActive").checked = template.active !== false;
  $("#studyTemplateAdminActive").disabled = false;
  $("#studyTemplateAdminEditorTitle").textContent = bundled ? `${title} · incluida` : title;
  $("#studyTemplateAdminFileMeta").textContent = [
    bundled ? "Biblioteca anatómica incluida con HCOP" : "",
    originalFileName,
    bytes ? formatBytes(bytes) : "",
    mime,
    template.revision ? `versión ${template.revision}` : "",
    template.available === false
      ? `${studyTemplateAvailabilityLabel(template)}; vuelva a elegir el mismo archivo desde “Nueva plantilla” para restaurarlo`
      : ""
  ].filter(Boolean).join(" · ");
  $("#studyTemplatePreviewTitle").textContent = title;
  const archive = $("#archiveStudyTemplateBtn");
  $("#cancelStudyTemplateUploadBtn").hidden = true;
  archive.hidden = bundled;
  $("#saveStudyTemplateBtn").hidden = bundled;
  archive.innerHTML = `<i data-lucide="${template.active === false ? "rotate-ccw" : "archive"}"></i>${template.active === false ? "Reactivar" : "Desactivar"}`;
  $("#saveStudyTemplateBtn span").textContent = "Guardar cambios";
  showStudyTemplatePreview(studyTemplateImageUrl(template), title);
  setStudyTemplateEditorReadOnly(bundled);
  icons(form);
}

async function loadStudyTemplateAdmin(selectIdentifier = "") {
  const list = $("#studyTemplateAdminList");
  list.setAttribute("aria-busy", "true");
  loading(list);
  try {
    const payload = await api(`/api/study-templates?scope=all&includeInactive=1&t=${Date.now()}`);
    state.studyTemplates = Array.isArray(payload.templates) ? payload.templates : [];
    renderStudyTemplateCategoryFilter();
    renderStudyTemplateAdminList();
    if (state.pendingStudyTemplateFile) return;
    const selected = findStudyTemplate(selectIdentifier)
      || findStudyTemplate(state.selectedStudyTemplate?.id)
      || findStudyTemplate(state.selectedStudyTemplate?.configurationId);
    if (selected) editStudyTemplate(selected);
    else if (state.selectedStudyTemplate) {
      state.selectedStudyTemplate = null;
      $("#studyTemplateAdminForm").hidden = true;
      $("#studyTemplateAdminEmpty").hidden = false;
    }
  } catch (error) {
    list.innerHTML = `<div class="catalog-empty">${escapeHtml(error.message)}</div>`;
  } finally {
    list.removeAttribute("aria-busy");
  }
}

function studyTemplateDraft(active = $("#studyTemplateAdminActive").checked) {
  const template = state.selectedStudyTemplate;
  const definition = { ...(template?.definition || {}) };
  const title = $("#studyTemplateAdminTitle").value.trim();
  const rightsConfirmed = $("#studyTemplateAdminRightsConfirmed").checked;
  return {
    key: template?.configurationKey || `study-template:${slug(title)}`,
    name: title,
    description: $("#studyTemplateAdminDescription").value.trim(),
    active,
    expectedRevision: $("#studyTemplateAdminRevision").value || undefined,
    definition: {
      ...definition,
      title,
      category: $("#studyTemplateAdminCategory").value.trim(),
      tags: commaList($("#studyTemplateAdminTags").value),
      author: $("#studyTemplateAdminAuthor").value.trim(),
      attribution: $("#studyTemplateAdminAttribution").value.trim(),
      sourceUrl: $("#studyTemplateAdminSourceUrl").value.trim(),
      license: $("#studyTemplateAdminLicense").value.trim(),
      licenseUrl: $("#studyTemplateAdminLicenseUrl").value.trim(),
      description: $("#studyTemplateAdminDescription").value.trim(),
      fileName: definition.fileName || studyTemplateField(template, "originalFileName"),
      fileUrl: definition.fileUrl || studyTemplateField(template, "file"),
      thumbnailUrl: definition.thumbnailUrl || studyTemplateField(template, "thumbnail"),
      sha256: definition.sha256 || studyTemplateField(template, "sha256"),
      mime: definition.mime || studyTemplateField(template, "mime"),
      bytes: Number(definition.bytes || studyTemplateField(template, "bytes", 0)),
      originalFileName: definition.originalFileName || studyTemplateField(template, "originalFileName"),
      rightsConfirmed
    }
  };
}

function setStudyTemplateBusy(busy) {
  const form = $("#studyTemplateAdminForm");
  form.setAttribute("aria-busy", busy ? "true" : "false");
  $("#saveStudyTemplateBtn").disabled = busy;
  $("#archiveStudyTemplateBtn").disabled = busy;
  $("#cancelStudyTemplateUploadBtn").disabled = busy;
  $("#studyTemplateAdminUpload").disabled = busy;
  $("#studyTemplateAdminUpload").closest("label").classList.toggle("is-disabled", busy);
}

function cancelStudyTemplateUpload() {
  clearPendingStudyTemplateFile();
  markConfigurationPanelClean("study-templates");
  state.selectedStudyTemplate = null;
  $("#studyTemplateAdminForm").hidden = true;
  $("#studyTemplateAdminEmpty").hidden = false;
  renderStudyTemplateAdminList();
}

function studyTemplateUploadQuery() {
  const file = state.pendingStudyTemplateFile;
  const parameters = new URLSearchParams({
    name: file.name,
    title: $("#studyTemplateAdminTitle").value.trim(),
    category: $("#studyTemplateAdminCategory").value.trim(),
    tags: $("#studyTemplateAdminTags").value.trim(),
    author: $("#studyTemplateAdminAuthor").value.trim(),
    attribution: $("#studyTemplateAdminAttribution").value.trim(),
    sourceUrl: $("#studyTemplateAdminSourceUrl").value.trim(),
    license: $("#studyTemplateAdminLicense").value.trim(),
    licenseUrl: $("#studyTemplateAdminLicenseUrl").value.trim(),
    description: $("#studyTemplateAdminDescription").value.trim(),
    rightsConfirmed: "1"
  });
  if (parameters.toString().length > 6000) {
    throw new Error("Los textos de la plantilla son demasiado extensos. Resuma la descripción, atribución o enlaces.");
  }
  return parameters;
}

async function uploadStudyTemplate() {
  const file = state.pendingStudyTemplateFile;
  if (!file) throw new Error("Seleccione el archivo de la nueva plantilla.");
  const response = await fetch(`/api/study-templates?${studyTemplateUploadQuery()}`, {
    method: "POST",
    cache: "no-store",
    headers: { Accept: "application/json", "Content-Type": studyTemplateMime(file) },
    body: file
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.error || "No se pudo agregar la plantilla.");
  return payload;
}

async function saveStudyTemplate(event, forcedActive) {
  event?.preventDefault();
  const form = $("#studyTemplateAdminForm");
  if (!form.reportValidity()) return null;
  if (!$("#studyTemplateAdminRightsConfirmed").checked) {
    toast("Confirme que la institución puede utilizar esta imagen.", "error");
    $("#studyTemplateAdminRightsConfirmed").focus();
    return null;
  }
  setStudyTemplateBusy(true);
  try {
    if (state.pendingStudyTemplateFile) {
      const payload = await uploadStudyTemplate();
      const selected = payload.template?.id || payload.item?.id || "";
      toast(payload.repaired
        ? "Archivo de la plantilla restaurado"
        : payload.existing
          ? "La plantilla ya quedó confirmada"
          : "Plantilla anatómica agregada");
      clearPendingStudyTemplateFile();
      $("#studyTemplateAdminSearch").value = "";
      $("#studyTemplateAdminCategoryFilter").value = "";
      $("#showInactiveStudyTemplates").checked = false;
      notifyConfigurationUpdated();
      markConfigurationPanelClean("study-templates");
      await loadStudyTemplateAdmin(selected);
      return payload.template || payload.item;
    }
    const id = $("#studyTemplateAdminId").value;
    if (!id || !state.selectedStudyTemplate) throw new Error("Seleccione una plantilla para editar.");
    const draft = studyTemplateDraft(forcedActive ?? $("#studyTemplateAdminActive").checked);
    const payload = await api(`/api/clinical/configuration/study-template/${id}`, { method: "PUT", body: JSON.stringify(draft) });
    toast(draft.active ? "Plantilla actualizada" : "Plantilla desactivada");
    if (!draft.active) $("#showInactiveStudyTemplates").checked = true;
    notifyConfigurationUpdated();
    markConfigurationPanelClean("study-templates");
    await loadStudyTemplateAdmin(payload.item?.id || id);
    return payload.item;
  } catch (error) {
    toast(error.message, "error");
    return null;
  } finally {
    setStudyTemplateBusy(false);
  }
}

async function toggleStudyTemplateActive() {
  const template = state.selectedStudyTemplate;
  const id = $("#studyTemplateAdminId").value;
  if (!template || !id) return;
  if (template.active === false) {
    await saveStudyTemplate(null, true);
    return;
  }
  setStudyTemplateBusy(true);
  try {
    await api(`/api/clinical/configuration/study-template/${id}`, { method: "DELETE" });
    $("#showInactiveStudyTemplates").checked = true;
    toast("Plantilla desactivada; el archivo se conserva");
    notifyConfigurationUpdated();
    markConfigurationPanelClean("study-templates");
    await loadStudyTemplateAdmin(id);
  } catch (error) {
    toast(error.message, "error");
  } finally {
    setStudyTemplateBusy(false);
  }
}

function selectedDiagnosisVisibleSystems() {
  return $$("[data-diagnosis-visible-system]:checked")
    .map((input) => input.value)
    .filter((system) => DIAGNOSIS_EQUIVALENCE_SYSTEMS.includes(system));
}

function applyDiagnosisVisibleSystems(systems) {
  const visible = new Set(
    Array.isArray(systems) && systems.some((system) => DIAGNOSIS_EQUIVALENCE_SYSTEMS.includes(system))
      ? systems
      : DIAGNOSIS_EQUIVALENCE_SYSTEMS
  );
  $$("[data-diagnosis-visible-system]").forEach((input) => {
    input.checked = visible.has(input.value);
  });
}

function setDiagnosisVisibleSettingsStatus(message, kind = "") {
  const status = $("#diagnosisVisibleSettingsStatus");
  status.className = `diagnosis-visible-status${kind ? ` is-${kind}` : ""}`;
  status.textContent = message;
}

async function loadDiagnosisDisplaySetting() {
  try {
    const payload = await api(`/api/clinical/configuration/diagnosis-setting?includeInactive=1&t=${Date.now()}`);
    state.diagnosisSettingItem = (payload.items || []).find((item) => item.key === "diagnosis-display")
      || payload.items?.[0]
      || null;
    applyDiagnosisVisibleSystems(state.diagnosisSettingItem?.definition?.visibleSystems);
    setDiagnosisVisibleSettingsStatus(
      state.diagnosisSettingItem ? `Configuración v${state.diagnosisSettingItem.revision}` : "Se muestran las tres clasificaciones."
    );
  } catch (error) {
    state.diagnosisSettingItem = null;
    applyDiagnosisVisibleSystems(DIAGNOSIS_EQUIVALENCE_SYSTEMS);
    setDiagnosisVisibleSettingsStatus(error.message, "error");
  }
}

async function saveDiagnosisDisplaySetting() {
  const button = $("#saveDiagnosisVisibleSettingsBtn");
  const visibleSystems = selectedDiagnosisVisibleSystems();
  if (!visibleSystems.length) {
    setDiagnosisVisibleSettingsStatus("Seleccione al menos una clasificación.", "error");
    $("[data-diagnosis-visible-system]")?.focus();
    return;
  }
  const draft = {
    key: "diagnosis-display",
    name: "diagnosis-display",
    active: true,
    expectedRevision: state.diagnosisSettingItem?.revision,
    definition: { schemaVersion: 1, visibleSystems }
  };
  button.disabled = true;
  try {
    const path = state.diagnosisSettingItem
      ? `/api/clinical/configuration/diagnosis-setting/${state.diagnosisSettingItem.id}`
      : "/api/clinical/configuration/diagnosis-setting";
    const payload = await api(path, {
      method: state.diagnosisSettingItem ? "PUT" : "POST",
      body: JSON.stringify(draft)
    });
    state.diagnosisSettingItem = payload.item;
    applyDiagnosisVisibleSystems(payload.item?.definition?.visibleSystems || visibleSystems);
    notifyConfigurationUpdated();
    setDiagnosisVisibleSettingsStatus(`Guardado · versión ${payload.item?.revision || 1}`, "saved");
    toast("Clasificaciones visibles actualizadas");
  } catch (error) {
    setDiagnosisVisibleSettingsStatus(error.message, "error");
    toast(error.message, "error");
  } finally {
    button.disabled = false;
  }
}

function diagnosticEquivalenceDefinition(item) {
  return item?.definition && typeof item.definition === "object" ? item.definition : {};
}

function diagnosticEquivalenceConcept(item, system) {
  const value = diagnosticEquivalenceDefinition(item)[system];
  return value && typeof value === "object" && !Array.isArray(value) ? value : {};
}

function diagnosticEquivalenceSearchText(item) {
  const definition = diagnosticEquivalenceDefinition(item);
  return [
    item?.name,
    item?.description,
    definition.notes,
    ...DIAGNOSIS_EQUIVALENCE_SYSTEMS.flatMap((system) => {
      const concept = diagnosticEquivalenceConcept(item, system);
      return [concept.code, concept.display];
    })
  ].filter(Boolean).join(" ").toLocaleLowerCase("es-AR");
}

async function loadDiagnosisEquivalences(selectId = "") {
  const list = $("#diagnosisEquivalenceConfigList");
  loading(list);
  try {
    const payload = await api(`/api/clinical/configuration/diagnosis-equivalence?includeInactive=1&t=${Date.now()}`);
    state.diagnosisEquivalences = Array.isArray(payload.items) ? payload.items : [];
    renderDiagnosisEquivalenceList();
    const selected = state.diagnosisEquivalences.find((item) => item.id === String(selectId))
      || state.diagnosisEquivalences.find((item) => item.id === state.selectedDiagnosisEquivalence?.id);
    if (selected) editDiagnosisEquivalence(selected);
  } catch (error) {
    list.innerHTML = `<div class="catalog-empty">${escapeHtml(error.message)}</div>`;
  }
}

function renderDiagnosisEquivalenceList() {
  const query = $("#diagnosisEquivalenceConfigSearch").value.trim().toLocaleLowerCase("es-AR");
  const showInactive = $("#showInactiveDiagnosisEquivalences").checked;
  const rows = state.diagnosisEquivalences.filter((item) =>
    (showInactive || item.active) && (!query || diagnosticEquivalenceSearchText(item).includes(query))
  );
  $("#diagnosisEquivalenceConfigList").innerHTML = rows.map((item) => {
    const snomed = diagnosticEquivalenceConcept(item, "snomed").code || "—";
    const cie10 = diagnosticEquivalenceConcept(item, "cie10").code || "—";
    const ajcc = diagnosticEquivalenceConcept(item, "ajcc").code || "—";
    return catalogItem(
      item,
      item.id === state.selectedDiagnosisEquivalence?.id,
      `SNOMED ${snomed} · CIE-10 ${cie10} · AJCC ${ajcc} · v${item.revision}`
    );
  }).join("") || `<div class="catalog-empty">No hay equivalencias que coincidan.</div>`;
  icons($("#diagnosisEquivalenceConfigList"));
}

function setDiagnosisEquivalenceSearchStatus(system, message, kind = "hint") {
  const status = $(`[data-diagnosis-equivalence-status="${system}"]`);
  if (!status) return;
  status.className = `diagnosis-equivalence-search-status is-${kind}`;
  status.textContent = message;
}

function resetDiagnosisEquivalenceSearch(system, message = "Escriba al menos dos caracteres") {
  state.diagnosisEquivalenceResults[system] = [];
  state.diagnosisEquivalenceSearchMeta[system] = {};
  const select = $(`[data-diagnosis-equivalence-results="${system}"]`);
  if (select) {
    select.disabled = false;
    select.removeAttribute("aria-busy");
    select.innerHTML = `<option value="">${escapeHtml(message)}</option>`;
  }
}

function resetAllDiagnosisEquivalenceSearches() {
  for (const system of DIAGNOSIS_EQUIVALENCE_SYSTEMS) {
    window.clearTimeout(diagnosisEquivalenceSearchTimers[system]);
    diagnosisEquivalenceSearchTimers[system] = null;
    state.diagnosisEquivalenceSearchRequest[system] += 1;
    resetDiagnosisEquivalenceSearch(system);
    setDiagnosisEquivalenceSearchStatus(
      system,
      system === "ajcc"
        ? "El catálogo AJCC es local. Seleccione el capítulo que corresponde revisar."
        : "Busque y seleccione una coincidencia, o complete los campos manualmente.",
      "hint"
    );
  }
}

function diagnosisEquivalenceResultLabel(item) {
  return [
    item?.code && `[${item.code}]`,
    item?.display,
    item?.group && `— ${item.group}`
  ].filter(Boolean).join(" ");
}

async function searchDiagnosisEquivalenceCatalog(system) {
  if (!DIAGNOSIS_EQUIVALENCE_SYSTEMS.includes(system)) return;
  const input = $(`[data-diagnosis-equivalence-search="${system}"]`);
  const select = $(`[data-diagnosis-equivalence-results="${system}"]`);
  const query = input?.value.trim() || "";
  if (query.length < 2) {
    resetDiagnosisEquivalenceSearch(system);
    setDiagnosisEquivalenceSearchStatus(system, "Escriba al menos dos caracteres para buscar.", "hint");
    return;
  }

  const requestId = state.diagnosisEquivalenceSearchRequest[system] + 1;
  state.diagnosisEquivalenceSearchRequest[system] = requestId;
  select.disabled = true;
  select.setAttribute("aria-busy", "true");
  select.innerHTML = `<option value="">Buscando coincidencias…</option>`;
  setDiagnosisEquivalenceSearchStatus(system, `Buscando en ${DIAGNOSIS_EQUIVALENCE_LABELS[system]}…`, "loading");

  try {
    const params = new URLSearchParams({ system, q: query, limit: "40" });
    const payload = await api(`/api/diagnosis-catalogs/search?${params}`);
    if (requestId !== state.diagnosisEquivalenceSearchRequest[system] || query !== input.value.trim()) return;
    const results = (Array.isArray(payload.items) ? payload.items : []).map((item) => ({
      code: String(item.code || item.id || "").trim(),
      display: String(item.display || item.name || item.term || "").trim(),
      group: String(item.group || "").trim(),
      version: String(item.version || payload.version || payload.edition || "").trim(),
      source: String(item.source || payload.source || "").trim(),
      sourceConceptId: String(item.sourceConceptId || "").trim()
    })).filter((item) => item.code && item.display);
    state.diagnosisEquivalenceResults[system] = results;
    select.innerHTML = results.length
      ? `<option value="">Seleccione una coincidencia</option>${results.map((item, index) =>
        `<option value="${index}">${escapeHtml(diagnosisEquivalenceResultLabel(item))}</option>`
      ).join("")}`
      : `<option value="">Sin coincidencias</option>`;
    setDiagnosisEquivalenceSearchStatus(
      system,
      results.length
        ? `${results.length} coincidencia${results.length === 1 ? "" : "s"}. Seleccione una para completar código y descripción.`
        : "No se encontraron coincidencias.",
      results.length ? "ready" : "empty"
    );
  } catch (error) {
    if (requestId !== state.diagnosisEquivalenceSearchRequest[system]) return;
    state.diagnosisEquivalenceResults[system] = [];
    select.innerHTML = `<option value="">Servicio no disponible</option>`;
    setDiagnosisEquivalenceSearchStatus(system, error.message, "error");
  } finally {
    if (requestId === state.diagnosisEquivalenceSearchRequest[system]) {
      select.disabled = false;
      select.removeAttribute("aria-busy");
    }
  }
}

function scheduleDiagnosisEquivalenceSearch(system) {
  if (!DIAGNOSIS_EQUIVALENCE_SYSTEMS.includes(system)) return;
  window.clearTimeout(diagnosisEquivalenceSearchTimers[system]);
  diagnosisEquivalenceSearchTimers[system] = window.setTimeout(
    () => searchDiagnosisEquivalenceCatalog(system),
    280
  );
}

function applyDiagnosisEquivalenceResult(system, selectedIndex) {
  if (!DIAGNOSIS_EQUIVALENCE_SYSTEMS.includes(system) || selectedIndex === "") return;
  const result = state.diagnosisEquivalenceResults[system][Number(selectedIndex)];
  if (!result) return;
  $(`[data-diagnosis-equivalence-code="${system}"]`).value = result.code;
  $(`[data-diagnosis-equivalence-display="${system}"]`).value = result.display;
  state.diagnosisEquivalenceSearchMeta[system] = {
    version: result.version,
    source: result.source,
    sourceConceptId: result.sourceConceptId
  };
  if (!$("#diagnosisEquivalenceName").value.trim() && system === "snomed") {
    $("#diagnosisEquivalenceName").value = result.display;
  }
  setDiagnosisEquivalenceSearchStatus(
    system,
    `${DIAGNOSIS_EQUIVALENCE_LABELS[system]} seleccionado. Puede ajustar código o descripción antes de guardar.`,
    "selected"
  );
}

function fillDiagnosisEquivalenceConcept(system, value = {}) {
  $(`[data-diagnosis-equivalence-search="${system}"]`).value = "";
  $(`[data-diagnosis-equivalence-code="${system}"]`).value = value.code || "";
  $(`[data-diagnosis-equivalence-display="${system}"]`).value = value.display || "";
  state.diagnosisEquivalenceSearchMeta[system] = {
    version: value.version || "",
    source: value.source || "",
    sourceConceptId: value.sourceConceptId || ""
  };
}

function newDiagnosisEquivalence() {
  markConfigurationPanelDirty("diagnosis-equivalences");
  state.selectedDiagnosisEquivalence = null;
  renderDiagnosisEquivalenceList();
  $("#diagnosisEquivalenceEmpty").hidden = true;
  $("#diagnosisEquivalenceConfigForm").hidden = false;
  $("#diagnosisEquivalenceConfigForm").reset();
  $("#diagnosisEquivalenceConfigId").value = "";
  $("#diagnosisEquivalenceConfigRevision").value = "";
  $("#diagnosisEquivalenceActive").checked = true;
  $("#diagnosisEquivalenceRelation").value = "exact";
  $("#diagnosisEquivalenceConfidence").value = "medium";
  $("#diagnosisEquivalenceEditorTitle").textContent = "Nueva equivalencia";
  $("#diagnosisEquivalenceRevisionLabel").textContent = "Sin guardar";
  $("#archiveDiagnosisEquivalenceBtn").hidden = true;
  resetAllDiagnosisEquivalenceSearches();
  DIAGNOSIS_EQUIVALENCE_SYSTEMS.forEach((system) => fillDiagnosisEquivalenceConcept(system));
  $("#diagnosisEquivalenceName").focus();
}

function editDiagnosisEquivalence(item) {
  const definition = diagnosticEquivalenceDefinition(item);
  state.selectedDiagnosisEquivalence = item;
  renderDiagnosisEquivalenceList();
  $("#diagnosisEquivalenceEmpty").hidden = true;
  $("#diagnosisEquivalenceConfigForm").hidden = false;
  $("#diagnosisEquivalenceConfigId").value = item.id;
  $("#diagnosisEquivalenceConfigRevision").value = item.revision;
  $("#diagnosisEquivalenceName").value = item.name || "";
  $("#diagnosisEquivalenceActive").checked = item.active !== false;
  $("#diagnosisEquivalenceRelation").value = ["exact", "broader", "conditional"].includes(definition.relation) ? definition.relation : "conditional";
  $("#diagnosisEquivalenceConfidence").value = ["high", "medium", "low"].includes(definition.confidence) ? definition.confidence : "medium";
  $("#diagnosisEquivalenceNotes").value = definition.notes || item.description || "";
  $("#diagnosisEquivalenceEditorTitle").textContent = item.name || "Equivalencia";
  $("#diagnosisEquivalenceRevisionLabel").textContent = `Versión ${item.revision} · ${item.active === false ? "desactivada" : "activa"}`;
  $("#archiveDiagnosisEquivalenceBtn").hidden = false;
  $("#archiveDiagnosisEquivalenceBtn").innerHTML = `<i data-lucide="${item.active === false ? "rotate-ccw" : "archive"}"></i><span>${item.active === false ? "Reactivar" : "Desactivar"}</span>`;
  resetAllDiagnosisEquivalenceSearches();
  DIAGNOSIS_EQUIVALENCE_SYSTEMS.forEach((system) => fillDiagnosisEquivalenceConcept(system, diagnosticEquivalenceConcept(item, system)));
  icons($("#diagnosisEquivalenceConfigForm"));
}

function diagnosisEquivalenceConceptDraft(system) {
  const meta = state.diagnosisEquivalenceSearchMeta[system] || {};
  return {
    code: $(`[data-diagnosis-equivalence-code="${system}"]`).value.trim(),
    display: $(`[data-diagnosis-equivalence-display="${system}"]`).value.trim(),
    version: String(meta.version || "").trim(),
    source: String(meta.source || "").trim(),
    ...(system === "ajcc" ? {} : { sourceConceptId: String(meta.sourceConceptId || "").trim() })
  };
}

function diagnosisEquivalenceDraft(active = $("#diagnosisEquivalenceActive").checked) {
  const notes = $("#diagnosisEquivalenceNotes").value.trim();
  return {
    name: $("#diagnosisEquivalenceName").value.trim(),
    description: notes,
    active,
    expectedRevision: $("#diagnosisEquivalenceConfigRevision").value || undefined,
    definition: {
      schemaVersion: 1,
      snomed: diagnosisEquivalenceConceptDraft("snomed"),
      cie10: diagnosisEquivalenceConceptDraft("cie10"),
      ajcc: diagnosisEquivalenceConceptDraft("ajcc"),
      relation: $("#diagnosisEquivalenceRelation").value,
      confidence: $("#diagnosisEquivalenceConfidence").value,
      notes
    }
  };
}

function validateDiagnosisEquivalenceDraft(draft) {
  if (!draft.name) {
    $("#diagnosisEquivalenceName").focus();
    throw new Error("Escriba el nombre de la equivalencia.");
  }
  const requiredSystems = draft.active ? DIAGNOSIS_EQUIVALENCE_SYSTEMS : ["ajcc"];
  const incompleteSystem = requiredSystems.find((system) =>
    !draft.definition[system].code || !draft.definition[system].display
  );
  if (incompleteSystem) {
    $(`[data-diagnosis-equivalence-code="${incompleteSystem}"]`).focus();
    throw new Error(
      incompleteSystem === "ajcc"
        ? "AJCC necesita código y descripción incluso en un borrador."
        : `Complete código y descripción de ${DIAGNOSIS_EQUIVALENCE_LABELS[incompleteSystem]}, o guarde la equivalencia desactivada como borrador.`
    );
  }
}

async function saveDiagnosisEquivalence(event, forcedActive) {
  event?.preventDefault();
  try {
    const draft = diagnosisEquivalenceDraft(forcedActive ?? $("#diagnosisEquivalenceActive").checked);
    validateDiagnosisEquivalenceDraft(draft);
    const id = $("#diagnosisEquivalenceConfigId").value;
    const payload = id
      ? await api(`/api/clinical/configuration/diagnosis-equivalence/${id}`, { method: "PUT", body: JSON.stringify(draft) })
      : await api("/api/clinical/configuration/diagnosis-equivalence", { method: "POST", body: JSON.stringify(draft) });
    notifyConfigurationUpdated();
    markConfigurationPanelClean("diagnosis-equivalences");
    toast(id ? "Equivalencia actualizada" : "Equivalencia creada");
    await loadDiagnosisEquivalences(payload.item.id);
  } catch (error) {
    toast(error.message, "error");
  }
}

async function toggleDiagnosisEquivalenceActive() {
  const item = state.selectedDiagnosisEquivalence;
  if (!item?.id) return;
  if (item.active === false) {
    await saveDiagnosisEquivalence(null, true);
    return;
  }
  try {
    await api(`/api/clinical/configuration/diagnosis-equivalence/${item.id}`, { method: "DELETE" });
    $("#showInactiveDiagnosisEquivalences").checked = true;
    notifyConfigurationUpdated();
    markConfigurationPanelClean("diagnosis-equivalences");
    toast("Equivalencia desactivada; se conservaron sus versiones");
    await loadDiagnosisEquivalences(item.id);
  } catch (error) {
    toast(error.message, "error");
  }
}

async function loadCalculators(selectId = "") {
  const list = $("#calculatorConfigList"); loading(list);
  try { const payload = await api(`/api/clinical/configuration/calculator?includeInactive=1&t=${Date.now()}`); state.calculators = payload.items || []; renderCalculatorList(); const selected = state.calculators.find((item) => item.id === String(selectId)) || state.calculators.find((item) => item.id === state.selectedCalculator?.id); if (selected) editCalculator(selected); }
  catch (error) { list.innerHTML = `<div class="catalog-empty">${escapeHtml(error.message)}</div>`; }
}

function loadBuiltInBlueprints() {
  if (builtInBlueprintPromise) return builtInBlueprintPromise;
  builtInBlueprintPromise = new Promise((resolve) => {
    const frame = $("#builtInBlueprintFrame");
    const finish = () => {
      try {
        const tools = frame?.contentWindow?.HCOP_BUILTIN_TOOL_BLUEPRINTS;
        if (Array.isArray(tools) && tools.length) return resolve(tools);
      } catch { /* mismo origen; el manifiesto queda como respaldo */ }
      return null;
    };
    if (finish()) return;
    frame?.addEventListener("load", () => { if (!finish()) setTimeout(() => finish() || resolve([]), 500); }, { once: true });
    window.addEventListener("message", (event) => {
      if (event.origin === location.origin && event.data?.type === "hcop-tool-blueprints" && Array.isArray(event.data.tools)) resolve(event.data.tools);
    }, { once: true });
    setTimeout(() => finish() || resolve([]), 5000);
  });
  return builtInBlueprintPromise;
}

async function loadCalculatorCenter(selectId = "") {
  const list = $("#builtInToolList"); loading(list);
  try {
    const [blueprints, manifest, settings] = await Promise.all([
      loadBuiltInBlueprints(),
      api(`/herramientas/manifest.json?t=${Date.now()}`),
      api(`/api/clinical/configuration/tool-settings?includeInactive=1&t=${Date.now()}`),
    ]);
    state.builtInTools = blueprints.length ? blueprints : (manifest.tools || []).map((title) => ({ title: repairText(title), key: slug(repairText(title)), fields: [] }));
    state.toolSettingsItem = settings.items?.[0] || null;
    state.disabledBuiltInKeys = [...(state.toolSettingsItem?.definition?.disabledBuiltInKeys || [])];
    renderBuiltInTools();
  } catch (error) { list.innerHTML = `<div class="catalog-empty">${escapeHtml(error.message)}</div>`; }
  await loadCalculators(selectId);
}

function renderBuiltInTools() {
  const query = $("#calculatorConfigSearch").value.trim().toLocaleLowerCase("es-AR");
  const visible = state.builtInTools.filter((tool) => !query || tool.title.toLocaleLowerCase("es-AR").includes(query));
  $("#builtInToolCount").textContent = `${state.builtInTools.length - state.disabledBuiltInKeys.length} de ${state.builtInTools.length} activas`;
  $("#builtInToolList").innerHTML = visible.map((tool) => {
    const override = state.calculators.find((item) => item.definition?.replacesBuiltInKey === tool.key);
    return `<div class="built-in-tool${state.selectedBuiltInKey === tool.key ? " active" : ""}"><input type="checkbox" data-built-in-key="${escapeHtml(tool.key)}" aria-label="Activar ${escapeHtml(tool.title)}"${state.disabledBuiltInKeys.includes(tool.key) ? "" : " checked"}><button type="button" data-built-in-edit="${escapeHtml(tool.key)}"><span>${escapeHtml(tool.title)}</span><small>${override ? `${override.definition?.mode === "score" ? "Score" : override.definition?.mode === "formula" ? "Fórmula" : "Personalizada"} · v${override.revision}` : "Editar o convertir"}</small></button></div>`;
  }).join("") || `<div class="catalog-empty">No hay coincidencias.</div>`;
}

async function saveBuiltInTools() {
  try {
    const disabledBuiltInKeys = [...state.disabledBuiltInKeys];
    const draft = { key: "tools-main", name: "Herramientas incluidas", active: true, expectedRevision: state.toolSettingsItem?.revision, definition: { disabledBuiltInKeys } };
    const exists = isPersistedConfigurationItem(state.toolSettingsItem);
    const path = exists ? `/api/clinical/configuration/tool-settings/${state.toolSettingsItem.id}` : "/api/clinical/configuration/tool-settings";
    const payload = await api(path, { method: exists ? "PUT" : "POST", body: JSON.stringify(draft) });
    state.toolSettingsItem = payload.item; state.disabledBuiltInKeys = disabledBuiltInKeys;
    notifyCalculatorConfigurationUpdated(); renderBuiltInTools(); toast("Herramientas actualizadas");
  } catch (error) { toast(error.message, "error"); }
}

function dayHospitalDraft() {
  return {
    key: "day-hospital-main",
    name: "Agenda principal de Hospital de dia",
    description: "Capacidad, intervalo minimo y jornada del turnero por sillon.",
    active: true,
    expectedRevision: state.dayHospitalSettingsItem?.revision,
    definition: {
      chairCount: Number($("#dayHospitalChairCount").value),
      slotMinutes: Number($("#dayHospitalSlotMinutes").value),
      startTime: $("#dayHospitalStartTime").value,
      endTime: $("#dayHospitalEndTime").value,
    },
  };
}

function dayHospitalTimeInMinutes(value) {
  const match = /^(\d{2}):(\d{2})$/.exec(String(value || ""));
  if (!match) return null;
  return Number(match[1]) * 60 + Number(match[2]);
}

function validateDayHospitalHours({ report = false } = {}) {
  const startInput = $("#dayHospitalStartTime");
  const endInput = $("#dayHospitalEndTime");
  const error = $("#dayHospitalTimeError");
  const saveButton = $("#saveDayHospitalSettingsBtn");
  const startMinutes = dayHospitalTimeInMinutes(startInput.value);
  const endMinutes = dayHospitalTimeInMinutes(endInput.value);
  const complete = startMinutes !== null && endMinutes !== null;
  const valid = complete && endMinutes > startMinutes;
  const message = complete
    ? "El fin de atencion debe ser posterior al inicio."
    : "Complete el horario de inicio y fin.";
  startInput.setCustomValidity(valid ? "" : message);
  endInput.setCustomValidity(valid ? "" : message);
  error.textContent = message;
  error.hidden = valid;
  saveButton.disabled = !valid;
  saveButton.setAttribute("aria-disabled", String(!valid));
  if (report && !valid) endInput.reportValidity();
  return valid;
}

function renderDayHospitalPreview() {
  const draft = dayHospitalDraft().definition;
  if (!validateDayHospitalHours()) {
    $("#schedulePreviewTitle").textContent = "Jornada invalida";
    $("#dayHospitalSchedulePreview").textContent = "Corrija el horario para calcular los casilleros disponibles.";
    $("#dayHospitalPreviewBars").innerHTML = "";
    return;
  }
  const [startHour, startMinute] = draft.startTime.split(":").map(Number);
  const [endHour, endMinute] = draft.endTime.split(":").map(Number);
  const totalMinutes = Math.max(0, endHour * 60 + endMinute - startHour * 60 - startMinute);
  const slots = Math.floor(totalMinutes / Math.max(1, draft.slotMinutes));
  const columnsPerHour = { 5: 4, 10: 3, 15: 2, 20: 3, 30: 2 }[draft.slotMinutes] || 3;
  const rowsPerHour = (60 / draft.slotMinutes) / columnsPerHour;
  $("#schedulePreviewTitle").textContent = `${draft.chairCount} ${draft.chairCount === 1 ? "sillon" : "sillones"}`;
  $("#dayHospitalSchedulePreview").textContent = `${slots} casilleros de ${draft.slotMinutes} minutos por sillon, de ${draft.startTime} a ${draft.endTime}. Cada hora: ${rowsPerHour} ${rowsPerHour === 1 ? "fila" : "filas"} × ${columnsPerHour} columnas.`;
  $("#dayHospitalPreviewBars").innerHTML = Array.from({ length: Math.min(draft.chairCount || 0, 12) }, () => `<i style="--slots:${Math.min(slots, 60)}"></i>`).join("");
}

async function loadDayHospitalSettings() {
  try {
    const payload = await api(`/api/clinical/configuration/day-hospital-settings?includeInactive=1&t=${Date.now()}`);
    const item = payload.items?.[0] || null;
    state.dayHospitalSettingsItem = item;
    const definition = { chairCount: 6, slotMinutes: 10, startTime: "08:00", endTime: "16:00", ...(item?.definition || {}) };
    $("#dayHospitalConfigId").value = item?.id || ""; $("#dayHospitalConfigRevision").value = item?.revision || "";
    $("#dayHospitalChairCount").value = definition.chairCount; $("#dayHospitalSlotMinutes").value = [5, 10, 15, 20, 30].includes(Number(definition.slotMinutes)) ? definition.slotMinutes : 10;
    $("#dayHospitalStartTime").value = definition.startTime; $("#dayHospitalEndTime").value = definition.endTime;
    renderDayHospitalPreview();
  } catch (error) { toast(error.message, "error"); }
}

async function saveDayHospitalSettings(event) {
  event.preventDefault();
  if (!validateDayHospitalHours({ report: true })) return;
  try {
    const draft = dayHospitalDraft();
    const exists = isPersistedConfigurationItem(state.dayHospitalSettingsItem);
    const path = exists ? `/api/clinical/configuration/day-hospital-settings/${state.dayHospitalSettingsItem.id}` : "/api/clinical/configuration/day-hospital-settings";
    const payload = await api(path, { method: exists ? "PUT" : "POST", body: JSON.stringify(draft) });
    state.dayHospitalSettingsItem = payload.item; localStorage.setItem("hcop-configuration-updated", String(Date.now()));
    markConfigurationPanelClean("day-hospital");
    renderDayHospitalPreview(); toast("Agenda de Hospital de dia actualizada");
  } catch (error) { toast(error.message, "error"); }
}

function renderCalculatorList() {
  const query = $("#calculatorConfigSearch").value.trim().toLowerCase(), showInactive = $("#showInactiveCalculators").checked;
  const rows = state.calculators.filter((item) => (showInactive || item.active) && (!query || `${item.name} ${item.description} ${item.definition?.category}`.toLowerCase().includes(query)));
  $("#calculatorConfigList").innerHTML = rows.map((item) => catalogItem(item, item.id === state.selectedCalculator?.id, `${item.definition?.category || "General"} · v${item.revision}`)).join("") || `<div class="catalog-empty">Todavia no hay calculadoras configurables.</div>`;
}

function newCalculator() {
  markConfigurationPanelDirty("calculators");
  state.selectedCalculator = null; renderCalculatorList(); $("#calculatorEmpty").hidden = true; $("#calculatorConfigForm").hidden = false; $("#calculatorConfigForm").reset(); $("#calculatorConfigId").value = ""; $("#calculatorConfigRevision").value = ""; $("#calculatorActive").checked = true; $("#calculatorDecimals").value = "2"; $("#calculatorResultLabel").value = "Resultado"; $("#calculatorEditorTitle").textContent = "Nueva calculadora"; $("#calculatorRevisionLabel").textContent = "Sin guardar"; $("#archiveCalculatorBtn").hidden = true; $("#calculatorVariables").innerHTML = ""; addCalculatorVariable({ label: "Peso", key: "peso", type: "number", unit: "kg", required: true }); addCalculatorVariable({ label: "Altura", key: "altura", type: "number", unit: "cm", required: true }); $("#calculatorExpression").value = "sqrt(peso * altura / 3600)"; renderCalculatorPreview();
}

function editCalculator(item) {
  state.selectedCalculator = item; renderCalculatorList(); $("#calculatorEmpty").hidden = true; $("#calculatorConfigForm").hidden = false; $("#calculatorConfigId").value = item.id; $("#calculatorConfigRevision").value = item.revision; $("#calculatorName").value = item.name; $("#calculatorDescription").value = item.description || item.definition?.clinicalUse || ""; $("#calculatorCategory").value = item.definition?.category || "general"; $("#calculatorSource").value = item.definition?.source || ""; $("#calculatorActive").checked = item.active; $("#calculatorExpression").value = item.definition?.expression || ""; $("#calculatorResultLabel").value = item.definition?.resultLabel || "Resultado"; $("#calculatorResultUnit").value = item.definition?.resultUnit || ""; $("#calculatorDecimals").value = item.definition?.decimals ?? 2; $("#calculatorRanges").value = (item.definition?.ranges || []).map((range) => `${range.min ?? ""} | ${range.max ?? ""} | ${range.label} | ${range.severity}`).join("\n"); $("#calculatorEditorTitle").textContent = item.name; $("#calculatorRevisionLabel").textContent = `Version ${item.revision} · ${item.active ? "activa" : "desactivada"}`; $("#archiveCalculatorBtn").hidden = false; $("#archiveCalculatorBtn").innerHTML = `<i data-lucide="${item.active ? "archive" : "rotate-ccw"}"></i>${item.active ? "Desactivar" : "Reactivar"}`; $("#calculatorVariables").innerHTML = ""; (item.definition?.fields || []).forEach(addCalculatorVariable); renderCalculatorPreview(); icons($("#calculatorConfigForm"));
}

function parseOptions(value) { return String(value || "").split("|").map((part) => part.trim()).filter(Boolean).map((part) => { const split = part.indexOf(":"); return split < 0 ? { value: part, label: part } : { value: part.slice(0, split).trim(), label: part.slice(split + 1).trim() }; }); }
function optionsText(options = []) { return options.map((item) => `${item.value}:${item.label}`).join(" | "); }

function addCalculatorVariable(field = {}) {
  const card = $("#calculatorVariableTemplate").content.firstElementChild.cloneNode(true); card.dataset.type = field.type || "number"; $(".builder-label", card).value = field.label || "Nueva variable"; $(".builder-key", card).value = field.key || slug(field.label || "variable"); $(".builder-type", card).value = field.type || "number"; $(".builder-unit", card).value = field.unit || ""; $(".builder-min", card).value = field.min ?? ""; $(".builder-max", card).value = field.max ?? ""; $(".builder-options", card).value = optionsText(field.options); $(".builder-required", card).checked = field.required !== false; $("#calculatorVariables").append(card); wireBuilderCard(card, renderCalculatorPreview); icons(card); renderCalculatorPreview();
}

function calculatorFields() { return $$(".builder-card", $("#calculatorVariables")).map((card) => ({ label: $(".builder-label", card).value.trim(), key: $(".builder-key", card).value.trim(), type: $(".builder-type", card).value, unit: $(".builder-unit", card).value.trim(), min: $(".builder-min", card).value || null, max: $(".builder-max", card).value || null, required: $(".builder-required", card).checked, options: parseOptions($(".builder-options", card).value) })); }
function parseRanges() { return $("#calculatorRanges").value.split("\n").map((line) => line.split("|").map((part) => part.trim())).filter((parts) => parts[2]).map(([min, max, label, severity]) => ({ min: min === "" ? null : Number(min), max: max === "" ? null : Number(max), label, severity: severity || "info" })); }
function calculatorDraft(active = $("#calculatorActive").checked) { return { name: $("#calculatorName").value.trim(), description: $("#calculatorDescription").value.trim(), active, expectedRevision: $("#calculatorConfigRevision").value || undefined, definition: { category: $("#calculatorCategory").value, source: $("#calculatorSource").value.trim(), clinicalUse: $("#calculatorDescription").value.trim(), fields: calculatorFields(), expression: $("#calculatorExpression").value.trim(), resultLabel: $("#calculatorResultLabel").value.trim(), resultUnit: $("#calculatorResultUnit").value.trim(), decimals: Number($("#calculatorDecimals").value), ranges: parseRanges() } }; }

async function saveCalculator(event, forcedActive) { event?.preventDefault(); try { const draft = calculatorDraft(forcedActive ?? $("#calculatorActive").checked); if (!draft.name) throw new Error("Escriba el nombre de la calculadora."); window.SafeExpression.evaluate(draft.definition.expression, Object.fromEntries(draft.definition.fields.map((field) => [field.key, 1]))); const id = $("#calculatorConfigId").value; const payload = id ? await api(`/api/clinical/configuration/calculator/${id}`, { method: "PUT", body: JSON.stringify(draft) }) : await api("/api/clinical/configuration/calculator", { method: "POST", body: JSON.stringify(draft) }); markConfigurationPanelClean("calculators"); toast(id ? "Calculadora actualizada" : "Calculadora creada"); notifyCalculatorConfigurationUpdated(); await loadCalculators(payload.item.id); }
  catch (error) { toast(error.message, "error"); } }

function renderCalculatorPreview() {
  const preview = $("#calculatorPreview"), fields = calculatorFields(); if (!preview) return;
  const controls = fields.map((field) => field.type === "select" ? `<label><small>${escapeHtml(field.label)}</small><select data-preview-key="${escapeHtml(field.key)}">${field.options.map((option) => `<option value="${escapeHtml(option.value)}">${escapeHtml(option.label)}</option>`).join("")}</select></label>` : field.type === "checkbox" ? `<label><small>${escapeHtml(field.label)}</small><input data-preview-key="${escapeHtml(field.key)}" type="checkbox"></label>` : `<label><small>${escapeHtml(field.label)}</small><input data-preview-key="${escapeHtml(field.key)}" type="number" value="1" step="any"></label>`).join("");
  preview.innerHTML = `<span>Vista previa en vivo</span><div class="field-grid">${controls}</div><strong data-preview-result>Complete la formula.</strong><small data-preview-detail></small>`;
  $$('[data-preview-key]', preview).forEach((input) => input.addEventListener("input", calculatePreview)); calculatePreview();
}
function calculatePreview() { const preview = $("#calculatorPreview"), resultNode = $("[data-preview-result]", preview), detail = $("[data-preview-detail]", preview); if (!resultNode) return; try { const values = Object.fromEntries($$("[data-preview-key]", preview).map((input) => [input.dataset.previewKey, input.type === "checkbox" ? input.checked : input.value])); const value = window.SafeExpression.evaluate($("#calculatorExpression").value, values); const decimals = Math.max(0, Math.min(6, Number($("#calculatorDecimals").value) || 0)); const range = parseRanges().find((item) => (item.min == null || value >= item.min) && (item.max == null || value <= item.max)); resultNode.textContent = `${$("#calculatorResultLabel").value || "Resultado"}: ${value.toFixed(decimals)} ${$("#calculatorResultUnit").value}`.trim(); detail.textContent = range?.label || ""; }
  catch (error) { resultNode.textContent = "Formula pendiente"; detail.textContent = error.message; } }

async function loadResearchForms(selectId = "") { const list = $("#researchConfigList"); loading(list); try { const payload = await api(`/api/clinical/configuration/research-form?includeInactive=1&t=${Date.now()}`); state.researchForms = payload.items || []; renderResearchList(); const selected = state.researchForms.find((item) => item.id === String(selectId)) || state.researchForms.find((item) => item.id === state.selectedResearch?.id); if (selected) editResearchForm(selected); } catch (error) { list.innerHTML = `<div class="catalog-empty">${escapeHtml(error.message)}</div>`; } }
function renderResearchList() { const query = $("#researchConfigSearch").value.trim().toLowerCase(), showInactive = $("#showInactiveResearch").checked; const rows = state.researchForms.filter((item) => (showInactive || item.active) && (!query || `${item.name} ${item.definition?.category}`.toLowerCase().includes(query))); $("#researchConfigList").innerHTML = rows.map((item) => catalogItem(item, item.id === state.selectedResearch?.id, `${(item.definition?.fields || []).filter((field) => field.type !== "section").length} campos · v${item.revision}`)).join("") || `<div class="catalog-empty">Todavia no hay formularios personalizados.</div>`; }
function newResearchForm() { markConfigurationPanelDirty("research"); state.selectedResearch = null; renderResearchList(); $("#researchEmpty").hidden = true; $("#researchConfigForm").hidden = false; $("#researchConfigForm").reset(); $("#researchConfigId").value = ""; $("#researchConfigRevision").value = ""; $("#researchFormActive").checked = true; $("#researchFormCategory").value = "Investigacion"; $("#researchEditorTitle").textContent = "Nuevo formulario"; $("#researchRevisionLabel").textContent = "Sin guardar"; $("#archiveResearchBtn").hidden = true; $("#researchFormFields").innerHTML = ""; addResearchField({ type: "section", label: "Datos del evento", key: "datos_evento" }); addResearchField({ type: "date", label: "Fecha del evento", key: "fecha_evento", required: true }); addResearchField({ type: "text", label: "Codigo del participante", key: "codigo_participante", required: true }); }
function editResearchForm(item) { state.selectedResearch = item; renderResearchList(); $("#researchEmpty").hidden = true; $("#researchConfigForm").hidden = false; $("#researchConfigId").value = item.id; $("#researchConfigRevision").value = item.revision; $("#researchFormName").value = item.name; $("#researchFormCategory").value = item.definition?.category || "Investigacion"; $("#researchFormInstructions").value = item.definition?.instructions || item.description || ""; $("#researchFormActive").checked = item.active; $("#researchEditorTitle").textContent = item.name; $("#researchRevisionLabel").textContent = `Version ${item.revision} · ${item.active ? "activo" : "desactivado"}`; $("#archiveResearchBtn").hidden = false; $("#archiveResearchBtn").innerHTML = `<i data-lucide="${item.active ? "archive" : "rotate-ccw"}"></i>${item.active ? "Desactivar" : "Reactivar"}`; $("#researchFormFields").innerHTML = ""; (item.definition?.fields || []).forEach(addResearchField); icons($("#researchConfigForm")); }
function addResearchField(field = {}) { const card = $("#researchFieldTemplate").content.firstElementChild.cloneNode(true); card.dataset.type = field.type || "text"; $(".builder-label", card).value = field.label || "Nuevo campo"; $(".builder-key", card).value = field.key || slug(field.label || "campo"); $(".builder-type", card).value = field.type || "text"; $(".builder-placeholder", card).value = field.placeholder || field.help || ""; $(".builder-options", card).value = optionsText(field.options); $(".builder-required", card).checked = Boolean(field.required); $("#researchFormFields").append(card); wireBuilderCard(card); icons(card); }
function researchFields() { return $$(".builder-card", $("#researchFormFields")).map((card) => ({ label: $(".builder-label", card).value.trim(), key: $(".builder-key", card).value.trim(), type: $(".builder-type", card).value, placeholder: $(".builder-placeholder", card).value.trim(), required: $(".builder-required", card).checked, options: parseOptions($(".builder-options", card).value) })); }
function researchDraft(active = $("#researchFormActive").checked) { return { name: $("#researchFormName").value.trim(), description: $("#researchFormInstructions").value.trim(), active, expectedRevision: $("#researchConfigRevision").value || undefined, definition: { category: $("#researchFormCategory").value.trim(), instructions: $("#researchFormInstructions").value.trim(), fields: researchFields() } }; }
async function saveResearchForm(event, forcedActive) { event?.preventDefault(); try { const draft = researchDraft(forcedActive ?? $("#researchFormActive").checked); if (!draft.name) throw new Error("Escriba el nombre del formulario."); const id = $("#researchConfigId").value; const payload = id ? await api(`/api/clinical/configuration/research-form/${id}`, { method: "PUT", body: JSON.stringify(draft) }) : await api("/api/clinical/configuration/research-form", { method: "POST", body: JSON.stringify(draft) }); markConfigurationPanelClean("research"); toast(id ? "Formulario actualizado" : "Formulario creado"); localStorage.setItem("hcop-configuration-updated", String(Date.now())); await loadResearchForms(payload.item.id); } catch (error) { toast(error.message, "error"); } }

function inferLlmProvider(config) {
  const provider = String(config?.provider || "").trim().toLowerCase();
  const endpoint = String(config?.baseUrl || config?.endpoint || "").toLowerCase();
  if (endpoint.includes("generativelanguage.googleapis.com")) return "gemini";
  if (endpoint.includes(":1234")) return "lm-studio";
  if (endpoint.includes(":11434")) return "ollama";
  if (["gemini", "openai-compatible", "lm-studio", "ollama"].includes(provider)) return provider;
  return "openai-compatible";
}

function selectedLlmApiKeyAction() {
  return $('input[name="llmApiKeyAction"]:checked')?.value || "keep";
}

function setLlmConnectionResult(message = "", kind = "") {
  const node = $("#llmConnectionResult");
  node.textContent = message;
  node.className = `llm-connection-result${kind ? ` is-${kind}` : ""}`;
}

function setLlmBusy(busy, target = "") {
  const form = $("#llmConfigurationForm");
  form.setAttribute("aria-busy", String(Boolean(busy)));
  $("#testLlmConnectionBtn").disabled = Boolean(busy);
  $("#saveLlmConfigurationBtn").disabled = Boolean(busy);
  if (busy && target) setLlmConnectionResult(target, "loading");
}

function updateLlmKeyControls() {
  const action = selectedLlmApiKeyAction();
  const row = $("#llmApiKeyInputRow");
  const input = $("#llmConfigApiKey");
  row.hidden = action !== "replace";
  input.required = action === "replace";
  if (action !== "replace") input.value = "";
}

function isLocalLlmEndpoint(value) {
  try {
    const hostname = new URL(String(value || "")).hostname.toLowerCase();
    return ["127.0.0.1", "localhost", "::1"].includes(hostname);
  } catch {
    return false;
  }
}

function updateLlmEnvironmentNotice() {
  const notice = $("#llmEnvironmentNotice");
  const local = isLocalLlmEndpoint($("#llmConfigEndpoint").value);
  notice.classList.toggle("is-cloud", !local);
  notice.innerHTML = local
    ? `<i data-lucide="shield-check"></i><div><strong>Conexión local</strong><span>Los datos se enviarán solamente al servicio instalado en este equipo.</span></div>`
    : `<i data-lucide="cloud-alert"></i><div><strong>Servicio en la nube</strong><span>Las solicitudes y la información incluida se enviarán a un proveedor externo.</span></div>`;
  icons(notice);
}

function llmDraft() {
  const action = selectedLlmApiKeyAction();
  const draft = {
    enabled: $("#llmConfigEnabled").checked,
    provider: $("#llmConfigProvider").value,
    baseUrl: $("#llmConfigEndpoint").value.trim(),
    model: $("#llmConfigModel").value.trim(),
    apiKeyAction: action
  };
  if (action === "replace") draft.apiKey = $("#llmConfigApiKey").value.trim();
  return draft;
}

function validateLlmDraft(draft) {
  if (!draft.baseUrl) throw new Error("Complete el endpoint del servicio LLM.");
  try {
    const endpoint = new URL(draft.baseUrl);
    if (!["http:", "https:"].includes(endpoint.protocol)) throw new Error();
  } catch {
    throw new Error("El endpoint debe ser una dirección HTTP o HTTPS válida.");
  }
  if (!draft.model) throw new Error("Complete el nombre del modelo.");
  if (draft.apiKeyAction === "replace" && !draft.apiKey) throw new Error("Pegue la nueva API key o elija Conservar.");
}

async function loadLlmConfiguration() {
  setLlmBusy(true, "Cargando la configuración del servicio…");
  try {
    const payload = await api(`/api/config?t=${Date.now()}`);
    const config = payload.llm || {};
    state.llmConfig = config;
    $("#llmConfigEnabled").checked = Boolean(config.enabled);
    $("#llmConfigProvider").value = inferLlmProvider(config);
    $("#llmConfigEndpoint").value = config.baseUrl || config.endpoint || "";
    $("#llmConfigModel").value = config.model || "";
    $("#llmConfigApiKey").value = "";
    const lockedFields = new Set(Array.isArray(config.lockedFields) ? config.lockedFields : []);
    $("#llmConfigEndpoint").readOnly = lockedFields.has("baseUrl");
    $("#llmConfigModel").readOnly = lockedFields.has("model");
    $("#llmConfigProvider").disabled = lockedFields.has("baseUrl") || lockedFields.has("model");
    $$("[data-llm-preset]").forEach((button) => {
      button.disabled = lockedFields.has("baseUrl") || lockedFields.has("model");
    });
    $$('input[name="llmApiKeyAction"]').forEach((input) => {
      input.disabled = lockedFields.has("apiKey");
    });
    $('input[name="llmApiKeyAction"][value="keep"]').checked = true;
    const hasApiKey = Boolean(config.hasApiKey || config.apiKeyConfigured || config.keyStatus === "configured");
    $("#llmApiKeyBadge").textContent = lockedFields.has("apiKey")
      ? "Clave definida por entorno"
      : hasApiKey
        ? "Clave configurada"
        : "Sin clave guardada";
    $("#llmApiKeyBadge").classList.toggle("is-configured", hasApiKey);
    $("#llmApiKeyStatus").textContent = lockedFields.has("apiKey")
      ? "La clave proviene de una variable de entorno y no puede modificarse desde esta pantalla."
      : hasApiKey
        ? "Hay una clave privada guardada. Por seguridad, su valor no se muestra."
        : "No hay una clave privada guardada para este proveedor.";
    updateLlmKeyControls();
    updateLlmEnvironmentNotice();
    setLlmConnectionResult(
      lockedFields.size
        ? `Configuración cargada. ${lockedFields.size} campo(s) están definidos por variables de entorno.`
        : "Configuración cargada. Puede probarla o modificarla.",
      "ready"
    );
  } catch (error) {
    setLlmConnectionResult(error.message, "error");
  } finally {
    setLlmBusy(false);
  }
}

function applyLlmPreset(name) {
  const preset = LLM_PRESETS[name];
  if (!preset) return;
  $("#llmConfigProvider").value = preset.provider;
  $("#llmConfigEndpoint").value = preset.baseUrl;
  $("#llmConfigModel").value = preset.model;
  updateLlmEnvironmentNotice();
  setLlmConnectionResult(`Configuración rápida ${name === "gemini" ? "Gemini actual" : name === "lm-studio" ? "LM Studio" : "Ollama"} aplicada. Falta guardar.`, "pending");
  $("#llmConfigEndpoint").focus();
}

async function testLlmConnection() {
  try {
    const draft = llmDraft();
    validateLlmDraft(draft);
    setLlmBusy(true, "Probando la conexión con el modelo…");
    const payload = await api("/api/llm/test", {
      method: "POST",
      body: JSON.stringify({ llm: draft })
    });
    const detail = [payload.model, payload.response || payload.message].filter(Boolean).join(" · ");
    setLlmConnectionResult(`Conexión correcta${detail ? `: ${detail}` : "."}`, "success");
  } catch (error) {
    setLlmConnectionResult(`No se pudo conectar: ${error.message}`, "error");
  } finally {
    setLlmBusy(false);
  }
}

async function saveLlmConfiguration(event) {
  event.preventDefault();
  try {
    const draft = llmDraft();
    validateLlmDraft(draft);
    setLlmBusy(true, "Guardando la configuración…");
    await api("/api/config", { method: "PUT", body: JSON.stringify({ llm: draft }) });
    $("#llmConfigApiKey").value = "";
    toast("Configuración de inteligencia artificial guardada");
    notifyConfigurationUpdated();
    markConfigurationPanelClean("artificial-intelligence");
    await loadLlmConfiguration();
  } catch (error) {
    setLlmConnectionResult(error.message, "error");
    toast(error.message, "error");
  } finally {
    setLlmBusy(false);
  }
}

function accessArray(payload, ...keys) {
  if (Array.isArray(payload)) return payload;
  for (const key of keys) {
    if (Array.isArray(payload?.[key])) return payload[key];
    if (Array.isArray(payload?.data?.[key])) return payload.data[key];
  }
  if (Array.isArray(payload?.items)) return payload.items;
  if (Array.isArray(payload?.data?.items)) return payload.data.items;
  return [];
}

function accessBoolean(value, fallback = false) {
  if (typeof value === "boolean") return value;
  if (value === 1 || value === "1" || String(value).toLowerCase() === "true") return true;
  if (value === 0 || value === "0" || String(value).toLowerCase() === "false") return false;
  return fallback;
}

function normalizeAdminRole(raw = {}) {
  return {
    ...raw,
    id: String(raw.id ?? raw.roleId ?? raw.role_id ?? raw.key ?? ""),
    key: String(raw.key ?? raw.roleKey ?? raw.code ?? ""),
    name: repairText(raw.name ?? raw.displayName ?? raw.label ?? raw.key ?? "Rol"),
    description: repairText(raw.description ?? raw.summary ?? ""),
    system: accessBoolean(raw.system ?? raw.isSystem, false),
    active: accessBoolean(raw.active ?? raw.enabled, true),
    userCount: Number(raw.userCount ?? raw.usersCount ?? raw.assignedUsers ?? 0) || 0,
    permissions: accessArray(raw, "permissions", "permissionKeys")
      .map((permission) => String(permission?.key ?? permission?.permission ?? permission ?? "").trim())
      .filter(Boolean),
  };
}

function normalizeAdminUser(raw = {}) {
  const rawRoles = accessArray(raw, "roles", "roleIds", "assignedRoles");
  const singleRole = raw.role ?? raw.roleId ?? raw.role_id;
  const roles = rawRoles.length ? rawRoles : singleRole == null ? [] : [singleRole];
  const username = String(raw.username ?? raw.login ?? raw.email ?? "").trim();
  const email = String(raw.email ?? "").trim();
  return {
    ...raw,
    id: String(raw.id ?? raw.userId ?? raw.user_id ?? ""),
    username,
    email,
    displayName: repairText(raw.displayName ?? raw.fullName ?? raw.name ?? raw.nombre ?? (username || email || "Usuario")),
    specialty: repairText(raw.specialty ?? raw.especialidad ?? ""),
    licenseNumber: String(raw.licenseNumber ?? raw.license ?? raw.matricula ?? "").trim(),
    active: accessBoolean(raw.active ?? raw.enabled, true),
    lastLoginAt: raw.lastLoginAt ?? raw.last_login_at ?? raw.lastAccessAt ?? "",
    roles: roles.map((role) => {
      if (role && typeof role === "object") return normalizeAdminRole(role);
      const id = String(role ?? "");
      return state.access.roles.find((item) => item.id === id) || { id, key: "", name: id };
    }).filter((role) => role.id || role.key),
  };
}

function normalizeAccessMe(payload = {}) {
  const raw = payload.user ?? payload.me ?? payload.actor ?? payload.data?.user ?? payload;
  const roles = accessArray(raw, "roles").map(normalizeAdminRole);
  const permissions = [
    ...accessArray(raw, "permissions", "permissionKeys"),
    ...accessArray(payload, "permissions", "permissionKeys"),
  ].map((permission) => String(permission?.key ?? permission?.permission ?? permission ?? "").trim()).filter(Boolean);
  return {
    authenticated: accessBoolean(
      payload.authenticated ?? raw.authenticated,
      Boolean(raw.id ?? raw.userId ?? raw.username ?? raw.email)
    ),
    loginRequired: accessBoolean(payload.loginRequired ?? payload.security?.loginRequired, true),
    autoLoginEnabled: accessBoolean(payload.autoLoginEnabled ?? payload.security?.autoLoginEnabled, false),
    user: normalizeAdminUser({ ...raw, roles }),
    permissions: [...new Set(permissions)],
  };
}

function hasAccessPermission(permission) {
  const permissions = new Set(state.access.me?.permissions || []);
  if (permissions.has("*") || permissions.has(permission)) return true;
  const parts = String(permission).split(".");
  while (parts.length > 1) {
    parts.pop();
    if (permissions.has(`${parts.join(".")}.*`)) return true;
  }
  return false;
}

function accessCanManage(kind) {
  return hasAccessPermission(ACCESS_PERMISSIONS[kind] || `admin.manage-${kind}`);
}

function setAccessControlStatus(message = "", kind = "") {
  const output = $("#accessControlStatus");
  if (!output) return;
  output.textContent = message;
  output.className = kind ? `is-${kind}` : "";
}

function renderAccessDenied(message) {
  $("#accessControlWorkspace").hidden = true;
  $("#accessControlDenied").hidden = false;
  $("#accessControlDeniedMessage").textContent = message ||
    "Su usuario no tiene permiso para administrar usuarios, roles o seguridad.";
  setAccessControlStatus("");
  icons($("#accessControlDenied"));
}

function availableAccessView(preferred = state.access.activeView) {
  const permissions = {
    users: accessCanManage("users"),
    roles: accessCanManage("roles"),
    security: accessCanManage("security"),
  };
  return permissions[preferred]
    ? preferred
    : ["users", "roles", "security"].find((view) => permissions[view]) || "";
}

function setAccessAdminView(view) {
  const allowed = availableAccessView(view);
  if (!allowed) {
    renderAccessDenied("Su cuenta puede abrir Configuración, pero no tiene permisos administrativos.");
    return;
  }
  state.access.activeView = allowed;
  $$("[data-access-admin-tab]").forEach((button) => {
    const canOpen = accessCanManage(button.dataset.accessAdminTab);
    const active = canOpen && button.dataset.accessAdminTab === allowed;
    button.disabled = !canOpen;
    button.classList.toggle("active", active);
    button.setAttribute("aria-selected", String(active));
    button.title = canOpen ? "" : "Su rol no incluye este permiso administrativo";
  });
  $$("[data-access-admin-panel]").forEach((panel) => {
    const active = panel.dataset.accessAdminPanel === allowed;
    panel.classList.toggle("active", active);
    panel.hidden = !active;
  });
  if (allowed === "users") {
    renderAdminUserList();
    populateAdminUserRoleOptions();
  } else if (allowed === "roles") {
    renderAdminRoleList();
  } else {
    renderAdminSecuritySettings();
  }
  icons($("#accessControlWorkspace"));
}

function accessRoleLabel(role) {
  return role?.name || role?.key || "Sin rol";
}

function accessUserRoleLabel(user) {
  const names = (user?.roles || []).map(accessRoleLabel).filter(Boolean);
  return names.length ? names.join(", ") : "Sin rol";
}

function reconcileAdminUserRoles() {
  state.access.users.forEach((user) => {
    user.roles = (user.roles || []).map((assignedRole) =>
      state.access.roles.find((role) =>
        role.id === assignedRole.id || (assignedRole.key && role.key === assignedRole.key)
      ) || assignedRole);
  });
}

function accessDateLabel(value) {
  if (!value) return "Nunca";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString("es-AR", {
    day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit",
  });
}

function renderAdminUserList() {
  const list = $("#adminUserList");
  if (!list) return;
  const query = $("#adminUserSearch")?.value.trim().toLocaleLowerCase("es-AR") || "";
  const showInactive = Boolean($("#showInactiveAdminUsers")?.checked);
  const rows = state.access.users.filter((user) =>
    (showInactive || user.active) &&
    (!query || [
      user.displayName, user.username, user.email, user.licenseNumber, user.specialty, accessUserRoleLabel(user),
    ].join(" ").toLocaleLowerCase("es-AR").includes(query)));
  $("#adminUserCount").textContent = `${rows.length} ${rows.length === 1 ? "registrado" : "registrados"}`;
  list.innerHTML = rows.map((user) => `
    <button class="access-item${state.access.selectedUserId === user.id ? " active" : ""}${user.active ? "" : " is-inactive"}"
      type="button" role="listitem" data-admin-user-id="${escapeHtml(user.id)}" aria-pressed="${state.access.selectedUserId === user.id}">
      <span><strong>${escapeHtml(user.displayName)}</strong><small>${escapeHtml(user.username)} · ${escapeHtml(accessUserRoleLabel(user))}</small></span>
      <em>${user.active ? "Activo" : "Inactivo"}</em>
    </button>`).join("") || `<div class="catalog-empty">No hay usuarios que coincidan.</div>`;
}

function adminUserSelectedRoleIds() {
  return $$("[data-admin-user-role]:checked", $("#adminUserRoleOptions"))
    .map((input) => String(input.value || "").trim())
    .filter(Boolean);
}

function populateAdminUserRoleOptions(selectedIds = adminUserSelectedRoleIds()) {
  const output = $("#adminUserRoleOptions");
  if (!output) return;
  const selected = new Set((Array.isArray(selectedIds) ? selectedIds : [selectedIds])
    .map((id) => String(id || "").trim()).filter(Boolean));
  const roles = state.access.roles.filter((role) => role.active || selected.has(role.id));
  output.innerHTML = roles.map((role) => `
    <label class="admin-user-role-option">
      <input type="checkbox" value="${escapeHtml(role.id)}" data-admin-user-role${selected.has(role.id) ? " checked" : ""}>
      <span><b>${escapeHtml(role.name)}</b><small>${escapeHtml(role.description || role.key || "Rol configurado")}${role.active ? "" : " · Inactivo"}</small></span>
    </label>`).join("") || `<div class="catalog-empty">No hay roles disponibles. Cree o active un rol primero.</div>`;
}

function resetAdminUserEditor() {
  state.access.selectedUserId = "";
  $("#adminUserForm").reset();
  $("#adminUserId").value = "";
  $("#adminUserActive").checked = true;
  $("#adminUserTemporaryPassword").required = true;
  $("#adminUserTemporaryPassword").value = "";
  $("#adminUserEditorTitle").textContent = "Nuevo usuario";
  $("#adminUserEditorMeta").textContent = "Complete los datos y entregue la clave temporal por un canal seguro.";
  populateAdminUserRoleOptions([]);
  $("#adminUserEmpty").hidden = true;
  $("#adminUserForm").hidden = false;
  renderAdminUserList();
  $("#adminUserDisplayName").focus();
}

function editAdminUser(userId) {
  const user = state.access.users.find((item) => item.id === String(userId));
  if (!user) return;
  state.access.selectedUserId = user.id;
  $("#adminUserId").value = user.id;
  $("#adminUserDisplayName").value = user.displayName;
  $("#adminUserUsername").value = user.username;
  $("#adminUserEmail").value = user.email;
  $("#adminUserLicenseNumber").value = user.licenseNumber;
  $("#adminUserSpecialty").value = user.specialty;
  $("#adminUserActive").checked = user.active;
  $("#adminUserTemporaryPassword").value = "";
  $("#adminUserTemporaryPassword").required = false;
  populateAdminUserRoleOptions(user.roles.map((role) => role.id));
  $("#adminUserEditorTitle").textContent = user.displayName;
  $("#adminUserEditorMeta").textContent = `Último ingreso: ${accessDateLabel(user.lastLoginAt)}. La clave actual no se muestra.`;
  $("#adminUserEmpty").hidden = true;
  $("#adminUserForm").hidden = false;
  renderAdminUserList();
  icons($("#adminUserForm"));
}

async function loadAdminUsers() {
  const list = $("#adminUserList");
  if (list) loading(list);
  const payload = await api(`/api/admin/users?t=${Date.now()}`);
  state.access.users = accessArray(payload, "items", "users").map(normalizeAdminUser);
  const knownRoleIds = new Set(state.access.roles.map((role) => role.id));
  for (const role of state.access.users.flatMap((user) => user.roles || [])) {
    if (role.id && !knownRoleIds.has(role.id)) {
      state.access.roles.push(normalizeAdminRole(role));
      knownRoleIds.add(role.id);
    }
  }
  renderAdminUserList();
  populateAdminUserRoleOptions();
  const selected = state.access.users.find((user) => user.id === state.access.selectedUserId);
  if (selected) editAdminUser(selected.id);
}

async function saveAdminUser(event) {
  event.preventDefault();
  if (!accessCanManage("users")) return renderAccessDenied("Su rol no permite modificar usuarios.");
  const form = event.currentTarget;
  if (!form.reportValidity()) return;
  const id = $("#adminUserId").value;
  const password = $("#adminUserTemporaryPassword").value;
  if (!id && !password) {
    $("#adminUserTemporaryPassword").setCustomValidity("Defina una clave temporal para el nuevo usuario.");
    $("#adminUserTemporaryPassword").reportValidity();
    $("#adminUserTemporaryPassword").setCustomValidity("");
    return;
  }
  const body = {
    username: $("#adminUserUsername").value.trim(),
    email: $("#adminUserEmail").value.trim(),
    displayName: $("#adminUserDisplayName").value.trim(),
    specialty: $("#adminUserSpecialty").value.trim(),
    licenseNumber: $("#adminUserLicenseNumber").value.trim(),
    active: $("#adminUserActive").checked,
    roleIds: [...adminUserSelectedRoleIds()],
    ...(password ? { password } : {}),
  };
  if (!body.roleIds.length) {
    toast("Seleccione al menos un rol para el usuario.", "error");
    $("#adminUserRoleOptions")?.focus();
    return;
  }
  form.setAttribute("aria-busy", "true");
  $$("button,input,select", form).forEach((control) => { control.disabled = true; });
  try {
    const payload = await api(id ? `/api/admin/users/${encodeURIComponent(id)}` : "/api/admin/users", {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(body),
    });
    const saved = normalizeAdminUser(payload.item ?? payload.user ?? payload.data?.item ?? { ...body, id });
    $("#adminUserTemporaryPassword").value = "";
    toast(id ? "Usuario actualizado" : "Usuario creado");
    notifyConfigurationUpdated();
    markConfigurationPanelClean("access-control");
    state.access.selectedUserId = saved.id || id;
    await loadAdminUsers();
  } catch (error) {
    toast(error.message, "error");
  } finally {
    form.removeAttribute("aria-busy");
    $$("button,input,select", form).forEach((control) => { control.disabled = false; });
    $("#adminUserTemporaryPassword").required = !$("#adminUserId").value;
  }
}

function renderAdminRoleList() {
  const list = $("#adminRoleList");
  if (!list) return;
  const query = $("#adminRoleSearch")?.value.trim().toLocaleLowerCase("es-AR") || "";
  const showInactive = Boolean($("#showInactiveAdminRoles")?.checked);
  const rows = state.access.roles.filter((role) =>
    (showInactive || role.active) &&
    (!query || [role.name, role.key, role.description].join(" ").toLocaleLowerCase("es-AR").includes(query)));
  $("#adminRoleCount").textContent = `${rows.length} ${rows.length === 1 ? "configurado" : "configurados"}`;
  list.innerHTML = rows.map((role) => `
    <button class="access-item${state.access.selectedRoleId === role.id ? " active" : ""}${role.active ? "" : " is-inactive"}"
      type="button" role="listitem" data-admin-role-id="${escapeHtml(role.id)}" aria-pressed="${state.access.selectedRoleId === role.id}">
      <span><strong>${escapeHtml(role.name)}</strong><small>${escapeHtml(role.key || "Sin código")} · ${role.userCount} usuario(s)</small></span>
      <em>${role.system ? "Sistema" : role.active ? "Activo" : "Inactivo"}</em>
    </button>`).join("") || `<div class="catalog-empty">No hay roles que coincidan.</div>`;
}

function permissionHumanLabel(value) {
  const labels = {
    view: "Ver", read: "Ver", manage: "Administrar", write: "Crear y modificar",
    create: "Crear", update: "Modificar", edit: "Modificar", delete: "Eliminar",
    archive: "Desactivar", export: "Exportar", prescribe: "Prescribir",
    schedule: "Dar turnos", administer: "Administrar", approve: "Aprobar",
    "manage-users": "Gestionar usuarios", "manage-roles": "Gestionar roles",
    "manage-security": "Gestionar seguridad",
  };
  return labels[value] || String(value || "").replace(/[-_]+/g, " ").replace(/^\w/, (letter) => letter.toLocaleUpperCase("es-AR"));
}

function permissionGroupDescriptor(permission) {
  const key = String(permission?.key ?? permission?.permission ?? permission ?? "");
  const explicitGroup = repairText(permission?.groupLabel ?? permission?.group ?? permission?.sectionLabel ?? "");
  const explicitAction = repairText(permission?.actionLabel ?? permission?.label ?? "");
  const parts = key.split(".").filter(Boolean);
  const action = parts.pop() || key;
  const namespace = parts.shift() || "general";
  const subject = parts.join(".");
  const groupNames = {
    admin: "Administración",
    section: subject ? `Sección · ${permissionHumanLabel(subject)}` : "Secciones",
    patient: "Pacientes",
    clinical: "Historia clínica",
    treatment: "Tratamientos",
    dayhospital: "Hospital de día",
    research: "Investigación",
    configuration: "Configuración",
  };
  return {
    key,
    groupKey: explicitGroup || (namespace === "section" ? `${namespace}.${subject}` : namespace),
    groupLabel: explicitGroup || groupNames[namespace] || permissionHumanLabel(namespace),
    actionLabel: explicitAction || permissionHumanLabel(action),
  };
}

function renderRolePermissionMatrix(selectedPermissions = []) {
  const output = $("#rolePermissionMatrix");
  if (!output) return;
  const selected = new Set(selectedPermissions.map(String));
  const catalog = state.access.permissionCatalog
    .map(permissionGroupDescriptor)
    .filter((permission) => permission.key);
  const grouped = new Map();
  for (const permission of catalog) {
    if (!grouped.has(permission.groupKey)) grouped.set(permission.groupKey, {
      label: permission.groupLabel,
      permissions: [],
    });
    grouped.get(permission.groupKey).permissions.push(permission);
  }
  output.innerHTML = [...grouped.values()].map((group) => `
    <section class="permission-group">
      <header><strong>${escapeHtml(group.label)}</strong><small>${group.permissions.length} ${group.permissions.length === 1 ? "acción" : "acciones"}</small></header>
      <div class="permission-group-list">${group.permissions.map((permission) => `
        <label class="permission-option" title="${escapeHtml(permission.key)}">
          <input type="checkbox" value="${escapeHtml(permission.key)}" data-admin-role-permission${selected.has(permission.key) ? " checked" : ""}>
          <span><b>${escapeHtml(permission.actionLabel)}</b><small>${escapeHtml(permission.key)}</small></span>
        </label>`).join("")}</div>
    </section>`).join("") || `<div class="catalog-empty">El servidor no informó permisos configurables.</div>`;
}

function resetAdminRoleEditor() {
  state.access.selectedRoleId = "";
  $("#adminRoleForm").reset();
  $("#adminRoleId").value = "";
  $("#adminRoleKey").readOnly = false;
  delete $("#adminRoleKey").dataset.touched;
  $("#adminRoleActive").checked = true;
  $("#adminRoleEditorTitle").textContent = "Nuevo rol";
  $("#adminRoleEditorMeta").textContent = "Defina el alcance por sección y acción.";
  renderRolePermissionMatrix([]);
  $("#adminRoleEmpty").hidden = true;
  $("#adminRoleForm").hidden = false;
  renderAdminRoleList();
  $("#adminRoleName").focus();
}

function editAdminRole(roleId) {
  const role = state.access.roles.find((item) => item.id === String(roleId));
  if (!role) return;
  state.access.selectedRoleId = role.id;
  $("#adminRoleId").value = role.id;
  $("#adminRoleName").value = role.name;
  $("#adminRoleKey").value = role.key;
  $("#adminRoleKey").readOnly = true;
  $("#adminRoleKey").dataset.touched = "1";
  $("#adminRoleDescription").value = role.description;
  $("#adminRoleActive").checked = role.active;
  $("#adminRoleEditorTitle").textContent = role.name;
  $("#adminRoleEditorMeta").textContent = role.system
    ? `Rol protegido del sistema · ${role.userCount} usuario(s)`
    : `${role.userCount} usuario(s) asignado(s)`;
  renderRolePermissionMatrix(role.permissions);
  $("#adminRoleEmpty").hidden = true;
  $("#adminRoleForm").hidden = false;
  renderAdminRoleList();
  icons($("#adminRoleForm"));
}

async function loadAdminRoles() {
  const list = $("#adminRoleList");
  if (list && accessCanManage("roles")) loading(list);
  const payload = await api(`/api/admin/roles?t=${Date.now()}`);
  state.access.roles = accessArray(payload, "items", "roles").map(normalizeAdminRole);
  const suppliedCatalog = accessArray(payload, "permissionCatalog", "availablePermissions", "permissions");
  const catalog = suppliedCatalog.length
    ? suppliedCatalog
    : [...new Set(state.access.roles.flatMap((role) => role.permissions))];
  state.access.permissionCatalog = catalog;
  renderAdminRoleList();
  populateAdminUserRoleOptions();
  const selected = state.access.roles.find((role) => role.id === state.access.selectedRoleId);
  if (selected) editAdminRole(selected.id);
}

async function saveAdminRole(event) {
  event.preventDefault();
  if (!accessCanManage("roles")) return renderAccessDenied("Su rol no permite modificar roles.");
  const form = event.currentTarget;
  if (!form.reportValidity()) return;
  const id = $("#adminRoleId").value;
  const roleKey = $("#adminRoleKey").value.trim() || slug($("#adminRoleName").value).replace(/_/g, "-");
  const body = {
    name: $("#adminRoleName").value.trim(),
    description: $("#adminRoleDescription").value.trim(),
    active: $("#adminRoleActive").checked,
    permissions: $$("[data-admin-role-permission]:checked", $("#rolePermissionMatrix")).map((input) => input.value),
    ...(!id ? { key: roleKey } : {}),
  };
  form.setAttribute("aria-busy", "true");
  $$("button,input,select,textarea", form).forEach((control) => { control.disabled = true; });
  try {
    const payload = await api(id ? `/api/admin/roles/${encodeURIComponent(id)}` : "/api/admin/roles", {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(body),
    });
    const saved = normalizeAdminRole(payload.item ?? payload.role ?? payload.data?.item ?? { ...body, id });
    toast(id ? "Rol actualizado" : "Rol creado");
    notifyConfigurationUpdated();
    markConfigurationPanelClean("access-control");
    state.access.selectedRoleId = saved.id || id;
    await loadAdminRoles();
  } catch (error) {
    toast(error.message, "error");
  } finally {
    form.removeAttribute("aria-busy");
    $$("button,input,select,textarea", form).forEach((control) => { control.disabled = false; });
    $("#adminRoleKey").readOnly = Boolean($("#adminRoleId").value);
  }
}

function normalizeSecuritySettings(payload = {}) {
  const raw = payload.item ?? payload.settings ?? payload.security ?? payload.data?.item ?? payload;
  const mode = String(raw.mode ?? raw.accessMode ?? "").toLowerCase();
  const loginRequired = raw.loginRequired ?? raw.requireLogin ??
    (mode ? !["automatic", "auto", "local"].includes(mode) : true);
  return {
    loginRequired: accessBoolean(loginRequired, true),
    autoUserId: String(raw.autoUserId ?? raw.automaticUserId ?? raw.autoLoginUserId ?? ""),
    autoUserUsername: String(raw.autoUserUsername ?? raw.automaticUserUsername ?? ""),
    autoUserEmail: String(raw.autoUserEmail ?? raw.automaticUserEmail ?? ""),
    autoUserName: repairText(raw.autoUserName ?? raw.automaticUserName ?? ""),
    sessionDurationMinutes: Number(
      raw.sessionDurationMinutes ?? raw.sessionMinutes ?? raw.sessionTtlMinutes ?? raw.sessionDuration ?? 43200
    ) || 43200,
    revision: raw.revision ?? raw.version ?? "",
  };
}

function updateAdminSecurityMode() {
  const automatic = $('input[name="adminAccessMode"]:checked')?.value === "automatic";
  const select = $("#adminSecurityAutoUser");
  select.disabled = !automatic;
  select.required = automatic;
  $("#adminSecurityAutoUserHelp").textContent = automatic
    ? "Esta cuenta identificará toda actividad abierta sin pantalla de ingreso."
    : "No se utiliza mientras el sistema exija usuario y clave.";
}

function renderAdminSecuritySettings() {
  const settings = state.access.security;
  if (!settings) return;
  const autoUserId = settings.autoUserId ||
    state.access.users.find((user) =>
      (settings.autoUserUsername && user.username === settings.autoUserUsername) ||
      (settings.autoUserEmail && user.email === settings.autoUserEmail))?.id || "";
  const users = state.access.users.filter((user) => user.active || user.id === autoUserId);
  const currentUser = state.access.me?.user;
  if (currentUser?.id && !users.some((user) => user.id === currentUser.id)) users.push(currentUser);
  if (autoUserId && !users.some((user) => user.id === autoUserId)) {
    users.push({
      id: autoUserId,
      displayName: settings.autoUserName || settings.autoUserUsername || settings.autoUserEmail || "Usuario automático actual",
      username: settings.autoUserUsername || settings.autoUserEmail,
      email: settings.autoUserEmail,
      active: true,
    });
  }
  $("#adminSecurityAutoUser").innerHTML = `<option value="">Seleccione un usuario activo</option>${users.map((user) =>
    `<option value="${escapeHtml(user.id)}">${escapeHtml(user.displayName)} · ${escapeHtml(user.username || user.email)}</option>`
  ).join("")}`;
  $("#adminSecurityAutoUser").value = users.some((user) => user.id === autoUserId) ? autoUserId : "";
  $(`input[name="adminAccessMode"][value="${settings.loginRequired ? "login" : "automatic"}"]`).checked = true;
  const duration = String(settings.sessionDurationMinutes);
  const existingDuration = [...$("#adminSecuritySessionMinutes").options]
    .find((option) => Number(option.value) === Number(duration));
  if (!existingDuration) {
    const option = document.createElement("option");
    option.value = duration;
    option.textContent = `${Number(duration).toLocaleString("es-AR")} minutos`;
    $("#adminSecuritySessionMinutes").append(option);
  }
  $("#adminSecuritySessionMinutes").value = existingDuration?.value || duration;
  $("#adminSecurityRevision").textContent = settings.revision ? `Versión ${settings.revision}` : "";
  updateAdminSecurityMode();
}

async function loadAdminSecuritySettings() {
  const payload = await api(`/api/admin/security-settings?t=${Date.now()}`);
  state.access.security = normalizeSecuritySettings(payload);
  renderAdminSecuritySettings();
}

async function saveAdminSecuritySettings(event) {
  event.preventDefault();
  if (!accessCanManage("security")) return renderAccessDenied("Su rol no permite modificar la seguridad.");
  const form = event.currentTarget;
  if (!form.reportValidity()) return;
  const loginRequired = $('input[name="adminAccessMode"]:checked')?.value !== "automatic";
  const autoUserId = $("#adminSecurityAutoUser").value || null;
  if (!loginRequired && !autoUserId) {
    $("#adminSecurityAutoUser").setCustomValidity("Seleccione el usuario para acceso automático.");
    $("#adminSecurityAutoUser").reportValidity();
    $("#adminSecurityAutoUser").setCustomValidity("");
    return;
  }
  const body = {
    loginRequired,
    autoUserId: loginRequired ? null : autoUserId,
    sessionDurationMinutes: Number($("#adminSecuritySessionMinutes").value),
  };
  form.setAttribute("aria-busy", "true");
  try {
    const payload = await api("/api/admin/security-settings", {
      method: "PUT",
      body: JSON.stringify(body),
    });
    state.access.security = normalizeSecuritySettings(payload.item ? payload : { item: body });
    toast("Configuración de seguridad guardada");
    notifyConfigurationUpdated();
    markConfigurationPanelClean("access-control");
    await loadAdminSecuritySettings();
  } catch (error) {
    toast(error.message, "error");
  } finally {
    form.removeAttribute("aria-busy");
  }
}

async function loadAccessControl({ force = false } = {}) {
  if (state.access.loaded && !force) {
    setAccessAdminView(state.access.activeView);
    return;
  }
  $("#accessControlDenied").hidden = true;
  $("#accessControlWorkspace").hidden = false;
  setAccessControlStatus("Comprobando permisos…");
  try {
    state.access.me = normalizeAccessMe(await api(`/api/auth/me?t=${Date.now()}`));
    if (!state.access.me.authenticated) {
      renderAccessDenied("Debe iniciar sesión con una cuenta administrativa para abrir esta sección.");
      return;
    }
    if (!hasAccessPermission(ACCESS_PERMISSIONS.configuration)) {
      renderAccessDenied("Su rol no permite abrir la configuración del sistema.");
      return;
    }
    const firstView = availableAccessView(state.access.activeView);
    if (!firstView) {
      renderAccessDenied("Su cuenta puede abrir Configuración, pero no administrar usuarios, roles ni seguridad.");
      return;
    }
    const user = state.access.me.user;
    $("#accessCurrentUserName").textContent = user.displayName || user.username || user.email;
    $("#accessCurrentUserMeta").textContent = [
      user.username,
      user.email,
      user.licenseNumber ? `Matrícula ${user.licenseNumber}` : "",
      accessUserRoleLabel(user),
    ].filter(Boolean).join(" · ");
    const tasks = [];
    if (accessCanManage("roles") || accessCanManage("users")) {
      tasks.push(loadAdminRoles().catch((error) => {
        if (accessCanManage("roles")) throw error;
        state.access.roles = [];
      }));
    }
    if (accessCanManage("users")) tasks.push(loadAdminUsers());
    if (accessCanManage("security")) tasks.push(loadAdminSecuritySettings());
    await Promise.all(tasks);
    reconcileAdminUserRoles();
    renderAdminUserList();
    state.access.loaded = true;
    $("#accessControlDenied").hidden = true;
    $("#accessControlWorkspace").hidden = false;
    setAccessControlStatus("Datos administrativos actualizados.", "ready");
    setAccessAdminView(firstView);
  } catch (error) {
    state.access.loaded = false;
    if (error.status === 401 || error.status === 403) {
      renderAccessDenied(error.message);
    } else {
      renderAccessDenied(`No se pudo cargar la administración: ${error.message}`);
    }
  }
}

function wireBuilderCard(card, onChange = () => {}) {
  const type = $(".builder-type", card); type.addEventListener("change", () => { card.dataset.type = type.value; onChange(); });
  $(".builder-label", card).addEventListener("input", (event) => { const key = $(".builder-key", card); if (!key.dataset.touched) key.value = slug(event.target.value); onChange(); });
  $(".builder-key", card).addEventListener("input", (event) => { event.target.dataset.touched = "1"; onChange(); });
  $$('input,select', card).forEach((control) => control.addEventListener("input", onChange));
  $(".remove", card).addEventListener("click", () => { markConfigurationPanelDirty(card); card.remove(); onChange(); });
  card.addEventListener("dragstart", () => card.classList.add("dragging")); card.addEventListener("dragend", () => { markConfigurationPanelDirty(card); card.classList.remove("dragging"); $$(".drag-over").forEach((node) => node.classList.remove("drag-over")); onChange(); });
  card.addEventListener("dragover", (event) => { event.preventDefault(); const dragging = $(".builder-card.dragging", card.parentElement); if (!dragging || dragging === card) return; card.classList.add("drag-over"); const rect = card.getBoundingClientRect(); card.parentElement.insertBefore(dragging, event.clientY < rect.top + rect.height / 2 ? card : card.nextSibling); });
  card.addEventListener("dragleave", () => card.classList.remove("drag-over"));
}

function wireConfigurationDirtyTracking() {
  const content = $(".configuration-content");
  if (!content) return;
  const markFromControl = (event) => {
    const form = event.target.closest?.("form");
    if (form && DIRTY_TRACKED_CONFIGURATION_FORMS.has(form.id)) markConfigurationPanelDirty(form);
  };
  content.addEventListener("input", markFromControl);
  content.addEventListener("change", markFromControl);
  content.addEventListener("click", (event) => {
    const mutationButton = event.target.closest?.(
      "#addCalculatorVariableBtn, #addCalculatorRangeBtn, #insertFormulaVariableBtn, [data-formula-token], [data-formula-function], [data-add-research-field]"
    );
    if (mutationButton) markConfigurationPanelDirty(mutationButton);
  });
}

function wireEvents() {
  wireConfigurationDirtyTracking();
  $$('[data-config-tab]').forEach((button) => button.addEventListener("click", () => setTab(button.dataset.configTab)));
  $("#refreshAccessControlBtn").addEventListener("click", () => {
    state.access.loaded = false;
    loadAccessControl({ force: true });
  });
  $$("[data-access-admin-tab]").forEach((button) => {
    button.addEventListener("click", () => setAccessAdminView(button.dataset.accessAdminTab));
  });
  $("#adminUserSearch").addEventListener("input", renderAdminUserList);
  $("#showInactiveAdminUsers").addEventListener("change", renderAdminUserList);
  $("#adminUserList").addEventListener("click", (event) => {
    const button = event.target.closest("[data-admin-user-id]");
    if (button) editAdminUser(button.dataset.adminUserId);
  });
  $("#newAdminUserBtn").addEventListener("click", () => {
    resetAdminUserEditor();
    markConfigurationPanelDirty("access-control");
  });
  $("#cancelAdminUserBtn").addEventListener("click", () => {
    markConfigurationPanelClean("access-control");
    state.access.selectedUserId = "";
    $("#adminUserForm").hidden = true;
    $("#adminUserEmpty").hidden = false;
    renderAdminUserList();
  });
  $("#adminUserTemporaryPassword").addEventListener("input", (event) => event.target.setCustomValidity(""));
  $("#adminUserForm").addEventListener("submit", saveAdminUser);
  $("#adminRoleSearch").addEventListener("input", renderAdminRoleList);
  $("#showInactiveAdminRoles").addEventListener("change", renderAdminRoleList);
  $("#adminRoleList").addEventListener("click", (event) => {
    const button = event.target.closest("[data-admin-role-id]");
    if (button) editAdminRole(button.dataset.adminRoleId);
  });
  $("#newAdminRoleBtn").addEventListener("click", () => {
    resetAdminRoleEditor();
    markConfigurationPanelDirty("access-control");
  });
  $("#cancelAdminRoleBtn").addEventListener("click", () => {
    markConfigurationPanelClean("access-control");
    state.access.selectedRoleId = "";
    $("#adminRoleForm").hidden = true;
    $("#adminRoleEmpty").hidden = false;
    renderAdminRoleList();
  });
  $("#adminRoleName").addEventListener("input", () => {
    if (!$("#adminRoleId").value && !$("#adminRoleKey").dataset.touched) {
      $("#adminRoleKey").value = slug($("#adminRoleName").value).replace(/_/g, "-");
    }
  });
  $("#adminRoleKey").addEventListener("input", (event) => { event.target.dataset.touched = "1"; });
  $("#adminRoleForm").addEventListener("submit", saveAdminRole);
  $("#selectAllRolePermissionsBtn").addEventListener("click", () => {
    $$("[data-admin-role-permission]", $("#rolePermissionMatrix")).forEach((input) => { input.checked = true; });
  });
  $("#clearRolePermissionsBtn").addEventListener("click", () => {
    $$("[data-admin-role-permission]", $("#rolePermissionMatrix")).forEach((input) => { input.checked = false; });
  });
  $$('input[name="adminAccessMode"]').forEach((input) => input.addEventListener("change", updateAdminSecurityMode));
  $("#adminSecurityAutoUser").addEventListener("change", (event) => event.target.setCustomValidity(""));
  $("#adminSecuritySettingsForm").addEventListener("submit", saveAdminSecuritySettings);
  $("#guideConfigSearch").addEventListener("input", renderGuideList); $("#guideConfigList").addEventListener("click", (event) => { const button = event.target.closest("[data-item-id]"); if (button) selectGuide(button.dataset.itemId); }); $("#guideConfigForm").addEventListener("submit", saveGuide); $("#guideUpload").addEventListener("change", (event) => uploadGuides([...event.target.files])); $("#archiveGuideBtn").addEventListener("click", () => saveGuide(null, state.selectedGuide?.active === false));
  $("#studyTemplateAdminSearch").addEventListener("input", renderStudyTemplateAdminList);
  $("#studyTemplateAdminCategoryFilter").addEventListener("change", renderStudyTemplateAdminList);
  $("#showInactiveStudyTemplates").addEventListener("change", renderStudyTemplateAdminList);
  $("#studyTemplateAdminList").addEventListener("click", (event) => {
    const button = event.target.closest("[data-study-template-id]");
    const template = findStudyTemplate(button?.dataset.studyTemplateId);
    if (template) editStudyTemplate(template);
  });
  $("#studyTemplateAdminUpload").addEventListener("change", (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (file) beginStudyTemplateUpload(file);
  });
  $("#studyTemplateAdminForm").addEventListener("submit", saveStudyTemplate);
  $("#cancelStudyTemplateUploadBtn").addEventListener("click", cancelStudyTemplateUpload);
  $("#archiveStudyTemplateBtn").addEventListener("click", toggleStudyTemplateActive);
  $("#saveDiagnosisVisibleSettingsBtn").addEventListener("click", saveDiagnosisDisplaySetting);
  $$("[data-diagnosis-visible-system]").forEach((input) => {
    input.addEventListener("change", () => {
      setDiagnosisVisibleSettingsStatus(
        selectedDiagnosisVisibleSystems().length ? "Cambios sin guardar." : "Seleccione al menos una clasificación.",
        selectedDiagnosisVisibleSystems().length ? "" : "error"
      );
    });
  });
  $("#newDiagnosisEquivalenceBtn").addEventListener("click", newDiagnosisEquivalence);
  $("#diagnosisEquivalenceConfigSearch").addEventListener("input", renderDiagnosisEquivalenceList);
  $("#showInactiveDiagnosisEquivalences").addEventListener("change", renderDiagnosisEquivalenceList);
  $("#diagnosisEquivalenceConfigList").addEventListener("click", (event) => {
    const button = event.target.closest("[data-item-id]");
    const item = state.diagnosisEquivalences.find((entry) => entry.id === button?.dataset.itemId);
    if (item) editDiagnosisEquivalence(item);
  });
  $("#diagnosisEquivalenceConfigForm").addEventListener("submit", saveDiagnosisEquivalence);
  $("#archiveDiagnosisEquivalenceBtn").addEventListener("click", toggleDiagnosisEquivalenceActive);
  $$("[data-diagnosis-equivalence-search]").forEach((input) => {
    input.addEventListener("input", () => scheduleDiagnosisEquivalenceSearch(input.dataset.diagnosisEquivalenceSearch));
  });
  $$("[data-diagnosis-equivalence-results]").forEach((select) => {
    select.addEventListener("change", () => applyDiagnosisEquivalenceResult(select.dataset.diagnosisEquivalenceResults, select.value));
  });
  $$("[data-diagnosis-equivalence-code], [data-diagnosis-equivalence-display]").forEach((control) => {
    control.addEventListener("input", () => {
      const system = control.dataset.diagnosisEquivalenceCode || control.dataset.diagnosisEquivalenceDisplay;
      state.diagnosisEquivalenceSearchMeta[system] = {};
    });
  });
  $("#newCalculatorBtn").addEventListener("click", () => newCalculator("formula")); $("#newScoreBtn").addEventListener("click", () => newCalculator("score")); $("#calculatorConfigSearch").addEventListener("input", () => { renderCalculatorList(); renderBuiltInTools(); }); $("#showInactiveCalculators").addEventListener("change", renderCalculatorList); $("#calculatorConfigList").addEventListener("click", (event) => { const button = event.target.closest("[data-item-id]"); const item = state.calculators.find((entry) => entry.id === button?.dataset.itemId); if (item) editCalculator(item); }); $("#calculatorConfigForm").addEventListener("submit", saveCalculator); $("#archiveCalculatorBtn").addEventListener("click", () => saveCalculator(null, !state.selectedCalculator?.active)); $("#addCalculatorVariableBtn").addEventListener("click", () => addCalculatorVariable()); ["#calculatorExpression", "#calculatorBasePoints", "#calculatorResultLabel", "#calculatorResultUnit", "#calculatorDecimals"].forEach((selector) => $(selector)?.addEventListener("input", renderCalculatorPreview));
  $("#builtInToolList").addEventListener("click", (event) => { const button = event.target.closest("[data-built-in-edit]"); if (button) editBuiltInTool(state.builtInTools.find((tool) => tool.key === button.dataset.builtInEdit)); }); $("#builtInToolList").addEventListener("change", (event) => { const input = event.target.closest("[data-built-in-key]"); if (!input) return; state.disabledBuiltInKeys = input.checked ? state.disabledBuiltInKeys.filter((key) => key !== input.dataset.builtInKey) : [...new Set([...state.disabledBuiltInKeys, input.dataset.builtInKey])]; $("#builtInToolCount").textContent = `${state.builtInTools.length - state.disabledBuiltInKeys.length} de ${state.builtInTools.length} activas`; }); $("#saveBuiltInToolsBtn").addEventListener("click", saveBuiltInTools);
  $("#newResearchFormBtn").addEventListener("click", newResearchForm); $("#researchConfigSearch").addEventListener("input", renderResearchList); $("#showInactiveResearch").addEventListener("change", renderResearchList); $("#researchConfigList").addEventListener("click", (event) => { const button = event.target.closest("[data-item-id]"); const item = state.researchForms.find((entry) => entry.id === button?.dataset.itemId); if (item) editResearchForm(item); }); $("#researchConfigForm").addEventListener("submit", saveResearchForm); $("#archiveResearchBtn").addEventListener("click", () => saveResearchForm(null, !state.selectedResearch?.active)); $$("[data-add-research-field]").forEach((button) => button.addEventListener("click", () => addResearchField({ type: button.dataset.addResearchField, label: button.textContent.trim(), key: slug(button.textContent) })));
  $("#dayHospitalSettingsForm").addEventListener("submit", saveDayHospitalSettings); ["#dayHospitalChairCount", "#dayHospitalSlotMinutes", "#dayHospitalStartTime", "#dayHospitalEndTime"].forEach((selector) => $(selector).addEventListener("input", renderDayHospitalPreview));
  $("#llmConfigurationForm").addEventListener("submit", saveLlmConfiguration);
  $("#testLlmConnectionBtn").addEventListener("click", testLlmConnection);
  $$("[data-llm-preset]").forEach((button) => button.addEventListener("click", () => applyLlmPreset(button.dataset.llmPreset)));
  $$('input[name="llmApiKeyAction"]').forEach((input) => input.addEventListener("change", updateLlmKeyControls));
  $("#llmConfigEndpoint").addEventListener("input", updateLlmEnvironmentNotice);
  $("#llmConfigProvider").addEventListener("change", () => {
    updateLlmEnvironmentNotice();
    setLlmConnectionResult("Proveedor modificado. Revise el endpoint y el modelo antes de guardar.", "pending");
  });
}

document.addEventListener("DOMContentLoaded", () => { icons(); wireEvents(); checkStatus(); setTab(location.hash.slice(1) || "protocols"); });
