"use strict";

const DATA_URL = "/api/hc";
const DEMO_URL = DATA_URL;
const STORAGE_KEY = "hc-oncologica-paper-v4";
const ACTIVE_STORAGE_KEY = `${STORAGE_KEY}:active`;
const SPLIT_KEY = "hc-oncologica-left-width-v2";
const SPLIT_DEFAULT_PERCENT = 58;
const SPLIT_BALANCED_PERCENT = 50;
const SPLIT_STACK_BREAKPOINT = 980;
const LIRA_PRESENTATION_VERSION = 3;
const LOCAL_STRUCTURED_SECTION_KEYS = Object.freeze([
  "chiefComplaint",
  "currentIllness",
  "personalHistory",
  "physicalExam",
  "summaryPlan"
]);
const LOCAL_INLINE_LOAD_SECTION_KEYS = Object.freeze([
  ...LOCAL_STRUCTURED_SECTION_KEYS,
  "studies",
  "systemicTreatments",
  "oncologicSurgeries"
]);
const STRUCTURED_SECTION_FORMS = Object.freeze({
  chiefComplaint: {
    intro: "Registre el motivo que origina la consulta actual.",
    fields: [
      { path: "narrative.chiefComplaint", label: "Motivo de consulta", kind: "textarea", rows: 5, wide: true }
    ]
  },
  currentIllness: {
    intro: "Describa el inicio, la evolucion y el estado actual de la enfermedad.",
    fields: [
      { path: "narrative.currentIllness", label: "Antecedentes de enfermedad actual", kind: "textarea", rows: 8, wide: true }
    ]
  },
  personalHistory: {
    intro: "Complete cada grupo por separado para conservar los datos estructurados.",
    fields: [
      { path: "narrative.backgroundClinical", label: "Antecedentes clinicos / quirurgicos", kind: "textarea", rows: 5 },
      { path: "narrative.currentMedication", label: "Medicacion habitual", kind: "textarea", rows: 5 },
      { path: "narrative.familyOncology", label: "Antecedentes oncofamiliares", kind: "textarea", rows: 4 },
      { path: "narrative.gynecology", label: "Antecedentes gineco-obstetricos", kind: "textarea", rows: 4 }
    ]
  },
  physicalExam: {
    intro: "Registre medidas antropometricas y el examen fisico de ingreso.",
    fields: [
      { path: "exam.weightKg", label: "Peso (kg)", kind: "number", inputMode: "decimal", min: 0.01, step: 0.01 },
      { path: "exam.heightM", label: "Talla (cm)", kind: "number", inputMode: "decimal", min: 0.01, max: 250, step: 0.1 },
      { path: "narrative.physicalExam", label: "Examen fisico", kind: "textarea", rows: 8, wide: true }
    ],
    showExamTools: true
  },
  summaryPlan: {
    intro: "Cierre la historia con una sintesis clinica y la conducta acordada.",
    fields: [
      { path: "narrative.summary", label: "Conclusion / resumen", kind: "textarea", rows: 7, wide: true },
      { path: "narrative.plan", label: "Conducta / plan", kind: "textarea", rows: 6, wide: true }
    ]
  }
});
const DEFAULT_PHYSICAL_EXAM_TEXT = "Estado general: paciente en buen estado general. Karnofsky 100. Normohidratada. Afebril. Corazon: R1 y R2 normofoneticos, silencios libres. Torax: murmullo vesicular conservado sin ruidos agregados. Abdomen: blando, depresible, indoloro. SNC: sin signos neurologicos focales.";
const STUDY_UPLOAD_ACCEPTED_EXTENSIONS = new Set([
  "png", "jpg", "jpeg", "gif", "webp", "avif", "bmp", "ico", "tif", "tiff", "heic", "heif", "svg", "dcm",
  "pdf", "doc", "docx", "rtf", "odt", "ppt", "pps", "pptx", "ppsx", "odp",
  "mp4", "m4v", "mov", "3gp", "webm", "mkv", "avi", "mpeg", "mpg", "ogv", "wmv", "flv"
]);
const STUDY_UPLOAD_MAX_FILES = 20;
const STUDY_UPLOAD_MAX_FILE_BYTES = 250 * 1024 * 1024;
const STUDY_UPLOAD_MAX_BATCH_BYTES = 500 * 1024 * 1024;
const BUNDLED_STUDY_TEMPLATE_COUNT = 165;
const DIAGNOSTIC_CLASSIFICATION_SYSTEMS = Object.freeze(["snomed", "cie10", "ajcc"]);
const DIAGNOSTIC_CLASSIFICATION_LABELS = Object.freeze({
  ajcc: "AJCC",
  cie10: "CIE-10",
  snomed: "SNOMED CT"
});
const LEGACY_DIAGNOSIS_PROJECTION_KEY = "primary-oncology";
const STUDY_CLIPBOARD_IMAGE_FORMATS = new Map([
  ["image/png", { extension: "png", mime: "image/png" }],
  ["image/x-png", { extension: "png", mime: "image/png" }],
  ["image/jpeg", { extension: "jpg", mime: "image/jpeg" }],
  ["image/jpg", { extension: "jpg", mime: "image/jpeg" }],
  ["image/pjpeg", { extension: "jpg", mime: "image/jpeg" }],
  ["image/gif", { extension: "gif", mime: "image/gif" }],
  ["image/webp", { extension: "webp", mime: "image/webp" }],
  ["image/avif", { extension: "avif", mime: "image/avif" }],
  ["image/bmp", { extension: "bmp", mime: "image/bmp" }],
  ["image/x-ms-bmp", { extension: "bmp", mime: "image/bmp" }],
  ["image/x-bmp", { extension: "bmp", mime: "image/bmp" }],
  ["image/x-icon", { extension: "ico", mime: "image/x-icon" }],
  ["image/vnd.microsoft.icon", { extension: "ico", mime: "image/x-icon" }],
  ["image/tiff", { extension: "tiff", mime: "image/tiff" }],
  ["image/x-tiff", { extension: "tiff", mime: "image/tiff" }],
  ["image/heic", { extension: "heic", mime: "image/heic" }],
  ["image/heif", { extension: "heif", mime: "image/heif" }],
  ["image/svg+xml", { extension: "svg", mime: "image/svg+xml" }],
  ["application/dicom", { extension: "dcm", mime: "application/dicom" }]
]);

let state = null;
let selectedStudyId = null;
let pendingStudyFile = null;
let pendingStudyUploads = [];
let studyUploadBusy = false;
let studyUploadReturnFocus = null;
let studyUploadBatchVersion = 0;
let studyUploadPersistenceError = "";
let studyTemplates = [];
let studyTemplatesLoaded = false;
let studyTemplateSelectedId = "";
let studyTemplateBusy = false;
let studyTemplateReturnFocus = null;
const studySessionUploadDeletes = new Map();
let studyDeleteBusyId = "";
let studyImageReorderBusyId = "";
let studyClipboardSequence = 0;
let studyClipboardPointerInside = false;
let studyClipboardPointerX = null;
let studyClipboardPointerY = null;
let saveTimer = null;
let clinicalPersistenceQueue = Promise.resolve(true);
let clinicalPersistenceLastError = null;
let isDraggingSplitter = false;
let splitDragOffset = 0;
let evolutionMode = "evolucion";
let editingEvolutionId = null;
let editingSectionKey = null;
let sectionEditSessionSequence = 0;
let printTimestamp = null;
let timelineFilters = new Set();
let timelineMilestonesOnly = false;
let timelineSearchQuery = "";
let timelineSearchTimer = null;
let timelineAiLoading = false;
let timelineAiAttempted = false;
let timelineAiError = "";
let timelineGenerationVersion = 0;
let agentConversation = [];
let agentBusy = false;
let prescriptionType = "medication";
let prescriptionPreviewId = null;
let systemicFormCatalog = [];
let pendingSystemicDraft = null;
let systemicFormBusy = false;
let systemicFormRequestId = 0;
let medicationSearchTimer = null;
let protocolData = null;
let protocolDetail = null;
let seerTnmData = null;
let ajcc8Catalog = null;
let ajcc8Site = null;
let ajcc8StageTimer = null;
let diagnosticClassificationUi = {
  activeSystem: "snomed",
  patientKey: "",
  drafts: { snomed: null, cie10: null, ajcc: null },
  queries: { snomed: "", cie10: "", ajcc: "" },
  results: { ajcc: [], cie10: [], snomed: [] },
  loadedQuery: { ajcc: null, cie10: null, snomed: null },
  loading: { ajcc: false, cie10: false, snomed: false },
  errors: { ajcc: "", cie10: "", snomed: "" },
  requestId: { ajcc: 0, cie10: 0, snomed: 0 },
  selectionRequestId: 0
};
let diagnosticAjccUi = {
  site: null,
  siteId: "",
  loading: false,
  error: "",
  requestId: 0,
  values: {},
  stage: "",
  stageEdited: false,
  sourceRow: null,
  calculating: false,
  calculationError: "",
  stageRequestId: 0
};
let diagnosticEquivalences = null;
let diagnosticEquivalencesLoading = null;
let diagnosticAjccCatalog = null;
let diagnosticAjccCatalogLoading = null;
let diagnosticAjccCatalogError = "";
let diagnosticVisibleSystems = [...DIAGNOSTIC_CLASSIFICATION_SYSTEMS];
let diagnosticDisplaySettingsLoaded = false;
let diagnosticDisplaySettingsLoading = null;
let diagnosticClassificationSearchTimer = null;
let diagnosticAjccStageTimer = null;
let diagnosticClassificationModalReturnFocus = null;
let diagnosticClassificationSaveBusy = false;
let diagnosticClassificationDraftEntryId = "";
let localGuides = [];
let researchFormTemplates = [];
let activeResearchTemplateId = "";
let researchTemplatesLoaded = false;
let activeStudyImageMenuId = "";
let activeStudyImageContext = null;
let evolutionDraftAttachments = [];
let studyImageModalReturnFocus = null;
let annotationMode = false;
let annotationTool = "draw";
let annotationColor = "#e53935";
let annotationWidth = 7;
const ANNOTATION_ERASER_WIDTH_MULTIPLIER = 3;
let annotationFill = "none";
let annotationBaseImage = null;
let annotationLayerCanvas = null;
let annotationCommands = [];
let activeAnnotationStroke = null;
let pendingClinicalSelection = [];
let clinicalPeriodFocusTimer = null;
let timelinePeriodFocusTimer = null;
let timelineSelection = null;
let lastAgentHighlights = [];
let clinicalScrollResizeObserver = null;
let timelineControlsSyncFrame = null;
let liraImportSelectedPatientId = "";
let liraImportResults = [];
let liraImportSearchTimer = null;
let liraImportSearchController = null;
let liraImportPreviewController = null;
let liraImportReturnFocus = null;
let liraImportBusy = false;
let liraPatientRefreshBusy = false;
let closeActivePatientBusy = false;
let newPatientReturnFocus = null;
let newPatientBusy = false;
let liraSourceAvailable = false;
let liraImportSelectedImportable = false;
let clinicalContextVersion = 0;
let careView = "treatments";
let careTreatments = [];
let careInfusions = [];
let careScheduleSettings = { chairCount: 6, slotMinutes: 10, startTime: "08:00", endTime: "16:00", timeZone: "America/Argentina/Buenos_Aires" };
let careScheduleInfusions = [];
let careScheduleCandidates = [];
let careScheduleMode = "chairs";
let careScheduleSelectedCandidateId = "";
let careScheduleDrag = null;
let careScheduleDropTarget = null;
let careScheduleBusy = false;
let careScheduleRequestVersion = 0;
let careScheduleCandidateRequestVersion = 0;
let careScheduleCandidateSearchTimer = null;
let careScheduleCandidateVisibleLimit = 200;
let careSchedulePharmacyVisibleLimit = 250;
let careScheduleDetailInfusionId = "";
let careScheduleDetailSource = "schedule";
let careScheduleDetailQrScan = null;
let careScheduleHoverInfusionId = "";
let careScheduleVisibleChairCount = 6;
let careScheduleChairOffset = 0;
let careQrScannerStream = null;
let careQrScannerFrame = 0;
let careQrScannerLastFrameAt = 0;
let careQrScannerDecoding = false;
let careQrScannerBusy = false;
let careQrScannerRequestVersion = 0;
let careQrScannerReturnFocus = null;
let careQrScannerLastCode = "";
let careQrScannerOperationId = "";
let careQrScannerResolved = null;
let careQrBarcodeDetector = null;
let careQrDecoderLoading = null;
let careQrFinalizeBusy = false;
let careQrFinalizeOperationId = "";
let careDrugs = [];
let careSchemes = [];
let careTreatmentOptions = null;
let careDiagnosisProjectionPromise = null;
let careDiagnosisProjectionKey = "";
let careDiagnosisProjectionTerminalKey = "";
let careSelectedInfusionId = "";
let careDrugSearchTimer = null;
let careRequestVersion = 0;
let careInfusionRequestVersion = 0;
let careTreatmentDetailRequestVersion = 0;
let careTreatmentRequirementsState = { requestId: 0, status: "idle", schemeId: "" };
let careBusy = false;
let careTreatmentArchiveBusy = false;
let careInfusionMutationBusy = false;
let careHospitalReturnFocus = null;
let careInfusionModalReturnFocus = null;
let careTreatmentCollections = {
  oncological: [],
  nonOncological: [],
  procedures: [],
  referrals: []
};
let careTreatmentManagerState = {
  tab: "oncological",
  query: "",
  sortColumn: 0,
  sortDirection: "desc",
  selectedId: "",
  mode: "list",
  detailPane: "drugs",
  detailCycle: 0,
  exactDetail: null,
  observationApplicationId: ""
};
let careInfusionTableState = {
  page: 0,
  sortKey: "scheduled",
  sortDirection: "asc"
};
let careExpandedTreatmentId = "";
let careTreatmentWorkflowOptions = null;
let careTreatmentWorkflowEditor = { kind: "", recordId: "0" };
let clinicalSession = {
  authenticated: false,
  loginRequired: false,
  autoLoginEnabled: true,
  user: null,
  permissions: new Set()
};
let clinicalLoginGateResolve = null;
let clinicalInboxItems = [];
let clinicalInboxActiveId = "";
let clinicalInboxPollTimer = null;
let clinicalInboxBusy = false;
let clinicalInboxAutoOpenPending = false;
let clinicalSessionExpirationHandling = false;
let clinicalSessionExpiredPending = false;
let careScheduleWorkflowCandidateId = "";
let careScheduleWorkflowAction = "";
let careScheduleWorkflowBusy = false;
const careScheduleWorkflowUsers = new Map();

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));
const clinicalNativeFetch = window.fetch.bind(window);

window.fetch = async (...args) => {
  const response = await clinicalNativeFetch(...args);
  try {
    const input = args[0];
    const requestUrl = typeof input === "string" || input instanceof URL ? String(input) : String(input?.url || "");
    const url = new URL(requestUrl, window.location.href);
    const isProtectedApi = url.origin === window.location.origin && url.pathname.startsWith("/api/");
    const isAuthenticationExchange = ["/api/auth/me", "/api/auth/login", "/api/auth/logout"].includes(url.pathname);
    if (response.status === 401 && isProtectedApi && !isAuthenticationExchange) {
      window.queueMicrotask(() => handleClinicalSessionExpired());
    }
  } catch {
    // La respuesta original siempre se entrega aunque no pueda analizarse su URL.
  }
  return response;
};

document.addEventListener("DOMContentLoaded", async () => {
  applyStoredSplit();
  wireEvents();
  await initializeClinicalSession();
  await loadState({ forceServer: clinicalSession.authenticated });
  syncClinicalActorToState();
  renderAll();
  setPrescriptionType(prescriptionType);
  initializeLiraImporter();
  initializeGuidedHelp();
  startClinicalInboxPolling({ openFirst: true });
  if (new URLSearchParams(window.location.search).get("open") === "day-hospital-schedule") {
    await openCareTreatmentManagerModal({ mode: "chairs" });
  }
});

function normalizeClinicalPermissions(payload = {}) {
  const user = payload?.user || null;
  const direct = Array.isArray(user?.permissions) ? user.permissions : Array.isArray(payload?.permissions) ? payload.permissions : null;
  const inherited = Array.isArray(user?.roles)
    ? user.roles.flatMap((role) => Array.isArray(role?.permissions) ? role.permissions : [])
    : [];
  if (!direct && !inherited.length) return new Set();
  return new Set([...direct || [], ...inherited].map((item) => String(item || "").trim()).filter(Boolean));
}

function clinicalHasPermission(permission) {
  if (!permission) return true;
  const permissions = clinicalSession.permissions;
  if (!(permissions instanceof Set)) return false;
  return permissions.has(permission) || permissions.has("*");
}

function clinicalRoleLabel(user = clinicalSession.user) {
  const names = Array.isArray(user?.roles)
    ? user.roles.map((role) => String(role?.name || role?.key || role || "").trim()).filter(Boolean)
    : [];
  return names.join(" · ") || user?.specialty || "Sesión clínica";
}

function clinicalUserDisplayName(user = clinicalSession.user) {
  return String(user?.displayName || user?.name || user?.username || user?.email || "Usuario local").trim();
}

function renderClinicalSession() {
  const user = clinicalSession.user;
  const authenticated = Boolean(clinicalSession.authenticated && user);
  const controls = $("#clinicalSessionControls");
  const loginButton = $("#clinicalLoginBtn");
  const logoutButton = $("#clinicalLogoutBtn");
  if (controls) controls.hidden = !authenticated;
  if (loginButton) loginButton.hidden = authenticated || clinicalSession.loginRequired;
  if (logoutButton) logoutButton.hidden = !authenticated || !clinicalSession.loginRequired;
  if (authenticated) {
    const displayName = clinicalUserDisplayName(user);
    const name = $("#clinicalUserName");
    const role = $("#clinicalUserRole");
    const avatar = $("#clinicalUserAvatar");
    if (name) name.textContent = displayName;
    if (role) role.textContent = clinicalRoleLabel(user);
    if (avatar) avatar.textContent = displayName.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase() || "U";
    if (controls) controls.title = `${displayName} · ${clinicalRoleLabel(user)}`;
  }
  const canReceiveTasks = authenticated && (
    clinicalHasPermission("workflow.resolve-prescription") ||
    clinicalHasPermission("workflow.resolve-continuity")
  );
  const inboxButton = $("#clinicalInboxBtn");
  if (inboxButton) inboxButton.hidden = !canReceiveTasks;
  applyClinicalPermissions();
  refreshIcons();
}

function applyClinicalPermissions() {
  $$("[data-permission]").forEach((element) => {
    const allowed = clinicalHasPermission(element.dataset.permission);
    element.dataset.accessHidden = String(!allowed);
    if ("disabled" in element) element.disabled = !allowed;
    element.setAttribute("aria-hidden", String(!allowed));
  });

  const sectionMap = {
    studies: "section.studies.view",
    care: "section.day-hospital.view",
    prescription: "section.prescriptions.view",
    agent: "section.agent.view",
    research: "section.research.view",
    timeline: "section.timeline.view",
    protocols: "section.protocols.view",
    tools: "section.tools.view"
  };
  Object.entries(sectionMap).forEach(([tab, permission]) => {
    const hidden = !clinicalHasPermission(permission);
    const panel = $(`[data-right-panel="${tab}"]`);
    if (panel) panel.dataset.accessHidden = String(hidden);
  });

  const historyRestricted = !clinicalHasPermission("section.history.view");
  const supportRestricted = Object.values(sectionMap).every((permission) => !clinicalHasPermission(permission));
  const workspace = $("#splitWorkspace");
  const clinicalPanel = $("#clinicalPanel");
  const studiesPanel = $("#studiesPanel");
  const splitterShell = $("#splitterShell");
  if (clinicalPanel) clinicalPanel.dataset.accessHidden = String(historyRestricted);
  if (studiesPanel) studiesPanel.dataset.accessHidden = String(supportRestricted);
  if (splitterShell) splitterShell.dataset.accessHidden = String(historyRestricted || supportRestricted);
  workspace?.classList.toggle("is-history-restricted", historyRestricted && !supportRestricted);
  workspace?.classList.toggle("is-support-restricted", supportRestricted && !historyRestricted);

  const activeRightTab = $('[data-right-tab][aria-selected="true"]');
  if (state && activeRightTab?.dataset.accessHidden === "true") {
    const firstAllowed = $('[data-right-tab]:not([data-access-hidden="true"])');
    if (firstAllowed) setRightTab(firstAllowed.dataset.rightTab);
  }

  const mutationGroups = [
    ["section.history.edit", "#newPatientBtn,[data-action='open-evolution'],[data-action='edit-evolution'],[data-action='open-section-editor'],[data-action='open-diagnosis']"],
    ["section.studies.edit", "#openStudyUploadBtn,#openStudyTemplateBtn,#confirmStudyUploadBtn,#confirmStudyTemplateBtn,#annotateStudyImageBtn,#saveStudyAnnotationBtn,[data-study-upload-trigger],[data-action='delete-study'],[data-action='annotate-study']"],
    ["section.day-hospital.edit", "#openCareInfusionModalBtn,[data-care-manager-action='schedule'],.care-pharmacy-state button"],
    ["section.prescriptions.edit", "#careHospitalNewTreatmentTab,#careHospitalOpenNewTreatmentBtn,#openCareTreatmentModalBtn,#careHierarchyNewTreatmentBtn,[data-care-manager-action='new'],#careTreatmentForm input,#careTreatmentForm select,#careTreatmentForm textarea,#careTreatmentForm button,#prescriptionForm input,#prescriptionForm select,#prescriptionForm textarea,#prescriptionForm button"],
    ["section.research.edit", "#researchForm input,#researchForm select,#researchForm textarea,#researchForm button"]
  ];
  mutationGroups.forEach(([permission, selector]) => {
    const disabled = !clinicalHasPermission(permission);
    $$(selector).forEach((element) => {
      if ("disabled" in element) element.disabled = disabled;
      element.setAttribute("aria-disabled", String(disabled));
      element.classList.toggle("is-permission-disabled", disabled);
    });
  });
  const prescriptionForm = $("#prescriptionForm");
  const researchForm = $("#researchForm");
  if (prescriptionForm) prescriptionForm.inert = !clinicalHasPermission("section.prescriptions.edit");
  if (researchForm) researchForm.inert = !clinicalHasPermission("section.research.edit");

  const toolsUseRestricted = !clinicalHasPermission("section.tools.use");
  const calculatorTab = $('[data-tool-tab="calculators"]');
  const calculatorPane = $('[data-tool-pane="calculators"]');
  if (calculatorTab) calculatorTab.dataset.accessHidden = String(toolsUseRestricted);
  if (calculatorPane) calculatorPane.dataset.accessHidden = String(toolsUseRestricted);
  if (state && toolsUseRestricted && calculatorTab?.classList.contains("active")) setToolTab("guides");
}

function syncClinicalActorToState() {
  if (!state?.meta || !clinicalSession.authenticated || !clinicalSession.user) return;
  const user = clinicalSession.user;
  const displayName = clinicalUserDisplayName(user);
  state.meta.currentUser = displayName;
  state.meta.currentProfessional = {
    ...(state.meta.currentProfessional || {}),
    firstName: displayName,
    lastName: displayName,
    license: user.licenseNumber || state.meta.currentProfessional?.license || "",
    specialty: user.specialty || state.meta.currentProfessional?.specialty || "",
    userId: user.id
  };
}

function purgeClinicalLocalCache() {
  try {
    const keys = [];
    for (let index = 0; index < localStorage.length; index += 1) {
      const key = localStorage.key(index);
      if (key === STORAGE_KEY || key === ACTIVE_STORAGE_KEY || key?.startsWith(`${STORAGE_KEY}:`)) keys.push(key);
    }
    keys.forEach((key) => localStorage.removeItem(key));
  } catch {
    // La seguridad de sesión no depende de que el navegador habilite almacenamiento local.
  }
}

function clinicalLocalCacheAllowed() {
  return clinicalSession.loginRequired !== true;
}

function handleClinicalSessionExpired() {
  if (clinicalSessionExpirationHandling) return;
  clinicalSessionExpirationHandling = true;
  clinicalSessionExpiredPending = true;
  stopClinicalInboxPolling();
  clinicalInboxItems = [];
  clinicalInboxActiveId = "";
  clinicalInboxAutoOpenPending = false;
  updateClinicalInboxBadge();
  careScheduleWorkflowUsers.clear();
  purgeClinicalLocalCache();
  clinicalSession = {
    authenticated: false,
    loginRequired: true,
    autoLoginEnabled: false,
    user: null,
    permissions: new Set()
  };
  renderClinicalSession();
  $$(".modal-backdrop.open").forEach((modal) => {
    if (modal.id === "clinicalLoginModal") return;
    modal.classList.remove("open");
    modal.setAttribute("aria-hidden", "true");
    modal.hidden = true;
  });
  showClinicalLoginModal();
  setClinicalFormError("#clinicalLoginError", "La sesión venció. Ingrese nuevamente para continuar de forma segura.");
}

async function initializeClinicalSession() {
  try {
    const response = await fetch("/api/auth/me", { cache: "no-store", headers: { Accept: "application/json" } });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok && response.status !== 401) throw new Error(payload.error || "No se pudo comprobar la sesión");
    if (typeof payload.loginRequired !== "boolean" && !payload.user && !payload.currentUser) {
      throw new Error("El servicio no devolvió un contexto de acceso válido");
    }
    updateClinicalSession(payload);
  } catch (error) {
    console.warn("El control de acceso no está disponible; la interfaz queda bloqueada.", error);
    clinicalSession = {
      authenticated: false,
      loginRequired: true,
      autoLoginEnabled: false,
      user: null,
      permissions: new Set()
    };
    purgeClinicalLocalCache();
    renderClinicalSession();
    showClinicalLoginModal();
    setClinicalFormError(
      "#clinicalLoginError",
      "El control de acceso no está disponible. Reintente el ingreso en unos instantes."
    );
    await new Promise((resolve) => { clinicalLoginGateResolve = resolve; });
    return true;
  }
  if (clinicalSession.loginRequired && !clinicalSession.authenticated) {
    showClinicalLoginModal();
    await new Promise((resolve) => { clinicalLoginGateResolve = resolve; });
  }
  return true;
}

function updateClinicalSession(payload = {}) {
  const user = payload.user || payload.currentUser || null;
  clinicalSession = {
    authenticated: payload.authenticated === true || Boolean(user),
    loginRequired: payload.loginRequired === true,
    autoLoginEnabled: payload.autoLoginEnabled !== false,
    user,
    permissions: normalizeClinicalPermissions({ ...payload, user })
  };
  if (clinicalSession.loginRequired) purgeClinicalLocalCache();
  renderClinicalSession();
}

function showClinicalLoginModal() {
  const modal = $("#clinicalLoginModal");
  if (!modal) return;
  modal.hidden = false;
  modal.classList.add("open");
  modal.setAttribute("aria-hidden", "false");
  document.body.classList.add("modal-open", "clinical-auth-required");
  const shell = $(".app-shell");
  if (shell) {
    shell.inert = true;
    shell.setAttribute("aria-hidden", "true");
  }
  window.requestAnimationFrame(() => $("#clinicalLoginUsername")?.focus());
}

function closeClinicalLoginModal() {
  const modal = $("#clinicalLoginModal");
  if (!modal) return;
  modal.classList.remove("open");
  modal.setAttribute("aria-hidden", "true");
  modal.hidden = true;
  document.body.classList.remove("clinical-auth-required");
  const shell = $(".app-shell");
  if (shell) {
    shell.inert = false;
    shell.removeAttribute("aria-hidden");
  }
  if (!$(".modal-backdrop.open")) document.body.classList.remove("modal-open");
}

function setClinicalFormError(selector, message = "") {
  const output = $(selector);
  if (!output) return;
  output.textContent = message;
  output.hidden = !message;
}

function wireClinicalSessionEvents() {
  $("#clinicalLoginForm")?.addEventListener("submit", submitClinicalLogin);
  $("#clinicalLoginBtn")?.addEventListener("click", showClinicalLoginModal);
  $("#clinicalLogoutBtn")?.addEventListener("click", logoutClinicalSession);
  $("#clinicalInboxBtn")?.addEventListener("click", () => openClinicalInbox({ explicit: true }));
  $("#closeClinicalInboxBtn")?.addEventListener("click", closeClinicalInbox);
  $("#clinicalInboxLaterBtn")?.addEventListener("click", closeClinicalInbox);
  $("#clinicalInboxResolveForm")?.addEventListener("submit", submitClinicalInboxResolution);
  $("#clinicalInboxResolution")?.addEventListener("change", syncClinicalInboxResolutionFields);
  document.addEventListener("keydown", (event) => {
    if (event.key === "Tab" && $("#clinicalInboxModal")?.classList.contains("open")) {
      event.stopImmediatePropagation();
      trapCareModalFocus(event, "clinicalInboxModal");
    }
  });
  window.addEventListener("focus", () => {
    if (clinicalSession.authenticated) void loadClinicalInbox({ openFirst: true });
  });
}

async function submitClinicalLogin(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const username = String(form.elements.username?.value || "").trim();
  const password = String(form.elements.password?.value || "");
  if (!username || !password) {
    setClinicalFormError("#clinicalLoginError", "Complete el usuario y la contraseña.");
    return;
  }
  const submit = $("#clinicalLoginSubmitBtn");
  if (submit) submit.disabled = true;
  setClinicalFormError("#clinicalLoginError");
  try {
    const response = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ username, password })
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok === false) throw new Error(payload.error || "Usuario o contraseña incorrectos");
    const reloadAfterLogin = clinicalSessionExpiredPending;
    purgeClinicalLocalCache();
    updateClinicalSession({ ...payload, authenticated: payload.authenticated !== false });
    if (state) {
      syncClinicalActorToState();
      renderAll();
    }
    closeClinicalLoginModal();
    const resolveGate = clinicalLoginGateResolve;
    clinicalLoginGateResolve = null;
    resolveGate?.(true);
    clinicalSessionExpirationHandling = false;
    clinicalSessionExpiredPending = false;
    if (reloadAfterLogin) {
      window.location.reload();
      return;
    }
    if (state) startClinicalInboxPolling({ openFirst: true });
  } catch (error) {
    setClinicalFormError("#clinicalLoginError", error.message || "No se pudo iniciar sesión.");
    $("#clinicalLoginPassword")?.focus();
    if ($("#clinicalLoginPassword")) $("#clinicalLoginPassword").value = "";
  } finally {
    if (submit) submit.disabled = false;
  }
}

async function logoutClinicalSession() {
  const button = $("#clinicalLogoutBtn");
  if (button) button.disabled = true;
  try {
    await fetch("/api/auth/logout", { method: "POST", headers: { Accept: "application/json" } });
  } finally {
    stopClinicalInboxPolling();
    purgeClinicalLocalCache();
    window.location.reload();
  }
}

function startClinicalInboxPolling({ openFirst = false } = {}) {
  stopClinicalInboxPolling();
  if (!clinicalSession.authenticated) return;
  if (!clinicalHasPermission("workflow.resolve-prescription") && !clinicalHasPermission("workflow.resolve-continuity")) return;
  clinicalInboxAutoOpenPending ||= openFirst;
  void loadClinicalInbox({ openFirst });
  clinicalInboxPollTimer = window.setInterval(() => void loadClinicalInbox({ openFirst: true }), 30000);
}

function stopClinicalInboxPolling() {
  if (clinicalInboxPollTimer) window.clearInterval(clinicalInboxPollTimer);
  clinicalInboxPollTimer = null;
}

function clinicalInboxRows(payload = {}) {
  const items = Array.isArray(payload) ? payload : payload.items || payload.requests || payload.inbox || payload.tasks || [];
  return Array.isArray(items)
    ? items.filter((item) => {
      if (String(item?.status || "pending") !== "pending") return false;
      const type = String(item?.type || item?.requestType || "").toLowerCase();
      return type.includes("continu")
        ? clinicalHasPermission("workflow.resolve-continuity")
        : clinicalHasPermission("workflow.resolve-prescription");
    })
    : [];
}

function clinicalTaskSeen(item) {
  return item?.seen === true || Boolean(item?.seenAt);
}

function updateClinicalInboxBadge() {
  const button = $("#clinicalInboxBtn");
  const count = $("#clinicalInboxCount");
  const pending = clinicalInboxItems.length;
  const unseen = clinicalInboxItems.filter((item) => !clinicalTaskSeen(item)).length;
  if (count) {
    count.textContent = String(pending > 99 ? "99+" : pending);
    count.hidden = pending === 0;
    count.setAttribute("aria-label", `${pending} ${pending === 1 ? "solicitud pendiente" : "solicitudes pendientes"}`);
  }
  if (button) {
    button.classList.toggle("has-pending", pending > 0);
    button.title = pending ? `${pending} ${pending === 1 ? "solicitud clínica pendiente" : "solicitudes clínicas pendientes"}` : "Sin solicitudes clínicas pendientes";
    button.setAttribute("aria-label", button.title);
    button.dataset.unseenCount = String(unseen);
  }
}

function hasBlockingClinicalModal() {
  return careScheduleBusy || careBusy || careInfusionMutationBusy || careScheduleWorkflowBusy ||
    liraImportBusy || newPatientBusy || studyUploadBusy || systemicFormBusy ||
    $$(".modal-backdrop.open").some((modal) =>
      !["clinicalLoginModal", "clinicalInboxModal"].includes(modal.id));
}

async function loadClinicalInbox({ openFirst = false } = {}) {
  if (!clinicalSession.authenticated || clinicalInboxBusy) return;
  clinicalInboxBusy = true;
  try {
    const response = await fetch(`/api/clinical/treatment-workflow-requests/inbox?t=${Date.now()}`, {
      cache: "no-store",
      headers: { Accept: "application/json" }
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      if (response.status === 401) return;
      throw new Error(payload.error || "No se pudieron consultar las solicitudes");
    }
    clinicalInboxItems = clinicalInboxRows(payload);
    updateClinicalInboxBadge();
    clinicalInboxAutoOpenPending ||= openFirst;
    if (clinicalInboxAutoOpenPending && !hasBlockingClinicalModal() && !$("#clinicalInboxModal")?.classList.contains("open")) {
      const unseen = clinicalInboxItems.find((item) => !clinicalTaskSeen(item));
      if (unseen) {
        clinicalInboxAutoOpenPending = false;
        await openClinicalInbox({ itemId: String(unseen.id), explicit: false });
      } else if (!clinicalInboxItems.length) {
        clinicalInboxAutoOpenPending = false;
      }
    }
  } catch (error) {
    console.warn(error.message || error);
  } finally {
    clinicalInboxBusy = false;
  }
}

function clinicalTaskType(item) {
  const value = String(item?.type || item?.requestType || "").toLowerCase();
  return value.includes("continu") ? "continuity_request" : "prescription_request";
}

function clinicalTaskPatientName(item) {
  return String(item?.patientName || item?.patient?.fullName || item?.patient?.name || `Paciente ${item?.patientId || ""}`).trim();
}

async function openClinicalInbox({ itemId = "", explicit = false } = {}) {
  if (!clinicalInboxItems.length) {
    if (explicit) {
      await loadClinicalInbox();
      if (!clinicalInboxItems.length) {
        toast("No hay solicitudes clínicas pendientes");
        return;
      }
    } else {
      return;
    }
  }
  const item = clinicalInboxItems.find((entry) => String(entry.id) === String(itemId)) || clinicalInboxItems[0];
  if (!item) return;
  clinicalInboxActiveId = String(item.id);
  renderClinicalInboxItem(item);
  showCareModal("clinicalInboxModal");
  if (!clinicalTaskSeen(item)) {
    item.seen = true;
    item.seenAt = new Date().toISOString();
    updateClinicalInboxBadge();
    fetch(`/api/clinical/treatment-workflow-requests/${encodeURIComponent(item.id)}/seen`, {
      method: "PATCH",
      headers: { Accept: "application/json" }
    }).catch(() => {});
  }
}

function renderClinicalInboxItem(item) {
  const type = clinicalTaskType(item);
  const isPrescription = type === "prescription_request";
  const title = $("#clinicalInboxTitle");
  const description = $("#clinicalInboxDescription");
  if (title) title.textContent = isPrescription ? "Solicitud de prescripción" : "Confirmación de continuidad";
  if (description) description.textContent = isPrescription
    ? "Confirme la prescripción para este mismo tratamiento y ciclo, o rechace la solicitud indicando el motivo."
    : "Defina si el tratamiento continúa o debe suspenderse.";
  const requestedBy = item.requestedBy?.displayName || item.requestedByDisplayName ||
    item.requestedByName || item.createdByName || "Equipo asistencial";
  const treatment = item.scheme || item.treatmentName || item.treatment?.scheme || item.treatment?.name || "Tratamiento";
  const message = item.message || item.reason || "Sin mensaje adicional.";
  const output = $("#clinicalInboxSummary");
  if (output) {
    output.innerHTML = `
      <div><span>Paciente</span><strong>${escapeHtml(clinicalTaskPatientName(item))}</strong><small>${item.patientDni ? `DNI ${escapeHtml(item.patientDni)}` : `ID ${escapeHtml(item.patientId || "—")}`}</small></div>
      <div><span>Tratamiento</span><strong>${escapeHtml(treatment)}</strong><small>Ciclo ${escapeHtml(item.cycleNumber || "—")}</small></div>
      <div class="is-wide"><span>Solicitado por ${escapeHtml(requestedBy)}</span><p>${escapeHtml(message)}</p></div>
      ${isPrescription ? `<div class="is-wide clinical-inbox-cycle-scope"><span>Alcance de la confirmación</span><p>Se aplicará al tratamiento existente (ID ${escapeHtml(item.treatmentId || "sin informar")}) y al ciclo ${escapeHtml(item.cycleNumber || "actual")}. Los ciclos previos y las sesiones realizadas no cambian; confirmar no crea otro tratamiento.</p></div>` : ""}`;
  }
  const resolution = $("#clinicalInboxResolution");
  if (resolution) {
    resolution.innerHTML = isPrescription
      ? `<option value="">Seleccione una decisión...</option><option value="prescription_confirmed">Prescripción confirmada</option><option value="prescription_rejected">Rechazar solicitud</option>`
      : `<option value="">Seleccione una decisión...</option><option value="continue">Continuar tratamiento</option><option value="temporary_hold">Suspender transitoriamente</option><option value="discontinued">Suspender definitivamente</option>`;
    resolution.value = "";
  }
  if ($("#clinicalInboxReason")) $("#clinicalInboxReason").value = "";
  if ($("#clinicalInboxResumeDate")) $("#clinicalInboxResumeDate").value = "";
  const position = $("#clinicalInboxPosition");
  const index = Math.max(0, clinicalInboxItems.findIndex((entry) => String(entry.id) === String(item.id)));
  if (position) position.textContent = `${index + 1} de ${clinicalInboxItems.length}`;
  setClinicalFormError("#clinicalInboxError");
  syncClinicalInboxResolutionFields();
  refreshIcons();
}

function syncClinicalInboxResolutionFields() {
  const item = clinicalInboxItems.find((entry) => String(entry.id) === clinicalInboxActiveId);
  const resolution = $("#clinicalInboxResolution")?.value || "";
  const type = clinicalTaskType(item);
  const suspension = ["temporary_hold", "discontinued"].includes(resolution);
  const rejected = resolution === "prescription_rejected";
  const reason = $("#clinicalInboxReason");
  const reasonLabel = $("#clinicalInboxReasonLabel");
  const dateField = $("#clinicalInboxResumeDateField");
  const warning = $("#clinicalInboxTemporaryWarning");
  if (reason) {
    reason.required = suspension || rejected;
    reason.placeholder = suspension || rejected ? "La causa es obligatoria..." : "Agregue una nota clínica si corresponde...";
  }
  if (reasonLabel) reasonLabel.textContent = suspension ? "Causa de la suspensión" : rejected ? "Motivo del rechazo" : "Nota clínica";
  if (dateField) dateField.hidden = resolution !== "temporary_hold";
  if (warning) warning.hidden = resolution !== "temporary_hold";
  const submit = $("#clinicalInboxResolveBtn");
  if (submit) {
    submit.disabled = !resolution;
    $("span", submit).textContent = type === "prescription_request" && resolution === "prescription_confirmed"
      ? "Confirmar prescripción"
      : "Registrar decisión";
  }
}

function closeClinicalInbox() {
  closeCareModal("clinicalInboxModal");
  clinicalInboxActiveId = "";
}

async function submitClinicalInboxResolution(event) {
  event.preventDefault();
  if (clinicalInboxBusy) return;
  const item = clinicalInboxItems.find((entry) => String(entry.id) === clinicalInboxActiveId);
  if (!item) return;
  const resolution = $("#clinicalInboxResolution")?.value || "";
  const reason = String($("#clinicalInboxReason")?.value || "").trim();
  const resumeDate = $("#clinicalInboxResumeDate")?.value || "";
  if (!resolution) return;
  if (["prescription_rejected", "temporary_hold", "discontinued"].includes(resolution) && !reason) {
    setClinicalFormError("#clinicalInboxError", "La causa es obligatoria para esta decisión.");
    $("#clinicalInboxReason")?.focus();
    return;
  }
  clinicalInboxBusy = true;
  const submit = $("#clinicalInboxResolveBtn");
  if (submit) submit.disabled = true;
  setClinicalFormError("#clinicalInboxError");
  try {
    const payload = await careScheduleJson(`/api/clinical/treatment-workflow-requests/${encodeURIComponent(item.id)}/resolve`, {
      method: "POST",
      body: JSON.stringify({ resolution, reason, ...(resumeDate ? { resumeDate } : {}) })
    });
    const patientId = String(item.patientId || payload.item?.patientId || "");
    clinicalInboxItems = clinicalInboxItems.filter((entry) => String(entry.id) !== String(item.id));
    updateClinicalInboxBadge();
    closeClinicalInbox();
    await refreshWorkflowClinicalSurfaces(patientId, payload);
    toast(resolution === "prescription_confirmed"
      ? "Prescripción confirmada para el tratamiento y ciclo actuales"
      : "Decisión registrada en la historia clínica");
    if (clinicalInboxItems.length) window.setTimeout(() => {
      if (!hasBlockingClinicalModal()) void openClinicalInbox({ explicit: false });
      else clinicalInboxAutoOpenPending = true;
    }, 250);
  } catch (error) {
    setClinicalFormError("#clinicalInboxError", error.message || "No se pudo registrar la decisión.");
  } finally {
    clinicalInboxBusy = false;
    if (submit) submit.disabled = false;
  }
}

function wireExplicitModalDismissal() {
  document.addEventListener("click", (event) => {
    const backdrop = event.target.closest?.(".modal-backdrop, .lira-observation-backdrop");
    if (!backdrop || event.target !== backdrop) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    const closeButton = backdrop.querySelector(
      '[aria-label^="Cerrar"], [title^="Cerrar"], [data-care-manager-detail-action="close-observation"]'
    );
    closeButton?.focus?.({ preventScroll: true });
  }, true);
}

function wireEvents() {
  wireExplicitModalDismissal();
  wireClinicalSessionEvents();
  $("#newPatientBtn")?.addEventListener("click", openNewPatientModal);
  $("#closeNewPatientBtn")?.addEventListener("click", closeNewPatientModal);
  $("#cancelNewPatientBtn")?.addEventListener("click", closeNewPatientModal);
  $("#newPatientForm")?.addEventListener("submit", createNewPatient);
  $("#newPatientForm")?.addEventListener("input", (event) => {
    event.target.removeAttribute?.("aria-invalid");
    hideNewPatientError();
  });
  $("#openLiraImportBtn")?.addEventListener("click", () => openLiraImportModal());
  $("#refreshActiveLiraPatientBtn")?.addEventListener("click", refreshActiveLiraPatient);
  $("#closeActivePatientBtn")?.addEventListener("click", closeActivePatient);
  $("#closeLiraImportBtn")?.addEventListener("click", closeLiraImportModal);
  $("#cancelLiraImportBtn")?.addEventListener("click", closeLiraImportModal);
  $("#refreshLiraStatusBtn")?.addEventListener("click", refreshLiraStatus);
  $("#confirmLiraImportBtn")?.addEventListener("click", importSelectedLiraPatient);
  wireCareEvents();
  $("#liraPatientSearchForm")?.addEventListener("submit", (event) => {
    event.preventDefault();
    searchLiraPatients($("#liraPatientSearchInput")?.value || "");
  });
  $("#liraPatientSearchInput")?.addEventListener("input", (event) => {
    window.clearTimeout(liraImportSearchTimer);
    clearLiraPatientSelection();
    const query = event.target.value.trim();
    liraImportSearchTimer = window.setTimeout(() => searchLiraPatients(query), 280);
  });
  $("#liraPatientResults")?.addEventListener("click", (event) => {
    const result = event.target.closest("[data-lira-patient-id]");
    if (result) selectLiraPatient(result.dataset.liraPatientId);
  });
  $("#liraPatientResults")?.addEventListener("keydown", handleLiraResultsKeydown);
  $("#printBtn").addEventListener("click", printClinicalDocument);
  const clinicalScrollArea = $(".clinical-panel .panel-scroll");
  clinicalScrollArea?.addEventListener("scroll", updateClinicalScrollEndButton, { passive: true });
  window.addEventListener("scroll", updateClinicalScrollEndButton, { passive: true });
  document.body.addEventListener("scroll", updateClinicalScrollEndButton, { passive: true });
  $("#clinicalScrollEndBtn")?.addEventListener("click", scrollClinicalDocumentToEnd);
  if (window.ResizeObserver && clinicalScrollArea) {
    clinicalScrollResizeObserver = new ResizeObserver(updateClinicalScrollEndButton);
    clinicalScrollResizeObserver.observe(clinicalScrollArea);
    clinicalScrollResizeObserver.observe($("#clinicalDocument"));
  }
  window.addEventListener("beforeprint", preparePrintDocument);
  window.addEventListener("afterprint", restorePrintDocument);
  $("#copySummaryBtn").addEventListener("click", copySummary);
  $("#closePatientModalBtn").addEventListener("click", closePatientModal);
  $("#cancelPatientModalBtn").addEventListener("click", closePatientModal);
  $("#savePatientModalBtn").addEventListener("click", async () => {
    await saveState();
    closePatientModal();
  });
  $("#closeEvolutionModalBtn").addEventListener("click", closeEvolutionModal);
  $("#cancelEvolutionModalBtn").addEventListener("click", closeEvolutionModal);
  $("#deleteEvolutionBtn").addEventListener("click", deleteEditingEvolution);
  $("#closeDiagnosticClassificationModalBtn")?.addEventListener("click", closeDiagnosticClassificationModal);
  $("#closeSectionEditModalBtn").addEventListener("click", closeSectionEditModal);
  $("#cancelSectionEditModalBtn").addEventListener("click", closeSectionEditModal);
  $("#saveSectionEditBtn").addEventListener("click", saveSectionEdit);
  $("#addSectionStudyRowBtn").addEventListener("click", () => addSectionStudyRow({}, { prepend: true }));
  $("#sectionStudiesRows").addEventListener("click", handleSectionStudyRowAction);
  $("#sectionStructuredFields").addEventListener("input", updateStructuredSectionDerivedFields);
  $("#sectionStructuredFields").addEventListener("click", (event) => {
    if (event.target.closest('[data-action="prefill-section-exam"]')) prefillStructuredPhysicalExam();
  });
  $("#closeSectionHistoryModalBtn").addEventListener("click", closeSectionHistoryModal);
  $("#closePrescriptionPreviewBtn")?.addEventListener("click", closePrescriptionPreview);
  $("#cancelPrescriptionPreviewBtn")?.addEventListener("click", closePrescriptionPreview);
  $("#printPrescriptionPreviewBtn")?.addEventListener("click", () => {
    if (pendingSystemicDraft) {
      confirmAndPrintSystemicForm();
      return;
    }
    const item = state.prescriptions.find((entry) => entry.id === prescriptionPreviewId);
    if (item) printPrescriptionDraft(item);
  });
  $("#prescriptionPreviewBody")?.addEventListener("input", handleSystemicPreviewInput);
  $("#systemicTemplateSelect")?.addEventListener("change", renderSystemicTemplateDescription);
  $("#systemicPreviewToolbar")?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-systemic-page-nav]");
    if (!button) return;
    $("#systemicPreviewPages")?.querySelector(`[data-systemic-page=\"${button.dataset.systemicPageNav}\"]`)?.scrollIntoView({ behavior: "smooth", block: "start" });
  });
  $("#closeStudyImageModalBtn")?.addEventListener("click", closeStudyImageModal);
  $("#printStudyImageBtn")?.addEventListener("click", () => printActiveStudyImage());
  $("#annotateStudyImageBtn")?.addEventListener("click", beginStudyImageAnnotation);
  $("#attachStudyImageBtn")?.addEventListener("click", attachActiveStudyImageToEvolution);
  $("#cancelStudyAnnotationBtn")?.addEventListener("click", cancelStudyImageAnnotation);
  $("#saveStudyAnnotationBtn")?.addEventListener("click", saveStudyImageAnnotation);
  $("#undoStudyAnnotationBtn")?.addEventListener("click", undoStudyAnnotation);
  $("#clearStudyAnnotationsBtn")?.addEventListener("click", clearStudyAnnotations);
  $$("[data-annotation-tool]").forEach((button) => button.addEventListener("click", () => setAnnotationTool(button.dataset.annotationTool)));
  $$("[data-annotation-fill]").forEach((button) => button.addEventListener("click", () => setAnnotationFill(button.dataset.annotationFill)));
  $$("[data-annotation-color]").forEach((button) => button.addEventListener("click", () => setAnnotationColor(button.dataset.annotationColor)));
  $$("[data-annotation-width]").forEach((button) => button.addEventListener("click", () => setAnnotationWidth(Number(button.dataset.annotationWidth))));
  $("#studyAnnotationColor")?.addEventListener("input", (event) => setAnnotationColor(event.target.value, { custom: true }));
  $("#studyAnnotationCanvas")?.addEventListener("pointerdown", beginAnnotationPointer);
  $("#studyAnnotationCanvas")?.addEventListener("pointermove", moveAnnotationPointer);
  $("#studyAnnotationCanvas")?.addEventListener("pointerup", endAnnotationPointer);
  $("#studyAnnotationCanvas")?.addEventListener("pointercancel", endAnnotationPointer);
  $("#studyAnnotationCanvas")?.addEventListener("pointerleave", hideAnnotationEraserCursor);
  $("#evolutionAttachmentBand")?.addEventListener("click", handleEvolutionAttachmentAction);

  document.addEventListener("keydown", (event) => {
    if (event.key === "Tab" && $("#liraImportModal")?.classList.contains("open")) {
      trapLiraImportFocus(event);
      return;
    }
  });

  document.addEventListener("pointerdown", (event) => {
    if (!event.target.closest('[data-action="highlight-clinical-selection"], [data-action="remove-clinical-highlight"]')) return;
    pendingClinicalSelection = captureClinicalSelection();
    event.preventDefault();
  });

  document.addEventListener("click", (event) => {
    const diagnosticSaveButton = event.target.closest("[data-diagnostic-save]");
    if (diagnosticSaveButton) {
      saveDiagnosticClassifications();
    }

    const diagnosticAjccRetry = event.target.closest("[data-diagnostic-ajcc-retry]");
    if (diagnosticAjccRetry) {
      loadDiagnosticAjccSite({ force: true });
    }

    const retryTimelineButton = event.target.closest('[data-action="retry-timeline-ai"]');
    if (retryTimelineButton) {
      timelineAiAttempted = false;
      timelineAiError = "";
      generateTimelineWithLlm();
    }

    const timelineFilter = event.target.closest("[data-timeline-filter]");
    if (timelineFilter) toggleTimelineFilter(timelineFilter.dataset.timelineFilter);

    const milestonesButton = event.target.closest('[data-action="toggle-milestones"]');
    if (milestonesButton) {
      timelineMilestonesOnly = !timelineMilestonesOnly;
      renderRightTimeline();
    }

    const resetTimelineButton = event.target.closest('[data-action="reset-timeline"]');
    if (resetTimelineButton) {
      window.clearTimeout(timelineSearchTimer);
      timelineFilters.clear();
      timelineMilestonesOnly = false;
      timelineSearchQuery = "";
      timelineSelection = null;
      renderRightTimeline();
    }

    const clinicalPeriodEntry = event.target.closest("#clinicalDocument [data-clinical-date]");
    const currentSelection = window.getSelection();
    if (clinicalPeriodEntry && !event.target.closest("button") && (!currentSelection || currentSelection.isCollapsed)) {
      focusTimelineDate(clinicalPeriodEntry.dataset.clinicalDate);
    }

    const expandTimelineButton = event.target.closest('[data-action="expand-timeline"]');
    if (expandTimelineButton) toggleAllTimelineSections(expandTimelineButton);

    const timelinePeriodToggle = event.target.closest('[data-action="toggle-timeline-period"]');
    if (timelinePeriodToggle) {
      event.preventDefault();
      event.stopPropagation();
      const details = timelinePeriodToggle.closest("details.right-timeline-year, details.right-timeline-month");
      if (details) details.open = !details.open;
      return;
    }

    const timelinePeriod = event.target.closest("#rightTimeline .right-timeline-year-head, #rightTimeline .right-timeline-month-head");
    if (timelinePeriod) {
      event.preventDefault();
      focusClinicalPeriod(timelinePeriod.dataset.start, timelinePeriod.dataset.end, timelinePeriod);
    }

    const timelineDatedEntry = event.target.closest("#rightTimeline .right-timeline-item[data-clinical-date], #rightTimeline .right-timeline-day[data-clinical-date]");
    if (timelineDatedEntry && !timelinePeriod && !event.target.closest("button, a, input, select, textarea")) {
      if (timelineDatedEntry.matches(".right-timeline-item") && timelineDatedEntry.dataset.sourceRecordId) {
        focusClinicalRecord(timelineDatedEntry.dataset.sourceRecordType, timelineDatedEntry.dataset.sourceRecordId, timelineDatedEntry);
      } else {
        focusClinicalPeriod(timelineDatedEntry.dataset.clinicalDate, timelineDatedEntry.dataset.clinicalDate, timelineDatedEntry);
      }
    }

    const patientButton = event.target.closest('[data-action="edit-patient"]');
    if (patientButton) openPatientModal();

    const clinicalHighlightButton = event.target.closest('[data-action="highlight-clinical-selection"]');
    if (clinicalHighlightButton) highlightSelectedClinicalText();

    const clinicalUnhighlightButton = event.target.closest('[data-action="remove-clinical-highlight"]');
    if (clinicalUnhighlightButton) removeSelectedClinicalHighlight();

    const clinicalSearchToggle = event.target.closest('[data-action="toggle-clinical-search"]');
    if (clinicalSearchToggle) toggleClinicalSearch(clinicalSearchToggle);

    const evolutionButton = event.target.closest('[data-action="open-evolution"]');
    if (evolutionButton) openEvolutionModal("evolucion");

    const diagnosisButton = event.target.closest('[data-action="open-diagnosis"]');
    if (diagnosisButton) openDiagnosticClassificationModal(diagnosisButton);

    const milestoneButton = event.target.closest('[data-action="toggle-milestone"]');
    if (milestoneButton) toggleEvolutionMilestone(milestoneButton.dataset.id);

    const sectionMilestoneButton = event.target.closest('[data-action="toggle-section-milestone"]');
    if (sectionMilestoneButton) toggleSectionMilestone(sectionMilestoneButton.dataset.sectionKey);

    const sectionEditButton = event.target.closest('[data-action="edit-section"]');
    if (sectionEditButton) openSectionEditModal(sectionEditButton.dataset.sectionKey);

    const sectionHistoryButton = event.target.closest('[data-action="view-section-history"]');
    if (sectionHistoryButton) openSectionHistoryModal(sectionHistoryButton.dataset.sectionKey);

    const evolutionHistoryButton = event.target.closest('[data-action="view-evolution-history"]');
    if (evolutionHistoryButton) openEvolutionHistoryModal(evolutionHistoryButton.dataset.id);

    const agentNavigation = event.target.closest("[data-agent-nav-date], [data-agent-nav-text]");
    if (agentNavigation) navigateClinicalFromAgent(agentNavigation.dataset.agentNavDate || "", agentNavigation.dataset.agentNavText || agentNavigation.textContent);

    const researchNavigation = event.target.closest('[data-action="focus-research-record"]');
    if (researchNavigation) focusResearchRecord(researchNavigation.dataset.id);
  });

  document.addEventListener("keydown", (event) => {
    if (!['Enter', ' '].includes(event.key)) return;
    const timelineItem = event.target.closest("#rightTimeline .right-timeline-item[role='button']");
    if (!timelineItem) return;
    event.preventDefault();
    if (timelineItem.dataset.sourceRecordId) {
      focusClinicalRecord(timelineItem.dataset.sourceRecordType, timelineItem.dataset.sourceRecordId, timelineItem);
    } else {
      focusClinicalPeriod(timelineItem.dataset.clinicalDate, timelineItem.dataset.clinicalDate, timelineItem);
    }
  });

  $$("[data-evolution-mode]").forEach((button) => {
    button.addEventListener("click", () => setEvolutionMode(button.dataset.evolutionMode));
  });

  $$("[data-tab]").forEach((button) => {
    button.addEventListener("click", () => setActiveTab(button.dataset.tab));
  });

  $$("[data-right-tab]").forEach((button) => {
    button.addEventListener("click", () => setRightTab(button.dataset.rightTab));
  });
  window.addEventListener("resize", updateRightTabLabels);
  window.addEventListener("resize", updateClinicalScrollEndButton);

  document.addEventListener("input", handleBindableEdit);
  document.addEventListener("input", (event) => {
    if (event.target.matches("[data-diagnostic-ajcc-stage]")) {
      updateDiagnosticAjccStageDraft(event.target);
      return;
    }
    if (!event.target.matches("[data-timeline-search], [data-clinical-search]")) return;
    const clinicalSearch = event.target.matches("[data-clinical-search]");
    timelineSearchQuery = event.target.value;
    window.clearTimeout(timelineSearchTimer);
    timelineSearchTimer = window.setTimeout(() => {
      renderRightTimeline();
      highlightTimelineSearchMatches({ scrollToFirst: true });
      syncClinicalSearchInput();
      const search = $(clinicalSearch ? "[data-clinical-search]" : "[data-timeline-search]");
      search?.focus();
      search?.setSelectionRange(search.value.length, search.value.length);
    }, 180);
  });
  document.addEventListener("change", handleBindableEdit);
  document.addEventListener("change", (event) => {
    if (event.target.matches("[data-diagnostic-select]")) {
      selectDiagnosticClassification(event.target.dataset.diagnosticSelect, event.target.value);
      return;
    }
    if (event.target.matches("[data-diagnostic-ajcc-field], [data-diagnostic-ajcc-axis]")) {
      updateDiagnosticAjccDraft(event.target);
    }
  });

  $("#addEvolutionBtn").addEventListener("click", addEvolution);
  $("#clearEvolutionBtn").addEventListener("click", clearEvolutionForm);
  $("#buildEvolutionBtn")?.addEventListener("click", buildEvolutionDraft);
  $("#prefillExamBtn")?.addEventListener("click", prefillPhysicalExam);

  $("#studySearch")?.addEventListener("input", renderStudyList);
  $("#studyTypeFilter")?.addEventListener("change", renderStudyList);
  $("#studyForm").addEventListener("submit", addStudy);
  $("#resetStudyBtn").addEventListener("click", resetStudyForm);
  $("#studyFile").addEventListener("change", (event) => setPendingStudyFile(event.target.files[0]));
  $("#openStudyUploadBtn")?.addEventListener("click", () => openStudyUploadModal());
  $("#openStudyTemplateBtn")?.addEventListener("click", openStudyTemplateModal);
  $("#closeStudyUploadBtn")?.addEventListener("click", closeStudyUploadModal);
  $("#cancelStudyUploadBtn")?.addEventListener("click", closeStudyUploadModal);
  $("#confirmStudyUploadBtn")?.addEventListener("click", uploadPendingStudyFiles);
  $("#studyUploadFiles")?.addEventListener("change", (event) => {
    addPendingStudyFiles(event.target.files);
    event.target.value = "";
  });
  $("#studyUploadQueue")?.addEventListener("click", handleStudyUploadQueueAction);
  $("#studyUploadZone")?.addEventListener("keydown", (event) => {
    if (!["Enter", " "].includes(event.key)) return;
    event.preventDefault();
    $("#studyUploadFiles")?.click();
  });
  $("#closeStudyTemplateBtn")?.addEventListener("click", closeStudyTemplateModal);
  $("#cancelStudyTemplateBtn")?.addEventListener("click", closeStudyTemplateModal);
  $("#confirmStudyTemplateBtn")?.addEventListener("click", importSelectedStudyTemplate);
  $("#studyTemplateSearch")?.addEventListener("input", renderStudyTemplateGallery);
  $("#studyTemplateCategory")?.addEventListener("change", renderStudyTemplateGallery);
  $("#studyTemplateGallery")?.addEventListener("click", handleStudyTemplateGalleryAction);
  document.addEventListener("paste", handleStudyClipboardPaste);
  document.addEventListener("pointermove", updateStudyClipboardPointerContext, { passive: true });
  document.addEventListener("pointerdown", updateStudyClipboardPointerContext, { passive: true });
  document.addEventListener("pointerout", handleStudyClipboardPointerOut, { passive: true });
  $("#researchForm")?.addEventListener("submit", saveResearchRecord);
  $("#resetResearchBtn")?.addEventListener("click", resetResearchForm);
  $("#researchTemplateSelect")?.addEventListener("change", (event) => setResearchTemplate(event.target.value));

  $("#agentChatForm")?.addEventListener("submit", (event) => {
    event.preventDefault();
    sendAgentMessage();
  });
  $("#clearAgentChatBtn")?.addEventListener("click", clearAgentChat);
  $("#agentChatInput")?.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      sendAgentMessage();
    }
  });
  document.addEventListener("click", (event) => {
    const promptButton = event.target.closest("[data-agent-prompt]");
    if (!promptButton) return;
    const input = $("#agentChatInput");
    input.value = promptButton.dataset.agentPrompt;
    input.focus();
    input.setSelectionRange(input.value.length, input.value.length);
  });
  document.addEventListener("pointermove", handleAgentChartPointer);
  document.addEventListener("pointerout", (event) => {
    if (event.target.closest("[data-chart-tooltip]") && !event.relatedTarget?.closest?.("[data-chart-tooltip]")) hideAgentChartTooltip(event.target.closest(".agent-chart"));
  });
  $$('[data-prescription-type]').forEach((button) => button.addEventListener("click", () => setPrescriptionType(button.dataset.prescriptionType)));
  $("#prescriptionForm")?.addEventListener("submit", addPrescriptionDraft);
  $("#rxClearBtn")?.addEventListener("click", clearPrescriptionEditor);
  $("#rxExportBtn")?.addEventListener("click", exportPrescriptionDrafts);
  $("#rxDrugSearch")?.addEventListener("input", (event) => {
    clearTimeout(medicationSearchTimer);
    medicationSearchTimer = setTimeout(() => searchMedicationCatalog(event.target.value), 180);
  });
  $("#rxDrugResults")?.addEventListener("click", handleMedicationResult);
  $("#rxMedicationPresets")?.addEventListener("click", handleMedicationPreset);
  $("#rxCertificateType")?.addEventListener("change", fillCertificateTemplate);
  $("#protocolCategory")?.addEventListener("change", renderProtocolSchemeOptions);
  $("#protocolSource")?.addEventListener("change", () => { protocolData=null; protocolDetail=null; loadProtocols(); });
  $("#protocolScheme")?.addEventListener("change", loadProtocolDetail);
  $("#protocolDrug")?.addEventListener("change", renderProtocolDrugDetail);
  window.addEventListener("storage", (event) => {
    if (event.key === "hcop-protocol-catalog-updated") {
      protocolData = null;
      protocolDetail = null;
      if ($('[data-right-panel="protocols"]')?.classList.contains("active")) loadProtocols();
    }
    if (event.key === "hcop-configuration-updated") {
      localGuides = [];
      diagnosticEquivalences = null;
      diagnosticDisplaySettingsLoaded = false;
      researchTemplatesLoaded = false;
      studyTemplatesLoaded = false;
      studyTemplates = [];
      studyTemplateSelectedId = "";
      if ($('[data-right-panel="research"]')?.classList.contains("active")) loadResearchTemplates(true);
      if ($("#studyTemplateModal")?.classList.contains("open")) void reloadOpenStudyTemplateCatalog();
      const frame = $("#clinicalCalculatorFrame");
      if (frame) frame.src = `/herramientas/index.html?embedded=1&v=${Date.now()}`;
      void loadDiagnosticDisplaySettings({ force: true });
      if (
        $("#careTreatmentManagerModal")?.classList.contains("open")
        && (careScheduleMode === "chairs" || careScheduleMode === "pharmacy")
      ) {
        void loadCareSchedule();
      }
    }
  });
  $$('[data-tool-tab]').forEach((button)=>button.addEventListener('click',()=>setToolTab(button.dataset.toolTab)));
  $("#tnmStagingForm")?.addEventListener("change", handleAjcc8Change);
  $("#guideSearch")?.addEventListener("input", renderGuideLibrary);
  $("#refreshGuidesBtn")?.addEventListener("click", () => loadGuideLibrary(true));
  $("#guideList")?.addEventListener("click", (event)=>{const button=event.target.closest("[data-guide-name]");if(button)openGuidePdf(button.dataset.guideName)});
  $("#closeGuidePdfBtn")?.addEventListener("click", closeGuidePdf);
  $("#closeGuidePdfFooterBtn")?.addEventListener("click", closeGuidePdf);
  $("#calculatorType")?.addEventListener("change", renderCalculatorFields);
  $("#calculatorFields")?.addEventListener("input", calculateClinicalTool);
  $("#systemConfigForm")?.addEventListener("submit", saveSystemConfig);
  document.addEventListener("click",(event)=>{const button=event.target.closest("[data-update-catalog]");if(button)updateCatalog(button.dataset.updateCatalog,button)});
  $("#protocolOverview")?.addEventListener("click", (event) => {
    const button=event.target.closest("[data-protocol-drug-index]");
    if(!button)return;
    $("#protocolDrug").value=button.dataset.protocolDrugIndex;
    renderProtocolDrugDetail();
  });
  document.addEventListener("click", (event) => {
    const preset = event.target.closest("[data-study-preset]");
    if (preset) applyStudyPreset(preset.dataset.studyPreset);
    const draftAction = event.target.closest("[data-rx-action]");
    if (draftAction) handlePrescriptionDraftAction(draftAction.dataset.rxAction, draftAction.dataset.id);
  });

  const uploadZone = $("#uploadZone");
  uploadZone.addEventListener("dragover", (event) => {
    event.preventDefault();
    uploadZone.classList.add("dragging");
  });
  uploadZone.addEventListener("dragleave", () => uploadZone.classList.remove("dragging"));
  uploadZone.addEventListener("drop", (event) => {
    event.preventDefault();
    uploadZone.classList.remove("dragging");
    setPendingStudyFile(event.dataTransfer.files[0]);
  });

  $("#studyList").addEventListener("click", handleStudyAction);
  $("#studyList").addEventListener("dragover", handleStudyListDragOver);
  $("#studyList").addEventListener("dragleave", handleStudyListDragLeave);
  $("#studyList").addEventListener("drop", handleStudyListDrop);
  const studyUploadZone = $("#studyUploadZone");
  studyUploadZone?.addEventListener("dragover", (event) => {
    event.preventDefault();
    if (studyUploadBusy) return;
    event.dataTransfer.dropEffect = "copy";
    studyUploadZone.classList.add("dragging");
  });
  studyUploadZone?.addEventListener("dragleave", (event) => {
    if (event.relatedTarget && studyUploadZone.contains(event.relatedTarget)) return;
    studyUploadZone.classList.remove("dragging");
  });
  studyUploadZone?.addEventListener("drop", (event) => {
    event.preventDefault();
    studyUploadZone.classList.remove("dragging");
    if (!studyUploadBusy) addPendingStudyFiles(event.dataTransfer.files);
  });
  $("#studyImageList")?.addEventListener("click", handleStudyImageAction);
  $("#studyImageList")?.addEventListener("keydown", (event) => {
    if ((event.key === "Enter" || event.key === " ") && event.target.matches(".study-image-tile")) {
      event.preventDefault();
      toggleStudyImageMenu(event.target.dataset.imageId);
    }
  });
  $("#studyLocalImageList")?.addEventListener("click", handleStudyImageAction);
  $("#studyLocalImageList")?.addEventListener("keydown", (event) => {
    if ((event.key === "Enter" || event.key === " ") && event.target.matches(".study-image-tile")) {
      event.preventDefault();
      toggleStudyImageMenu(event.target.dataset.imageId);
    }
  });
  $("#clinicalDocument").addEventListener("click", handleTimelineAction);
  $("#rightTimeline").addEventListener("toggle", handleTimelineDetailsToggle, true);

  $$(".study-repos [data-repo-url]").forEach((button) => {
    button.addEventListener("click", () => window.open(button.dataset.repoUrl, "_blank", "noopener"));
  });

  wireSplitter();
}

function initializeLiraImporter() {
  refreshLiraStatus();
}

function openNewPatientModal() {
  const modal = $("#newPatientModal");
  const form = $("#newPatientForm");
  if (!modal || !form || modal.classList.contains("open")) return;
  newPatientReturnFocus = document.activeElement;
  form.reset();
  $$("[aria-invalid]", form).forEach((field) => field.removeAttribute("aria-invalid"));
  const birthDate = $('[name="birthDate"]', form);
  if (birthDate) birthDate.max = today();
  hideNewPatientError();
  modal.classList.add("open");
  modal.setAttribute("aria-hidden", "false");
  $("#newPatientBtn")?.setAttribute("aria-expanded", "true");
  window.requestAnimationFrame(() => $('[name="firstName"]', form)?.focus());
}

function closeNewPatientModal({ force = false } = {}) {
  const modal = $("#newPatientModal");
  if (!modal?.classList.contains("open") || (newPatientBusy && !force)) return;
  modal.classList.remove("open");
  modal.setAttribute("aria-hidden", "true");
  $("#newPatientBtn")?.setAttribute("aria-expanded", "false");
  const returnFocus = newPatientReturnFocus;
  newPatientReturnFocus = null;
  if (returnFocus?.isConnected) returnFocus.focus();
}

function hideNewPatientError() {
  const error = $("#newPatientError");
  if (!error) return;
  error.hidden = true;
  error.textContent = "";
}

function showNewPatientError(message, fieldName = "") {
  const form = $("#newPatientForm");
  const field = fieldName ? $(`[name="${fieldName}"]`, form) : null;
  if (field) {
    field.setAttribute("aria-invalid", "true");
    field.focus();
  }
  const error = $("#newPatientError");
  if (!error) return;
  error.textContent = message;
  error.hidden = false;
}

function validateNewPatientForm(form) {
  const input = Object.fromEntries(
    [...new FormData(form).entries()].map(([key, value]) => [key, String(value || "").trim()])
  );
  $$("[aria-invalid]", form).forEach((field) => field.removeAttribute("aria-invalid"));
  if (!input.firstName) return { error: "El nombre es obligatorio.", field: "firstName" };
  if (!input.lastName) return { error: "El apellido es obligatorio.", field: "lastName" };
  if (!input.dni && !input.medicalRecord) {
    return { error: "Informe el DNI o el numero de historia clinica.", field: "dni" };
  }
  if (input.birthDate && input.birthDate > today()) {
    return { error: "La fecha de nacimiento no puede ser futura.", field: "birthDate" };
  }
  const emailField = $('[name="email"]', form);
  if (input.email && emailField && !emailField.validity.valid) {
    return { error: "El correo electronico no es valido.", field: "email" };
  }
  return { input };
}

function setNewPatientBusy(busy) {
  newPatientBusy = busy;
  const modal = $("#newPatientModal");
  const form = $("#newPatientForm");
  const submit = $("#saveNewPatientBtn");
  const cancel = $("#cancelNewPatientBtn");
  const close = $("#closeNewPatientBtn");
  if (modal) {
    modal.classList.toggle("is-busy", busy);
    modal.setAttribute("aria-busy", String(busy));
  }
  $$("input, select", form).forEach((field) => { field.disabled = busy; });
  if (submit) {
    submit.disabled = busy;
    $("span", submit).textContent = busy ? "Creando ficha..." : "Crear y abrir ficha";
    submit.classList.toggle("is-loading", busy);
  }
  if (cancel) cancel.disabled = busy;
  if (close) close.disabled = busy;
  refreshIcons();
}

async function createNewPatient(event) {
  event.preventDefault();
  if (newPatientBusy) return;
  const form = event.currentTarget;
  const validation = validateNewPatientForm(form);
  if (validation.error) {
    showNewPatientError(validation.error, validation.field);
    return;
  }

  const previousState = state;
  const previousStorageKey = getClinicalStorageKey(previousState);
  const previousStoredState = clinicalLocalCacheAllowed() ? localStorage.getItem(previousStorageKey) : null;
  let serverCreated = false;
  setNewPatientBusy(true);
  hideNewPatientError();
  try {
    const response = await fetch("/api/clinical/patients", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(validation.input)
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok === false) {
      const existing = payload.existingPatient;
      const detail = existing
        ? ` Ya corresponde a ${existing.fullName || "un paciente existente"} (ID ${existing.id}).`
        : "";
      const error = new Error(`${payload.error || "No se pudo crear el paciente."}${detail}`);
      error.code = payload.code || "";
      error.field = payload.field || payload.duplicateFields?.[0] || "";
      throw error;
    }
    serverCreated = true;
    const patientId = String(payload.patientId || "");
    await loadState({ forceServer: true });
    if (getActiveLiraPatientId() !== patientId ||
        String(state?.patient?.liraId || "") !== patientId ||
        String(state?.meta?.liraImport?.origin || "") !== "local") {
      throw new Error("El paciente se creo, pero no se pudo activar su ficha local.");
    }
    resetPatientContextAfterLiraImport();
    renderAll();
    closeNewPatientModal({ force: true });
    setActiveTab("historia");
    setRightTab("studies");
    refreshCareWorkspace({ force: true });
    toast("Paciente creado con una historia clinica en blanco");
  } catch (error) {
    if (serverCreated) {
      toast("El paciente se creo. La ficha se recargara para terminar de abrirlo.");
      window.setTimeout(() => window.location.reload(), 900);
    } else {
      state = previousState;
      if (clinicalLocalCacheAllowed()) {
        if (previousStoredState) localStorage.setItem(previousStorageKey, previousStoredState);
        else localStorage.removeItem(previousStorageKey);
      }
      showNewPatientError(error.message || "No se pudo crear el paciente.", error.field);
    }
  } finally {
    setNewPatientBusy(false);
  }
}

function currentHelpTopic() {
  const openModalTopics = [
    ["#newPatientModal.open", "patients"],
    ["#liraImportModal.open", "patients"],
    ["#careTreatmentManagerModal.open", {
      chairs: "scheduler-chairs",
      pharmacy: "scheduler-pharmacy",
      treatments: "treatment-detail",
      "new-treatment": "treatment-new"
    }[careScheduleMode] || "scheduler-chairs"],
    ["#careInfusionModal.open", "care"],
    ["#prescriptionPreviewModal.open", "prescription"],
    ["#studyTemplateModal.open", "studies"],
    ["#studyUploadModal.open", "studies"],
    ["#studyImageModal.open", "studies"],
    ["#guidePdfModal.open", "tools-guides"],
    ["#patientModal.open", "history"],
    ["#evolutionModal.open", "history"],
    ["#sectionEditModal.open", "history"],
    ["#sectionHistoryModal.open", "history"]
  ];
  const modalTopic = openModalTopics.find(([selector]) => $(selector))?.[1];
  if (modalTopic) return modalTopic;
  const rightTab = $('[data-right-tab][aria-selected="true"]')?.dataset.rightTab || "studies";
  if (rightTab === "tools") {
    const tool = $('[data-tool-tab].active')?.dataset.toolTab || "guides";
    return `tools-${tool}`;
  }
  return {
    studies: "studies",
    care: "care",
    prescription: "prescription",
    agent: "agent",
    research: "research",
    timeline: "timeline",
    protocols: "protocols"
  }[rightTab] || "overview";
}

function installModalHelpButtons() {
  const topics = {
    liraImportModal: "patients",
    careInfusionModal: "care",
    patientModal: "history",
    evolutionModal: "history",
    sectionEditModal: "history",
    sectionHistoryModal: "history",
    prescriptionPreviewModal: "prescription",
    studyImageModal: "studies",
    guidePdfModal: "tools-guides"
  };
  Object.entries(topics).forEach(([modalId, topic]) => {
    const modal = document.getElementById(modalId);
    const header = $(".modal-header", modal);
    if (!header || $("[data-help-trigger]", header)) return;
    const button = document.createElement("button");
    button.className = "section-help-button";
    button.type = "button";
    button.dataset.helpTrigger = "";
    button.dataset.helpTopic = topic;
    button.title = "Ayuda de esta seccion";
    button.setAttribute("aria-label", "Ayuda de esta seccion");
    button.innerHTML = '<i data-lucide="circle-help"></i>';
    const close = $(".icon-button", header);
    if (close) close.before(button);
    else header.append(button);
  });
  refreshIcons();
}

function initializeGuidedHelp() {
  if (!window.HcopHelp) return;
  installModalHelpButtons();
  window.HcopHelp.init({
    page: "main",
    menuTrigger: "#openHelpMenuBtn",
    getContext: currentHelpTopic,
    startFromLocation: true,
    adapters: {
      rightTab: (value) => setRightTab(value),
      toolTab: (value) => setToolTab(value),
      careView: (value) => setCareView(value),
      scheduleMode: (value) => setCareScheduleMode(value),
      hospitalTab: (value) => activateCareHospitalTab(value),
      modal: async (value) => {
        if (value === "liraImportModal") openLiraImportModal();
        if (value === "careTreatmentManagerModal") await openCareTreatmentManagerModal({ mode: "treatments" });
      }
    }
  });
}

function openLiraImportModal({ auto = false } = {}) {
  const modal = $("#liraImportModal");
  if (!modal || modal.classList.contains("open")) return;
  liraImportReturnFocus = auto ? $("#openLiraImportBtn") : document.activeElement;
  modal.classList.add("open");
  modal.setAttribute("aria-hidden", "false");
  $("#openLiraImportBtn")?.setAttribute("aria-expanded", "true");
  void refreshLiraStatus().then((available) => {
    if (!available || !modal.classList.contains("open")) return;
    if (!String($("#liraPatientSearchInput")?.value || "").trim()) void searchLiraPatients("");
  });
  window.requestAnimationFrame(() => $("#liraPatientSearchInput")?.focus());
}

function closeLiraImportModal({ force = false } = {}) {
  const modal = $("#liraImportModal");
  if (!modal?.classList.contains("open") || (liraImportBusy && !force)) return;
  modal.classList.remove("open");
  modal.setAttribute("aria-hidden", "true");
  $("#openLiraImportBtn")?.setAttribute("aria-expanded", "false");
  liraImportSearchController?.abort();
  liraImportPreviewController?.abort();
  const returnFocus = liraImportReturnFocus;
  liraImportReturnFocus = null;
  if (returnFocus?.isConnected) returnFocus.focus();
}

function trapLiraImportFocus(event) {
  const modal = $("#liraImportModal");
  if (!modal) return;
  const focusable = $$('button:not([disabled]), input:not([disabled]), [href], [tabindex]:not([tabindex="-1"])', modal)
    .filter((element) => !element.hidden && element.getClientRects().length);
  if (!focusable.length) return;
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

async function refreshLiraStatus() {
  const button = $("#refreshLiraStatusBtn");
  if (button) button.disabled = true;
  setLiraSourceStatus("loading", "Comprobando la base clinica", "Consultando la informacion local en vivo.");
  try {
    const response = await fetch("/api/lira/status", { cache: "no-store" });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok === false) throw new Error(payload.error || "No se pudo consultar la base clinica");
    const explicitAvailability = [payload.available, payload.ready, payload.configured, payload.patientCatalogExists]
      .find((value) => typeof value === "boolean");
    const available = explicitAvailability !== false;
    const patientCount = firstFiniteNumber(payload.patientCount, payload.totalPatients, payload.total, payload.counts?.patients);
    const relationalCount = firstFiniteNumber(payload.relationalCount, payload.historiesCount, payload.counts?.histories, payload.counts?.relational);
    const details = [
      Number.isFinite(patientCount) ? `${patientCount} pacientes en el catalogo` : "Base clinica accesible",
      Number.isFinite(relationalCount) ? `${relationalCount} historias preparadas` : ""
    ].filter(Boolean).join(" - ");
    liraSourceAvailable = available;
    setLiraSourceStatus(available ? "ready" : "error", available ? "Base clinica disponible" : "Base clinica no disponible", payload.message || details);
    setLiraSearchEnabled(available);
    return available;
  } catch (error) {
    liraSourceAvailable = false;
    setLiraSourceStatus("error", "No se pudo acceder a la base clinica", error.message || "Compruebe que el servicio local este iniciado.");
    setLiraSearchEnabled(false);
    return false;
  } finally {
    if (button) button.disabled = false;
    refreshIcons();
  }
}

function setLiraSourceStatus(status, title, detail) {
  const element = $("#liraSourceStatus");
  if (!element) return;
  element.classList.remove("is-loading", "is-ready", "is-error");
  element.classList.add(`is-${status}`);
  $("strong", element).textContent = title;
  $("small", element).textContent = detail || "";
}

function getActiveLiraPatientId() {
  const patientId = String(state?.meta?.liraImport?.patientId || "").trim();
  return /^\d{1,18}$/.test(patientId) ? patientId : "";
}

function activePatientIsLocal() {
  return String(state?.meta?.liraImport?.origin || "") === "local";
}

function wireCareEvents() {
  $$('[data-care-view-target]').forEach((button) => {
    button.addEventListener("click", () => setCareView(button.dataset.careViewTarget));
  });
  $("#refreshCareBtn")?.addEventListener("click", () => refreshCareWorkspace({ force: true }));
  $("#openCareInfusionManagerBtn")?.addEventListener("click", () => openCareTreatmentManagerModal());
  $("#openCareTreatmentModalBtn")?.addEventListener("click", openCareTreatmentModal);
  $("#openCareInfusionModalBtn")?.addEventListener("click", () => openCareInfusionModal());
  $("#careHierarchyNewTreatmentBtn")?.addEventListener("click", openCareTreatmentModal);
  $("#careScheduleDate")?.addEventListener("change", handleCareScheduleDateChange);
  $("#careScheduleDate")?.addEventListener("blur", handleCareScheduleDateChange);
  $("#careScheduleCalendarDate")?.addEventListener("change", (event) => {
    if (!event.target.value) return;
    setCareScheduleDate(event.target.value);
    void loadCareSchedule();
  });
  $("#careScheduleRefreshBtn")?.addEventListener("click", refreshCareHospitalMode);
  $("#careScheduleTodayBtn")?.addEventListener("click", () => { setCareScheduleDate(careScheduleDateValue()); loadCareSchedule(); });
  $("#careSchedulePreviousDayBtn")?.addEventListener("click", () => shiftCareScheduleDate(-1));
  $("#careScheduleNextDayBtn")?.addEventListener("click", () => shiftCareScheduleDate(1));
  $$("[data-care-hospital-tab]").forEach((button) => {
    button.addEventListener("click", () => activateCareHospitalTab(button.dataset.careHospitalTab));
  });
  $("#careTreatmentManagerModal")?.addEventListener("click", handleCareHospitalAction);
  $("#careScheduleCandidateSearch")?.addEventListener("input", (event) => {
    careScheduleCandidateVisibleLimit = 200;
    renderCareScheduleCandidates();
    renderCareScheduleSearchHighlights();
    window.clearTimeout(careScheduleCandidateSearchTimer);
    careScheduleCandidateSearchTimer = window.setTimeout(() => loadCareScheduleCandidates(event.target.value), 280);
  });
  $("#careSchedulePharmacySearch")?.addEventListener("input", (event) => {
    careSchedulePharmacyVisibleLimit = 250;
    renderCareSchedulePharmacy();
    window.clearTimeout(careScheduleCandidateSearchTimer);
    careScheduleCandidateSearchTimer = window.setTimeout(() => loadCareScheduleCandidates(event.target.value), 280);
  });
  $("#careSchedulePharmacyRows")?.addEventListener("click", updateCareSchedulePharmacyState);
  $("#careScheduleCandidateFilter")?.addEventListener("change", () => {
    careScheduleCandidateVisibleLimit = 200;
    renderCareScheduleCandidates();
    renderCareScheduleAvailability();
  });
  $("#careSchedulePreviousChairsBtn")?.addEventListener("click", () => shiftCareScheduleChairViewport(-1));
  $("#careScheduleNextChairsBtn")?.addEventListener("click", () => shiftCareScheduleChairViewport(1));
  $("#careScheduleZoomInBtn")?.addEventListener("click", () => zoomCareScheduleChairViewport(-1));
  $("#careScheduleZoomOutBtn")?.addEventListener("click", () => zoomCareScheduleChairViewport(1));
  $("#careScheduleCandidates")?.addEventListener("dragstart", beginCareScheduleDrag);
  $("#careScheduleCandidates")?.addEventListener("click", selectCareScheduleCandidate);
  $("#careScheduleCandidates")?.addEventListener("keydown", selectCareScheduleCandidate);
  $("#careScheduleWorkflowActions")?.addEventListener("click", handleCareScheduleWorkflowActionChoice);
  $("#careScheduleWorkflowActionForm")?.addEventListener("submit", submitCareScheduleWorkflowAction);
  $("#closeCareScheduleWorkflowActionBtn")?.addEventListener("click", closeCareScheduleWorkflowActionModal);
  $("#cancelCareScheduleWorkflowActionBtn")?.addEventListener("click", closeCareScheduleWorkflowActionModal);
  $("#careScheduleSuspendKind")?.addEventListener("change", syncCareScheduleSuspensionFields);
  $("#careScheduleGrid")?.addEventListener("dragstart", beginCareScheduleDrag);
  $("#careScheduleGrid")?.addEventListener("dragover", handleCareScheduleDragOver);
  $("#careScheduleGrid")?.addEventListener("dragleave", (event) => { if (!event.currentTarget.contains(event.relatedTarget)) clearCareScheduleDragTarget(true); });
  $("#careScheduleGrid")?.addEventListener("drop", dropCareScheduleItem);
  $("#careScheduleGrid")?.addEventListener("click", removeCareScheduleAppointment);
  $("#careScheduleGrid")?.addEventListener("pointermove", showCareScheduleHoverCard);
  $("#careScheduleGrid")?.addEventListener("pointerleave", hideCareScheduleHoverCard);
  $("#openCareQrScannerBtn")?.addEventListener("click", openCareQrScannerModal);
  $("#closeCareQrScannerBtn")?.addEventListener("click", closeCareQrScannerModal);
  $("#closeCareQrScannerFooterBtn")?.addEventListener("click", closeCareQrScannerModal);
  $("#startCareQrCameraBtn")?.addEventListener("click", startCareQrCamera);
  $("#stopCareQrCameraBtn")?.addEventListener("click", () => stopCareQrCamera({ announce: true }));
  $("#careQrImageInput")?.addEventListener("change", handleCareQrImage);
  $("#careQrManualForm")?.addEventListener("submit", handleCareQrManualSubmit);
  $("#careQrScannerResult")?.addEventListener("click", handleCareQrResolvedAction);
  window.addEventListener("pagehide", () => stopCareQrCamera());
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") stopCareQrCamera();
  });
  $("#closeCareScheduleDetailBtn")?.addEventListener("click", closeCareScheduleDetailModal);
  $("#careScheduleDetailBody")?.addEventListener("click", updateCareScheduleDetailFlag);
  $("#careScheduleDetailBody")?.addEventListener("click", openCareQrTreatmentFromDetail);
  $("#careScheduleDetailBody")?.addEventListener("click", handleCareInfusionAction);
  $("#careScheduleDetailBody")?.addEventListener("input", syncCareQrAdministrationCompletion);
  $("#careScheduleDetailBody")?.addEventListener("change", syncCareQrAdministrationCompletion);
  $("#careScheduleDetailBody")?.addEventListener("submit", submitCareQrAdministrationCompletion);
  document.addEventListener("dragend", () => {
    careScheduleDrag = null;
    clearCareScheduleDragTarget(true);
    clearCareScheduleDraggingVisuals();
  });
  $("#careTreatmentHierarchy")?.addEventListener("click", handleCareTreatmentHierarchyAction);
  $("#careHierarchySearch")?.addEventListener("input", renderCareTreatmentHierarchy);
  [$("#careHierarchyDateFilter"), $("#careHierarchyStatusFilter")].filter(Boolean).forEach((filter) => {
    filter.addEventListener("change", renderCareTreatmentHierarchy);
  });
  $("#clearCareHierarchyFiltersBtn")?.addEventListener("click", () => {
    if ($("#careHierarchySearch")) $("#careHierarchySearch").value = "";
    if ($("#careHierarchyDateFilter")) $("#careHierarchyDateFilter").value = "";
    if ($("#careHierarchyStatusFilter")) $("#careHierarchyStatusFilter").value = "";
    renderCareTreatmentHierarchy();
  });
  $$('[data-care-treatment-surface]').forEach(wireCareTreatmentSurface);
  $("#careInfusionList")?.addEventListener("click", handleCareInfusionTableClick);
  $("#careInfusionList")?.addEventListener("keydown", (event) => {
    if (!["Enter", " "].includes(event.key) || !event.target.closest("tr[data-care-infusion-id]")) return;
    event.preventDefault();
    handleCareInfusionSelection(event);
  });
  $("#careInfusionActions")?.addEventListener("click", handleCareInfusionAction);
  $("#careInfusionPagination")?.addEventListener("click", handleCareInfusionPagination);
  $("#careInfusionLength")?.addEventListener("change", () => {
    careInfusionTableState.page = 0;
    careSelectedInfusionId = "";
    renderCareInfusions();
  });
  $("#careDrugResults")?.addEventListener("click", handleCareDrugAction);
  $("#careDrugSearch")?.addEventListener("input", (event) => {
    window.clearTimeout(careDrugSearchTimer);
    careDrugSearchTimer = window.setTimeout(() => loadCareDrugs(event.target.value), 250);
  });
  [$("#careInfusionStatusFilter"), $("#careInfusionDateFilter")].filter(Boolean).forEach((filter) => {
    filter.addEventListener("change", () => {
      careInfusionTableState.page = 0;
      careSelectedInfusionId = "";
      renderCareInfusions();
    });
  });
  [$("#careInfusionSearch"), $("#careInfusionInlineSearch")].filter(Boolean).forEach((search) => {
    search.addEventListener("input", (event) => {
      [$("#careInfusionSearch"), $("#careInfusionInlineSearch")].filter(Boolean).forEach((input) => {
        if (input !== event.target && input.value !== event.target.value) input.value = event.target.value;
      });
      careInfusionTableState.page = 0;
      careSelectedInfusionId = "";
      renderCareInfusions();
    });
  });
  $("#clearCareInfusionFiltersBtn")?.addEventListener("click", () => {
    if ($("#careInfusionDateFilter")) $("#careInfusionDateFilter").value = "";
    if ($("#careInfusionStatusFilter")) $("#careInfusionStatusFilter").value = "";
    if ($("#careInfusionSearch")) $("#careInfusionSearch").value = "";
    if ($("#careInfusionInlineSearch")) $("#careInfusionInlineSearch").value = "";
    careInfusionTableState.page = 0;
    careSelectedInfusionId = "";
    renderCareInfusions();
  });
  $("#careTreatmentForm")?.addEventListener("submit", submitCareTreatment);
  $("#careInfusionForm")?.addEventListener("submit", submitCareInfusion);
  $("#careTreatmentForm")?.addEventListener("change", (event) => {
    if (event.target.matches('[name="esquema"], [name="scheme"], [name="schemeId"]')) void renderCareTreatmentRequirements();
    if (event.target.matches('[name="diagnostico"], [name="diagnosis"], [name="diagnosisId"], [name="esquema"], [name="scheme"], [name="schemeId"]')) {
      renderCareProtocolCompatibility();
    }
    if (event.target.matches('[name="esquema"], [name="scheme"], [name="schemeId"], [name="cantidadCiclos"], [name="cycles"], [name="cicloInicial"], [name="initialCycle"], [name="fechaPrimerCiclo"]')) {
      renderCareTreatmentProjection();
    }
  });
  $("#careTreatmentForm")?.addEventListener("input", (event) => {
    if (event.target.matches('[name="cantidadCiclos"], [name="cycles"], [name="cicloInicial"], [name="initialCycle"], [name="fechaPrimerCiclo"]')) renderCareTreatmentProjection();
  });
  $("#careTreatmentForm")?.addEventListener("invalid", (event) => {
    if (event.target.closest("#careTreatmentRequirementList")) toast("Complete los requisitos obligatorios del esquema antes de guardar");
  }, true);
  $("#careTreatmentRequirementList")?.addEventListener("input", (event) => {
    if (!event.target.matches('input[type="number"]')) return;
    const confirmation = $("[data-care-requirements-confirm]", event.currentTarget);
    if (confirmation) confirmation.checked = false;
  });
  $("#careTreatmentRequirementList")?.addEventListener("click", (event) => {
    if (event.target.closest("[data-care-requirements-retry]")) void renderCareTreatmentRequirements();
  });
  $("#careTreatmentManagerDetail")?.addEventListener("click", handleCareTreatmentManagerDetailAction);
  $("#closeCareTreatmentManagerBtn")?.addEventListener("click", closeCareTreatmentManagerModal);
  $("#careTreatmentWorkflowForm")?.addEventListener("submit", submitCareTreatmentWorkflow);
  $("#closeCareTreatmentWorkflowBtn")?.addEventListener("click", () => closeCareModal("careTreatmentWorkflowModal"));
  $("#cancelCareTreatmentWorkflowBtn")?.addEventListener("click", () => closeCareModal("careTreatmentWorkflowModal"));
  $("#closeCareTreatmentModalBtn")?.addEventListener("click", () => closeCareModal("careTreatmentModal"));
  $("#cancelCareTreatmentBtn")?.addEventListener("click", () => closeCareModal("careTreatmentModal"));
  $("#closeCareInfusionModalBtn")?.addEventListener("click", closeCareInfusionModal);
  $("#cancelCareInfusionBtn")?.addEventListener("click", closeCareInfusionModal);
  $$('[data-care-modal-close]').forEach((button) => {
    button.addEventListener("click", () => closeCareModal(button.closest(".modal-backdrop, [role=dialog]")?.id));
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Tab" && $("#careQrScannerModal")?.classList.contains("open")) {
      trapCareModalFocus(event, "careQrScannerModal");
      return;
    }
    if (event.key === "Tab" && $("#careScheduleDetailModal")?.classList.contains("open")) {
      trapCareModalFocus(event, "careScheduleDetailModal");
      return;
    }
    if (event.key === "Tab" && $("#careScheduleWorkflowActionModal")?.classList.contains("open")) {
      trapCareModalFocus(event, "careScheduleWorkflowActionModal");
      return;
    }
    if (event.key === "Tab" && $("#careInfusionModal")?.classList.contains("open")) {
      trapCareModalFocus(event, "careInfusionModal");
      return;
    }
    if (event.key === "Tab" && $("#careTreatmentManagerModal")?.classList.contains("open")) {
      trapCareModalFocus(event, "careTreatmentManagerModal");
      return;
    }
  });
}

function wireCareTreatmentSurface(surface) {
  surface.addEventListener("click", (event) => {
    const tab = event.target.closest("[data-care-treatment-tab]");
    if (tab) {
      setCareTreatmentManagerTab(tab.dataset.careTreatmentTab);
      return;
    }
    const sort = event.target.closest("[data-care-manager-sort-index]");
    if (sort) {
      const sortColumn = Number(sort.dataset.careManagerSortIndex);
      if (careTreatmentManagerState.sortColumn === sortColumn) {
        careTreatmentManagerState.sortDirection = careTreatmentManagerState.sortDirection === "asc" ? "desc" : "asc";
      } else {
        careTreatmentManagerState.sortColumn = sortColumn;
        careTreatmentManagerState.sortDirection = "asc";
      }
      careTreatmentManagerState.selectedId = "";
      renderCareTreatmentManager();
      return;
    }
    const directView = event.target.closest("[data-care-manager-view-id]");
    if (directView) {
      const recordId = String(directView.dataset.careManagerViewId || "");
      const record = getCareTreatmentManagerRecords().find((item) => careTreatmentManagerRecordId(item) === recordId);
      if (record) {
        careTreatmentManagerState.selectedId = recordId;
        openCareTreatmentManagerDetailFromSurface(record, "drugs");
      }
      return;
    }
    if (event.target.closest("[data-care-manager-consent-link]")) return;
    if (event.target.closest("tr[data-care-manager-record-id]")) {
      handleCareTreatmentManagerRowClick(event);
      return;
    }
    if (event.target.closest("[data-care-manager-action]")) handleCareTreatmentManagerAction(event);
  });

  surface.addEventListener("input", (event) => {
    if (!event.target.matches('[data-care-treatment-role="search"]')) return;
    careTreatmentManagerState.query = event.target.value;
    careTreatmentManagerState.selectedId = "";
    renderCareTreatmentManager();
  });

  surface.addEventListener("keydown", (event) => {
    if (event.target.closest("[data-care-manager-view-id], [data-care-manager-consent-link]")) return;
    if (!["Enter", " "].includes(event.key) || !event.target.closest("tr[data-care-manager-record-id]")) return;
    event.preventDefault();
    handleCareTreatmentManagerRowClick(event);
  });
}

function setCareView(value, { refresh = true } = {}) {
  const normalized = ["infusion", "actions"].includes(value) ? "treatments" : value;
  const allowed = new Set(["treatments", "drugs"]);
  careView = allowed.has(normalized) ? normalized : "treatments";
  $$('[data-care-view-target]').forEach((button) => {
    const active = button.dataset.careViewTarget === careView;
    button.classList.toggle("active", active);
    button.setAttribute("aria-selected", String(active));
  });
  $$('[data-care-view]').forEach((panel) => {
    const active = panel.dataset.careView === careView;
    panel.classList.toggle("active", active);
    panel.hidden = !active;
  });
  if (!refresh) return;
  if (careView === "drugs" && !careDrugs.length) loadCareDrugs($("#careDrugSearch")?.value || "");
}

function careScheduleDateValue(date = new Date()) {
  const year = date.getFullYear();
  return `${year}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function careScheduleDateDisplay(value) {
  const match = String(value || "").match(/^(\d{4})-(\d{2})-(\d{2})$/);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : "";
}

function careScheduleDateFromDisplay(value) {
  const match = String(value || "").trim().match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
  if (!match) return "";
  const iso = `${match[3]}-${match[2]}-${match[1]}`;
  const date = new Date(`${iso}T12:00:00`);
  return Number.isNaN(date.getTime()) || careScheduleDateValue(date) !== iso ? "" : iso;
}

function careScheduleWeekday(value) {
  const date = new Date(`${value}T12:00:00`);
  const label = new Intl.DateTimeFormat("es-AR", { weekday: "long" }).format(date);
  return label.charAt(0).toUpperCase() + label.slice(1);
}

function setCareScheduleDate(value) {
  const input = $("#careScheduleDate");
  if (!input) return;
  const iso = /^\d{4}-\d{2}-\d{2}$/.test(String(value || "")) ? value : careScheduleDateValue();
  input.dataset.iso = iso;
  input.value = careScheduleDateDisplay(iso);
  if ($("#careScheduleCalendarDate")) $("#careScheduleCalendarDate").value = iso;
  if ($("#careScheduleWeekday")) $("#careScheduleWeekday").textContent = careScheduleWeekday(iso);
}

function selectedCareScheduleDate() {
  const input = $("#careScheduleDate");
  return careScheduleDateFromDisplay(input?.value) || input?.dataset.iso || careScheduleDateValue();
}

function handleCareScheduleDateChange() {
  const input = $("#careScheduleDate");
  if (!input) return;
  const value = careScheduleDateFromDisplay(input.value);
  if (!value) {
    setCareScheduleDate(input.dataset.iso || careScheduleDateValue());
    toast("Ingrese la fecha como 00/00/0000");
    return;
  }
  if (value === input.dataset.iso) return;
  setCareScheduleDate(value);
  void loadCareSchedule();
}

function shiftCareScheduleDate(days) {
  const date = new Date(`${selectedCareScheduleDate()}T12:00:00`);
  date.setDate(date.getDate() + days);
  setCareScheduleDate(careScheduleDateValue(date));
  void loadCareSchedule();
}

function careScheduleClockMinutes(value) {
  const [hours, minutes] = String(value || "00:00").split(":").map(Number);
  return hours * 60 + minutes;
}

function careScheduleClock(value) {
  return `${String(Math.floor(value / 60)).padStart(2, "0")}:${String(value % 60).padStart(2, "0")}`;
}

function careScheduleChair(value) {
  return Number(String(value || "").match(/\d+/)?.[0] || 0);
}

function careScheduleDurationLabel(value) {
  const minutes = Math.max(1, Number(value) || 60);
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return `${hours} h${remainder ? ` ${remainder} min` : ""}`;
}

function careScheduleItemDuration(item) {
  return Math.max(1, Number(item?.durationMinutes) || Number(item?.scheduleDurationMinutes) || 60);
}

function careScheduleInfusionScheme(infusion) {
  const drugNames = (infusion?.medications || []).map((item) => item?.drugName).filter(Boolean);
  return infusion?.sourceRef?.scheduler?.drugScheme || drugNames.join(" + ") || infusion?.drugScheme || infusion?.sourceRef?.scheduler?.scheme || infusion?.treatmentScheme || "Esquema no informado";
}

function careScheduleMedicationReceived(item) {
  return item?.medicationState === "received" ||
    item?.sourceRef?.scheduler?.medicationState === "received" ||
    item?.medicationReceived === true ||
    item?.sourceRef?.scheduler?.medicationReceived === true;
}

function careScheduleMedicationWithPatient(item) {
  return item?.medicationState === "patient" ||
    item?.sourceRef?.scheduler?.medicationState === "patient" ||
    item?.medicationWithPatient === true ||
    item?.patientHasMedication === true ||
    item?.medicationLocation === "patient" ||
    item?.sourceRef?.scheduler?.medicationWithPatient === true ||
    item?.sourceRef?.scheduler?.patientHasMedication === true ||
    item?.sourceRef?.scheduler?.medicationLocation === "patient";
}

function careSchedulePrescriptionConfirmed(item) {
  return item?.prescriptionWorkflowState === "confirmed" ||
    item?.prescriptionConfirmed === true ||
    item?.sourceRef?.scheduler?.prescriptionConfirmed === true;
}

function careScheduleMedicationAvailable(item) {
  return careScheduleMedicationReceived(item) || careScheduleMedicationWithPatient(item);
}

function careScheduleWorkflowStatus(item) {
  const value = String(item?.workflowStatus || item?.courseState || item?.continuityState || "active").toLowerCase();
  if (["temporary", "temporary_hold", "suspended_temporary", "paused"].includes(value)) return "temporary_hold";
  if (["definitive", "discontinued", "suspended_definitive", "cancelled"].includes(value)) return "discontinued";
  return "active";
}

function careScheduleWorkflowEffectiveCycle(item) {
  const currentCycle = Math.max(1, Number(item?.cycleNumber) || 1);
  const reactivationCycle = Number(item?.reactivationCycle);
  if (Number.isInteger(reactivationCycle) && reactivationCycle > 0) return reactivationCycle;
  const effectiveFromCycle = Number(item?.effectiveFromCycle);
  if (Number.isInteger(effectiveFromCycle) && effectiveFromCycle > 0) return effectiveFromCycle;
  return currentCycle;
}

function careScheduleWorkflowIsEffectiveCycle(item) {
  const currentCycle = Math.max(1, Number(item?.cycleNumber) || 1);
  return currentCycle === careScheduleWorkflowEffectiveCycle(item);
}

function careScheduleWorkflowBlockedByPriorCycle(item) {
  if (careScheduleWorkflowStatus(item) === "active") return false;
  const currentCycle = Math.max(1, Number(item?.cycleNumber) || 1);
  return currentCycle > careScheduleWorkflowEffectiveCycle(item);
}

function careSchedulePrescriptionWorkflowState(item) {
  const value = String(item?.prescriptionWorkflowState || item?.prescriptionState || "").toLowerCase();
  if (["confirmed", "available", "issued"].includes(value) || careSchedulePrescriptionConfirmed(item)) return "confirmed";
  if (["requested", "pending", "sent"].includes(value)) return "requested";
  if (["rejected", "declined"].includes(value)) return "rejected";
  return "required";
}

function careScheduleResumeFlowState(item) {
  if (careScheduleWorkflowStatus(item) !== "temporary_hold" || !careScheduleWorkflowIsEffectiveCycle(item)) return "";
  const prescriptionState = careSchedulePrescriptionWorkflowState(item);
  if (prescriptionState === "confirmed") return "ready";
  if (prescriptionState === "requested" || careScheduleOpenRequestId(item, "prescription")) return "waiting";
  return "needs-prescription";
}

function careScheduleOpenRequestId(item, kind) {
  const requests = item?.pendingRequestIds || item?.openRequests || {};
  if (kind === "prescription") return requests.prescription || requests.prescription_request || item?.pendingPrescriptionRequestId || "";
  return requests.continuity || requests.continuity_request || item?.pendingContinuityRequestId || "";
}

function careScheduleCandidateBlockedReason(item) {
  if (!clinicalHasPermission("section.day-hospital.edit")) return "Su rol permite consultar el turnero, pero no asignar turnos.";
  const workflowStatus = careScheduleWorkflowStatus(item);
  if (careScheduleWorkflowBlockedByPriorCycle(item)) {
    return `Bloqueado por suspensión desde ciclo ${careScheduleWorkflowEffectiveCycle(item)}.`;
  }
  if (workflowStatus === "temporary_hold") return "Tratamiento suspendido transitoriamente.";
  if (workflowStatus === "discontinued") return "Tratamiento suspendido definitivamente.";
  if (careScheduleOpenRequestId(item, "continuity")) return "Continuidad pendiente de decisión médica.";
  const prescriptionState = careSchedulePrescriptionWorkflowState(item);
  if (prescriptionState === "requested") return "Prescripción solicitada; espere la respuesta médica.";
  if (prescriptionState === "rejected") return "La solicitud de prescripción fue rechazada.";
  if (prescriptionState !== "confirmed") return "Falta una prescripción confirmada.";
  return "";
}

function careScheduleCandidateCanManage() {
  return clinicalHasPermission("section.day-hospital.edit") &&
    ["workflow.suspend", "workflow.resume", "workflow.request-prescription", "workflow.request-continuity"]
    .some((permission) => clinicalHasPermission(permission));
}

function careScheduleAppointmentConfirmed(item) {
  return item?.appointmentConfirmed === true || item?.sourceRef?.scheduler?.appointmentConfirmed === true;
}

function careSchedulePatientDni(item) {
  return String(item?.patientDni || item?.dni || "").trim();
}

function setCareHospitalTab(mode) {
  const allowed = new Set(["chairs", "pharmacy", "treatments", "new-treatment"]);
  careScheduleMode = allowed.has(mode) ? mode : "chairs";
  if (careScheduleMode === "new-treatment" && !clinicalHasPermission("section.prescriptions.edit")) {
    careScheduleMode = getActiveLiraPatientId() ? "treatments" : "chairs";
  }
  const modal = $("#careTreatmentManagerModal");
  if (modal) modal.dataset.careHospitalMode = careScheduleMode;
  $$("[data-care-hospital-tab]").forEach((button) => {
    const active = button.dataset.careHospitalTab === careScheduleMode;
    button.classList.toggle("active", active);
    button.setAttribute("aria-selected", String(active));
    button.tabIndex = active ? 0 : -1;
  });
  $$("[data-care-hospital-panel]").forEach((panel) => {
    const active = panel.dataset.careHospitalPanel === careScheduleMode;
    panel.classList.toggle("active", active);
    panel.hidden = !active;
  });
  if (careScheduleMode !== "treatments" && modal?.classList.contains("is-detail")) {
    careTreatmentDetailRequestVersion += 1;
    careTreatmentManagerState.mode = "list";
    careTreatmentManagerState.exactDetail = null;
    careTreatmentManagerState.observationApplicationId = "";
    modal.classList.remove("is-detail");
    if ($("#careTreatmentManagerDetail")) $("#careTreatmentManagerDetail").hidden = true;
  }
  renderCareHospitalPatientContext();
  if (careScheduleMode === "pharmacy") {
    renderCareSchedulePharmacy();
  } else if (careScheduleMode === "chairs") {
    renderCareSchedule();
  } else if (careScheduleMode === "treatments" && getActiveLiraPatientId()) {
    renderCareTreatmentManagerPatientSummary();
    renderCareTreatmentManager();
  }
  refreshIcons();
}

function activateCareHospitalTab(mode) {
  setCareHospitalTab(mode);
  if (careScheduleMode === "chairs" || careScheduleMode === "pharmacy") {
    void loadCareSchedule();
  } else if (careScheduleMode === "new-treatment" && getActiveLiraPatientId() && !careTreatmentOptions && !careBusy) {
    void refreshCareWorkspace();
  } else if (careScheduleMode === "treatments" && getActiveLiraPatientId() && !careTreatments.length && !careBusy) {
    void refreshCareWorkspace();
  }
}

function setCareScheduleMode(mode) {
  activateCareHospitalTab(mode);
}

async function refreshCareHospitalMode() {
  if (careScheduleMode === "chairs" || careScheduleMode === "pharmacy") {
    await loadCareSchedule();
    return;
  }
  if (getActiveLiraPatientId()) {
    await refreshCareWorkspace({ force: true });
  } else {
    renderCareHospitalPatientContext();
  }
}

function handleCareHospitalAction(event) {
  const action = event.target.closest("[data-care-hospital-action]")?.dataset.careHospitalAction;
  if (!action) return;
  if (action === "select-patient") {
    closeCareTreatmentManagerModal({ restoreFocus: false });
    openLiraImportModal();
    return;
  }
  if (action === "open-new-treatment") {
    if (!getActiveLiraPatientId()) {
      renderCareHospitalPatientContext();
      return;
    }
    if (!careTreatmentOptions) {
      void refreshCareWorkspace().then(() => {
        if (careTreatmentOptions && getActiveLiraPatientId()) openCareTreatmentModal();
      });
      return;
    }
    openCareTreatmentModal();
  }
}

function careScheduleNormalizeSearch(value) {
  return String(value || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim().toLocaleLowerCase("es-AR");
}

function careScheduleSearchQuery(source = "chairs") {
  const input = source === "pharmacy" ? $("#careSchedulePharmacySearch") : $("#careScheduleCandidateSearch");
  return careScheduleNormalizeSearch(input?.value);
}

function careScheduleSearchableText(item) {
  return careScheduleNormalizeSearch([
    item?.patientName,
    careSchedulePatientDni(item),
    item?.scheme,
    item?.drugScheme,
    item?.treatmentScheme,
    careScheduleInfusionScheme(item),
    item?.diagnosis,
    item?.insurance,
    item?.affiliateNumber,
    item?.suggestedDate,
    Number(item?.cycleNumber) > 0 ? `ciclo ${item.cycleNumber}` : "",
    careSchedulePrescriptionConfirmed(item) ? "prescripcion confirmada" : "falta prescripcion",
    careScheduleMedicationAvailable(item) ? "medicacion disponible" : "falta medicacion",
  ].filter(Boolean).join(" "));
}

function careScheduleMatchesSearch(item, query = careScheduleSearchQuery()) {
  return !query || careScheduleSearchableText(item).includes(query);
}

function careScheduleDateLabel(value) {
  const match = careScheduleCandidateDate(value).match(/^(\d{4})-(\d{2})-(\d{2})$/);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : "A definir";
}

function careScheduleCandidateDate(value) {
  if (value instanceof Date && !Number.isNaN(value.getTime())) {
    return [
      String(value.getFullYear()).padStart(4, "0"),
      String(value.getMonth() + 1).padStart(2, "0"),
      String(value.getDate()).padStart(2, "0"),
    ].join("-");
  }
  const match = String(value || "").trim().match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!match) return "";
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return "";
  return `${match[1]}-${match[2]}-${match[3]}`;
}

function careScheduleCandidateCompare(left, right) {
  const leftDate = careScheduleCandidateDate(left?.suggestedDate);
  const rightDate = careScheduleCandidateDate(right?.suggestedDate);
  if (leftDate !== rightDate) {
    if (!leftDate) return 1;
    if (!rightDate) return -1;
    const chronological = leftDate.localeCompare(rightDate);
    if (chronological) return chronological;
  }
  return String(left?.patientName || "").localeCompare(String(right?.patientName || ""), "es-AR", {
    numeric: true,
    sensitivity: "base",
  }) ||
    String(left?.treatmentId || "").localeCompare(String(right?.treatmentId || ""), "es-AR", {
      numeric: true,
      sensitivity: "base",
    }) ||
    Number(left?.cycleNumber || 0) - Number(right?.cycleNumber || 0) ||
    String(left?.id || "").localeCompare(String(right?.id || ""), "es-AR", {
      numeric: true,
      sensitivity: "base",
    });
}

function careScheduleBusinessDaysUntil(value, referenceValue = null) {
  const parseDate = (input) => {
    const match = String(input || "").slice(0, 10).match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (!match) return null;
    const timestamp = Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
    const date = new Date(timestamp);
    return date.toISOString().slice(0, 10) === match[0] ? timestamp : null;
  };
  const target = parseDate(value);
  const now = new Date();
  const reference = parseDate(referenceValue) ??
    Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  if (target == null || reference == null) return null;
  if (target === reference) return 0;
  const direction = target > reference ? 1 : -1;
  let cursor = reference;
  let businessDays = 0;
  while (cursor !== target) {
    cursor += direction * 24 * 60 * 60 * 1000;
    const weekday = new Date(cursor).getUTCDay();
    if (weekday !== 0 && weekday !== 6) businessDays += 1;
  }
  return businessDays * direction;
}

async function careScheduleJson(path, options = {}) {
  const response = await fetch(path, { cache: "no-store", headers: { Accept: "application/json", ...(options.body ? { "Content-Type": "application/json" } : {}) }, ...options });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(payload.error || "No se pudo actualizar el turnero.");
    error.code = payload.code || "";
    error.status = response.status;
    throw error;
  }
  return payload;
}

async function loadCareSchedule() {
  const dateInput = $("#careScheduleDate");
  if (!dateInput) return;
  if (!dateInput.dataset.iso) setCareScheduleDate(careScheduleDateValue());
  const scheduleDate = selectedCareScheduleDate();
  setCareScheduleDate(scheduleDate);
  const requestVersion = ++careScheduleRequestVersion;
  const candidateRequestVersion = ++careScheduleCandidateRequestVersion;
  $("#careScheduleStatus").textContent = "Cargando agenda...";
  try {
    const [settingsPayload, infusionsPayload, candidatesPayload] = await Promise.all([
      careScheduleJson(`/api/clinical/configuration/day-hospital-settings?t=${Date.now()}`),
      careScheduleJson(`/api/clinical/infusions?date=${encodeURIComponent(scheduleDate)}&t=${Date.now()}`),
      careScheduleJson(`/api/clinical/infusion-candidates?q=${encodeURIComponent(careScheduleMode === "pharmacy" ? $("#careSchedulePharmacySearch")?.value || "" : $("#careScheduleCandidateSearch")?.value || "")}&t=${Date.now()}`),
    ]);
    if (requestVersion !== careScheduleRequestVersion) return;
    careScheduleSettings = { ...careScheduleSettings, ...(settingsPayload.items?.[0]?.definition || {}) };
    careScheduleInfusions = infusionsPayload.infusions || [];
    if (candidateRequestVersion === careScheduleCandidateRequestVersion) {
      careScheduleCandidates = candidatesPayload.candidates || [];
    }
    renderCareSchedule();
  } catch (error) {
    if (requestVersion !== careScheduleRequestVersion) return;
    $("#careScheduleStatus").textContent = error.message;
    $("#careScheduleGrid").innerHTML = `<div class="care-empty-state"><strong>No se pudo abrir la agenda</strong><span>${escapeHtml(error.message)}</span></div>`;
  }
}

async function loadCareScheduleCandidates(query = "") {
  const requestVersion = ++careScheduleCandidateRequestVersion;
  try {
    const payload = await careScheduleJson(`/api/clinical/infusion-candidates?q=${encodeURIComponent(query)}&t=${Date.now()}`);
    if (requestVersion !== careScheduleCandidateRequestVersion) return;
    careScheduleCandidates = payload.candidates || [];
    if (careScheduleMode === "pharmacy") renderCareSchedulePharmacy();
    else if (careScheduleMode === "chairs") renderCareScheduleCandidates();
    renderCareScheduleSearchHighlights();
  } catch (error) {
    if (requestVersion === careScheduleCandidateRequestVersion) {
      if ($("#careScheduleCandidateCount")) $("#careScheduleCandidateCount").textContent = error.message;
      if ($("#careSchedulePharmacyCount")) $("#careSchedulePharmacyCount").textContent = error.message;
    }
  }
}

function renderCareScheduleCandidates() {
  const output = $("#careScheduleCandidates"); if (!output) return;
  const query = careScheduleSearchQuery();
  const filter = $("#careScheduleCandidateFilter")?.value || "prescribed";
  const rows = careScheduleCandidates.filter((item) =>
    (filter !== "prescription-confirmed" || careSchedulePrescriptionConfirmed(item)) &&
    (filter !== "missing-prescription" || !careSchedulePrescriptionConfirmed(item)) &&
    (filter !== "missing-medication" || !careScheduleMedicationAvailable(item)) &&
    (filter !== "medication-received" || careScheduleMedicationReceived(item)) &&
    (filter !== "medication-with-patient" || careScheduleMedicationWithPatient(item)) &&
    careScheduleMatchesSearch(item, query)).slice().sort((left, right) =>
    careScheduleCandidateCompare(left, right));
  if (careScheduleSelectedCandidateId && !rows.some((item) => String(item.id) === careScheduleSelectedCandidateId)) careScheduleSelectedCandidateId = "";
  const countLabel = filter === "medication-received"
    ? "con medicación recibida"
    : filter === "medication-with-patient"
      ? (rows.length === 1 ? "en poder del paciente" : "en poder de pacientes")
      : filter === "prescription-confirmed"
        ? (rows.length === 1 ? "ciclo con prescripción" : "ciclos con prescripción")
        : filter === "missing-prescription"
          ? "sin prescripción"
          : filter === "missing-medication"
            ? "sin medicación"
            : (rows.length === 1 ? "ciclo pendiente" : "ciclos pendientes");
  $("#careScheduleCandidateCount").textContent = `${rows.length} ${countLabel}`;
  const emptyTitle = filter === "medication-received"
    ? "Sin medicaciones recibidas"
    : filter === "medication-with-patient"
      ? "Sin medicación en poder del paciente"
      : filter === "prescription-confirmed"
        ? "Sin ciclos con prescripción"
        : filter === "missing-prescription"
          ? "Sin ciclos pendientes de prescripción"
          : filter === "missing-medication"
            ? "Sin ciclos pendientes de medicación"
            : "Sin ciclos pendientes";
  const emptyDescription = filter === "medication-received"
    ? "No hay ciclos con recepción de medicación confirmada que coincidan."
    : filter === "medication-with-patient"
      ? "No hay ciclos cuya medicación esté en poder del paciente y coincidan con la búsqueda."
      : filter === "prescription-confirmed"
        ? "No hay ciclos con prescripción disponible que coincidan con la búsqueda."
        : filter === "missing-prescription"
          ? "Todos los ciclos visibles tienen su prescripción disponible."
          : filter === "missing-medication"
            ? "Todos los ciclos visibles tienen la medicación recibida o en poder del paciente."
            : "No hay ciclos de tratamiento sin turno que coincidan.";
  const todayValue = careScheduleDateValue();
  const visibleRows = rows.slice(0, careScheduleCandidateVisibleLimit);
  const cardsMarkup = visibleRows.map((item) => {
    const selected = String(item.id) === careScheduleSelectedCandidateId;
    const dni = careSchedulePatientDni(item);
    const medicationReceived = careScheduleMedicationReceived(item);
    const medicationWithPatient = careScheduleMedicationWithPatient(item);
    const medicationAvailable = medicationReceived || medicationWithPatient;
    const prescriptionConfirmed = careSchedulePrescriptionConfirmed(item);
    const prescriptionWorkflowState = careSchedulePrescriptionWorkflowState(item);
    const workflowStatus = careScheduleWorkflowStatus(item);
    const resumeFlowState = careScheduleResumeFlowState(item);
    const workflowEffectiveCycle = careScheduleWorkflowEffectiveCycle(item);
    const workflowBlockedByPriorCycle = careScheduleWorkflowBlockedByPriorCycle(item);
    const workflowCycleCanManage = workflowStatus === "active" || careScheduleWorkflowIsEffectiveCycle(item);
    const prescriptionRequested = prescriptionWorkflowState === "requested" || Boolean(careScheduleOpenRequestId(item, "prescription"));
    const continuityRequested = Boolean(careScheduleOpenRequestId(item, "continuity"));
    const blockedReason = careScheduleCandidateBlockedReason(item);
    const blocked = Boolean(blockedReason);
    const medicationClass = medicationWithPatient ? " is-medication-with-patient" : medicationReceived ? " is-medication-received" : " is-medication-pending";
    const medicationLabel = medicationWithPatient ? "La medicacion esta en poder del paciente" : medicationReceived ? "Medicacion recibida" : "Recepcion de medicacion pendiente";
    const medicationBadge = medicationWithPatient ? `<em class="care-schedule-candidate-custody"><i data-lucide="briefcase-medical"></i>La tiene el paciente</em>` : "";
    const suggestedDate = careScheduleCandidateDate(item.suggestedDate);
    const businessDays = suggestedDate ? careScheduleBusinessDaysUntil(suggestedDate, todayValue) : null;
    const overdue = Boolean(suggestedDate && suggestedDate < todayValue);
    const timingClass = !suggestedDate
      ? " is-date-unknown"
      : overdue ? " is-overdue is-due-soon"
        : businessDays < 5 ? " is-due-soon" : " is-due-later";
    const countdownValue = businessDays == null ? "—" : Math.abs(businessDays);
    const countdownLabel = overdue ? "días háb. venc." : "días hábiles";
    const prescriptionAlert = resumeFlowState || workflowBlockedByPriorCycle
      ? ""
      : prescriptionConfirmed
      ? ""
      : prescriptionRequested
        ? `<span class="care-schedule-candidate-alert is-request-alert"><i data-lucide="send"></i>Prescripción solicitada</span>`
        : prescriptionWorkflowState === "rejected"
          ? `<span class="care-schedule-candidate-alert is-suspension-alert"><i data-lucide="file-x-2"></i>Prescripción rechazada</span>`
          : `<span class="care-schedule-candidate-alert is-prescription-alert"><i data-lucide="file-x-2"></i>Falta prescripción</span>`;
    const medicationAlert = medicationAvailable
      ? ""
      : `<span class="care-schedule-candidate-alert is-medication-alert"><i data-lucide="package-x"></i>Falta medicación</span>`;
    const workflowAlert = resumeFlowState === "ready"
      ? `<span class="care-schedule-candidate-alert is-resume-ready-alert"><i data-lucide="circle-check"></i>Listo para reanudar</span>`
      : resumeFlowState === "waiting"
        ? `<span class="care-schedule-candidate-alert is-request-alert"><i data-lucide="clock-3"></i>Prescripción solicitada para reanudar</span>`
        : resumeFlowState === "needs-prescription"
          ? `<span class="care-schedule-candidate-alert is-prescription-alert"><i data-lucide="file-pen-line"></i>Prescripción requerida para reanudar</span>`
      : workflowBlockedByPriorCycle
        ? `<span class="care-schedule-candidate-alert is-suspension-alert"><i data-lucide="lock-keyhole"></i>Bloqueado por suspensión desde ciclo ${workflowEffectiveCycle}</span>`
      : workflowStatus === "discontinued"
        ? `<span class="care-schedule-candidate-alert is-suspension-alert"><i data-lucide="ban"></i>Suspensión definitiva</span>`
        : continuityRequested
          ? `<span class="care-schedule-candidate-alert is-request-alert"><i data-lucide="stethoscope"></i>Continuidad solicitada</span>`
          : "";
    const alertMarkup = workflowAlert || prescriptionAlert || medicationAlert
      ? `<div class="care-schedule-candidate-alerts">${workflowAlert}${prescriptionAlert}${medicationAlert}</div>`
      : "";
    const prescriptionLabel = prescriptionConfirmed ? "Prescripción disponible" : "Falta prescripción";
    const dueLabel = suggestedDate ? `Fecha prevista ${careScheduleDateLabel(suggestedDate)}` : "Fecha prevista a definir";
    const countdownAria = businessDays == null
      ? "Fecha prevista a definir"
      : overdue ? `${Math.abs(businessDays)} días hábiles de atraso` : `Faltan ${businessDays} días hábiles`;
    const managementLabel = resumeFlowState === "waiting"
      ? "Esperando"
      : resumeFlowState === "needs-prescription"
        ? "Solicitar prescripción"
        : resumeFlowState === "ready" ? "Reanudar" : "Gestionar";
    const managementIcon = resumeFlowState === "waiting"
      ? "clock-3"
      : resumeFlowState === "needs-prescription"
        ? "file-pen-line"
        : resumeFlowState === "ready" ? "play-circle" : "clipboard-clock";
    const managementButton = careScheduleCandidateCanManage() && workflowCycleCanManage
      ? `<button class="care-schedule-candidate-manage" type="button" draggable="false" data-care-schedule-workflow="${escapeAttr(item.id)}" title="${escapeAttr(managementLabel)}" aria-label="${escapeAttr(managementLabel)} ciclo ${escapeAttr(item.cycleNumber || "")} de ${escapeAttr(item.patientName || "paciente")}"><i data-lucide="${managementIcon}"></i><span>${managementLabel}</span></button>`
      : "";
    const title = blockedReason || `${prescriptionLabel}. ${medicationLabel}. ${dueLabel}. Seleccionar para ver en celeste dónde entra`;
    return `<article class="care-schedule-candidate${medicationClass}${timingClass}${prescriptionConfirmed ? "" : " is-missing-prescription"}${medicationAvailable ? "" : " is-missing-medication"}${workflowStatus !== "active" ? ` is-${workflowStatus.replaceAll("_", "-")}` : ""}${continuityRequested || prescriptionRequested ? " is-workflow-requested" : ""}${blocked ? " is-workflow-blocked" : ""}${selected ? " is-selected" : ""}" draggable="${!blocked}" role="button" tabindex="0" aria-pressed="${selected}" data-placement-disabled="${blocked}" data-care-schedule-candidate="${escapeHtml(item.id)}" title="${escapeAttr(title)}"><div class="care-schedule-candidate-main"><div class="care-schedule-candidate-copy"><strong>${escapeHtml(item.patientName)}</strong><small>${dni ? `DNI ${escapeHtml(dni)}` : "DNI no informado"}</small>${medicationBadge}<span>${escapeHtml(item.drugScheme || item.scheme)}</span></div><div class="care-schedule-candidate-countdown${overdue ? " is-overdue" : ""}" aria-label="${escapeAttr(countdownAria)}"><b>${escapeHtml(countdownValue)}</b><small>${countdownLabel}</small><time${suggestedDate ? ` datetime="${escapeAttr(suggestedDate)}"` : ""}>${escapeHtml(careScheduleDateLabel(suggestedDate))}</time></div></div>${alertMarkup}<footer><span>Ciclo ${item.cycleNumber}/${item.totalCycles}</span><b>${escapeHtml(careScheduleDurationLabel(item.durationMinutes))}</b>${managementButton}</footer></article>`;
  }).join("");
  const remaining = Math.max(0, rows.length - visibleRows.length);
  const loadMoreMarkup = remaining
    ? `<button class="care-schedule-load-more" type="button" data-care-schedule-load-more><strong>Mostrar ${Math.min(200, remaining)} ciclos más</strong><small>${visibleRows.length} de ${rows.length} visibles · puede buscar un paciente para ver todos sus ciclos</small></button>`
    : "";
  output.innerHTML = rows.length
    ? `${cardsMarkup}${loadMoreMarkup}`
    : `<div class="care-empty-state"><i data-lucide="calendar-check"></i><strong>${emptyTitle}</strong><span>${emptyDescription}</span></div>`;
  applyClinicalPermissions();
  refreshIcons();
}

function renderCareSchedulePharmacy() {
  const output = $("#careSchedulePharmacyRows");
  if (!output) return;
  const query = careScheduleSearchQuery("pharmacy");
  const rows = careScheduleCandidates.filter((item) => careScheduleMatchesSearch(item, query));
  if ($("#careSchedulePharmacyCount")) {
    $("#careSchedulePharmacyCount").textContent = `${rows.length} ${rows.length === 1 ? "ciclo pendiente" : "ciclos pendientes"}`;
  }
  const visibleRows = rows.slice(0, careSchedulePharmacyVisibleLimit);
  const rowsMarkup = visibleRows.map((item) => {
    const patientId = String(item.patientId || "");
    const treatmentId = String(item.treatmentId || "");
    const cycleNumber = Math.max(1, Number(item.cycleNumber) || 1);
    const state = item.medicationState === "received"
      ? "received"
      : item.medicationState === "patient" || careScheduleMedicationWithPatient(item)
        ? "patient"
        : careScheduleMedicationReceived(item) ? "received" : "pending";
    const dni = careSchedulePatientDni(item);
    const insurance = String(item.insurance || "").trim();
    const affiliate = String(item.affiliateNumber || "").trim();
    const qr = `<a class="care-pharmacy-qr" href="/api/clinical/patients/${encodeURIComponent(patientId)}/treatments/${encodeURIComponent(treatmentId)}/documents/qr?cycle=${encodeURIComponent(cycleNumber)}" target="_blank" rel="noopener" title="Imprimir la etiqueta de este ciclo. Se podrá escanear después de asignar el turno."><i data-lucide="qr-code"></i><span>Imprimir</span></a>`;
    return `<tr data-care-pharmacy-candidate="${escapeAttr(item.id)}">
      <td><strong>${escapeHtml(item.patientName || "Paciente sin nombre")}</strong><small>${dni ? `DNI ${escapeHtml(dni)}` : "DNI no informado"}${insurance ? ` · ${escapeHtml(insurance)}` : ""}${affiliate ? ` · N.º ${escapeHtml(affiliate)}` : ""}</small></td>
      <td><time datetime="${escapeAttr(item.suggestedDate || "")}">${escapeHtml(careScheduleDateLabel(item.suggestedDate))}</time><small>${Number(item.cycleDays) > 0 ? `Cada ${escapeHtml(item.cycleDays)} días` : "Intervalo no informado"}</small></td>
      <td><strong>${cycleNumber} de ${Math.max(cycleNumber, Number(item.totalCycles) || cycleNumber)}</strong><small>Próximo ciclo</small></td>
      <td><strong>${escapeHtml(item.drugScheme || item.scheme || "Esquema no informado")}</strong><small>${escapeHtml(item.diagnosis || "Diagnóstico no informado")}</small></td>
      <td><strong>${escapeHtml(careScheduleDurationLabel(item.durationMinutes))}</strong><small>Tiempo de sillón</small></td>
      <td><div class="care-pharmacy-state" role="group" aria-label="Ubicación de la medicación de ${escapeAttr(item.patientName || "paciente")}">
        <button type="button" class="${state === "pending" ? "active" : ""}" data-care-pharmacy-state="pending" data-patient-id="${escapeAttr(patientId)}" data-treatment-id="${escapeAttr(treatmentId)}" data-cycle-number="${cycleNumber}" title="Recepción pendiente"><i data-lucide="package"></i><span>Pendiente</span></button>
        <button type="button" class="${state === "received" ? "active" : ""}" data-care-pharmacy-state="received" data-patient-id="${escapeAttr(patientId)}" data-treatment-id="${escapeAttr(treatmentId)}" data-cycle-number="${cycleNumber}" title="Medicación recibida en el centro"><i data-lucide="package-check"></i><span>Recibida</span></button>
        <button type="button" class="${state === "patient" ? "active" : ""}" data-care-pharmacy-state="patient" data-patient-id="${escapeAttr(patientId)}" data-treatment-id="${escapeAttr(treatmentId)}" data-cycle-number="${cycleNumber}" title="La medicación está en poder del paciente"><i data-lucide="briefcase-medical"></i><span>Paciente</span></button>
      </div></td>
      <td>${qr}</td>
    </tr>`;
  }).join("");
  const remaining = Math.max(0, rows.length - visibleRows.length);
  const loadMoreMarkup = remaining
    ? `<tr class="care-pharmacy-load-more-row"><td colspan="7"><button class="care-schedule-load-more" type="button" data-care-pharmacy-load-more><strong>Mostrar ${Math.min(250, remaining)} ciclos más</strong><small>${visibleRows.length} de ${rows.length} visibles · use el buscador para encontrar un paciente</small></button></td></tr>`
    : "";
  output.innerHTML = rows.length
    ? `${rowsMarkup}${loadMoreMarkup}`
    : `<tr><td colspan="7"><div class="care-empty-state"><i data-lucide="package-search"></i><strong>Sin ciclos pendientes</strong><span>No hay pacientes que coincidan con la búsqueda.</span></div></td></tr>`;
  applyClinicalPermissions();
  refreshIcons();
}

async function updateCareSchedulePharmacyState(event) {
  if (event.target.closest("[data-care-pharmacy-load-more]")) {
    careSchedulePharmacyVisibleLimit += 250;
    renderCareSchedulePharmacy();
    return;
  }
  const button = event.target.closest("[data-care-pharmacy-state]");
  if (!button || careScheduleBusy) return;
  if (!clinicalHasPermission("section.day-hospital.edit")) {
    toast("Su rol permite consultar Farmacia, pero no modificarla.");
    return;
  }
  const item = careScheduleCandidates.find((candidate) =>
    String(candidate.patientId) === button.dataset.patientId &&
    String(candidate.treatmentId) === button.dataset.treatmentId &&
    Number(candidate.cycleNumber) === Number(button.dataset.cycleNumber));
  if (!item) return;
  const medicationState = button.dataset.carePharmacyState;
  const previous = {
    medicationState: item.medicationState,
    medicationReceived: item.medicationReceived,
    medicationWithPatient: item.medicationWithPatient,
    logisticsRevision: item.logisticsRevision,
  };
  item.medicationState = medicationState;
  item.medicationReceived = medicationState === "received";
  item.medicationWithPatient = medicationState === "patient";
  careScheduleBusy = true;
  renderCareSchedulePharmacy();
  try {
    const payload = await careScheduleJson(`/api/clinical/treatment-cycles/${encodeURIComponent(item.patientId)}/${encodeURIComponent(item.treatmentId)}/${encodeURIComponent(item.cycleNumber)}/logistics`, {
      method: "PATCH",
      body: JSON.stringify({
        expectedVersion: Number(item.logisticsRevision) || 0,
        plannedDate: item.suggestedDate || null,
        medicationState,
        reason: "Actualización desde la vista de Farmacia del turnero",
      }),
    });
    const logistics = payload.logistics || {};
    Object.assign(item, {
      suggestedDate: logistics.plannedDate || item.suggestedDate,
      medicationState: logistics.medicationState || medicationState,
      medicationReceived: logistics.medicationReceived === true,
      medicationWithPatient: logistics.medicationWithPatient === true,
      logisticsRevision: Number(logistics.revision) || 1,
    });
    if (careScheduleMode === "chairs") renderCareScheduleCandidates();
    renderCareSchedulePharmacy();
    toast(medicationState === "received"
      ? "Medicación recibida en el centro"
      : medicationState === "patient" ? "Medicación en poder del paciente" : "Recepción de medicación pendiente");
  } catch (error) {
    Object.assign(item, previous);
    renderCareSchedulePharmacy();
    if (error.code === "VERSION_CONFLICT") await loadCareScheduleCandidates(careScheduleSearchQuery("pharmacy"));
    toast(error.message || "No se pudo actualizar Farmacia");
  } finally {
    careScheduleBusy = false;
  }
}

function renderCareScheduleSearchHighlights() {
  const query = careScheduleSearchQuery();
  for (const infusion of careScheduleInfusions) {
    const matches = Boolean(query) && careScheduleMatchesSearch(infusion, query);
    const muted = Boolean(query) && !matches;
    const nodes = [
      $(`[data-care-schedule-infusion="${CSS.escape(String(infusion.id))}"]`),
      ...$$(`[data-care-schedule-fragment-for="${CSS.escape(String(infusion.id))}"]`),
    ].filter(Boolean);
    for (const node of nodes) {
      node.classList.toggle("is-search-match", matches);
      node.classList.toggle("is-search-muted", muted);
    }
  }
}

function syncCareScheduleCandidateSelection() {
  $$("[data-care-schedule-candidate]").forEach((node) => {
    const selected = node.dataset.careScheduleCandidate === careScheduleSelectedCandidateId;
    node.classList.toggle("is-selected", selected);
    node.setAttribute("aria-pressed", String(selected));
  });
}

function selectCareScheduleCandidate(event) {
  if (event.type === "keydown" && event.key !== "Enter" && event.key !== " ") return;
  const workflowButton = event.target.closest?.("[data-care-schedule-workflow]");
  if (workflowButton) {
    event.preventDefault();
    event.stopPropagation();
    void openCareScheduleWorkflowActionModal(workflowButton.dataset.careScheduleWorkflow);
    return;
  }
  if (event.target.closest("[data-care-schedule-load-more]")) {
    careScheduleCandidateVisibleLimit += 200;
    renderCareScheduleCandidates();
    return;
  }
  const candidateNode = event.target.closest("[data-care-schedule-candidate]");
  if (!candidateNode) return;
  if (event.type === "keydown") event.preventDefault();
  const candidate = careScheduleCandidates.find((item) => String(item.id) === candidateNode.dataset.careScheduleCandidate);
  const blockedReason = careScheduleCandidateBlockedReason(candidate);
  if (blockedReason) {
    toast(blockedReason);
    return;
  }
  const candidateId = candidateNode.dataset.careScheduleCandidate;
  careScheduleSelectedCandidateId = careScheduleSelectedCandidateId === candidateId ? "" : candidateId;
  syncCareScheduleCandidateSelection();
  renderCareScheduleAvailability();
}

function careScheduleWorkflowCandidate() {
  return careScheduleCandidates.find((item) => String(item.id) === String(careScheduleWorkflowCandidateId)) || null;
}

function careScheduleWorkflowActionLabel(action, item = careScheduleWorkflowCandidate()) {
  const reactivationFlow = careScheduleResumeFlowState(item);
  return {
    suspend: "Registrar suspensión",
    "request-prescription": reactivationFlow ? "Solicitar prescripción" : "Enviar solicitud",
    "request-continuity": "Enviar consulta",
    resume: "Reanudar mismo tratamiento"
  }[action] || "Continuar";
}

function renderCareScheduleResumeFlow(item, resumeFlowState) {
  const output = $("#careScheduleResumeFlow");
  if (!output) return;
  if (!resumeFlowState) {
    output.hidden = true;
    output.innerHTML = "";
    delete output.dataset.state;
    return;
  }
  const cycleNumber = Math.max(1, Number(item?.cycleNumber) || 1);
  const totalCycles = Math.max(cycleNumber, Number(item?.totalCycles) || cycleNumber);
  const copy = resumeFlowState === "ready"
    ? {
        icon: "circle-check",
        eyebrow: "Paso 2 de 2",
        title: "Todo listo para reanudar",
        description: "La prescripción fue confirmada. Registre el motivo y continúe el ciclo actual.",
        prescriptionState: "is-complete",
        prescriptionDetail: "Prescripción confirmada",
        resumeState: "is-current",
        resumeDetail: "Reanudar ahora"
      }
    : resumeFlowState === "waiting"
      ? {
          icon: "clock-3",
          eyebrow: "Solicitud enviada",
        title: "En espera de la prescripción",
          description: "El tratamiento permanece suspendido hasta que el médico confirme la prescripción.",
          prescriptionState: "is-current",
          prescriptionDetail: "Esperando respuesta médica",
          resumeState: "is-pending",
          resumeDetail: "Se habilitará al confirmar"
        }
      : {
          icon: "file-pen-line",
          eyebrow: "Paso 1 de 2",
          title: "Solicite la prescripción para reanudar",
          description: "Seleccione el médico que revisará la indicación antes de reanudar.",
          prescriptionState: "is-current",
          prescriptionDetail: "Solicitar al médico",
          resumeState: "is-pending",
          resumeDetail: "Disponible después de confirmar"
        };
  output.dataset.state = resumeFlowState;
  output.hidden = false;
  output.innerHTML = `
    <header>
      <span class="care-schedule-resume-flow-icon"><i data-lucide="${copy.icon}"></i></span>
      <div><small>${copy.eyebrow}</small><strong>${copy.title}</strong><p>${copy.description}</p></div>
    </header>
    <ol class="care-schedule-resume-steps">
      <li class="${copy.prescriptionState}"><i data-lucide="${resumeFlowState === "ready" ? "check" : resumeFlowState === "waiting" ? "clock-3" : "file-pen-line"}"></i><span><strong>1. Prescripción del ciclo</strong><small>${copy.prescriptionDetail}</small></span></li>
      <li class="${copy.resumeState}"><i data-lucide="${resumeFlowState === "ready" ? "play" : "circle-dashed"}"></i><span><strong>2. Reanudar</strong><small>${copy.resumeDetail}</small></span></li>
    </ol>
    <p class="care-schedule-resume-guarantee"><i data-lucide="shield-check"></i><span>Se actúa sobre el ciclo suspendido ${cycleNumber} de ${totalCycles} del mismo tratamiento (ID ${escapeHtml(item?.treatmentId || "sin informar")}). Los ciclos previos y las sesiones realizadas no cambian. La agenda se conserva y puede reprogramarse luego. No se crea otro tratamiento.</span></p>`;
}

function setCareScheduleWorkflowActionCopy(resumeFlowState) {
  const prescription = $('[data-workflow-action="request-prescription"]', $("#careScheduleWorkflowActions"));
  const resume = $('[data-workflow-action="resume"]', $("#careScheduleWorkflowActions"));
  const prescriptionStrong = prescription ? $("strong", prescription) : null;
  const prescriptionSmall = prescription ? $("small", prescription) : null;
  if (prescriptionStrong) prescriptionStrong.textContent = "Solicitar prescripción";
  if (prescriptionSmall) prescriptionSmall.textContent = resumeFlowState ? "Para reanudar este ciclo" : "Enviar a un médico";
  const resumeStrong = resume ? $("strong", resume) : null;
  const resumeSmall = resume ? $("small", resume) : null;
  if (resumeStrong) resumeStrong.textContent = "Reanudar";
  if (resumeSmall) resumeSmall.textContent = "Mismo tratamiento y ciclo";
}

async function openCareScheduleWorkflowActionModal(candidateId) {
  const item = careScheduleCandidates.find((candidate) => String(candidate.id) === String(candidateId));
  if (!item || !careScheduleCandidateCanManage()) return;
  careScheduleWorkflowCandidateId = String(item.id);
  careScheduleWorkflowAction = "";
  $("#careScheduleWorkflowActionForm")?.reset();
  const title = $("#careScheduleWorkflowTitle");
  const description = $("#careScheduleWorkflowDescription");
  if (title) title.textContent = item.patientName || "Gestión del ciclo";
  if (description) description.textContent = `Ciclo ${Number(item.cycleNumber) || 1} · ${item.drugScheme || item.scheme || "Esquema no informado"}`;
  const summary = $("#careScheduleWorkflowSummary");
  const workflowStatus = careScheduleWorkflowStatus(item);
  const prescriptionState = careSchedulePrescriptionWorkflowState(item);
  const resumeFlowState = careScheduleResumeFlowState(item);
  const suspension = item.suspension || {};
  if (description) description.textContent = resumeFlowState
    ? `Reanudar tratamiento · Ciclo ${Number(item.cycleNumber) || 1} · ${item.drugScheme || item.scheme || "Esquema no informado"}`
    : `Ciclo ${Number(item.cycleNumber) || 1} · ${item.drugScheme || item.scheme || "Esquema no informado"}`;
  const workflowContext = workflowStatus === "temporary_hold"
    ? `Suspendido transitoriamente${suspension.resumeDate ? ` hasta ${careScheduleDateLabel(suspension.resumeDate)}` : ""}${suspension.reason ? ` · ${suspension.reason}` : ""}`
    : workflowStatus === "discontinued"
      ? `Suspendido definitivamente${suspension.reason ? ` · ${suspension.reason}` : ""}`
      : careScheduleOpenRequestId(item, "continuity")
        ? "Existe una solicitud de continuidad pendiente."
        : careScheduleOpenRequestId(item, "prescription")
          ? "Existe una solicitud de prescripción pendiente."
          : "";
  if (summary) {
    summary.innerHTML = `
      <div><span>Paciente</span><strong>${escapeHtml(item.patientName || "Paciente sin nombre")}</strong><small>${careSchedulePatientDni(item) ? `DNI ${escapeHtml(careSchedulePatientDni(item))}` : `ID ${escapeHtml(item.patientId || "—")}`}</small></div>
      <div><span>Tratamiento</span><strong>${escapeHtml(item.drugScheme || item.scheme || "Esquema no informado")}</strong><small>Ciclo ${escapeHtml(item.cycleNumber || "1")} de ${escapeHtml(item.totalCycles || item.cycleNumber || "1")}</small></div>
      <div><span>Fecha prevista</span><strong>${escapeHtml(careScheduleDateLabel(item.suggestedDate) || "A definir")}</strong><small>${escapeHtml(careScheduleDurationLabel(careScheduleItemDuration(item)))}</small></div>
      ${workflowContext ? `<div class="is-wide care-schedule-workflow-current-state"><span>Estado actual</span><strong>${escapeHtml(workflowContext)}</strong></div>` : ""}`;
  }
  renderCareScheduleResumeFlow(item, resumeFlowState);
  setCareScheduleWorkflowActionCopy(resumeFlowState);

  const actionAvailability = {
    suspend: workflowStatus === "active" && clinicalHasPermission("workflow.suspend"),
    "request-prescription": (
      resumeFlowState
        ? resumeFlowState === "needs-prescription"
        : workflowStatus === "active" && !["requested", "confirmed"].includes(prescriptionState) &&
          !careScheduleOpenRequestId(item, "prescription")
    ) && clinicalHasPermission("workflow.request-prescription"),
    "request-continuity": workflowStatus === "active" && !careScheduleOpenRequestId(item, "continuity") &&
      clinicalHasPermission("workflow.request-continuity"),
    resume: resumeFlowState === "ready" &&
      clinicalHasPermission("workflow.resume")
  };
  $$("[data-workflow-action]", $("#careScheduleWorkflowActions")).forEach((button) => {
    const visible = Boolean(actionAvailability[button.dataset.workflowAction]);
    button.hidden = !visible;
    button.classList.remove("active");
    button.setAttribute("aria-pressed", "false");
  });
  $$("[data-workflow-panel]", $("#careScheduleWorkflowActionModal")).forEach((panel) => { panel.hidden = true; });
  const availableActions = Object.keys(actionAvailability).filter((action) => actionAvailability[action]);
  const firstAction = availableActions[0] || "";
  const actions = $("#careScheduleWorkflowActions");
  if (actions) {
    actions.hidden = availableActions.length === 0;
    actions.classList.toggle("is-single-action", availableActions.length === 1);
    actions.classList.toggle("is-resume-flow", Boolean(resumeFlowState));
  }
  const submit = $("#submitCareScheduleWorkflowActionBtn");
  const cancel = $("#cancelCareScheduleWorkflowActionBtn");
  if (submit) {
    submit.hidden = !firstAction;
    submit.disabled = !firstAction;
  }
  if (cancel && $("span", cancel)) $("span", cancel).textContent = firstAction ? "Cancelar" : "Cerrar";
  setClinicalFormError("#careScheduleWorkflowError");
  showCareModal("careScheduleWorkflowActionModal");
  if (firstAction) await setCareScheduleWorkflowAction(firstAction);
  else if (!resumeFlowState || resumeFlowState !== "waiting") {
    const message = workflowStatus === "discontinued"
      ? "La suspensión definitiva no puede reanudarse desde el turnero."
      : "Su rol no tiene una acción disponible para este ciclo.";
    setClinicalFormError("#careScheduleWorkflowError", message);
  }
}

function closeCareScheduleWorkflowActionModal() {
  if (careScheduleWorkflowBusy) return;
  const candidateId = careScheduleWorkflowCandidateId;
  closeCareModal("careScheduleWorkflowActionModal");
  careScheduleWorkflowCandidateId = "";
  careScheduleWorkflowAction = "";
  if (candidateId) window.requestAnimationFrame(() =>
    $(`[data-care-schedule-workflow="${CSS.escape(candidateId)}"]`)?.focus());
}

async function handleCareScheduleWorkflowActionChoice(event) {
  const button = event.target.closest("[data-workflow-action]");
  if (!button || button.hidden || button.disabled) return;
  await setCareScheduleWorkflowAction(button.dataset.workflowAction);
}

async function setCareScheduleWorkflowAction(action) {
  careScheduleWorkflowAction = action;
  const item = careScheduleWorkflowCandidate();
  const resumeFlowState = careScheduleResumeFlowState(item);
  $$("[data-workflow-action]", $("#careScheduleWorkflowActions")).forEach((button) => {
    const active = button.dataset.workflowAction === action;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", String(active));
  });
  $$("[data-workflow-panel]", $("#careScheduleWorkflowActionModal")).forEach((panel) => {
    const active = panel.dataset.workflowPanel === action;
    panel.hidden = !active;
    $$("input, select, textarea", panel).forEach((field) => { field.disabled = !active; });
  });
  const submit = $("#submitCareScheduleWorkflowActionBtn");
  if (submit) {
    submit.hidden = false;
    submit.disabled = false;
    $("span", submit).textContent = careScheduleWorkflowActionLabel(action, item);
  }
  const prescriptionNote = $("#careSchedulePrescriptionFlowNote span");
  if (prescriptionNote) {
    prescriptionNote.textContent = resumeFlowState
      ? "La confirmación habilitará Reanudar sobre este mismo tratamiento y ciclo. Los ciclos previos y las sesiones realizadas no cambian."
      : "La solicitud aparecerá al destinatario cuando ingrese al sistema.";
  }
  const resumeNote = $("#careScheduleResumeFlowNote span");
  if (resumeNote) {
    resumeNote.textContent = "La prescripción está confirmada. Se reanudará este mismo tratamiento en el ciclo actual, sin duplicarlo.";
  }
  setClinicalFormError("#careScheduleWorkflowError");
  syncCareScheduleSuspensionFields();
  if (action === "request-prescription") {
    await loadCareScheduleWorkflowUsers("workflow.resolve-prescription", "#careSchedulePrescriptionDoctor");
  } else if (action === "request-continuity") {
    await loadCareScheduleWorkflowUsers("workflow.resolve-continuity", "#careScheduleContinuityDoctor");
  }
  refreshIcons();
}

function syncCareScheduleSuspensionFields() {
  const temporary = $("#careScheduleSuspendKind")?.value !== "definitive";
  const field = $("#careScheduleSuspendResumeDateField");
  if (field) field.hidden = !temporary;
  if (!temporary && $("#careScheduleSuspendResumeDate")) $("#careScheduleSuspendResumeDate").value = "";
}

function careScheduleWorkflowUserLabel(user) {
  const roles = Array.isArray(user?.roles)
    ? user.roles.map((role) => role?.name || role?.key || role).filter(Boolean).join(", ")
    : "";
  return [user?.displayName || user?.name || user?.username || user?.email || `Usuario ${user?.id}`, user?.specialty, roles]
    .filter(Boolean).join(" · ");
}

async function loadCareScheduleWorkflowUsers(capability, selector) {
  const select = $(selector);
  if (!select) return;
  select.disabled = true;
  select.innerHTML = `<option value="">Cargando médicos...</option>`;
  try {
    let users = careScheduleWorkflowUsers.get(capability);
    if (!users) {
      const payload = await careScheduleJson(`/api/clinical/users?capability=${encodeURIComponent(capability)}&t=${Date.now()}`);
      users = Array.isArray(payload.items) ? payload.items : Array.isArray(payload.users) ? payload.users : [];
      careScheduleWorkflowUsers.set(capability, users);
    }
    select.innerHTML = `<option value="">Seleccione un médico...</option>${users.map((user) =>
      `<option value="${escapeAttr(user.id)}">${escapeHtml(careScheduleWorkflowUserLabel(user))}</option>`).join("")}`;
    select.disabled = users.length === 0;
    if (!users.length) {
      select.innerHTML = `<option value="">No hay médicos habilitados para responder</option>`;
      setClinicalFormError("#careScheduleWorkflowError", "Configure al menos un médico con permiso para responder esta solicitud.");
      if ($("#submitCareScheduleWorkflowActionBtn")) $("#submitCareScheduleWorkflowActionBtn").disabled = true;
    }
  } catch (error) {
    select.innerHTML = `<option value="">No se pudieron cargar los médicos</option>`;
    setClinicalFormError("#careScheduleWorkflowError", error.message || "No se pudieron cargar los médicos.");
    if ($("#submitCareScheduleWorkflowActionBtn")) $("#submitCareScheduleWorkflowActionBtn").disabled = true;
  }
}

async function submitCareScheduleWorkflowAction(event) {
  event.preventDefault();
  if (careScheduleWorkflowBusy) return;
  const item = careScheduleWorkflowCandidate();
  const action = careScheduleWorkflowAction;
  if (!item || !action) return;
  const resumeFlowState = careScheduleResumeFlowState(item);
  const patientId = String(item.patientId || "");
  const treatmentId = String(item.treatmentId || "");
  const cycleNumber = Math.max(1, Number(item.cycleNumber) || 1);
  let path = "";
  let body = {};

  if (action === "suspend") {
    const kind = $("#careScheduleSuspendKind")?.value === "definitive" ? "definitive" : "temporary";
    const reason = String($("#careScheduleSuspendReason")?.value || "").trim();
    const resumeDate = $("#careScheduleSuspendResumeDate")?.value || "";
    if (!reason) {
      setClinicalFormError("#careScheduleWorkflowError", "Indique el motivo de la suspensión.");
      $("#careScheduleSuspendReason")?.focus();
      return;
    }
    path = `/api/clinical/treatments/${encodeURIComponent(patientId)}/${encodeURIComponent(treatmentId)}/suspend`;
    body = { kind, reason, cycleNumber, ...(kind === "temporary" && resumeDate ? { resumeDate } : {}) };
  } else if (action === "resume") {
    const reason = String($("#careScheduleResumeReason")?.value || "").trim();
    if (!reason) {
      setClinicalFormError("#careScheduleWorkflowError", "Indique el motivo para reanudar el tratamiento.");
      $("#careScheduleResumeReason")?.focus();
      return;
    }
    path = `/api/clinical/treatments/${encodeURIComponent(patientId)}/${encodeURIComponent(treatmentId)}/resume`;
    body = { reason };
  } else {
    const isPrescription = action === "request-prescription";
    const assignedToUserId = String($(isPrescription ? "#careSchedulePrescriptionDoctor" : "#careScheduleContinuityDoctor")?.value || "");
    const message = String($(isPrescription ? "#careSchedulePrescriptionMessage" : "#careScheduleContinuityMessage")?.value || "").trim();
    if (!assignedToUserId) {
      setClinicalFormError("#careScheduleWorkflowError", "Seleccione el médico que recibirá la solicitud.");
      $(isPrescription ? "#careSchedulePrescriptionDoctor" : "#careScheduleContinuityDoctor")?.focus();
      return;
    }
    path = "/api/clinical/treatment-workflow-requests";
    body = {
      type: isPrescription ? "prescription_request" : "continuity_request",
      patientId,
      treatmentId,
      cycleNumber,
      assignedToUserId,
      message
    };
  }

  careScheduleWorkflowBusy = true;
  const submit = $("#submitCareScheduleWorkflowActionBtn");
  if (submit) submit.disabled = true;
  setClinicalFormError("#careScheduleWorkflowError");
  try {
    const payload = await careScheduleJson(path, { method: "POST", body: JSON.stringify(body) });
    careScheduleWorkflowBusy = false;
    closeCareScheduleWorkflowActionModal();
    await refreshWorkflowClinicalSurfaces(patientId, payload);
    toast(action === "suspend"
      ? "Suspensión registrada en la historia clínica"
      : action === "resume"
        ? `Tratamiento reanudado en el ciclo ${cycleNumber}`
        : action === "request-prescription" && resumeFlowState
          ? "Solicitud de prescripción enviada; el ciclo permanece suspendido"
          : "Solicitud enviada y registrada en la historia clínica");
  } catch (error) {
    setClinicalFormError("#careScheduleWorkflowError", error.message || "No se pudo completar la acción.");
  } finally {
    careScheduleWorkflowBusy = false;
    if (submit) submit.disabled = false;
  }
}

function mergeWorkflowEvolution(payload, patientId) {
  if (String(getActiveLiraPatientId()) !== String(patientId)) return false;
  const evolution = payload?.evolution || payload?.event?.evolution || null;
  if (!evolution || typeof evolution !== "object") return false;
  state.evolutions = Array.isArray(state.evolutions) ? state.evolutions : [];
  const evolutionId = String(evolution.id || "");
  if (!evolutionId || !state.evolutions.some((entry) => String(entry.id) === evolutionId)) {
    state.evolutions.unshift({ ...evolution });
  }
  const revision = Number(payload.documentRevision);
  if (Number.isSafeInteger(revision) && revision > 0) state.meta.persistenceRevision = revision;
  state.meta.updatedAt = evolution.createdAt || evolution.date || new Date().toISOString();
  syncClinicalActorToState();
  storeClinicalStateLocally();
  renderAll();
  return true;
}

async function refreshWorkflowClinicalSurfaces(patientId, payload = {}) {
  careScheduleSelectedCandidateId = "";
  await loadCareScheduleCandidates(careScheduleSearchQuery(careScheduleMode === "pharmacy" ? "pharmacy" : "chairs"));
  if (String(getActiveLiraPatientId()) !== String(patientId)) return;
  try {
    const response = await fetch(`/api/clinical/patients/${encodeURIComponent(patientId)}/activate`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      cache: "no-store",
      body: "{}"
    });
    const activation = await response.json().catch(() => ({}));
    if (!response.ok || activation.ok === false) throw new Error(activation.error || "No se pudo actualizar la hoja clínica");
    const documentState = activation.state || activation.document?.document;
    if (!documentState) throw new Error("La hoja clínica no fue devuelta");
    state = normalizeState(documentState);
    syncClinicalActorToState();
    resetPatientContextAfterLiraImport();
    renderAll();
    await refreshCareWorkspace({ force: true });
  } catch (error) {
    if (!mergeWorkflowEvolution(payload, patientId)) {
      console.warn("La acción se guardó, pero la hoja deberá actualizarse manualmente.", error);
    }
  }
}

function careScheduleSupportedSlotMinutes(value) {
  const interval = Number(value);
  return [5, 10, 15, 20, 30].includes(interval) ? interval : 10;
}

function careScheduleGridLayout() {
  const slotMinutes = careScheduleSupportedSlotMinutes(careScheduleSettings.slotMinutes);
  const start = careScheduleClockMinutes(careScheduleSettings.startTime);
  const end = careScheduleClockMinutes(careScheduleSettings.endTime);
  const slotsPerHour = 60 / slotMinutes;
  const columnsPerChair = { 5: 4, 10: 3, 15: 2, 20: 3, 30: 2 }[slotMinutes];
  const rowsPerHour = slotsPerHour / columnsPerChair;
  const totalSlots = Math.max(1, Math.floor((end - start) / slotMinutes));
  const hourGroups = Math.ceil(totalSlots / slotsPerHour);
  return {
    slotMinutes,
    start,
    end,
    totalSlots,
    slotsPerHour,
    columnsPerChair,
    rowsPerHour,
    visualRows: hourGroups * rowsPerHour,
  };
}

function careScheduleChairViewport() {
  const total = Math.max(1, Number(careScheduleSettings.chairCount) || 1);
  const minimumVisible = Math.min(3, total);
  careScheduleVisibleChairCount = Math.min(total, Math.max(minimumVisible, Number(careScheduleVisibleChairCount) || 6));
  careScheduleChairOffset = Math.min(Math.max(0, Number(careScheduleChairOffset) || 0), Math.max(0, total - careScheduleVisibleChairCount));
  return {
    total,
    visible: careScheduleVisibleChairCount,
    offset: careScheduleChairOffset,
    first: careScheduleChairOffset + 1,
    last: careScheduleChairOffset + careScheduleVisibleChairCount,
    minimumVisible,
  };
}

function updateCareScheduleChairViewportControls(viewport = careScheduleChairViewport()) {
  const label = $("#careScheduleChairRange");
  const previous = $("#careSchedulePreviousChairsBtn");
  const next = $("#careScheduleNextChairsBtn");
  const zoomIn = $("#careScheduleZoomInBtn");
  const zoomOut = $("#careScheduleZoomOutBtn");
  if (label) label.textContent = `Sillones ${viewport.first}–${viewport.last} de ${viewport.total}`;
  if (previous) previous.disabled = viewport.offset <= 0;
  if (next) next.disabled = viewport.last >= viewport.total;
  if (zoomIn) zoomIn.disabled = viewport.visible <= viewport.minimumVisible;
  if (zoomOut) zoomOut.disabled = viewport.visible >= viewport.total;
}

function shiftCareScheduleChairViewport(direction) {
  const viewport = careScheduleChairViewport();
  careScheduleChairOffset = Math.min(
    Math.max(0, viewport.offset + Math.sign(Number(direction) || 0)),
    Math.max(0, viewport.total - viewport.visible)
  );
  renderCareSchedule();
}

function zoomCareScheduleChairViewport(direction) {
  const viewport = careScheduleChairViewport();
  const step = viewport.total >= 6 ? 2 : 1;
  const nextVisible = Math.min(
    viewport.total,
    Math.max(viewport.minimumVisible, viewport.visible + Math.sign(Number(direction) || 0) * step)
  );
  if (nextVisible === viewport.visible) return;
  careScheduleVisibleChairCount = nextVisible;
  careScheduleChairOffset = Math.min(viewport.offset, Math.max(0, viewport.total - nextVisible));
  careScheduleChairViewport();
  renderCareSchedule();
}

function careScheduleGridPosition(slotIndex, chair, layout = careScheduleGridLayout(), firstChair = 1) {
  const hourGroup = Math.floor(slotIndex / layout.slotsPerHour);
  const withinHour = slotIndex % layout.slotsPerHour;
  const subColumn = withinHour % layout.columnsPerChair;
  return {
    row: 2 + hourGroup * layout.rowsPerHour + Math.floor(withinHour / layout.columnsPerChair),
    column: 1 + (chair - firstChair) * layout.columnsPerChair + subColumn,
    subColumn,
    hourGroup,
  };
}

function careScheduleAppointmentSegments(startSlot, span, chair, layout, firstChair = 1) {
  const segments = [];
  const lastSlot = Math.min(layout.totalSlots, startSlot + span);
  for (let slotIndex = startSlot; slotIndex < lastSlot; slotIndex += 1) {
    const position = careScheduleGridPosition(slotIndex, chair, layout, firstChair);
    const current = segments[segments.length - 1];
    if (current && current.row === position.row && current.columnEnd === position.column) {
      current.columnEnd += 1;
      current.slotCount += 1;
    } else {
      segments.push({
        row: position.row,
        rowEnd: position.row + 1,
        columnStart: position.column,
        columnEnd: position.column + 1,
        slotCount: 1,
      });
    }
  }
  return segments.reduce((merged, segment) => {
    const previous = merged[merged.length - 1];
    if (
      previous &&
      previous.rowEnd === segment.row &&
      previous.columnStart === segment.columnStart &&
      previous.columnEnd === segment.columnEnd
    ) {
      previous.rowEnd = segment.rowEnd;
      previous.slotCount += segment.slotCount;
      return merged;
    }
    merged.push({ ...segment });
    return merged;
  }, []);
}

function careScheduleAppointmentBridge(previous, current) {
  if (!previous || !current || previous.rowEnd !== current.row) return null;
  const overlapStart = Math.max(previous.columnStart, current.columnStart);
  const overlapEnd = Math.min(previous.columnEnd, current.columnEnd);
  if (overlapStart < overlapEnd) return null;
  return {
    row: current.row,
    rowEnd: current.rowEnd,
    columnStart: Math.min(previous.columnStart, current.columnStart),
    columnEnd: Math.max(previous.columnEnd, current.columnEnd),
  };
}

function careScheduleAppointmentCornerClasses(segments, index) {
  const segment = segments[index];
  const previous = index > 0 && segments[index - 1].rowEnd === segment.row ? segments[index - 1] : null;
  const next = index < segments.length - 1 && segment.rowEnd === segments[index + 1].row ? segments[index + 1] : null;
  const classes = [];
  if (!previous) {
    classes.push("has-convex-top-left", "has-convex-top-right");
  } else {
    if (segment.columnStart !== previous.columnStart) {
      classes.push(segment.columnStart < previous.columnStart ? "has-convex-top-left" : "has-concave-top-left");
    }
    if (segment.columnEnd !== previous.columnEnd) {
      classes.push(segment.columnEnd > previous.columnEnd ? "has-convex-top-right" : "has-concave-top-right");
    }
  }
  if (!next) {
    classes.push("has-convex-bottom-left", "has-convex-bottom-right");
  } else {
    if (segment.columnStart !== next.columnStart) {
      classes.push(segment.columnStart < next.columnStart ? "has-convex-bottom-left" : "has-concave-bottom-left");
    }
    if (segment.columnEnd !== next.columnEnd) {
      classes.push(segment.columnEnd > next.columnEnd ? "has-convex-bottom-right" : "has-concave-bottom-right");
    }
  }
  return classes;
}

function careScheduleHoverTarget(eventTarget) {
  const node = eventTarget?.closest?.("[data-care-schedule-infusion],[data-care-schedule-fragment-for]");
  if (!node) return null;
  const infusionId = node.dataset.careScheduleInfusion || node.dataset.careScheduleFragmentFor;
  const item = careScheduleInfusions.find((infusion) => String(infusion.id) === String(infusionId));
  return item && !item.optimistic ? item : null;
}

function careScheduleHoverCardMarkup(item) {
  const scheduled = item.scheduledAt ? new Date(item.scheduledAt) : null;
  const validDate = scheduled && !Number.isNaN(scheduled.getTime());
  const durationMinutes = careScheduleItemDuration(item);
  const slotMinutes = careScheduleSupportedSlotMinutes(careScheduleSettings.slotMinutes);
  const span = Math.max(1, Math.ceil(durationMinutes / slotMinutes));
  const startMinutes = validDate ? scheduled.getHours() * 60 + scheduled.getMinutes() : null;
  const occupiedRange = startMinutes === null
    ? "Horario no informado"
    : `${careScheduleClock(startMinutes)} a ${careScheduleClock(startMinutes + span * slotMinutes - 1)}`;
  const dateLabel = validDate
    ? new Intl.DateTimeFormat("es-AR", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric" }).format(scheduled)
    : "Fecha no informada";
  const medicationReceived = careScheduleMedicationReceived(item);
  const medicationWithPatient = careScheduleMedicationWithPatient(item);
  const appointmentConfirmed = careScheduleAppointmentConfirmed(item);
  const prescriptionConfirmed = item.prescriptionConfirmed !== false && Boolean(item.treatmentId);
  const patientDni = careSchedulePatientDni(item);
  const insurance = String(item.insurance || "").trim();
  const affiliateNumber = String(item.affiliateNumber || "").trim();
  const status = (label, confirmed, confirmedText, pendingText) =>
    `<span class="${confirmed ? "is-confirmed" : "is-pending"}"><i aria-hidden="true"></i>${escapeHtml(label)}: <strong>${escapeHtml(confirmed ? confirmedText : pendingText)}</strong></span>`;
  return `
    <header><strong>${escapeHtml(item.patientName || "Paciente sin nombre")}</strong><small>${patientDni ? `DNI ${escapeHtml(patientDni)}` : "DNI no informado"}</small></header>
    <dl>
      <div><dt>Turno</dt><dd>${escapeHtml(dateLabel)} · ${escapeHtml(occupiedRange)} · Sillón ${escapeHtml(careScheduleChair(item.chair) || "—")}</dd></div>
      <div><dt>Obra social</dt><dd>${escapeHtml(insurance || "No informada")}${affiliateNumber ? ` · N.º ${escapeHtml(affiliateNumber)}` : " · Número no informado"}</dd></div>
      <div><dt>Diagnóstico</dt><dd>${escapeHtml(item.diagnosis || "No informado")}</dd></div>
      <div><dt>Esquema</dt><dd>${escapeHtml(careScheduleInfusionScheme(item))}</dd></div>
      <div><dt>Ciclo y duración</dt><dd>Ciclo ${escapeHtml(Number(item.cycleNumber) || 1)} · ${escapeHtml(careScheduleDurationLabel(durationMinutes))}</dd></div>
    </dl>
    <footer>
      ${status("Prescripción", prescriptionConfirmed, "Confirmada", "No confirmada")}
      ${status("Medicación", medicationReceived, "Recibida", "No confirmada")}
      ${status("La tiene el paciente", medicationWithPatient, "Sí", "No")}
      ${status("Turno", appointmentConfirmed, "Confirmado", "No confirmado")}
    </footer>`;
}

function positionCareScheduleHoverCard(event) {
  const card = $("#careScheduleHoverCard");
  if (!card || card.hidden) return;
  const gap = 14;
  const margin = 8;
  const rect = card.getBoundingClientRect();
  let left = event.clientX + gap;
  let top = event.clientY + gap;
  if (left + rect.width > window.innerWidth - margin) left = event.clientX - rect.width - gap;
  if (top + rect.height > window.innerHeight - margin) top = event.clientY - rect.height - gap;
  card.style.left = `${Math.max(margin, left)}px`;
  card.style.top = `${Math.max(margin, top)}px`;
}

function showCareScheduleHoverCard(event) {
  if (careScheduleDrag) {
    hideCareScheduleHoverCard();
    return;
  }
  const item = careScheduleHoverTarget(event.target);
  if (!item) {
    hideCareScheduleHoverCard();
    return;
  }
  const card = $("#careScheduleHoverCard");
  if (!card) return;
  if (careScheduleHoverInfusionId !== String(item.id)) {
    careScheduleHoverInfusionId = String(item.id);
    card.innerHTML = careScheduleHoverCardMarkup(item);
    card.hidden = false;
  }
  positionCareScheduleHoverCard(event);
}

function hideCareScheduleHoverCard() {
  careScheduleHoverInfusionId = "";
  const card = $("#careScheduleHoverCard");
  if (!card) return;
  card.hidden = true;
  card.innerHTML = "";
}

function renderCareSchedule() {
  hideCareScheduleHoverCard();
  if (careScheduleMode === "pharmacy") {
    renderCareSchedulePharmacy();
    return;
  }
  renderCareScheduleCandidates();
  const grid = $("#careScheduleGrid");
  const layout = careScheduleGridLayout();
  const chairViewport = careScheduleChairViewport();
  careScheduleSettings.slotMinutes = layout.slotMinutes;
  grid.dataset.slotMinutes = String(layout.slotMinutes);
  grid.dataset.columnsPerChair = String(layout.columnsPerChair);
  grid.dataset.rowsPerHour = String(layout.rowsPerHour);
  grid.dataset.firstChair = String(chairViewport.first);
  grid.dataset.lastChair = String(chairViewport.last);
  grid.style.gridTemplateColumns = `repeat(${chairViewport.visible * layout.columnsPerChair}, minmax(0, 1fr))`;
  grid.style.gridTemplateRows = `28px repeat(${layout.visualRows}, minmax(18px, 1fr))`;
  let html = "";
  for (let chair = chairViewport.first; chair <= chairViewport.last; chair += 1) {
    const columnStart = 1 + (chair - chairViewport.first) * layout.columnsPerChair;
    html += `<div class="care-schedule-chair${chair > chairViewport.first ? " has-chair-gap" : ""}" style="grid-column:${columnStart}/span ${layout.columnsPerChair};grid-row:1">Sillon ${chair}</div>`;
  }
  for (let slot = 0; slot < layout.totalSlots; slot += 1) {
    const minutes = layout.start + slot * layout.slotMinutes;
    const timeLabel = careScheduleClock(minutes);
    for (let chair = chairViewport.first; chair <= chairViewport.last; chair += 1) {
      const position = careScheduleGridPosition(slot, chair, layout, chairViewport.first);
      const firstVisualRow = (slot % layout.slotsPerHour) < layout.columnsPerChair;
      const classes = [
        "care-schedule-slot",
        firstVisualRow ? "is-hour-start" : "",
        position.subColumn === 0 ? "is-chair-start" : "",
        position.subColumn === layout.columnsPerChair - 1 ? "is-chair-end" : "",
        chair === chairViewport.first ? "is-viewport-start" : "",
      ].filter(Boolean).join(" ");
      html += `<div class="${classes}" role="gridcell" data-chair="${chair}" data-slot-index="${slot}" data-time="${timeLabel}" style="grid-column:${position.column};grid-row:${position.row}" title="Sillon ${chair}, ${timeLabel}" aria-label="Sillon ${chair}, ${timeLabel}"><span class="care-schedule-slot-time">${timeLabel}</span></div>`;
    }
  }
  let scheduledCount = 0;
  for (const infusion of careScheduleInfusions) {
    if (infusion.clinicalStatus === "cancelled") continue;
    const chair = careScheduleChair(infusion.chair); if (chair < 1 || chair > careScheduleSettings.chairCount || !infusion.scheduledAt) continue;
    const scheduled = new Date(infusion.scheduledAt);
    const minutes = scheduled.getHours() * 60 + scheduled.getMinutes();
    const slotOffset = (minutes - layout.start) / layout.slotMinutes;
    if (!Number.isInteger(slotOffset)) continue;
    const slot = slotOffset;
    const span = Math.max(1, Math.ceil(careScheduleItemDuration(infusion) / layout.slotMinutes));
    if (slot < 0 || slot + span > layout.totalSlots) continue;
    scheduledCount += 1;
    if (chair < chairViewport.first || chair > chairViewport.last) continue;
    const occupiedRange = `${careScheduleClock(minutes)} a ${careScheduleClock(minutes + span * layout.slotMinutes - 1)}`;
    const segments = careScheduleAppointmentSegments(slot, span, chair, layout, chairViewport.first);
    if (!segments.length) continue;
    let primarySegment = 0;
    for (let index = 1; index < segments.length; index += 1) {
      if (segments[index].slotCount > segments[primarySegment].slotCount) primarySegment = index;
    }
    const chairColumnStart = 1 + (chair - chairViewport.first) * layout.columnsPerChair;
    const appointmentConfirmed = careScheduleAppointmentConfirmed(infusion);
    const appointmentStatusClass = appointmentConfirmed ? "is-appointment-confirmed" : "is-appointment-pending";
    const patientDni = careSchedulePatientDni(infusion);
    let appointmentPieceMarkup = "";
    segments.forEach((segment, index) => {
      const style = `grid-column:${segment.columnStart}/${segment.columnEnd};grid-row:${segment.row}/${segment.rowEnd}`;
      const cornerClasses = careScheduleAppointmentCornerClasses(segments, index);
      const hasChairGap = chair > chairViewport.first && segment.columnStart === chairColumnStart;
      const segmentClasses = [
        index === 0 ? "is-appointment-start" : "",
        index === segments.length - 1 ? "is-appointment-end" : "",
        appointmentStatusClass,
        ...cornerClasses,
        hasChairGap ? "has-chair-gap" : "",
      ].filter(Boolean).join(" ");
      const concaveCorners = cornerClasses.filter((name) => name.startsWith("has-concave-")).map((name) =>
        `<i class="care-schedule-concave-corner ${name} ${appointmentStatusClass}${infusion.optimistic ? " is-saving" : ""}${hasChairGap ? " has-chair-gap" : ""}" style="${style}" aria-hidden="true"></i>`).join("");
      const bridge = careScheduleAppointmentBridge(segments[index - 1], segment);
      if (bridge) {
        const bridgeStyle = `grid-column:${bridge.columnStart}/${bridge.columnEnd};grid-row:${bridge.row}/${bridge.rowEnd}`;
        const bridgeHasChairGap = chair > chairViewport.first && bridge.columnStart === chairColumnStart;
        appointmentPieceMarkup += `<i class="care-schedule-appointment-fragment care-schedule-appointment-bridge ${appointmentStatusClass}${infusion.optimistic ? " is-saving" : ""}${bridgeHasChairGap ? " has-chair-gap" : ""}" data-care-schedule-fragment-for="${escapeHtml(infusion.id)}" style="${bridgeStyle}" aria-hidden="true"></i>`;
      }
      appointmentPieceMarkup += `<div class="care-schedule-appointment-fragment${segmentClasses ? ` ${segmentClasses}` : ""}${infusion.optimistic ? " is-saving" : ""}" data-care-schedule-fragment-for="${escapeHtml(infusion.id)}" style="${style}" aria-hidden="true"></div>${concaveCorners}`;
    });
    const contentSegment = segments[primarySegment];
    const contentStyle = `grid-column:${contentSegment.columnStart}/${contentSegment.columnEnd};grid-row:${contentSegment.row}/${contentSegment.rowEnd}`;
    const contentHasChairGap = chair > chairViewport.first && contentSegment.columnStart === chairColumnStart;
    html += `${appointmentPieceMarkup}<article class="care-schedule-appointment care-schedule-appointment-content${segments.length > 1 ? " is-fragmented" : ""} ${appointmentStatusClass}${infusion.optimistic ? " is-saving" : ""}${contentHasChairGap ? " has-chair-gap" : ""}" draggable="false" data-care-schedule-infusion="${escapeHtml(infusion.id)}" data-care-schedule-segments="${segments.length}" style="${contentStyle}" aria-label="${escapeHtml(infusion.patientName || "Paciente")}. ${occupiedRange}. Haga clic para ver toda la informacion del turno" aria-busy="${infusion.optimistic ? "true" : "false"}"><header><div class="care-schedule-appointment-identity"><strong>${escapeHtml(infusion.patientName || "Paciente")}</strong><small>${patientDni ? `DNI ${escapeHtml(patientDni)}` : "DNI no informado"}</small><time class="care-schedule-appointment-range">${occupiedRange}</time></div>${infusion.optimistic ? "" : `<span class="care-schedule-appointment-actions"><button class="care-schedule-detail-button" type="button" draggable="false" data-care-schedule-detail="${escapeHtml(infusion.id)}" title="Abrir detalle del turno" aria-label="Abrir detalle del turno de ${escapeHtml(infusion.patientName || "paciente")}"><i data-lucide="file-text"></i></button><button class="care-schedule-move-handle" type="button" draggable="true" data-care-schedule-move="${escapeHtml(infusion.id)}" title="Mover turno a otro horario o sillon" aria-label="Mover turno de ${escapeHtml(infusion.patientName || "paciente")} a otro horario o sillon"><i data-lucide="move"></i></button><button type="button" draggable="false" data-care-schedule-remove="${escapeHtml(infusion.id)}" title="Quitar turno del sillon" aria-label="Quitar del sillon el turno de ${escapeHtml(infusion.patientName || "paciente")}"><i data-lucide="x"></i></button></span>`}</header><span class="care-schedule-appointment-scheme">${escapeHtml(careScheduleInfusionScheme(infusion))}</span><footer><b>${escapeHtml(careScheduleDurationLabel(careScheduleItemDuration(infusion)))}</b><small>${infusion.optimistic ? "Guardando" : `Ciclo ${Number(infusion.cycleNumber) || 1}`}</small></footer></article>`;
  }
  grid.innerHTML = html;
  const activeInfusionCount = careScheduleInfusions.filter((infusion) =>
    infusion.clinicalStatus !== "cancelled").length;
  const unplaced = activeInfusionCount - scheduledCount;
  const scheduleSummary = `${careScheduleSettings.chairCount} sillones · casilleros de ${careScheduleSettings.slotMinutes} min · ${scheduledCount} turnos en grilla${unplaced ? ` · ${unplaced} sin sillon o fuera de jornada` : ""}`;
  $("#careScheduleStatus").dataset.summary = scheduleSummary;
  $("#careScheduleStatus").textContent = scheduleSummary;
  updateCareScheduleChairViewportControls(chairViewport);
  renderCareScheduleAvailability();
  renderCareScheduleSearchHighlights();
  if ($("#careScheduleDetailModal")?.classList.contains("open")) renderCareScheduleDetail();
  refreshIcons();
}

function beginCareScheduleDrag(event) {
  clearCareScheduleDraggingVisuals();
  careScheduleDropTarget = null;
  careScheduleDrag = null;
  if (!clinicalHasPermission("section.day-hospital.edit")) {
    event.preventDefault();
    return;
  }
  const candidateNode = event.target.closest("[data-care-schedule-candidate]");
  const moveHandle = event.target.closest("[data-care-schedule-move]");
  const infusionNode = moveHandle?.closest("[data-care-schedule-infusion]");
  if (candidateNode) {
    const data = careScheduleCandidates.find((item) => String(item.id) === candidateNode.dataset.careScheduleCandidate);
    const blockedReason = data ? careScheduleCandidateBlockedReason(data) : "";
    if (blockedReason) {
      event.preventDefault();
      toast(blockedReason);
      return;
    }
    if (data) {
      careScheduleDrag = { type: "candidate", data, durationMinutes: careScheduleItemDuration(data) };
      careScheduleSelectedCandidateId = String(data.id);
      syncCareScheduleCandidateSelection();
      renderCareScheduleAvailability();
    }
  } else if (moveHandle && infusionNode) {
    const data = careScheduleInfusions.find((item) => String(item.id) === infusionNode.dataset.careScheduleInfusion);
    if (data) careScheduleDrag = { type: "infusion", data, durationMinutes: careScheduleItemDuration(data) };
  }
  if (!careScheduleDrag) {
    event.preventDefault();
    return;
  }
  event.dataTransfer.effectAllowed = "move"; event.dataTransfer.setData("text/plain", careScheduleDrag.data.id || "turno");
  if (infusionNode) {
    event.dataTransfer.setDragImage(infusionNode, 18, 12);
    window.requestAnimationFrame(() => {
      infusionNode.classList.add("is-dragging");
      $$("[data-care-schedule-fragment-for]").forEach((fragment) => {
        if (fragment.dataset.careScheduleFragmentFor === infusionNode.dataset.careScheduleInfusion) {
          fragment.classList.add("is-dragging");
        }
      });
    });
  }
}

function clearCareScheduleDraggingVisuals() {
  $$(".care-schedule-appointment.is-dragging,.care-schedule-appointment-fragment.is-dragging")
    .forEach((node) => node.classList.remove("is-dragging"));
}

function careSchedulePlacementTarget(placementItem, type, slotNode) {
  if (!slotNode || !placementItem) return null;
  const chair = Number(slotNode.dataset.chair), slotIndex = Number(slotNode.dataset.slotIndex);
  const span = Math.ceil(careScheduleItemDuration(placementItem) / careScheduleSettings.slotMinutes);
  const totalSlots = Math.floor((careScheduleClockMinutes(careScheduleSettings.endTime) - careScheduleClockMinutes(careScheduleSettings.startTime)) / careScheduleSettings.slotMinutes);
  const start = careScheduleClockMinutes(careScheduleSettings.startTime) + slotIndex * careScheduleSettings.slotMinutes;
  const end = start + span * careScheduleSettings.slotMinutes;
  const conflict = careScheduleInfusions.some((infusion) => {
    if (type === "infusion" && String(infusion.id) === String(placementItem.id)) return false;
    if (infusion.clinicalStatus === "cancelled") return false;
    if (careScheduleChair(infusion.chair) !== chair || !infusion.scheduledAt) return false;
    const date = new Date(infusion.scheduledAt); const itemStart = date.getHours() * 60 + date.getMinutes();
    const itemEnd = itemStart + Math.ceil(careScheduleItemDuration(infusion) / careScheduleSettings.slotMinutes) * careScheduleSettings.slotMinutes;
    return itemStart < end && itemEnd > start;
  });
  return { chair, slotIndex, span, valid: slotIndex + span <= totalSlots && !conflict, time: slotNode.dataset.time };
}

function careScheduleTarget(slotNode) {
  if (!careScheduleDrag) return null;
  return careSchedulePlacementTarget(careScheduleDrag.data, careScheduleDrag.type, slotNode);
}

function renderCareScheduleAvailability() {
  const slots = $$(".care-schedule-slot");
  slots.forEach((slot) => {
    slot.classList.remove("is-candidate-fit");
    const label = `Sillon ${slot.dataset.chair}, ${slot.dataset.time}`;
    slot.title = label;
    slot.setAttribute("aria-label", label);
  });
  const status = $("#careScheduleStatus");
  if (status?.dataset.summary) status.textContent = status.dataset.summary;
  const candidate = careScheduleCandidates.find((item) => String(item.id) === careScheduleSelectedCandidateId);
  if (!candidate) return;
  const blockedReason = careScheduleCandidateBlockedReason(candidate);
  if (blockedReason) {
    careScheduleSelectedCandidateId = "";
    syncCareScheduleCandidateSelection();
    if (status) status.textContent = blockedReason;
    return;
  }
  let availableStarts = 0;
  slots.forEach((slot) => {
    const target = careSchedulePlacementTarget(candidate, "candidate", slot);
    if (!target?.valid) return;
    availableStarts += 1;
    slot.classList.add("is-candidate-fit");
    const label = `Disponible para ${candidate.patientName}: sillon ${target.chair}, ${target.time}`;
    slot.title = label;
    slot.setAttribute("aria-label", label);
  });
  if (status) status.textContent = `${candidate.patientName} · ${careScheduleDurationLabel(careScheduleItemDuration(candidate))} · ${availableStarts} inicios disponibles en celeste`;
}

function clearCareScheduleDragTarget(forgetTarget = false) {
  $$(".care-schedule-slot.is-drag-target,.care-schedule-slot.is-drag-invalid").forEach((node) => node.classList.remove("is-drag-target", "is-drag-invalid"));
  if (forgetTarget) careScheduleDropTarget = null;
}

function handleCareScheduleDragOver(event) {
  const slot = event.target.closest(".care-schedule-slot"); if (!slot || !careScheduleDrag) return;
  event.preventDefault(); clearCareScheduleDragTarget();
  const target = careScheduleTarget(slot); if (!target) return;
  careScheduleDropTarget = { ...target };
  for (let index = target.slotIndex; index < target.slotIndex + target.span; index += 1) {
    $(`.care-schedule-slot[data-chair="${target.chair}"][data-slot-index="${index}"]`)?.classList.add(target.valid ? "is-drag-target" : "is-drag-invalid");
  }
  event.dataTransfer.dropEffect = target.valid ? "move" : "none";
}

async function dropCareScheduleItem(event) {
  event.preventDefault();
  if (!clinicalHasPermission("section.day-hospital.edit")) return;
  const target = careScheduleDropTarget || careScheduleTarget(event.target.closest(".care-schedule-slot"));
  clearCareScheduleDragTarget(true);
  clearCareScheduleDraggingVisuals();
  if (!target?.valid || !careScheduleDrag || careScheduleBusy) return;
  const drag = careScheduleDrag;
  careScheduleBusy = true;
  const scheduledAt = `${selectedCareScheduleDate()}T${target.time}:00-03:00`;
  const previousInfusions = careScheduleInfusions;
  const previousCandidates = careScheduleCandidates;
  const optimisticId = `optimistic:${Date.now()}`;
  if (drag.type === "candidate") {
    const item = drag.data;
    careScheduleCandidates = careScheduleCandidates.filter((candidate) => candidate.id !== item.id);
    careScheduleInfusions = [...careScheduleInfusions, {
      id: optimisticId,
      patientId: item.patientId,
      treatmentId: item.treatmentId,
      patientName: item.patientName,
      patientDni: item.patientDni || "",
      insurance: item.insurance || "",
      affiliateNumber: item.affiliateNumber || "",
      diagnosis: item.diagnosis || "",
      cycleNumber: item.cycleNumber,
      scheduledAt,
      chair: String(target.chair),
      durationMinutes: careScheduleItemDuration(item),
      clinicalStatus: "planned",
      pharmacyStatus: "pending",
      administrationStatus: "not_started",
      sourceRef: { scheduler: { scheme: item.scheme || "", drugScheme: item.drugScheme || item.scheme || "", timeBasis: "local-wall-clock-v2", prescriptionConfirmed: careSchedulePrescriptionConfirmed(item), medicationReceived: careScheduleMedicationReceived(item), medicationWithPatient: careScheduleMedicationWithPatient(item), medicationLocation: careScheduleMedicationWithPatient(item) ? "patient" : "", appointmentConfirmed: false } },
      prescriptionConfirmed: careSchedulePrescriptionConfirmed(item),
      medicationReceived: careScheduleMedicationReceived(item),
      medicationWithPatient: careScheduleMedicationWithPatient(item),
      appointmentConfirmed: false,
      optimistic: true,
      medications: [],
    }];
  } else {
    careScheduleInfusions = careScheduleInfusions.map((infusion) => String(infusion.id) === String(drag.data.id)
      ? { ...infusion, scheduledAt, chair: String(target.chair), durationMinutes: careScheduleItemDuration(infusion), optimistic: true }
      : infusion);
  }
  renderCareSchedule();
  try {
    if (drag.type === "candidate") {
      const item = drag.data;
      const payload = await careScheduleJson("/api/clinical/infusions", { method: "POST", body: JSON.stringify({ patientId: item.patientId, treatmentId: item.treatmentId, cycleNumber: item.cycleNumber, scheduledAt, chair: String(target.chair), durationMinutes: careScheduleItemDuration(item), clinicalStatus: "planned", pharmacyStatus: "pending", administrationStatus: "not_started", notes: "Turno asignado desde el turnero por sillon", sourceRef: { scheduler: { scheme: item.scheme || "", drugScheme: item.drugScheme || item.scheme || "", timeBasis: "local-wall-clock-v2", prescriptionConfirmed: careSchedulePrescriptionConfirmed(item), medicationReceived: careScheduleMedicationReceived(item), medicationWithPatient: careScheduleMedicationWithPatient(item), medicationLocation: careScheduleMedicationWithPatient(item) ? "patient" : "", appointmentConfirmed: false } }, medications: [] }) });
      careScheduleInfusions = careScheduleInfusions.map((infusion) => infusion.id === optimisticId
        ? { ...infusion, ...(payload.infusion || {}), patientName: item.patientName, optimistic: false }
        : infusion);
      toast("Turno asignado");
    } else {
      const item = drag.data;
      const payload = await careScheduleJson(`/api/clinical/infusions/${item.id}`, { method: "PATCH", body: JSON.stringify({ expectedVersion: item.revision, scheduledAt, chair: String(target.chair), durationMinutes: careScheduleItemDuration(item) }) });
      careScheduleInfusions = careScheduleInfusions.map((infusion) => String(infusion.id) === String(item.id)
        ? { ...infusion, ...(payload.infusion || {}), patientName: item.patientName, optimistic: false }
        : infusion);
      toast("Turno reprogramado");
    }
    renderCareSchedule();
    await loadCareSchedule();
  } catch (error) {
    careScheduleInfusions = previousInfusions;
    careScheduleCandidates = previousCandidates;
    renderCareSchedule();
    if (error.code === "CHAIR_SCHEDULE_CONFLICT") {
      await loadCareSchedule();
      toast("Ese lugar acaba de ser ocupado. La agenda se actualizo.");
    } else {
      toast(error.message);
    }
  } finally {
    careScheduleBusy = false;
    careScheduleDrag = null;
  }
}

async function removeCareScheduleAppointment(event) {
  const button = event.target.closest("[data-care-schedule-remove]");
  if (!button) {
    if (event.target.closest("[data-care-schedule-move]")) return;
    const appointment = event.target.closest("[data-care-schedule-infusion],[data-care-schedule-fragment-for]");
    const infusionId = appointment?.dataset.careScheduleInfusion || appointment?.dataset.careScheduleFragmentFor;
    if (infusionId) openCareScheduleDetailModal(infusionId);
    return;
  }
  if (!clinicalHasPermission("section.day-hospital.edit")) {
    toast("Su rol permite consultar el turnero, pero no modificarlo.");
    return;
  }
  if (careScheduleBusy) return;
  event.preventDefault();
  event.stopPropagation();
  const item = careScheduleInfusions.find((infusion) => String(infusion.id) === String(button.dataset.careScheduleRemove));
  if (!item) return;
  careScheduleBusy = true;
  button.disabled = true;
  try {
    await careScheduleJson(`/api/clinical/infusions/${item.id}`, {
      method: "PATCH",
      body: JSON.stringify({
        expectedVersion: item.revision,
        scheduledAt: null,
        chair: null,
        clinicalStatus: "cancelled",
        pharmacyStatus: "cancelled",
        administrationStatus: "cancelled",
        reason: "Turno eliminado desde el turnero por sillon",
      }),
    });
    careScheduleInfusions = careScheduleInfusions.filter((infusion) =>
      String(infusion.id) !== String(item.id));
    renderCareSchedule();
    await loadCareSchedule();
    toast("Turno quitado del sillon. El estado del tratamiento no cambio.");
  } catch (error) {
    toast(error.message);
    button.disabled = false;
  } finally {
    careScheduleBusy = false;
  }
}

function setCareQrScannerStatus(tone, title, detail = "") {
  const output = $("#careQrScannerStatus");
  if (!output) return;
  const icons = { idle: "info", loading: "loader-circle", success: "circle-check-big", error: "triangle-alert" };
  output.className = `care-qr-status is-${tone}`;
  output.innerHTML = `<i data-lucide="${icons[tone] || icons.idle}"></i><div><strong>${escapeHtml(title)}</strong><span>${escapeHtml(detail)}</span></div>`;
  refreshIcons();
}

function setCareQrCameraState(active, label = "") {
  const stateOutput = $("#careQrCameraState");
  const placeholder = $("#careQrCameraPlaceholder");
  const start = $("#startCareQrCameraBtn");
  const stop = $("#stopCareQrCameraBtn");
  if (stateOutput) {
    stateOutput.classList.toggle("is-active", active);
    stateOutput.innerHTML = `<i data-lucide="${active ? "camera" : "camera-off"}"></i> ${escapeHtml(label || (active ? "Cámara activa" : "Cámara detenida"))}`;
  }
  if (placeholder) placeholder.hidden = active;
  if (start) start.disabled = active || careQrScannerBusy;
  if (stop) stop.disabled = !active;
  refreshIcons();
}

function resetCareQrScanner() {
  careQrScannerLastCode = "";
  careQrScannerOperationId = "";
  careQrScannerResolved = null;
  careQrScannerBusy = false;
  const manual = $("#careQrManualCode");
  const image = $("#careQrImageInput");
  const result = $("#careQrScannerResult");
  if (manual) manual.value = "";
  if (image) image.value = "";
  if (result) {
    result.hidden = true;
    result.innerHTML = "";
  }
  setCareQrCameraState(false);
  setCareQrScannerStatus("idle", "Listo para escanear", "El código se valida en el sistema local antes de abrir la ficha.");
}

function openCareQrScannerModal() {
  if (!clinicalHasPermission("section.day-hospital.edit")) {
    toast("Su rol permite consultar Hospital de día, pero no registrar escaneos.");
    return;
  }
  careQrScannerReturnFocus = document.activeElement;
  resetCareQrScanner();
  showCareModal("careQrScannerModal");
  $("#openCareQrScannerBtn")?.setAttribute("aria-expanded", "true");
  window.requestAnimationFrame(() => $("#startCareQrCameraBtn")?.focus());
}

function closeCareQrScannerModal({ restoreFocus = true } = {}) {
  careQrScannerRequestVersion += 1;
  stopCareQrCamera();
  closeCareModal("careQrScannerModal");
  $("#openCareQrScannerBtn")?.setAttribute("aria-expanded", "false");
  const returnFocus = careQrScannerReturnFocus;
  careQrScannerReturnFocus = null;
  if (restoreFocus && returnFocus?.isConnected) window.requestAnimationFrame(() => returnFocus.focus());
}

function stopCareQrCamera({ announce = false } = {}) {
  if (careQrScannerFrame) window.cancelAnimationFrame(careQrScannerFrame);
  careQrScannerFrame = 0;
  careQrScannerLastFrameAt = 0;
  careQrScannerDecoding = false;
  careQrScannerStream?.getTracks?.().forEach((track) => track.stop());
  careQrScannerStream = null;
  const video = $("#careQrScannerVideo");
  if (video) video.srcObject = null;
  setCareQrCameraState(false);
  if (announce) setCareQrScannerStatus("idle", "Cámara detenida", "Puede reiniciarla, elegir una imagen o pegar el contenido del código.");
}

async function loadCareQrDecoderFallback() {
  if (typeof window.jsQR === "function") return true;
  if (careQrDecoderLoading) return careQrDecoderLoading;
  careQrDecoderLoading = new Promise((resolve) => {
    const existing = document.querySelector('script[data-care-qr-decoder="jsqr"]');
    if (existing) {
      existing.addEventListener("load", () => resolve(typeof window.jsQR === "function"), { once: true });
      existing.addEventListener("error", () => resolve(false), { once: true });
      return;
    }
    const script = document.createElement("script");
    script.src = "/__clone/vendor/jsQR.js";
    script.async = true;
    script.dataset.careQrDecoder = "jsqr";
    script.addEventListener("load", () => resolve(typeof window.jsQR === "function"), { once: true });
    script.addEventListener("error", () => resolve(false), { once: true });
    document.head.append(script);
  }).finally(() => {
    careQrDecoderLoading = null;
  });
  return careQrDecoderLoading;
}

async function ensureCareQrDecoder() {
  if ("BarcodeDetector" in window) {
    try {
      careQrBarcodeDetector ||= new window.BarcodeDetector({ formats: ["qr_code"] });
      return "native";
    } catch {
      careQrBarcodeDetector = null;
    }
  }
  return await loadCareQrDecoderFallback() ? "jsqr" : "";
}

function drawCareQrSource(source, width, height) {
  const canvas = $("#careQrScannerCanvas");
  const context = canvas?.getContext("2d", { willReadFrequently: true });
  if (!canvas || !context || !width || !height) return null;
  const scale = Math.min(1, 1600 / Math.max(width, height));
  canvas.width = Math.max(1, Math.round(width * scale));
  canvas.height = Math.max(1, Math.round(height * scale));
  context.drawImage(source, 0, 0, canvas.width, canvas.height);
  return { canvas, context };
}

async function decodeCareQrCanvas(canvas, context) {
  if (careQrBarcodeDetector) {
    const codes = await careQrBarcodeDetector.detect(canvas);
    const value = String(codes?.[0]?.rawValue || "").trim();
    if (value) return value;
  }
  if (typeof window.jsQR === "function") {
    const image = context.getImageData(0, 0, canvas.width, canvas.height);
    return String(window.jsQR(image.data, image.width, image.height, { inversionAttempts: "attemptBoth" })?.data || "").trim();
  }
  return "";
}

async function scanCareQrCameraFrame() {
  if (!careQrScannerStream || !$("#careQrScannerModal")?.classList.contains("open")) return;
  const frameTime = window.performance?.now?.() || Date.now();
  if (frameTime - careQrScannerLastFrameAt < 120) {
    careQrScannerFrame = window.requestAnimationFrame(scanCareQrCameraFrame);
    return;
  }
  careQrScannerLastFrameAt = frameTime;
  const video = $("#careQrScannerVideo");
  if (!careQrScannerDecoding && video?.readyState >= 2 && video.videoWidth && video.videoHeight) {
    careQrScannerDecoding = true;
    try {
      const drawing = drawCareQrSource(video, video.videoWidth, video.videoHeight);
      const value = drawing ? await decodeCareQrCanvas(drawing.canvas, drawing.context) : "";
      if (value) {
        stopCareQrCamera();
        await resolveCareQrCode(value);
        return;
      }
    } catch {
      // Un fotograma ilegible es normal mientras se encuadra el código.
    } finally {
      careQrScannerDecoding = false;
    }
  }
  if (careQrScannerStream) careQrScannerFrame = window.requestAnimationFrame(scanCareQrCameraFrame);
}

async function startCareQrCamera() {
  if (careQrScannerStream || careQrScannerBusy) return;
  if (!navigator.mediaDevices?.getUserMedia) {
    setCareQrScannerStatus("error", "La cámara no está disponible", "Use una imagen del QR o pegue su contenido.");
    return;
  }
  setCareQrScannerStatus("loading", "Preparando el lector", "Comprobando la cámara y el decodificador local.");
  const decoder = await ensureCareQrDecoder();
  if (!decoder) {
    setCareQrScannerStatus("error", "No hay un decodificador QR disponible", "Puede pegar el contenido del código y continuar sin cámara.");
    return;
  }
  try {
    careQrScannerStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: "environment" } },
      audio: false
    });
    const video = $("#careQrScannerVideo");
    if (!video) throw new Error("No se encontró la vista de la cámara.");
    video.srcObject = careQrScannerStream;
    await video.play();
    setCareQrCameraState(true, decoder === "native" ? "Cámara activa" : "Cámara activa · lector local");
    setCareQrScannerStatus("loading", "Buscando el código", "Centre el QR dentro del recuadro. La lectura es automática.");
    careQrScannerFrame = window.requestAnimationFrame(scanCareQrCameraFrame);
  } catch (error) {
    stopCareQrCamera();
    setCareQrScannerStatus("error", "No se pudo abrir la cámara", "Revise el permiso del navegador o use una imagen del QR.");
  }
}

async function handleCareQrImage(event) {
  const file = event.target.files?.[0];
  if (!file || careQrScannerBusy) return;
  stopCareQrCamera();
  setCareQrScannerStatus("loading", "Leyendo la imagen", "Buscando un código QR legible.");
  try {
    const decoder = await ensureCareQrDecoder();
    if (!decoder) throw new Error("No hay un decodificador QR disponible para imágenes.");
    const bitmap = await createImageBitmap(file);
    const drawing = drawCareQrSource(bitmap, bitmap.width, bitmap.height);
    bitmap.close();
    const value = drawing ? await decodeCareQrCanvas(drawing.canvas, drawing.context) : "";
    if (!value) throw new Error("No se encontró un QR legible en la imagen seleccionada.");
    await resolveCareQrCode(value);
  } catch (error) {
    setCareQrScannerStatus("error", "No se pudo leer la imagen", error.message || "Pruebe con una imagen más nítida.");
  } finally {
    event.target.value = "";
  }
}

function handleCareQrManualSubmit(event) {
  event.preventDefault();
  const code = $("#careQrManualCode")?.value || "";
  void resolveCareQrCode(code);
}

function careQrEntityLabel(value, fallback = "") {
  if (["string", "number"].includes(typeof value)) return String(value).trim() || fallback;
  if (value && typeof value === "object") {
    return String(value.name || value.nombre || value.label || value.scheme || value.diagnosis || value.id || "").trim() || fallback;
  }
  return fallback;
}

function normalizeCareQrInfusion(payload) {
  const patient = payload?.patient || {};
  const treatment = payload?.treatment || {};
  const infusion = payload?.infusion || {};
  return {
    ...infusion,
    id: String(infusion.id || "").trim(),
    patientId: String(infusion.patientId || patient.id || "").trim(),
    treatmentId: String(infusion.treatmentId || treatment.id || "").trim(),
    cycleNumber: Number(infusion.cycleNumber) || 1,
    patientName: infusion.patientName || patient.fullName || patient.name || "Paciente sin nombre",
    patientDni: infusion.patientDni || patient.dni || "",
    medicalRecord: infusion.medicalRecord || patient.medicalRecord || "",
    diagnosis: infusion.diagnosis || careQrEntityLabel(treatment.diagnosis, "No informado"),
    scheme: infusion.scheme || careQrEntityLabel(treatment.scheme, "Esquema no informado")
  };
}

function enrichCareQrInfusion(item) {
  const candidate = careScheduleCandidates.find((entry) =>
    String(entry.patientId || "") === String(item.patientId || "") &&
    String(entry.treatmentId || "") === String(item.treatmentId || "") &&
    Number(entry.cycleNumber) === Number(item.cycleNumber)) || {};
  const patient = state?.patient || {};
  return {
    ...candidate,
    ...item,
    patientName: item.patientName || patient.fullName || candidate.patientName,
    patientDni: item.patientDni || patient.dni || candidate.patientDni,
    medicalRecord: item.medicalRecord || patient.medicalRecord || candidate.medicalRecord,
    insurance: item.insurance || candidate.insurance || patient.insurance || "",
    affiliateNumber: item.affiliateNumber || candidate.affiliateNumber || patient.affiliateNumber || ""
  };
}

function mergeCareQrInfusion(item) {
  if (!item?.id) return;
  const merge = (items) => {
    const current = items.find((entry) => String(entry.id) === String(item.id));
    return current
      ? items.map((entry) => String(entry.id) === String(item.id) ? { ...entry, ...item } : entry)
      : [...items, item];
  };
  careScheduleInfusions = merge(careScheduleInfusions);
  careInfusions = merge(careInfusions);
}

function renderCareQrResolved(payload) {
  const result = $("#careQrScannerResult");
  if (!result) return;
  const patient = payload?.patient || {};
  const treatment = payload?.treatment || {};
  const infusion = normalizeCareQrInfusion(payload);
  const chair = careScheduleChair(infusion.chair);
  const appointment = infusion.scheduledAt
    ? `${formatCareDateTime(infusion.scheduledAt)}${chair ? ` · Sillón ${chair}` : ""}`
    : "Turno todavía no asignado";
  result.hidden = false;
  result.innerHTML = `
    <header><i data-lucide="scan-line"></i><div><small>QR identificado</small><strong>${escapeHtml(patient.fullName || infusion.patientName)}</strong></div></header>
    <dl>
      <div><dt>Documento</dt><dd>${escapeHtml(patient.dni ? `DNI ${patient.dni}` : "DNI no informado")}</dd></div>
      <div><dt>Tratamiento</dt><dd>${escapeHtml(careQrEntityLabel(treatment.scheme, infusion.scheme))}</dd></div>
      <div><dt>Ciclo</dt><dd>${escapeHtml(infusion.cycleNumber)}</dd></div>
      <div><dt>Turno</dt><dd>${escapeHtml(appointment)}</dd></div>
    </dl>
    <div class="care-qr-result-actions">
      <button class="tool-button primary" type="button" data-care-qr-confirm><i data-lucide="clipboard-check"></i><span>Abrir ficha de administración</span></button>
      <button class="tool-button" type="button" data-care-qr-rescan><i data-lucide="scan-line"></i><span>Escanear otro</span></button>
    </div>`;
  refreshIcons();
}

async function activateCareQrPatient(patientId) {
  const targetPatientId = String(patientId || "").trim();
  if (!targetPatientId) throw new Error("El QR no identifica un paciente.");
  const previousState = state;
  try {
    const response = await fetch(`/api/clinical/patients/${encodeURIComponent(targetPatientId)}/activate`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      cache: "no-store",
      body: "{}"
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok === false) throw new Error(payload.error || "No se pudo abrir la historia del paciente.");
    const documentState = payload.state || payload.document?.document;
    if (!documentState) throw new Error("La base local no devolvió la historia clínica.");
    const activatedState = normalizeState(documentState);
    if (String(activatedState?.meta?.liraImport?.patientId || "") !== targetPatientId ||
        String(activatedState?.meta?.liraImport?.origin || "") !== "local") {
      throw new Error("La historia local abierta no coincide con el paciente identificado por el QR.");
    }
    state = activatedState;
    clinicalContextVersion += 1;
    timelineAiLoading = false;
    agentBusy = false;
    setAgentBusy(false);
    syncClinicalActorToState();
    resetPatientContextAfterLiraImport();
    renderAll();
    setRightTab("care");
    await refreshCareWorkspace({ force: true });
  } catch (error) {
    state = previousState;
    renderAll();
    throw error;
  }
}

async function openResolvedCareQrAdministration() {
  if (careQrScannerBusy) return;
  const payload = careQrScannerResolved;
  const infusion = normalizeCareQrInfusion(payload);
  if (!payload?.patient?.id || !payload?.treatment?.id || !infusion.id) {
    setCareQrScannerStatus("error", "Vuelva a identificar el QR", "No hay una aplicación confirmada para abrir.");
    return;
  }
  const requestVersion = ++careQrScannerRequestVersion;
  careQrScannerBusy = true;
  $("#careQrScannerResult")?.querySelectorAll("button").forEach((button) => {
    button.disabled = true;
  });
  setCareQrScannerStatus("loading", "Abriendo la ficha", "Recuperando la versión más reciente de la aplicación.");
  try {
    await activateCareQrPatient(payload.patient.id);
    if (requestVersion !== careQrScannerRequestVersion) return;
    const latestPayload = await careScheduleJson(
      `/api/clinical/infusions?patientId=${encodeURIComponent(payload.patient.id)}&t=${Date.now()}`
    );
    if (requestVersion !== careQrScannerRequestVersion) return;
    const latestInfusion = (latestPayload.infusions || []).find((item) =>
      String(item.id) === String(infusion.id));
    if (!latestInfusion) throw new Error("La aplicación ya no está disponible. Actualice el turno y vuelva a escanear.");
    const enrichedInfusion = enrichCareQrInfusion({ ...infusion, ...latestInfusion });
    mergeCareQrInfusion(enrichedInfusion);
    careQrScannerBusy = false;
    closeCareQrScannerModal({ restoreFocus: false });
    openCareScheduleDetailModal(enrichedInfusion.id, {
      source: "qr",
      qrScan: { ...payload, infusion: enrichedInfusion }
    });
    toast(payload.idempotent || payload.scan?.idempotent
      ? "QR ya registrado; ficha de administración abierta"
      : "QR registrado; ficha de administración abierta");
  } catch (error) {
    if (requestVersion !== careQrScannerRequestVersion) return;
    renderCareQrResolved(payload);
    setCareQrScannerStatus("error", "No se pudo abrir la ficha", error.message || "Actualice el turno y vuelva a intentarlo.");
  } finally {
    if (requestVersion === careQrScannerRequestVersion) {
      careQrScannerBusy = false;
      $("#careQrScannerResult")?.querySelectorAll("button").forEach((button) => {
        button.disabled = false;
      });
    }
  }
}

function handleCareQrResolvedAction(event) {
  if (event.target.closest("[data-care-qr-confirm]")) {
    void openResolvedCareQrAdministration();
    return;
  }
  if (!event.target.closest("[data-care-qr-rescan]") || careQrScannerBusy) return;
  resetCareQrScanner();
  window.requestAnimationFrame(() => $("#startCareQrCameraBtn")?.focus());
}

async function resolveCareQrCode(rawCode) {
  const code = String(rawCode || "").trim();
  if (careQrScannerBusy) return;
  if (!clinicalHasPermission("section.day-hospital.edit")) {
    setCareQrScannerStatus("error", "Acción no permitida", "El registro del escaneo requiere permiso de edición en Hospital de día.");
    return;
  }
  if (!code) {
    setCareQrScannerStatus("error", "Ingrese un código", "Pegue el contenido completo del QR para identificarlo.");
    $("#careQrManualCode")?.focus();
    return;
  }
  stopCareQrCamera();
  if (code !== careQrScannerLastCode || !careQrScannerOperationId) {
    careQrScannerResolved = null;
    const result = $("#careQrScannerResult");
    if (result) {
      result.hidden = true;
      result.innerHTML = "";
    }
    careQrScannerLastCode = code;
    careQrScannerOperationId = makeId("qr-scan");
  }
  const requestVersion = ++careQrScannerRequestVersion;
  careQrScannerBusy = true;
  setCareQrCameraState(false);
  setCareQrScannerStatus("loading", "Validando el QR", "Buscando paciente, tratamiento y ciclo en la base local.");
  try {
    const payload = await careScheduleJson("/api/clinical/qr-scans", {
      method: "POST",
      body: JSON.stringify({ code, operationId: careQrScannerOperationId })
    });
    if (requestVersion !== careQrScannerRequestVersion) return;
    const infusion = normalizeCareQrInfusion(payload);
    if (payload.ok === false || !payload.patient?.id || !payload.treatment?.id || !infusion.id) {
      throw new Error(payload.error || "El QR no resolvió una aplicación clínica completa.");
    }
    const resolvedCycleNumber = Number(payload.infusion?.cycleNumber);
    if (String(infusion.patientId) !== String(payload.patient.id) ||
        String(infusion.treatmentId) !== String(payload.treatment.id) ||
        !Number.isSafeInteger(resolvedCycleNumber) ||
        resolvedCycleNumber < 1) {
      throw new Error("La identidad del paciente, tratamiento o ciclo no coincide con la aplicación.");
    }
    careQrScannerResolved = payload;
    renderCareQrResolved(payload);
    setCareQrScannerStatus("success", "QR reconocido", "Verifique el nombre, el documento y el ciclo antes de abrir la ficha.");
  } catch (error) {
    if (requestVersion !== careQrScannerRequestVersion) return;
    if (careQrScannerResolved) renderCareQrResolved(careQrScannerResolved);
    setCareQrScannerStatus("error", "No se pudo identificar el QR", error.message || "Compruebe que corresponda a una aplicación local con turno.");
  } finally {
    if (requestVersion === careQrScannerRequestVersion) {
      careQrScannerBusy = false;
      setCareQrCameraState(Boolean(careQrScannerStream));
    }
  }
}

function openCareScheduleDetailModal(infusionId, { source = "schedule", qrScan = null } = {}) {
  const item = careScheduleInfusions.find((infusion) => String(infusion.id) === String(infusionId));
  if (!item || item.optimistic) return;
  careScheduleDetailInfusionId = String(item.id);
  careScheduleDetailSource = source === "qr" ? "qr" : "schedule";
  careScheduleDetailQrScan = careScheduleDetailSource === "qr" ? qrScan : null;
  careQrFinalizeOperationId = "";
  careQrFinalizeBusy = false;
  renderCareScheduleDetail();
  showCareModal("careScheduleDetailModal");
}

function closeCareScheduleDetailModal({ restoreFocus = true } = {}) {
  const infusionId = careScheduleDetailInfusionId;
  closeCareModal("careScheduleDetailModal");
  careScheduleDetailInfusionId = "";
  careScheduleDetailSource = "schedule";
  careScheduleDetailQrScan = null;
  careQrFinalizeOperationId = "";
  careQrFinalizeBusy = false;
  if (restoreFocus && infusionId) {
    window.requestAnimationFrame(() => $(`[data-care-schedule-infusion="${CSS.escape(infusionId)}"]`)?.focus());
  }
}

function careScheduleDetailStatus(label, confirmed, confirmedText, pendingText) {
  return `<div class="care-schedule-detail-status ${confirmed ? "is-confirmed" : "is-pending"}"><i data-lucide="${confirmed ? "circle-check-big" : "circle-dashed"}"></i><span>${escapeHtml(label)}</span><strong>${escapeHtml(confirmed ? confirmedText : pendingText)}</strong></div>`;
}

function careQrAdministrationState(item) {
  const clinicalStatus = String(item?.clinicalStatus || "planned").toLowerCase();
  const pharmacyStatus = String(item?.pharmacyStatus || "pending").toLowerCase();
  const administrationStatus = String(item?.administrationStatus || "not_started").toLowerCase();
  const medications = Array.isArray(item?.medications) ? item.medications : null;
  const pendingMedications = medications
    ? medications.filter((medication) =>
      !["completed", "withheld", "cancelled"].includes(String(medication?.administrationStatus || "").toLowerCase()))
    : [];
  const medicationsReady = Boolean(medications) && pendingMedications.length === 0;
  const pharmacyReady = ["released", "not_required"].includes(pharmacyStatus);
  const administrationReady = administrationStatus === "completed";
  const clinicalReady = clinicalStatus === "observation";
  const completed = clinicalStatus === "completed";
  const unavailable = ["cancelled"].includes(clinicalStatus) || ["cancelled", "withheld"].includes(administrationStatus);
  const blockers = [];
  if (!completed && !clinicalReady) blockers.push("El estado clínico debe estar En observación.");
  if (!completed && !pharmacyReady) blockers.push("Farmacia debe estar Liberada o No requerida.");
  if (!completed && !administrationReady) blockers.push("La administración general debe estar Completada.");
  if (!completed && !medications) blockers.push("No se pudo verificar el estado de las drogas; vuelva a escanear.");
  if (!completed && pendingMedications.length) {
    const names = pendingMedications.map((medication) => medication.drugName || "droga sin nombre").slice(0, 3);
    blockers.push(`Drogas pendientes: ${names.join(", ")}${pendingMedications.length > names.length ? "…" : ""}.`);
  }
  const allowed = clinicalStatus === "observation" &&
    pharmacyReady &&
    administrationReady &&
    medicationsReady &&
    !completed &&
    !unavailable;
  return {
    clinicalStatus,
    pharmacyStatus,
    administrationStatus,
    medications,
    pendingMedications,
    clinicalReady,
    pharmacyReady,
    administrationReady,
    medicationsReady,
    medicationCount: medications?.length || 0,
    resolvedMedicationCount: medications ? medications.length - pendingMedications.length : 0,
    completed,
    unavailable,
    allowed,
    blockers
  };
}

function renderCareQrAdministrationCompletion(item) {
  const stateInfo = careQrAdministrationState(item);
  const canEdit = clinicalHasPermission("section.day-hospital.edit");
  const statusLabel = CARE_INFUSION_LABELS[stateInfo.clinicalStatus] || stateInfo.clinicalStatus;
  const pharmacyLabel = CARE_INFUSION_LABELS[stateInfo.pharmacyStatus] || stateInfo.pharmacyStatus;
  const administrationLabel = CARE_INFUSION_LABELS[stateInfo.administrationStatus] || stateInfo.administrationStatus;
  if (stateInfo.completed) {
    return `<section class="care-qr-administration is-completed" aria-label="Finalización de la administración">
      <header><i data-lucide="badge-check"></i><div><small>Administración por QR</small><strong>Sesión finalizada</strong><span>El estado local de esta aplicación ya figura como completado.</span></div>
        <button class="tool-button care-qr-open-treatment" type="button" data-care-qr-open-treatment data-treatment-id="${escapeAttr(item.treatmentId || "")}" data-cycle-number="${escapeAttr(Number(item.cycleNumber) || 1)}"><i data-lucide="notebook-tabs"></i><span>Abrir tratamiento completo</span></button>
      </header>
    </section>`;
  }
  const stateMessage = stateInfo.unavailable
    ? "La aplicación está cancelada o suspendida y no admite finalización."
    : stateInfo.allowed
      ? "Todo listo para cerrar. Confirme la administración y deje una observación clínica."
      : "Complete los puntos pendientes antes de cerrar la aplicación.";
  const checklist = [
    ["Estado clínico", stateInfo.clinicalReady, stateInfo.clinicalReady ? "En observación" : statusLabel],
    ["Farmacia", stateInfo.pharmacyReady, pharmacyLabel],
    ["Administración", stateInfo.administrationReady, administrationLabel],
    ["Drogas", stateInfo.medicationsReady,
      stateInfo.medications
        ? stateInfo.medicationCount
          ? `${stateInfo.resolvedMedicationCount}/${stateInfo.medicationCount} resueltas`
          : "Sin drogas pendientes"
        : "No verificadas"],
  ];
  const checklistMarkup = checklist.map(([label, ready, value]) =>
    `<li class="care-qr-check ${ready ? "is-ready" : "is-pending"}"><i data-lucide="${ready ? "circle-check-big" : "circle-dashed"}"></i><span><strong>${escapeHtml(label)}</strong><small>${escapeHtml(value)}</small></span></li>`
  ).join("");
  const nextStep = getCareInfusionNextStep(item);
  const nextStepMarkup = nextStep && !stateInfo.unavailable
    ? `<div class="care-qr-next-step"><span><strong>Siguiente paso</strong><small>${escapeHtml(nextStep.label)} para continuar el circuito.</small></span><button class="tool-button primary" type="button" data-care-infusion-action="advance" data-infusion-id="${escapeAttr(item.id || "")}" data-version="${escapeAttr(item.revision || "")}"${canEdit && !careInfusionMutationBusy ? "" : " disabled"}><i data-lucide="arrow-right"></i><span>${escapeHtml(nextStep.label)}</span></button></div>`
    : "";
  return `<section class="care-qr-administration" aria-label="Finalización de la administración">
    <header><i data-lucide="clipboard-check"></i><div><small>Administración por QR</small><strong>Finalizar aplicación</strong><span>${escapeHtml(stateMessage)}</span></div>
      <button class="tool-button care-qr-open-treatment" type="button" data-care-qr-open-treatment data-treatment-id="${escapeAttr(item.treatmentId || "")}" data-cycle-number="${escapeAttr(Number(item.cycleNumber) || 1)}"><i data-lucide="notebook-tabs"></i><span>Abrir tratamiento completo</span></button>
    </header>
    <ul class="care-qr-closing-checklist" aria-label="Requisitos para finalizar">${checklistMarkup}</ul>
    ${nextStepMarkup}
    <form id="careQrAdministrationCompletionForm" novalidate>
      <label class="care-qr-confirmation">
        <input id="careQrAdministrationConfirmed" name="confirmed" type="checkbox"${stateInfo.allowed && canEdit ? "" : " disabled"}>
        <span><strong>Confirmo que la administración fue completada</strong><small>Esta acción queda vinculada al usuario activo y a la lectura del QR.</small></span>
      </label>
      <label class="care-qr-observation" for="careQrAdministrationObservation">
        <span>Observación de finalización</span>
        <textarea id="careQrAdministrationObservation" name="observation" rows="3" minlength="3" maxlength="3000" required placeholder="Registre tolerancia, incidencias o condición al egreso"${stateInfo.allowed && canEdit ? "" : " disabled"}></textarea>
      </label>
      <div class="care-qr-administration-actions">
        <p id="careQrAdministrationStatus" role="status" aria-live="polite">${canEdit ? escapeHtml(stateMessage) : "Su rol no permite finalizar aplicaciones."}</p>
        <button class="tool-button primary" id="careQrAdministrationCompleteBtn" type="submit" disabled><i data-lucide="check-check"></i><span>Finalizar aplicación</span></button>
      </div>
    </form>
  </section>`;
}

function syncCareQrAdministrationCompletion(event) {
  const form = event?.target?.closest?.("#careQrAdministrationCompletionForm") || $("#careQrAdministrationCompletionForm");
  if (!form || careScheduleDetailSource !== "qr") return;
  const item = careScheduleInfusions.find((infusion) => String(infusion.id) === careScheduleDetailInfusionId);
  const stateInfo = careQrAdministrationState(item);
  const confirmed = Boolean(form.elements.confirmed?.checked);
  const observation = String(form.elements.observation?.value || "").trim();
  const button = $("#careQrAdministrationCompleteBtn", form);
  if (button) {
    button.disabled = careQrFinalizeBusy ||
      !clinicalHasPermission("section.day-hospital.edit") ||
      !stateInfo.allowed ||
      !confirmed ||
      observation.length < 3;
  }
}

function setCareQrAdministrationStatus(message, tone = "idle") {
  const output = $("#careQrAdministrationStatus");
  if (!output) return;
  output.textContent = message;
  output.className = `is-${tone}`;
}

async function openCareQrTreatmentFromDetail(event) {
  const button = event.target.closest("[data-care-qr-open-treatment]");
  if (!button || careScheduleDetailSource !== "qr") return;
  const treatmentId = String(button.dataset.treatmentId || careScheduleDetailQrScan?.treatment?.id || "").trim();
  const cycleNumber = Math.max(1, Number(button.dataset.cycleNumber || careScheduleDetailQrScan?.infusion?.cycleNumber) || 1);
  if (!treatmentId) {
    toast("El QR no identifica el tratamiento completo.");
    return;
  }
  button.disabled = true;
  try {
    let record = careTreatments.find((item) => careTreatmentManagerRecordId(item) === treatmentId);
    if (!record) {
      await refreshCareWorkspace({ force: true });
      record = careTreatments.find((item) => careTreatmentManagerRecordId(item) === treatmentId);
    }
    if (!record) throw new Error("El tratamiento identificado ya no está disponible en la historia local.");
    closeCareScheduleDetailModal({ restoreFocus: false });
    setCareHospitalTab("treatments");
    openCareTreatmentManagerDetail(record, "drugs", { cycleNumber });
    toast(`Tratamiento abierto en el ciclo ${cycleNumber}`);
  } catch (error) {
    toast(error.message || "No se pudo abrir el tratamiento completo.");
    button.disabled = false;
  }
}

async function submitCareQrAdministrationCompletion(event) {
  if (!event.target.matches("#careQrAdministrationCompletionForm")) return;
  event.preventDefault();
  if (careQrFinalizeBusy || careScheduleDetailSource !== "qr") return;
  if (!clinicalHasPermission("section.day-hospital.edit")) {
    setCareQrAdministrationStatus("Su rol no permite finalizar aplicaciones.", "error");
    return;
  }
  const form = event.target;
  const item = careScheduleInfusions.find((infusion) => String(infusion.id) === careScheduleDetailInfusionId);
  if (!item) {
    setCareQrAdministrationStatus("La aplicación ya no está disponible. Vuelva a escanear el QR.", "error");
    return;
  }
  const stateInfo = careQrAdministrationState(item);
  if (!stateInfo.allowed) {
    setCareQrAdministrationStatus("El estado actual no permite finalizar. Avance primero hasta En observación.", "error");
    syncCareQrAdministrationCompletion();
    return;
  }
  const confirmed = Boolean(form.elements.confirmed?.checked);
  const observation = String(form.elements.observation?.value || "").trim();
  if (!confirmed || observation.length < 3) {
    setCareQrAdministrationStatus("Confirme la administración y escriba una observación de al menos 3 caracteres.", "error");
    syncCareQrAdministrationCompletion();
    return;
  }
  const expectedVersion = Number(item.revision);
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 1) {
    setCareQrAdministrationStatus("La aplicación no tiene una versión válida. Vuelva a escanear el QR.", "error");
    return;
  }
  careQrFinalizeOperationId ||= makeId("qr-finalize");
  careQrFinalizeBusy = true;
  syncCareQrAdministrationCompletion();
  setCareQrAdministrationStatus("Registrando la finalización y la evolución clínica...", "loading");
  try {
    const payload = await careScheduleJson(`/api/clinical/infusions/${encodeURIComponent(item.id)}/finalize`, {
      method: "POST",
      body: JSON.stringify({
        expectedVersion,
        observation,
        operationId: careQrFinalizeOperationId,
        confirmed: true
      })
    });
    if (payload.ok === false || !payload.infusion?.id) {
      throw new Error(payload.error || "El servidor no confirmó la finalización.");
    }
    const completed = { ...item, ...payload.infusion };
    mergeCareQrInfusion(completed);
    if (careScheduleDetailQrScan) careScheduleDetailQrScan = { ...careScheduleDetailQrScan, infusion: completed };
    if (getActiveLiraPatientId() === String(completed.patientId || "")) {
      try {
        await loadState({ forceServer: true });
        syncClinicalActorToState();
        renderAll();
      } catch {
        // La finalización ya quedó confirmada; la historia podrá refrescarse manualmente.
      }
    }
    renderCareSchedule();
    renderCareScheduleDetail();
    toast(payload.idempotent ? "La aplicación ya estaba finalizada" : "Aplicación finalizada y evolución registrada");
  } catch (error) {
    setCareQrAdministrationStatus(error.message || "No se pudo finalizar la aplicación.", "error");
  } finally {
    careQrFinalizeBusy = false;
    syncCareQrAdministrationCompletion();
  }
}

function renderCareScheduleDetail() {
  const output = $("#careScheduleDetailBody");
  if (!output) return;
  const item = careScheduleInfusions.find((infusion) => String(infusion.id) === careScheduleDetailInfusionId);
  if (!item) {
    output.innerHTML = `<div class="care-empty-state"><strong>El turno ya no está disponible</strong><span>Actualice la agenda para continuar.</span></div>`;
    return;
  }
  const scheduled = item.scheduledAt ? new Date(item.scheduledAt) : null;
  const dateLabel = scheduled && !Number.isNaN(scheduled.getTime())
    ? new Intl.DateTimeFormat("es-AR", { dateStyle: "full" }).format(scheduled)
    : "Sin fecha";
  const timeLabel = scheduled && !Number.isNaN(scheduled.getTime())
    ? new Intl.DateTimeFormat("es-AR", { hour: "2-digit", minute: "2-digit", hour12: false }).format(scheduled)
    : "Sin hora";
  const medicationReceived = careScheduleMedicationReceived(item);
  const appointmentConfirmed = careScheduleAppointmentConfirmed(item);
  const prescriptionConfirmed = item.prescriptionConfirmed !== false && Boolean(item.treatmentId);
  const patientDni = careSchedulePatientDni(item);
  const insurance = String(item.insurance || "").trim();
  const affiliateNumber = String(item.affiliateNumber || "").trim();
  const title = $("#careScheduleDetailTitle");
  const subtitle = $("#careScheduleDetailSubtitle");
  const kicker = $("#careScheduleDetailKicker");
  const fromQr = careScheduleDetailSource === "qr";
  if (kicker) kicker.textContent = fromQr ? "Ficha de administración · QR" : "Detalle del turno";
  if (title) title.textContent = item.patientName || "Detalle del turno";
  if (subtitle) subtitle.textContent = `${dateLabel} · ${timeLabel} · Sillón ${careScheduleChair(item.chair) || "—"}`;
  output.innerHTML = `
    <section class="care-schedule-detail-patient">
      <div><span>Paciente</span><strong>${escapeHtml(item.patientName || "Paciente sin nombre")}</strong><small>${patientDni ? `DNI ${escapeHtml(patientDni)}` : "DNI no informado"}</small></div>
      <div><span>Obra social</span><strong>${escapeHtml(insurance || "No informada")}</strong><small>${affiliateNumber ? `N.º ${escapeHtml(affiliateNumber)}` : "Número no informado"}</small></div>
    </section>
    <dl class="care-schedule-detail-grid">
      <div><dt>Diagnóstico</dt><dd>${escapeHtml(item.diagnosis || "No informado")}</dd></div>
      <div><dt>Esquema</dt><dd>${escapeHtml(careScheduleInfusionScheme(item))}</dd></div>
      <div><dt>Turno</dt><dd>${escapeHtml(dateLabel)} · ${escapeHtml(timeLabel)} · Sillón ${escapeHtml(careScheduleChair(item.chair) || "—")}</dd></div>
      <div><dt>Ciclo y duración</dt><dd>Ciclo ${escapeHtml(Number(item.cycleNumber) || 1)} · ${escapeHtml(careScheduleDurationLabel(careScheduleItemDuration(item)))}</dd></div>
    </dl>
    <section class="care-schedule-detail-confirmations" aria-label="Confirmaciones del turno">
      ${careScheduleDetailStatus("Prescripción", prescriptionConfirmed, "Confirmada", "No confirmada")}
      <button type="button" data-care-schedule-detail-flag="medicationReceived" class="${medicationReceived ? "is-confirmed" : "is-pending"}" aria-pressed="${medicationReceived}">
        <i data-lucide="${medicationReceived ? "package-check" : "package"}"></i><span>Recepción de medicación</span><strong>${medicationReceived ? "Recibida" : "No confirmada"}</strong><small>${medicationReceived ? "Haga clic para desmarcar" : "Haga clic para confirmar"}</small>
      </button>
      <button type="button" data-care-schedule-detail-flag="appointmentConfirmed" class="${appointmentConfirmed ? "is-confirmed" : "is-pending"}" aria-pressed="${appointmentConfirmed}">
        <i data-lucide="${appointmentConfirmed ? "calendar-check-2" : "calendar-clock"}"></i><span>Confirmación del turno</span><strong>${appointmentConfirmed ? "Confirmado" : "No confirmado"}</strong><small>${appointmentConfirmed ? "Haga clic para desmarcar" : "Haga clic para confirmar"}</small>
      </button>
    </section>
    ${fromQr ? renderCareQrAdministrationCompletion(item) : ""}`;
  refreshIcons();
  if (fromQr) syncCareQrAdministrationCompletion();
}

async function updateCareScheduleDetailFlag(event) {
  const button = event.target.closest("[data-care-schedule-detail-flag]");
  if (!button || careScheduleBusy) return;
  if (!clinicalHasPermission("section.day-hospital.edit")) {
    toast("Su rol permite consultar el turno, pero no modificarlo.");
    return;
  }
  const flag = button.dataset.careScheduleDetailFlag;
  if (!["medicationReceived", "appointmentConfirmed"].includes(flag)) return;
  const item = careScheduleInfusions.find((infusion) => String(infusion.id) === careScheduleDetailInfusionId);
  if (!item) return;
  const nextValue = flag === "medicationReceived"
    ? !careScheduleMedicationReceived(item)
    : !careScheduleAppointmentConfirmed(item);
  const scheduler = {
    ...(item.sourceRef?.scheduler || {}),
    [flag]: nextValue,
    [`${flag}At`]: nextValue ? new Date().toISOString() : null,
  };
  const sourceRef = { ...(item.sourceRef || {}), scheduler };
  careScheduleBusy = true;
  button.disabled = true;
  try {
    const payload = await careScheduleJson(`/api/clinical/infusions/${item.id}`, {
      method: "PATCH",
      body: JSON.stringify({
        expectedVersion: item.revision,
        sourceRef,
        reason: flag === "medicationReceived"
          ? "Actualización de recepción de medicación desde el turnero"
          : "Actualización de confirmación del turno",
      }),
    });
    careScheduleInfusions = careScheduleInfusions.map((infusion) => String(infusion.id) === String(item.id)
      ? { ...infusion, ...(payload.infusion || {}), sourceRef, [flag]: nextValue }
      : infusion);
    renderCareSchedule();
    toast(flag === "medicationReceived"
      ? nextValue ? "Recepción de medicación confirmada" : "Recepción de medicación desmarcada"
      : nextValue ? "Turno confirmado" : "Confirmación de turno desmarcada");
  } catch (error) {
    toast(error.message);
    if (error.code === "VERSION_CONFLICT") await loadCareSchedule();
    else renderCareScheduleDetail();
  } finally {
    careScheduleBusy = false;
  }
}

function clinicalPayloadItems(payload, keys = []) {
  if (Array.isArray(payload)) return payload;
  for (const key of ["items", "records", "data", ...keys]) {
    if (Array.isArray(payload?.[key])) return payload[key];
  }
  return [];
}

function careTreatmentId(item) {
  return String(item?.id ?? item?.treatmentId ?? item?.sourceRecordId ?? "").trim();
}

function careField(item, ...names) {
  for (const name of names) {
    const value = item?.[name];
    if (value !== undefined && value !== null && String(value).trim() !== "") return String(value).trim();
  }
  return "";
}

function setCareRuntimeStatus(tone, label, detail = "") {
  const output = $("#careServiceStatus");
  if (output) {
    output.classList.remove("is-checking", "is-ready", "is-offline");
    output.classList.add(`is-${tone}`);
    const text = $("span", output);
    if (text) text.textContent = label;
    output.title = detail || label;
  }
}

function renderCareTreatmentManagerPatientSummary() {
  const output = $("#careTreatmentManagerPatientSummary");
  if (!output) return;
  const imported = careTreatmentManagerState.exactDetail?.patient || {};
  const patient = state?.patient || {};
  const oncology = state?.oncology || {};
  const fallbackInsurance = [patient.insurance, patient.affiliateNumber].filter(Boolean).join(" ");
  const fields = [
    ["admission", "Admision", imported.admission || formatDate(oncology.diagnosisDate)],
    ["dni", "DNI", imported.dni || patient.dni],
    ["birth", "Fecha Nac.", imported.birthDate || formatDate(patient.birthDate)],
    ["weight", "Peso / SC", imported.weightSurface || [state?.exam?.weightKg && `${state.exam.weightKg} kg`, $("#bsaOutput")?.value && `${$("#bsaOutput").value} sc`].filter(Boolean).join(" / ")],
    ["sex", "Sexo", imported.sex || patient.sex],
    ["insurance", "Obra Social", imported.insurance || fallbackInsurance],
    ["diagnosis", "Diagnostico", imported.diagnosis || oncology.diagnosis],
    ["tnm", "TNM", imported.tnm || [oncology.tnm?.t, oncology.tnm?.n, oncology.tnm?.m].filter(Boolean).join(" ")],
    ["stage", "Estadio", imported.stage || oncology.tnm?.stage || oncology.stage],
    ["biomarkers", "IHQ", imported.biomarkers || oncology.biomarkers],
    ["diagnosis-summary", "Resumen Diagnostico", imported.diagnosisSummary || String(state?.narrative?.summary || "").split("\n")[0]]
  ].filter(([, , value]) => String(value || "").trim());
  output.innerHTML = fields.map(([key, label, value]) => `<div data-care-patient-field="${key}"><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`).join("");
}

function renderCareHospitalPatientContext() {
  const patientId = getActiveLiraPatientId();
  const patient = state?.patient || {};
  const name = patientId
    ? String(patient.fullName || "Paciente sin nombre").trim() || "Paciente sin nombre"
    : "Sin paciente activo";
  $("#careHospitalPatientContext")?.classList.toggle("is-active", Boolean(patientId));
  if ($("#careHospitalPatientName")) $("#careHospitalPatientName").textContent = name;
  if ($("#careHospitalPatientMeta")) {
    $("#careHospitalPatientMeta").textContent = patientId
      ? [
        patient.dni ? `DNI ${patient.dni}` : "",
        `HC ${patient.medicalRecord || "s/d"}`,
        `ID ${patientId}`
      ].filter(Boolean).join(" · ")
      : "Sillones y Farmacia disponibles sin paciente";
  }
  if ($("#careHospitalTreatmentsEmpty")) $("#careHospitalTreatmentsEmpty").hidden = Boolean(patientId);
  if ($("#careHospitalTreatmentsContent")) $("#careHospitalTreatmentsContent").hidden = !patientId;
  if ($("#careHospitalNewTreatmentTitle")) {
    $("#careHospitalNewTreatmentTitle").textContent = patientId
      ? `Nuevo tratamiento para ${name}`
      : "Seleccione un paciente";
  }
  if ($("#careHospitalNewTreatmentDescription")) {
    $("#careHospitalNewTreatmentDescription").textContent = patientId
      ? "Complete el protocolo, los ciclos, la fecha inicial y sus requisitos previos."
      : "Abra una historia clinica para prescribir un protocolo.";
  }
  if ($("#careHospitalOpenNewTreatmentBtn")) {
    const button = $("#careHospitalOpenNewTreatmentBtn");
    button.hidden = !patientId || !clinicalHasPermission("section.prescriptions.edit");
    button.disabled = Boolean(patientId) && (careBusy || !careTreatmentOptions);
    const label = $("span", button);
    if (label) label.textContent = button.disabled ? "Cargando datos..." : "Crear tratamiento";
  }
  if ($("#careHospitalSelectPatientBtn")) $("#careHospitalSelectPatientBtn").hidden = Boolean(patientId);
}

function renderCareWorkspace() {
  const patientId = getActiveLiraPatientId();
  const name = String(state?.patient?.fullName || "Paciente sin seleccionar").trim() || "Paciente sin seleccionar";
  const nameOutput = $("#carePatientName");
  const metaOutput = $("#carePatientMeta");
  if (nameOutput) nameOutput.textContent = name;
  if (metaOutput) {
    metaOutput.textContent = patientId
      ? [`HC ${state?.patient?.medicalRecord || "s/d"}`, `ID ${patientId}`].join(" · ")
      : "Abra un paciente de la base clínica para operar tratamientos e infusiones";
  }
  if ($("#careTreatmentManagerPatientName")) $("#careTreatmentManagerPatientName").textContent = name;
  if ($("#careTreatmentManagerPatientMeta")) {
    $("#careTreatmentManagerPatientMeta").textContent = patientId
      ? [`HC ${state?.patient?.medicalRecord || "s/d"}`, `ID ${patientId}`].join(" · ")
      : "Abra una historia clinica para comenzar";
  }
  renderCareTreatmentManagerPatientSummary();
  renderCareHospitalPatientContext();
  [$("#openCareTreatmentModalBtn"), $("#careHierarchyNewTreatmentBtn")].filter(Boolean)
    .forEach((button) => {
      button.disabled = !patientId || careBusy || !clinicalHasPermission("section.prescriptions.edit");
    });
  if ($("#openCareInfusionModalBtn")) {
    $("#openCareInfusionModalBtn").disabled =
      !patientId || careBusy || !clinicalHasPermission("section.day-hospital.edit");
  }
  setCareView(careView, { refresh: false });
  if (!patientId) {
    careTreatments = [];
    careInfusions = [];
    careSelectedInfusionId = "";
    careTreatmentManagerState.selectedId = "";
    careTreatmentCollections = { oncological: [], nonOncological: [], procedures: [], referrals: [] };
    renderCareTreatments();
    renderCareInfusions();
  }
  renderCareTreatmentManagerActions();
  renderCareInfusionActions();
}

function careTreatmentOptionDiagnoses(payload) {
  const options = payload?.options || payload || {};
  const diagnoses = options.diagnoses || options.diagnosticos;
  return Array.isArray(diagnoses) ? diagnoses : [];
}

function careTreatmentOptionProjectionKey(item) {
  const entryId = String(
    item?.hcopDiagnosisEntryId || item?.diagnosisEntryId ||
    item?.hcopProjection?.entryId || ""
  ).trim();
  if (entryId) return `entry:${entryId}`;
  const markerKey = String(
    item?.hcopProjectionKey || item?.hcopProjection?.key || ""
  ).trim();
  return markerKey === LEGACY_DIAGNOSIS_PROJECTION_KEY
    ? `legacy:${LEGACY_DIAGNOSIS_PROJECTION_KEY}`
    : "";
}

function hasCompletePersistedCareDiagnosis() {
  const revision = Number(state.meta?.persistenceRevision);
  return Number.isSafeInteger(revision) && revision > 0 &&
    getDiagnosisRecords().some(diagnosticSnapshotIsComplete);
}

async function ensureCareDiagnosisProjection(patientId, optionsPayload) {
  const activeDiagnoses = careTreatmentOptionDiagnoses(optionsPayload)
    .filter((item) => optionId(item) && !isCareInactive(item));
  if (!hasCompletePersistedCareDiagnosis()) return optionsPayload;
  const projectedKeys = new Set(activeDiagnoses.map(careTreatmentOptionProjectionKey).filter(Boolean));
  const missingRecords = getDiagnosisRecords().filter((record) =>
    diagnosticSnapshotIsComplete(record) &&
    !projectedKeys.has(diagnosisRecordProjectionKey(record))
  );
  if (!missingRecords.length) return optionsPayload;

  const revision = Number(state.meta.persistenceRevision);
  const projectionKey = `${patientId}:${revision}:${missingRecords
    .map(diagnosisRecordProjectionKey)
    .join(",")}`;
  if (careDiagnosisProjectionTerminalKey === projectionKey) return optionsPayload;
  if (careDiagnosisProjectionPromise && careDiagnosisProjectionKey === projectionKey) {
    return careDiagnosisProjectionPromise;
  }

  const projectionPromise = (async () => {
    try {
      let projectedAny = false;
      for (const record of missingRecords) {
        const requestBody = {
          expectedRevision: revision,
          reason: "Vinculacion automatica de un diagnostico historico guardado"
        };
        if (!record.legacyProjection) requestBody.diagnosisEntryId = record.id;
        const response = await fetch(
          `/api/clinical/patients/${encodeURIComponent(patientId)}/diagnosis`,
          {
          method: "PUT",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
            body: JSON.stringify(requestBody)
          }
        );
        const payload = await response.json().catch(() => ({}));
        if (!response.ok || payload.ok === false || !payload.diagnosis?.id) {
          if ([404, 409, 422].includes(response.status)) {
            careDiagnosisProjectionTerminalKey = projectionKey;
          } else {
            console.warn("No se pudo vincular automaticamente el diagnostico historico.", payload.error || response.status);
          }
          if (!projectedAny) return optionsPayload;
          break;
        }
        projectedAny = true;
      }

      const refreshedResponse = await fetch(
        `/api/clinical/patients/${encodeURIComponent(patientId)}/treatment-options`,
        { cache: "no-store" }
      );
      const refreshedPayload = await refreshedResponse.json().catch(() => ({}));
      if (!refreshedResponse.ok || refreshedPayload.ok === false) {
        console.warn("El diagnostico se vinculo, pero no se pudieron actualizar las opciones de tratamiento.");
        return optionsPayload;
      }
      if (String(refreshedPayload.patientId || "") !== String(patientId)) {
        console.warn("Se descartaron opciones de tratamiento de otro paciente.");
        return optionsPayload;
      }
      if (careDiagnosisProjectionTerminalKey === projectionKey) {
        careDiagnosisProjectionTerminalKey = "";
      }
      return refreshedPayload;
    } catch (error) {
      console.warn("No se pudo vincular automaticamente el diagnostico historico.", error);
      return optionsPayload;
    }
  })();

  careDiagnosisProjectionPromise = projectionPromise;
  careDiagnosisProjectionKey = projectionKey;
  try {
    return await projectionPromise;
  } finally {
    if (careDiagnosisProjectionPromise === projectionPromise) {
      careDiagnosisProjectionPromise = null;
      careDiagnosisProjectionKey = "";
    }
  }
}

async function refreshCareWorkspace({ force = false } = {}) {
  const patientId = getActiveLiraPatientId();
  const requestVersion = ++careRequestVersion;
  const infusionRequestVersion = ++careInfusionRequestVersion;
  careBusy = true;
  renderCareWorkspace();
  setCareRuntimeStatus("checking", "Actualizando", "Consultando la base clínica local");
  const refresh = $("#refreshCareBtn");
  if (refresh) refresh.disabled = true;
  try {
    const statusResponse = await fetch("/api/clinical/status", { cache: "no-store" });
    const status = await statusResponse.json().catch(() => ({}));
    if (!statusResponse.ok || status.ok === false) throw new Error(status.error || "La base clínica no está disponible");
    if (!patientId) {
      setCareRuntimeStatus("ready", "Base clínica activa", "Seleccione un paciente para abrir su centro oncológico");
      return;
    }
    const [treatmentsResponse, optionsResponse, infusionsResponse] = await Promise.all([
      fetch(`/api/clinical/patients/${encodeURIComponent(patientId)}/treatments`, { cache: "no-store" }),
      fetch(`/api/clinical/patients/${encodeURIComponent(patientId)}/treatment-options`, { cache: "no-store" }),
      fetch(`/api/clinical/infusions?patientId=${encodeURIComponent(patientId)}`, { cache: "no-store" }).catch((error) => ({
        ok: false,
        json: async () => ({ error: error.message || "No se pudo cargar Hospital de Dia" })
      }))
    ]);
    const [treatmentsPayload, initialOptionsPayload, infusionsPayload] = await Promise.all([
      treatmentsResponse.json().catch(() => ({})),
      optionsResponse.json().catch(() => ({})),
      infusionsResponse.json().catch(() => ({}))
    ]);
    const canApplyInfusions = infusionRequestVersion === careInfusionRequestVersion;
    if (!treatmentsResponse.ok || !optionsResponse.ok) {
      const error = new Error(treatmentsPayload.error || initialOptionsPayload.error || "No se pudieron cargar los tratamientos");
      error.careTarget = "treatments";
      throw error;
    }
    if (String(initialOptionsPayload.patientId || "") !== String(patientId)) {
      const error = new Error("Las opciones de tratamiento no corresponden al paciente activo");
      error.careTarget = "treatments";
      throw error;
    }
    if (!infusionsResponse.ok && canApplyInfusions) {
      const error = new Error(infusionsPayload.error || "No se pudo cargar Hospital de Dia");
      error.careTarget = "infusion";
      throw error;
    }
    if (requestVersion !== careRequestVersion) return;
    const optionsPayload = await ensureCareDiagnosisProjection(patientId, initialOptionsPayload);
    if (requestVersion !== careRequestVersion) return;
    careTreatmentCollections = {
      oncological: Array.isArray(treatmentsPayload.oncology)
        ? treatmentsPayload.oncology
        : clinicalPayloadItems(treatmentsPayload, ["oncology", "treatments"]),
      nonOncological: Array.isArray(treatmentsPayload.nonOncology) ? treatmentsPayload.nonOncology : [],
      procedures: Array.isArray(treatmentsPayload.procedures) ? treatmentsPayload.procedures : [],
      referrals: Array.isArray(treatmentsPayload.referrals) ? treatmentsPayload.referrals : []
    };
    careTreatments = careTreatmentCollections.oncological;
    await reconcileTreatmentEvolutionEntries(careTreatments);
    if (requestVersion !== careRequestVersion) return;
    careTreatmentOptions = optionsPayload;
    if (canApplyInfusions) careInfusions = clinicalPayloadItems(infusionsPayload, ["infusions", "sessions"]);
    renderCareTreatments();
    if (canApplyInfusions) renderCareInfusions();
    populateCareForms();
    setCareRuntimeStatus("ready", "Datos sincronizados", `${careTreatments.length} tratamientos · ${careInfusions.length} sesiones de infusión`);
    if (force) toast("Centro oncológico actualizado");
  } catch (error) {
    if (requestVersion !== careRequestVersion) return;
    setCareRuntimeStatus("offline", "Base clínica no disponible", error.message || "No se pudo actualizar");
    renderCareError(error.message || "No se pudo acceder a los datos clínicos.", error.careTarget || careView);
  } finally {
    if (requestVersion === careRequestVersion) {
      careBusy = false;
      if (refresh) refresh.disabled = false;
      renderCareWorkspace();
      refreshIcons();
    }
  }
}

function renderCareError(message, target = careView) {
  if (target === "treatments") {
    careTreatmentManagerState.selectedId = "";
    $$('[data-care-treatment-surface]').forEach((surface) => {
      const table = $('[data-care-treatment-role="table"]', surface);
      if (table?.tBodies?.[0]) table.tBodies[0].innerHTML = `<tr class="is-empty"><td colspan="8">No se pudieron cargar los registros.</td></tr>`;
      const error = $('[data-care-treatment-role="error"]', surface);
      if (error) {
        error.textContent = message;
        error.hidden = false;
      }
    });
    renderCareTreatmentManagerActions();
  } else if (target === "infusion") {
    careInfusions = [];
    const output = $("#careInfusionList");
    if (output) output.innerHTML = `<table class="care-treatment-manager-table care-infusion-table" id="careInfusionTable"><tbody><tr class="is-empty"><td>No se pudo abrir Hospital de Dia: ${escapeHtml(message)}</td></tr></tbody></table>`;
    if ($("#careInfusionTableStatus")) $("#careInfusionTableStatus").textContent = "No se pudo actualizar la agenda";
    if ($("#careInfusionPagination")) $("#careInfusionPagination").innerHTML = "";
    careSelectedInfusionId = "";
    renderCareInfusionActions();
    renderCareTreatmentHierarchy();
  } else {
    const output = $("#careDrugResults");
    if (output) output.innerHTML = `<div class="care-empty-state is-error"><i data-lucide="triangle-alert"></i><strong>No se pudo abrir este módulo</strong><span>${escapeHtml(message)}</span></div>`;
  }
  refreshIcons();
}

function renderCareTreatments() {
  renderCareTreatmentManager();
  renderCareTreatmentHierarchy();
}

const CARE_TREATMENT_MANAGER_VIEWS = {
  oncological: {
    collection: "oncological",
    columns: [
      { label: "Fecha Creacion", fields: ["fechaCreacion", "date", "createdAt"] },
      { label: "Tipo", fields: ["tipo", "type"] },
      { label: "Diagnostico", fields: ["diagnostico", "diagnosis"] },
      { label: "Esquema", fields: ["esquema", "scheme"] },
      { label: "Oncologo", fields: ["oncologo", "oncologist", "professional"] },
      { label: "Estado", fields: ["estadoTratamiento", "status"] },
      { label: "Cant. de Ciclos", fields: ["cantidadCiclos", "cycles", "cycleCount"] },
      { label: "Consentimiento", fields: ["estadoConsentimiento", "consentStatus"] }
    ],
    actions: [
      { name: "new", label: "Nuevo tratamiento", icon: "plus", primary: true },
      { name: "view", label: "Ver detalle", icon: "eye", selection: true },
      { name: "schedule", label: "Turnos en Hospital de dia", icon: "calendar-plus", selection: true }
    ]
  },
  "non-oncological": {
    collection: "nonOncological",
    columns: [
      { label: "Tipo de tratamiento", fields: ["tipoTratamiento", "type"] },
      { label: "Indicaciones", fields: ["indicaciones", "observations"] },
      { label: "Fecha de indicacion", fields: ["fechaIndicacion", "date"] }
    ],
    actions: [
      { name: "new-workflow", label: "Nuevo", icon: "plus", primary: true },
      { name: "edit-workflow", label: "Editar", icon: "pencil", selection: true },
      { name: "archive-workflow", label: "Archivar", icon: "archive", selection: true, danger: true },
      { name: "view", label: "Ver detalle", icon: "eye", selection: true }
    ]
  },
  procedures: {
    collection: "procedures",
    columns: [
      { label: "Tipo de practica", fields: ["tipoPractica", "type"] },
      { label: "Fecha de realizacion", fields: ["fechaRealizacion", "date"] },
      { label: "Resultado", fields: ["resultado", "result", "observations"] },
      { label: "Archivo", fields: ["nombreArchivoOriginal", "fileName"] }
    ],
    actions: [
      { name: "new-workflow", label: "Nueva", icon: "plus", primary: true },
      { name: "edit-workflow", label: "Editar", icon: "pencil", selection: true },
      { name: "archive-workflow", label: "Archivar", icon: "archive", selection: true, danger: true },
      { name: "view", label: "Ver detalle", icon: "eye", selection: true }
    ]
  },
  referrals: {
    collection: "referrals",
    columns: [
      { label: "Tipo de derivacion", fields: ["tipoDerivacion", "type"] },
      { label: "Diagnostico", fields: ["diagnostico", "diagnosis"] },
      { label: "Observaciones", fields: ["observaciones", "observations"] },
      { label: "Fecha de indicacion", fields: ["fechaIndicacion", "date"] }
    ],
    actions: [
      { name: "new-workflow", label: "Nueva", icon: "plus", primary: true },
      { name: "edit-workflow", label: "Editar", icon: "pencil", selection: true },
      { name: "archive-workflow", label: "Archivar", icon: "archive", selection: true, danger: true },
      { name: "view", label: "Ver detalle", icon: "eye", selection: true }
    ]
  }
};

const CARE_TREATMENT_INLINE_COLUMN_LABELS = Object.freeze({
  "Fecha Creacion": "Fecha",
  "Cant. de Ciclos": "Ciclos"
});

function getCareTreatmentManagerView() {
  return CARE_TREATMENT_MANAGER_VIEWS[careTreatmentManagerState.tab] || CARE_TREATMENT_MANAGER_VIEWS.oncological;
}

function getCareTreatmentManagerRecords() {
  const view = getCareTreatmentManagerView();
  return Array.isArray(careTreatmentCollections[view.collection]) ? careTreatmentCollections[view.collection] : [];
}

function careTreatmentManagerRecordId(item) {
  return careTreatmentId(item) || String(item?.recordId ?? item?.sourceRecordId ?? "").trim();
}

function careTreatmentManagerCell(item, fields) {
  return careField(item, ...(fields || [])) || "—";
}

function careDateSortValue(value) {
  const text = String(value || "").trim();
  const localMatch = text.match(/^(\d{1,2})[/-](\d{1,2})[/-](\d{4})(?:\D+(\d{1,2}):(\d{2}))?/);
  if (localMatch) {
    const [, day, month, year, hour = "0", minute = "0"] = localMatch;
    return Date.UTC(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute));
  }
  const parsed = Date.parse(text);
  return Number.isNaN(parsed) ? null : parsed;
}

function careTreatmentManagerSortValue(record, column) {
  const value = careTreatmentManagerCell(record, column.fields);
  const isDateColumn = (column.fields || []).some((field) => /fecha|date|created/i.test(field));
  if (!isDateColumn) return value;
  return careDateSortValue(value) ?? value;
}

function setCareTreatmentManagerTab(tab) {
  const activeTab = tab === "oncological" ? tab : "oncological";
  careTreatmentDetailRequestVersion += 1;
  careTreatmentManagerState = {
    ...careTreatmentManagerState,
    tab: activeTab,
    query: "",
    sortColumn: 0,
    sortDirection: "desc",
    selectedId: "",
    mode: "list",
    detailPane: "drugs",
    detailCycle: 0,
    exactDetail: null,
    observationApplicationId: ""
  };
  $$('[data-care-treatment-role="search"]').forEach((search) => { search.value = ""; });
  $("#careTreatmentManagerModal")?.classList.remove("is-detail");
  if ($("#careTreatmentManagerDetail")) $("#careTreatmentManagerDetail").hidden = true;
  renderCareTreatmentManager();
}

async function openCareTreatmentManagerModal({ mode = careScheduleMode } = {}) {
  const modal = $("#careTreatmentManagerModal");
  if (!modal) return;
  const wasOpen = modal.classList.contains("open");
  if (!wasOpen) careHospitalReturnFocus = document.activeElement;
  careTreatmentManagerState.tab = "oncological";
  careTreatmentManagerState.mode = "list";
  careTreatmentManagerState.detailCycle = 0;
  careTreatmentManagerState.exactDetail = null;
  careTreatmentManagerState.observationApplicationId = "";
  modal.classList.remove("is-detail");
  if ($("#careTreatmentManagerDetail")) $("#careTreatmentManagerDetail").hidden = true;
  setCareScheduleDate(selectedCareScheduleDate());
  $("#openCareInfusionManagerBtn")?.setAttribute("aria-expanded", "true");
  renderCareWorkspace();
  if (!wasOpen) showCareModal("careTreatmentManagerModal");
  setCareHospitalTab(mode);
  window.requestAnimationFrame(() => $(`[data-care-hospital-tab="${careScheduleMode}"]`)?.focus());
  if (careScheduleMode === "chairs" || careScheduleMode === "pharmacy") await loadCareSchedule();
  else if (careScheduleMode === "treatments" && getActiveLiraPatientId() && !careTreatments.length && !careBusy) await refreshCareWorkspace();
}

function closeCareTreatmentManagerModal({ restoreFocus = true } = {}) {
  const modal = $("#careTreatmentManagerModal");
  if (!modal?.classList.contains("open")) return;
  careScheduleRequestVersion += 1;
  careScheduleCandidateRequestVersion += 1;
  careTreatmentDetailRequestVersion += 1;
  careScheduleDrag = null;
  hideCareScheduleHoverCard();
  clearCareScheduleDragTarget(true);
  clearCareScheduleDraggingVisuals();
  closeCareScheduleDetailModal({ restoreFocus: false });
  closeCareModal("careTreatmentManagerModal");
  $("#openCareInfusionManagerBtn")?.setAttribute("aria-expanded", "false");
  careTreatmentManagerState.mode = "list";
  careTreatmentManagerState.exactDetail = null;
  careTreatmentManagerState.observationApplicationId = "";
  modal.classList.remove("is-detail");
  if ($("#careTreatmentManagerDetail")) $("#careTreatmentManagerDetail").hidden = true;
  const sortedColumn = getCareTreatmentManagerView().columns[careTreatmentManagerState.sortColumn];
  if (careTreatmentManagerState.tab === "oncological" && sortedColumn?.label === "Consentimiento") {
    careTreatmentManagerState.sortColumn = 0;
    careTreatmentManagerState.sortDirection = "desc";
    careTreatmentManagerState.selectedId = "";
    renderCareTreatmentManager();
  }
  const returnFocus = careHospitalReturnFocus || $("#openCareInfusionManagerBtn");
  careHospitalReturnFocus = null;
  if (restoreFocus) window.requestAnimationFrame(() => returnFocus?.focus?.());
}

function trapCareModalFocus(event, modalId) {
  const modal = $(`#${modalId}`);
  if (!modal) return;
  const focusable = $$(
    'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
    modal
  ).filter((element) => !element.hidden && element.getAttribute("aria-hidden") !== "true" && !element.closest("[hidden]"));
  if (!focusable.length) {
    event.preventDefault();
    $("[role=dialog]", modal)?.focus();
    return;
  }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (!modal.contains(document.activeElement)) {
    event.preventDefault();
    first.focus();
  } else if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

function renderCareTreatmentManagerActions() {
  const selected = Boolean(careTreatmentManagerState.selectedId);
  const disabledByContext = !getActiveLiraPatientId() || careBusy || careTreatmentArchiveBusy;
  $$('[data-care-treatment-role="actions"]').forEach((output) => {
    const actions = getCareTreatmentManagerView().actions.filter((action) => action.name !== "view");
    output.innerHTML = actions.map((action) => {
      const permission = action.name === "new" ? "section.prescriptions.edit"
        : action.name === "schedule" ? "section.day-hospital.edit" : "section.day-hospital.edit";
      const allowed = clinicalHasPermission(permission);
      return `
      <button class="tool-button${action.primary ? " primary" : ""}${action.danger ? " danger" : ""}" type="button" data-care-manager-action="${escapeAttr(action.name)}" data-permission="${escapeAttr(permission)}"${!allowed || disabledByContext || (action.selection && !selected) ? " disabled" : ""}>
        <i data-lucide="${escapeAttr(action.icon)}"></i><span>${escapeHtml(action.label)}</span>
      </button>`;
    }).join("");
  });
}

function renderCareTreatmentConsentControl(record, className = "") {
  const treatmentId = careTreatmentManagerRecordId(record);
  const status = careField(record, "estadoConsentimiento", "consentStatus") || "No disponible";
  const explicitAvailability = careField(record, "consentAvailable", "hasConsent", "consentDocumentAvailable");
  const normalizedStatus = normalizeSearchText(status);
  const unavailableStatus = /(?:^|\s)(?:no|sin|pendiente|rechazad|ausente|faltante|retirado)(?:\s|$)/.test(normalizedStatus);
  const availableByStatus = /firmad|signed|aprobado|disponible|completado/.test(normalizedStatus) && !unavailableStatus;
  const available = explicitAvailability
    ? /^(?:1|true|si|sí|yes)$/i.test(explicitAvailability)
    : availableByStatus;
  const classes = `care-consent-document ${available ? "is-available" : "is-missing"}${className ? ` ${className}` : ""}`;
  const legend = available
    ? `Consentimiento: ${status}. Abrir documento`
    : `Consentimiento: ${status}. Documento no disponible`;
  const icon = `<i data-lucide="file-text"></i>`;
  return available && treatmentId
    ? `<a class="${classes}" href="/api/clinical/treatments/${escapeAttr(treatmentId)}/consent" target="_blank" rel="noopener" data-care-manager-consent-link title="${escapeAttr(legend)}" aria-label="${escapeAttr(legend)}">${icon}</a>`
    : `<span class="${classes}" role="img" tabindex="0" title="${escapeAttr(legend)}" aria-label="${escapeAttr(legend)}">${icon}</span>`;
}

function careTreatmentDuration(record) {
  const direct = careField(record, "estimatedDurationText", "durationText");
  if (direct) return direct;
  const minutes = Number(careField(record, "estimatedDurationMinutes", "durationMinutes"));
  if (!Number.isInteger(minutes) || minutes < 1) return "Sin estimar";
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return [hours ? `${hours} h` : "", rest ? `${rest} min` : ""].filter(Boolean).join(" ");
}

function careTreatmentDurationTitle(record) {
  const notes = String(record?.durationEstimate?.notes || "").trim();
  return notes || "Duración operativa estimada para planificación; no reemplaza la indicación de administración.";
}

function renderCareTreatmentManagerTableCell(record, column) {
  const value = careTreatmentManagerCell(record, column.fields);
  if (column.label !== "Esquema") return `<td>${escapeHtml(value)}</td>`;
  return `<td class="care-treatment-scheme-cell"><span>${escapeHtml(value)}</span><small title="${escapeAttr(careTreatmentDurationTitle(record))}"><i data-lucide="clock-3"></i>Duración estimada: ${escapeHtml(careTreatmentDuration(record))}</small></td>`;
}

function renderCareTreatmentManager() {
  const surfaces = $$('[data-care-treatment-surface]');
  if (!surfaces.length) return;
  const view = getCareTreatmentManagerView();
  const records = getCareTreatmentManagerRecords();
  const term = String(careTreatmentManagerState.query || "").trim().toLocaleLowerCase("es");
  const filtered = term
    ? records.filter((record) => view.columns.some((column) =>
      careTreatmentManagerCell(record, column.fields).toLocaleLowerCase("es").includes(term)))
    : records;
  const sortIndex = Math.max(0, Math.min(Number(careTreatmentManagerState.sortColumn) || 0, view.columns.length - 1));
  careTreatmentManagerState.sortColumn = sortIndex;
  const sortColumn = view.columns[sortIndex];
  const sorted = [...filtered].sort((left, right) => {
    const leftValue = careTreatmentManagerSortValue(left, sortColumn);
    const rightValue = careTreatmentManagerSortValue(right, sortColumn);
    const comparison = typeof leftValue === "number" && typeof rightValue === "number"
      ? leftValue - rightValue
      : String(leftValue).localeCompare(String(rightValue), "es", { numeric: true, sensitivity: "base" });
    return comparison * (careTreatmentManagerState.sortDirection === "desc" ? -1 : 1);
  });
  if (!sorted.some((record) => careTreatmentManagerRecordId(record) === careTreatmentManagerState.selectedId)) {
    careTreatmentManagerState.selectedId = "";
  }

  surfaces.forEach((surface) => {
    const inline = surface.dataset.careTreatmentSurface === "inline";
    const surfaceRecords = sorted;
    const columns = inline && careTreatmentManagerState.tab === "oncological"
      ? view.columns.filter((column) => column.label !== "Consentimiento")
      : view.columns;
    $$('[data-care-treatment-tab]', surface).forEach((button) => {
      const active = button.dataset.careTreatmentTab === careTreatmentManagerState.tab;
      button.classList.toggle("active", active);
      button.setAttribute("aria-selected", String(active));
    });
    const search = $('[data-care-treatment-role="search"]', surface);
    if (search && search.value !== careTreatmentManagerState.query) search.value = careTreatmentManagerState.query;
    const table = $('[data-care-treatment-role="table"]', surface);
    if (table) {
      table.tHead.innerHTML = `<tr>${columns.map((column) => {
        const columnIndex = view.columns.indexOf(column);
        const activeSort = columnIndex === careTreatmentManagerState.sortColumn;
        const direction = careTreatmentManagerState.sortDirection;
        const columnLabel = inline ? CARE_TREATMENT_INLINE_COLUMN_LABELS[column.label] || column.label : column.label;
        return `<th class="is-sortable" scope="col" aria-sort="${activeSort ? direction === "asc" ? "ascending" : "descending" : "none"}"><button type="button" data-care-manager-sort-index="${columnIndex}"><span>${escapeHtml(columnLabel)}</span><i data-lucide="${activeSort ? direction === "asc" ? "arrow-up" : "arrow-down" : "chevrons-up-down"}"></i></button></th>`;
      }).join("")}<th class="care-treatment-view-column" scope="col"><span>Acciones</span></th></tr>`;
      table.tBodies[0].innerHTML = surfaceRecords.length
        ? surfaceRecords.map((record) => {
          const id = careTreatmentManagerRecordId(record);
          const selected = id && id === careTreatmentManagerState.selectedId;
          const consentControl = renderCareTreatmentConsentControl(record);
          const directViewCell = `<td class="care-treatment-view-cell"><div class="care-treatment-row-actions">${consentControl}<button type="button" data-care-manager-view-id="${escapeAttr(id)}" title="Ver detalle" aria-label="Ver detalle del tratamiento"><i data-lucide="eye"></i></button></div></td>`;
          return `<tr tabindex="0" data-care-manager-record-id="${escapeAttr(id)}" class="${selected ? "is-selected" : ""}" aria-selected="${String(selected)}">${columns.map((column) => renderCareTreatmentManagerTableCell(record, column)).join("")}${directViewCell}</tr>`;
        }).join("")
        : `<tr class="is-empty"><td colspan="${columns.length + 1}">No hay registros en esta seccion.</td></tr>`;
    }
    const error = $('[data-care-treatment-role="error"]', surface);
    if (error) error.hidden = true;
  });
  renderCareTreatmentManagerActions();
  refreshIcons();
}

function handleCareTreatmentManagerRowClick(event) {
  const row = event.target.closest("tr[data-care-manager-record-id]");
  if (!row) return;
  const id = String(row.dataset.careManagerRecordId || "");
  careTreatmentManagerState.selectedId = careTreatmentManagerState.selectedId === id ? "" : id;
  renderCareTreatmentManager();
}

function selectedCareTreatmentManagerRecord() {
  return getCareTreatmentManagerRecords().find((record) =>
    careTreatmentManagerRecordId(record) === careTreatmentManagerState.selectedId) || null;
}

function handleCareTreatmentManagerAction(event) {
  const action = event.target.closest("[data-care-manager-action]")?.dataset.careManagerAction;
  if (!action) return;
  if (action === "new") {
    if (!clinicalHasPermission("section.prescriptions.edit")) {
      toast("Su rol no permite prescribir tratamientos.");
      return;
    }
    openCareTreatmentModal();
    return;
  }
  if (action === "new-workflow") {
    openCareTreatmentWorkflowModal(careTreatmentManagerState.tab);
    return;
  }
  const record = selectedCareTreatmentManagerRecord();
  if (!record) return;
  if (action === "view") openCareTreatmentManagerDetailFromSurface(record, "drugs");
  else if (action === "schedule") openCareInfusionModal(careTreatmentManagerRecordId(record));
  else if (action === "edit-workflow") openCareTreatmentWorkflowModal(careTreatmentManagerState.tab, record);
  else if (action === "archive-workflow") archiveCareTreatmentWorkflow(record);
}

function openCareTreatmentManagerDetailFromSurface(record, pane) {
  const modal = $("#careTreatmentManagerModal");
  if (!modal) return;
  if (!modal.classList.contains("open")) {
    void openCareTreatmentManagerModal({ mode: "treatments" });
  } else {
    setCareHospitalTab("treatments");
  }
  openCareTreatmentManagerDetail(record, pane);
  window.requestAnimationFrame(() => $("button", $("#careTreatmentManagerDetail"))?.focus());
}

function careTreatmentWorkflowDefinition(kind) {
  return {
    "non-oncological": {
      singular: "tratamiento no oncologico",
      options: "nonOncologyTypes",
      typeLabel: "Tipo de tratamiento",
      recordType: (record) => careField(record, "tipoTratamiento", "type")
    },
    procedures: {
      singular: "practica realizada",
      options: "procedureTypes",
      typeLabel: "Tipo de practica",
      recordType: (record) => careField(record, "tipoPractica", "type")
    },
    referrals: {
      singular: "derivacion",
      options: "referralTypes",
      typeLabel: "Tipo de derivacion",
      recordType: (record) => careField(record, "tipoDerivacion", "type")
    }
  }[kind] || null;
}

async function ensureCareTreatmentWorkflowOptions() {
  if (careTreatmentWorkflowOptions) return careTreatmentWorkflowOptions;
  const patientId = getActiveLiraPatientId();
  if (!patientId) throw new Error("Seleccione un paciente antes de editar el registro.");
  const response = await fetch(`/api/clinical/patients/${encodeURIComponent(patientId)}/treatment-workflow-options`, { cache: "no-store" });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.error || "No se pudieron cargar las opciones del registro.");
  careTreatmentWorkflowOptions = payload.options || payload;
  return careTreatmentWorkflowOptions;
}

function careTreatmentWorkflowOptionMarkup(items, selectedLabel) {
  const selectedId = optionId((items || []).find((item) => optionLabel(item) === selectedLabel));
  return `<option value="">Seleccione...</option>${(items || []).map((item) => `<option value="${escapeAttr(optionId(item))}"${optionId(item) === selectedId ? " selected" : ""}${isCareInactive(item) && optionId(item) !== selectedId ? " disabled" : ""}>${escapeHtml(optionLabel(item))}</option>`).join("")}`;
}

function careTreatmentWorkflowDateForInput(value) {
  const text = String(value || "").trim();
  const local = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text);
  if (local) return `${local[3]}-${local[2]}-${local[1]}`;
  return /^\d{4}-\d{2}-\d{2}/.test(text) ? text.slice(0, 10) : "";
}

function careTreatmentWorkflowDateForApi(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value || ""));
  return match ? `${match[3]}/${match[2]}/${match[1]}` : String(value || "");
}

function renderCareTreatmentWorkflowFields(kind, record, options) {
  const output = $("#careTreatmentWorkflowFields");
  const definition = careTreatmentWorkflowDefinition(kind);
  if (!output || !definition) return;
  const typeOptions = Array.isArray(options?.[definition.options]) ? options[definition.options] : [];
  const typeField = `<label class="care-form-field" for="careTreatmentWorkflowType"><span>${escapeHtml(definition.typeLabel)}</span><select id="careTreatmentWorkflowType" name="typeId" required>${careTreatmentWorkflowOptionMarkup(typeOptions, definition.recordType(record || {}))}</select></label>`;
  if (kind === "non-oncological") {
    output.innerHTML = `${typeField}<label class="care-form-field" for="careTreatmentWorkflowIndications"><span>Indicaciones</span><textarea id="careTreatmentWorkflowIndications" name="indications" rows="7" maxlength="10000" required>${escapeHtml(careField(record, "indicaciones", "observations"))}</textarea></label>`;
  } else if (kind === "procedures") {
    output.innerHTML = `<div class="care-form-grid care-form-grid--two">${typeField}<label class="care-form-field" for="careTreatmentWorkflowDate"><span>Fecha de realizacion</span><input id="careTreatmentWorkflowDate" name="date" type="date" value="${escapeAttr(careTreatmentWorkflowDateForInput(careField(record, "fechaRealizacion", "date")))}" required></label><label class="care-form-field care-form-field--wide" for="careTreatmentWorkflowResult"><span>Resultado</span><textarea id="careTreatmentWorkflowResult" name="result" rows="7" maxlength="10000">${escapeHtml(careField(record, "resultado", "result", "observations"))}</textarea></label></div>`;
  } else if (kind === "referrals") {
    output.innerHTML = `${typeField}<label class="care-form-field" for="careTreatmentWorkflowDiagnosis"><span>Diagnostico</span><input id="careTreatmentWorkflowDiagnosis" name="diagnosis" value="${escapeAttr(careField(record, "diagnostico", "diagnosis"))}" maxlength="1000" required></label><label class="care-form-field" for="careTreatmentWorkflowObservations"><span>Observaciones</span><textarea id="careTreatmentWorkflowObservations" name="observations" rows="6" maxlength="10000">${escapeHtml(careField(record, "observaciones", "observations"))}</textarea></label>`;
  }
}

async function openCareTreatmentWorkflowModal(kind, record = null) {
  const definition = careTreatmentWorkflowDefinition(kind);
  if (!definition || !getActiveLiraPatientId()) return;
  careTreatmentWorkflowEditor = { kind, recordId: record ? careTreatmentManagerRecordId(record) : "0" };
  const title = $("#careTreatmentWorkflowTitle");
  if (title) title.textContent = `${record ? "Editar" : "Nuevo"} ${definition.singular}`;
  const output = $("#careTreatmentWorkflowFields");
  if (output) output.innerHTML = `<p class="care-inline-loading"><i data-lucide="loader-circle"></i>Cargando opciones...</p>`;
  const error = $("#careTreatmentWorkflowError");
  if (error) error.hidden = true;
  showCareModal("careTreatmentWorkflowModal");
  refreshIcons();
  try {
    const options = await ensureCareTreatmentWorkflowOptions();
    renderCareTreatmentWorkflowFields(kind, record, options);
    refreshIcons();
    $("#careTreatmentWorkflowFields select, #careTreatmentWorkflowFields input, #careTreatmentWorkflowFields textarea")?.focus();
  } catch (reason) {
    if (output) output.innerHTML = "";
    if (error) {
      error.textContent = reason.message || "No se pudo abrir el editor.";
      error.hidden = false;
    }
  }
}

async function submitCareTreatmentWorkflow(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const patientId = getActiveLiraPatientId();
  const { kind, recordId } = careTreatmentWorkflowEditor;
  if (!patientId || !careTreatmentWorkflowDefinition(kind)) return;
  const fields = Object.fromEntries(new FormData(form).entries());
  const body = {
    recordId,
    typeId: fields.typeId,
    indications: fields.indications || "",
    date: careTreatmentWorkflowDateForApi(fields.date),
    result: fields.result || "",
    diagnosis: fields.diagnosis || "",
    observations: fields.observations || ""
  };
  const submit = $("#saveCareTreatmentWorkflowBtn");
  const error = $("#careTreatmentWorkflowError");
  if (submit) submit.disabled = true;
  if (error) error.hidden = true;
  try {
    const response = await fetch(`/api/clinical/patients/${encodeURIComponent(patientId)}/treatment-workflows/${encodeURIComponent(kind)}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || "No se pudo guardar el registro.");
    closeCareModal("careTreatmentWorkflowModal");
    careTreatmentManagerState.selectedId = "";
    await refreshCareWorkspace({ force: true });
    toast("Registro guardado en la base clinica local");
  } catch (reason) {
    if (error) {
      error.textContent = reason.message || "No se pudo guardar el registro.";
      error.hidden = false;
    }
  } finally {
    if (submit) submit.disabled = false;
  }
}

async function archiveCareTreatmentWorkflow(record) {
  const patientId = getActiveLiraPatientId();
  const kind = careTreatmentManagerState.tab;
  const recordId = careTreatmentManagerRecordId(record);
  if (!patientId || !recordId || !careTreatmentWorkflowDefinition(kind) || careTreatmentArchiveBusy) return;
  if (!window.confirm("Esta a punto de archivar el registro seleccionado.")) return;
  careTreatmentArchiveBusy = true;
  renderCareTreatmentManagerActions();
  try {
    const response = await fetch(`/api/clinical/patients/${encodeURIComponent(patientId)}/treatment-workflows/${encodeURIComponent(kind)}/${encodeURIComponent(recordId)}`, { method: "DELETE" });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || "No se pudo archivar el registro.");
    careTreatmentManagerState.selectedId = "";
    await refreshCareWorkspace({ force: true });
    toast("Registro archivado");
  } catch (reason) {
    const message = reason.message || "No se pudo archivar el registro.";
    $$('[data-care-treatment-role="error"]').forEach((error) => {
      error.textContent = message;
      error.hidden = false;
    });
    toast(message);
  } finally {
    careTreatmentArchiveBusy = false;
    renderCareTreatmentManagerActions();
  }
}

function careTreatmentDetailRows(item) {
  const view = getCareTreatmentManagerView();
  return view.columns.map((column) => [column.label, careTreatmentManagerCell(item, column.fields)]);
}

function careInfusionVisualState(value) {
  const normalized = normalizeSearchText(value);
  if (/complet|finaliz|termin/.test(normalized)) return "is-completed";
  if (/cancel|suspend|withheld|paused/.test(normalized)) return "is-cancelled";
  if (/checked_in|ready|in_progress|observation|admit|curso|observ/.test(normalized)) return "is-current";
  return "is-pending";
}

function renderCareTreatmentCycleStrip(item) {
  const count = Math.max(1, Math.min(Number(careField(item, "cantidadCiclos", "cycles", "cycleCount")) || 1, 50));
  const treatmentId = careTreatmentManagerRecordId(item);
  const sessions = careInfusions.filter((session) => careField(session, "treatmentId", "treatment_id") === treatmentId);
  return Array.from({ length: count }, (_, index) => {
    const cycle = index + 1;
    const session = sessions.find((candidate) => Number(careField(candidate, "cycleNumber", "cycle_number")) === cycle);
    const stateValue = careField(session, "clinicalStatus", "clinical_status", "status");
    const stateClass = careInfusionVisualState(stateValue);
    return `<span class="care-treatment-cycle ${stateClass}">Ciclo ${cycle}</span>`;
  }).join("");
}

function renderCareTreatmentApplications(item) {
  const treatmentId = careTreatmentManagerRecordId(item);
  const sessions = careInfusions
    .filter((session) => careField(session, "treatmentId", "treatment_id") === treatmentId)
    .sort((a, b) => String(careField(a, "scheduledAt", "scheduled_at")).localeCompare(String(careField(b, "scheduledAt", "scheduled_at"))));
  if (!sessions.length) return `<div class="care-treatment-detail-empty">No hay aplicaciones programadas para este tratamiento.</div>`;
  return `<ol class="care-treatment-application-timeline">${sessions.map((session) => {
    const status = careField(session, "clinicalStatus", "clinical_status", "status") || "planned";
    const cycle = careField(session, "cycleNumber", "cycle_number") || "—";
    const date = careField(session, "scheduledAt", "scheduled_at");
    const chair = careField(session, "chair", "sillon");
    const statusClass = careInfusionVisualState(status);
    return `<li class="${statusClass}"><span></span><div><strong>Ciclo ${escapeHtml(cycle)}</strong><small>${escapeHtml(formatCareDateTime(date))}${chair ? ` · Sillon ${escapeHtml(chair)}` : ""}</small><em>${escapeHtml(CARE_INFUSION_LABELS[status] || status)}</em></div></li>`;
  }).join("")}</ol>`;
}

function careTreatmentDetailStatusClass(status) {
  const normalized = normalizeSearchText(status);
  if (/cancel|suspend|paus/.test(normalized)) return "is-cancelled";
  if (/complet|finaliz|termin/.test(normalized)) return "is-completed";
  if (/pend|program|esper/.test(normalized)) return "is-pending";
  if (/activ|inici|curso|ready|list/.test(normalized)) return "is-ready";
  return "";
}

function careTreatmentIsLocalRecord(item) {
  const treatmentId = careTreatmentManagerRecordId(item);
  const numericId = /^\d+$/.test(treatmentId) ? Number(treatmentId) : 0;
  return Boolean(
    item?.origenLocal === true ||
    careField(item, "clinicalEntryId") ||
    (Number.isSafeInteger(numericId) && numericId >= 8_000_000_000_000_000)
  );
}

function careTreatmentSessions(item) {
  const treatmentId = careTreatmentManagerRecordId(item);
  return careInfusions
    .filter((session) => careField(session, "treatmentId", "treatment_id") === treatmentId)
    .sort((left, right) =>
      String(careField(left, "scheduledAt", "scheduled_at"))
        .localeCompare(String(careField(right, "scheduledAt", "scheduled_at"))));
}

function careTreatmentSchemeMatch(item, schemes) {
  const schemeName = normalizeSearchText(careField(item, "esquema", "scheme"));
  if (!schemeName) return null;
  const namedSchemes = (schemes || []).filter((scheme) =>
    normalizeSearchText(careField(scheme, "nombre", "name")));
  return namedSchemes.find((scheme) =>
    normalizeSearchText(careField(scheme, "nombre", "name")) === schemeName) ||
    namedSchemes.find((scheme) => {
      const candidateName = normalizeSearchText(careField(scheme, "nombre", "name"));
      return candidateName.includes(schemeName) || schemeName.includes(candidateName);
    }) ||
    null;
}

async function loadCareTreatmentLocalScheme(item) {
  const known = careTreatmentSchemeMatch(item, careSchemes);
  if (known) return known;
  const schemeName = careField(item, "esquema", "scheme");
  if (!schemeName) return null;
  try {
    const response = await fetch(`/api/clinical/schemes?q=${encodeURIComponent(schemeName)}`, { cache: "no-store" });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) return null;
    return careTreatmentSchemeMatch(item, clinicalPayloadItems(payload, ["schemes"]));
  } catch {
    return null;
  }
}

function careTreatmentApplicationDayNumbers(value) {
  const days = [...String(value || "").matchAll(/\d+/g)]
    .map((match) => Number(match[0]))
    .filter((number) => Number.isSafeInteger(number) && number > 0 && number <= 365);
  return [...new Set(days.length ? days : [1])].sort((left, right) => left - right);
}

function careTreatmentLocalMedicationState(medication) {
  const status = normalizeSearchText(careField(
    medication,
    "administrationStatus", "administration_status", "status"
  ));
  if (/withheld|cancel|suspend|omit/.test(status)) return "withheld";
  if (/complete|administer|aplicad|finaliz/.test(status)) return "administered";
  return "planned";
}

function careTreatmentLocalDrugs(item, scheme, sessions) {
  const calculation = item?.calculation && typeof item.calculation === "object" ? item.calculation : {};
  const components = Array.isArray(scheme?.components) ? scheme.components : [];
  if (components.length) {
    return components.map((component) => ({
      drugName: careField(component, "drugName", "droga", "name") || "Droga",
      calculationMethod: careField(component, "doseCalculationMethod", "calculationMethod", "calculoDosis"),
      prescribedDoseText: careField(component, "prescribedDoseText", "doseText", "dosisDiaria"),
      applicationDays: careField(component, "day", "applicationDays", "dia") || "1",
      route: careField(component, "route", "viaAdministracion"),
      administrationTime: careField(component, "administrationTime", "tiempoAdministracion"),
      creatinine: calculation.creatinina,
      targetAuc: calculation.targetAUC,
      totalDoseText: careField(component, "totalDoseText", "prescribedDoseText", "doseText", "dosisDiaria")
    }));
  }
  const medications = sessions.flatMap((session) =>
    Array.isArray(session?.medications) ? session.medications : []);
  return [...new Map(medications.map((medication) => {
    const drugName = careField(medication, "drugName", "drug_name", "name") || "Droga";
    return [normalizeSearchText(drugName), {
      drugName,
      calculationMethod: "",
      prescribedDoseText: careField(medication, "prescribedDoseText", "prescribed_dose_text", "doseText"),
      applicationDays: "1",
      route: careField(medication, "route", "via"),
      administrationTime: "",
      totalDoseText: careField(medication, "prescribedDoseText", "prescribed_dose_text", "doseText")
    }];
  })).values()];
}

function careTreatmentLocalApplication(session) {
  const notes = careField(session, "notes", "observations", "observaciones");
  const scheduledAt = careField(session, "scheduledAt", "scheduled_at");
  const cycleNumber = careField(session, "cycleNumber", "cycle_number") || "ciclo";
  const observations = Array.isArray(session?.observations)
    ? session.observations
    : notes ? [{ observation: notes, user: "Equipo tratante" }] : [];
  return {
    applicationId: careField(session, "id", "sessionId") || `local-${cycleNumber}-${scheduledAt || "sin-fecha"}`,
    sourceCycleId: "",
    date: formatCareDateTime(scheduledAt),
    scheduledAt,
    status: careField(session, "clinicalStatus", "clinical_status", "status"),
    vitals: session?.vitals && typeof session.vitals === "object" ? session.vitals : {},
    observations
  };
}

function buildCareTreatmentLocalDetail(item, scheme = null) {
  const sessions = careTreatmentSessions(item);
  const initialCycle = Math.max(1, Number(careField(item, "cicloInicial", "initialCycle")) || 1);
  const totalCycles = Math.max(initialCycle, Math.min(
    50,
    Number(careField(item, "cantidadCiclos", "cycles", "cycleCount")) || initialCycle
  ));
  const drugs = careTreatmentLocalDrugs(item, scheme, sessions);
  const cycles = Array.from({ length: totalCycles - initialCycle + 1 }, (_, index) => {
    const number = initialCycle + index;
    const cycleSessions = sessions.filter((session) =>
      Number(careField(session, "cycleNumber", "cycle_number")) === number);
    const statuses = cycleSessions.map((session) =>
      normalizeSearchText(careField(session, "clinicalStatus", "clinical_status", "status")));
    const completed = statuses.length > 0 && statuses.every((status) => /complete|finaliz|termin/.test(status));
    const cycleState = completed ? "completed" : cycleSessions.length ? "current" : "pending";
    const storedMedications = cycleSessions.flatMap((session) =>
      Array.isArray(session?.medications) ? session.medications : []);
    const dayNumbers = [...new Set(drugs.flatMap((drug) =>
      careTreatmentApplicationDayNumbers(drug.applicationDays)))];
    const days = dayNumbers.sort((left, right) => left - right).map((day) => {
      const scheduledDrugs = drugs.filter((drug) =>
        careTreatmentApplicationDayNumbers(drug.applicationDays).includes(day));
      const medicationRows = day === dayNumbers[0] && storedMedications.length
        ? storedMedications.map((medication) => ({
          drugName: careField(medication, "drugName", "drug_name", "name") || "Droga",
          actualDoseText: careField(medication, "prescribedDoseText", "prescribed_dose_text", "doseText"),
          prescribedDoseText: "",
          status: careTreatmentLocalMedicationState(medication)
        }))
        : scheduledDrugs.map((drug) => ({
          drugName: drug.drugName,
          actualDoseText: drug.prescribedDoseText,
          prescribedDoseText: "",
          status: "planned"
        }));
      return { day, rest: false, status: completed ? "completed" : "pending", medications: medicationRows };
    });
    return {
      number,
      state: cycleState,
      selected: false,
      disabled: false,
      sourceCycleId: "",
      drugs,
      applications: cycleSessions.map(careTreatmentLocalApplication),
      days
    };
  });
  const activeCycle = cycles.find((cycle) => cycle.state !== "completed")?.number || cycles.at(-1)?.number || initialCycle;
  return {
    patientId: getActiveLiraPatientId(),
    treatmentId: careTreatmentManagerRecordId(item),
    activeCycle,
    cycles,
    actions: {
      prescription: false,
      medicationOrder: false,
      treatmentSheet: false,
      treatmentSheetCycles: [],
      qr: true
    },
    documentAvailability: {
      prescription: false,
      medicationOrder: false,
      qr: true,
      treatmentSheetCycles: []
    },
    localView: true,
    localRecord: careTreatmentIsLocalRecord(item),
    schemeFound: Boolean(scheme),
    localScheme: scheme,
    compatibilityFallback: true
  };
}

function careTreatmentExactCycle(detail) {
  const cycles = Array.isArray(detail?.cycles) ? detail.cycles : [];
  const requested = Number(careTreatmentManagerState.detailCycle) || Number(detail?.activeCycle) || cycles[0]?.number || 1;
  return cycles.find((cycle) => Number(cycle.number) === requested) || cycles[0] || null;
}

function careTreatmentDocumentUrl(patientId, treatmentId, kind, parameters = {}) {
  const query = new URLSearchParams(Object.entries(parameters).filter(([, value]) => String(value || "").trim()));
  return `/api/clinical/patients/${encodeURIComponent(patientId)}/treatments/${encodeURIComponent(treatmentId)}/documents/${encodeURIComponent(kind)}${query.size ? `?${query}` : ""}`;
}

function renderCareExactCycleStrip(detail) {
  const selected = careTreatmentExactCycle(detail);
  return (detail.cycles || []).map((cycle) => {
    const number = Number(cycle.number);
    const active = number === Number(selected?.number);
    const state = cycle.state === "completed" ? "is-completed" : cycle.disabled ? "is-pending" : "is-current";
    return `<button class="care-treatment-cycle ${state}${active ? " is-selected" : ""}" type="button" data-care-manager-cycle="${number}" aria-pressed="${String(active)}"${cycle.disabled ? " disabled" : ""}><span>Ciclo ${number}</span></button>`;
  }).join("");
}

function careTreatmentValueWithUnit(value, unit) {
  const textValue = String(value || "").trim();
  if (!textValue) return "";
  return /[a-z%]/i.test(textValue) ? textValue : `${textValue} ${unit}`;
}

function renderCareExactDrugs(cycle) {
  const drugs = Array.isArray(cycle?.drugs) ? cycle.drugs : [];
  if (!drugs.length) return `<div class="care-treatment-detail-empty">Este ciclo no tiene drogas prescriptas.</div>`;
  return `<div class="care-treatment-drug-cards">${drugs.map((drug) => {
    const rows = [
      ["Método de cálculo de dosis:", drug.calculationMethod],
      drug.creatinine && ["Creatinina:", careTreatmentValueWithUnit(drug.creatinine, "mg/dl")],
      drug.targetAuc && ["Target AUC (área bajo la curva):", careTreatmentValueWithUnit(drug.targetAuc, "mg/ml/min")],
      ["Dosis diaria calculada:", drug.calculatedDoseText],
      ["Dosis diaria real:", drug.prescribedDoseText],
      ["Días de aplicación:", drug.applicationDays],
      ["Vía de administración:", drug.route],
      ["Tiempo de administración:", drug.administrationTime]
    ].filter(Boolean);
    return `<article>
      <header><strong>${escapeHtml(drug.drugName || "Droga")}</strong></header>
      <ul>${rows.map(([label, value]) => `<li><strong>${escapeHtml(label)}</strong>${value ? ` ${escapeHtml(value)}` : ""}</li>`).join("")}</ul>
      <footer><strong>Cantidad total:</strong>${drug.totalDoseText ? ` ${escapeHtml(drug.totalDoseText)}` : ""}</footer>
    </article>`;
  }).join("")}</div>`;
}

function careApplicationMedicationIcon(status) {
  if (status === "administered") return "check";
  if (status === "withheld") return "x";
  return "house";
}

function renderCareExactApplicationDays(cycle, observationLabel = "Signos vitales y Obs.") {
  const days = Array.isArray(cycle?.days) ? cycle.days : [];
  if (!days.length) return `<div class="care-treatment-detail-empty">Este ciclo aún no tiene aplicaciones registradas.</div>`;
  const application = (cycle.applications || [])[0] || null;
  return `<ol class="lira-treatment-application-days">${days.map((day, index) => {
    const storedMedications = Array.isArray(day.medications) ? day.medications : [];
    const usesPlannedDrugs = !storedMedications.length && !day.rest && Number(day.day) === 1;
    const medications = usesPlannedDrugs ? (cycle.drugs || []).map((drug) => ({
      drugName: drug.drugName,
      actualDoseText: drug.prescribedDoseText,
      prescribedDoseText: "",
      status: "planned"
    })) : storedMedications;
    const visualState = day.status === "completed" ? "is-completed" : medications.length ? "is-current" : "is-pending";
    const observationButton = index === 0 && application?.applicationId
      ? `<button class="lira-treatment-observation-trigger" type="button" data-care-manager-detail-action="observation" data-application-id="${escapeAttr(application.applicationId)}"><i data-lucide="calendar-check"></i><span>${escapeHtml(observationLabel)}</span></button>`
      : "";
    const content = day.rest
      ? `<strong class="lira-treatment-rest">DESCANSO</strong>`
      : `${observationButton}${medications.map((medication) => `<p class="is-${escapeAttr(medication.status || "planned")}">${usesPlannedDrugs || medication.status === "planned" ? "" : `<i data-lucide="${careApplicationMedicationIcon(medication.status)}"></i>`}<span>${escapeHtml(medication.drugName)}:</span> ${escapeHtml(medication.actualDoseText || medication.prescribedDoseText || "")}${medication.prescribedDoseText && medication.actualDoseText ? ` <small>(${escapeHtml(medication.prescribedDoseText)})</small>` : ""}</p>`).join("")}`;
    return `<li class="${visualState}"><div class="lira-treatment-day-content">${content}</div><div class="lira-treatment-day-time"><h4>Día ${escapeHtml(day.day)}</h4></div></li>`;
  }).join("")}</ol>`;
}

function careObservationValue(row, ...keys) {
  for (const key of keys) {
    const exact = row?.[key];
    if (exact !== undefined && exact !== null && String(exact).trim()) return String(exact).trim();
    const found = Object.entries(row || {}).find(([name, value]) => normalizeSearchText(name) === normalizeSearchText(key) && String(value ?? "").trim());
    if (found) return String(found[1]).trim();
  }
  return "";
}

function renderCareExactObservationModal(detail) {
  const applicationId = String(careTreatmentManagerState.observationApplicationId || "");
  if (!applicationId) return "";
  const application = (detail.cycles || []).flatMap((cycle) => cycle.applications || [])
    .find((candidate) => String(candidate.applicationId) === applicationId);
  if (!application) return "";
  const vitals = application.vitals || {};
  const rows = Array.isArray(application.observations) ? application.observations : [];
  return `<div class="lira-observation-backdrop" role="presentation" data-care-manager-detail-action="close-observation">
    <section class="lira-observation-modal" role="dialog" aria-modal="true" aria-labelledby="liraObservationTitle" data-care-observation-dialog tabindex="-1">
      <header><div><small>Aplicacion ${escapeHtml(application.applicationId)}</small><h3 id="liraObservationTitle">${escapeHtml(application.date || "Fecha sin registrar")}</h3><p>Signos vitales y observaciones</p></div><button class="icon-button" type="button" data-care-manager-detail-action="close-observation" aria-label="Cerrar"><i data-lucide="x"></i></button></header>
      <div class="lira-vital-grid">${[
        ["Saturacion", vitals.oxygenSaturation, "%"],
        ["Temperatura", vitals.temperature, "°C"],
        ["Tension arterial", vitals.bloodPressure, "mmHg"],
        ["Frecuencia cardiaca", vitals.heartRate, "lat/min"]
      ].map(([label, value, unit]) => `<div><span>${escapeHtml(label)}</span><strong>${escapeHtml(value || "—")}</strong><small>${value ? escapeHtml(unit) : ""}</small></div>`).join("")}</div>
      <section class="lira-observation-history"><h4>Observaciones (historial)</h4><div class="lira-observation-table-shell"><table><thead><tr><th>Observacion</th><th>Usuario</th></tr></thead><tbody>${rows.length ? rows.map((row) => `<tr><td>${escapeHtml(careObservationValue(row, "observacion", "observation", "detalle") || "—")}</td><td>${escapeHtml(careObservationValue(row, "usuario", "user", "profesional") || "—")}</td></tr>`).join("") : `<tr><td colspan="2">Sin observaciones registradas.</td></tr>`}</tbody></table></div></section>
      <footer><button class="tool-button" type="button" data-care-manager-detail-action="close-observation"><i data-lucide="x"></i><span>Cerrar</span></button></footer>
    </section>
  </div>`;
}

function renderCareExactApplications(detail, cycle) {
  const observationLabel = "Signos vitales y Obs.";
  return `${renderCareExactApplicationDays(cycle, observationLabel)}${renderCareExactObservationModal(detail)}`;
}

function renderCareExactHospitalDay(item, cycle) {
  const treatmentId = careTreatmentManagerRecordId(item);
  const sessions = careTreatmentSessions(item);
  const visible = sessions.filter((session) => !cycle || Number(careField(session, "cycleNumber", "cycle_number")) === Number(cycle.number));
  const scheduledSession = visible.find((session) =>
    careField(session, "clinicalStatus", "clinical_status") !== "cancelled" &&
    Boolean(careField(session, "scheduledAt", "scheduled_at")));
  const scheduleLabel = scheduledSession
    ? "Ver turno en Sillones"
    : cycle?.disabled ? "Gestionar ciclo en Sillones" : "Asignar en Sillones";
  const scheduleIcon = scheduledSession ? "calendar-search" : cycle?.disabled ? "clipboard-clock" : "calendar-plus";
  return `<section class="lira-hospital-day-branch"><header><div><small>Hospital de dia</small><h4>Ciclo ${escapeHtml(cycle?.number || "")}</h4></div><button class="tool-button primary" type="button" data-care-manager-detail-action="schedule" data-treatment-id="${escapeAttr(treatmentId)}" data-cycle-number="${escapeAttr(cycle?.number || "")}" data-session-id="${escapeAttr(careField(scheduledSession, "id", "sessionId"))}" data-scheduled-date="${escapeAttr(careLocalDateKey(careField(scheduledSession, "scheduledAt", "scheduled_at")))}"><i data-lucide="${scheduleIcon}"></i><span>${scheduleLabel}</span></button></header>${visible.length ? visible.map((session) => {
    const clinical = careField(session, "clinicalStatus", "clinical_status") || "planned";
    const pharmacy = careField(session, "pharmacyStatus", "pharmacy_status") || "pending";
    const administration = careField(session, "administrationStatus", "administration_status") || "not_started";
    const next = getCareInfusionNextStep(session);
    const sessionId = careField(session, "id", "sessionId");
    const version = careField(session, "revision", "version") || "1";
    return `<article class="lira-hospital-session"><div><strong>${escapeHtml(formatCareDateTime(careField(session, "scheduledAt", "scheduled_at")))}</strong><span>${careField(session, "chair", "sillon") ? `Sillon ${escapeHtml(careField(session, "chair", "sillon"))}` : "Sin sillon asignado"}</span></div><dl><div><dt>Estado clinico</dt><dd>${escapeHtml(CARE_INFUSION_LABELS[clinical] || clinical)}</dd></div><div><dt>Farmacia</dt><dd>${escapeHtml(CARE_INFUSION_LABELS[pharmacy] || pharmacy)}</dd></div><div><dt>Administracion</dt><dd>${escapeHtml(CARE_INFUSION_LABELS[administration] || administration)}</dd></div></dl>${next ? `<button class="tool-button primary" type="button" data-care-infusion-action="advance" data-infusion-id="${escapeAttr(sessionId)}" data-version="${escapeAttr(version)}"><i data-lucide="arrow-right"></i><span>${escapeHtml(next.label)}</span></button>` : `<span class="care-badge ${escapeAttr(careInfusionVisualState(clinical))}">${escapeHtml(CARE_INFUSION_LABELS[clinical] || clinical)}</span>`}</article>`;
  }).join("") : `<div class="care-treatment-detail-empty care-treatment-actionable-empty"><i data-lucide="calendar-plus"></i><strong>Este ciclo todavía no tiene turno.</strong><span>Programe la aplicación para continuar con farmacia, sillón y administración.</span></div>`}</section>`;
}

function renderCareExactPharmacy(item, cycle) {
  const treatmentId = careTreatmentManagerRecordId(item);
  const session = careInfusions.find((candidate) => careField(candidate, "treatmentId", "treatment_id") === treatmentId && Number(careField(candidate, "cycleNumber", "cycle_number")) === Number(cycle?.number));
  const medicationStates = new Map((session?.medications || []).map((medication) => [normalizeSearchText(careField(medication, "drugName", "drug_name")), medication]));
  const drugs = Array.isArray(cycle?.drugs) ? cycle.drugs : [];
  const pharmacyStatus = careField(session, "pharmacyStatus", "pharmacy_status");
  return `<section class="lira-pharmacy-branch"><header><div><small>Farmacia</small><h4>Preparacion del ciclo ${escapeHtml(cycle?.number || "")}</h4></div><span class="care-badge">${escapeHtml(CARE_INFUSION_LABELS[pharmacyStatus] || pharmacyStatus || "Pendiente de turno")}</span></header>${!session ? `<div class="care-treatment-local-next-step"><span>Primero asigne un turno para habilitar la recepción y preparación de medicación.</span><button class="tool-button primary" type="button" data-care-manager-detail-pane="day-hospital"><i data-lucide="calendar-plus"></i><span>Ir a Hospital de día</span></button></div>` : ""}<div>${drugs.map((drug) => {
    const medication = medicationStates.get(normalizeSearchText(drug.drugName));
    const preparation = careField(medication, "preparationStatus", "preparation_status") || "pending";
    const administration = careField(medication, "administrationStatus", "administration_status") || "not_started";
    return `<article><div><strong>${escapeHtml(drug.drugName)}</strong><span>${escapeHtml(drug.prescribedDoseText || "")}${drug.route ? ` · ${escapeHtml(drug.route)}` : ""}</span></div><dl><div><dt>Preparacion</dt><dd>${escapeHtml(CARE_INFUSION_LABELS[preparation] || preparation)}</dd></div><div><dt>Administracion</dt><dd>${escapeHtml(CARE_INFUSION_LABELS[administration] || administration)}</dd></div></dl></article>`;
  }).join("") || `<div class="care-treatment-detail-empty">No hay drogas para preparar en este ciclo.</div>`}</div></section>`;
}

function renderCareExactDocuments(detail, cycle, patientId, treatmentId) {
  const actions = detail.actions || {};
  const availability = detail.documentAvailability || {};
  const sheetEnabled = actions.treatmentSheet && (actions.treatmentSheetCycles || []).includes(Number(cycle?.number));
  const sheetAvailable = (availability.treatmentSheetCycles || []).includes(Number(cycle?.number));
  const links = [
    actions.prescription && ["prescription", "Descargar prescripción", "Prescripción", "file-down", {}, availability.prescription !== false],
    sheetEnabled && ["treatment-sheet", "Descargar hoja de tratamiento", "Hoja tratamiento", "clipboard-down", { cycle: cycle.number }, sheetAvailable || !detail.documentAvailability],
    cycle?.number && ["qr", "Abrir QR para imprimir", "QR", "qr-code", { cycle: cycle.number }, true]
  ].filter(Boolean);
  return `<div class="lira-treatment-downloads" aria-label="Documentos del tratamiento">${links.map(([kind, label, shortLabel, icon, parameters, available]) => available
    ? `<a class="lira-treatment-document-button" href="${escapeAttr(careTreatmentDocumentUrl(patientId, treatmentId, kind, parameters))}" target="_blank" rel="noopener" title="${escapeAttr(label)}" aria-label="${escapeAttr(label)}"><i data-lucide="${escapeAttr(icon)}"></i><span>${escapeHtml(shortLabel)}</span></a>`
    : `<button class="lira-treatment-document-button" type="button" disabled title="${escapeAttr(`${label}: documento no disponible en la base clínica local`)}" aria-label="${escapeAttr(`${label}: no disponible`)}"><i data-lucide="${escapeAttr(icon)}"></i><span>${escapeHtml(shortLabel)}</span></button>`).join("")}</div>`;
}

function renderCareTreatmentLocalSummary(item, detail, cycle) {
  if (!detail?.localView) return "";
  const sessions = careTreatmentSessions(item);
  const cycleSession = sessions.find((session) =>
    Number(careField(session, "cycleNumber", "cycle_number")) === Number(cycle?.number));
  const chair = careField(cycleSession, "chair", "sillon");
  const chairLabel = chair ? (/sill[oó]n/i.test(chair) ? chair : `Sillón ${chair}`) : "";
  const appointmentStatus = cycleSession
    ? `${formatCareDateTime(careField(cycleSession, "scheduledAt", "scheduled_at"))}${chairLabel ? ` · ${chairLabel}` : ""}`
    : "Sin turno asignado";
  const pharmacyStatus = careField(cycleSession, "pharmacyStatus", "pharmacy_status");
  const calculation = item?.calculation && typeof item.calculation === "object" ? item.calculation : {};
  const calculationRows = [
    calculation.peso && ["Peso", `${calculation.peso} kg`],
    calculation.talla && ["Talla", `${calculation.talla} cm`],
    calculation.superficieCorporal && ["Superficie corporal", `${calculation.superficieCorporal} m²`],
    calculation.creatinina && ["Creatinina", `${calculation.creatinina} mg/dl`],
    calculation.tfg && ["TFG", calculation.tfg],
    calculation.targetAUC && ["Target AUC", calculation.targetAUC]
  ].filter(Boolean);
  const clinicalRows = [
    ["Esquema", careField(item, "esquema", "scheme") || "No informado"],
    ["Tipo e intención", [careField(item, "tipo", "type"), careField(item, "caracter", "intent")].filter(Boolean).join(" · ") || "No informado"],
    ["Profesional", careField(item, "oncologo", "oncologist") || "No informado"],
    ["Plan", `${careField(item, "cantidadCiclos", "cycles", "cycleCount") || "1"} ciclos${careField(item, "duracionCiclo", "cycleDays") ? ` · cada ${careField(item, "duracionCiclo", "cycleDays")} días` : ""}`],
    ["Primer ciclo", careTreatmentHistoryDate(careField(item, "fechaPrimerCiclo", "firstCycleDate"))],
    ["Consentimiento", careField(item, "estadoConsentimiento", "consentStatus") || "Pendiente"],
    ["Requisitos", item?.requirementsConfirmed ? "Verificados" : "Pendientes de verificación"]
  ];
  const observations = careField(item, "observaciones", "observations");
  return `<section class="care-treatment-local-summary" aria-label="Resumen local del tratamiento">
    <div class="care-treatment-local-facts">${clinicalRows.map(([label, value]) => `<div><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`).join("")}</div>
    ${calculationRows.length ? `<div class="care-treatment-local-calculation" aria-label="Datos de cálculo">${calculationRows.map(([label, value]) => `<span><small>${escapeHtml(label)}</small><b>${escapeHtml(value)}</b></span>`).join("")}</div>` : ""}
    ${observations ? `<p><strong>Observaciones</strong><span>${escapeHtml(observations)}</span></p>` : ""}
    ${!detail.schemeFound ? `<p class="care-treatment-local-notice"><i data-lucide="info"></i><span>El tratamiento está registrado. El detalle de drogas se completará cuando el esquema esté disponible en Protocolos.</span></p>` : ""}
    <div class="care-treatment-local-statuses">
      <button type="button" data-care-manager-detail-pane="day-hospital"><i data-lucide="calendar-clock"></i><span><small>Turno · Ciclo ${escapeHtml(cycle?.number || "")}</small><strong>${escapeHtml(appointmentStatus)}</strong></span><i data-lucide="chevron-right"></i></button>
      <button type="button" data-care-manager-detail-pane="pharmacy"><i data-lucide="pill"></i><span><small>Farmacia</small><strong>${escapeHtml(CARE_INFUSION_LABELS[pharmacyStatus] || pharmacyStatus || "Pendiente de turno")}</strong></span><i data-lucide="chevron-right"></i></button>
    </div>
  </section>`;
}

function careTreatmentManagerDetailMarkup(item, pane, detail = careTreatmentManagerState.exactDetail) {
  const title = careField(item, "diagnostico", "diagnosis") || "Tratamiento oncologico";
  const scheme = careField(item, "esquema", "scheme");
  const status = careField(item, "estadoTratamiento", "status") || "Registrado";
  const duration = careTreatmentDuration(item);
  const treatmentId = careTreatmentManagerRecordId(item);
  const patientId = getActiveLiraPatientId();
  if (!detail) return `<button class="tool-button care-treatment-detail-back" type="button" data-care-manager-detail-action="back"><i data-lucide="arrow-left"></i><span>Volver a tratamientos</span></button><div class="lira-treatment-loading"><i data-lucide="loader-circle"></i><strong>Cargando datos del tratamiento...</strong><span>Plan, ciclos, drogas, aplicaciones y estados operativos.</span></div>`;
  const cycle = careTreatmentExactCycle(detail);
  const allowedPanes = new Set(["drugs", "applications", "day-hospital", "pharmacy"]);
  const activePane = allowedPanes.has(pane) ? pane : "drugs";
  const statusClass = careTreatmentDetailStatusClass(status);
  const paneMarkup = activePane === "drugs" ? renderCareExactDrugs(cycle)
    : activePane === "applications" ? renderCareExactApplications(detail, cycle)
    : activePane === "day-hospital" ? renderCareExactHospitalDay(item, cycle)
    : renderCareExactPharmacy(item, cycle);
  return `<button class="tool-button care-treatment-detail-back lira-treatment-list-back" type="button" data-care-manager-detail-action="back" aria-label="Volver a tratamientos" title="Volver a tratamientos"><i data-lucide="arrow-left"></i><span>Volver a tratamientos</span></button>
    <article class="care-treatment-detail-card lira-treatment-exact${detail.localView ? " is-local-view" : ""}">
      <header class="lira-treatment-overview-header">
        <div class="lira-treatment-title"><h3>Tratamiento para ${escapeHtml(title)}</h3><span class="care-badge ${escapeAttr(statusClass)}">${escapeHtml(status)}</span><span class="care-treatment-duration-chip" title="${escapeAttr(careTreatmentDurationTitle(item))}"><i data-lucide="clock-3"></i>${escapeHtml(duration)}</span></div>
        <div class="lira-treatment-header-tools">
          <nav class="lira-treatment-branch-nav" aria-label="Gestión local del tratamiento"><button type="button" data-care-manager-detail-pane="day-hospital" class="${activePane === "day-hospital" ? "active" : ""}"><i data-lucide="syringe"></i><span>Hospital de día</span></button><button type="button" data-care-manager-detail-pane="pharmacy" class="${activePane === "pharmacy" ? "active" : ""}"><i data-lucide="pill"></i><span>Farmacia</span></button><button type="button" data-care-manager-detail-action="refresh-detail"><i data-lucide="refresh-cw"></i><span>${detail.localView ? "Actualizar estados" : "Actualizar detalle"}</span></button></nav>
          <div class="lira-treatment-heading-actions">${renderCareExactDocuments(detail, cycle, patientId, treatmentId)}</div>
        </div>
      </header>
      ${renderCareTreatmentLocalSummary(item, detail, cycle)}
      <div class="care-treatment-cycles" aria-label="Ciclos del tratamiento">${renderCareExactCycleStrip(detail)}</div>
      <nav class="care-treatment-detail-tabs" aria-label="Detalle del tratamiento"><button type="button" data-care-manager-detail-pane="drugs" class="${activePane === "drugs" ? "active" : ""}">Drogas</button><button type="button" data-care-manager-detail-pane="applications" class="${activePane === "applications" ? "active" : ""}">Aplicaciones</button></nav>
      <section class="care-treatment-detail-pane" data-care-manager-detail-content="${escapeAttr(activePane)}">${paneMarkup}</section>
    </article>`;
}

function resolveCareTreatmentDetailCycle(detail, requestedCycle = 0) {
  const requested = Math.max(0, Number(requestedCycle) || 0);
  const cycles = Array.isArray(detail?.cycles) ? detail.cycles : [];
  const available = new Set(cycles.map((cycle) => Number(cycle?.number)).filter(Number.isFinite));
  if (requested && (!available.size || available.has(requested))) return requested;
  return Number(detail?.activeCycle) || Number(cycles[0]?.number) || 1;
}

async function loadCareTreatmentManagerDrugs(item, requestVersion) {
  const output = $("#careTreatmentManagerDetail");
  const patientId = getActiveLiraPatientId();
  const treatmentId = careTreatmentManagerRecordId(item);
  if (!output || !patientId || !treatmentId) return;
  try {
    const response = await fetch(`/api/clinical/patients/${encodeURIComponent(patientId)}/treatments/${encodeURIComponent(treatmentId)}/detail`, { cache: "no-store" });
    const payload = await response.json().catch(() => ({}));
    let detail;
    if (response.status === 404) {
      detail = buildCareTreatmentLocalDetail(item, await loadCareTreatmentLocalScheme(item));
    } else if (!response.ok) {
      throw new Error(payload.error || "No se pudo abrir el detalle del tratamiento");
    } else {
      detail = payload.detail || payload;
      if (careTreatmentIsLocalRecord(item)) {
        const hasProtocolDrugs = (detail.cycles || []).some((cycle) =>
          Array.isArray(cycle?.drugs) && cycle.drugs.length);
        detail = {
          ...detail,
          localView: true,
          localRecord: true,
          origin: detail.origin || "local",
          schemeFound: typeof detail.schemeFound === "boolean" ? detail.schemeFound : hasProtocolDrugs
        };
      }
    }
    if (requestVersion !== careTreatmentDetailRequestVersion) return;
    careTreatmentManagerState.exactDetail = detail;
    careTreatmentManagerState.detailCycle = resolveCareTreatmentDetailCycle(
      careTreatmentManagerState.exactDetail,
      careTreatmentManagerState.detailCycle
    );
    renderCareTreatmentManagerPatientSummary();
    output.innerHTML = careTreatmentManagerDetailMarkup(item, careTreatmentManagerState.detailPane);
  } catch (error) {
    if (requestVersion !== careTreatmentDetailRequestVersion) return;
    console.warn("No se pudo cargar el detalle clínico local.", error);
    output.innerHTML = `<button class="tool-button care-treatment-detail-back" type="button" data-care-manager-detail-action="back"><i data-lucide="arrow-left"></i><span>Volver a tratamientos</span></button><div class="care-treatment-detail-empty is-error"><strong>No se pudo abrir el detalle del tratamiento.</strong><span>Revise la conexión con la base clínica local y vuelva a intentar.</span><button class="tool-button primary" type="button" data-care-manager-detail-action="refresh-detail"><i data-lucide="refresh-cw"></i><span>Reintentar</span></button></div>`;
  }
  refreshIcons();
}

function openCareTreatmentManagerDetail(item, pane = "drugs", { cycleNumber = 0 } = {}) {
  const output = $("#careTreatmentManagerDetail");
  const modal = $("#careTreatmentManagerModal");
  if (!output || !modal) return;
  careTreatmentManagerState.selectedId = careTreatmentManagerRecordId(item);
  careTreatmentManagerState.mode = "detail";
  careTreatmentManagerState.detailPane = pane;
  careTreatmentManagerState.detailCycle = Math.max(0, Number(cycleNumber) || 0);
  careTreatmentManagerState.exactDetail = null;
  careTreatmentManagerState.observationApplicationId = "";
  const requestVersion = ++careTreatmentDetailRequestVersion;
  modal.classList.add("is-detail");
  output.hidden = false;
  output.innerHTML = careTreatmentManagerDetailMarkup(item, pane, null);
  loadCareTreatmentManagerDrugs(item, requestVersion);
  refreshIcons();
  output.querySelector("button")?.focus();
}

async function openCareScheduleFromTreatmentDetail(actionButton, item) {
  const patientId = getActiveLiraPatientId();
  const treatmentId = String(actionButton?.dataset.treatmentId || careTreatmentManagerRecordId(item));
  const cycleNumber = Math.max(1, Number(actionButton?.dataset.cycleNumber) || Number(careTreatmentExactCycle(careTreatmentManagerState.exactDetail)?.number) || 1);
  const sessionId = String(actionButton?.dataset.sessionId || "");
  const scheduledDate = String(actionButton?.dataset.scheduledDate || "");
  const patientName = getLiraPatientName(state?.patient);
  const patientSearch = patientName.replace(/[,;]+/g, " ").replace(/\s+/g, " ").trim();
  const search = $("#careScheduleCandidateSearch");
  if (search) search.value = patientName === "Paciente sin nombre" ? "" : patientSearch;
  careScheduleSelectedCandidateId = "";
  if (/^\d{4}-\d{2}-\d{2}$/.test(scheduledDate)) setCareScheduleDate(scheduledDate);
  setCareHospitalTab("chairs");
  await loadCareSchedule();

  const scheduledSession = careScheduleInfusions.find((session) =>
    (sessionId && String(session.id) === sessionId) ||
    (String(session.patientId || "") === patientId &&
      String(session.treatmentId || "") === treatmentId &&
      Number(session.cycleNumber) === cycleNumber &&
      session.clinicalStatus !== "cancelled"));
  if (scheduledSession) {
    const actualDate = careLocalDateKey(scheduledSession.scheduledAt);
    if (actualDate && actualDate !== selectedCareScheduleDate()) {
      setCareScheduleDate(actualDate);
      await loadCareSchedule();
    }
    const chair = careScheduleChair(scheduledSession.chair);
    const viewport = careScheduleChairViewport();
    if (chair && (chair < viewport.first || chair > viewport.last)) {
      careScheduleChairOffset = Math.max(0, Math.min(
        viewport.total - viewport.visible,
        chair - Math.ceil(viewport.visible / 2)
      ));
      renderCareSchedule();
    }
    renderCareScheduleSearchHighlights();
    const appointment = $(`[data-care-schedule-infusion="${CSS.escape(String(scheduledSession.id))}"]`);
    appointment?.scrollIntoView?.({ block: "nearest", inline: "nearest" });
    toast("Turno resaltado en Sillones");
    return;
  }

  const candidate = careScheduleCandidates.find((entry) =>
    String(entry.patientId || "") === patientId &&
    String(entry.treatmentId || "") === treatmentId &&
    Number(entry.cycleNumber) === cycleNumber);
  if (!candidate) {
    toast("El ciclo no está disponible para asignar en Sillones");
    return;
  }
  const filter = $("#careScheduleCandidateFilter");
  if (filter) filter.value = "prescribed";
  const blockedReason = careScheduleCandidateBlockedReason(candidate);
  careScheduleSelectedCandidateId = blockedReason ? "" : String(candidate.id);
  renderCareScheduleCandidates();
  renderCareScheduleAvailability();
  const candidateCard = $(`[data-care-schedule-candidate="${CSS.escape(String(candidate.id))}"]`);
  candidateCard?.scrollIntoView?.({ block: "nearest" });
  toast(blockedReason || "Ciclo seleccionado para asignar en Sillones");
}

async function handleCareTreatmentManagerDetailAction(event) {
  const item = selectedCareTreatmentManagerRecord();
  const cycleButton = event.target.closest("[data-care-manager-cycle]");
  if (cycleButton && item) {
    careTreatmentManagerState.detailCycle = Number(cycleButton.dataset.careManagerCycle);
    careTreatmentManagerState.observationApplicationId = "";
    event.currentTarget.innerHTML = careTreatmentManagerDetailMarkup(item, careTreatmentManagerState.detailPane);
    refreshIcons();
    return;
  }
  const paneButton = event.target.closest("[data-care-manager-detail-pane]");
  if (paneButton && item) {
    careTreatmentManagerState.detailPane = paneButton.dataset.careManagerDetailPane;
    careTreatmentManagerState.observationApplicationId = "";
    event.currentTarget.innerHTML = careTreatmentManagerDetailMarkup(item, careTreatmentManagerState.detailPane);
    refreshIcons();
    return;
  }
  const actionButton = event.target.closest("[data-care-manager-detail-action]");
  const action = actionButton?.dataset.careManagerDetailAction;
  if (action === "back") {
    careTreatmentDetailRequestVersion += 1;
    careTreatmentManagerState.mode = "list";
    careTreatmentManagerState.exactDetail = null;
    $("#careTreatmentManagerModal")?.classList.remove("is-detail");
    if ($("#careTreatmentManagerDetail")) $("#careTreatmentManagerDetail").hidden = true;
    renderCareTreatmentManagerPatientSummary();
    renderCareTreatmentManager();
  } else if (action === "schedule") {
    await openCareScheduleFromTreatmentDetail(actionButton, item);
  } else if (action === "observation" && item) {
    careTreatmentManagerState.observationApplicationId = String(actionButton?.dataset.applicationId || "");
    event.currentTarget.innerHTML = careTreatmentManagerDetailMarkup(item, careTreatmentManagerState.detailPane);
    refreshIcons();
    $("[data-care-observation-dialog]", event.currentTarget)?.focus?.();
  } else if (action === "close-observation" && item) {
    careTreatmentManagerState.observationApplicationId = "";
    event.currentTarget.innerHTML = careTreatmentManagerDetailMarkup(item, careTreatmentManagerState.detailPane);
    refreshIcons();
  } else if (action === "refresh-detail" && item) {
    const localView = careTreatmentManagerState.exactDetail?.localView || careTreatmentIsLocalRecord(item);
    careTreatmentManagerState.exactDetail = null;
    const requestVersion = ++careTreatmentDetailRequestVersion;
    event.currentTarget.innerHTML = careTreatmentManagerDetailMarkup(item, careTreatmentManagerState.detailPane, null);
    refreshIcons();
    if (localView) await loadCareInfusions();
    await loadCareTreatmentManagerDrugs(item, requestVersion);
  } else if (event.target.closest("[data-care-infusion-action]") && item) {
    await handleCareInfusionAction(event);
    if (careTreatmentManagerState.exactDetail?.localView && !careTreatmentManagerState.exactDetail.compatibilityFallback) {
      const requestVersion = ++careTreatmentDetailRequestVersion;
      await loadCareTreatmentManagerDrugs(item, requestVersion);
      return;
    }
    if (careTreatmentManagerState.exactDetail?.compatibilityFallback) {
      careTreatmentManagerState.exactDetail = buildCareTreatmentLocalDetail(
        item,
        careTreatmentManagerState.exactDetail.localScheme
      );
    }
    event.currentTarget.innerHTML = careTreatmentManagerDetailMarkup(item, careTreatmentManagerState.detailPane);
    refreshIcons();
  }
}

const CARE_INFUSION_LABELS = {
  planned: "Programada", checked_in: "Admitido", ready: "Lista", in_progress: "En curso", observation: "En observación",
  paused: "Pausada", completed: "Finalizada", cancelled: "Cancelada", not_required: "No requerida",
  pending: "Farmacia pendiente", in_preparation: "En preparación", released: "Liberada",
  not_started: "No iniciada", withheld: "Suspendida"
};

const CARE_INFUSION_TABLE_COLUMNS = [
  { key: "scheduled", label: "Fecha y hora" },
  { key: "chair", label: "Sillon" },
  { key: "scheme", label: "Esquema" },
  { key: "cycle", label: "Ciclo" },
  { key: "clinical", label: "Estado" },
  { key: "pharmacy", label: "Farmacia" },
  { key: "administration", label: "Administracion" }
];

function getCareInfusionNextStep(item) {
  const clinical = careField(item, "clinicalStatus", "clinical_status") || "planned";
  const pharmacy = careField(item, "pharmacyStatus", "pharmacy_status") || "pending";
  const administration = careField(item, "administrationStatus", "administration_status") || "not_started";
  if ([clinical, pharmacy, administration].includes("cancelled") || clinical === "completed") return null;
  if (clinical === "planned") return { label: "Admitir", patch: { clinicalStatus: "checked_in" } };
  if (clinical === "paused") return { label: "Reanudar", patch: { clinicalStatus: "in_progress", administrationStatus: "in_progress" } };
  if (clinical === "checked_in" && pharmacy === "pending") return { label: "Iniciar preparación", patch: { pharmacyStatus: "in_preparation" } };
  if (pharmacy === "in_preparation") return { label: "Preparación lista", patch: { pharmacyStatus: "ready" } };
  if (pharmacy === "ready") return { label: "Liberar a sala", patch: { pharmacyStatus: "released", clinicalStatus: "ready" } };
  if (clinical === "checked_in" && ["not_required", "released"].includes(pharmacy)) return { label: "Lista para administrar", patch: { clinicalStatus: "ready" } };
  if (clinical === "ready") return { label: "Iniciar infusión", patch: { clinicalStatus: "in_progress", administrationStatus: "in_progress" } };
  if (clinical === "in_progress") return { label: "Pasar a observación", patch: { clinicalStatus: "observation", administrationStatus: "completed" } };
  return null;
}

function careHierarchyApplicationsForTreatment(treatmentId) {
  return careInfusions
    .filter((item) => careField(item, "treatmentId", "treatment_id") === String(treatmentId || ""))
    .sort((left, right) => String(careField(left, "scheduledAt", "scheduled_at", "fechaProgramada"))
      .localeCompare(String(careField(right, "scheduledAt", "scheduled_at", "fechaProgramada"))));
}

function careHierarchyApplicationSearchText(item) {
  const clinical = careField(item, "clinicalStatus", "clinical_status");
  const pharmacy = careField(item, "pharmacyStatus", "pharmacy_status");
  const administration = careField(item, "administrationStatus", "administration_status");
  return normalizeSearchText([
    careField(item, "scheduledAt", "scheduled_at", "fechaProgramada"),
    formatCareDateTime(careField(item, "scheduledAt", "scheduled_at", "fechaProgramada")),
    careInfusionScheme(item),
    careField(item, "chair", "sillon"),
    careField(item, "cycleNumber", "cycle_number"),
    clinical, pharmacy, administration,
    CARE_INFUSION_LABELS[clinical], CARE_INFUSION_LABELS[pharmacy], CARE_INFUSION_LABELS[administration]
  ].filter(Boolean).join(" "));
}

function careHierarchyApplicationMatches(item, { query = "", date = "", status = "" } = {}) {
  const scheduled = careField(item, "scheduledAt", "scheduled_at", "fechaProgramada");
  const statuses = [
    careField(item, "clinicalStatus", "clinical_status"),
    careField(item, "pharmacyStatus", "pharmacy_status"),
    careField(item, "administrationStatus", "administration_status")
  ];
  return (!date || careLocalDateKey(scheduled) === date)
    && (!status || statuses.includes(status))
    && (!query || careHierarchyApplicationSearchText(item).includes(query));
}

function careHierarchyTreatmentCycleNumbers(item, applications = []) {
  const initialCycle = Math.max(1, Number(careField(item, "cicloInicial", "initialCycle")) || 1);
  const lastPlannedCycle = Math.max(
    initialCycle,
    Math.min(50, Number(careField(item, "cantidadCiclos", "cycles", "cycleCount")) || initialCycle)
  );
  const numbers = new Set(
    Array.from({ length: lastPlannedCycle - initialCycle + 1 }, (_, index) => initialCycle + index)
  );
  applications.forEach((application) => {
    const number = Number(careField(application, "cycleNumber", "cycle_number"));
    if (Number.isSafeInteger(number) && number > 0 && number <= 50) numbers.add(number);
  });
  return [...numbers].sort((left, right) => left - right);
}

function careHierarchyProjectedCycleDate(item, cycleNumber) {
  const initialCycle = Math.max(1, Number(careField(item, "cicloInicial", "initialCycle")) || 1);
  const firstDate = String(careField(item, "fechaPrimerCiclo", "firstCycleDate") || "").slice(0, 10);
  const cycleDays = Math.max(0, Number(careField(item, "duracionCiclo", "cycleDays", "cycleDuration")) || 0);
  if (!firstDate || cycleNumber < initialCycle) return "";
  if (cycleNumber === initialCycle) return firstDate;
  return cycleDays ? addCareCalendarDays(firstDate, (cycleNumber - initialCycle) * cycleDays) : "";
}

function careHierarchyCycleState(applications) {
  if (!applications.length) {
    return { key: "pending", label: "Pendiente", detail: "Sin turno asignado" };
  }
  const clinicalStatuses = applications.map((application) =>
    careField(application, "clinicalStatus", "clinical_status") || "planned");
  const administrationStatuses = applications.map((application) =>
    careField(application, "administrationStatus", "administration_status") || "not_started");
  const activeIndexes = clinicalStatuses
    .map((status, index) => status === "cancelled" ? -1 : index)
    .filter((index) => index >= 0);
  if (!activeIndexes.length) {
    return { key: "cancelled", label: "Cancelado", detail: "No aplicado" };
  }
  const completedCount = activeIndexes.filter((index) =>
    clinicalStatuses[index] === "completed" || administrationStatuses[index] === "completed").length;
  if (completedCount === activeIndexes.length) {
    return { key: "completed", label: "Aplicado", detail: `${completedCount}/${activeIndexes.length} aplicaciones finalizadas` };
  }
  if (completedCount > 0) {
    return { key: "partial", label: "Aplicación parcial", detail: `${completedCount}/${activeIndexes.length} aplicaciones finalizadas` };
  }
  const statusPriority = ["observation", "in_progress", "ready", "checked_in", "planned", "paused"];
  const clinical = statusPriority.find((status) => clinicalStatuses.includes(status)) || clinicalStatuses[0];
  return {
    key: ["in_progress", "observation", "ready", "checked_in"].includes(clinical) ? "current" : clinical === "paused" ? "paused" : "scheduled",
    label: CARE_INFUSION_LABELS[clinical] || clinical,
    detail: activeIndexes.length === 1 ? "1 aplicación registrada" : `${activeIndexes.length} aplicaciones registradas`
  };
}

function careHierarchyCycleRecords(item, applications, allApplications = applications) {
  const filtered = applications !== allApplications;
  const cycleNumbers = filtered
    ? [...new Set(applications
      .map((application) => Number(careField(application, "cycleNumber", "cycle_number")))
      .filter((number) => Number.isSafeInteger(number) && number > 0))]
      .sort((left, right) => left - right)
    : careHierarchyTreatmentCycleNumbers(item, allApplications);
  return cycleNumbers.map((number) => {
    const cycleApplications = allApplications
      .filter((application) => Number(careField(application, "cycleNumber", "cycle_number")) === number)
      .sort((left, right) => String(careField(left, "scheduledAt", "scheduled_at", "fechaProgramada"))
        .localeCompare(String(careField(right, "scheduledAt", "scheduled_at", "fechaProgramada"))));
    const visibleApplications = filtered
      ? cycleApplications.filter((application) => applications.includes(application))
      : cycleApplications;
    const plannedDate = cycleApplications
      .map((application) => careField(application, "plannedDate", "planned_date"))
      .find(Boolean)
      || careHierarchyProjectedCycleDate(item, number);
    return {
      number,
      applications: visibleApplications,
      state: careHierarchyCycleState(cycleApplications),
      plannedDate,
      plannedDateSource: cycleApplications.some((application) =>
        careField(application, "plannedDate", "planned_date")) ? "Plan actualizado" : "Fecha prevista"
    };
  });
}

function renderCareHierarchyCycleApplication(item, index, total) {
  const id = careField(item, "id", "sessionId");
  const clinical = careField(item, "clinicalStatus", "clinical_status") || "planned";
  const pharmacy = careField(item, "pharmacyStatus", "pharmacy_status") || "pending";
  const administration = careField(item, "administrationStatus", "administration_status") || "not_started";
  const scheduledAt = careField(item, "scheduledAt", "scheduled_at", "fechaProgramada");
  const chair = careField(item, "chair", "sillon");
  const completed = clinical === "completed" || administration === "completed";
  const cancelled = clinical === "cancelled";
  const title = cancelled
    ? "Turno cancelado"
    : completed
      ? "Aplicación realizada"
      : total > 1 ? `Turno ${index + 1}` : "Turno asignado";
  return `<li class="care-hierarchy-cycle-appointment care-cycle-${escapeAttr(cancelled ? "cancelled" : completed ? "completed" : "scheduled")}" data-care-hierarchy-infusion-id="${escapeAttr(id)}">
    <div class="care-hierarchy-cycle-appointment-date">
      <span>${escapeHtml(title)}</span>
      <strong>${escapeHtml(formatCareDateTime(scheduledAt))}</strong>
      <small>${chair ? `Sillón ${escapeHtml(chair)}` : "Sillón sin asignar"}</small>
    </div>
    <dl>
      <div><dt>Estado</dt><dd><span class="care-status-badge care-status-${escapeAttr(clinical)}">${escapeHtml(CARE_INFUSION_LABELS[clinical] || clinical)}</span></dd></div>
      <div><dt>Farmacia</dt><dd>${escapeHtml(CARE_INFUSION_LABELS[pharmacy] || pharmacy)}</dd></div>
      <div><dt>Administración</dt><dd>${escapeHtml(CARE_INFUSION_LABELS[administration] || administration)}</dd></div>
    </dl>
  </li>`;
}

function renderCareHierarchyCycle(record) {
  const actualDates = record.applications
    .map((application) => careField(application, "scheduledAt", "scheduled_at", "fechaProgramada"))
    .filter(Boolean);
  const cycleHeading = {
    pending: "Programación pendiente",
    completed: "Aplicación finalizada",
    partial: "Aplicación parcial",
    current: "Aplicación en seguimiento",
    scheduled: "Turno registrado",
    paused: "Ciclo pausado",
    cancelled: "Ciclo cancelado"
  }[record.state.key] || "Seguimiento del ciclo";
  const actualDateLabel = actualDates.length
    ? actualDates.length === 1
      ? formatCareDateTime(actualDates[0])
      : `${formatCareDateTime(actualDates[0])} · ${actualDates.length} turnos`
    : "Todavía sin turno";
  return `<article class="care-hierarchy-cycle-row care-cycle-${escapeAttr(record.state.key)}" data-care-hierarchy-cycle="${escapeAttr(record.number)}">
    <div class="care-hierarchy-cycle-number"><span>Ciclo</span><strong>${escapeHtml(record.number)}</strong></div>
    <div class="care-hierarchy-cycle-body">
      <header>
        <div><strong>${escapeHtml(cycleHeading)}</strong><small>${escapeHtml(record.state.detail)}</small></div>
        <span class="care-hierarchy-cycle-state">${escapeHtml(record.state.label)}</span>
      </header>
      <dl class="care-hierarchy-cycle-dates">
        <div><dt>${escapeHtml(record.plannedDateSource)}</dt><dd>${record.plannedDate ? `<time datetime="${escapeAttr(record.plannedDate)}">${escapeHtml(careTreatmentHistoryDate(record.plannedDate))}</time>` : "No informada"}</dd></div>
        <div><dt>Turno real</dt><dd>${escapeHtml(actualDateLabel)}</dd></div>
      </dl>
      ${record.applications.length
        ? `<ol class="care-hierarchy-cycle-appointments">${record.applications.map((application, index) =>
          renderCareHierarchyCycleApplication(application, index, record.applications.length)).join("")}</ol>`
        : `<p class="care-hierarchy-cycle-without-appointment"><i data-lucide="calendar-clock"></i><span>Este ciclo todavía no fue asignado a un sillón.</span></p>`}
    </div>
  </article>`;
}

function careHierarchyCycleEffectiveDate(record, last = false) {
  const dates = record.applications
    .map((application) => careField(application, "scheduledAt", "scheduled_at", "fechaProgramada"))
    .filter(Boolean)
    .sort((left, right) => String(left).localeCompare(String(right)));
  return (last ? dates.at(-1) : dates[0]) || record.plannedDate || "";
}

function careHierarchyCycleDateLabel(value) {
  const raw = String(value || "");
  const dateKey = /^\d{4}-\d{2}-\d{2}$/.test(raw) ? raw : careLocalDateKey(value);
  return dateKey ? careTreatmentHistoryDate(dateKey) : "Sin fecha";
}

function renderCareHierarchyTreatment(item, applications, allApplications = applications) {
  const id = careTreatmentManagerRecordId(item);
  const expanded = id && id === careExpandedTreatmentId;
  const visibleCycleRecords = careHierarchyCycleRecords(item, applications, allApplications);
  const allCycleRecords = careHierarchyCycleRecords(item, allApplications, allApplications);
  const scheme = careField(item, "esquema", "scheme") || "Tratamiento sin esquema";
  const diagnosis = careField(item, "diagnostico", "diagnosis") || "Diagnóstico no informado";
  const type = careField(item, "tipo", "type") || "Tratamiento oncológico";
  const oncologist = careField(item, "oncologo", "oncologist", "professional") || "Sin oncólogo informado";
  const status = careField(item, "estadoTratamiento", "status") || "Registrado";
  const completedCycles = allCycleRecords.filter((record) => record.state.key === "completed").length;
  const firstCycleDate = allCycleRecords.length ? careHierarchyCycleEffectiveDate(allCycleRecords[0]) : "";
  const lastCycleDate = allCycleRecords.length
    ? careHierarchyCycleEffectiveDate(allCycleRecords.at(-1), true)
    : "";
  const firstDate = careHierarchyCycleDateLabel(firstCycleDate);
  const lastDate = careHierarchyCycleDateLabel(lastCycleDate);
  const cyclesLabel = `${completedCycles}/${allCycleRecords.length}`;
  const duration = careTreatmentDuration(item);
  return `<article class="care-hierarchy-treatment${expanded ? " is-expanded" : ""}" data-care-hierarchy-treatment-id="${escapeAttr(id)}">
    <header class="care-hierarchy-treatment-summary">
      <button class="care-hierarchy-toggle" type="button" data-care-hierarchy-toggle="${escapeAttr(id)}" aria-expanded="${String(expanded)}" aria-controls="careHierarchyDetail-${escapeAttr(id)}" title="${expanded ? "Ocultar ciclos y aplicaciones" : "Mostrar ciclos y aplicaciones"}"><i data-lucide="${expanded ? "minus" : "plus"}"></i></button>
      <div class="care-hierarchy-treatment-title"><strong>${escapeHtml(scheme)}</strong><span>${escapeHtml(type)} · ${escapeHtml(diagnosis)}</span><small>${escapeHtml(oncologist)}</small></div>
      <div class="care-hierarchy-summary-value"><span>Estado</span><strong>${escapeHtml(status)}</strong></div>
      <div class="care-hierarchy-summary-value"><span>Ciclos</span><strong>${escapeHtml(cyclesLabel)}</strong></div>
      <div class="care-hierarchy-summary-value care-hierarchy-duration" title="${escapeAttr(careTreatmentDurationTitle(item))}"><span>Duración</span><strong><i data-lucide="clock-3"></i>${escapeHtml(duration)}</strong></div>
      <div class="care-hierarchy-summary-value care-hierarchy-date-range"><span>Primera</span><strong>${escapeHtml(firstDate)}</strong><span>Última</span><strong>${escapeHtml(lastDate)}</strong></div>
      ${renderCareTreatmentConsentControl(item, "care-hierarchy-consent-document")}
      <button class="care-hierarchy-view" type="button" data-care-hierarchy-action="detail" data-treatment-id="${escapeAttr(id)}" title="Ver detalle" aria-label="Ver detalle del tratamiento"><i data-lucide="eye"></i></button>
    </header>
    <section class="care-hierarchy-treatment-detail" id="careHierarchyDetail-${escapeAttr(id)}"${expanded ? "" : " hidden"}>
      <div class="care-hierarchy-cycles">
        <div class="care-hierarchy-cycles-heading">
          <div><span>Seguimiento del tratamiento</span><strong>Ciclos y turnos reales</strong></div>
          <b>${completedCycles}/${allCycleRecords.length} aplicados</b>
        </div>
        ${visibleCycleRecords.length
          ? visibleCycleRecords.map(renderCareHierarchyCycle).join("")
          : `<div class="care-hierarchy-empty-application"><i data-lucide="calendar-x"></i><span>No hay ciclos que coincidan con los filtros actuales.</span></div>`}
      </div>
    </section>
  </article>`;
}

function renderCareTreatmentHierarchy() {
  const output = $("#careTreatmentHierarchy");
  if (!output) return;
  const patientId = getActiveLiraPatientId();
  const newTreatment = $("#careHierarchyNewTreatmentBtn");
  if (newTreatment) newTreatment.disabled = !patientId || careBusy;
  if (!patientId) {
    output.innerHTML = `<div class="care-empty-state"><i data-lucide="user-round-search"></i><strong>Seleccione un paciente</strong><span>Abra una historia clínica para consultar tratamientos y aplicaciones.</span></div>`;
    refreshIcons();
    return;
  }

  const query = normalizeSearchText($("#careHierarchySearch")?.value || "");
  const date = $("#careHierarchyDateFilter")?.value || "";
  const status = $("#careHierarchyStatusFilter")?.value || "";
  const hasApplicationFilter = Boolean(date || status);
  const treatments = [...careTreatments].sort((left, right) => careDateSortValue(careField(right, "fechaCreacion", "date", "createdAt"))
    - careDateSortValue(careField(left, "fechaCreacion", "date", "createdAt")));
  const visible = treatments.map((item) => {
    const id = careTreatmentManagerRecordId(item);
    const allApplications = careHierarchyApplicationsForTreatment(id);
    const treatmentMatchesQuery = !query || normalizeSearchText([
      careField(item, "fechaCreacion", "date", "createdAt"),
      careField(item, "tipo", "type"),
      careField(item, "diagnostico", "diagnosis"),
      careField(item, "esquema", "scheme"),
      careField(item, "oncologo", "oncologist", "professional"),
      careField(item, "estadoTratamiento", "status"),
      careField(item, "cantidadCiclos", "cycles", "cycleCount"),
      careField(item, "estadoConsentimiento", "consentStatus")
    ].filter(Boolean).join(" ")).includes(query);
    const applicationMatches = allApplications.filter((application) => careHierarchyApplicationMatches(application, {
      query: treatmentMatchesQuery ? "" : query,
      date,
      status
    }));
    const queryMatches = treatmentMatchesQuery || applicationMatches.length > 0;
    const filterMatches = !hasApplicationFilter || applicationMatches.length > 0;
    return queryMatches && filterMatches ? {
      item,
      applications: hasApplicationFilter || (query && !treatmentMatchesQuery) ? applicationMatches : allApplications,
      allApplications
    } : null;
  }).filter(Boolean);

  if (!visible.length) {
    output.innerHTML = `<div class="care-empty-state"><i data-lucide="search-x"></i><strong>Sin coincidencias</strong><span>No hay tratamientos o aplicaciones para los filtros seleccionados.</span></div>`;
  } else {
    output.innerHTML = visible.map(({ item, applications, allApplications }) => renderCareHierarchyTreatment(item, applications, allApplications)).join("");
  }
  refreshIcons();
}

async function handleCareTreatmentHierarchyAction(event) {
  const infusionAction = event.target.closest("[data-care-hierarchy-infusion-action]");
  if (infusionAction) {
    await handleCareInfusionAction(event);
    return;
  }
  const toggle = event.target.closest("[data-care-hierarchy-toggle]");
  if (toggle) {
    const treatmentId = String(toggle.dataset.careHierarchyToggle || "");
    careExpandedTreatmentId = careExpandedTreatmentId === treatmentId ? "" : treatmentId;
    renderCareTreatmentHierarchy();
    window.requestAnimationFrame(() => $(`[data-care-hierarchy-toggle="${CSS.escape(treatmentId)}"]`)?.focus());
    return;
  }
  const actionButton = event.target.closest("[data-care-hierarchy-action]");
  if (!actionButton) return;
  const treatmentId = String(actionButton.dataset.treatmentId || "");
  const record = careTreatments.find((item) => careTreatmentManagerRecordId(item) === treatmentId);
  if (!record) return;
  careTreatmentManagerState.selectedId = treatmentId;
  if (actionButton.dataset.careHierarchyAction === "detail") openCareTreatmentManagerDetailFromSurface(record, "drugs");
  else if (actionButton.dataset.careHierarchyAction === "schedule") openCareInfusionModal(treatmentId);
}

async function loadCareInfusions() {
  const patientId = getActiveLiraPatientId();
  if (!patientId) return;
  const requestVersion = ++careInfusionRequestVersion;
  careSelectedInfusionId = "";
  renderCareInfusions();
  const params = new URLSearchParams({ patientId });
  try {
    const response = await fetch(`/api/clinical/infusions?${params}`, { cache: "no-store" });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || "No se pudo cargar la agenda");
    if (requestVersion !== careInfusionRequestVersion) return;
    careInfusions = clinicalPayloadItems(payload, ["infusions", "sessions"]);
    renderCareInfusions();
  } catch (error) {
    if (requestVersion !== careInfusionRequestVersion) return;
    renderCareError(error.message || "No se pudo cargar la agenda.", "infusion");
  }
}

function careInfusionScheme(item) {
  const treatmentId = careField(item, "treatmentId", "treatment_id");
  const treatment = [...careTreatments, ...(Array.isArray(state?.treatments) ? state.treatments : [])]
    .find((candidate) => {
      const candidateId = careTreatmentId(candidate);
      return candidateId === treatmentId || candidateId.endsWith(`:${treatmentId}`);
    });
  return careField(item, "scheme", "esquema") || careField(treatment, "esquema", "scheme") || `Tratamiento ${treatmentId}`;
}

function careInfusionSortValue(item, key) {
  if (key === "scheduled") return careField(item, "scheduledAt", "scheduled_at", "fechaProgramada");
  if (key === "chair") return careField(item, "chair", "sillon");
  if (key === "scheme") return careInfusionScheme(item);
  if (key === "cycle") return careField(item, "cycleNumber", "cycle_number");
  if (key === "clinical") {
    const status = careField(item, "clinicalStatus", "clinical_status");
    return CARE_INFUSION_LABELS[status] || status;
  }
  if (key === "pharmacy") {
    const status = careField(item, "pharmacyStatus", "pharmacy_status");
    return CARE_INFUSION_LABELS[status] || status;
  }
  if (key === "administration") {
    const status = careField(item, "administrationStatus", "administration_status");
    return CARE_INFUSION_LABELS[status] || status;
  }
  return "";
}

function renderCareInfusions() {
  const output = $("#careInfusionList");
  if (!output) return;
  const selectedDate = $("#careInfusionDateFilter")?.value || "";
  const selectedStatus = $("#careInfusionStatusFilter")?.value || "";
  const agendaInfusions = careInfusions.filter((item) => {
    const scheduled = careField(item, "scheduledAt", "scheduled_at", "fechaProgramada");
    const statuses = [
      careField(item, "clinicalStatus", "clinical_status"),
      careField(item, "pharmacyStatus", "pharmacy_status"),
      careField(item, "administrationStatus", "administration_status")
    ];
    const matchesDate = !selectedDate || careLocalDateKey(scheduled) === selectedDate;
    const matchesStatus = !selectedStatus || statuses.includes(selectedStatus);
    return matchesDate && matchesStatus;
  });
  const query = normalizeSearchText($("#careInfusionInlineSearch")?.value || $("#careInfusionSearch")?.value || "");
  const filteredInfusions = agendaInfusions.filter((item) => !query || normalizeSearchText([
    careField(item, "scheduledAt", "scheduled_at", "fechaProgramada"),
    formatCareDateTime(careField(item, "scheduledAt", "scheduled_at", "fechaProgramada")),
    careInfusionScheme(item),
    careField(item, "chair", "sillon"),
    careField(item, "cycleNumber", "cycle_number"),
    careField(item, "clinicalStatus", "clinical_status"),
    careField(item, "pharmacyStatus", "pharmacy_status"),
    careField(item, "administrationStatus", "administration_status"),
    CARE_INFUSION_LABELS[careField(item, "clinicalStatus", "clinical_status")],
    CARE_INFUSION_LABELS[careField(item, "pharmacyStatus", "pharmacy_status")],
    CARE_INFUSION_LABELS[careField(item, "administrationStatus", "administration_status")]
  ].join(" ")).includes(query));
  const sortKey = CARE_INFUSION_TABLE_COLUMNS.some((column) => column.key === careInfusionTableState.sortKey)
    ? careInfusionTableState.sortKey
    : "scheduled";
  careInfusionTableState.sortKey = sortKey;
  const sortedInfusions = [...filteredInfusions].sort((left, right) => careInfusionSortValue(left, sortKey).localeCompare(
    careInfusionSortValue(right, sortKey),
    "es",
    { numeric: true, sensitivity: "base" }
  ) * (careInfusionTableState.sortDirection === "desc" ? -1 : 1));
  const infusionSurfaceIsModal = $("#careInfusionSurface")?.classList.contains("is-modal-surface");
  const limit = infusionSurfaceIsModal ? Number($("#careInfusionLength")?.value || 15) : -1;
  const pageSize = limit < 0 ? Math.max(sortedInfusions.length, 1) : limit || 15;
  const pageCount = Math.max(1, Math.ceil(sortedInfusions.length / pageSize));
  careInfusionTableState.page = Math.max(0, Math.min(Number(careInfusionTableState.page) || 0, pageCount - 1));
  const start = limit < 0 ? 0 : careInfusionTableState.page * pageSize;
  const visibleInfusions = limit < 0 ? sortedInfusions : sortedInfusions.slice(start, start + pageSize);
  if (!visibleInfusions.some((item) => careField(item, "id", "sessionId") === careSelectedInfusionId)) careSelectedInfusionId = "";

  const emptyMessage = !getActiveLiraPatientId()
    ? "Seleccione un paciente para abrir sus aplicaciones."
    : "No hay aplicaciones para los filtros seleccionados.";
  const rows = visibleInfusions.length ? visibleInfusions.map((item) => {
    const id = careField(item, "id", "sessionId");
    const clinicalStatus = careField(item, "clinicalStatus", "clinical_status") || "planned";
    const pharmacyStatus = careField(item, "pharmacyStatus", "pharmacy_status") || "pending";
    const administrationStatus = careField(item, "administrationStatus", "administration_status") || "not_started";
    const version = careField(item, "revision", "version") || "1";
    const scheduled = careField(item, "scheduledAt", "scheduled_at", "fechaProgramada");
    const scheme = careInfusionScheme(item);
    const selected = id && id === careSelectedInfusionId;
    return `<tr tabindex="0" data-care-infusion-id="${escapeAttr(id)}" data-version="${escapeAttr(version)}" data-status="${escapeAttr(clinicalStatus)}" class="${selected ? "is-selected" : ""}" aria-selected="${String(selected)}">
      <td>${escapeHtml(formatCareDateTime(scheduled))}</td>
      <td>${escapeHtml(careField(item, "chair", "sillon") || "Sin asignar")}</td>
      <td>${escapeHtml(scheme)}</td>
      <td>${escapeHtml(careField(item, "cycleNumber", "cycle_number") || "—")}</td>
      <td><span class="care-status-badge care-status-${escapeAttr(clinicalStatus)}">${escapeHtml(CARE_INFUSION_LABELS[clinicalStatus] || clinicalStatus)}</span></td>
      <td>${escapeHtml(CARE_INFUSION_LABELS[pharmacyStatus] || pharmacyStatus)}</td>
      <td>${escapeHtml(CARE_INFUSION_LABELS[administrationStatus] || administrationStatus)}</td>
    </tr>`;
  }).join("") : `<tr class="is-empty"><td colspan="7">${escapeHtml(emptyMessage)}</td></tr>`;

  output.innerHTML = `<table class="care-treatment-manager-table care-infusion-table" id="careInfusionTable">
    <thead><tr>${CARE_INFUSION_TABLE_COLUMNS.map((column) => {
      const activeSort = column.key === careInfusionTableState.sortKey;
      const direction = careInfusionTableState.sortDirection;
      return `<th class="is-sortable" scope="col" aria-sort="${activeSort ? direction === "asc" ? "ascending" : "descending" : "none"}"><button type="button" data-care-infusion-sort="${escapeAttr(column.key)}"><span>${escapeHtml(column.label)}</span><i data-lucide="${activeSort ? direction === "asc" ? "arrow-up" : "arrow-down" : "chevrons-up-down"}"></i></button></th>`;
    }).join("")}</tr></thead>
    <tbody>${rows}</tbody>
  </table>`;
  const status = $("#careInfusionTableStatus");
  if (status) status.textContent = visibleInfusions.length
    ? `Mostrando registros del ${start + 1} al ${start + visibleInfusions.length} de un total de ${filteredInfusions.length} registros`
    : "Mostrando registros del 0 al 0 de un total de 0 registros";
  const pagination = $("#careInfusionPagination");
  if (pagination) pagination.innerHTML = `<button type="button" data-care-infusion-page="previous"${careInfusionTableState.page <= 0 ? " disabled" : ""}><i data-lucide="chevron-left"></i><span>Anterior</span></button><span>Página ${careInfusionTableState.page + 1} de ${pageCount}</span><button type="button" data-care-infusion-page="next"${careInfusionTableState.page >= pageCount - 1 ? " disabled" : ""}><span>Siguiente</span><i data-lucide="chevron-right"></i></button>`;
  renderCareInfusionActions();
  renderCareTreatmentHierarchy();
  refreshIcons();
}

function handleCareInfusionTableClick(event) {
  const sort = event.target.closest("[data-care-infusion-sort]");
  if (sort) {
    const sortKey = String(sort.dataset.careInfusionSort || "scheduled");
    if (careInfusionTableState.sortKey === sortKey) {
      careInfusionTableState.sortDirection = careInfusionTableState.sortDirection === "asc" ? "desc" : "asc";
    } else {
      careInfusionTableState.sortKey = sortKey;
      careInfusionTableState.sortDirection = "asc";
    }
    careInfusionTableState.page = 0;
    careSelectedInfusionId = "";
    renderCareInfusions();
    return;
  }
  handleCareInfusionSelection(event);
}

function handleCareInfusionPagination(event) {
  const button = event.target.closest("[data-care-infusion-page]");
  if (!button) return;
  careInfusionTableState.page += button.dataset.careInfusionPage === "next" ? 1 : -1;
  careSelectedInfusionId = "";
  renderCareInfusions();
}

function handleCareInfusionSelection(event) {
  const row = event.target.closest("tr[data-care-infusion-id]");
  if (!row) return;
  const id = String(row.dataset.careInfusionId || "");
  careSelectedInfusionId = careSelectedInfusionId === id ? "" : id;
  renderCareInfusions();
}

function renderCareInfusionActions() {
  const infusion = careInfusions.find((item) => careField(item, "id", "sessionId") === careSelectedInfusionId) || null;
  const nextStep = infusion ? getCareInfusionNextStep(infusion) : null;
  const clinicalStatus = infusion ? careField(infusion, "clinicalStatus", "clinical_status") || "planned" : "";
  const id = infusion ? careField(infusion, "id", "sessionId") : "";
  const version = infusion ? careField(infusion, "revision", "version") || "1" : "";
  const contextDisabled = !getActiveLiraPatientId() || careBusy || careInfusionMutationBusy;
  const advances = $$('[data-care-infusion-action="advance"]');
  const cancels = $$('[data-care-infusion-action="cancel"]');

  advances.forEach((advance) => {
    advance.disabled = contextDisabled || !infusion || !nextStep;
    const label = $("span", advance);
    if (label) label.textContent = nextStep?.label || "Continuar flujo";
    if (id) {
      advance.dataset.infusionId = id;
      advance.dataset.version = version;
    } else {
      delete advance.dataset.infusionId;
      delete advance.dataset.version;
    }
  });
  cancels.forEach((cancel) => {
    cancel.disabled = contextDisabled || !infusion || ["completed", "cancelled"].includes(clinicalStatus);
    if (id) {
      cancel.dataset.infusionId = id;
      cancel.dataset.version = version;
    } else {
      delete cancel.dataset.infusionId;
      delete cancel.dataset.version;
    }
  });
}

function formatCareDateTime(value) {
  const text = String(value || "").trim();
  if (!text) return "Fecha pendiente";
  const parsed = new Date(text);
  if (Number.isNaN(parsed.getTime())) return text;
  return new Intl.DateTimeFormat("es-AR", { dateStyle: "short", timeStyle: "short" }).format(parsed);
}

function careLocalDateKey(value) {
  const parsed = new Date(String(value || ""));
  if (Number.isNaN(parsed.getTime())) return String(value || "").slice(0, 10);
  const year = parsed.getFullYear();
  const month = String(parsed.getMonth() + 1).padStart(2, "0");
  const day = String(parsed.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

async function handleCareInfusionAction(event) {
  const button = event.target.closest("[data-care-infusion-action], [data-care-hierarchy-infusion-action]");
  if (!button || careInfusionMutationBusy) return;
  const action = button.dataset.careInfusionAction || button.dataset.careHierarchyInfusionAction;
  const infusionId = String(button.dataset.infusionId || "");
  const expectedVersion = Number(button.dataset.version);
  const infusion = careInfusions.find((item) => careField(item, "id", "sessionId") === infusionId);
  if (!infusion) return;
  const nextStep = getCareInfusionNextStep(infusion);
  let patch = nextStep?.patch;
  let successLabel = nextStep?.label;
  if (action === "cancel") {
    const reason = window.prompt("Indique el motivo de la cancelación o suspensión:", "");
    if (!String(reason || "").trim()) return;
    patch = { clinicalStatus: "cancelled", pharmacyStatus: "cancelled", administrationStatus: "cancelled", reason: String(reason).trim() };
    successLabel = "Sesión cancelada";
  } else if (action !== "advance" || !patch) return;
  careInfusionMutationBusy = true;
  renderCareInfusionActions();
  try {
    const response = await fetch(`/api/clinical/infusions/${encodeURIComponent(infusionId)}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...patch, expectedVersion })
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || "No se pudo actualizar la sesión");
    await loadCareInfusions();
    if (payload.infusion?.id) {
      const updated = enrichCareQrInfusion(payload.infusion);
      mergeCareQrInfusion(updated);
      if (careScheduleDetailQrScan &&
          String(careScheduleDetailQrScan?.infusion?.id || "") === String(updated.id)) {
        careScheduleDetailQrScan = { ...careScheduleDetailQrScan, infusion: updated };
      }
    }
    renderCareSchedule();
    if (careScheduleDetailInfusionId === infusionId) renderCareScheduleDetail();
    toast(successLabel || "Sesión actualizada");
  } catch (error) {
    toast(error.message || "No se pudo actualizar la sesión");
  } finally {
    careInfusionMutationBusy = false;
    renderCareInfusionActions();
  }
}

async function loadCareDrugs(query) {
  const term = String(query || "").trim();
  const output = $("#careDrugResults");
  if (output) output.innerHTML = `<div class="care-empty-state is-loading"><i data-lucide="loader-circle"></i><strong>Consultando drogas y esquemas</strong></div>`;
  refreshIcons();
  try {
    const [drugResponse, schemeResponse] = await Promise.all([
      fetch(`/api/clinical/drugs?q=${encodeURIComponent(term)}`, { cache: "no-store" }),
      fetch(`/api/clinical/schemes?q=${encodeURIComponent(term)}`, { cache: "no-store" })
    ]);
    const [drugPayload, schemePayload] = await Promise.all([
      drugResponse.json().catch(() => ({})), schemeResponse.json().catch(() => ({}))
    ]);
    if (!drugResponse.ok) throw new Error(drugPayload.error || "No se pudo consultar el catálogo");
    if (!schemeResponse.ok) throw new Error(schemePayload.error || "No se pudieron consultar los esquemas");
    careDrugs = clinicalPayloadItems(drugPayload, ["drugs"]);
    careSchemes = clinicalPayloadItems(schemePayload, ["schemes"]);
    renderCareDrugs();
  } catch (error) {
    renderCareError(error.message || "No se pudo consultar el catálogo.");
  }
}

function renderCareDrugs() {
  const output = $("#careDrugResults");
  if (!output) return;
  const drugs = careDrugs.map((item, index) => ({ kind: "drug", item, index }));
  const schemes = careSchemes.map((item, index) => ({ kind: "scheme", item, index }));
  const rows = [...drugs, ...schemes];
  if ($("#careDrugResultCount")) $("#careDrugResultCount").textContent = String(rows.length);
  if (!rows.length) {
    output.innerHTML = `<div class="care-empty-state"><i data-lucide="pill"></i><strong>Sin resultados</strong><span>Busque por droga, esquema, vía o presentación.</span></div>`;
  } else {
    output.innerHTML = rows.map(({ kind, item, index }) => {
      const title = kind === "drug" ? careField(item, "monodroga", "name", "label") : careField(item, "nombre", "name");
      const detail = kind === "drug"
        ? [`${Array.isArray(item.instructions) ? item.instructions.length : 0} instrucciones`, `${Array.isArray(item.presentations) ? item.presentations.length : 0} presentaciones`].join(" · ")
        : [`Esquema`, `turno ${careTreatmentDuration(item)}`, careField(item, "duracionCiclo", "cycleDuration") ? `cada ${careField(item, "duracionCiclo", "cycleDuration")} días` : "", `${Array.isArray(item.components) ? item.components.length : 0} componentes`].filter(Boolean).join(" · ");
      return `<button type="button" class="care-drug-result" data-care-drug-kind="${kind}" data-care-drug-index="${index}"><span>${kind === "drug" ? "Droga" : "Esquema"}</span><strong>${escapeHtml(title || "Sin nombre")}</strong><small>${escapeHtml(detail)}</small><i data-lucide="chevron-right"></i></button>`;
    }).join("");
  }
  refreshIcons();
}

function handleCareDrugAction(event) {
  const button = event.target.closest("[data-care-drug-kind]");
  if (!button) return;
  const collection = button.dataset.careDrugKind === "scheme" ? careSchemes : careDrugs;
  renderCareDrugDetail(collection[Number(button.dataset.careDrugIndex)], button.dataset.careDrugKind);
  const alignDetail = () => {
    const detail = $("#careDrugDetail");
    const content = detail?.closest(".care-content");
    if (!detail || !content) return;
    const targetTop = content.scrollTop + detail.getBoundingClientRect().top - content.getBoundingClientRect().top - 8;
    content.scrollTop = Math.max(0, targetTop);
  };
  window.requestAnimationFrame(() => {
    alignDetail();
    window.requestAnimationFrame(alignDetail);
  });
}

function isCareInactive(item) {
  const value = item?.active ?? item?.activo;
  return value === false || value === 0 || ["0", "false", "inactive", "inactivo"].includes(String(value).toLowerCase());
}

function careYesNo(value) {
  if (value === null || value === undefined || value === "") return "";
  return value === true || value === 1 || ["1", "true", "si", "sí"].includes(String(value).toLowerCase()) ? "Sí" : "No";
}

function careCatalogRows(rows) {
  const visible = rows.filter(([, value]) => value !== null && value !== undefined && String(value).trim() !== "");
  return visible.length ? `<dl>${visible.map(([label, value]) => `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`).join("")}</dl>` : "";
}

function careCatalogCards(items, title, rowFactory) {
  if (!Array.isArray(items) || !items.length) return "";
  return `<section class="care-catalog-section"><h4>${escapeHtml(title)}</h4><div class="care-catalog-cards">${items.map((item, index) => `<article><strong>${escapeHtml(`${title} ${index + 1}`)}</strong>${careCatalogRows(rowFactory(item))}</article>`).join("")}</div></section>`;
}

function renderCareDrugDetail(item, kind) {
  const output = $("#careDrugDetail");
  if (!output || !item) return;
  const title = kind === "scheme" ? careField(item, "nombre", "name") : careField(item, "monodroga", "name", "label");
  let content;
  if (kind === "scheme") {
    content = careCatalogRows([
      ["Duración operativa estimada", careTreatmentDuration(item)],
      ["Duración del ciclo", careField(item, "duracionCiclo", "cycleDuration")],
      ["Componentes", Array.isArray(item.components) ? item.components.length : careField(item, "componentCount", "cantidadDrogas")],
      ["Estado", isCareInactive(item) ? "Inactivo" : "Activo"]
    ]) + careCatalogCards(item.components, "Componente", (component) => [
      ["Droga", component.drugName],
      ["Día", component.day],
      ["Dosis indicada", component.prescribedDoseText],
      ["Método de cálculo", component.doseCalculationMethod],
      ["Vía", component.route],
      ["Tiempo", component.administrationTime],
      ["Hospital de Día", careYesNo(component.dayHospital)]
    ]);
  } else {
    content = careCatalogRows([
      ["Instrucciones", Array.isArray(item.instructions) ? item.instructions.length : 0],
      ["Presentaciones", Array.isArray(item.presentations) ? item.presentations.length : 0],
      ["Estado", isCareInactive(item) ? "Inactiva" : "Activa"]
    ]) + careCatalogCards(item.instructions, "Instrucción", (instruction) => [
      ["Vía", instruction.route],
      ["Diluyente", instruction.diluent],
      ["Reconstituyente", instruction.reconstituent],
      ["Volumen final", instruction.finalVolume],
      ["Concentración", instruction.concentration],
      ["Fotosensible", careYesNo(instruction.photosensitive)],
      ["Laboratorio", instruction.laboratory],
      ["Guía de infusión", instruction.infusionGuide],
      ["Preparación", instruction.preparationObservations],
      ["Etiqueta", instruction.labelObservations],
      ["Estabilidad en frío", instruction.stability?.refrigerated],
      ["Estabilidad a temperatura ambiente", instruction.stability?.roomTemperature]
    ]) + careCatalogCards(item.presentations, "Presentación", (presentation) => [
      ["Cantidad", presentation.quantity],
      ["Solución", careYesNo(presentation.solution)],
      ["Liofilizado", careYesNo(presentation.lyophilized)],
      ["Reconstituir", careYesNo(presentation.reconstitute)],
      ["Frasco ampolla", careYesNo(presentation.vial)],
      ["Incluye solvente", careYesNo(presentation.includesSolvent)]
    ]);
  }
  output.innerHTML = `<header><small>${kind === "scheme" ? "Esquema terapéutico" : "Ficha de preparación"}</small><strong>${escapeHtml(title || "Sin nombre")}</strong></header>${content}<p class="care-safety-note"><i data-lucide="shield-check"></i>La dosis se muestra sólo como dato del esquema: no se calcula ni modifica desde este catálogo y debe validarse dentro del tratamiento del paciente.</p>`;
  refreshIcons();
}

function showCareModal(id) {
  const modal = $(`#${id}`);
  if (!modal) return;
  modal.hidden = false;
  modal.classList.add("open");
  modal.setAttribute("aria-hidden", "false");
  document.body.classList.add("modal-open");
  window.requestAnimationFrame(() => $("input, select, textarea, button", modal)?.focus());
}

function closeCareModal(id) {
  const modal = id ? $(`#${id}`) : null;
  if (!modal) return;
  modal.classList.remove("open");
  modal.setAttribute("aria-hidden", "true");
  modal.hidden = true;
  if (!$(".care-modal-backdrop.open")) document.body.classList.remove("modal-open");
}

function selectByNames(form, ...names) {
  for (const name of names) {
    const element = form?.elements?.namedItem(name);
    if (element) return element;
  }
  return null;
}

function optionId(item) {
  if (["string", "number"].includes(typeof item)) return String(item);
  return String(item?.id ?? item?.value ?? item?.sourceRecordId ?? "");
}

function optionLabel(item) {
  if (["string", "number"].includes(typeof item)) return String(item);
  return String(item?.nombre ?? item?.label ?? item?.name ?? item?.descripcion ?? item?.value ?? item?.id ?? "");
}

function fillCareSelect(select, items, placeholder = "Seleccione...") {
  if (!(select instanceof HTMLSelectElement)) return 0;
  const previous = select.value;
  const values = Array.isArray(items) ? items : [];
  const activeCount = values.filter((item) => optionId(item) && !isCareInactive(item)).length;
  select.innerHTML = `<option value="">${escapeHtml(placeholder)}</option>${values.map((item) => `<option value="${escapeAttr(optionId(item))}"${isCareInactive(item) ? " disabled" : ""}>${escapeHtml(optionLabel(item))}</option>`).join("")}`;
  if ([...select.options].some((option) => option.value === previous)) select.value = previous;
  else select.value = "";
  select.disabled = activeCount === 0;
  return activeCount;
}

function updateCareTreatmentSubmitAvailability() {
  const form = $("#careTreatmentForm");
  const submit = $("#saveCareTreatmentBtn");
  if (!form || !submit) return;
  const diagnosis = selectByNames(form, "diagnostico", "diagnosis", "diagnosisId");
  const hasDiagnosisOptions = Boolean(diagnosis && [...diagnosis.options]
    .some((option) => option.value && !option.disabled));
  submit.disabled = !clinicalHasPermission("section.prescriptions.edit") ||
    !hasDiagnosisOptions || careTreatmentRequirementsState.status === "loading";
}

function renderCareProtocolCompatibility() {
  const form = $("#careTreatmentForm");
  const warning = $("#careTreatmentProtocolWarning");
  if (!form || !warning) return false;
  const diagnosis = selectByNames(form, "diagnostico", "diagnosis", "diagnosisId")?.selectedOptions?.[0];
  const scheme = selectByNames(form, "esquema", "scheme", "schemeId")?.selectedOptions?.[0];
  const diagnosisGroup = String(diagnosis?.dataset.protocolGroup || "");
  const protocolGroup = String(scheme?.dataset.protocolGroup || "");
  const mismatch = Boolean(diagnosisGroup && protocolGroup && diagnosisGroup !== protocolGroup);
  const confirmation = $("#careTreatmentProtocolMismatchConfirmed");
  const reason = $("#careTreatmentProtocolMismatchReason");
  warning.hidden = !mismatch;
  if (confirmation) confirmation.required = mismatch;
  if (reason) reason.required = mismatch;
  if (!mismatch) {
    if (confirmation) confirmation.checked = false;
    if (reason) reason.value = "";
    return false;
  }
  const diagnosisLabel = diagnosis?.dataset.protocolGroupLabel || "el grupo del diagnóstico";
  const protocolLabel = scheme?.dataset.protocolGroupLabel || "otro grupo";
  const message = $("#careTreatmentProtocolWarningText");
  if (message) {
    message.textContent = `El diagnóstico se reconoce como ${diagnosisLabel}, pero el protocolo corresponde a ${protocolLabel}. Puede continuar si confirma y deja el motivo.`;
  }
  return true;
}

function populateCareForms() {
  const treatmentForm = $("#careTreatmentForm");
  if (treatmentForm && careTreatmentOptions) {
    const options = careTreatmentOptions.options || careTreatmentOptions;
    const diagnoses = options.diagnoses || options.diagnosticos || [];
    fillCareSelect(
      selectByNames(treatmentForm, "diagnostico", "diagnosis", "diagnosisId"),
      diagnoses,
      diagnoses.length ? "Seleccione diagnóstico..." : "Guarde primero un diagnóstico"
    );
    fillCareSelect(selectByNames(treatmentForm, "caracter", "character"), options.characters || options.caracteres, "Seleccione carácter...");
    fillCareSelect(selectByNames(treatmentForm, "tipoOncologico", "type", "treatmentType"), options.treatmentTypes || options.tipos, "Seleccione tipo...");
    fillCareSelect(selectByNames(treatmentForm, "esquema", "scheme", "schemeId"), options.schemes || options.esquemas, "Seleccione esquema...");
    fillCareSelect(selectByNames(treatmentForm, "estadoConsentimiento", "consent", "consentStatus"), options.consentStates || options.consentimientos, "Seleccione consentimiento...");
    const diagnosisSelect = selectByNames(treatmentForm, "diagnostico", "diagnosis", "diagnosisId");
    for (const diagnosis of diagnoses) {
      const option = [...(diagnosisSelect?.options || [])].find((item) => item.value === optionId(diagnosis));
      if (!option) continue;
      option.dataset.protocolGroup = String(diagnosis.protocolGroup || "");
      option.dataset.protocolGroupLabel = String(diagnosis.protocolGroupLabel || "");
    }
    const schemeSelect = selectByNames(treatmentForm, "esquema", "scheme", "schemeId");
    for (const scheme of options.schemes || options.esquemas || []) {
      const option = [...(schemeSelect?.options || [])].find((item) => item.value === optionId(scheme));
      if (!option) continue;
      option.dataset.cycleDays = String(scheme.duracionCiclo || scheme.cycleDays || "");
      option.dataset.durationMinutes = String(scheme.estimatedDurationMinutes || scheme.durationMinutes || "");
      option.dataset.durationText = String(scheme.estimatedDurationText || "");
      option.dataset.protocolGroup = String(scheme.protocolGroup || "");
      option.dataset.protocolGroupLabel = String(scheme.protocolGroupLabel || "");
    }
    renderCareProtocolCompatibility();
    updateCareTreatmentSubmitAvailability();
  }
  const infusionForm = $("#careInfusionForm");
  if (infusionForm) {
    const treatmentSelect = selectByNames(infusionForm, "treatmentId", "tratamiento", "treatment");
    fillCareSelect(treatmentSelect, careTreatments.map((item) => ({
      id: careTreatmentId(item),
      nombre: careField(item, "esquema", "scheme") || `Tratamiento ${careTreatmentId(item)}`
    })), "Seleccione tratamiento...");
  }
}

function renderCareTreatmentHistory() {
  const output = $("#careTreatmentHistoryList");
  const count = $("#careTreatmentHistoryCount");
  if (!output || !count) return;
  const rows = [...careTreatments].sort((left, right) =>
    (careDateSortValue(careField(right, "createdAt", "fechaCreacion", "date")) || 0) -
    (careDateSortValue(careField(left, "createdAt", "fechaCreacion", "date")) || 0));
  count.textContent = `${rows.length} ${rows.length === 1 ? "registrado" : "registrados"}`;
  output.innerHTML = rows.map((item) => {
    const scheme = careField(item, "scheme", "esquema") || "Esquema no informado";
    const diagnosis = careField(item, "diagnosis", "diagnostico") || "Diagnóstico no informado";
    const status = careField(item, "status", "estado") || "Registrado";
    const date = careField(item, "createdAt", "fechaCreacion", "date");
    const cycles = Number(careField(item, "cycleCount", "cantidadCiclos", "totalCycles")) || 0;
    return `<article><div><strong>${escapeHtml(scheme)}</strong><small>${escapeHtml(diagnosis)}</small></div><span>${escapeHtml(status)}</span><time>${escapeHtml(careTreatmentHistoryDate(date))}</time><b>${cycles ? `${cycles} ${cycles === 1 ? "ciclo" : "ciclos"}` : "Ciclos no informados"}</b></article>`;
  }).join("") || `<div class="care-empty-state care-empty-state--compact"><i data-lucide="history"></i><span>Este paciente todavía no tiene tratamientos registrados.</span></div>`;
  refreshIcons();
}

function addCareCalendarDays(value, days) {
  const date = new Date(`${value}T12:00:00`);
  if (Number.isNaN(date.getTime())) return "";
  date.setDate(date.getDate() + Number(days || 0));
  return careScheduleDateValue(date);
}

function careTreatmentHistoryDate(value) {
  const text = String(value || "").trim();
  const iso = text.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (iso) return `${iso[3]}/${iso[2]}/${iso[1]}`;
  const local = text.match(/^(\d{1,2})[/-](\d{1,2})[/-](\d{4})/);
  if (local) return `${local[1].padStart(2, "0")}/${local[2].padStart(2, "0")}/${local[3]}`;
  return text || "Sin fecha";
}

function renderCareTreatmentProjection() {
  const form = $("#careTreatmentForm");
  const output = $("#careTreatmentProjection");
  if (!form || !output) return;
  const schemeSelect = selectByNames(form, "esquema", "scheme", "schemeId");
  const option = schemeSelect?.selectedOptions?.[0];
  const firstDate = treatmentFormValue(form, "fechaPrimerCiclo");
  const cycleDays = Math.max(0, Number(option?.dataset.cycleDays) || 0);
  const durationMinutes = Math.max(0, Number(option?.dataset.durationMinutes) || 0);
  const totalCycles = Math.max(0, Number(treatmentFormValue(form, "cantidadCiclos", "cycles", "cycleCount")) || 0);
  const initialCycle = Math.max(1, Number(treatmentFormValue(form, "cicloInicial", "initialCycle")) || 1);
  if (!option?.value || !firstDate || !cycleDays || !totalCycles) {
    output.innerHTML = `<div class="care-empty-state care-empty-state--compact"><i data-lucide="calendar-range"></i><span>Seleccione un esquema, cantidad de ciclos y fecha para ver la proyección.</span></div>`;
    refreshIcons();
    return;
  }
  const projectedCount = Math.max(0, totalCycles - initialCycle + 1);
  const visibleCount = Math.min(projectedCount, 12);
  const dates = Array.from({ length: visibleCount }, (_, index) => {
    const cycle = initialCycle + index;
    const date = addCareCalendarDays(firstDate, index * cycleDays);
    return `<span><b>C${cycle}</b><time datetime="${escapeAttr(date)}">${escapeHtml(careScheduleDateLabel(date))}</time></span>`;
  }).join("");
  output.innerHTML = `<header><div><strong>Calendario estimado</strong><small>Cada ${cycleDays} días${durationMinutes ? ` · ${escapeHtml(careScheduleDurationLabel(durationMinutes))} de sillón por aplicación` : ""}</small></div><i data-lucide="calendar-range"></i></header><div class="care-treatment-projection-dates">${dates}</div>${projectedCount > visibleCount ? `<p>Se proyectaron ${projectedCount} ciclos; se muestran los primeros ${visibleCount}.</p>` : ""}<p>Estas fechas son orientativas. Farmacia puede confirmar la logística y el turnero asigna el horario y sillón definitivos.</p>`;
  refreshIcons();
}

function openCareTreatmentModal() {
  if (!clinicalHasPermission("section.prescriptions.edit")) {
    toast("Su rol no permite prescribir tratamientos.");
    return;
  }
  if (!getActiveLiraPatientId()) {
    openLiraImportModal();
    toast("Seleccione un paciente antes de crear el tratamiento");
    return;
  }
  const form = $("#careTreatmentForm");
  form?.reset();
  if (form) {
    delete form.dataset.clinicalEntryId;
    form.dataset.clinicalEntryId = makeId("treatment-entry");
  }
  populateCareForms();
  const firstCycleDate = $("#careTreatmentFirstCycleDate");
  if (firstCycleDate && !firstCycleDate.value) firstCycleDate.value = careScheduleDateValue();
  renderCareTreatmentHistory();
  renderCareTreatmentProjection();
  careTreatmentRequirementsState = { requestId: careTreatmentRequirementsState.requestId + 1, status: "idle", schemeId: "" };
  const requirements = $("#careTreatmentRequirementList");
  if (requirements) requirements.innerHTML = `<div class="care-empty-state care-empty-state--compact"><i data-lucide="list-checks"></i><span>Seleccione un esquema para ver sus requisitos.</span></div>`;
  $("#careTreatmentRequirements")?.setAttribute("aria-busy", "false");
  updateCareTreatmentSubmitAvailability();
  showCareModal("careTreatmentModal");
  refreshIcons();
}

function treatmentFormValue(form, ...names) {
  return String(selectByNames(form, ...names)?.value || "").trim();
}

function treatmentEvolutionValue(source, ...keys) {
  for (const key of keys) {
    const value = source?.[key];
    if (value !== null && value !== undefined && String(value).trim() !== "") {
      return String(value).trim();
    }
  }
  return "";
}

function treatmentFormSelectedLabel(form, ...names) {
  const control = selectByNames(form, ...names);
  if (!control || !control.value || !control.selectedOptions) return "";
  return String(control.selectedOptions?.[0]?.textContent || "").trim();
}

function buildTreatmentEvolutionEntry({
  treatmentId,
  form = null,
  fields = {},
  createdAt = new Date().toISOString(),
  entryId = ""
} = {}) {
  const stableTreatmentId = String(treatmentId || fields.id || "").trim();
  if (!stableTreatmentId) throw new Error("El tratamiento no tiene un identificador estable.");
  const calculation = fields.calculation && typeof fields.calculation === "object"
    ? fields.calculation
    : fields.datosCalculo && typeof fields.datosCalculo === "object"
      ? fields.datosCalculo
      : fields;
  const diagnosis = treatmentEvolutionValue(fields, "diagnosis", "diagnosticoLabel") ||
    treatmentFormSelectedLabel(form, "diagnostico", "diagnosis", "diagnosisId") ||
    treatmentEvolutionValue(fields, "diagnostico");
  const character = treatmentEvolutionValue(fields, "intent", "caracterLabel") ||
    treatmentFormSelectedLabel(form, "caracter", "character") ||
    treatmentEvolutionValue(fields, "caracter", "character");
  const treatmentType = treatmentEvolutionValue(fields, "type", "tipoLabel") ||
    treatmentFormSelectedLabel(form, "tipoOncologico", "type", "treatmentType") ||
    treatmentEvolutionValue(fields, "tipoOncologico", "treatmentType", "tipo");
  const scheme = treatmentEvolutionValue(fields, "scheme", "esquemaLabel") ||
    treatmentFormSelectedLabel(form, "esquema", "scheme", "schemeId") ||
    treatmentEvolutionValue(fields, "esquema");
  const consent = treatmentEvolutionValue(fields, "consentStatus", "estadoConsentimientoLabel") ||
    treatmentFormSelectedLabel(form, "estadoConsentimiento", "consent", "consentStatus") ||
    treatmentEvolutionValue(fields, "estadoConsentimiento");
  const cycles = treatmentEvolutionValue(fields, "cycles", "cantidadCiclos", "cycleCount");
  const initialCycle = treatmentEvolutionValue(fields, "initialCycle", "cicloInicial");
  const firstCycleDate = treatmentEvolutionValue(fields, "firstCycleDate", "fechaPrimerCiclo");
  const observations = treatmentEvolutionValue(fields, "observations", "observaciones", "notes");
  const weight = treatmentEvolutionValue(calculation, "peso", "weightKg", "weight");
  const height = treatmentEvolutionValue(calculation, "talla", "heightCm", "heightM", "height");
  const anthropometrics = calculateAnthropometrics(weight, height);
  const suppliedBodySurface = treatmentEvolutionValue(calculation, "superficieCorporal", "supCorporal", "bodySurface");
  const bodySurface = anthropometrics.bodySurface > 0
    ? round(anthropometrics.bodySurface, 3)
    : suppliedBodySurface;
  const lines = [
    "Alta de tratamiento oncológico.",
    diagnosis && `Diagnóstico: ${diagnosis}`,
    character && `Carácter: ${character}`,
    treatmentType && `Tipo de tratamiento: ${treatmentType}`,
    scheme && `Esquema: ${scheme}`,
    cycles && `Ciclos previstos: ${cycles}`,
    initialCycle && `Ciclo inicial: ${initialCycle}`,
    firstCycleDate && `Fecha prevista del primer ciclo: ${formatDateOptional(firstCycleDate)}`,
    consent && `Consentimiento: ${consent}`,
    weight && `Peso: ${weight} kg`,
    anthropometrics.heightCm > 0 && `Talla: ${anthropometrics.heightCm} cm`,
    anthropometrics.bmi > 0 && `IMC: ${round(anthropometrics.bmi, 2)} kg/m2`,
    bodySurface && `Superficie corporal: ${bodySurface} m2`,
    treatmentEvolutionValue(calculation, "edad", "age") &&
      `Edad usada para cálculo: ${treatmentEvolutionValue(calculation, "edad", "age")} años`,
    treatmentEvolutionValue(calculation, "creatinina", "creatinine") &&
      `Creatinina: ${treatmentEvolutionValue(calculation, "creatinina", "creatinine")}`,
    treatmentEvolutionValue(calculation, "tfg", "gfr") &&
      `TFG: ${treatmentEvolutionValue(calculation, "tfg", "gfr")}`,
    treatmentEvolutionValue(calculation, "targetAUC", "targetAuc") &&
      `Target AUC: ${treatmentEvolutionValue(calculation, "targetAUC", "targetAuc")}`,
    treatmentEvolutionValue(calculation, "calcio", "calcium") &&
      `Calcio: ${treatmentEvolutionValue(calculation, "calcio", "calcium")}`,
    treatmentEvolutionValue(calculation, "albumina", "albumin") &&
      `Albúmina: ${treatmentEvolutionValue(calculation, "albumina", "albumin")}`,
    treatmentEvolutionValue(calculation, "calcioCorregido", "correctedCalcium") &&
      `Calcio corregido: ${treatmentEvolutionValue(calculation, "calcioCorregido", "correctedCalcium")}`,
    fields.requirementsConfirmed === true || fields.requirementsConfirmed === "true"
      ? "Datos requeridos verificados: Sí"
      : "",
    fields.protocolMismatchConfirmed === true || fields.protocolMismatchConfirmed === "true"
      ? `Excepción diagnóstico-protocolo: ${treatmentEvolutionValue(fields, "protocolMismatchReason")}`
      : "",
    observations && `Observaciones: ${observations}`
  ].filter(Boolean);
  const clinicalDate = treatmentEvolutionValue(fields, "date", "createdDate") ||
    String(createdAt || "").slice(0, 10) || today();
  const stableEntryId = String(
    entryId || fields.clinicalEntryId || fields.treatmentEntryId || ""
  ).trim();
  const author = treatmentEvolutionValue(fields, "oncologist", "oncologo") ||
    state.meta.currentUser || "Profesional";
  const audit = buildAuditStamp("cargado", {
    at: createdAt,
    lastName: extractLastName(author)
  });
  return {
    id: `treatment-evolution-${stableTreatmentId.replace(/[^a-z0-9_.:-]/gi, "-")}`,
    date: clinicalDate,
    datePrecision: "day",
    author,
    reason: "Alta de tratamiento oncológico",
    specialty: "Oncología",
    text: lines.join("\n"),
    highlighted: true,
    immutable: true,
    attachments: [],
    linkedStudyIds: [],
    sourceRef: {
      kind: "oncological-treatment",
      treatmentId: stableTreatmentId,
      clinicalEntryId: stableEntryId
    },
    audit,
    createdAt: audit.at,
    updatedAt: audit.at
  };
}

function ensureTreatmentEvolutionEntry(options = {}) {
  const suppliedEntry = options.entry && typeof options.entry === "object" ? options.entry : null;
  const treatmentId = String(
    options.treatmentId || suppliedEntry?.sourceRef?.treatmentId || options.fields?.id || ""
  ).trim();
  const entryId = String(
    options.entryId || suppliedEntry?.sourceRef?.clinicalEntryId ||
    options.fields?.clinicalEntryId || options.fields?.treatmentEntryId || ""
  ).trim();
  state.evolutions = Array.isArray(state.evolutions) ? state.evolutions : [];
  const suppliedEvolutionId = String(suppliedEntry?.id || "").trim();
  const existingIndex = state.evolutions.findIndex((item) =>
    String(item?.sourceRef?.treatmentId || "") === treatmentId ||
    (entryId && String(item?.sourceRef?.clinicalEntryId || "") === entryId) ||
    (suppliedEvolutionId && String(item?.id || "") === suppliedEvolutionId) ||
    String(item?.id || "") === `treatment-evolution-${treatmentId.replace(/[^a-z0-9_.:-]/gi, "-")}`
  );
  if (existingIndex >= 0 && !suppliedEntry) {
    return { entry: state.evolutions[existingIndex], added: false, replaced: false };
  }
  const entry = suppliedEntry ? {
    ...suppliedEntry,
    immutable: true,
    sourceRef: {
      ...(suppliedEntry.sourceRef || {}),
      kind: suppliedEntry.sourceRef?.kind || "oncological-treatment",
      treatmentId: treatmentId || suppliedEntry.sourceRef?.treatmentId || "",
      clinicalEntryId: entryId || suppliedEntry.sourceRef?.clinicalEntryId || ""
    }
  } : buildTreatmentEvolutionEntry(options);
  if (existingIndex >= 0) {
    state.evolutions.splice(existingIndex, 1, entry);
    return { entry, added: false, replaced: true };
  }
  state.evolutions.unshift(entry);
  return { entry, added: true, replaced: false };
}

async function reconcileTreatmentEvolutionEntries(treatments = []) {
  let added = 0;
  for (const treatment of Array.isArray(treatments) ? treatments : []) {
    const entryId = String(treatment?.clinicalEntryId || "").trim();
    const treatmentId = String(treatment?.id || "").trim();
    if (!entryId || !treatmentId) continue;
    const result = ensureTreatmentEvolutionEntry({
      treatmentId,
      entryId,
      fields: treatment,
      createdAt: treatment.createdAt || new Date().toISOString()
    });
    if (result.added) added += 1;
  }
  if (!added) return true;
  state.meta.updatedAt = new Date().toISOString();
  invalidateAiTimeline();
  storeClinicalStateLocally();
  renderPreview();
  renderPatientOutputs();
  const persisted = await persistClinicalState({ silent: true });
  if (!persisted) {
    console.warn(clinicalLocalCacheAllowed()
      ? "Los tratamientos quedaron como evoluciones en el respaldo local, pero falta confirmar la ficha."
      : "No se confirmó el guardado de las evoluciones del tratamiento en la base clínica.");
  }
  return persisted;
}

async function renderCareTreatmentRequirements() {
  const form = $("#careTreatmentForm");
  const output = $("#careTreatmentRequirementList");
  const fieldset = $("#careTreatmentRequirements");
  const submit = $("#saveCareTreatmentBtn");
  const patientId = getActiveLiraPatientId();
  const schemeId = treatmentFormValue(form, "esquema", "scheme", "schemeId");
  const requestId = careTreatmentRequirementsState.requestId + 1;
  careTreatmentRequirementsState = { requestId, status: schemeId ? "loading" : "idle", schemeId };
  if (!form || !output || !patientId || !schemeId) {
    if (output) output.innerHTML = `<div class="care-empty-state care-empty-state--compact"><i data-lucide="list-checks"></i><span>Seleccione un esquema para ver sus requisitos.</span></div>`;
    fieldset?.setAttribute("aria-busy", "false");
    updateCareTreatmentSubmitAvailability();
    refreshIcons();
    return;
  }
  output.innerHTML = `<p class="care-inline-loading"><i data-lucide="loader-circle"></i>Revisando datos de cálculo requeridos...</p>`;
  fieldset?.setAttribute("aria-busy", "true");
  if (submit) submit.disabled = true;
  refreshIcons();
  try {
    const response = await fetch(`/api/clinical/patients/${encodeURIComponent(patientId)}/treatment-requirements/${encodeURIComponent(schemeId)}`, { cache: "no-store" });
    const requirementPayload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(requirementPayload.error || "No se pudieron consultar los requisitos");
    if (requestId !== careTreatmentRequirementsState.requestId || treatmentFormValue(form, "esquema", "scheme", "schemeId") !== schemeId) return;
    const requirement = requirementPayload.requirements || requirementPayload;
    const field = (name, label, value = "", attributes = "") => `<label class="care-requirement-field"><span>${escapeHtml(label)}</span><input name="${name}" type="number" value="${escapeAttr(value ?? "")}" ${attributes}></label>`;
    const fields = [];
    if (requirement.hayPeso || requirement.hayTalla || requirement.hayCalvert) fields.push(field("peso", "Peso (kg)", requirement.peso, 'min="0.01" step="0.01" required'));
    if (requirement.hayTalla) fields.push(field("talla", "Talla (cm)", requirement.talla, 'min="1" step="0.01" required'));
    if (requirement.hayCalvert) {
      fields.push(field("edad", "Edad", requirement.edad, 'min="1" max="129" required'));
      fields.push(field("creatinina", "Creatinina", requirement.creatinina, 'min="0.01" step="0.01" required'));
      fields.push(field("tfg", "TFG", "", 'min="0.01" step="0.01" required'));
      fields.push(field("targetAUC", "Target AUC", "", 'min="2" max="8" step="0.01" required'));
    }
    if (requirement.hayCalcioAlbumina) {
      fields.push(field("calcio", "Calcio", requirement.calcio, 'min="0.01" step="0.01" required'));
      fields.push(field("albumina", "Albúmina", requirement.albumina, 'min="0.01" step="0.01" required'));
    }
    careTreatmentRequirementsState = { requestId, status: "ready", schemeId };
    output.innerHTML = fields.length
      ? `<div class="care-requirements-summary"><i data-lucide="circle-alert"></i><span>Complete los datos obligatorios indicados por el esquema.</span></div><div class="care-requirements-grid">${fields.join("")}</div><label class="care-requirements-confirmation"><input id="careTreatmentRequirementsConfirmed" type="checkbox" data-care-requirements-confirm><span><strong>Datos verificados</strong><small>Confirmo que revisé estos valores antes de crear el tratamiento.</small></span></label>`
      : `<p class="care-requirements-ok"><i data-lucide="circle-check"></i>Este esquema no requiere datos de cálculo adicionales y puede guardarse.</p>`;
    refreshIcons();
  } catch (error) {
    if (requestId !== careTreatmentRequirementsState.requestId) return;
    careTreatmentRequirementsState = { requestId, status: "error", schemeId };
    output.innerHTML = `<div class="care-requirements-error"><i data-lucide="triangle-alert"></i><span>${escapeHtml(error.message || "No se pudieron consultar los requisitos.")}</span><button class="tool-button" type="button" data-care-requirements-retry><i data-lucide="refresh-cw"></i><span>Reintentar</span></button></div>`;
    refreshIcons();
  } finally {
    if (requestId === careTreatmentRequirementsState.requestId) {
      fieldset?.setAttribute("aria-busy", "false");
      updateCareTreatmentSubmitAvailability();
    }
  }
}

async function submitCareTreatment(event) {
  event.preventDefault();
  if (!clinicalHasPermission("section.prescriptions.edit")) {
    toast("Su rol no permite prescribir tratamientos.");
    return;
  }
  const form = event.currentTarget;
  const patientId = getActiveLiraPatientId();
  if (!patientId) return;
  renderCareProtocolCompatibility();
  if (!form.reportValidity()) return;
  const diagnosisSelect = selectByNames(form, "diagnostico", "diagnosis", "diagnosisId");
  const selectedDiagnosisId = String(diagnosisSelect?.value || "");
  const validDiagnosisIds = new Set([...(diagnosisSelect?.options || [])]
    .filter((option) => option.value && !option.disabled)
    .map((option) => option.value));
  if (!selectedDiagnosisId || !validDiagnosisIds.has(selectedDiagnosisId)) {
    toast("Seleccione un diagnóstico guardado del paciente");
    diagnosisSelect?.focus();
    return;
  }
  const schemeId = treatmentFormValue(form, "esquema", "scheme", "schemeId");
  if (careTreatmentRequirementsState.schemeId !== schemeId || careTreatmentRequirementsState.status === "loading") {
    toast("Espere a que se carguen los requisitos del esquema");
    return;
  }
  if (careTreatmentRequirementsState.status === "error") {
    toast("Reintente la consulta de requisitos antes de guardar");
    $("[data-care-requirements-retry]", form)?.focus();
    return;
  }
  const requirementsConfirmation = $("[data-care-requirements-confirm]", form);
  if (requirementsConfirmation && !requirementsConfirmation.checked) {
    toast("Confirme que verificó los requisitos del esquema");
    requirementsConfirmation.focus();
    return;
  }
  const fields = Object.fromEntries(new FormData(form).entries());
  const treatmentEntryId = String(form.dataset.clinicalEntryId || makeId("treatment-entry"));
  form.dataset.clinicalEntryId = treatmentEntryId;
  const anthropometrics = calculateAnthropometrics(fields.peso, fields.talla);
  const body = {
    ...fields,
    patientId,
    idPaciente: patientId,
    diagnostico: selectedDiagnosisId,
    caracter: fields.caracter || fields.character,
    tipoOncologico: fields.tipoOncologico || fields.type || fields.treatmentType,
    esquema: fields.esquema || fields.scheme || fields.schemeId,
    cantidadCiclos: fields.cantidadCiclos || fields.cycles || fields.cycleCount,
    cicloInicial: fields.cicloInicial || fields.initialCycle,
    estadoConsentimiento: fields.estadoConsentimiento || fields.consent || fields.consentStatus,
    observaciones: fields.observaciones || fields.notes || "",
    supCorporal: anthropometrics.bodySurface > 0 ? round(anthropometrics.bodySurface, 3) : "",
    requirementsConfirmed: !requirementsConfirmation || requirementsConfirmation.checked,
    protocolMismatchConfirmed: fields.protocolMismatchConfirmed === "true",
    protocolMismatchReason: fields.protocolMismatchReason || "",
    clinicalEntryId: treatmentEntryId
  };
  const submit = $('button[type="submit"]', form);
  if (submit) submit.disabled = true;
  try {
    const response = await fetch(`/api/clinical/patients/${encodeURIComponent(patientId)}/treatments`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Request-Id": `treatment:${treatmentEntryId}`
      },
      body: JSON.stringify(body)
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || "No se pudo crear el tratamiento");
    const treatmentId = String(payload.id || payload.treatment?.id || "").trim();
    if (!treatmentId) throw new Error("La base clinica no devolvio el tratamiento creado");
    const hasAtomicEvolutionContract = ["evolution", "evolutionCreated", "documentRevision"]
      .some((key) => Object.prototype.hasOwnProperty.call(payload, key));
    if (hasAtomicEvolutionContract) {
      const documentRevision = Number(payload.documentRevision);
      if (!payload.evolution || typeof payload.evolution !== "object") {
        throw new Error("La base clinica no devolvio la evolucion atomica del tratamiento");
      }
      if (!Number.isSafeInteger(documentRevision) || documentRevision < 1) {
        throw new Error("La base clinica no devolvio una revision valida de la historia");
      }
      ensureTreatmentEvolutionEntry({
        treatmentId,
        entryId: treatmentEntryId,
        entry: payload.evolution
      });
      state.meta.persistenceRevision = documentRevision;
      state.meta.updatedAt = payload.evolution.updatedAt || payload.evolution.createdAt ||
        payload.evolution.date || new Date().toISOString();
      invalidateAiTimeline();
      renderPreview();
      renderPatientOutputs();
    } else {
      // Compatibilidad con motores anteriores al contrato atómico tratamiento + evolución.
      const persistedTreatment = payload.treatment && typeof payload.treatment === "object"
        ? payload.treatment
        : body;
      const { added } = ensureTreatmentEvolutionEntry({
        treatmentId,
        entryId: treatmentEntryId,
        fields: persistedTreatment,
        form: payload.treatment ? null : form,
        createdAt: payload.createdAt || new Date().toISOString()
      });
      if (added) {
        state.meta.updatedAt = new Date().toISOString();
        invalidateAiTimeline();
        storeClinicalStateLocally();
        renderPreview();
        renderPatientOutputs();
      }
      const clinicalPersisted = await persistClinicalState({ silent: true });
      if (!clinicalPersisted) {
        toast(getClinicalPersistenceFailureMessage(
          "El tratamiento se creo, pero falta confirmar su evolucion en la ficha. Reintente Guardar."
        ));
        return;
      }
    }
    closeCareModal("careTreatmentModal");
    await refreshCareWorkspace({ force: true });
    toast(payload.idempotent
      ? "Tratamiento y evolucion confirmados sin duplicar"
      : "Tratamiento creado y agregado como evolucion");
  } catch (error) {
    toast(error.message || "No se pudo crear el tratamiento");
  } finally {
    updateCareTreatmentSubmitAvailability();
  }
}

function openCareInfusionModal(treatmentId = "") {
  if (!getActiveLiraPatientId()) {
    openLiraImportModal();
    toast("Seleccione un paciente antes de programar una infusión");
    return;
  }
  const form = $("#careInfusionForm");
  form?.reset();
  populateCareForms();
  const treatment = selectByNames(form, "treatmentId", "tratamiento", "treatment");
  if (treatmentId && treatment) treatment.value = treatmentId;
  const date = selectByNames(form, "date", "fecha");
  const time = selectByNames(form, "time", "hora");
  const next = new Date(Date.now() + 24 * 60 * 60 * 1000);
  next.setMinutes(0, 0, 0);
  const local = new Date(next.getTime() - next.getTimezoneOffset() * 60000).toISOString();
  if (date && !date.value) date.value = local.slice(0, 10);
  if (time && !time.value) time.value = local.slice(11, 16);
  careInfusionModalReturnFocus = document.activeElement;
  showCareModal("careInfusionModal");
}

function closeCareInfusionModal() {
  const modal = $("#careInfusionModal");
  if (!modal?.classList.contains("open")) return;
  const returnFocus = careInfusionModalReturnFocus;
  careInfusionModalReturnFocus = null;
  closeCareModal("careInfusionModal");
  if (returnFocus?.isConnected) window.requestAnimationFrame(() => returnFocus.focus());
}

async function submitCareInfusion(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const patientId = getActiveLiraPatientId();
  if (!patientId) return;
  const fields = Object.fromEntries(new FormData(form).entries());
  const scheduledAt = fields.scheduledAt || fields.fechaHora || (fields.date && fields.time ? new Date(`${fields.date}T${fields.time}:00`).toISOString() : "");
  const body = {
    ...fields,
    patientId,
    treatmentId: fields.treatmentId || fields.tratamiento || fields.treatment,
    cycleNumber: Number(fields.cycleNumber || fields.ciclo || fields.cycle),
    scheduledAt,
    chair: fields.chair || fields.sillon || "",
    notes: fields.notes || fields.notas || "",
    medications: []
  };
  const submit = $('button[type="submit"]', form);
  if (submit) submit.disabled = true;
  try {
    const response = await fetch("/api/clinical/infusions", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || "No se pudo programar la infusión");
    const detailWasOpen = $("#careTreatmentManagerModal")?.classList.contains("is-detail");
    const detailRecord = detailWasOpen ? selectedCareTreatmentManagerRecord() : null;
    const detailPane = careTreatmentManagerState.detailPane;
    closeCareInfusionModal();
    setCareView("infusion", { refresh: false });
    await loadCareInfusions();
    if (detailRecord && $("#careTreatmentManagerModal")?.classList.contains("open")) {
      openCareTreatmentManagerDetail(detailRecord, detailPane);
    }
    toast("Ciclo programado en Hospital de Dia");
  } catch (error) {
    toast(error.message || "No se pudo programar la infusión");
  } finally {
    if (submit) submit.disabled = false;
  }
}

function setLiraSearchEnabled(enabled) {
  const input = $("#liraPatientSearchInput");
  const button = $("#liraPatientSearchBtn");
  if (input) input.disabled = !enabled || liraImportBusy;
  if (button) button.disabled = !enabled || liraImportBusy;
}

async function searchLiraPatients(rawQuery) {
  const query = String(rawQuery || "").trim();
  if (!liraSourceAvailable) {
    renderLiraSearchState("La base clinica no esta disponible. Vuelva a comprobar la conexion.");
    return;
  }
  if (liraImportBusy) return;
  window.clearTimeout(liraImportSearchTimer);
  liraImportSearchController?.abort();
  liraImportSearchController = new AbortController();
  clearLiraPatientSelection();
  setLiraSearchBusy(true);
  renderLiraSearchState(query ? "Buscando pacientes..." : "Cargando pacientes recientes...", { clearResults: true });
  try {
    const response = await fetch(`/api/lira/patients?q=${encodeURIComponent(query)}`, {
      cache: "no-store",
      signal: liraImportSearchController.signal
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok === false) throw new Error(payload.error || "No se pudo completar la busqueda");
    const candidates = Array.isArray(payload.items)
      ? payload.items
      : Array.isArray(payload.patients)
        ? payload.patients
        : Array.isArray(payload.results)
          ? payload.results
          : [];
    liraImportResults = candidates.filter((patient) => getLiraPatientId(patient));
    renderLiraPatientResults(liraImportResults);
    const total = Number.isFinite(Number(payload.total)) ? Number(payload.total) : liraImportResults.length;
    renderLiraSearchState(liraImportResults.length
      ? query
        ? `${total} ${total === 1 ? "paciente encontrado" : "pacientes encontrados"}. Seleccione uno para revisar la disponibilidad.`
        : `${total} ${total === 1 ? "paciente reciente" : "pacientes recientes"}. Escriba para filtrar o seleccione uno.`
      : query ? "No se encontraron pacientes con esos datos." : "Todavía no hay pacientes cargados.", { clearResults: false });
  } catch (error) {
    if (error.name === "AbortError") return;
    liraImportResults = [];
    renderLiraSearchState(error.message || "No se pudo buscar en la base clinica.");
  } finally {
    setLiraSearchBusy(false);
  }
}

function setLiraSearchBusy(busy) {
  const results = $("#liraPatientResults");
  const button = $("#liraPatientSearchBtn");
  results?.setAttribute("aria-busy", String(busy));
  if (button) {
    button.disabled = busy || liraImportBusy || !liraSourceAvailable;
    $("span", button).textContent = busy ? "Buscando..." : "Buscar";
  }
}

function renderLiraSearchState(message, { clearResults = true } = {}) {
  const status = $("#liraPatientSearchMessage");
  if (status) status.textContent = message;
  if (clearResults) {
    const results = $("#liraPatientResults");
    if (results) results.innerHTML = "";
    liraImportResults = [];
  }
}

function renderLiraPatientResults(patients) {
  const results = $("#liraPatientResults");
  if (!results) return;
  results.innerHTML = patients.map((patient, index) => {
    const id = getLiraPatientId(patient);
    const name = getLiraPatientName(patient);
    const meta = getLiraPatientMeta(patient).join(" - ");
    return `
      <button class="lira-patient-result" type="button" role="option" tabindex="${index === 0 ? "0" : "-1"}" aria-selected="false" data-lira-patient-id="${escapeAttr(id)}">
        <span class="lira-patient-result-copy">
          <strong>${escapeHtml(name)}</strong>
          <small>${escapeHtml(meta || `ID ${id}`)}</small>
        </span>
        <i data-lucide="chevron-right" aria-hidden="true"></i>
      </button>`;
  }).join("");
  refreshIcons();
}

function handleLiraResultsKeydown(event) {
  const current = event.target.closest("[data-lira-patient-id]");
  if (!current) return;
  const results = $$('[data-lira-patient-id]', $("#liraPatientResults"));
  const index = results.indexOf(current);
  let nextIndex = index;
  if (event.key === "ArrowDown") nextIndex = Math.min(index + 1, results.length - 1);
  else if (event.key === "ArrowUp") nextIndex = Math.max(index - 1, 0);
  else if (event.key === "Home") nextIndex = 0;
  else if (event.key === "End") nextIndex = results.length - 1;
  else if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    selectLiraPatient(current.dataset.liraPatientId);
    return;
  } else return;
  event.preventDefault();
  results.forEach((result, resultIndex) => { result.tabIndex = resultIndex === nextIndex ? 0 : -1; });
  results[nextIndex]?.focus();
}

async function selectLiraPatient(patientId) {
  const id = String(patientId || "");
  if (!id || liraImportBusy) return;
  liraImportSelectedPatientId = id;
  liraImportSelectedImportable = false;
  $$('[data-lira-patient-id]', $("#liraPatientResults")).forEach((button) => {
    const selected = button.dataset.liraPatientId === id;
    button.classList.toggle("is-selected", selected);
    button.setAttribute("aria-selected", String(selected));
    button.tabIndex = selected ? 0 : -1;
  });
  const patient = liraImportResults.find((item) => getLiraPatientId(item) === id) || { id };
  renderLiraPreviewLoading(patient);
  const confirmButton = $("#confirmLiraImportBtn");
  if (confirmButton) confirmButton.disabled = true;
  liraImportPreviewController?.abort();
  liraImportPreviewController = new AbortController();
  try {
    const response = await fetch(`/api/lira/patients/${encodeURIComponent(id)}/preview`, {
      cache: "no-store",
      signal: liraImportPreviewController.signal
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok === false) throw new Error(payload.error || "No se pudo preparar la vista previa");
    if (liraImportSelectedPatientId !== id) return;
    const preview = payload.preview || payload.patientPreview || payload;
    const previewCompleteness = preview.completeness ?? payload.completeness;
    liraImportSelectedImportable = preview.importable !== false
      && preview.available !== false
      && previewCompleteness?.importable !== false
      && previewCompleteness?.available !== false;
    renderLiraPreview({
      patient: preview.patient || payload.patient || patient,
      counts: preview.counts || payload.counts || {},
      completeness: previewCompleteness,
      warnings: preview.warnings || payload.warnings || [],
      importable: liraImportSelectedImportable
    });
    if (confirmButton) confirmButton.disabled = !liraImportSelectedImportable;
  } catch (error) {
    if (error.name === "AbortError") return;
    if (liraImportSelectedPatientId !== id) return;
    renderLiraPreviewError(error.message || "No se pudo revisar esta historia.");
  }
}

function clearLiraPatientSelection() {
  liraImportPreviewController?.abort();
  liraImportSelectedPatientId = "";
  liraImportSelectedImportable = false;
  $$('[data-lira-patient-id]', $("#liraPatientResults")).forEach((button) => {
    button.classList.remove("is-selected");
    button.setAttribute("aria-selected", "false");
  });
  const preview = $("#liraPatientPreview");
  if (preview) preview.hidden = true;
  const confirmButton = $("#confirmLiraImportBtn");
  if (confirmButton) confirmButton.disabled = true;
}

function renderLiraPreviewLoading(patient) {
  const preview = $("#liraPatientPreview");
  if (!preview) return;
  preview.hidden = false;
  $("#liraPatientPreviewTitle").textContent = getLiraPatientName(patient);
  $("#liraPatientPreviewMeta").textContent = getLiraPatientMeta(patient).join(" - ");
  $("#liraCompletenessBadge").textContent = "Comprobando";
  $("#liraCompletenessBadge").className = "lira-completeness-badge is-loading";
  $("#liraPreviewCounts").innerHTML = `<span class="lira-preview-loading"><i data-lucide="loader-circle"></i>Revisando historia local...</span>`;
  $("#liraPreviewAvailability").innerHTML = "";
  $("#liraPreviewWarnings").hidden = true;
  refreshIcons();
}

function renderLiraPreview({ patient, counts, completeness, warnings, importable }) {
  const preview = $("#liraPatientPreview");
  if (!preview) return;
  preview.hidden = false;
  $("#liraPatientPreviewTitle").textContent = getLiraPatientName(patient);
  $("#liraPatientPreviewMeta").textContent = getLiraPatientMeta(patient).join(" - ");
  const info = getLiraCompletenessInfo(completeness);
  const badge = $("#liraCompletenessBadge");
  badge.textContent = info.label;
  badge.className = `lira-completeness-badge ${info.tone}`;
  const countEntries = getLiraCountEntries(counts);
  $("#liraPreviewCounts").innerHTML = countEntries.length
    ? countEntries.map(([label, value]) => `<span><strong>${escapeHtml(value)}</strong><small>${escapeHtml(label)}</small></span>`).join("")
    : `<span class="lira-preview-no-counts"><small>La historia esta disponible; el detalle se ordenara al abrirla.</small></span>`;
  const availability = $("#liraPreviewAvailability");
  availability.className = `lira-preview-availability ${importable ? "is-ready" : "is-error"}`;
  availability.innerHTML = `
    <i data-lucide="${importable ? "circle-check" : "triangle-alert"}" aria-hidden="true"></i>
    <div><strong>${importable ? "Lista para abrir" : "Historia incompleta o no disponible"}</strong><small>${escapeHtml(info.detail)}</small></div>`;
  const warningList = $("#liraPreviewWarnings");
  const safeWarnings = Array.isArray(warnings) ? warnings.filter(Boolean).slice(0, 8) : [];
  warningList.hidden = !safeWarnings.length;
  warningList.innerHTML = safeWarnings.map((warning) => `<li>${escapeHtml(typeof warning === "string" ? warning : warning.message || warning.label || "Advertencia de disponibilidad")}</li>`).join("");
  refreshIcons();
}

function renderLiraPreviewError(message) {
  const badge = $("#liraCompletenessBadge");
  badge.textContent = "No disponible";
  badge.className = "lira-completeness-badge is-error";
  $("#liraPreviewCounts").innerHTML = "";
  const availability = $("#liraPreviewAvailability");
  availability.className = "lira-preview-availability is-error";
  availability.innerHTML = `<i data-lucide="triangle-alert" aria-hidden="true"></i><div><strong>No se pudo abrir la historia</strong><small>${escapeHtml(message)}</small></div>`;
  $("#liraPreviewWarnings").hidden = true;
  const confirmButton = $("#confirmLiraImportBtn");
  if (confirmButton) confirmButton.disabled = true;
  refreshIcons();
}

function getLiraCompletenessInfo(completeness) {
  let percent = null;
  let label = "Disponible";
  let detail = "Se conservaran los registros clinicos disponibles y su procedencia local.";
  let missing = [];
  if (typeof completeness === "number" || (typeof completeness === "string" && /^\d+(?:\.\d+)?%?$/.test(completeness.trim()))) {
    percent = Number.parseFloat(completeness);
  } else if (completeness && typeof completeness === "object") {
    percent = firstFiniteNumber(completeness.percent, completeness.percentage, completeness.score, completeness.value);
    if (typeof completeness.label === "string") label = completeness.label;
    else if (typeof completeness.status === "string") label = completeness.status;
    missing = Array.isArray(completeness.missing) ? completeness.missing : Array.isArray(completeness.missingSources) ? completeness.missingSources : [];
    if (typeof completeness.message === "string") detail = completeness.message;
  } else if (typeof completeness === "string" && completeness.trim()) {
    label = completeness.trim();
  }
  if (Number.isFinite(percent)) {
    if (percent <= 1) percent *= 100;
    percent = Math.max(0, Math.min(100, Math.round(percent)));
    label = `${percent}% disponible`;
  }
  if (missing.length) detail = `${detail} Faltantes informados: ${missing.map(formatLiraCountLabel).join(", ")}.`;
  const tone = ((Number.isFinite(percent) && percent < 100) || missing.length) ? "is-partial" : "is-ready";
  return { label, detail, tone };
}

function getLiraCountEntries(counts) {
  if (!counts || typeof counts !== "object" || Array.isArray(counts)) return [];
  return Object.entries(counts)
    .filter(([, value]) => Number.isFinite(Number(value)))
    .slice(0, 12)
    .map(([key, value]) => [formatLiraCountLabel(key), String(Number(value))]);
}

function formatLiraCountLabel(value) {
  const labels = {
    diagnoses: "diagnosticos", treatments: "tratamientos", oncologyTreatments: "tratamientos oncologicos",
    exams: "examenes", studies: "estudios", evolutions: "evoluciones", antecedents: "antecedentes",
    support: "registros de soporte", scales: "escalas", attachments: "adjuntos", timelineEvents: "eventos clinicos",
    records: "registros", totalRows: "registros totales"
  };
  const key = String(value || "");
  if (labels[key]) return labels[key];
  return key.replace(/([a-z])([A-Z])/g, "$1 $2").replaceAll("_", " ").trim().toLowerCase();
}

function getLiraPatientId(patient) {
  return String(patient?.id ?? patient?.patientId ?? patient?.idPaciente ?? patient?.idLocal ?? "").trim();
}

function getLiraPatientName(patient) {
  const explicit = patient?.fullName || patient?.nombreCompleto || patient?.displayName;
  if (hasText(explicit)) return String(explicit).trim();
  const lastName = String(patient?.apellido || patient?.lastName || "").trim();
  const firstName = String(patient?.nombre || patient?.firstName || "").trim();
  return [lastName, firstName].filter(Boolean).join(", ") || "Paciente sin nombre";
}

function getLiraPatientMeta(patient) {
  const id = getLiraPatientId(patient);
  const dni = patient?.numeroDocumento ?? patient?.dni ?? patient?.documentNumber;
  const medicalRecord = patient?.numeroHC ?? patient?.medicalRecord ?? patient?.hc;
  return [
    hasText(dni) ? `DNI ${dni}` : "",
    hasText(medicalRecord) ? `HC ${medicalRecord}` : "",
    id ? `ID ${id}` : ""
  ].filter(Boolean).map(String);
}

function firstFiniteNumber(...values) {
  for (const value of values) {
    if (value === null || value === undefined || value === "") continue;
    const number = Number(value);
    if (Number.isFinite(number)) return number;
  }
  return NaN;
}

async function importSelectedLiraPatient() {
  const patientId = liraImportSelectedPatientId;
  if (!patientId || liraImportBusy) return;
  const previousState = state;
  const previousStorageKey = getClinicalStorageKey(previousState);
  const previousStoredState = clinicalLocalCacheAllowed() ? localStorage.getItem(previousStorageKey) : null;
  let serverImportCompleted = false;
  setLiraImportBusy(true);
  try {
    const response = await fetch(`/api/lira/patients/${encodeURIComponent(patientId)}/import`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{}"
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok === false) throw new Error(payload.error || "No se pudo abrir la historia completa");
    serverImportCompleted = true;
    clinicalContextVersion += 1;
    timelineAiLoading = false;
    agentBusy = false;
    setAgentBusy(false);
    await loadState({ forceServer: true });
    const importedPatientId = String(state?.meta?.liraImport?.patientId || "");
    const importedPatientLiraId = String(state?.patient?.liraId || "");
    if (importedPatientId !== patientId || importedPatientLiraId !== patientId) {
      throw new Error("La importacion termino, pero la identidad de la historia recargada no coincide con el paciente seleccionado.");
    }
    resetPatientContextAfterLiraImport();
    renderAll();
    setLiraImportBusy(false);
    closeLiraImportModal({ force: true });
    setRightTab("care");
    if (payload.normalization?.usedLlm && payload.normalization?.status === "completed") {
      toast("Historia clinica cargada y organizada");
    } else if (payload.normalization?.usedLlm && payload.normalization?.status === "reviewed") {
      toast("Historia clinica cargada y revisada");
    } else {
      toast("Historia clinica abierta desde la base local");
    }
  } catch (error) {
    if (serverImportCompleted) {
      toast("La historia se abrio, pero no pudo recargarse. HCOP volvera a intentarlo.");
      window.setTimeout(() => window.location.reload(), 900);
    } else {
      state = previousState;
      if (clinicalLocalCacheAllowed()) {
        if (previousStoredState) localStorage.setItem(previousStorageKey, previousStoredState);
        else localStorage.removeItem(previousStorageKey);
      }
      toast(error.message || "No se pudo abrir la historia completa");
    }
  } finally {
    setLiraImportBusy(false);
  }
}

async function refreshActiveLiraPatient() {
  const patientId = getActiveLiraPatientId();
  if (!patientId || liraPatientRefreshBusy) {
    if (!patientId) toast("Abra primero un paciente");
    return;
  }
  const previousState = state;
  setLiraPatientRefreshBusy(true);
  try {
    if (activePatientIsLocal()) {
      const response = await fetch(`/api/clinical/patients/${encodeURIComponent(patientId)}/activate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        cache: "no-store",
        body: "{}"
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok || payload.ok === false) {
        throw new Error(payload.error || "No se pudo recargar la ficha local");
      }
      const reloaded = normalizeState(payload.state || payload.document?.document);
      if (String(reloaded?.meta?.liraImport?.patientId || "") !== patientId ||
          String(reloaded?.meta?.liraImport?.origin || "") !== "local") {
        throw new Error("La ficha recuperada no corresponde al paciente activo");
      }
      state = reloaded;
      resetPatientContextAfterLiraImport();
      renderAll();
      await refreshCareWorkspace({ force: true });
      toast("Ficha recargada desde la base local");
      return;
    }
    const response = await fetch(`/api/lira/patients/${encodeURIComponent(patientId)}/refresh`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-HCOP-Refresh": "current-patient"
      },
      body: "{}"
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok === false) throw new Error(payload.error || "No se pudo actualizar la historia desde la base clinica");
    await loadState({ forceServer: true });
    if (getActiveLiraPatientId() !== patientId || String(state?.patient?.liraId || "") !== patientId) {
      state = previousState;
      throw new Error("La actualizacion no corresponde al paciente que estaba abierto");
    }
    resetPatientContextAfterLiraImport();
    renderAll();
    await refreshCareWorkspace({ force: true });
    toast("Historia actualizada desde la base clinica");
  } catch (error) {
    state = previousState;
    renderAll();
    toast(error.message || "No se pudo actualizar la historia desde la base clinica");
  } finally {
    setLiraPatientRefreshBusy(false);
  }
}

function setLiraPatientRefreshBusy(busy) {
  liraPatientRefreshBusy = busy;
  const button = $("#refreshActiveLiraPatientBtn");
  if (!button) return;
  const hasPatient = Boolean(getActiveLiraPatientId());
  button.disabled = busy || !hasPatient;
  button.classList.toggle("is-loading", busy);
  button.setAttribute("aria-busy", String(busy));
  button.title = busy
    ? "Actualizando toda la informacion desde la base clinica"
    : hasPatient
      ? "Actualizar toda la informacion de este paciente desde la base clinica"
      : "Abra primero un paciente";
  button.setAttribute("aria-label", button.title);
}

function syncActivePatientCloseButton() {
  const button = $("#closeActivePatientBtn");
  if (!button) return;
  const patientId = getActiveLiraPatientId();
  const patientName = String(state?.patient?.fullName || "").trim();
  const hasPatient = Boolean(patientId);
  button.disabled = closeActivePatientBusy || !hasPatient;
  button.classList.toggle("is-loading", closeActivePatientBusy);
  button.setAttribute("aria-busy", String(closeActivePatientBusy));
  button.title = closeActivePatientBusy
    ? "Cerrando paciente"
    : hasPatient
      ? `Cerrar paciente${patientName ? `: ${patientName}` : ""}`
      : "No hay un paciente abierto";
  button.setAttribute("aria-label", button.title);
}

async function closeActivePatient() {
  const patientId = getActiveLiraPatientId();
  if (!patientId || closeActivePatientBusy) return;
  const patientName = String(state?.patient?.fullName || "este paciente").trim() || "este paciente";
  if (!window.confirm(`¿Cerrar la ficha de ${patientName}?\n\nLa información guardada no se elimina y podrá volver a abrirla cuando la necesite.`)) {
    return;
  }

  closeActivePatientBusy = true;
  syncActivePatientCloseButton();
  try {
    await clinicalPersistenceQueue;
    const response = await fetch("/api/auth/active-patient", {
      method: "PUT",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ patientId: null })
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok === false) {
      throw new Error(payload.error || "No se pudo cerrar el paciente");
    }

    await loadState({ forceServer: true });
    resetPatientContextAfterLiraImport();
    syncClinicalActorToState();
    setActiveTab("historia");
    setRightTab("studies");
    renderAll();
    await refreshCareWorkspace({ force: true });
    toast("Paciente cerrado. La hoja quedó en blanco.");
  } catch (error) {
    toast(error.message || "No se pudo cerrar el paciente");
  } finally {
    closeActivePatientBusy = false;
    syncActivePatientCloseButton();
  }
}

function setLiraImportBusy(busy) {
  liraImportBusy = busy;
  const confirmButton = $("#confirmLiraImportBtn");
  const closeButton = $("#closeLiraImportBtn");
  const cancelButton = $("#cancelLiraImportBtn");
  if (confirmButton) {
    confirmButton.disabled = busy || !liraImportSelectedPatientId || !liraImportSelectedImportable;
    $("span", confirmButton).textContent = busy ? "Abriendo historia..." : "Abrir historia completa";
    const icon = $("i, svg", confirmButton);
    if (icon) icon.setAttribute("data-lucide", busy ? "loader-circle" : "file-down");
  }
  if (closeButton) closeButton.disabled = busy;
  if (cancelButton) cancelButton.disabled = busy;
  $("#liraImportModal")?.classList.toggle("is-busy", busy);
  $("#liraImportModal")?.setAttribute("aria-busy", String(busy));
  $$('[data-lira-patient-id]', $("#liraPatientResults")).forEach((button) => { button.disabled = busy; });
  setLiraSearchEnabled(liraSourceAvailable);
  refreshIcons();
}

function resetPatientContextAfterLiraImport() {
  clinicalContextVersion += 1;
  careRequestVersion += 1;
  careInfusionRequestVersion += 1;
  careTreatmentDetailRequestVersion += 1;
  careTreatments = [];
  careInfusions = [];
  careTreatmentOptions = null;
  careTreatmentWorkflowOptions = null;
  careTreatmentCollections = { oncological: [], nonOncological: [], procedures: [], referrals: [] };
  careTreatmentManagerState = {
    ...careTreatmentManagerState,
    tab: "oncological",
    query: "",
    page: 0,
    sortColumn: 0,
    sortDirection: "desc",
    selectedId: "",
    mode: "list",
    detailPane: "drugs",
    detailCycle: 0,
    exactDetail: null,
    observationApplicationId: ""
  };
  careInfusionTableState = { page: 0, sortKey: "scheduled", sortDirection: "asc" };
  careSelectedInfusionId = "";
  window.clearTimeout(saveTimer);
  window.clearTimeout(timelineSearchTimer);
  window.clearTimeout(clinicalPeriodFocusTimer);
  window.clearTimeout(timelinePeriodFocusTimer);
  window.clearTimeout(liraImportSearchTimer);
  window.clearTimeout(ajcc8StageTimer);
  window.clearTimeout(diagnosticClassificationSearchTimer);
  window.clearTimeout(diagnosticAjccStageTimer);
  window.cancelAnimationFrame(timelineControlsSyncFrame);
  saveTimer = null;
  timelineSearchTimer = null;
  clinicalPeriodFocusTimer = null;
  timelinePeriodFocusTimer = null;
  timelineControlsSyncFrame = null;
  liraImportSearchTimer = null;
  ajcc8StageTimer = null;
  diagnosticClassificationSearchTimer = null;
  diagnosticAjccStageTimer = null;
  diagnosticClassificationUi.patientKey = "";
  diagnosticEquivalences = null;
  resetDiagnosticAjccUiFromState();
  selectedStudyId = null;
  pendingStudyFile = null;
  studyUploadBatchVersion += 1;
  studyUploadBusy = false;
  pendingStudyUploads = [];
  studyUploadPersistenceError = "";
  studyTemplateSelectedId = "";
  studyTemplateBusy = false;
  closePrescriptionPreview();
  prescriptionPreviewId = null;
  pendingSystemicDraft = null;
  systemicFormRequestId += 1;
  setSystemicFormBusy(false);
  editingEvolutionId = null;
  editingSectionKey = null;
  printTimestamp = null;
  activeStudyImageMenuId = "";
  activeStudyImageContext = null;
  studyImageModalReturnFocus = null;
  evolutionDraftAttachments = [];
  annotationMode = false;
  annotationBaseImage = null;
  resetAnnotationLayerCanvas();
  annotationCommands = [];
  activeAnnotationStroke = null;
  timelineFilters.clear();
  timelineMilestonesOnly = false;
  timelineSearchQuery = "";
  timelineAiLoading = false;
  timelineAiAttempted = false;
  timelineAiError = "";
  timelineSelection = null;
  pendingClinicalSelection = [];
  ajcc8Site = null;
  agentBusy = false;
  setAgentBusy(false);
  clearAgentChat();
  closePatientModal();
  closeEvolutionModal();
  closeDiagnosticClassificationModal({ restoreFocus: false, discardDraft: true });
  closeSectionEditModal();
  closeSectionHistoryModal();
  closePrescriptionPreview();
  closeGuidePdf();
  closeStudyUploadModal({ force: true });
  closeStudyTemplateModal({ force: true });
  closeStudyImageModal();
  liraImportSearchController?.abort();
  liraImportPreviewController?.abort();
  liraImportSearchController = null;
  liraImportPreviewController = null;
  clearLiraPatientSelection();
  renderLiraSearchState("Escriba un dato para comenzar.");
  const agentInput = $("#agentChatInput");
  const clinicalSearch = $("[data-clinical-search]");
  const studySearch = $("#studySearch");
  const studyTypeFilter = $("#studyTypeFilter");
  const liraSearch = $("#liraPatientSearchInput");
  const careInfusionSearch = $("#careInfusionSearch");
  const careInfusionDate = $("#careInfusionDateFilter");
  const careInfusionStatus = $("#careInfusionStatusFilter");
  if (agentInput) agentInput.value = "";
  if (clinicalSearch) clinicalSearch.value = "";
  if (studySearch) studySearch.value = "";
  if (studyTypeFilter) studyTypeFilter.value = "";
  if (liraSearch) liraSearch.value = "";
  if (careInfusionSearch) careInfusionSearch.value = "";
  if (careInfusionDate) careInfusionDate.value = "";
  if (careInfusionStatus) careInfusionStatus.value = "";
  fillCareSelect($("#careTreatmentDiagnosis"), [], "Guarde primero un diagnóstico");
  updateCareTreatmentSubmitAvailability();
  $$('[data-care-treatment-role="search"]').forEach((input) => { input.value = ""; });
  if ($("#sectionEditTextarea")) $("#sectionEditTextarea").value = "";
  if ($("#sectionEditReason")) $("#sectionEditReason").value = "";
  if ($("#sectionStudiesRows")) $("#sectionStudiesRows").innerHTML = "";
  if ($("#sectionHistoryList")) $("#sectionHistoryList").innerHTML = "";
  if ($("#prescriptionPdfPreview")) $("#prescriptionPdfPreview").innerHTML = "";
  if ($("#studyImageCaption")) $("#studyImageCaption").textContent = "";
  $("#studyImageModalImage")?.removeAttribute("src");
  if ($("#guidePdfFrame")) $("#guidePdfFrame").src = "about:blank";
  if ($("#tnmPrimarySite")) $("#tnmPrimarySite").value = "";
  resetAjcc8Selectors();
  clearEvolutionForm();
  resetStudyForm();
  resetResearchForm();
  renderCareTreatments();
  renderCareInfusions();
}

function handleBindableEdit(event) {
  if (!event.target.matches("[data-field]")) return;
  syncBindableSiblings(event.target);
  collectBindableFields();
  touchSectionAuditForField(event.target.dataset.field);
  updateDerivedFields();
  renderPatientOutputs(true);
  renderPreview();
  queueLocalSave();
}

function openPatientModal() {
  $("#patientModal").classList.add("open");
  $("#patientModal").setAttribute("aria-hidden", "false");
  $('[data-field="patient.fullName"]', $("#patientModal"))?.focus();
}

function closePatientModal() {
  const modal = $("#patientModal");
  if (!modal.classList.contains("open")) return;
  modal.classList.remove("open");
  modal.setAttribute("aria-hidden", "true");
}

function openEvolutionModal(mode = "evolucion", entry = null, options = {}) {
  if (entry?.immutable) {
    toast("Esta evolucion fue generada por un tratamiento y no se puede modificar");
    return;
  }
  if (entry) {
    editingEvolutionId = entry.id;
    $("#evolutionDate").value = entry.date || today();
    $("#evolutionAuthor").value = entry.author || state.meta.currentUser || "";
    $("#evolutionReason").value = entry.reason || "";
    $("#evolutionText").value = entry.text || "";
    $("#evolutionHighlighted").checked = Boolean(entry.highlighted || entry.featured || entry.destacada);
    evolutionDraftAttachments = normalizeEvolutionAttachments(entry.attachments).map((attachment) => ({ ...attachment }));
  } else {
    editingEvolutionId = null;
    setupDraftDefaults();
    evolutionDraftAttachments = options.attachment ? [normalizeEvolutionAttachment(options.attachment)] : [];
  }

  setEvolutionMode("evolucion");
  updateEvolutionModalState();
  renderEvolutionAttachmentBand();
  $("#evolutionModal").classList.add("open");
  $("#evolutionModal").setAttribute("aria-hidden", "false");
  $("#evolutionText").focus();
}

function closeEvolutionModal() {
  const modal = $("#evolutionModal");
  if (!modal.classList.contains("open")) return;
  modal.classList.remove("open");
  modal.setAttribute("aria-hidden", "true");
}

function invalidateDiagnosticClassificationDraft() {
  window.clearTimeout(diagnosticClassificationSearchTimer);
  window.clearTimeout(diagnosticAjccStageTimer);
  diagnosticClassificationSearchTimer = null;
  diagnosticAjccStageTimer = null;
  diagnosticClassificationUi.patientKey = "";
  diagnosticClassificationUi.selectionRequestId += 1;
  DIAGNOSTIC_CLASSIFICATION_SYSTEMS.forEach((system) => {
    diagnosticClassificationUi.requestId[system] += 1;
    diagnosticClassificationUi.loading[system] = false;
  });
  diagnosticAjccUi.requestId += 1;
  diagnosticAjccUi.stageRequestId += 1;
  diagnosticAjccUi.loading = false;
  diagnosticAjccUi.calculating = false;
}

function openDiagnosticClassificationModal(trigger = null) {
  if (!getActiveLiraPatientId()) {
    toast("Cree o abra un paciente antes de agregar el diagnóstico");
    return;
  }
  const modal = $("#diagnosticClassificationModal");
  const body = $("#diagnosticClassificationModalBody");
  if (!modal || !body) return;
  diagnosticClassificationModalReturnFocus = trigger || document.activeElement;
  prepareDiagnosticClassificationDraftForNewEntry();
  body.innerHTML = renderDiagnosticClassificationSection({ editable: true });
  modal.classList.add("open");
  modal.setAttribute("aria-hidden", "false");
  refreshIcons();
  ensureDiagnosticDisplaySettings();
  ensureDiagnosticClassificationCatalogs();
  ensureDiagnosticAjccSite();
  window.requestAnimationFrame(() => {
    $('[data-diagnostic-select="ajcc"]', modal)?.focus();
  });
}

function closeDiagnosticClassificationModal({ restoreFocus = true, discardDraft = true } = {}) {
  const modal = $("#diagnosticClassificationModal");
  if (!modal) return;
  const wasOpen = modal.classList.contains("open");
  const returnFocus = diagnosticClassificationModalReturnFocus;
  diagnosticClassificationModalReturnFocus = null;
  if (discardDraft) {
    invalidateDiagnosticClassificationDraft();
    diagnosticClassificationDraftEntryId = "";
  }
  modal.classList.remove("open");
  modal.setAttribute("aria-hidden", "true");
  const body = $("#diagnosticClassificationModalBody");
  if (body) body.innerHTML = "";
  if (wasOpen && restoreFocus && returnFocus?.isConnected) {
    window.requestAnimationFrame(() => returnFocus.focus());
  }
}

function isLocalClinicalHistory() {
  return String(state?.meta?.liraImport?.origin || "") === "local";
}

function getStructuredSectionConfig(sectionKey) {
  return STRUCTURED_SECTION_FORMS[sectionKey] || null;
}

function shouldUseStructuredSectionForm(sectionKey) {
  if (sectionKey === "studies") return true;
  if (!getStructuredSectionConfig(sectionKey)) return false;
  if (state?.meta?.sectionFormModes?.[sectionKey] === "structured") return true;
  if (!isLocalClinicalHistory()) return false;
  const versions = state?.meta?.sectionVersions?.[sectionKey];
  return !Array.isArray(versions) || versions.length === 0;
}

function supportsInlineSectionLoad(sectionKey) {
  return LOCAL_INLINE_LOAD_SECTION_KEYS.includes(sectionKey);
}

function setSectionEditorPane(activePane) {
  const panes = {
    text: $("#sectionTextEditor"),
    studies: $("#sectionStudiesEditor"),
    structured: $("#sectionStructuredEditor")
  };
  Object.entries(panes).forEach(([name, pane]) => {
    if (pane) pane.hidden = name !== activePane;
  });
}

function beginSectionEditSession(sectionKey) {
  const modal = $("#sectionEditModal");
  const token = String(++sectionEditSessionSequence);
  modal.dataset.editSession = token;
  modal.dataset.editSectionKey = sectionKey;
  delete modal.dataset.pendingPersistence;
  delete modal.dataset.persistenceSuccessMessage;
  delete modal.dataset.persistencePendingMessage;
  setSectionEditorBusy(false, token);
  return { token, sectionKey };
}

function getSectionEditSession() {
  const modal = $("#sectionEditModal");
  return {
    token: String(modal.dataset.editSession || ""),
    sectionKey: String(modal.dataset.editSectionKey || editingSectionKey || "")
  };
}

function isCurrentSectionEditSession(session) {
  if (!session?.token || !session.sectionKey) return false;
  const modal = $("#sectionEditModal");
  return modal.classList.contains("open") &&
    modal.dataset.editSession === session.token &&
    modal.dataset.editSectionKey === session.sectionKey &&
    editingSectionKey === session.sectionKey;
}

function setSectionEditorBusy(busy, token = $("#sectionEditModal")?.dataset.editSession || "") {
  const modal = $("#sectionEditModal");
  if (!modal || (token && modal.dataset.editSession && modal.dataset.editSession !== String(token))) return;
  modal.classList.toggle("is-busy", Boolean(busy));
  modal.setAttribute("aria-busy", String(Boolean(busy)));
  const body = $(".modal-body", modal);
  if (body) body.inert = Boolean(busy);
  const save = $("#saveSectionEditBtn");
  if (save) {
    save.disabled = Boolean(busy);
    save.classList.toggle("is-loading", Boolean(busy));
  }
}

function setSectionPersistencePending(session, successMessage, pendingMessage) {
  const modal = $("#sectionEditModal");
  if (!isCurrentSectionEditSession(session)) return false;
  modal.dataset.pendingPersistence = "true";
  modal.dataset.persistenceSuccessMessage = successMessage;
  modal.dataset.persistencePendingMessage = pendingMessage;
  setSectionEditorBusy(true, session.token);
  return true;
}

async function completeSectionEditPersistence(session, successMessage, pendingMessage) {
  let persisted = false;
  try {
    persisted = await persistClinicalState({ silent: true });
  } catch (error) {
    console.error("No se pudo persistir la seccion clinica", error);
  }
  if (!isCurrentSectionEditSession(session)) return persisted;
  setSectionEditorBusy(false, session.token);
  if (persisted) {
    delete $("#sectionEditModal").dataset.pendingPersistence;
    closeSectionEditModal();
    toast(successMessage);
  } else {
    const saveLabel = $("#saveSectionEditBtn span");
    if (saveLabel) saveLabel.textContent = "Reintentar guardado";
    toast(getClinicalPersistenceFailureMessage(pendingMessage));
  }
  return persisted;
}

async function retryPendingSectionEditPersistence() {
  const modal = $("#sectionEditModal");
  if (modal.dataset.pendingPersistence !== "true") return false;
  const session = getSectionEditSession();
  const successMessage = modal.dataset.persistenceSuccessMessage || "Seccion guardada";
  const pendingMessage = modal.dataset.persistencePendingMessage ||
    "No se pudo sincronizar. El editor queda abierto para reintentar.";
  setSectionEditorBusy(true, session.token);
  await completeSectionEditPersistence(session, successMessage, pendingMessage);
  return true;
}

function isInitialSectionEntry(sectionKey) {
  const versions = state?.meta?.sectionVersions?.[sectionKey];
  return !hasText(getSectionCurrentText(sectionKey)) && (!Array.isArray(versions) || versions.length === 0);
}

function prepareSectionEditAudit(sectionKey) {
  const initial = isInitialSectionEntry(sectionKey);
  const modal = $("#sectionEditModal");
  const reasonField = $("#sectionEditReasonField");
  const reason = $("#sectionEditReason");
  const saveLabel = $("#saveSectionEditBtn span");
  modal.dataset.initialLoad = String(initial);
  $(".sheet-kicker", modal).textContent = initial ? "Cargar seccion" : "Modificar seccion";
  reasonField.hidden = initial;
  $("#sectionEditReasonLabel").textContent = initial ? "Motivo del registro" : "Motivo de la modificacion";
  $("#sectionEditReasonHint").textContent = initial
    ? "La primera carga quedara identificada automaticamente."
    : "Quedara registrado en el historial de la seccion.";
  reason.value = initial ? "Carga inicial" : "";
  saveLabel.textContent = initial ? "Cargar en historia" : "Guardar modificacion";
  return initial;
}

function openSectionEditModal(sectionKey) {
  const title = getSectionTitle(sectionKey);
  if (!title && sectionKey !== "diagnosis") return;

  editingSectionKey = sectionKey;
  beginSectionEditSession(sectionKey);
  const isStudyEditor = sectionKey === "studies";
  const isStructuredEditor = !isStudyEditor && shouldUseStructuredSectionForm(sectionKey);
  $("#sectionEditModalTitle").textContent = title || "Datos oncologicos";
  setSectionEditorPane(isStudyEditor ? "studies" : isStructuredEditor ? "structured" : "text");
  if (isStudyEditor) {
    renderSectionStudiesEditor();
  } else if (isStructuredEditor) {
    renderStructuredSectionForm(sectionKey);
  } else {
    $("#sectionEditTextarea").value = getSectionCurrentText(sectionKey);
  }
  prepareSectionEditAudit(sectionKey);
  $("#sectionEditModal").classList.add("open");
  $("#sectionEditModal").setAttribute("aria-hidden", "false");
  const focusTarget = isStudyEditor
    ? $(".section-study-row:not(.locked) .section-study-date", $("#sectionStudiesRows")) || $("#addSectionStudyRowBtn")
    : isStructuredEditor
      ? $("[data-section-form-field]", $("#sectionStructuredFields"))
      : $("#sectionEditTextarea");
  focusTarget?.focus();
}

function closeSectionEditModal() {
  const modal = $("#sectionEditModal");
  if (!modal.classList.contains("open")) return;
  setSectionEditorBusy(false, modal.dataset.editSession || "");
  modal.classList.remove("open");
  modal.setAttribute("aria-hidden", "true");
  delete modal.dataset.initialLoad;
  delete modal.dataset.editSession;
  delete modal.dataset.editSectionKey;
  delete modal.dataset.pendingPersistence;
  delete modal.dataset.persistenceSuccessMessage;
  delete modal.dataset.persistencePendingMessage;
  setSectionEditorPane("text");
  $("#sectionStructuredFields").innerHTML = "";
  $("#sectionEditReasonField").hidden = false;
  editingSectionKey = null;
}

function renderStructuredSectionForm(sectionKey) {
  const config = getStructuredSectionConfig(sectionKey);
  if (!config) return;
  $("#sectionStructuredIntro").textContent = config.intro || "";
  const fields = config.fields.map((field) => {
    const storedValue = getByPath(state, field.path) ?? "";
    const value = String(field.path === "exam.heightM" && hasText(storedValue)
      ? normalizeHeightCm(storedValue)
      : storedValue);
    const className = field.wide ? "section-structured-field is-wide" : "section-structured-field";
    const control = field.kind === "textarea"
      ? `<textarea data-section-form-field="${escapeAttr(field.path)}" rows="${Number(field.rows) || 4}">${escapeHtml(value)}</textarea>`
      : `<input type="${field.kind === "number" ? "number" : "text"}" data-section-form-field="${escapeAttr(field.path)}" value="${escapeAttr(value)}"${field.inputMode ? ` inputmode="${escapeAttr(field.inputMode)}"` : ""}${field.min != null ? ` min="${escapeAttr(field.min)}"` : ""}${field.max != null ? ` max="${escapeAttr(field.max)}"` : ""}${field.step != null ? ` step="${escapeAttr(field.step)}"` : ""}>`;
    return `<label class="${className}"><span>${escapeHtml(field.label)}</span>${control}</label>`;
  }).join("");
  const examTools = config.showExamTools ? `
    <div class="section-exam-tools is-wide">
      <div class="section-exam-metrics" aria-live="polite">
        <span><small>IMC</small><strong id="sectionExamBmi">-</strong></span>
        <span><small>Superficie corporal</small><strong id="sectionExamBsa">-</strong></span>
      </div>
      <button class="ghost-button" type="button" data-action="prefill-section-exam">
        <i data-lucide="clipboard-check"></i>
        <span>Usar examen fisico habitual</span>
      </button>
    </div>
  ` : "";
  $("#sectionStructuredFields").innerHTML = fields + examTools;
  updateStructuredSectionDerivedFields();
  refreshIcons();
}

function updateStructuredSectionDerivedFields() {
  const root = $("#sectionStructuredFields");
  const weight = parseNumber($('[data-section-form-field="exam.weightKg"]', root)?.value);
  const heightCm = normalizeHeightCm($('[data-section-form-field="exam.heightM"]', root)?.value);
  const metrics = calculateAnthropometrics(weight, heightCm);
  const bmiOutput = $("#sectionExamBmi");
  const bsaOutput = $("#sectionExamBsa");
  if (!bmiOutput || !bsaOutput) return;
  if (metrics.weightKg > 0 && metrics.heightCm > 0) {
    bmiOutput.textContent = round(metrics.bmi, 2);
    bsaOutput.textContent = `${round(metrics.bodySurface, 3)} m2`;
  } else {
    bmiOutput.textContent = "-";
    bsaOutput.textContent = "-";
  }
}

function prefillStructuredPhysicalExam() {
  const field = $('[data-section-form-field="narrative.physicalExam"]', $("#sectionStructuredFields"));
  if (!field) return;
  if (field.value.trim()) {
    toast("Examen fisico ya tiene texto");
    return;
  }
  field.value = DEFAULT_PHYSICAL_EXAM_TEXT;
  field.focus();
  toast("Plantilla agregada");
}

function getStructuredSectionText(sectionKey, clinicalState = state) {
  const oncology = clinicalState?.oncology || {};
  const narrative = clinicalState?.narrative || {};
  const exam = clinicalState?.exam || {};
  const bySection = {
    diagnosis: () => [
      renderDiagnosisRecordsPlainText(),
      getDiagnosisRecords().length ? "" : plainMeta("Diagnostico", oncology.diagnosis),
      getDiagnosisRecords().length ? "" : plainMeta("Fecha de diagnostico", formatDateOptional(oncology.diagnosisDate, oncology.diagnosisDatePrecision)),
      getDiagnosisRecords().length ? "" : plainMeta("Topografia / localizacion", oncology.topography),
      getDiagnosisRecords().length ? "" : plainMeta("Histologia / anatomia patologica", oncology.histology),
      getDiagnosisRecords().length ? "" : plainMeta("Estadificacion", oncology.stage),
      plainMeta("Biomarcadores", oncology.biomarkers),
      plainMeta("Estado", oncology.status),
      plainMeta("Intencion", oncology.intent),
      plainMeta("Performance status", oncology.performanceStatus)
    ].filter(Boolean).join("\n"),
    chiefComplaint: () => narrative.chiefComplaint,
    currentIllness: () => narrative.currentIllness,
    personalHistory: () => [
      plainMeta("Clinicos / quirurgicos", narrative.backgroundClinical),
      plainMeta("Medicacion habitual", narrative.currentMedication),
      plainMeta("Oncofamiliares", narrative.familyOncology),
      plainMeta("Gineco-obstetricos", narrative.gynecology)
    ].filter(Boolean).join("\n"),
    physicalExam: () => [
      plainMeta("Peso", exam.weightKg ? `${exam.weightKg} kg` : ""),
      plainMeta("Talla", exam.heightM ? `${normalizeHeightCm(exam.heightM)} cm` : ""),
      formatPhysicalExamPlainText(narrative.physicalExam)
    ].filter(Boolean).join("\n"),
    summaryPlan: () => [
      plainMeta("Conclusion / resumen", narrative.summary),
      plainMeta("Conducta / plan", narrative.plan)
    ].filter(Boolean).join("\n")
  };
  return bySection[sectionKey]?.() || "";
}

function collectStructuredSectionDraft(config) {
  const draftState = {
    oncology: { ...(state?.oncology || {}) },
    narrative: { ...(state?.narrative || {}) },
    exam: { ...(state?.exam || {}) }
  };
  const root = $("#sectionStructuredFields");
  $$("[aria-invalid]", root).forEach((field) => field.removeAttribute("aria-invalid"));

  for (const field of config.fields) {
    const control = $(`[data-section-form-field="${CSS.escape(field.path)}"]`, root);
    const rawValue = String(control?.value || "").trim();
    let value = rawValue;
    if (field.kind === "number" && rawValue) {
      const numericValue = parseNumber(rawValue);
      const invalidHeight = field.path === "exam.heightM" &&
        (numericValue < 30 || numericValue > Number(field.max || 250));
      if (!Number.isFinite(numericValue) || numericValue <= 0 || invalidHeight) {
        control?.setAttribute("aria-invalid", "true");
        return {
          error: invalidHeight
            ? `${field.label} debe ingresarse en centimetros.`
            : `${field.label} debe ser un numero mayor que cero.`,
          control
        };
      }
      value = String(field.path === "exam.heightM"
        ? normalizeHeightMeters(numericValue)
        : numericValue);
    }
    setByPath(draftState, field.path, value);
  }

  return { draftState };
}

async function saveSectionEdit() {
  if (!editingSectionKey) return;
  if (await retryPendingSectionEditPersistence()) return;
  if (editingSectionKey === "studies") {
    await saveStudiesSectionEdit();
    return;
  }
  if (shouldUseStructuredSectionForm(editingSectionKey)) {
    await saveStructuredSectionEdit();
    return;
  }

  const session = getSectionEditSession();
  const initial = $("#sectionEditModal").dataset.initialLoad === "true";
  const content = $("#sectionEditTextarea").value.trim();
  const reason = $("#sectionEditReason").value.trim() || (initial ? "Carga inicial" : "");
  if (!content) {
    toast("Seccion vacia");
    return;
  }
  if (!initial && !reason) {
    toast("Indique motivo de modificacion");
    $("#sectionEditReason").focus();
    return;
  }

  const current = getSectionCurrentText(editingSectionKey).trim();
  if (content === current) {
    toast("Sin cambios");
    return;
  }

  const audit = buildAuditStamp(initial ? "cargado" : "modificado");
  if (!initial) ensureSectionInitialHistory(editingSectionKey, current);
  addSectionVersion(editingSectionKey, reason, content, audit);
  state.meta.sectionAudit[editingSectionKey] = audit;

  state.meta.updatedAt = new Date().toISOString();
  storeClinicalStateLocally();
  renderPreview();
  renderPatientOutputs();
  const successMessage = initial ? "Seccion cargada" : "Seccion modificada";
  const pendingMessage = "No se pudo sincronizar. El editor queda abierto con los datos para reintentar.";
  setSectionPersistencePending(session, successMessage, pendingMessage);
  await completeSectionEditPersistence(session, successMessage, pendingMessage);
}

async function saveStructuredSectionEdit() {
  if (await retryPendingSectionEditPersistence()) return;
  const sectionKey = editingSectionKey;
  const config = getStructuredSectionConfig(sectionKey);
  if (!config) return;
  const session = getSectionEditSession();
  const initial = $("#sectionEditModal").dataset.initialLoad === "true";
  const reason = $("#sectionEditReason").value.trim() || (initial ? "Carga inicial" : "");
  if (!initial && !reason) {
    toast("Indique motivo de modificacion");
    $("#sectionEditReason").focus();
    return;
  }

  const draft = collectStructuredSectionDraft(config);
  if (draft.error) {
    toast(draft.error);
    draft.control?.focus();
    return;
  }

  const previous = getStructuredSectionText(sectionKey, state).trim();
  const content = getStructuredSectionText(sectionKey, draft.draftState).trim();
  if (!content && !previous) {
    toast("Complete al menos un campo");
    $("[data-section-form-field]", $("#sectionStructuredFields"))?.focus();
    return;
  }
  if (content === previous) {
    toast("Sin cambios");
    return;
  }

  config.fields.forEach((field) => {
    setByPath(state, field.path, getByPath(draft.draftState, field.path));
  });
  state.meta.sectionFormModes ??= {};
  state.meta.sectionFormModes[sectionKey] = "structured";
  state.meta.sectionAudit ??= {};
  const audit = buildAuditStamp(initial ? "cargado" : "modificado");
  if (!initial) ensureSectionInitialHistory(sectionKey, previous || "Sin datos cargados.");
  addSectionVersion(sectionKey, reason, content || "Sin datos cargados.", audit);
  state.meta.sectionAudit[sectionKey] = audit;
  state.meta.updatedAt = audit.at;
  storeClinicalStateLocally();
  fillBindableFields();
  updateDerivedFields();
  renderPreview();
  renderPatientOutputs();
  const successMessage = initial ? "Seccion cargada en la historia" : "Seccion modificada";
  const pendingMessage = "No se pudo sincronizar. El editor queda abierto con los datos para reintentar.";
  setSectionPersistencePending(session, successMessage, pendingMessage);
  await completeSectionEditPersistence(session, successMessage, pendingMessage);
}

function renderSectionStudiesEditor() {
  const rows = $("#sectionStudiesRows");
  rows.innerHTML = "";
  [...state.studies]
    .sort((a, b) => (a.date || "").localeCompare(b.date || ""))
    .forEach((study) => addSectionStudyRow({
      id: study.id,
      date: study.date || "",
      type: study.type || "Otro",
      title: study.title || "",
      source: study.source || "",
      description: study.summary || ""
    }, { locked: true, focus: false }));

  if (!rows.children.length) addSectionStudyRow({}, { prepend: true });
}

function renderSectionStudyTypeOptions(currentType = "") {
  const selectedType = String(currentType || "Otro").trim() || "Otro";
  const options = Array.from($("#studyType")?.options || []).map((option) => ({
    value: String(option.value || ""),
    label: String(option.textContent || option.value || "")
  }));
  if (!options.some((option) => option.value === selectedType)) {
    options.unshift({ value: selectedType, label: selectedType });
  }
  return options
    .map((option) => `<option value="${escapeAttr(option.value)}"${option.value === selectedType ? " selected" : ""}>${escapeHtml(option.label)}</option>`)
    .join("");
}

function addSectionStudyRow(study = {}, options = {}) {
  const rows = $("#sectionStudiesRows");
  const id = study.id || "";
  const isLocked = options.locked ?? Boolean(id);
  const disabled = isLocked ? " disabled" : "";
  const studyTypes = renderSectionStudyTypeOptions(study.type);
  const rowHtml = `
    <article class="section-study-row ${isLocked ? "locked" : ""}" data-study-id="${escapeAttr(id)}">
      <div class="section-study-fields">
        <label>
          <span>Fecha</span>
          <input class="section-study-date" type="date" value="${escapeAttr(study.date || "")}"${disabled}>
        </label>
        <label>
          <span>Tipo</span>
          <select class="section-study-type"${disabled}>${studyTypes}</select>
        </label>
        <label>
          <span>Titulo</span>
          <input class="section-study-title" value="${escapeAttr(study.title || "")}"${disabled}>
        </label>
        <label>
          <span>Origen</span>
          <input class="section-study-source" value="${escapeAttr(study.source || (id ? "" : "Repositorio local"))}"${disabled}>
        </label>
        <label class="is-wide">
          <span>Descripcion / resultado</span>
          <textarea class="section-study-description" rows="4"${disabled}>${escapeHtml(study.description || "")}</textarea>
        </label>
      </div>
      <div class="section-study-row-actions">
        ${id ? `
          <button class="tiny-button" type="button" data-action="edit-section-study-row" title="Modificar estudio">
            <i data-lucide="pencil"></i>
          </button>
        ` : ""}
        <button class="tiny-button danger" type="button" data-action="remove-section-study-row" title="Quitar campo">
          <i data-lucide="trash-2"></i>
        </button>
      </div>
    </article>
  `;
  rows.insertAdjacentHTML(options.prepend ? "afterbegin" : "beforeend", rowHtml);
  const row = options.prepend ? rows.firstElementChild : rows.lastElementChild;
  refreshIcons();
  if (options.focus !== false && !isLocked) $(".section-study-date", row)?.focus();
}

function handleSectionStudyRowAction(event) {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const row = button.closest(".section-study-row");
  if (!row) return;

  if (button.dataset.action === "edit-section-study-row") {
    enableSectionStudyRow(row, button);
    return;
  }

  if (button.dataset.action === "remove-section-study-row") {
    row.remove();
    if (!$("#sectionStudiesRows").children.length) addSectionStudyRow({}, { prepend: true });
  }
}

function enableSectionStudyRow(row, editButton) {
  row.classList.remove("locked");
  $$("input, select, textarea", row).forEach((field) => {
    field.disabled = false;
  });
  editButton.hidden = true;
  $(".section-study-description", row)?.focus();
}

function studiesSectionText(records = []) {
  return [...records]
    .sort((a, b) => (a.date || "").localeCompare(b.date || ""))
    .map((study) => [
      withFinalPeriod([formatDateOptional(study.date, study.datePrecision), study.type, study.title].filter(hasText).join(" - ")),
      study.summary,
      plainMeta("Origen", getStudySourceLabel(study))
    ].filter(hasText).join("\n"))
    .join("\n\n");
}

function sectionStudyRecordChanged(current = {}, next = {}) {
  const fields = ["date", "datePrecision", "type", "title", "source", "summary"];
  return fields.some((field) => {
    const fallback = field === "type" ? "Otro" : field === "datePrecision" ? "day" : "";
    return String(current[field] || fallback) !== String(next[field] || fallback);
  });
}

function studySectionRecordsDiffer(currentRecords = [], nextRecords = []) {
  if (currentRecords.length !== nextRecords.length) return true;
  const currentById = new Map(currentRecords.map((study) => [String(study.id || ""), study]));
  return nextRecords.some((study) => {
    const current = currentById.get(String(study.id || ""));
    return !current || sectionStudyRecordChanged(current, study);
  });
}

async function saveStudiesSectionEdit() {
  if (await retryPendingSectionEditPersistence()) return;
  const session = getSectionEditSession();
  const initial = $("#sectionEditModal").dataset.initialLoad === "true";
  const reason = $("#sectionEditReason").value.trim() || (initial ? "Carga inicial" : "");
  if (!initial && !reason) {
    toast("Indique motivo de modificacion");
    $("#sectionEditReason").focus();
    return;
  }

  const audit = buildAuditStamp(initial ? "cargado" : "modificado");
  const previous = studiesSectionText(state.studies).trim();
  const originalById = new Map(state.studies.map((study) => [study.id, study]));
  const nextStudies = $$(".section-study-row", $("#sectionStudiesRows"))
    .map((row) => {
      const id = row.dataset.studyId || makeId("est");
      const original = originalById.get(id) || {};
      const date = $(".section-study-date", row).value || "";
      const type = $(".section-study-type", row)?.value || original.type || "Otro";
      const title = $(".section-study-title", row)?.value.trim() || "";
      const source = $(".section-study-source", row)?.value.trim() || "";
      const description = $(".section-study-description", row).value.trim();
      if (!date && !title && !description) return null;
      const nextDate = date || original.date || today();
      const nextStudy = {
        ...original,
        id,
        date: nextDate,
        datePrecision: nextDate === (original.date || "") ? normalizeDatePrecision(original.datePrecision) : "day",
        type,
        title: title || original.title || "Estudio complementario",
        source: original.id ? source : source || "Repositorio local",
        summary: description,
        tags: Array.isArray(original.tags) ? original.tags : []
      };
      const changed = !original.id || sectionStudyRecordChanged(original, nextStudy);
      const rowAudit = changed ? audit : getRecordAudit(original, { action: "cargado" });
      return {
        ...nextStudy,
        audit: rowAudit,
        updatedAt: changed ? rowAudit.at : original.updatedAt || rowAudit.at,
        createdAt: original.createdAt || rowAudit.at
      };
    })
    .filter(Boolean);

  const sortedStudies = nextStudies.sort((a, b) => (a.date || "").localeCompare(b.date || ""));
  const content = studiesSectionText(sortedStudies).trim();

  if (!content && !previous) {
    toast("Complete al menos un estudio");
    $(".section-study-row:not(.locked) .section-study-date", $("#sectionStudiesRows"))?.focus();
    return;
  }
  if (!studySectionRecordsDiffer(state.studies, sortedStudies)) {
    toast("Sin cambios");
    return;
  }

  state.studies = sortedStudies;
  selectedStudyId = state.studies[0]?.id || null;
  if (!initial) ensureSectionInitialHistory("studies", previous || "Sin estudios complementarios registrados.");
  addSectionVersion("studies", reason, content || "Sin estudios complementarios registrados.", audit);
  state.meta.sectionAudit.studies = audit;
  state.meta.updatedAt = new Date().toISOString();
  storeClinicalStateLocally();
  renderStudyList();
  renderStudyViewer();
  renderPreview();
  renderPrescriptionDrafts();
  renderPatientOutputs();
  const successMessage = initial ? "Estudios cargados en la historia" : "Estudios modificados";
  const pendingMessage = "No se pudieron sincronizar los estudios. El editor queda abierto para reintentar.";
  setSectionPersistencePending(session, successMessage, pendingMessage);
  await completeSectionEditPersistence(session, successMessage, pendingMessage);
}

function addSectionVersion(sectionKey, reason, content, audit = buildAuditStamp("modificado")) {
  const versions = getSectionVersions(sectionKey);
  versions.push({
    id: makeId(`sec-${sectionKey}`),
    createdAt: audit.at,
    author: audit.lastName,
    license: audit.license,
    reason,
    content,
    audit
  });
}

function ensureSectionInitialHistory(sectionKey, content) {
  const versions = getSectionVersions(sectionKey);
  if (versions.some((version) => getVersionAudit(version).action === "cargado")) return;
  const audit = getInitialSectionAudit();
  const initialContent = versions.length ? getSectionInitialContent(sectionKey) : content;
  versions.unshift({
    id: makeId(`sec-${sectionKey}-initial`),
    createdAt: audit.at,
    author: audit.lastName,
    license: audit.license,
    reason: "Carga inicial",
    content: initialContent,
    audit
  });
}

function getSectionHistoryEntries(sectionKey) {
  const versions = getSectionVersions(sectionKey);
  if (!versions.length) return [];

  const hasInitial = versions.some((version) => getVersionAudit(version).action === "cargado");
  const entries = hasInitial
    ? [...versions]
    : [buildSectionInitialHistoryEntry(sectionKey), ...versions];

  return entries
    .filter((entry) => hasText(entry.content) || hasText(entry.createdAt));
}

function buildSectionInitialHistoryEntry(sectionKey) {
  const audit = getInitialSectionAudit();
  return {
    id: `sec-${sectionKey}-initial-display`,
    createdAt: audit.at,
    author: audit.lastName,
    license: audit.license,
    reason: "Carga inicial",
    content: getSectionInitialContent(sectionKey),
    audit
  };
}

function getSectionInitialContent(sectionKey) {
  if (usesStructuredSectionEditor(sectionKey) && getSectionVersions(sectionKey).length) {
    return getSectionVersions(sectionKey)[0]?.content || getSectionDefaultText(sectionKey);
  }
  return getSectionDefaultText(sectionKey);
}

function getInitialSectionAudit() {
  return buildAuditStamp("cargado", { at: state.meta.createdAt || state.meta.updatedAt || new Date().toISOString() });
}

function getHistoryEntryLabel(version, index, entries) {
  const audit = getVersionAudit(version);
  if (audit.action === "cargado") return "Carga inicial";
  if (index === entries.length - 1) return "Version vigente";
  const previousEdits = entries.slice(0, index + 1).filter((entry) => getVersionAudit(entry).action !== "cargado").length;
  return `Modificacion ${previousEdits}`;
}

function openSectionHistoryModal(sectionKey) {
  const entries = getSectionHistoryEntries(sectionKey);
  if (!entries.length) return;

  $("#sectionHistoryModalTitle").textContent = `Historial - ${getSectionTitle(sectionKey) || "Datos oncologicos"}`;
  $("#sectionHistoryList").innerHTML = entries
    .map((version, index) => `
      <article class="history-item">
        <header>
          <strong>${escapeHtml(getHistoryEntryLabel(version, index, entries))}</strong>
          <span>${escapeHtml(formatDayDateTime(version.createdAt))}</span>
        </header>
        ${renderAuditLine(getVersionAudit(version), "history-audit")}
        <p><strong>Motivo:</strong> ${escapeHtml(version.reason || getHistoryEntryLabel(version, index, entries))}</p>
        <p class="history-content">${escapeHtml(version.content)}</p>
      </article>
    `)
    .join("");

  $("#sectionHistoryModal").classList.add("open");
  $("#sectionHistoryModal").setAttribute("aria-hidden", "false");
}

function closeSectionHistoryModal() {
  const modal = $("#sectionHistoryModal");
  if (!modal.classList.contains("open")) return;
  modal.classList.remove("open");
  modal.setAttribute("aria-hidden", "true");
}

function updateEvolutionModalState() {
  const isEditing = Boolean(editingEvolutionId);
  const submitButton = $("#addEvolutionBtn");
  $("#deleteEvolutionBtn").hidden = !isEditing;
  $("#evolutionModalTitle").textContent = isEditing ? "Modificar evolucion" : "Agregar evolucion";
  $(".modal-header .sheet-kicker", $("#evolutionModal")).textContent = isEditing ? "Editar evolucion" : "Nueva evolucion";
  $("span", submitButton).textContent = isEditing ? "Guardar cambios" : "Cargar en hoja";
  setButtonIcon(submitButton, isEditing ? "check" : "plus");
  refreshIcons();
}

function setEvolutionMode(mode) {
  evolutionMode = "evolucion";
  $$("[data-evolution-mode]").forEach((button) => {
    button.classList.toggle("active", button.dataset.evolutionMode === evolutionMode);
  });

  $("#evolutionTextLabel").textContent = "Evolucion";
}

function wireSplitter() {
  const splitter = $("#splitter");
  const workspace = $("#splitWorkspace");
  const shell = $("#splitterShell");
  if (!splitter || !workspace || !shell) return;

  splitter.addEventListener("pointerdown", (event) => {
    if (event.pointerType === "mouse" && event.button !== 0) return;
    event.preventDefault();
    beginSplitDrag(event.clientX);
    splitter.setPointerCapture(event.pointerId);
    updateSplitFromClientX(event.clientX);
  });

  splitter.addEventListener("pointermove", (event) => {
    if (!isDraggingSplitter) return;
    event.preventDefault();
    updateSplitFromClientX(event.clientX);
  });

  splitter.addEventListener("pointerup", (event) => {
    if (!isDraggingSplitter) return;
    if (splitter.hasPointerCapture(event.pointerId)) splitter.releasePointerCapture(event.pointerId);
    endSplitDrag();
  });

  splitter.addEventListener("pointercancel", () => {
    endSplitDrag();
  });

  splitter.addEventListener("dblclick", (event) => {
    event.preventDefault();
    setSplitPreset("balanced");
  });

  $$("[data-split-preset]", shell).forEach((button) => {
    button.addEventListener("click", () => setSplitPreset(button.dataset.splitPreset));
  });

  window.addEventListener("resize", () => {
    const current = getStoredSplitPercent();
    if (isSplitWorkspaceStacked()) {
      syncSplitPanelAccessibility("custom");
      return;
    }
    setSplitPercent(current, { quiet: true });
  });

  splitter.addEventListener("keydown", (event) => {
    const current = getStoredSplitPercent();
    const step = event.shiftKey ? 10 : 2;
    const metrics = getSplitMetrics();
    let next = null;

    if (event.key === "ArrowLeft") next = current - step;
    if (event.key === "ArrowRight") next = current + step;
    if (event.key === "Home") next = metrics.minPct;
    if (event.key === "End") next = metrics.maxPct;
    if (next === null) return;

    event.preventDefault();
    setSplitPercent(next, { quiet: true });
  });
}

function beginSplitDrag(clientX) {
  isDraggingSplitter = true;
  const splitter = $("#splitter");
  const rect = splitter.getBoundingClientRect();
  splitDragOffset = Number.isFinite(clientX) ? clientX - (rect.left + rect.width / 2) : 0;
  splitter.classList.add("dragging");
  document.body.classList.add("resizing");
}

function endSplitDrag() {
  isDraggingSplitter = false;
  splitDragOffset = 0;
  $("#splitter").classList.remove("dragging");
  document.body.classList.remove("resizing");
}

function updateSplitFromClientX(clientX) {
  const metrics = getSplitMetrics();
  const dividerCenter = clientX - splitDragOffset;
  const leftPx = dividerCenter - metrics.contentLeft - metrics.railWidth / 2;
  const pct = clamp((leftPx / metrics.available) * 100, metrics.minPct, metrics.maxPct);
  setSplitPercent(pct, { quiet: true });
}

function setSplitPercent(percent, { quiet = false } = {}) {
  const metrics = getSplitMetrics();
  const numericPercent = Number(percent);
  const requested = Number.isFinite(numericPercent) ? numericPercent : SPLIT_DEFAULT_PERCENT;
  const pct = clamp(requested, metrics.minPct, metrics.maxPct);
  const leftPx = metrics.available * (pct / 100);
  document.documentElement.style.setProperty("--left-width", `${leftPx}px`);
  localStorage.setItem(SPLIT_KEY, String(Math.round(pct * 10) / 10));
  updateSplitControlState(pct, metrics);
  updateRightTabLabels();
  if (!quiet) toast(`Panel izquierdo ${Math.round(pct)}%`);
  return pct;
}

function applyStoredSplit() {
  const stored = getStoredSplitPercent();
  if (isSplitWorkspaceStacked()) {
    document.documentElement.style.setProperty("--left-width", `${stored}%`);
    syncSplitPanelAccessibility("custom");
    return;
  }
  setSplitPercent(stored, { quiet: true });
}

function getStoredSplitPercent() {
  const raw = localStorage.getItem(SPLIT_KEY);
  if (raw === null || raw.trim() === "") return SPLIT_DEFAULT_PERCENT;
  const stored = Number(raw);
  return Number.isFinite(stored) ? stored : SPLIT_DEFAULT_PERCENT;
}

function isSplitWorkspaceStacked() {
  return Boolean(window.matchMedia?.(`(max-width: ${SPLIT_STACK_BREAKPOINT}px)`).matches);
}

function getSplitMetrics() {
  const workspace = $("#splitWorkspace");
  const shell = $("#splitterShell");
  const rect = workspace.getBoundingClientRect();
  const style = window.getComputedStyle(workspace);
  const paddingLeft = Number.parseFloat(style.paddingLeft) || 0;
  const paddingRight = Number.parseFloat(style.paddingRight) || 0;
  const borderLeft = Number.parseFloat(style.borderLeftWidth) || 0;
  const railFromCss = Number.parseFloat(style.getPropertyValue("--splitter-width")) || 36;
  const railWidth = shell?.getBoundingClientRect().width || railFromCss;
  const contentWidth = Math.max(workspace.clientWidth - paddingLeft - paddingRight, 1);
  const available = Math.max(contentWidth - railWidth, 1);

  return {
    available,
    contentLeft: rect.left + borderLeft + paddingLeft,
    maxPct: 100,
    minPct: 0,
    railWidth
  };
}

function getSplitPresetTarget(preset, metrics = getSplitMetrics()) {
  if (preset === "studies") return 0;
  if (preset === "history") return 100;
  return clamp(SPLIT_BALANCED_PERCENT, metrics.minPct, metrics.maxPct);
}

function setSplitPreset(preset) {
  const metrics = getSplitMetrics();
  const target = getSplitPresetTarget(preset, metrics);
  setSplitPercent(target, { quiet: true });
  const messages = {
    studies: "Historia colapsada: solo Estudios",
    balanced: "Historia y Estudios a la mitad",
    history: "Estudios colapsados: solo Historia"
  };
  toast(messages[preset] || messages.balanced);
}

function updateSplitControlState(percent, metrics = getSplitMetrics()) {
  const splitter = $("#splitter");
  const workspace = $("#splitWorkspace");
  if (!splitter || !workspace) return;

  const historyPercent = Math.round(percent);
  const studiesPercent = 100 - historyPercent;
  splitter.setAttribute("aria-valuemin", String(Math.round(metrics.minPct)));
  splitter.setAttribute("aria-valuemax", String(Math.round(metrics.maxPct)));
  splitter.setAttribute("aria-valuenow", String(Math.round(percent * 10) / 10));
  const valueText = percent <= 0
    ? "Solo Estudios; Historia colapsada"
    : percent >= 100
      ? "Solo Historia; Estudios colapsados"
      : `Historia ${historyPercent}%, estudios ${studiesPercent}%`;
  splitter.setAttribute("aria-valuetext", valueText);

  let activePreset = "custom";
  $$("[data-split-preset]", $("#splitterShell")).forEach((button) => {
    const target = getSplitPresetTarget(button.dataset.splitPreset, metrics);
    const tolerance = button.dataset.splitPreset === "balanced" ? 0.6 : 0.01;
    const active = Math.abs(percent - target) < tolerance;
    button.setAttribute("aria-pressed", String(active));
    if (active) activePreset = button.dataset.splitPreset;
  });
  workspace.dataset.splitPosition = activePreset;
  syncSplitPanelAccessibility(activePreset);
}

function syncSplitPanelAccessibility(activePreset) {
  const canCollapse = !isSplitWorkspaceStacked();
  const clinicalPanel = $("#clinicalPanel");
  const studiesPanel = $("#studiesPanel");
  const clinicalCollapsed = canCollapse && activePreset === "studies";
  const studiesCollapsed = canCollapse && activePreset === "history";

  if (clinicalPanel) {
    clinicalPanel.inert = clinicalCollapsed;
    clinicalPanel.toggleAttribute("aria-hidden", clinicalCollapsed);
  }
  if (studiesPanel) {
    studiesPanel.inert = studiesCollapsed;
    studiesPanel.toggleAttribute("aria-hidden", studiesCollapsed);
  }
}

async function loadState({ forceServer = false } = {}) {
  if (!forceServer) {
    state = normalizeState(createEmptyState());
    if (typeof diagnosticClassificationUi !== "undefined") diagnosticClassificationUi.patientKey = "";
    storeClinicalStateLocally();
    return;
  }

  try {
    const response = await fetch(DATA_URL, { cache: "no-store" });
    if (!response.ok) {
      const error = new Error(`HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    state = normalizeState(await response.json());
  } catch (apiError) {
    if ([401, 403, 409].includes(Number(apiError?.status))) {
      if (Number(apiError.status) === 401) handleClinicalSessionExpired();
      throw apiError;
    }
    if (!clinicalLocalCacheAllowed()) {
      state = normalizeState(createEmptyState());
      if (typeof diagnosticClassificationUi !== "undefined") diagnosticClassificationUi.patientKey = "";
      return;
    }
    const activeStorageKey = localStorage.getItem(ACTIVE_STORAGE_KEY) || `${STORAGE_KEY}:local`;
    try {
      const local = localStorage.getItem(activeStorageKey);
      if (!local) throw new Error("Sin respaldo local");
      state = normalizeState(JSON.parse(local));
    } catch {
      try {
        const response = await fetch(DEMO_URL, { cache: "no-store" });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        state = normalizeState(await response.json());
      } catch (demoError) {
        state = normalizeState(createEmptyState());
      }
    }
  }

  if (typeof diagnosticClassificationUi !== "undefined") diagnosticClassificationUi.patientKey = "";
  storeClinicalStateLocally();
}

function getClinicalStorageKey(value = state) {
  const patientId = String(value?.meta?.liraImport?.patientId || value?.patient?.liraId || "").trim();
  return `${STORAGE_KEY}:${/^\d{1,18}$/.test(patientId) ? `lira-${patientId}` : "local"}`;
}

function storeClinicalStateLocally(serializedState) {
  if (!clinicalLocalCacheAllowed()) {
    purgeClinicalLocalCache();
    return false;
  }
  const storageKey = getClinicalStorageKey();
  try {
    const serialized = typeof serializedState === "string" ? serializedState : JSON.stringify(state);
    localStorage.setItem(storageKey, serialized);
    localStorage.setItem(ACTIVE_STORAGE_KEY, storageKey);
    return true;
  } catch {
    localStorage.removeItem(storageKey);
    return false;
  }
}

async function saveState() {
  collectBindableFields();
  state.meta.updatedAt = new Date().toISOString();
  storeClinicalStateLocally();

  const persisted = await persistClinicalState({ silent: true });
  toast(persisted
    ? (getActiveLiraPatientId() ? "Ficha clinica guardada" : "Ficha guardada localmente")
    : getClinicalPersistenceFailureMessage(clinicalLocalCacheAllowed()
      ? "El borrador quedo guardado localmente"
      : "No se pudo guardar la ficha en la base clínica"));

  renderPatientOutputs();
  renderPreview();
}

function queueLocalSave() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    storeClinicalStateLocally();
  }, 250);
}

function renderAll() {
  fillBindableFields();
  setupDraftDefaults();
  resetResearchForm({ preserveProtocol: true });
  updateDerivedFields();
  renderPatientOutputs();
  renderStudyList();
  renderStudyImages();
  renderStudyViewer();
  renderResearchPanel();
  loadResearchTemplates().catch(() => {});
  renderPrescriptionDrafts();
  renderCareWorkspace();
  renderPreview();
  updateRightTabLabels();
  applyClinicalPermissions();
  refreshIcons();
}

function fillBindableFields() {
  $$("[data-field]").forEach((element) => {
    const storedValue = getByPath(state, element.dataset.field) ?? "";
    const value = element.dataset.field === "exam.heightM" && hasText(storedValue)
      ? normalizeHeightCm(storedValue)
      : storedValue;
    if (element instanceof HTMLSelectElement && hasText(value)
      && !Array.from(element.options).some((option) => option.value === String(value))) {
      const option = new Option(String(value), String(value));
      option.dataset.importedValue = "true";
      element.add(option);
    }
    element.value = value;
  });
}

function syncBindableSiblings(source) {
  const path = source.dataset.field;
  $$(`[data-field="${path}"]`).forEach((element) => {
    if (element !== source) element.value = source.value;
  });
}

function collectBindableFields() {
  $$("[data-field]").forEach((element) => {
    const value = element.dataset.field === "exam.heightM" && hasText(element.value)
      ? normalizeHeightMeters(element.value)
      : element.value;
    setByPath(state, element.dataset.field, value);
  });
}

function renderPatientOutputs(isDirty = false) {
  $$("[data-output]").forEach((element) => {
    element.textContent = getByPath(state, element.dataset.output) || "-";
  });

  $("#updatedBadge").textContent = isDirty
    ? "Cambios locales"
      : state.meta.updatedAt
      ? `Actualizado ${formatDateTime(state.meta.updatedAt)}`
      : "Sin guardar";
  if ($("#prescriptionPatientName")) {
    const prescriptionCoverage = getPrescriptionCoverage();
    $("#prescriptionPatientName").textContent = state.patient.fullName || "Paciente sin identificar";
    $("#prescriptionPatientMeta").textContent = [
      `DNI ${state.patient.dni || "—"}`,
      `HC ${state.patient.medicalRecord || "—"}`,
      `Obra social ${prescriptionCoverage.insurance || "—"}`,
      `N.° de afiliado ${prescriptionCoverage.affiliateNumber || "—"}`
    ].join(" · ");
  }
  setLiraPatientRefreshBusy(liraPatientRefreshBusy);
  syncActivePatientCloseButton();
}

function openEvolutionHistoryModal(id) {
  const entry = state.evolutions.find((item) => item.id === id);
  if (!entry) return;
  const initial = {
    action: "cargado",
    at: entry.createdAt || entry.audit?.at || "",
    date: entry.date,
    author: entry.author,
    reason: entry.reason,
    text: entry.text,
    attachments: normalizeEvolutionAttachments(entry.attachments),
    audit: entry.audit
  };
  const history = entry.history?.length ? [...entry.history] : [initial];
  if (!entry.deleted && entry.history?.length) history.push({action:"vigente",at:entry.updatedAt,date:entry.date,author:entry.author,reason:entry.reason,text:entry.text,audit:entry.audit});
  const labels = {cargado:"Carga inicial",modificado:"Version anterior",eliminado:"Evolucion eliminada",vigente:"Version vigente"};
  $("#sectionHistoryModalTitle").textContent = "Historial de la evolucion";
  $("#sectionHistoryList").innerHTML = history.map((version,index)=>`
    <article class="history-item ${version.action === "eliminado" ? "history-item--deleted" : ""}">
      <header><strong>${escapeHtml(index === 0 && version.action !== "eliminado" ? "Carga inicial" : labels[version.action] || "Modificacion")}</strong><span>${escapeHtml(formatDayDateTime(version.at))}</span></header>
      <p><strong>Fecha clinica:</strong> ${escapeHtml(formatDateOptional(version.date))}</p>
      <p><strong>Profesional:</strong> ${escapeHtml(version.author || "-")} ${version.reason ? `· ${escapeHtml(version.reason)}` : ""}</p>
      <p class="history-content">${escapeHtml(version.text || "")}</p>
      ${normalizeEvolutionAttachments(version.attachments).length ? `<p><strong>Imagenes adjuntas:</strong> ${normalizeEvolutionAttachments(version.attachments).length}</p>` : ""}
    </article>`).join("");
  $("#sectionHistoryModal").classList.add("open");
  $("#sectionHistoryModal").setAttribute("aria-hidden", "false");
}

function updateDerivedFields() {
  const metrics = calculateAnthropometrics(state.exam.weightKg, state.exam.heightM);
  if (metrics.weightKg > 0 && metrics.heightCm > 0) {
    $("#bmiOutput").value = round(metrics.bmi, 2);
    $("#bsaOutput").value = `${round(metrics.bodySurface, 3)} m2`;
  } else {
    $("#bmiOutput").value = "";
    $("#bsaOutput").value = "";
  }
}

function setupDraftDefaults() {
  $("#evolutionDate").value ||= today();
  $("#evolutionAuthor").value ||= state.meta.currentUser || "";
  $("#evolutionReason").value ||= "Oncologia";
  $("#studyDate").value ||= today();
  $("#studySource").value ||= "Repositorio local";
  if ($("#rxCertificateFrom")) {
    $("#rxCertificateFrom").value ||= today();
    $("#rxCertificateTo").value ||= today();
    if (!$("#rxCertificateText").value) fillCertificateTemplate();
  }
}

function setActiveTab(tabName) {
  $$("[data-tab]").forEach((tab) => tab.classList.toggle("active", tab.dataset.tab === tabName));
  $$("[data-panel]").forEach((panel) => panel.classList.toggle("active", panel.dataset.panel === tabName));
  const historyActive = tabName === "historia";
  $$(".paper-highlight-actions button:not(#closeActivePatientBtn), .paper-highlight-actions input").forEach((control) => { control.disabled = !historyActive; });
  syncActivePatientCloseButton();
  if (!historyActive) {
    const shell = $("[data-clinical-search-shell]");
    shell?.classList.remove("open");
    $("[data-action='toggle-clinical-search']", shell || document)?.setAttribute("aria-expanded", "false");
  }
  window.requestAnimationFrame(updateClinicalScrollEndButton);
}

function scrollClinicalDocumentToEnd() {
  const scrollArea = $(".clinical-panel .panel-scroll");
  const clinicalPanel = $(".clinical-panel");
  if (!scrollArea || !clinicalPanel) return;

  const hasInternalOverflow = scrollArea.scrollHeight - scrollArea.clientHeight > 8;
  if (hasInternalOverflow) {
    scrollArea.scrollTo({
      top: Math.max(0, scrollArea.scrollHeight - scrollArea.clientHeight),
      behavior: "smooth"
    });
    return;
  }

  const panelRect = clinicalPanel.getBoundingClientRect();
  const pageScroller = document.body.scrollHeight > document.documentElement.scrollHeight
    ? document.body
    : (document.scrollingElement || document.documentElement);
  pageScroller.scrollTo({
    top: Math.max(0, pageScroller.scrollTop + panelRect.bottom - window.innerHeight),
    behavior: "smooth"
  });
}

function updateClinicalScrollEndButton() {
  const scrollArea = $(".clinical-panel .panel-scroll");
  const clinicalPanel = $(".clinical-panel");
  const button = $("#clinicalScrollEndBtn");
  const historyPanel = $('[data-panel="historia"]');
  if (!scrollArea || !clinicalPanel || !button) return;

  const internalOverflow = scrollArea.scrollHeight - scrollArea.clientHeight > 8;
  const panelRect = clinicalPanel.getBoundingClientRect();
  const remaining = internalOverflow
    ? scrollArea.scrollHeight - scrollArea.clientHeight - scrollArea.scrollTop
    : panelRect.bottom - window.innerHeight;
  const isRelevantViewport = internalOverflow || (panelRect.top < window.innerHeight && panelRect.bottom > 0);
  const shouldShow = Boolean(historyPanel?.classList.contains("active") && isRelevantViewport && remaining > 12);
  button.hidden = !shouldShow;
}

function setRightTab(tabName) {
  const requestedTab = $(`[data-right-tab="${tabName}"]`);
  const fallbackTab = $('[data-right-tab]:not([data-access-hidden="true"])');
  const activeTab = requestedTab && requestedTab.dataset.accessHidden !== "true"
    ? tabName
    : fallbackTab?.dataset.rightTab || "studies";
  $$("[data-right-tab]").forEach((tab) => {
    const active = tab.dataset.rightTab === activeTab;
    tab.classList.toggle("active", active);
    tab.setAttribute("aria-selected", String(active));
  });
  $$("[data-right-panel]").forEach((panel) => {
    const active = panel.dataset.rightPanel === activeTab;
    panel.classList.toggle("active", active);
    panel.setAttribute("aria-hidden", String(!active));
  });
  renderRightTimeline();
  if (activeTab === "protocols") loadProtocols();
  if (activeTab === "tools") { setToolTab($('[data-tool-tab].active')?.dataset.toolTab || 'guides'); }
  if (activeTab === "research") loadResearchTemplates().finally(renderResearchPanel);
  if (activeTab === "settings") loadSystemConfig();
  if (activeTab === "care") refreshCareWorkspace();
}

function setToolTab(tabName){
  $$('[data-tool-tab]').forEach((button)=>{const active=button.dataset.toolTab===tabName;button.classList.toggle('active',active);button.setAttribute('aria-selected',String(active))});
  $$('[data-tool-pane]').forEach((pane)=>{const active=pane.dataset.toolPane===tabName;pane.classList.toggle('active',active);pane.setAttribute('aria-hidden',String(!active))});
  if(tabName==='guides')loadGuideLibrary();
  if(tabName==='tnm'){$('#tnmDate').value ||= today();loadAjcc8Catalog()}
  if(tabName==='calculators')renderCalculatorFields();
  refreshIcons();
}

async function loadGuideLibrary(force=false){
  if(localGuides.length&&!force){renderGuideLibrary();return}
  const list=$("#guideList");if(list)list.innerHTML='<div class="protocol-loading"><i data-lucide="loader-circle"></i><span>Actualizando biblioteca...</span></div>';refreshIcons();
  try{const response=await fetch(`/api/guides?t=${Date.now()}`);const payload=await response.json();if(!response.ok)throw new Error(payload.error||"No se pudieron cargar las guias");localGuides=payload.guides||[];renderGuideLibrary();if(force)toast(`${localGuides.length} guias locales`)}
  catch(error){list.innerHTML=`<div class="protocol-empty"><strong>No se pudo abrir la biblioteca</strong><span>${escapeHtml(error.message)}</span></div>`}
}

function renderGuideLibrary(){
  const list=$("#guideList");if(!list)return;const query=normalizeSearchText($("#guideSearch")?.value||"");
  const guides=localGuides.filter((item)=>!query||normalizeSearchText([item.title,item.site,item.source,item.audience].join(" ")).includes(query));
  list.innerHTML=guides.map((item)=>`<button class="guide-card" type="button" data-guide-name="${escapeAttr(item.name)}"><span class="guide-card-icon"><i data-lucide="book-open"></i></span><span class="guide-card-copy"><small>${escapeHtml(item.source)} · ${escapeHtml(item.site)}</small><strong>${escapeHtml(item.title)}</strong><em>${escapeHtml(item.audience)} · ${formatFileSize(item.size)}</em></span><i data-lucide="chevron-right"></i></button>`).join("")||'<div class="protocol-empty"><i data-lucide="book-x"></i><strong>Sin guias</strong><span>No hay documentos que coincidan con la busqueda.</span></div>';refreshIcons();
}

function formatFileSize(bytes){const value=Number(bytes)||0;return value>=1048576?`${(value/1048576).toFixed(1)} MB`:`${Math.max(1,Math.round(value/1024))} KB`}

function openGuidePdf(name){
  const guide=localGuides.find((item)=>item.name===name);if(!guide)return;
  $("#guidePdfTitle").textContent=guide.title;$("#guidePdfSource").textContent=`${guide.source} · ${guide.site}`;$("#guidePdfFrame").src=guide.url;
  $("#guidePdfModal").classList.add("open");$("#guidePdfModal").setAttribute("aria-hidden","false");document.body.classList.add("modal-open");
}

function closeGuidePdf(){
  const modal=$("#guidePdfModal");if(!modal?.classList.contains("open"))return;modal.classList.remove("open");modal.setAttribute("aria-hidden","true");$("#guidePdfFrame").src="about:blank";document.body.classList.remove("modal-open");
}

async function loadAjcc8Catalog(){
  if(ajcc8Catalog)return;
  try{const response=await fetch("/api/ajcc8");const payload=await response.json();if(!response.ok)throw new Error(payload.error||"Base AJCC 8 no disponible");ajcc8Catalog=payload;
    const groups=payload.sites.reduce((map,item)=>{const group=item.group||"Otros";(map[group]||=[]).push(item);return map},{});
    $("#tnmPrimarySite").innerHTML='<option value="">Seleccione el sitio tumoral</option>'+Object.entries(groups).map(([group,items])=>`<optgroup label="${escapeAttr(group)}">${items.map((item)=>`<option value="${escapeAttr(item.id)}">${escapeHtml(item.name)}</option>`).join("")}</optgroup>`).join("");
  }catch(error){$("#tnmPrimarySite").innerHTML='<option value="">No se pudo cargar AJCC 8</option>';toast(error.message)}
}

async function loadAjcc8Site(){
  const id=$("#tnmPrimarySite").value;if(!id){ajcc8Site=null;resetAjcc8Selectors();return}
  try{const response=await fetch(`/api/ajcc8/detail?id=${encodeURIComponent(id)}`);const payload=await response.json();if(!response.ok)throw new Error(payload.error||"Sitio AJCC 8 no disponible");ajcc8Site=payload;renderAjcc8Selectors();await calculateAjcc8Stage()}
  catch(error){ajcc8Site=null;resetAjcc8Selectors();toast(error.message)}
}

function handleAjcc8Change(event){
  if(event.target.id==="tnmPrimarySite"){loadAjcc8Site();return}
  if(event.target.id==="tnmPrefix"){renderAjcc8Selectors();calculateAjcc8Stage();return}
  renderAjcc8Definitions();window.clearTimeout(ajcc8StageTimer);ajcc8StageTimer=window.setTimeout(calculateAjcc8Stage,80);
}

function resetAjcc8Selectors(){
  ["#tnmT","#tnmN","#tnmM"].forEach((selector)=>{$(selector).disabled=true;$(selector).innerHTML='<option value="">Seleccione el sitio</option>'});
  $("#tnmSpecificFactors").hidden=true;$("#tnmSpecificFactors").innerHTML="";renderAjcc8Definitions();
  $("#tnmResult").innerHTML='<span>Resultado</span><strong>Seleccione un sitio tumoral</strong><small>AJCC Cancer Staging Manual, 8.ª edicion</small>';
}

function renderAjcc8Selectors(){
  if(!ajcc8Site){resetAjcc8Selectors();return}
  const prefix=$("#tnmPrefix").value,classification=prefix.includes("p")?"p":"c";
  ["T","N","M"].forEach((axis)=>{const select=$(`#tnm${axis}`),previous=select.value;let categories=ajcc8Site.axes[axis]?.categories||[];
    if(axis==="T"&&categories.some((item)=>/^[cp]T/.test(item.code)))categories=categories.filter((item)=>item.code.startsWith(classification+"T"));
    select.disabled=false;select.innerHTML='<option value="">Seleccione</option>'+categories.map((item)=>`<option value="${escapeAttr(item.code)}">${escapeHtml(item.code)}</option>`).join("");if([...select.options].some((item)=>item.value===previous))select.value=previous;
  });
  const excluded=new Set(["T","N","M","Classification","DescY","DescR","DescM"]),extras=Object.entries(ajcc8Site.axes).filter(([key])=>!excluded.has(key));
  const container=$("#tnmSpecificFactors");container.hidden=!extras.length;container.innerHTML=extras.length?'<h4>Factores necesarios para agrupar el estadio</h4>'+extras.map(([key,axis])=>`<label><span>${escapeHtml(axis.label||key)}</span><select data-ajcc-axis="${escapeAttr(key)}"><option value="">Seleccione</option>${(axis.categories||[]).map((item)=>`<option value="${escapeAttr(item.code)}">${escapeHtml(item.code)} — ${escapeHtml(item.description)}</option>`).join("")}</select></label>`).join(""):"";
  renderAjcc8Definitions();
}

function renderAjcc8Definitions(){
  ["T","N","M"].forEach((axis)=>{const value=$(`#tnm${axis}`)?.value,item=ajcc8Site?.axes?.[axis]?.categories?.find((entry)=>entry.code===value);$(`#tnm${axis}Definition`).textContent=item?`${item.code} = ${item.description}`:""});
}

async function calculateAjcc8Stage(){
  if(!ajcc8Site)return;const prefix=$("#tnmPrefix").value,values={T:$("#tnmT").value,N:$("#tnmN").value,M:$("#tnmM").value,Classification:prefix.includes("p")?"p":"c",DescY:prefix.includes("y")?"Yes":"No",DescR:prefix==="r"?"Yes":"No",DescM:"No"};
  $$("[data-ajcc-axis]").forEach((select)=>values[select.dataset.ajccAxis]=select.value);
  const result=$("#tnmResult"),selected=[values.T,values.N,values.M].filter(Boolean).join(" ");
  if(!values.T||!values.N||!values.M){result.innerHTML=`<span>${escapeHtml(ajcc8Site.name)} · AJCC 8</span><strong>${escapeHtml(selected||"Seleccione T, N y M")}</strong><small>Complete los tres componentes para agrupar el estadio.</small>`;return}
  try{const response=await fetch("/api/ajcc8/stage",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({id:ajcc8Site.id,values})});const payload=await response.json();
    if(!response.ok)throw new Error(payload.error||"No se pudo agrupar el estadio");
    result.innerHTML=`<span>${escapeHtml(ajcc8Site.name)} · AJCC 8 · cálculo local</span><strong>${escapeHtml(selected)}${payload.stage?` · Estadio ${escapeHtml(payload.stage)}`:""}</strong><small>${payload.stage?"Agrupación determinística según la matriz del sitio.":`Faltan datos: ${escapeHtml((payload.missing||[]).join(" · ")||"combinación no contemplada")}`}</small>`;
  }catch(error){result.innerHTML=`<span>Resultado</span><strong>${escapeHtml(selected)}</strong><small>${escapeHtml(error.message)}</small>`}
}

function renderCalculatorFields(){
  const type=$('#calculatorType')?.value||'bsa',container=$('#calculatorFields');if(!container)return;
  const number=(id,label,unit,placeholder)=>`<label><span>${label}</span><div class="calculator-input"><input id="${id}" type="number" min="0" step="any" placeholder="${placeholder}"><small>${unit}</small></div></label>`;
  if(type==='bsa')container.innerHTML=`<div class="calculator-grid">${number('calcWeight','Peso','kg','70')}${number('calcHeight','Altura','cm','170')}</div>`;
  else if(type==='bmi')container.innerHTML=`<div class="calculator-grid">${number('calcWeight','Peso','kg','70')}${number('calcHeight','Altura','cm','170')}</div>`;
  else container.innerHTML=`<div class="calculator-grid">${number('calcAuc','AUC objetivo','','5')}${number('calcGfr','Filtrado glomerular','ml/min','80')}</div><p class="calculator-note">Dosis = AUC × (filtrado glomerular + 25). Verifique el metodo usado para estimar la funcion renal.</p>`;
  $('#calculatorResult').innerHTML='<span>Resultado</span><strong>—</strong>';
}

function calculateClinicalTool(){
  const type=$('#calculatorType').value,result=$('#calculatorResult');let value='',detail='';
  if(type==='bsa'){const w=Number($('#calcWeight')?.value),h=Number($('#calcHeight')?.value);if(w>0&&h>0){value=`${Math.sqrt(w*h/3600).toFixed(2)} m²`;detail='Formula de Mosteller'}}
  if(type==='bmi'){const w=Number($('#calcWeight')?.value),h=Number($('#calcHeight')?.value)/100;if(w>0&&h>0){const bmi=w/(h*h);value=`${bmi.toFixed(1)} kg/m²`;detail=bmi<18.5?'Bajo peso':bmi<25?'Rango habitual':bmi<30?'Sobrepeso':'Obesidad'}}
  if(type==='calvert'){const auc=Number($('#calcAuc')?.value),gfr=Number($('#calcGfr')?.value);if(auc>0&&gfr>=0){value=`${Math.round(auc*(gfr+25))} mg`;detail='Dosis total estimada de carboplatino'}}
  result.innerHTML=`<span>Resultado</span><strong>${escapeHtml(value||'—')}</strong>${detail?`<small>${escapeHtml(detail)}</small>`:''}`;
}

function updateRightTabLabels() {
  const tabsContainer = $(".right-panel-tabs");
  if (!tabsContainer) return;

  const tabs = $$("[data-right-tab]", tabsContainer);
  tabs.forEach((tab) => {
    tab.dataset.fullLabel ||= tab.textContent.trim();
    const label = $(".right-tab-label", tab);
    if (label) label.textContent = tab.dataset.fullLabel;
    tab.title = tab.dataset.fullLabel;
    tab.setAttribute("aria-label", tab.dataset.fullLabel);
  });
  tabsContainer.classList.remove("compact", "icon-only");
  const measurementContext = document.createElement("canvas").getContext("2d");
  const requiredWidth = tabs.reduce((total, tab) => {
    const label = $(".right-tab-label", tab);
    if (!label) return total;
    const style = window.getComputedStyle(tab);
    const horizontalPadding = (Number.parseFloat(style.paddingLeft) || 0) + (Number.parseFloat(style.paddingRight) || 0);
    if (measurementContext) measurementContext.font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`;
    const labelWidth = measurementContext?.measureText(tab.dataset.fullLabel).width || label.scrollWidth;
    return total + labelWidth + horizontalPadding + 4;
  }, 0);
  tabsContainer.classList.toggle("icon-only", requiredWidth > tabsContainer.clientWidth);
}

function printClinicalDocument() {
  setActiveTab("historia");
  preparePrintDocument();
  window.print();
}

function preparePrintDocument() {
  if (!state) return;
  collectBindableFields();
  updateDerivedFields();
  printTimestamp = new Date();
  $("#clinicalDocument").innerHTML = buildClinicalDocumentHtml({
    editable: false,
    printMode: true,
    printedAt: printTimestamp
  });
  renderPersistentClinicalHighlights($("#clinicalDocument"));
}

function restorePrintDocument() {
  if (!state) return;
  printTimestamp = null;
  renderPreview();
}

function addEvolution() {
  collectBindableFields();
  const text = $("#evolutionText").value.trim();
  if (!text) {
    toast("Evolucion vacia");
    return;
  }

  if (editingEvolutionId) {
    const entry = state.evolutions.find((item) => item.id === editingEvolutionId);
    if (!entry) {
      toast("Evolucion no encontrada");
      editingEvolutionId = null;
      updateEvolutionModalState();
      return;
    }

    entry.history ||= [];
    entry.history.push({
      id: makeId("evo-version"),
      action: "modificado",
      at: entry.updatedAt || entry.createdAt || new Date().toISOString(),
      date: entry.date,
      author: entry.author,
      reason: entry.reason,
      text: entry.text,
      highlighted: Boolean(entry.highlighted),
      attachments: normalizeEvolutionAttachments(entry.attachments).map((attachment) => ({ ...attachment })),
      audit: entry.audit
    });
    entry.date = $("#evolutionDate").value || today();
    entry.datePrecision = "day";
    entry.author = $("#evolutionAuthor").value.trim() || state.meta.currentUser || "Profesional";
    entry.reason = $("#evolutionReason").value.trim() || "Oncologia";
    entry.text = text;
    entry.highlighted = $("#evolutionHighlighted").checked;
    entry.attachments = evolutionDraftAttachments.map((attachment) => ({ ...attachment, caption: attachment.caption || text }));
    const audit = buildAuditStamp("modificado", { lastName: extractLastName(entry.author) });
    entry.audit = audit;
    entry.updatedAt = audit.at;
    entry.createdAt ||= audit.at;
    entry.linkedStudyIds = [...new Set(entry.attachments.map((attachment) => attachment.studyId).filter(hasText))];

    state.meta.updatedAt = new Date().toISOString();
    invalidateAiTimeline();
    editingEvolutionId = null;
    clearEvolutionForm();
    renderPreview();
    renderPatientOutputs();
    persistStateSilently();
    closeEvolutionModal();
    toast("Evolucion modificada");
    return;
  }

  const audit = buildAuditStamp("cargado", { lastName: extractLastName($("#evolutionAuthor").value.trim() || state.meta.currentUser) });
  state.evolutions.unshift({
    id: makeId("evo"),
    date: today(),
    datePrecision: "day",
    author: $("#evolutionAuthor").value.trim() || state.meta.currentUser || "Profesional",
    reason: $("#evolutionReason").value.trim() || "Oncologia",
    text,
    highlighted: false,
    attachments: evolutionDraftAttachments.map((attachment) => ({ ...attachment, caption: attachment.caption || text })),
    linkedStudyIds: [...new Set(evolutionDraftAttachments.map((attachment) => attachment.studyId).filter(hasText))],
    audit,
    createdAt: audit.at,
    updatedAt: audit.at
  });

  state.meta.updatedAt = new Date().toISOString();
  invalidateAiTimeline();
  clearEvolutionForm();
  renderPreview();
  renderPatientOutputs();
  persistStateSilently();
  closeEvolutionModal();
  toast("Evolucion cargada");
}

function deleteEditingEvolution() {
  if (!editingEvolutionId) return;
  const entry = state.evolutions.find((item) => item.id === editingEvolutionId);
  if (!entry) {
    editingEvolutionId = null;
    updateEvolutionModalState();
    toast("Evolucion no encontrada");
    return;
  }

  const deletionAudit = buildAuditStamp("eliminado", { lastName: extractLastName(entry.author) });
  entry.history ||= [];
  entry.history.push({id:makeId("evo-version"),action:"eliminado",at:deletionAudit.at,date:entry.date,author:entry.author,reason:entry.reason,text:entry.text,highlighted:Boolean(entry.highlighted),attachments:normalizeEvolutionAttachments(entry.attachments).map((attachment)=>({...attachment})),audit:deletionAudit});
  entry.deleted = true;
  entry.deletedAt = deletionAudit.at;
  entry.deletionAudit = deletionAudit;
  state.meta.updatedAt = new Date().toISOString();
  editingEvolutionId = null;
  clearEvolutionForm();
  renderPreview();
  renderPatientOutputs();
  queueLocalSave();
  closeEvolutionModal();
  toast("Evolucion eliminada");
}

function clearEvolutionForm() {
  editingEvolutionId = null;
  $("#evolutionDate").value = today();
  $("#evolutionReason").value = "Oncologia";
  $("#evolutionText").value = "";
  $("#evolutionHighlighted").checked = false;
  evolutionDraftAttachments = [];
  renderEvolutionAttachmentBand();
  setEvolutionMode("evolucion");
  updateEvolutionModalState();
}

function buildEvolutionDraft() {
  collectBindableFields();
  if (evolutionMode === "hc") {
    $("#evolutionText").value = getClinicalDocumentText();
    $("#evolutionReason").value ||= "Oncologia";
    toast("HC completa cargada");
    return;
  }

  const selectedStudy = state.studies.find((study) => study.id === selectedStudyId);
  const latestStudies = (selectedStudy ? [selectedStudy] : state.studies.slice(0, 3))
    .map((study) => [
      withFinalPeriod([formatDateOptional(study.date, study.datePrecision), study.type, study.title].filter(hasText).join(" - ")),
      study.summary || getStudySourceLabel(study)
    ].filter(hasText).join("\n"))
    .join("\n\n");

  const parts = [
    state.narrative.chiefComplaint && `Motivo de consulta: ${state.narrative.chiefComplaint}`,
    state.narrative.currentIllness && `Enfermedad actual: ${state.narrative.currentIllness}`,
    latestStudies && `Estudios:\n${latestStudies}`,
    state.narrative.physicalExam && `Examen fisico al ingreso:\n${formatPhysicalExamPlainText(state.narrative.physicalExam) || state.narrative.physicalExam}`,
    state.narrative.plan && `Conducta: ${state.narrative.plan}`
  ].filter(Boolean);

  $("#evolutionText").value = parts.join("\n\n");
  toast("Texto armado");
}

function prefillPhysicalExam() {
  const field = $('[data-field="narrative.physicalExam"]');
  if (!field || field.value.trim()) {
    toast("Examen fisico ya tiene texto");
    return;
  }

  field.value = DEFAULT_PHYSICAL_EXAM_TEXT;
  syncBindableSiblings(field);
  collectBindableFields();
  renderPreview();
  queueLocalSave();
  toast("Plantilla agregada");
}

function getClinicalDocumentText() {
  return buildClinicalDocumentPlainText();
}

function buildClinicalDocumentPlainText({ includePatientIdentity = true } = {}) {
  const patient = state.patient;
  const oncology = state.oncology;
  const narrative = state.narrative;
  const blocks = [
    "Historia clinica oncologica",
    includePatientIdentity ? (patient.fullName || "Paciente") : "Paciente [identidad omitida]",
    (includePatientIdentity ? [
      plainMeta("HC", patient.medicalRecord),
      plainMeta("DNI", patient.dni),
      plainMeta("ID clinico", patient.liraId),
      plainMeta("Fecha nac.", formatDateOptional(patient.birthDate, patient.birthDatePrecision)),
      plainMeta("Sexo", patient.sex),
      plainMeta("Fecha de fallecimiento", formatDateOptional(patient.deathDate, patient.deathDatePrecision)),
      plainMeta("Obra social", patient.insurance),
      plainMeta("Afiliado", patient.affiliateNumber),
      plainMeta("Otras coberturas", formatAdditionalCoverages(patient)),
      plainMeta("Telefono", patient.phone)
    ] : [plainMeta("Sexo", patient.sex)]).filter(Boolean).join("\n"),
    getSectionCurrentText("diagnosis"),
    plainSection("Motivo de consulta", getSectionCurrentText("chiefComplaint")),
    plainSection("Antecedentes de enfermedad actual", getSectionCurrentText("currentIllness")),
    plainSection("Antecedentes personales", getSectionCurrentText("personalHistory")),
    plainSection("Estudios complementarios", getSectionCurrentText("studies")),
    plainSection("Examen fisico", getSectionCurrentText("physicalExam")),
    plainSection("Tratamientos sistemicos", getSectionCurrentText("systemicTreatments")),
    plainSection("Tratamientos radioterapicos", getSectionCurrentText("radiotherapyTreatments")),
    plainSection("Cirugias oncologicas", getSectionCurrentText("oncologicSurgeries")),
    plainSection("Conclusion / resumen", getSectionCurrentText("summaryPlan")),
    plainSection("Investigacion clinica", renderResearchRecordsPlainText()),
    plainSection("Evoluciones", getSectionCurrentText("evolutions")),
    plainSection("Prescripciones e indicaciones", renderPrescriptionsPlainText()),
    plainSection(
      "Clasificacion diagnostica",
      getVisibleDiagnosticSystems()
        .map((system) => {
          const classification = diagnosticClassificationText(
            getDiagnosticClassifications()[system],
            { includeSystem: true, system }
          );
          const tnm = system === "ajcc" ? diagnosticTnmText() : "";
          return [classification, tnm && `TNM: ${tnm}`].filter(hasText).join("\n");
        })
        .filter(hasText)
        .join("\n")
    )
  ];

  return blocks.filter(hasText).join("\n\n").replace(/\n{3,}/g, "\n\n").trim();
}

function getClinicalDocumentForLlm() {
  return redactDirectPatientIdentifiers(buildClinicalDocumentPlainText({ includePatientIdentity: false }));
}

function redactDirectPatientIdentifiers(value) {
  let text = String(value || "");
  const patient = state?.patient || {};
  const fullName = String(patient.fullName || "").trim();
  const nameParts = fullName.split(/[,\s]+/).map((part) => part.trim()).filter((part) => part.length >= 4);
  const nameVariants = new Set([fullName]);
  if (fullName.includes(",")) {
    const [lastName, ...rest] = fullName.split(",");
    const firstNames = rest.join(" ").trim();
    if (lastName.trim() && firstNames) {
      nameVariants.add(`${firstNames} ${lastName.trim()}`);
      nameVariants.add(`${lastName.trim()} ${firstNames}`);
    }
  }
  nameParts.forEach((part) => nameVariants.add(part));

  const replacements = [
    ...Array.from(nameVariants).filter(hasText).map((item) => [item, "[paciente]"]),
    [patient.address, "[domicilio omitido]"],
    [patient.email, "[email omitido]"],
    [patient.birthDate, "[fecha de nacimiento omitida]"],
    [formatDateOptional(patient.birthDate, patient.birthDatePrecision), "[fecha de nacimiento omitida]"],
    [patient.deathDate, "[fecha personal omitida]"],
    [formatDateOptional(patient.deathDate, patient.deathDatePrecision), "[fecha personal omitida]"]
  ].filter(([item]) => String(item || "").trim().length >= 3)
    .sort((a, b) => String(b[0]).length - String(a[0]).length);

  replacements.forEach(([item, replacement]) => {
    text = text.replace(new RegExp(escapeRegExpLiteral(String(item).trim()), "gi"), replacement);
  });
  text = text.replace(
    /(^|\n)(\s*(?:DNI|documento|n[uú]mero de documento|HC|historia cl[ií]nica|ID Lira|ID paciente|tel[eé]fono|celular|domicilio|direcci[oó]n|email|correo|afiliado|n[uú]mero de afiliado)\s*:\s*)[^\n]*/gim,
    "$1$2[identificador omitido]"
  );
  [
    [patient.dni, "[documento omitido]"],
    [patient.phone, "[telefono omitido]"]
  ].forEach(([item, replacement]) => {
    const digits = String(item || "").replace(/\D/g, "");
    if (digits.length < 6) return;
    const loosePattern = digits.split("").map(escapeRegExpLiteral).join("[\\s.()/-]*");
    text = text.replace(new RegExp(loosePattern, "g"), replacement);
  });
  return text;
}

function escapeRegExpLiteral(value) {
  return String(value || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function getTimelineEventsForLlm() {
  const events = Array.isArray(state?.meta?.aiTimelineEvents) ? state.meta.aiTimelineEvents : [];
  try {
    return JSON.parse(redactDirectPatientIdentifiers(JSON.stringify(events)));
  } catch {
    return [];
  }
}

function plainSection(title, value) {
  return hasText(value) ? `${title}\n${value}` : "";
}

function plainMeta(label, value) {
  return hasText(value) ? `${label}: ${value}` : "";
}

function handleTimelineAction(event) {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const entry = state.evolutions.find((item) => item.id === button.dataset.id);
  if (!entry) return;

  if (button.dataset.action === "view-evolution-attachment") {
    const attachment = normalizeEvolutionAttachments(entry.attachments).find((item) => item.id === button.dataset.attachmentId);
    if (attachment) openEvolutionAttachmentViewer(attachment);
    return;
  }

  if (button.dataset.action === "edit-evolution") {
    openEvolutionModal("evolucion", entry);
  }
}

function updateStudyClipboardPointerContext(event) {
  const panelBody = $("#studiesPanel .right-panel-body");
  const path = typeof event.composedPath === "function" ? event.composedPath() : [];
  studyClipboardPointerInside = Boolean(
    panelBody && (path.includes(panelBody) || panelBody.contains(event.target))
  );
  if (Number.isFinite(event.clientX) && Number.isFinite(event.clientY)) {
    studyClipboardPointerX = event.clientX;
    studyClipboardPointerY = event.clientY;
  }
}

function handleStudyClipboardPointerOut(event) {
  if (event.relatedTarget) return;
  studyClipboardPointerInside = false;
  studyClipboardPointerX = null;
  studyClipboardPointerY = null;
}

function isStudyClipboardPointerInside() {
  const panelBody = $("#studiesPanel .right-panel-body");
  if (!panelBody) return false;
  const bounds = panelBody.getBoundingClientRect();
  if (bounds.width <= 0 || bounds.height <= 0) return false;
  if (studyClipboardPointerInside) return true;
  if (!Number.isFinite(studyClipboardPointerX) || !Number.isFinite(studyClipboardPointerY)) return false;
  const pointedElement = document.elementFromPoint(studyClipboardPointerX, studyClipboardPointerY);
  return Boolean(pointedElement && (pointedElement === panelBody || panelBody.contains(pointedElement)));
}

function handleStudyClipboardPaste(event) {
  if (!isStudyClipboardPasteContext()) return;
  const files = getStudyClipboardImageFiles(event.clipboardData);
  if (!files.length) return;
  event.preventDefault();
  if (studyUploadBusy) {
    toast("Espere a que termine la carga actual");
    return;
  }
  openStudyUploadModal(files);
  toast(`${files.length} ${files.length === 1 ? "imagen pegada" : "imagenes pegadas"} desde el portapapeles`);
}

function isStudyClipboardPasteContext() {
  const modal = $("#studyUploadModal");
  if (modal?.classList.contains("open")) return true;
  if ($(".modal-backdrop.open:not(#studyUploadModal)")) return false;
  const panel = $('[data-right-panel="studies"]');
  const studiesSelected = $('[data-right-tab="studies"]')?.getAttribute("aria-selected") === "true";
  return Boolean(
    studiesSelected &&
    panel?.classList.contains("active") &&
    panel.getAttribute("aria-hidden") !== "true" &&
    isStudyClipboardPointerInside()
  );
}

function getStudyClipboardImageFiles(clipboardData) {
  if (!clipboardData) return [];
  const itemFiles = Array.from(clipboardData.items || [])
    .filter((item) => item.kind === "file")
    .map((item) => item.getAsFile?.())
    .filter(Boolean);
  const candidates = itemFiles.length ? itemFiles : Array.from(clipboardData.files || []);
  return candidates
    .map((file) => normalizeStudyClipboardImage(file))
    .filter(Boolean);
}

function normalizeStudyClipboardImage(file) {
  const declaredMime = String(file?.type || "").trim().toLowerCase();
  let format = STUDY_CLIPBOARD_IMAGE_FORMATS.get(declaredMime);
  if (!format) {
    const extension = getStudyFileExtension(file?.name);
    if (getStudyUploadCategory(extension) !== "image") return null;
    format = { extension, mime: declaredMime || "application/octet-stream" };
  }
  const now = new Date();
  const timestamp = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0"),
    "-",
    String(now.getHours()).padStart(2, "0"),
    String(now.getMinutes()).padStart(2, "0"),
    String(now.getSeconds()).padStart(2, "0")
  ].join("");
  studyClipboardSequence += 1;
  const sequence = String(studyClipboardSequence).padStart(2, "0");
  return new File([file], `imagen-portapapeles-${timestamp}-${sequence}.${format.extension}`, {
    type: format.mime,
    lastModified: now.getTime()
  });
}

function resolveStudyTemplateAssetPath(value) {
  const raw = String(value || "").trim().replace(/\\/g, "/");
  if (!raw || raw.includes("..") || /[?#\u0000-\u001f\u007f]/.test(raw)) return "";
  const relative = raw.replace(/^\.?\//, "");
  const pathname = raw.startsWith("/")
    ? raw
    : /^(?:assets\/study-templates|api\/media\/images)\//i.test(relative)
      ? `/${relative}`
      : `/assets/study-templates/${relative}`;
  const bundledAsset = /^\/assets\/study-templates\/(?:[A-Za-z0-9][A-Za-z0-9._-]*\/)*[A-Za-z0-9][A-Za-z0-9._-]*$/;
  const managedAsset = /^\/api\/media\/images\/[A-Za-z0-9][A-Za-z0-9._-]{0,179}$/;
  return bundledAsset.test(pathname) || managedAsset.test(pathname) ? pathname : "";
}

function normalizeStudyTemplateEntry(entry, index) {
  if (!entry || typeof entry !== "object" || Array.isArray(entry)) return null;
  const origin = String(entry.origin || "").trim().toLowerCase() === "custom" ? "custom" : "builtin";
  const configurationIdValue = String(entry.configurationId || "").trim();
  const configurationId = /^[1-9]\d{0,17}$/.test(configurationIdValue) ? configurationIdValue : "";
  const id = String(entry.id || (origin === "custom" && configurationId
    ? `custom-${configurationId}`
    : `template-${index + 1}`)).trim().toLowerCase();
  const title = String(entry.title || entry.name || "").trim();
  const category = String(entry.category || "general").trim().toLowerCase();
  const file = resolveStudyTemplateAssetPath(entry.file || entry.path || entry.image);
  const thumbnail = resolveStudyTemplateAssetPath(entry.thumbnail || entry.thumbnailFile || file);
  const sourceUrl = String(entry.sourceUrl || entry.sourcePage || entry.source || "").trim();
  const sourceLabel = String(entry.sourceLabel || entry.definition?.sourceLabel || "").trim();
  const author = String(entry.author || "").trim();
  const license = String(entry.license || "").trim();
  const licenseUrl = String(entry.licenseUrl || "").trim();
  const attribution = String(entry.attribution || [author, license].filter(Boolean).join(" · ")).trim();
  const sha256 = String(entry.sha256 || "").trim().toLowerCase();
  const tags = Array.isArray(entry.tags) ? entry.tags.map((tag) => String(tag || "").trim()).filter(Boolean) : [];
  const revisionValue = Number(entry.revision);
  const revision = Number.isSafeInteger(revisionValue) && revisionValue > 0 ? revisionValue : 0;
  const available = entry.available !== false;
  const availabilityReason = String(entry.availabilityReason || "").trim();
  if (!/^[a-z0-9][a-z0-9._-]{1,80}$/.test(id) || !title || !file || !thumbnail) return null;
  if (origin === "custom" && (!file.startsWith("/api/media/images/") || !thumbnail.startsWith("/api/media/images/"))) return null;
  if (origin !== "custom" && !/^https:\/\//i.test(sourceUrl)) return null;
  if (sourceUrl && !/^https:\/\//i.test(sourceUrl)) return null;
  if (licenseUrl && !/^https:\/\//i.test(licenseUrl)) return null;
  if ((origin === "custom" || sha256) && !/^[a-f0-9]{64}$/.test(sha256)) return null;
  return {
    id,
    title,
    category,
    file,
    thumbnail,
    sourceUrl,
    sourceLabel,
    author,
    license,
    licenseUrl,
    attribution,
    sha256,
    tags,
    origin,
    configurationId,
    revision,
    available,
    availabilityReason
  };
}

function getStudyTemplateCategoryLabel(category) {
  const labels = {
    "cuerpo-completo": "Cuerpo completo",
    cuerpo: "Cuerpo completo",
    ginecologia: "Ginecología",
    urologia: "Urología",
    "cabeza-cuello": "Cabeza y cuello",
    torax: "Tórax",
    abdomen: "Abdomen y pelvis",
    "abdomen-pelvis": "Abdomen y pelvis",
    extremidades: "Extremidades",
    "sistema-nervioso": "Sistema nervioso",
    "sistema-musculoesqueletico": "Sistema musculoesquelético",
    "organos-individuales": "Órganos individuales",
    organos: "Órganos individuales",
    general: "Anatomía general"
  };
  if (labels[category]) return labels[category];
  return String(category || "general")
    .replace(/[-_]+/g, " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

async function loadStudyTemplates() {
  if (studyTemplatesLoaded) return studyTemplates;
  let unique = [];
  try {
    const response = await fetch("/api/study-templates", { cache: "no-store" });
    const payload = await response.json().catch(() => null);
    if (!response.ok || !payload) throw new Error("No se pudo abrir el catálogo integrado");
    const entries = Array.isArray(payload) ? payload : payload.templates;
    if (!Array.isArray(entries)) throw new Error("El catálogo integrado no es válido");
    const normalized = entries.map(normalizeStudyTemplateEntry).filter(Boolean);
    const seen = new Set();
    unique = normalized.filter((template) => {
      if (seen.has(template.id)) return false;
      seen.add(template.id);
      return true;
    });
    if (unique.filter((template) => template.origin !== "custom").length !== BUNDLED_STUDY_TEMPLATE_COUNT) {
      throw new Error("El catálogo integrado no conserva la biblioteca anatómica base");
    }
  } catch {
    const response = await fetch("/assets/study-templates/manifest.json", { cache: "no-store" });
    const payload = await response.json().catch(() => null);
    if (!response.ok || !payload) throw new Error("No se pudo abrir la biblioteca anatómica local");
    const entries = Array.isArray(payload) ? payload : payload.templates;
    if (!Array.isArray(entries)) throw new Error("El catálogo de plantillas no es válido");
    const normalized = entries.map(normalizeStudyTemplateEntry).filter(Boolean);
    const seen = new Set();
    unique = normalized.filter((template) => {
      if (seen.has(template.id)) return false;
      seen.add(template.id);
      return true;
    });
  }
  if (unique.filter((template) => template.origin !== "custom").length !== BUNDLED_STUDY_TEMPLATE_COUNT) {
    throw new Error(`La biblioteca anatómica debe contener exactamente ${BUNDLED_STUDY_TEMPLATE_COUNT} plantillas incluidas`);
  }
  studyTemplates = unique.filter((template) => template.available !== false);
  studyTemplatesLoaded = true;
  const categorySelect = $("#studyTemplateCategory");
  if (categorySelect) {
    const selected = categorySelect.value;
    const categories = [...new Set(studyTemplates.map((template) => template.category))].sort((a, b) =>
      getStudyTemplateCategoryLabel(a).localeCompare(getStudyTemplateCategoryLabel(b), "es")
    );
    categorySelect.innerHTML = `<option value="">Todas las categorías</option>${categories.map((category) =>
      `<option value="${escapeAttr(category)}">${escapeHtml(getStudyTemplateCategoryLabel(category))}</option>`
    ).join("")}`;
    if (categories.includes(selected)) categorySelect.value = selected;
  }
  return studyTemplates;
}

async function reloadOpenStudyTemplateCatalog() {
  const modal = $("#studyTemplateModal");
  if (!modal?.classList.contains("open")) return;
  renderStudyTemplateGallery();
  try {
    await loadStudyTemplates();
    if (!modal.classList.contains("open")) return;
    $("#studyTemplateError").hidden = true;
    $("#studyTemplateError").textContent = "";
    renderStudyTemplateGallery();
  } catch (error) {
    if (!modal.classList.contains("open")) return;
    $("#studyTemplateGallery").setAttribute("aria-busy", "false");
    $("#studyTemplateGallery").innerHTML = `<div class="study-template-empty"><i data-lucide="image-off"></i><strong>Biblioteca no disponible</strong><span>Revise los archivos locales de plantillas.</span></div>`;
    $("#studyTemplateError").hidden = false;
    $("#studyTemplateError").textContent = error.message || "No se pudieron cargar las plantillas";
    $("#studyTemplateCount").textContent = "Sin plantillas";
    refreshIcons();
  }
}

async function openStudyTemplateModal() {
  const modal = $("#studyTemplateModal");
  if (!modal || modal.classList.contains("open")) return;
  studyTemplateReturnFocus = document.activeElement;
  studyTemplatesLoaded = false;
  studyTemplates = [];
  studyTemplateSelectedId = "";
  studyTemplateBusy = false;
  $("#studyTemplateSearch").value = "";
  $("#studyTemplateCategory").value = "";
  $("#studyTemplateError").hidden = true;
  $("#studyTemplateError").textContent = "";
  modal.classList.add("open");
  modal.setAttribute("aria-hidden", "false");
  document.body.classList.add("modal-open");
  renderStudyTemplateGallery();
  window.requestAnimationFrame(() => $("#studyTemplateSearch")?.focus());
  try {
    await loadStudyTemplates();
    if (!modal.classList.contains("open")) return;
    renderStudyTemplateGallery();
  } catch (error) {
    if (!modal.classList.contains("open")) return;
    $("#studyTemplateGallery").setAttribute("aria-busy", "false");
    $("#studyTemplateGallery").innerHTML = `<div class="study-template-empty"><i data-lucide="image-off"></i><strong>Biblioteca no disponible</strong><span>Revise los archivos locales de plantillas.</span></div>`;
    $("#studyTemplateError").hidden = false;
    $("#studyTemplateError").textContent = error.message || "No se pudieron cargar las plantillas";
    $("#studyTemplateCount").textContent = "Sin plantillas";
    refreshIcons();
  }
}

function closeStudyTemplateModal({ force = false } = {}) {
  const modal = $("#studyTemplateModal");
  if (!modal?.classList.contains("open") || (studyTemplateBusy && !force)) return;
  modal.classList.remove("open", "is-busy");
  modal.setAttribute("aria-hidden", "true");
  modal.setAttribute("aria-busy", "false");
  studyTemplateSelectedId = "";
  studyTemplateBusy = false;
  document.body.classList.remove("modal-open");
  const returnFocus = studyTemplateReturnFocus;
  studyTemplateReturnFocus = null;
  if (!force && returnFocus?.isConnected) window.requestAnimationFrame(() => returnFocus.focus());
}

function getFilteredStudyTemplates() {
  const term = normalizeSearchText($("#studyTemplateSearch")?.value || "");
  const category = $("#studyTemplateCategory")?.value || "";
  return studyTemplates.filter((template) => {
    if (category && template.category !== category) return false;
    if (!term) return true;
    return normalizeSearchText([
      template.title,
      template.category,
      getStudyTemplateCategoryLabel(template.category),
      template.author,
      ...template.tags
    ].join(" ")).includes(term);
  });
}

function renderStudyTemplateGallery() {
  const gallery = $("#studyTemplateGallery");
  const count = $("#studyTemplateCount");
  const selection = $("#studyTemplateSelection");
  const confirm = $("#confirmStudyTemplateBtn");
  if (!gallery || !count || !selection || !confirm) return;
  if (!studyTemplatesLoaded) {
    gallery.setAttribute("aria-busy", "true");
    gallery.innerHTML = `<div class="study-template-empty"><i data-lucide="loader-circle"></i><strong>Cargando plantillas…</strong><span>Preparando miniaturas locales.</span></div>`;
    count.textContent = "Cargando biblioteca…";
    selection.innerHTML = `<span>Seleccione una miniatura</span>`;
    confirm.disabled = true;
    refreshIcons();
    return;
  }

  const templates = getFilteredStudyTemplates();
  let selected = studyTemplates.find((template) => template.id === studyTemplateSelectedId);
  if (selected && !templates.some((template) => template.id === selected.id)) {
    studyTemplateSelectedId = "";
    selected = null;
  }
  gallery.setAttribute("aria-busy", "false");
  count.textContent = `${templates.length} ${templates.length === 1 ? "plantilla" : "plantillas"}`;
  gallery.innerHTML = templates.length ? templates.map((template) => `
    <div role="listitem">
      <button class="study-template-card ${template.id === studyTemplateSelectedId ? "is-selected" : ""}" type="button" aria-pressed="${template.id === studyTemplateSelectedId ? "true" : "false"}" data-study-template-id="${escapeAttr(template.id)}">
        <span class="study-template-thumbnail"><img src="${escapeAttr(template.thumbnail)}" loading="lazy" alt=""></span>
        <span class="study-template-card-copy">
          <strong>${escapeHtml(template.title)}</strong>
          <small>${escapeHtml(getStudyTemplateCategoryLabel(template.category))}</small>
          ${template.license || template.sourceLabel ? `<em>${escapeHtml(template.license || template.sourceLabel)}</em>` : ""}
        </span>
        <i data-lucide="check"></i>
      </button>
    </div>
  `).join("") : `
    <div class="study-template-empty">
      <i data-lucide="search-x"></i>
      <strong>Sin coincidencias</strong>
      <span>Pruebe otra palabra o categoría.</span>
    </div>
  `;
  if (selected) {
    const reference = selected.sourceUrl
      ? `<a href="${escapeAttr(selected.sourceUrl)}" target="_blank" rel="noopener">${selected.license ? "Fuente y licencia" : "Fuente"}</a>`
      : selected.licenseUrl
        ? `<a href="${escapeAttr(selected.licenseUrl)}" target="_blank" rel="noopener">Licencia</a>`
        : selected.license
          ? `<span>${escapeHtml(selected.license)}</span>`
          : selected.sourceLabel
            ? `<span>${escapeHtml(selected.sourceLabel)}</span>`
            : "";
    selection.innerHTML = `
      <span>Seleccionada</span>
      <strong>${escapeHtml(selected.title)}</strong>
      ${reference}
    `;
  } else {
    selection.innerHTML = `<span>Seleccione una miniatura</span>`;
  }
  confirm.disabled = !selected || studyTemplateBusy;
  refreshIcons();
}

function handleStudyTemplateGalleryAction(event) {
  const card = event.target.closest("[data-study-template-id]");
  if (!card || studyTemplateBusy) return;
  studyTemplateSelectedId = card.dataset.studyTemplateId;
  renderStudyTemplateGallery();
}

function setStudyTemplateBusy(busy) {
  studyTemplateBusy = busy;
  const modal = $("#studyTemplateModal");
  if (modal) {
    modal.classList.toggle("is-busy", busy);
    modal.setAttribute("aria-busy", String(busy));
  }
  $("#closeStudyTemplateBtn").disabled = busy;
  $("#cancelStudyTemplateBtn").disabled = busy;
  $("#studyTemplateSearch").disabled = busy;
  $("#studyTemplateCategory").disabled = busy;
  const confirm = $("#confirmStudyTemplateBtn");
  if (confirm) {
    confirm.disabled = busy || !studyTemplateSelectedId;
    $("span", confirm).textContent = busy ? "Agregando plantilla…" : "Agregar como imagen";
  }
  $$("#studyTemplateGallery [data-study-template-id]").forEach((card) => { card.disabled = busy; });
  refreshIcons();
}

function getStudyTemplateExtension(template, contentType) {
  const pathExtension = getStudyFileExtension(String(template?.file || "").split(/[?#]/, 1)[0]);
  if (["png", "jpg", "jpeg", "gif", "webp", "avif", "bmp"].includes(pathExtension)) return pathExtension;
  return {
    "image/png": "png",
    "image/jpeg": "jpg",
    "image/gif": "gif",
    "image/webp": "webp",
    "image/avif": "avif",
    "image/bmp": "bmp"
  }[String(contentType || "").toLowerCase()] || "";
}

function sha256HexFromBytes(value) {
  const input = value instanceof Uint8Array ? value : new Uint8Array(value);
  const state = new Uint32Array([
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
  ]);
  const constants = new Uint32Array([
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
  ]);
  const paddedLength = Math.ceil((input.byteLength + 9) / 64) * 64;
  const padded = new Uint8Array(paddedLength);
  padded.set(input);
  padded[input.byteLength] = 0x80;
  const view = new DataView(padded.buffer);
  const bitLength = input.byteLength * 8;
  view.setUint32(paddedLength - 8, Math.floor(bitLength / 0x100000000) >>> 0, false);
  view.setUint32(paddedLength - 4, bitLength >>> 0, false);

  const words = new Uint32Array(64);
  const rotateRight = (word, bits) => (word >>> bits) | (word << (32 - bits));
  for (let offset = 0; offset < paddedLength; offset += 64) {
    for (let index = 0; index < 16; index += 1) {
      words[index] = view.getUint32(offset + index * 4, false);
    }
    for (let index = 16; index < 64; index += 1) {
      const first = rotateRight(words[index - 15], 7)
        ^ rotateRight(words[index - 15], 18)
        ^ (words[index - 15] >>> 3);
      const second = rotateRight(words[index - 2], 17)
        ^ rotateRight(words[index - 2], 19)
        ^ (words[index - 2] >>> 10);
      words[index] = (words[index - 16] + first + words[index - 7] + second) >>> 0;
    }

    let a = state[0];
    let b = state[1];
    let c = state[2];
    let d = state[3];
    let e = state[4];
    let f = state[5];
    let g = state[6];
    let h = state[7];
    for (let index = 0; index < 64; index += 1) {
      const upperE = rotateRight(e, 6) ^ rotateRight(e, 11) ^ rotateRight(e, 25);
      const choice = (e & f) ^ (~e & g);
      const first = (h + upperE + choice + constants[index] + words[index]) >>> 0;
      const upperA = rotateRight(a, 2) ^ rotateRight(a, 13) ^ rotateRight(a, 22);
      const majority = (a & b) ^ (a & c) ^ (b & c);
      const second = (upperA + majority) >>> 0;
      h = g;
      g = f;
      f = e;
      e = (d + first) >>> 0;
      d = c;
      c = b;
      b = a;
      a = (first + second) >>> 0;
    }
    state[0] = (state[0] + a) >>> 0;
    state[1] = (state[1] + b) >>> 0;
    state[2] = (state[2] + c) >>> 0;
    state[3] = (state[3] + d) >>> 0;
    state[4] = (state[4] + e) >>> 0;
    state[5] = (state[5] + f) >>> 0;
    state[6] = (state[6] + g) >>> 0;
    state[7] = (state[7] + h) >>> 0;
  }
  return Array.from(state, (word) => word.toString(16).padStart(8, "0")).join("");
}

async function getBlobSha256(blob) {
  if (typeof blob?.arrayBuffer !== "function") return "";
  const bytes = new Uint8Array(await blob.arrayBuffer());
  if (globalThis.crypto?.subtle) {
    try {
      const digest = await globalThis.crypto.subtle.digest("SHA-256", bytes);
      return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
    } catch {
      // En HTTP por IP SubtleCrypto puede no estar disponible; se conserva la misma verificacion local.
    }
  }
  return sha256HexFromBytes(bytes);
}

async function importSelectedStudyTemplate() {
  if (studyTemplateBusy) return;
  const template = studyTemplates.find((item) => item.id === studyTemplateSelectedId);
  if (!template) {
    toast("Seleccione una plantilla anatómica");
    return;
  }
  const patientId = getActiveLiraPatientId();
  if (!patientId) {
    $("#studyTemplateError").hidden = false;
    $("#studyTemplateError").textContent = "Abra o cree un paciente antes de agregar una plantilla.";
    return;
  }

  setStudyTemplateBusy(true);
  $("#studyTemplateError").hidden = true;
  $("#studyTemplateError").textContent = "";
  let descriptor = null;
  let record = null;
  let persisted = false;
  const clinicalStateAtStart = state;
  const previousStudies = state.studies;
  const previousSelectedStudyId = selectedStudyId;
  const hadUpdatedAt = Object.prototype.hasOwnProperty.call(state.meta || {}, "updatedAt");
  const previousUpdatedAt = state.meta?.updatedAt;
  const hadStudiesAudit = Object.prototype.hasOwnProperty.call(state.meta?.sectionAudit || {}, "studies");
  const previousStudiesAudit = state.meta?.sectionAudit?.studies;

  try {
    const sourceResponse = await fetch(template.file, { cache: "no-store" });
    if (!sourceResponse.ok) throw new Error("No se pudo abrir la plantilla local");
    const blob = await sourceResponse.blob();
    if (getActiveLiraPatientId() !== patientId) throw new Error("El paciente activo cambió durante la carga");
    const localSha256 = await getBlobSha256(blob);
    if (getActiveLiraPatientId() !== patientId) throw new Error("El paciente activo cambió durante la verificación");
    if (template.sha256 && localSha256 !== template.sha256) {
      throw new Error("La plantilla local no coincide con el catálogo verificado");
    }
    const extension = getStudyTemplateExtension(template, blob.type);
    if (!extension || !String(blob.type || "").toLowerCase().startsWith("image/")) {
      throw new Error("La plantilla no tiene un formato de imagen compatible");
    }
    const safeId = template.id.replace(/[^a-z0-9._-]+/g, "-").slice(0, 70);
    const file = new File([blob], `plantilla-${safeId}.${extension}`, {
      type: blob.type,
      lastModified: Date.now()
    });
    const entry = {
      id: makeId("template-upload"),
      studyId: makeId("est"),
      file,
      extension,
      status: "uploading",
      error: ""
    };
    descriptor = await uploadStudyFile(file, { patientId, studyId: entry.studyId });
    if (getActiveLiraPatientId() !== patientId) throw new Error("El paciente activo cambió durante la carga");
    record = buildStudyRecordFromUpload(entry, descriptor);
    record.title = template.title;
    record.type = "Plantilla anatómica";
    record.source = "Biblioteca anatómica";
    record.summary = `Plantilla anatómica para marcación clínica: ${template.title}.`;
    record.tags = [...new Set([getStudyTemplateCategoryLabel(template.category), ...template.tags])];
    record.templateSource = {
      id: template.id,
      category: template.category,
      origin: template.origin,
      ...(template.configurationId ? { configurationId: template.configurationId } : {}),
      ...(template.revision ? { revision: template.revision } : {}),
      ...(template.sourceLabel ? { sourceLabel: template.sourceLabel } : {}),
      sourceUrl: template.sourceUrl,
      author: template.author,
      license: template.license,
      licenseUrl: template.licenseUrl,
      attribution: template.attribution,
      sha256: template.sha256
    };
    if (record.attachments?.[0]) {
      record.attachments[0].templateId = template.id;
      record.attachments[0].sourceUrl = template.sourceUrl;
      record.attachments[0].license = template.license;
      record.attachments[0].templateSha256 = template.sha256;
    }

    state.studies ??= [];
    state.studies = [record, ...state.studies];
    selectedStudyId = record.id;
    state.meta ??= {};
    state.meta.sectionAudit ??= {};
    state.meta.sectionAudit.studies = record.audit;
    state.meta.updatedAt = record.audit.at;
    if (getActiveLiraPatientId() !== patientId) throw new Error("El paciente activo cambió antes de guardar");
    persisted = await persistClinicalState({ silent: true });
    if (!persisted) throw new Error(getClinicalPersistenceFailureMessage("No se pudo guardar la plantilla en la ficha"));
    if (getActiveLiraPatientId() !== patientId || state !== clinicalStateAtStart) {
      throw new Error("La plantilla se guardó, pero el paciente activo cambió; vuelva a abrir su ficha para verla");
    }

    rememberStudySessionUpload(record, descriptor, patientId);
    storeClinicalStateLocally();
    renderStudyList();
    renderStudyViewer();
    renderPreview();
    renderPatientOutputs();
    setStudyTemplateBusy(false);
    closeStudyTemplateModal();
    toast("Plantilla agregada; ya puede marcarla o sumarla a una evolución");
  } catch (error) {
    if (!persisted && state === clinicalStateAtStart) {
      state.studies = previousStudies;
      selectedStudyId = previousSelectedStudyId;
      if (hadStudiesAudit) {
        state.meta ??= {};
        state.meta.sectionAudit ??= {};
        state.meta.sectionAudit.studies = previousStudiesAudit;
      } else if (state.meta?.sectionAudit) {
        delete state.meta.sectionAudit.studies;
      }
      if (hadUpdatedAt) state.meta.updatedAt = previousUpdatedAt;
      else if (state.meta) delete state.meta.updatedAt;
      storeClinicalStateLocally();
      renderStudyList();
      renderStudyViewer();
    }
    if (!persisted && record?.id) studySessionUploadDeletes.delete(String(record.id));
    if (!persisted && descriptor?.url && descriptor?.deleteToken) {
      await fetch(descriptor.url, {
        method: "DELETE",
        headers: { "X-Study-Delete-Token": descriptor.deleteToken }
      }).catch(() => {});
    }
    $("#studyTemplateError").hidden = false;
    $("#studyTemplateError").textContent = error.message || "No se pudo agregar la plantilla";
    setStudyTemplateBusy(false);
  }
}

function openStudyUploadModal(files = []) {
  const modal = $("#studyUploadModal");
  if (!modal || studyUploadBusy) return;
  if (!modal.classList.contains("open")) {
    studyUploadReturnFocus = document.activeElement;
    pendingStudyUploads = [];
    studyUploadPersistenceError = "";
    $("#studyUploadError").hidden = true;
    $("#studyUploadError").textContent = "";
    modal.classList.add("open");
    modal.setAttribute("aria-hidden", "false");
    document.body.classList.add("modal-open");
  }
  if (files?.length) addPendingStudyFiles(files);
  else renderStudyUploadQueue();
  window.requestAnimationFrame(() => $("#studyUploadZone")?.focus());
}

function closeStudyUploadModal({ force = false } = {}) {
  const modal = $("#studyUploadModal");
  if (!modal?.classList.contains("open")) return;
  if (studyUploadBusy && !force) {
    toast("Espere a que termine la carga");
    return;
  }
  studyUploadBatchVersion += 1;
  studyUploadBusy = false;
  pendingStudyUploads = [];
  studyUploadPersistenceError = "";
  modal.classList.remove("open", "is-busy");
  modal.setAttribute("aria-hidden", "true");
  modal.setAttribute("aria-busy", "false");
  $("#studyUploadFiles").value = "";
  $("#studyUploadZone")?.classList.remove("dragging");
  $("#studyUploadError").hidden = true;
  $("#studyUploadError").textContent = "";
  renderStudyUploadQueue();
  document.body.classList.remove("modal-open");
  const returnFocus = studyUploadReturnFocus;
  studyUploadReturnFocus = null;
  if (!force && returnFocus?.isConnected) window.requestAnimationFrame(() => returnFocus.focus());
}

function addPendingStudyFiles(fileList) {
  const files = Array.from(fileList || []);
  if (!files.length || studyUploadBusy) return;
  if (!$("#studyUploadModal")?.classList.contains("open")) {
    openStudyUploadModal(files);
    return;
  }

  let totalBytes = pendingStudyUploads
    .filter((entry) => entry.status !== "error")
    .reduce((total, entry) => total + Number(entry.file?.size || 0), 0);
  for (const file of files) {
    const extension = getStudyFileExtension(file.name);
    let error = "";
    if (pendingStudyUploads.length >= STUDY_UPLOAD_MAX_FILES) {
      error = `Solo se pueden preparar ${STUDY_UPLOAD_MAX_FILES} archivos por lote.`;
    } else if (!STUDY_UPLOAD_ACCEPTED_EXTENSIONS.has(extension)) {
      error = "Formato no admitido.";
    } else if (!Number(file.size)) {
      error = "El archivo esta vacio.";
    } else if (file.size > STUDY_UPLOAD_MAX_FILE_BYTES) {
      error = "Supera el limite de 250 MB.";
    } else if (totalBytes + file.size > STUDY_UPLOAD_MAX_BATCH_BYTES) {
      error = "El lote supera el limite total de 500 MB.";
    }
    if (!error) totalBytes += file.size;
    pendingStudyUploads.push({
      id: makeId("upload"),
      file,
      extension,
      status: error ? "error" : "ready",
      error,
      studyId: ""
    });
  }
  renderStudyUploadQueue();
}

function handleStudyUploadQueueAction(event) {
  const button = event.target.closest("[data-study-upload-action]");
  if (!button || studyUploadBusy) return;
  const entry = pendingStudyUploads.find((item) => item.id === button.dataset.id);
  if (!entry) return;
  if (button.dataset.studyUploadAction === "remove") {
    pendingStudyUploads = pendingStudyUploads.filter((item) => item.id !== entry.id);
  }
  if (button.dataset.studyUploadAction === "retry") {
    entry.status = "ready";
    entry.error = "";
  }
  renderStudyUploadQueue();
}

function renderStudyUploadQueue() {
  const output = $("#studyUploadQueue");
  const confirm = $("#confirmStudyUploadBtn");
  const modal = $("#studyUploadModal");
  if (!output || !confirm || !modal) return;
  const ready = pendingStudyUploads.filter((entry) => entry.status === "ready");
  const uploading = pendingStudyUploads.filter((entry) => entry.status === "uploading");
  const errors = pendingStudyUploads.filter((entry) => entry.status === "error");
  output.innerHTML = pendingStudyUploads.length
    ? pendingStudyUploads.map((entry) => {
      const icon = getStudyUploadIcon(entry.extension);
      const stateLabel = {
        ready: "Listo para subir",
        uploading: "Subiendo...",
        uploaded: "Cargado",
        error: entry.error || "No se pudo cargar"
      }[entry.status] || "";
      return `
        <article class="study-upload-item is-${escapeAttr(entry.status)}">
          <span class="study-upload-file-icon"><i data-lucide="${escapeAttr(icon)}"></i></span>
          <div class="study-upload-file-copy">
            <strong>${escapeHtml(entry.file.name)}</strong>
            <small>${escapeHtml([formatFileSize(entry.file.size), getStudyUploadKindLabel(entry.extension)].filter(hasText).join(" · "))}</small>
            <em>${escapeHtml(stateLabel)}</em>
          </div>
          ${entry.status === "error" ? `
            <button class="icon-button" type="button" data-study-upload-action="retry" data-id="${escapeAttr(entry.id)}" title="Reintentar" aria-label="Reintentar ${escapeAttr(entry.file.name)}"><i data-lucide="refresh-cw"></i></button>
          ` : ""}
          ${entry.status !== "uploading" && entry.status !== "uploaded" ? `
            <button class="icon-button" type="button" data-study-upload-action="remove" data-id="${escapeAttr(entry.id)}" title="Quitar" aria-label="Quitar ${escapeAttr(entry.file.name)}"><i data-lucide="x"></i></button>
          ` : ""}
        </article>
      `;
    }).join("")
    : `
      <div class="study-upload-queue-empty">
        <i data-lucide="files"></i>
        <span>Los archivos seleccionados apareceran aqui.</span>
      </div>
    `;
  confirm.disabled = studyUploadBusy || !ready.length;
  $("span", confirm).textContent = studyUploadBusy
    ? `Subiendo ${uploading.length || 1}...`
    : ready.length
      ? `Subir ${ready.length} ${ready.length === 1 ? "archivo" : "archivos"}`
      : "Subir archivos";
  const icon = $("i, svg", confirm);
  if (icon) icon.setAttribute("data-lucide", studyUploadBusy ? "loader-circle" : "upload");
  modal.classList.toggle("is-busy", studyUploadBusy);
  modal.setAttribute("aria-busy", String(studyUploadBusy));
  $("#closeStudyUploadBtn").disabled = studyUploadBusy;
  $("#cancelStudyUploadBtn").disabled = studyUploadBusy;
  $("#studyUploadFiles").disabled = studyUploadBusy;
  const generalError = studyUploadPersistenceError || (errors.length
    ? `${errors.length} ${errors.length === 1 ? "archivo necesita revision" : "archivos necesitan revision"}.`
    : "");
  $("#studyUploadError").hidden = !generalError;
  $("#studyUploadError").textContent = generalError;
  refreshIcons();
}

async function uploadPendingStudyFiles() {
  if (studyUploadBusy) return;
  const patientId = getActiveLiraPatientId();
  if (!patientId) {
    $("#studyUploadError").hidden = false;
    $("#studyUploadError").textContent = "Abra o cree un paciente antes de subir estudios.";
    return;
  }
  const candidates = pendingStudyUploads.filter((entry) => entry.status === "ready");
  if (!candidates.length) return;

  studyUploadBusy = true;
  studyUploadPersistenceError = "";
  const batchVersion = ++studyUploadBatchVersion;
  const patientAtStart = patientId;
  const uploadedRecords = [];
  renderStudyUploadQueue();

  for (const entry of candidates) {
    if (batchVersion !== studyUploadBatchVersion || patientAtStart !== getActiveLiraPatientId()) break;
    entry.status = "uploading";
    entry.error = "";
    entry.studyId = entry.studyId || makeId("est");
    renderStudyUploadQueue();
    try {
      const descriptor = await uploadStudyFile(entry.file, {
        patientId: patientAtStart,
        studyId: entry.studyId
      });
      if (batchVersion !== studyUploadBatchVersion || patientAtStart !== getActiveLiraPatientId()) return;
      const record = buildStudyRecordFromUpload(entry, descriptor);
      uploadedRecords.push(record);
      rememberStudySessionUpload(record, descriptor, patientAtStart);
      entry.status = "uploaded";
    } catch (error) {
      entry.status = "error";
      entry.error = error.message || "No se pudo cargar el archivo.";
    }
    renderStudyUploadQueue();
  }

  if (batchVersion !== studyUploadBatchVersion) return;
  studyUploadBusy = false;
  if (uploadedRecords.length) {
    state.studies ??= [];
    state.studies.unshift(...uploadedRecords);
    selectedStudyId = uploadedRecords[0].id;
    const latestAudit = uploadedRecords.at(-1)?.audit || buildAuditStamp("cargado");
    state.meta.sectionAudit ??= {};
    state.meta.sectionAudit.studies = latestAudit;
    state.meta.updatedAt = latestAudit.at;
    storeClinicalStateLocally();
    renderStudyList();
    renderStudyViewer();
    renderPreview();
    renderPatientOutputs();
    const persisted = await persistClinicalState({ silent: true });
    if (!persisted) {
      queueLocalSave();
      studyUploadPersistenceError = "Los archivos quedaron cargados, pero la ficha no pudo sincronizarse. Se reintentara al guardar.";
    }
  }

  const failed = pendingStudyUploads.filter((entry) => entry.status === "error");
  renderStudyUploadQueue();
  if (!failed.length && uploadedRecords.length && !studyUploadPersistenceError) {
    const count = uploadedRecords.length;
    closeStudyUploadModal({ force: true });
    toast(`${count} ${count === 1 ? "estudio cargado" : "estudios cargados"}`);
    $("#studyList")?.focus?.();
    return;
  }
  if (uploadedRecords.length) {
    toast(`${uploadedRecords.length} cargado${uploadedRecords.length === 1 ? "" : "s"}; revise los archivos pendientes`);
  }
}

async function uploadStudyFile(file, { patientId, studyId }) {
  const query = new URLSearchParams({
    patientId: String(patientId),
    studyId: String(studyId),
    name: file.name
  });
  const response = await fetch(`/api/media/studies?${query}`, {
    method: "POST",
    headers: { "Content-Type": file.type || "application/octet-stream" },
    body: file
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || !payload.url) {
    throw new Error(payload.error || `No se pudo subir ${file.name}.`);
  }
  return payload;
}

function rememberStudySessionUpload(record, descriptor, patientId) {
  if (
    !record?.id ||
    descriptor?.category !== "image" ||
    !descriptor?.url ||
    !descriptor?.deleteToken
  ) return;
  studySessionUploadDeletes.set(String(record.id), {
    patientId: String(patientId),
    studyId: String(record.id),
    fileUrl: String(descriptor.url),
    deleteToken: String(descriptor.deleteToken),
    deleteExpiresAt: String(descriptor.deleteExpiresAt || "")
  });
}

function getStudySessionDeleteAuthorization(study) {
  const studyId = String(study?.id || "");
  const authorization = studySessionUploadDeletes.get(studyId);
  if (!authorization || authorization.patientId !== getActiveLiraPatientId()) return null;
  const expiresAt = Date.parse(authorization.deleteExpiresAt || "");
  if (Number.isFinite(expiresAt) && expiresAt <= Date.now()) {
    studySessionUploadDeletes.delete(studyId);
    return null;
  }
  const fileUrl = String(getStudyPrimaryFile(study)?.url || "");
  if (!fileUrl || authorization.fileUrl !== fileUrl) return null;
  return authorization;
}

function buildStudyRecordFromUpload(entry, descriptor) {
  const audit = buildAuditStamp("cargado");
  const fileDescriptor = {
    id: descriptor.id,
    fileName: descriptor.fileName || entry.file.name,
    contentType: descriptor.contentType || entry.file.type || "application/octet-stream",
    size: Number(descriptor.size || entry.file.size || 0),
    sha256: descriptor.sha256 || "",
    category: descriptor.category || getStudyUploadCategory(entry.extension),
    previewable: Boolean(descriptor.previewable),
    url: descriptor.url,
    uploadedAt: descriptor.uploadedAt || audit.at
  };
  const record = {
    id: entry.studyId,
    date: today(),
    datePrecision: "day",
    type: getStudyUploadTypeLabel(fileDescriptor.category, entry.extension),
    title: entry.file.name.replace(/\.[^.]+$/, "") || entry.file.name,
    source: "Repositorio local",
    summary: "",
    tags: [],
    attachments: [fileDescriptor],
    fileName: fileDescriptor.fileName,
    fileType: fileDescriptor.contentType,
    fileSize: fileDescriptor.size,
    fileCategory: fileDescriptor.category,
    fileSha256: fileDescriptor.sha256,
    fileUrl: fileDescriptor.url,
    audit,
    createdAt: audit.at,
    updatedAt: audit.at
  };
  if (fileDescriptor.category === "image" && fileDescriptor.previewable) {
    record.presentationKind = "loose-image";
    record.previewImageUrl = fileDescriptor.url;
    record.displayImageUrls = [fileDescriptor.url];
    record.imageUrls = [fileDescriptor.url];
    record.imageCount = 1;
  } else if (fileDescriptor.category === "pdf") {
    record.reportUrl = fileDescriptor.url;
  } else {
    record.studyUrl = fileDescriptor.url;
  }
  return record;
}

function getStudyFileExtension(fileName) {
  const match = String(fileName || "").toLowerCase().match(/\.([a-z0-9]+)$/);
  return match?.[1] || "";
}

function getStudyUploadCategory(extension) {
  if (["png", "jpg", "jpeg", "gif", "webp", "avif", "bmp", "ico", "tif", "tiff", "heic", "heif", "svg", "dcm"].includes(extension)) return "image";
  if (extension === "pdf") return "pdf";
  if (["doc", "docx", "rtf", "odt"].includes(extension)) return "word";
  if (["ppt", "pps", "pptx", "ppsx", "odp"].includes(extension)) return "presentation";
  if (["mp4", "m4v", "mov", "3gp", "webm", "mkv", "avi", "mpeg", "mpg", "ogv", "wmv", "flv"].includes(extension)) return "video";
  return "file";
}

function getStudyUploadKindLabel(extension) {
  const category = getStudyUploadCategory(extension);
  return {
    image: extension === "dcm" ? "Imagen DICOM" : "Imagen",
    pdf: "PDF",
    word: "Word",
    presentation: "PowerPoint",
    video: "Video"
  }[category] || "Archivo";
}

function getStudyUploadTypeLabel(category, extension) {
  if (category === "image") return extension === "dcm" ? "Imagen DICOM" : "Imagen";
  return {
    pdf: "Documento PDF",
    word: "Documento Word",
    presentation: "Presentacion",
    video: "Video"
  }[category] || "Otro";
}

function getStudyUploadIcon(extension) {
  return {
    image: extension === "dcm" ? "scan" : "image",
    pdf: "file-text",
    word: "file-type-2",
    presentation: "presentation",
    video: "video"
  }[getStudyUploadCategory(extension)] || "file";
}

function handleStudyListDragOver(event) {
  if (!Array.from(event.dataTransfer?.types || []).includes("Files")) return;
  event.preventDefault();
  event.dataTransfer.dropEffect = "copy";
  $("#studyList")?.classList.add("is-drop-target");
}

function handleStudyListDragLeave(event) {
  const list = $("#studyList");
  if (event.relatedTarget && list?.contains(event.relatedTarget)) return;
  list?.classList.remove("is-drop-target");
}

function handleStudyListDrop(event) {
  if (!event.dataTransfer?.files?.length) return;
  event.preventDefault();
  $("#studyList")?.classList.remove("is-drop-target");
  openStudyUploadModal(event.dataTransfer.files);
}

async function addStudy(event) {
  event.preventDefault();
  const title = $("#studyTitle").value.trim();
  const summary = $("#studySummary").value.trim();
  const selectedType = String($("#studyType").value || "");
  if (/(?:imagen|foto|dicom)/i.test(selectedType) && !pendingStudyFile) {
    toast("Seleccione un archivo para registrar un estudio de imagen");
    $("#studyFile")?.focus();
    return;
  }
  if (!title && !summary && !pendingStudyFile) {
    toast("Estudio vacio");
    return;
  }

  let filePayload = {};
  if (pendingStudyFile) {
    const fileType = pendingStudyFile.type || "application/octet-stream";
    const dataUrl = await readFileAsDataUrl(pendingStudyFile);
    filePayload = { fileName: pendingStudyFile.name, fileType };
    if (fileType.startsWith("image/")) {
      try {
        const response = await fetch("/api/media/images", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ dataUrl, fileName: pendingStudyFile.name, kind: "original" })
        });
        const media = await response.json().catch(() => ({}));
        if (!response.ok || !media.url) throw new Error(media.error || "No se pudo guardar la imagen");
        filePayload.previewImageUrl = media.url;
        filePayload.displayImageUrls = [media.url];
        filePayload.imageUrls = [media.url];
        filePayload.imageCount = 1;
      } catch (error) {
        filePayload.dataUrl = dataUrl;
      }
    } else {
      filePayload.dataUrl = dataUrl;
    }
  }

  const study = {
    id: makeId("est"),
    date: $("#studyDate").value || extractDate(summary || title) || today(),
    datePrecision: "day",
    type: $("#studyType").value,
    title: title || pendingStudyFile?.name || "Estudio sin titulo",
    source: $("#studySource").value.trim() || "Repositorio local",
    summary,
    tags: [],
    ...filePayload
  };
  const audit = buildAuditStamp("cargado");
  study.audit = audit;
  study.createdAt = audit.at;
  study.updatedAt = audit.at;
  if (study.fileType?.startsWith("image/") && getStudyImages(study).length === 1) {
    study.presentationKind = "loose-image";
  }

  state.studies.unshift(study);
  selectedStudyId = study.id;
  resetStudyForm();
  renderStudyList();
  renderStudyViewer();
  renderPreview();
  queueLocalSave();
  toast("Estudio cargado");
}

function resetStudyForm() {
  $("#studyForm").reset();
  $("#studyDate").value = today();
  $("#studySource").value = "Repositorio local";
  $("#uploadLabel").textContent = "Adjunto o foto";
  $("#studyFile").value = "";
  pendingStudyFile = null;
}

function setPendingStudyFile(file) {
  if (!file) return;
  pendingStudyFile = file;
  $("#uploadLabel").textContent = file.name;
  if (!$("#studyTitle").value.trim()) $("#studyTitle").value = file.name.replace(/\.[^.]+$/, "");
  if (file.type.startsWith("image/")) $("#studyType").value = "Foto";
}

function renderStudyList() {
  const list = $("#studyList");
  const term = $("#studySearch")?.value.trim().toLowerCase() || "";
  const type = $("#studyTypeFilter")?.value || "";
  const repositoryStudies = [...getRepositoryStudies()];
  const looseLocalStudies = getLooseLocalImageStudies();
  const cardStudies = repositoryStudies.filter((study) => !isLooseLocalImageStudy(study));
  const fileCount = $("#studyFileCount");
  if (fileCount) {
    fileCount.textContent = repositoryStudies.length
      ? `${repositoryStudies.length} ${repositoryStudies.length === 1 ? "estudio" : "estudios"}`
      : "Sin estudios cargados";
  }

  const studies = cardStudies
    .filter((study) => !type || study.type === type || study.modality === type)
    .filter((study) => {
      if (!term) return true;
      return [study.title, study.fileName, study.summary, getStudySourceLabel(study), study.type, study.modality, study.reportUrl, study.studyUrl]
        .join(" ")
        .toLowerCase()
        .includes(term);
    })
    .sort((a, b) => (b.date || "").localeCompare(a.date || ""));

  if (!studies.length) {
    const hasFilteredRecords = cardStudies.length > 0;
    list.innerHTML = hasFilteredRecords
      ? `<div class="viewer-empty">No hay estudios que coincidan con el filtro.</div>`
      : looseLocalStudies.length
        ? ""
        : `
        <button class="study-empty-upload" type="button" data-action="open-study-upload" aria-haspopup="dialog" aria-controls="studyUploadModal">
          <i data-lucide="cloud-upload"></i>
          <strong>Sin estudios cargados</strong>
          <span>Seleccione archivos, arrastrelos o pegue una imagen con Ctrl+V</span>
          <small>Imagenes, DICOM, PDF, Word, PowerPoint y video</small>
        </button>
      `;
    const selectedStillExists = repositoryStudies.some(
      (study) => String(study.id) === String(selectedStudyId)
    );
    if (!selectedStillExists) selectedStudyId = looseLocalStudies[0]?.id || null;
    renderStudyImages();
    renderStudyViewer();
    refreshIcons();
    return;
  }

  const selectedStillExists = repositoryStudies.some(
    (study) => String(study.id) === String(selectedStudyId)
  );
  if (!selectedStillExists) {
    selectedStudyId = studies[0]?.id || looseLocalStudies[0]?.id || null;
  }

  list.innerHTML = studies.map((study) => {
    const authorization = getStudySessionDeleteAuthorization(study);
    const deleting = studyDeleteBusyId === String(study.id);
    const metadataOnlyImage = isStudyImageRecordWithoutFile(study);
    return `
      <article class="study-card pangea-study-card ${study.id === selectedStudyId ? "active" : ""}" data-action="view-study" data-id="${escapeAttr(study.id)}">
        <div class="study-row-main">
          <span class="study-row-file-icon" aria-hidden="true"><i data-lucide="${escapeAttr(getStudyRecordIcon(study))}"></i></span>
          <span class="study-row-date">${escapeHtml(formatDateOptional(study.date, study.datePrecision) || "Sin fecha")}</span>
          <span class="study-title">${escapeHtml(study.title)}</span>
          <span class="study-type">${escapeHtml(getStudyBadge(study))}</span>
          <span class="study-row-source">${escapeHtml([
            getStudySourceLabel(study) || "PangeaSystem",
            study.fileSize ? formatFileSize(study.fileSize) : ""
          ].filter(hasText).join(" · "))}</span>
        </div>
        <div class="study-actions">
          <button class="tiny-button study-detail-button" type="button" data-action="view-study" data-id="${escapeAttr(study.id)}" title="${metadataOnlyImage ? "Ver registro sin archivo adjunto" : "Ver imágenes y detalle"}">
            <i data-lucide="${metadataOnlyImage ? "file-search" : "eye"}"></i>
            <span>${metadataOnlyImage ? "Ver registro" : "Ver imágenes / detalle"}</span>
          </button>
          ${renderStudyExternalLinks(study)}
          ${authorization ? `
            <button
              class="tiny-button study-session-delete"
              type="button"
              data-action="delete-study"
              data-id="${escapeAttr(study.id)}"
              title="Eliminar esta imagen cargada en la sesión actual"
              aria-label="Eliminar ${escapeAttr(study.title || "imagen")} de esta sesión"
              ${deleting ? "disabled aria-busy=\"true\"" : ""}
            >
              <i data-lucide="${deleting ? "loader-circle" : "trash-2"}"></i>
              <span>${deleting ? "Eliminando…" : "Eliminar"}</span>
            </button>
          ` : ""}
        </div>
      </article>
    `;
  }).join("");
  renderStudyImages();
  refreshIcons();
}

function handleStudyAction(event) {
  if (event.target.closest("a")) return;
  const button = event.target.closest("[data-action]");
  if (!button) return;
  if (button.dataset.action === "open-study-upload") {
    openStudyUploadModal();
    return;
  }
  const study = getRepositoryStudies().find((item) => item.id === button.dataset.id);
  if (!study) return;

  if (button.dataset.action === "view-study") {
    selectedStudyId = study.id;
    renderStudyList();
    renderStudyViewer();
  }

  if (button.dataset.action === "insert-study") {
    selectedStudyId = study.id;
    openEvolutionModal("evolucion");
    insertStudyIntoEvolution(study);
    renderStudyList();
    renderStudyViewer();
  }

  if (button.dataset.action === "delete-study") {
    const authorization = getStudySessionDeleteAuthorization(study);
    if (!authorization) {
      renderStudyList();
      toast("Esta imagen ya no se puede eliminar desde la sesión actual");
      return;
    }
    void deleteStudyUploadedInCurrentSession(study, authorization);
    return;
  }
}

async function deleteStudyUploadedInCurrentSession(study, authorization) {
  const currentAuthorization = getStudySessionDeleteAuthorization(study);
  if (
    !currentAuthorization ||
    currentAuthorization.deleteToken !== authorization.deleteToken ||
    studyDeleteBusyId
  ) return;
  if (!(state.studies || []).some((item) => String(item.id) === String(study.id))) {
    studySessionUploadDeletes.delete(String(study.id));
    renderStudyList();
    return;
  }
  if (!window.confirm("¿Eliminar esta imagen cargada en la sesión actual? Esta acción no se puede deshacer.")) return;

  const previousStudies = state.studies;
  const previousSelectedStudyId = selectedStudyId;
  const hadUpdatedAt = Object.prototype.hasOwnProperty.call(state.meta || {}, "updatedAt");
  const previousUpdatedAt = state.meta?.updatedAt;
  const hadStudiesAudit = Object.prototype.hasOwnProperty.call(state.meta?.sectionAudit || {}, "studies");
  const previousStudiesAudit = state.meta?.sectionAudit?.studies;

  studyDeleteBusyId = String(study.id);
  state.studies = (state.studies || []).filter((item) => String(item.id) !== String(study.id));
  selectedStudyId = getLooseLocalImageStudies()[0]?.id || getRepositoryStudies()[0]?.id || null;
  const audit = buildAuditStamp("eliminado");
  state.meta ??= {};
  state.meta.sectionAudit ??= {};
  state.meta.sectionAudit.studies = audit;
  state.meta.updatedAt = audit.at;
  storeClinicalStateLocally();
  renderStudyList();
  renderStudyViewer();
  renderPreview();
  renderPatientOutputs();

  const persisted = await persistClinicalState({ silent: true });
  if (!persisted) {
    state.studies = previousStudies;
    selectedStudyId = previousSelectedStudyId;
    if (hadStudiesAudit) state.meta.sectionAudit.studies = previousStudiesAudit;
    else delete state.meta.sectionAudit.studies;
    if (hadUpdatedAt) state.meta.updatedAt = previousUpdatedAt;
    else delete state.meta.updatedAt;
    storeClinicalStateLocally();
    studyDeleteBusyId = "";
    renderStudyList();
    renderStudyViewer();
    renderPreview();
    renderPatientOutputs();
    toast(getClinicalPersistenceFailureMessage("No se pudo confirmar la eliminación de la imagen"));
    return;
  }

  if (activeStudyImageContext?.study?.id === study.id) closeStudyImageModal();
  try {
    const response = await fetch(authorization.fileUrl, {
      method: "DELETE",
      headers: { "X-Study-Delete-Token": authorization.deleteToken }
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || "No se pudo limpiar el archivo local");
    toast(payload.retainedBecauseShared
      ? "Imagen eliminada de la ficha; el archivo compartido se conservó"
      : "Imagen eliminada");
  } catch {
    toast("La imagen se eliminó de la ficha, pero no se pudo limpiar el archivo local");
  } finally {
    studySessionUploadDeletes.delete(String(study.id));
    studyDeleteBusyId = "";
    renderStudyList();
    renderStudyViewer();
  }
}

function insertStudyIntoEvolution(study) {
  const line = `Estudio ${formatDateOptional(study.date, study.datePrecision) || "Sin fecha"} - ${study.type}: ${study.title}. ${study.summary || ""}`.trim();
  const current = $("#evolutionText").value.trim();
  $("#evolutionText").value = current ? `${current}\n${line}` : line;
  toast("Estudio agregado a evolucion");
}

function getStudyMetaLine(study) {
  return [formatDateOptional(study.date, study.datePrecision), getStudySourceLabel(study) || "PangeaSystem"].filter(hasText).join(" - ");
}

function getStudySourceLabel(study) {
  const source = String(study?.source ?? "").trim();
  return source.toLowerCase() === "pangeasystem demo" ? "" : source;
}

function getRepositoryStudies() {
  const combined = [...(state.externalStudies || []), ...(state.studies || [])];
  return [...new Map(combined.map((study) => [study.id, study])).values()];
}

function isLooseLocalImageStudy(study) {
  if (!study?.id) return false;
  const studyId = String(study.id);
  if ((state.externalStudies || []).some((item) => String(item?.id || "") === studyId)) return false;
  const localStudy = (state.studies || []).find((item) => String(item?.id || "") === studyId);
  if (!localStudy) return false;
  const images = getStudyImages(localStudy);
  if (images.length !== 1) return false;
  if (localStudy.presentationKind === "loose-image") return true;
  const primaryFile = getStudyPrimaryFile(localStudy);
  return Boolean(
    (primaryFile?.category === "image" && primaryFile.previewable !== false) ||
    String(localStudy.fileType || "").toLowerCase().startsWith("image/")
  );
}

function getLooseLocalImageStudies() {
  return (state.studies || []).filter((study) => isLooseLocalImageStudy(study));
}

function getLooseImageUploadedAt(study) {
  const primaryFile = getStudyPrimaryFile(study);
  return primaryFile?.uploadedAt || study?.createdAt || study?.audit?.at || "";
}

function getStudyBadge(study) {
  const label = study.modality || study.type || "Otro";
  return isStudyImageRecordWithoutFile(study) ? `${label} · sin archivo` : label;
}

function isStudyImageRecordWithoutFile(study) {
  const type = String(study?.modality || study?.type || "");
  if (!/(?:imagen|foto|dicom)/i.test(type)) return false;
  return !getStudyImages(study).length &&
    !getStudyPrimaryFile(study) &&
    !hasText(study?.reportUrl) &&
    !hasText(study?.studyUrl);
}

function getStudyAttachments(study) {
  if (Array.isArray(study?.attachments) && study.attachments.length) {
    return study.attachments.filter((item) => item && typeof item === "object" && hasText(item.url));
  }
  const legacyUrl = study?.fileUrl || study?.reportUrl || study?.studyUrl || "";
  if (!legacyUrl) return [];
  return [{
    id: `file-${hashTimelineSource(legacyUrl)}`,
    fileName: study.fileName || study.title || "Archivo de estudio",
    contentType: study.fileType || "",
    size: Number(study.fileSize || 0),
    category: study.fileCategory || (
      study.fileType?.startsWith("image/") ? "image"
        : study.fileType === "application/pdf" ? "pdf"
          : study.fileType?.startsWith("video/") ? "video" : "file"
    ),
    previewable: Boolean(study.previewImageUrl || study.fileType === "application/pdf" || study.fileType?.startsWith("video/")),
    url: legacyUrl
  }];
}

function getStudyPrimaryFile(study) {
  return getStudyAttachments(study)[0] || null;
}

function getStudyRecordIcon(study) {
  const file = getStudyPrimaryFile(study);
  const category = file?.category || (
    getStudyImages(study).length ? "image" : ""
  );
  return {
    image: "image",
    pdf: "file-text",
    word: "file-type-2",
    presentation: "presentation",
    video: "video"
  }[category] || "file-search";
}

function renderStudyExternalLinks(study, { compact = false } = {}) {
  const primaryFile = getStudyPrimaryFile(study);
  const existingUrls = new Set([study.reportUrl, study.studyUrl].filter(hasText));
  return [
    study.reportUrl ? `
      <a class="tiny-button" href="${escapeAttr(study.reportUrl)}" target="_blank" rel="noopener" title="Ver informe">
        <i data-lucide="file-text"></i>
        ${compact ? "" : "<span>Informe</span>"}
      </a>
    ` : "",
    study.studyUrl ? `
      <a class="tiny-button" href="${escapeAttr(study.studyUrl)}" target="_blank" rel="noopener" title="Ver estudio">
        <i data-lucide="external-link"></i>
        ${compact ? "" : "<span>Abrir estudio</span>"}
      </a>
    ` : "",
    primaryFile?.url && !existingUrls.has(primaryFile.url) ? `
      <a class="tiny-button" href="${escapeAttr(primaryFile.url)}" target="_blank" rel="noopener" title="Abrir archivo cargado">
        <i data-lucide="${escapeAttr(getStudyRecordIcon(study))}"></i>
        ${compact ? "" : `<span>${primaryFile.category === "image" ? "Abrir imagen" : "Abrir archivo"}</span>`}
      </a>
    ` : "",
    study.templateSource?.sourceUrl ? `
      <a class="tiny-button" href="${escapeAttr(study.templateSource.sourceUrl)}" target="_blank" rel="noopener" title="${escapeAttr([study.templateSource.author, study.templateSource.license].filter(hasText).join(" · ") || "Fuente de la plantilla")}">
        <i data-lucide="badge-check"></i>
        ${compact ? "" : "<span>Fuente y licencia</span>"}
      </a>
    ` : ""
  ].join("");
}

function renderLooseStudyImageCards(studies) {
  const reorderBusy = Boolean(studyImageReorderBusyId);
  return studies.map((study, index) => {
    const image = getStudyImages(study)[0];
    if (!image) return "";
    const authorization = getStudySessionDeleteAuthorization(study);
    const deleting = studyDeleteBusyId === String(study.id);
    const uploadedAt = getLooseImageUploadedAt(study);
    const uploadedLabel = uploadedAt ? formatDateTime(uploadedAt) : "Fecha no disponible";
    const first = index === 0;
    const last = index === studies.length - 1;
    return `
      <article class="study-image-tile study-image-tile--loose ${study.id === selectedStudyId ? "is-selected" : ""}" data-study-id="${escapeAttr(study.id)}" data-image-id="${escapeAttr(image.id)}">
        <button class="loose-study-image-preview" type="button" data-study-image-action="view" title="Ampliar imagen" aria-label="Ampliar ${escapeAttr(study.title || image.label)}">
          <img src="${escapeAttr(image.url)}" loading="lazy" alt="${escapeAttr(image.label)}" onerror="this.closest('.study-image-tile').classList.add('broken')">
          ${image.bitmapEdited ? `<span class="study-image-derived"><i data-lucide="pencil-line"></i>Imagen editada</span>` : ""}
        </button>
        <div class="loose-study-image-meta">
          <strong>${escapeHtml(study.title || "Imagen")}</strong>
          <span><i data-lucide="calendar-clock"></i>Cargada ${escapeHtml(uploadedLabel)}</span>
        </div>
        <div class="study-image-actions loose-study-image-actions" role="toolbar" aria-label="Acciones de ${escapeAttr(study.title || "imagen")}">
          <button type="button" data-study-image-action="view" title="Ampliar imagen"><i data-lucide="maximize-2"></i><span>Ampliar</span></button>
          <button type="button" data-study-image-action="print" title="Imprimir imagen"><i data-lucide="printer"></i><span>Imprimir</span></button>
          <button type="button" data-study-image-action="annotate" title="Escribir o dibujar sobre la imagen"><i data-lucide="pencil-line"></i><span>Escribir / dibujar</span></button>
          <button type="button" data-study-image-action="attach" title="Agregar a una evolucion"><i data-lucide="notebook-pen"></i><span>A evolucion</span></button>
          <button type="button" data-study-image-action="move-up" title="${first ? "Esta imagen ya es la primera" : "Subir una posicion"}" aria-label="Subir ${escapeAttr(study.title || "imagen")} una posicion" ${first || reorderBusy ? "disabled" : ""}><i data-lucide="arrow-up"></i><span>Subir</span></button>
          <button type="button" data-study-image-action="move-down" title="${last ? "Esta imagen ya es la ultima" : "Bajar una posicion"}" aria-label="Bajar ${escapeAttr(study.title || "imagen")} una posicion" ${last || reorderBusy ? "disabled" : ""}><i data-lucide="arrow-down"></i><span>Bajar</span></button>
          ${authorization ? `
            <button class="is-danger" type="button" data-study-image-action="delete" title="Eliminar esta imagen cargada en la sesion actual" aria-label="Eliminar ${escapeAttr(study.title || "imagen")}" ${deleting || reorderBusy ? "disabled" : ""}>
              <i data-lucide="${deleting ? "loader-circle" : "trash-2"}"></i><span>${deleting ? "Eliminando..." : "Eliminar"}</span>
            </button>
          ` : ""}
        </div>
      </article>
    `;
  }).join("");
}

function renderLocalStudyImages() {
  const panel = $("#studyLocalImagesPanel");
  const imageHead = $("#studyLocalImagesHead");
  const imageList = $("#studyLocalImageList");
  if (!panel || !imageHead || !imageList) return;
  const looseStudies = getLooseLocalImageStudies();
  panel.hidden = !looseStudies.length;
  if (!looseStudies.length) {
    imageHead.innerHTML = "";
    imageList.innerHTML = "";
    return;
  }
  imageHead.innerHTML = `
    <span>Imagenes</span>
    <strong>Imagenes cargadas y plantillas</strong>
    <em>${escapeHtml(`${looseStudies.length} ${looseStudies.length === 1 ? "imagen" : "imagenes"}`)}</em>
  `;
  imageList.innerHTML = renderLooseStudyImageCards(looseStudies);
}

function renderStudyImages() {
  renderLocalStudyImages();
  const imageList = $("#studyImageList");
  const imageHead = $("#studyImagesHead");
  const panel = $("#studyImagesPanel");
  if (!imageList || !imageHead) return;

  const study = getRepositoryStudies().find((item) => item.id === selectedStudyId);
  if (!study || isLooseLocalImageStudy(study)) {
    if (panel) panel.hidden = true;
    imageList.classList.remove("study-image-grid--loose");
    imageHead.innerHTML = "";
    imageList.innerHTML = "";
    return;
  }
  if (panel) panel.hidden = false;

  imageList.classList.remove("study-image-grid--loose");
  const images = getStudyImages(study);
  const primaryFile = getStudyPrimaryFile(study);
  const total = Number(study.imageCount || images.length || 0);
  const imageLabel = images.length && total > images.length
    ? `${images.length} visibles / ${total}`
    : (images.length ? `${images.length} imagenes` : "Sin imagenes importadas");
  imageHead.innerHTML = `
    <span>Imagenes</span>
    <strong>${escapeHtml(study.title || "Estudio")}</strong>
    <em>${escapeHtml(imageLabel)}</em>
  `;

  if (!images.length) {
    if (primaryFile?.url) {
      imageHead.innerHTML = `
        <span>Archivo</span>
        <strong>${escapeHtml(primaryFile.fileName || study.title || "Estudio")}</strong>
        <em>${escapeHtml(primaryFile.size ? formatFileSize(primaryFile.size) : getStudyUploadTypeLabel(primaryFile.category, getStudyFileExtension(primaryFile.fileName)))}</em>
      `;
      if (primaryFile.category === "video" && primaryFile.previewable) {
        imageList.innerHTML = `
          <div class="study-file-preview study-file-preview--video">
            <video controls preload="metadata">
              <source src="${escapeAttr(primaryFile.url)}" type="${escapeAttr(primaryFile.contentType || "video/mp4")}">
            </video>
            <a class="tool-button" href="${escapeAttr(primaryFile.url)}" target="_blank" rel="noopener"><i data-lucide="external-link"></i><span>Abrir video</span></a>
          </div>
        `;
      } else if (primaryFile.category === "pdf" && primaryFile.previewable) {
        imageList.innerHTML = `
          <div class="study-file-preview study-file-preview--pdf">
            <embed src="${escapeAttr(primaryFile.url)}" type="application/pdf">
            <a class="tool-button" href="${escapeAttr(primaryFile.url)}" target="_blank" rel="noopener"><i data-lucide="external-link"></i><span>Abrir PDF</span></a>
          </div>
        `;
      } else {
        imageList.innerHTML = `
          <a class="study-file-card" href="${escapeAttr(primaryFile.url)}" target="_blank" rel="noopener">
            <span class="study-file-card-icon"><i data-lucide="${escapeAttr(getStudyRecordIcon(study))}"></i></span>
            <span><strong>${escapeHtml(primaryFile.fileName || study.title || "Archivo")}</strong><small>${escapeHtml([getStudyUploadTypeLabel(primaryFile.category, getStudyFileExtension(primaryFile.fileName)), primaryFile.size ? formatFileSize(primaryFile.size) : ""].filter(hasText).join(" · "))}</small></span>
            <i data-lucide="download"></i>
          </a>
        `;
      }
      refreshIcons();
      return;
    }
    imageList.innerHTML = isStudyImageRecordWithoutFile(study)
      ? `<div class="study-images-empty">Registro histórico de imagen sin archivo adjunto. Se conserva la información clínica, pero no se presenta como una imagen disponible.</div>`
      : `<div class="study-images-empty">Este estudio no tiene imágenes importadas.</div>`;
    return;
  }

  imageList.innerHTML = images.map((image, index) => `
    <article class="study-image-tile ${activeStudyImageMenuId === image.id ? "menu-open" : ""}" tabindex="0" role="button" aria-label="${escapeAttr(image.label)}. Mostrar acciones" aria-expanded="${activeStudyImageMenuId === image.id ? "true" : "false"}" data-study-id="${escapeAttr(study.id)}" data-image-id="${escapeAttr(image.id)}">
      <img src="${escapeAttr(image.url)}" loading="lazy" alt="${escapeAttr(image.label)}" onerror="this.closest('.study-image-tile').classList.add('broken')">
      <span class="study-image-number">${escapeHtml(String(index + 1))}</span>
      ${image.annotated ? `<span class="study-image-derived"><i data-lucide="pencil-line"></i>Copia anotada</span>` : ""}
      <div class="study-image-actions" role="toolbar" aria-label="Acciones de imagen">
        <button type="button" data-study-image-action="view" title="Ampliar imagen"><i data-lucide="maximize-2"></i><span>Ampliar</span></button>
        <button type="button" data-study-image-action="print" title="Imprimir imagen"><i data-lucide="printer"></i><span>Imprimir</span></button>
        <button type="button" data-study-image-action="annotate" title="Marcar o escribir sobre una copia"><i data-lucide="pencil-line"></i><span>Anotar</span></button>
        <button type="button" data-study-image-action="attach" title="Agregar a una evolucion"><i data-lucide="notebook-pen"></i><span>A evolucion</span></button>
      </div>
    </article>
  `).join("");
  refreshIcons();
}

function getStudyImages(study) {
  const preferredUrls = Array.isArray(study.displayImageUrls) ? study.displayImageUrls : [];
  const urls = preferredUrls.length
    ? preferredUrls
    : [
      study.dataUrl && study.fileType?.startsWith("image/") ? study.dataUrl : "",
      study.previewImageUrl,
      ...(Array.isArray(study.imageUrls) ? study.imageUrls : []),
      ...(Array.isArray(study.documentImageUrls) ? study.documentImageUrls : [])
    ].filter(hasText);
  const assets = normalizeStudyImageAssets(study.imageAssets);
  study.imageAssets = assets;
  const uniqueUrls = [...new Set([
    ...urls,
    ...assets.map((asset) => asset.originalUrl).filter(hasText)
  ])];
  return uniqueUrls.map((sourceUrl, index) => {
    const id = `image-${hashTimelineSource(sourceUrl)}`;
    const asset = assets.find((item) => item.id === id || item.originalUrl === sourceUrl);
    const activeVersion = asset?.versions?.find((version) => version.id === asset.activeVersionId) || asset?.versions?.at(-1);
    return {
      id,
      sourceUrl,
      url: activeVersion?.url || asset?.currentUrl || asset?.localUrl || sourceUrl,
      localUrl: asset?.localUrl || "",
      versionId: activeVersion?.id || "original",
      annotated: Boolean(activeVersion),
      bitmapEdited: activeVersion?.kind === "bitmap-edit",
      asset,
      label: `${study.title || "Estudio"} - Imagen ${index + 1}`
    };
  });
}

function normalizeStudyImageAssets(assets) {
  return Array.isArray(assets) ? assets.map((asset) => ({
    id: asset.id || `image-${hashTimelineSource(asset.originalUrl || asset.localUrl || asset.currentUrl || makeId("image"))}`,
    originalUrl: asset.originalUrl || asset.sourceUrl || "",
    localUrl: asset.localUrl || "",
    currentUrl: asset.currentUrl || "",
    activeVersionId: asset.activeVersionId || "",
    versions: Array.isArray(asset.versions) ? asset.versions.map((version) => ({ ...version })) : [],
    importedAt: asset.importedAt || "",
    audit: asset.audit || null
  })) : [];
}

function toggleStudyImageMenu(imageId) {
  activeStudyImageMenuId = activeStudyImageMenuId === imageId ? "" : imageId;
  renderStudyImages();
  if (activeStudyImageMenuId) {
    $(`[data-image-id="${CSS.escape(activeStudyImageMenuId)}"]`)?.focus();
  }
}

async function handleStudyImageAction(event) {
  const actionButton = event.target.closest("[data-study-image-action]");
  const tile = event.target.closest(".study-image-tile");
  if (!tile) return;
  if (!actionButton) {
    toggleStudyImageMenu(tile.dataset.imageId);
    return;
  }
  const context = findStudyImageContext(tile.dataset.studyId, tile.dataset.imageId);
  if (!context) {
    toast("Imagen no disponible");
    return;
  }
  const action = actionButton.dataset.studyImageAction;
  if (context.bitmapEdit && selectedStudyId !== context.study.id) {
    selectedStudyId = context.study.id;
    $$("#studyList .study-card").forEach((card) => card.classList.toggle("active", card.dataset.id === selectedStudyId));
    $$("#studyLocalImageList .study-image-tile--loose").forEach((card) => card.classList.toggle("is-selected", card.dataset.studyId === selectedStudyId));
    renderStudyImages();
  }
  if (action === "view") openStudyImageModal(context);
  if (action === "print") printStudyImageContext(context);
  if (action === "annotate") {
    openStudyImageModal(context);
    await beginStudyImageAnnotation();
  }
  if (action === "attach") await attachStudyImageContextToEvolution(context);
  if (action === "move-up") await moveLooseStudyImage(context.study, -1);
  if (action === "move-down") await moveLooseStudyImage(context.study, 1);
  if (action === "delete") {
    const authorization = getStudySessionDeleteAuthorization(context.study);
    if (!authorization) {
      renderStudyList();
      toast("Esta imagen ya no se puede eliminar desde la sesion actual");
      return;
    }
    await deleteStudyUploadedInCurrentSession(context.study, authorization);
  }
}

async function moveLooseStudyImage(study, direction) {
  if (studyImageReorderBusyId || studyDeleteBusyId || !isLooseLocalImageStudy(study)) return;
  const looseStudies = getLooseLocalImageStudies();
  const currentIndex = looseStudies.findIndex((item) => String(item.id) === String(study.id));
  const targetIndex = currentIndex + Math.sign(Number(direction) || 0);
  if (currentIndex < 0 || targetIndex < 0 || targetIndex >= looseStudies.length) return;

  const currentStateIndex = (state.studies || []).findIndex((item) => String(item.id) === String(looseStudies[currentIndex].id));
  const targetStateIndex = (state.studies || []).findIndex((item) => String(item.id) === String(looseStudies[targetIndex].id));
  if (currentStateIndex < 0 || targetStateIndex < 0) return;

  const previousStudies = [...state.studies];
  const hadUpdatedAt = Object.prototype.hasOwnProperty.call(state.meta || {}, "updatedAt");
  const previousUpdatedAt = state.meta?.updatedAt;
  const hadStudiesAudit = Object.prototype.hasOwnProperty.call(state.meta?.sectionAudit || {}, "studies");
  const previousStudiesAudit = state.meta?.sectionAudit?.studies;

  studyImageReorderBusyId = String(study.id);
  state.studies = [...state.studies];
  [state.studies[currentStateIndex], state.studies[targetStateIndex]] = [
    state.studies[targetStateIndex],
    state.studies[currentStateIndex]
  ];
  const audit = buildAuditStamp("modificado");
  state.meta ??= {};
  state.meta.sectionAudit ??= {};
  state.meta.sectionAudit.studies = audit;
  state.meta.updatedAt = audit.at;
  storeClinicalStateLocally();
  renderStudyList();

  const persisted = await persistClinicalState({ silent: true });
  if (!persisted) {
    state.studies = previousStudies;
    if (hadStudiesAudit) state.meta.sectionAudit.studies = previousStudiesAudit;
    else delete state.meta.sectionAudit.studies;
    if (hadUpdatedAt) state.meta.updatedAt = previousUpdatedAt;
    else delete state.meta.updatedAt;
    storeClinicalStateLocally();
    toast(getClinicalPersistenceFailureMessage("No se pudo guardar el nuevo orden de las imagenes"));
  } else {
    toast(direction < 0 ? "Imagen subida una posicion" : "Imagen bajada una posicion");
  }
  studyImageReorderBusyId = "";
  renderStudyList();
  renderStudyViewer();
}

function findStudyImageContext(studyId, imageId) {
  const study = getRepositoryStudies().find((item) => item.id === studyId);
  if (!study) return null;
  const image = getStudyImages(study).find((item) => item.id === imageId);
  return image ? { study, image, readOnly: false, bitmapEdit: isLooseLocalImageStudy(study) } : null;
}

function openStudyImageModal(context) {
  if (!context?.image?.url) return;
  studyImageModalReturnFocus = document.activeElement;
  activeStudyImageContext = context;
  annotationMode = false;
  annotationCommands = [];
  activeAnnotationStroke = null;
  resetAnnotationLayerCanvas();
  $("#studyImageModalTitle").textContent = context.study?.title || context.attachment?.title || "Imagen de estudio";
  $("#studyImageModalMeta").textContent = [
    context.study?.type || context.study?.modality || "Imagen",
    context.bitmapEdit && getLooseImageUploadedAt(context.study) ? `Cargada ${formatDateTime(getLooseImageUploadedAt(context.study))}` : (context.study?.date ? formatDateOptional(context.study.date) : ""),
    context.bitmapEdit
      ? (context.image.bitmapEdited ? "Imagen editada" : "Imagen editable")
      : (context.image.annotated ? "Copia anotada" : "Original preservado")
  ].filter(hasText).join(" - ");
  $("#studyImageCaption").textContent = context.attachment?.caption || context.study?.summary || context.image.label || "";
  $("#studyImageModalImage").src = context.image.url;
  $("#studyImageModalImage").alt = context.image.label || "Imagen de estudio";
  $("#studyImageModalImage").hidden = false;
  $("#studyAnnotationCanvas").hidden = true;
  $("#studyAnnotationToolbar").hidden = true;
  $("#cancelStudyAnnotationBtn").hidden = true;
  $("#saveStudyAnnotationBtn").hidden = true;
  $("#annotateStudyImageBtn").hidden = Boolean(context.readOnly);
  $("#attachStudyImageBtn").hidden = Boolean(context.readOnly);
  $("span", $("#annotateStudyImageBtn")).textContent = context.bitmapEdit ? "Escribir / dibujar" : "Anotar copia";
  $("span", $("#cancelStudyAnnotationBtn")).textContent = context.bitmapEdit ? "Cancelar edicion" : "Cancelar anotacion";
  $("span", $("#saveStudyAnnotationBtn")).textContent = context.bitmapEdit ? "Guardar imagen" : "Guardar copia anotada";
  $("#printStudyImageBtn").hidden = false;
  $("#studyImageModal").classList.add("open");
  $("#studyImageModal").setAttribute("aria-hidden", "false");
  document.body.classList.add("modal-open");
  $("#closeStudyImageModalBtn").focus();
  refreshIcons();
}

function closeStudyImageModal() {
  const modal = $("#studyImageModal");
  if (!modal?.classList.contains("open")) return;
  annotationMode = false;
  annotationBaseImage = null;
  resetAnnotationLayerCanvas();
  annotationCommands = [];
  activeAnnotationStroke = null;
  modal.classList.remove("open");
  modal.setAttribute("aria-hidden", "true");
  $("#studyImageModalImage").removeAttribute("src");
  document.body.classList.remove("modal-open");
  const returnFocus = studyImageModalReturnFocus;
  studyImageModalReturnFocus = null;
  returnFocus?.focus?.();
}

function setStudyImageLoading(isLoading, message = "Preparando imagen...") {
  $("#studyImageLoading").hidden = !isLoading;
  $("#studyImageLoading span").textContent = message;
  refreshIcons();
}

async function ensureLocalStudyImage(context) {
  if (!context?.study || !context?.image) throw new Error("Imagen no disponible");
  const { study, image } = context;
  study.imageAssets = normalizeStudyImageAssets(study.imageAssets);
  let asset = study.imageAssets.find((item) => item.id === image.id || item.originalUrl === image.sourceUrl);
  const currentUrl = image.url || asset?.currentUrl || asset?.localUrl || image.sourceUrl;
  if (isManagedMediaUrl(currentUrl)) {
    if (!asset) {
      asset = { id: image.id, originalUrl: String(image.sourceUrl || "").startsWith("data:") ? "" : image.sourceUrl, localUrl: currentUrl, currentUrl: "", activeVersionId: "", versions: [], importedAt: new Date().toISOString(), audit: buildAuditStamp("cargado") };
      study.imageAssets.push(asset);
    }
    return findStudyImageContext(study.id, image.id) || context;
  }
  const payload = String(image.sourceUrl || "").startsWith("data:")
    ? { dataUrl: image.sourceUrl, fileName: study.fileName || `${study.id}.png`, kind: "original" }
    : { sourceUrl: image.sourceUrl };
  const response = await fetch("/api/media/images", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || !result.url) throw new Error(result.error || "No se pudo importar la imagen al repositorio local");
  if (!asset) {
    asset = { id: image.id, originalUrl: String(image.sourceUrl || "").startsWith("data:") ? "" : image.sourceUrl, localUrl: result.url, currentUrl: "", activeVersionId: "", versions: [], importedAt: new Date().toISOString(), audit: buildAuditStamp("cargado") };
    study.imageAssets.push(asset);
  } else {
    asset.localUrl = result.url;
    asset.importedAt ||= new Date().toISOString();
    asset.audit ||= buildAuditStamp("cargado");
  }
  queueLocalSave();
  renderStudyImages();
  return findStudyImageContext(study.id, image.id) || context;
}

function isManagedMediaUrl(url) {
  try {
    const parsed = new URL(url, window.location.href);
    return parsed.origin === window.location.origin && (
      parsed.pathname.startsWith("/api/media/images/") ||
      parsed.pathname.startsWith("/api/media/studies/")
    );
  } catch (error) {
    return false;
  }
}

async function beginStudyImageAnnotation() {
  if (!activeStudyImageContext || activeStudyImageContext.readOnly || annotationMode) return;
  setStudyImageLoading(true, activeStudyImageContext.bitmapEdit ? "Preparando editor de imagen..." : "Creando copia local segura...");
  try {
    activeStudyImageContext = await ensureLocalStudyImage(activeStudyImageContext);
    const source = activeStudyImageContext.image.url;
    annotationBaseImage = await loadCanvasImage(source);
    const canvas = $("#studyAnnotationCanvas");
    canvas.width = annotationBaseImage.naturalWidth || annotationBaseImage.width;
    canvas.height = annotationBaseImage.naturalHeight || annotationBaseImage.height;
    annotationCommands = [];
    activeAnnotationStroke = null;
    resetAnnotationLayerCanvas();
    $("#studyAnnotationText").value = "";
    annotationMode = true;
    setAnnotationTool("draw");
    setAnnotationFill(annotationFill);
    setAnnotationColor(annotationColor);
    setAnnotationWidth(annotationWidth);
    redrawAnnotationCanvas();
    $("#studyImageModalImage").hidden = true;
    canvas.hidden = false;
    $("#studyAnnotationToolbar").hidden = false;
    $("#annotateStudyImageBtn").hidden = true;
    $("#attachStudyImageBtn").hidden = true;
    $("#printStudyImageBtn").hidden = true;
    $("#cancelStudyAnnotationBtn").hidden = false;
    $("#saveStudyAnnotationBtn").hidden = false;
    $("#studyImageModalMeta").textContent = activeStudyImageContext.bitmapEdit
      ? "Edicion rasterizada - texto y trazos quedaran integrados en la imagen"
      : "Anotacion sobre copia - el original se conserva intacto";
    refreshIcons();
  } catch (error) {
    toast(error.message || "No se pudo preparar la imagen");
  } finally {
    setStudyImageLoading(false);
  }
}

function cancelStudyImageAnnotation() {
  if (!activeStudyImageContext) return;
  annotationMode = false;
  annotationBaseImage = null;
  resetAnnotationLayerCanvas();
  annotationCommands = [];
  activeAnnotationStroke = null;
  openStudyImageModal(activeStudyImageContext);
}

function setAnnotationTool(tool) {
  annotationTool = ["draw", "highlight", "eraser", "text", "circle", "rectangle", "arrow"].includes(tool) ? tool : "draw";
  $$("[data-annotation-tool]").forEach((button) => {
    const active = button.dataset.annotationTool === annotationTool;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", String(active));
  });
  $("#studyAnnotationCanvas")?.classList.toggle("text-tool", annotationTool === "text");
  $("#studyAnnotationCanvas")?.classList.toggle("eraser-tool", annotationTool === "eraser");
  const shapeTool = isAnnotationShapeTool(annotationTool);
  const fillOptions = $("#studyAnnotationFillOptions");
  if (fillOptions) fillOptions.hidden = !shapeTool;
  const eraserTool = annotationTool === "eraser";
  const colorGroup = $("#studyAnnotationColorGroup");
  colorGroup?.classList.toggle("is-disabled", eraserTool);
  colorGroup?.setAttribute("aria-disabled", String(eraserTool));
  $$("[data-annotation-color], #studyAnnotationColor").forEach((control) => {
    control.disabled = eraserTool;
  });
  if ($("#studyAnnotationWidthLabel")) $("#studyAnnotationWidthLabel").textContent = eraserTool ? "Tamaño goma" : "Grosor";
  if (!eraserTool) hideAnnotationEraserCursor();
  const textField = $("#studyAnnotationTextField");
  if (textField) textField.hidden = annotationTool !== "text";
  if (annotationTool === "text") $("#studyAnnotationText")?.focus();
}

function isAnnotationShapeTool(tool) {
  return ["circle", "rectangle", "arrow"].includes(tool);
}

function setAnnotationFill(fill) {
  annotationFill = fill === "solid" ? "solid" : "none";
  $$("[data-annotation-fill]").forEach((button) => {
    const active = button.dataset.annotationFill === annotationFill;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", String(active));
  });
}

function setAnnotationColor(color, { custom = false } = {}) {
  if (!/^#[0-9a-f]{6}$/i.test(String(color || ""))) return;
  annotationColor = color.toLowerCase();
  $$("[data-annotation-color]").forEach((button) => {
    const active = button.dataset.annotationColor.toLowerCase() === annotationColor && !custom;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", String(active));
  });
  if ($("#studyAnnotationColor")?.value.toLowerCase() !== annotationColor) $("#studyAnnotationColor").value = annotationColor;
}

function setAnnotationWidth(width) {
  annotationWidth = [3, 7, 14].includes(Number(width)) ? Number(width) : 7;
  $$("[data-annotation-width]").forEach((button) => {
    const active = Number(button.dataset.annotationWidth) === annotationWidth;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", String(active));
  });
  updateAnnotationEraserCursorSize();
}

function getAnnotationPoint(event) {
  const canvas = $("#studyAnnotationCanvas");
  const rect = canvas.getBoundingClientRect();
  return {
    x: (event.clientX - rect.left) * (canvas.width / Math.max(rect.width, 1)),
    y: (event.clientY - rect.top) * (canvas.height / Math.max(rect.height, 1)),
    scale: canvas.width / Math.max(rect.width, 1)
  };
}

function updateAnnotationEraserCursorSize() {
  const cursor = $("#studyAnnotationEraserCursor");
  if (!cursor) return;
  const size = Math.max(12, annotationWidth * ANNOTATION_ERASER_WIDTH_MULTIPLIER);
  cursor.style.width = `${size}px`;
  cursor.style.height = `${size}px`;
}

function updateAnnotationEraserCursor(event) {
  const cursor = $("#studyAnnotationEraserCursor");
  const canvas = $("#studyAnnotationCanvas");
  const stage = $("#studyImageStage");
  if (!cursor || !canvas || !stage || !annotationMode || annotationTool !== "eraser" || canvas.hidden) {
    hideAnnotationEraserCursor();
    return;
  }
  const canvasRect = canvas.getBoundingClientRect();
  const stageRect = stage.getBoundingClientRect();
  const insideCanvas = event.clientX >= canvasRect.left
    && event.clientX <= canvasRect.right
    && event.clientY >= canvasRect.top
    && event.clientY <= canvasRect.bottom;
  if (!insideCanvas && activeAnnotationStroke?.type !== "eraser") {
    hideAnnotationEraserCursor();
    return;
  }
  updateAnnotationEraserCursorSize();
  cursor.style.left = `${clamp(event.clientX, canvasRect.left, canvasRect.right) - stageRect.left}px`;
  cursor.style.top = `${clamp(event.clientY, canvasRect.top, canvasRect.bottom) - stageRect.top}px`;
  cursor.classList.toggle("is-erasing", activeAnnotationStroke?.type === "eraser");
  cursor.hidden = false;
}

function hideAnnotationEraserCursor() {
  const cursor = $("#studyAnnotationEraserCursor");
  if (!cursor) return;
  cursor.hidden = true;
  cursor.classList.remove("is-erasing");
}

function beginAnnotationPointer(event) {
  if (!annotationMode || (Number.isFinite(event.button) && event.button !== 0)) return;
  event.preventDefault();
  const canvas = $("#studyAnnotationCanvas");
  const point = getAnnotationPoint(event);
  const color = annotationColor;
  const widthMultiplier = annotationTool === "highlight"
    ? 2.4
    : (annotationTool === "eraser" ? ANNOTATION_ERASER_WIDTH_MULTIPLIER : 1);
  const width = annotationWidth * point.scale * widthMultiplier;
  if (annotationTool === "text") {
    const text = $("#studyAnnotationText").value.trim();
    if (!text) {
      toast("Escriba el texto antes de ubicarlo en la imagen");
      $("#studyAnnotationText").focus();
      return;
    }
    const drawingContext = canvas.getContext("2d");
    const margin = Math.max(8, canvas.width * 0.015);
    let fontSize = Math.max(22, Math.min(92, 28 * point.scale));
    drawingContext.font = `700 ${fontSize}px Arial, sans-serif`;
    let textWidth = drawingContext.measureText(text).width;
    if (textWidth > canvas.width - (margin * 2)) {
      fontSize = Math.max(14, fontSize * ((canvas.width - (margin * 2)) / textWidth));
      drawingContext.font = `700 ${fontSize}px Arial, sans-serif`;
      textWidth = drawingContext.measureText(text).width;
    }
    annotationCommands.push({
      type: "text",
      text,
      color,
      x: clamp(point.x, margin, Math.max(margin, canvas.width - textWidth - margin)),
      y: clamp(point.y, fontSize + margin, canvas.height - margin),
      fontSize
    });
    redrawAnnotationCanvas();
    return;
  }
  if (annotationTool === "eraser" && !annotationCommands.some((command) => command.type !== "eraser")) {
    toast("No hay anotaciones nuevas para borrar");
    updateAnnotationEraserCursor(event);
    return;
  }
  canvas.setPointerCapture?.(event.pointerId);
  if (isAnnotationShapeTool(annotationTool)) {
    activeAnnotationStroke = {
      type: "shape",
      shape: annotationTool,
      tool: annotationTool,
      color,
      width,
      filled: annotationFill === "solid",
      square: annotationTool === "rectangle" && event.shiftKey,
      start: { x: point.x, y: point.y },
      end: { x: point.x, y: point.y },
      pointerId: event.pointerId
    };
    redrawAnnotationCanvas();
    return;
  }
  activeAnnotationStroke = {
    type: annotationTool === "eraser" ? "eraser" : "stroke",
    color: annotationTool === "eraser" ? "#000000" : color,
    width,
    opacity: annotationTool === "highlight" ? 0.32 : 1,
    tool: annotationTool,
    pointerId: event.pointerId,
    points: [{ x: point.x, y: point.y }]
  };
  if (annotationTool === "eraser") updateAnnotationEraserCursor(event);
}

function moveAnnotationPointer(event) {
  if (annotationMode && annotationTool === "eraser") updateAnnotationEraserCursor(event);
  if (!annotationMode || !activeAnnotationStroke) return;
  if (Number.isFinite(activeAnnotationStroke.pointerId) && Number.isFinite(event.pointerId) && activeAnnotationStroke.pointerId !== event.pointerId) return;
  event.preventDefault();
  const point = getAnnotationPoint(event);
  if (activeAnnotationStroke.type === "shape") {
    activeAnnotationStroke.end = { x: point.x, y: point.y };
    activeAnnotationStroke.square = activeAnnotationStroke.tool === "rectangle" && event.shiftKey;
    redrawAnnotationCanvas();
    return;
  }
  activeAnnotationStroke.points.push({ x: point.x, y: point.y });
  redrawAnnotationCanvas();
}

function endAnnotationPointer(event) {
  if (!activeAnnotationStroke) return;
  if (Number.isFinite(activeAnnotationStroke.pointerId) && Number.isFinite(event.pointerId) && activeAnnotationStroke.pointerId !== event.pointerId) return;
  event.preventDefault();
  const canvas = $("#studyAnnotationCanvas");
  if (event.type === "pointercancel") {
    activeAnnotationStroke = null;
    try {
      canvas.releasePointerCapture?.(event.pointerId);
    } catch (error) {
      // El navegador puede haber liberado la captura antes de informar la cancelacion.
    }
    hideAnnotationEraserCursor();
    redrawAnnotationCanvas();
    return;
  }
  if (activeAnnotationStroke.type === "shape") {
    const point = getAnnotationPoint(event);
    activeAnnotationStroke.end = { x: point.x, y: point.y };
    activeAnnotationStroke.square = activeAnnotationStroke.tool === "rectangle" && event.shiftKey;
    if (isDrawableAnnotationShape(activeAnnotationStroke)) {
      delete activeAnnotationStroke.pointerId;
      annotationCommands.push(activeAnnotationStroke);
    }
  } else {
    if (activeAnnotationStroke.points.length === 1) activeAnnotationStroke.points.push({ ...activeAnnotationStroke.points[0], x: activeAnnotationStroke.points[0].x + 1 });
    delete activeAnnotationStroke.pointerId;
    annotationCommands.push(activeAnnotationStroke);
  }
  activeAnnotationStroke = null;
  try {
    canvas.releasePointerCapture?.(event.pointerId);
  } catch (error) {
    // La captura puede perderse si el puntero termina fuera del modal.
  }
  redrawAnnotationCanvas();
  if (annotationTool === "eraser") updateAnnotationEraserCursor(event);
}

function getAnnotationShapeGeometry(command) {
  const shape = command?.shape || command?.tool;
  const start = command?.start || { x: 0, y: 0 };
  const end = command?.end || start;
  const deltaX = Number(end.x) - Number(start.x);
  const deltaY = Number(end.y) - Number(start.y);

  if (shape === "circle") {
    const size = Math.max(Math.abs(deltaX), Math.abs(deltaY));
    const signedWidth = (deltaX < 0 ? -1 : 1) * size;
    const signedHeight = (deltaY < 0 ? -1 : 1) * size;
    return {
      kind: "circle",
      centerX: Number(start.x) + (signedWidth / 2),
      centerY: Number(start.y) + (signedHeight / 2),
      radius: size / 2
    };
  }

  if (shape === "rectangle") {
    let finalX = Number(end.x);
    let finalY = Number(end.y);
    if (command.square) {
      const size = Math.max(Math.abs(deltaX), Math.abs(deltaY));
      finalX = Number(start.x) + ((deltaX < 0 ? -1 : 1) * size);
      finalY = Number(start.y) + ((deltaY < 0 ? -1 : 1) * size);
    }
    return {
      kind: "rectangle",
      x: Math.min(Number(start.x), finalX),
      y: Math.min(Number(start.y), finalY),
      width: Math.abs(finalX - Number(start.x)),
      height: Math.abs(finalY - Number(start.y))
    };
  }

  if (shape === "arrow") {
    const length = Math.hypot(deltaX, deltaY);
    if (!length) return { kind: "arrow", length: 0, points: [] };
    const unitX = deltaX / length;
    const unitY = deltaY / length;
    const perpendicularX = -unitY;
    const perpendicularY = unitX;
    const lineWidth = Math.max(1, Number(command.width) || 1);
    const headLength = Math.min(Math.max(lineWidth * 4, length * 0.28), length * 0.55);
    const headHalfWidth = Math.min(Math.max(lineWidth * 2.1, length * 0.12), length * 0.32);
    const shaftHalfWidth = Math.min(Math.max(lineWidth * 0.62, 1.5), headHalfWidth * 0.46);
    const neckX = Number(end.x) - (unitX * headLength);
    const neckY = Number(end.y) - (unitY * headLength);
    const offset = (x, y, amount) => ({ x: x + (perpendicularX * amount), y: y + (perpendicularY * amount) });
    return {
      kind: "arrow",
      length,
      points: [
        offset(Number(start.x), Number(start.y), shaftHalfWidth),
        offset(neckX, neckY, shaftHalfWidth),
        offset(neckX, neckY, headHalfWidth),
        { x: Number(end.x), y: Number(end.y) },
        offset(neckX, neckY, -headHalfWidth),
        offset(neckX, neckY, -shaftHalfWidth),
        offset(Number(start.x), Number(start.y), -shaftHalfWidth)
      ]
    };
  }

  return { kind: "unknown" };
}

function isDrawableAnnotationShape(command) {
  const geometry = getAnnotationShapeGeometry(command);
  const lineWidth = Math.max(1, Number(command?.width) || 1);
  if (geometry.kind === "circle") return geometry.radius >= Math.max(2, lineWidth * 0.75);
  if (geometry.kind === "rectangle") {
    return Math.min(geometry.width, geometry.height) >= 2
      && Math.hypot(geometry.width, geometry.height) >= Math.max(6, lineWidth * 1.5);
  }
  if (geometry.kind === "arrow") return geometry.length >= Math.max(6, lineWidth * 2);
  return false;
}

function drawAnnotationShape(context, command) {
  const geometry = getAnnotationShapeGeometry(command);
  if (!isDrawableAnnotationShape(command)) return;
  context.save();
  context.strokeStyle = command.color;
  context.fillStyle = command.color;
  context.lineWidth = command.width;
  context.lineCap = "round";
  context.lineJoin = "round";
  context.beginPath();
  if (geometry.kind === "circle") {
    context.arc(geometry.centerX, geometry.centerY, geometry.radius, 0, Math.PI * 2);
  } else if (geometry.kind === "rectangle") {
    context.rect(geometry.x, geometry.y, geometry.width, geometry.height);
  } else if (geometry.kind === "arrow") {
    geometry.points.forEach((point, index) => index ? context.lineTo(point.x, point.y) : context.moveTo(point.x, point.y));
    context.closePath();
  }
  if (command.filled) {
    context.globalAlpha = geometry.kind === "arrow" ? 0.42 : 0.22;
    context.fill();
    context.globalAlpha = 1;
  }
  context.stroke();
  context.restore();
}

function drawAnnotationStroke(context, command) {
  const eraser = command.type === "eraser";
  context.save();
  if (eraser) context.globalCompositeOperation = "destination-out";
  context.strokeStyle = eraser ? "#000000" : command.color;
  context.lineWidth = command.width;
  context.globalAlpha = eraser ? 1 : Number(command.opacity ?? 1);
  context.lineCap = "round";
  context.lineJoin = "round";
  context.beginPath();
  command.points.forEach((point, index) => index ? context.lineTo(point.x, point.y) : context.moveTo(point.x, point.y));
  context.stroke();
  context.restore();
}

function drawAnnotationText(context, command) {
  context.save();
  context.font = `700 ${command.fontSize}px Arial, sans-serif`;
  context.lineWidth = Math.max(3, command.fontSize / 9);
  context.strokeStyle = "rgba(255,255,255,.95)";
  context.strokeText(command.text, command.x, command.y);
  context.fillStyle = command.color;
  context.fillText(command.text, command.x, command.y);
  context.restore();
}

function drawAnnotationCommand(context, command) {
  if (command.type === "stroke" || command.type === "eraser") {
    drawAnnotationStroke(context, command);
    return;
  }
  if (command.type === "shape") {
    drawAnnotationShape(context, command);
    return;
  }
  if (command.type === "text") drawAnnotationText(context, command);
}

function getAnnotationLayerCanvas(canvas) {
  if (!annotationLayerCanvas) annotationLayerCanvas = document.createElement("canvas");
  if (annotationLayerCanvas.width !== canvas.width || annotationLayerCanvas.height !== canvas.height) {
    annotationLayerCanvas.width = canvas.width;
    annotationLayerCanvas.height = canvas.height;
  }
  return annotationLayerCanvas;
}

function resetAnnotationLayerCanvas() {
  if (annotationLayerCanvas) {
    annotationLayerCanvas.width = 1;
    annotationLayerCanvas.height = 1;
  }
  annotationLayerCanvas = null;
  hideAnnotationEraserCursor();
}

function redrawAnnotationCanvas() {
  const canvas = $("#studyAnnotationCanvas");
  if (!canvas || !annotationBaseImage) return;
  const context = canvas.getContext("2d");
  context.clearRect(0, 0, canvas.width, canvas.height);
  context.drawImage(annotationBaseImage, 0, 0, canvas.width, canvas.height);
  const annotationLayer = getAnnotationLayerCanvas(canvas);
  const annotationContext = annotationLayer.getContext("2d");
  annotationContext.clearRect(0, 0, annotationLayer.width, annotationLayer.height);
  [...annotationCommands, ...(activeAnnotationStroke ? [activeAnnotationStroke] : [])].forEach((command) => {
    drawAnnotationCommand(annotationContext, command);
  });
  context.drawImage(annotationLayer, 0, 0, canvas.width, canvas.height);
  updateAnnotationActionState();
}

function updateAnnotationActionState() {
  const hasCommands = annotationCommands.length > 0;
  if ($("#undoStudyAnnotationBtn")) $("#undoStudyAnnotationBtn").disabled = !hasCommands;
  if ($("#clearStudyAnnotationsBtn")) $("#clearStudyAnnotationsBtn").disabled = !hasCommands;
  if ($("#saveStudyAnnotationBtn")) $("#saveStudyAnnotationBtn").disabled = !hasCommands;
}

function undoStudyAnnotation() {
  if (!annotationCommands.length) return;
  annotationCommands.pop();
  redrawAnnotationCanvas();
}

function clearStudyAnnotations() {
  annotationCommands = [];
  activeAnnotationStroke = null;
  redrawAnnotationCanvas();
}

async function saveStudyImageAnnotation() {
  if (!annotationMode || !activeStudyImageContext) return;
  if (!annotationCommands.length) {
    toast("Agregue una marca o texto antes de guardar");
    return;
  }
  const bitmapEdit = Boolean(activeStudyImageContext.bitmapEdit);
  setStudyImageLoading(true, bitmapEdit ? "Guardando imagen editada..." : "Guardando copia anotada...");
  let rollback = null;
  try {
    redrawAnnotationCanvas();
    const canvas = $("#studyAnnotationCanvas");
    const dataUrl = canvas.toDataURL("image/png");
    const response = await fetch("/api/media/images", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ dataUrl, fileName: `${activeStudyImageContext.study.id}-${activeStudyImageContext.image.id}.png`, kind: "annotation" })
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok || !result.url) throw new Error(result.error || "No se pudo guardar la copia anotada");
    const study = activeStudyImageContext.study;
    study.imageAssets = normalizeStudyImageAssets(study.imageAssets);
    const previousImageAssets = study.imageAssets.map((item) => JSON.parse(JSON.stringify(item)));
    const hadStudyUpdatedAt = Object.prototype.hasOwnProperty.call(study, "updatedAt");
    const previousStudyUpdatedAt = study.updatedAt;
    const hadBitmapEditedAt = Object.prototype.hasOwnProperty.call(study, "bitmapEditedAt");
    const previousBitmapEditedAt = study.bitmapEditedAt;
    const hadUpdatedAt = Object.prototype.hasOwnProperty.call(state.meta || {}, "updatedAt");
    const previousUpdatedAt = state.meta?.updatedAt;
    const hadStudiesAudit = Object.prototype.hasOwnProperty.call(state.meta?.sectionAudit || {}, "studies");
    const previousStudiesAudit = state.meta?.sectionAudit?.studies;
    rollback = () => {
      study.imageAssets = previousImageAssets;
      if (hadStudyUpdatedAt) study.updatedAt = previousStudyUpdatedAt;
      else delete study.updatedAt;
      if (hadBitmapEditedAt) study.bitmapEditedAt = previousBitmapEditedAt;
      else delete study.bitmapEditedAt;
      if (hadStudiesAudit) {
        state.meta ??= {};
        state.meta.sectionAudit ??= {};
        state.meta.sectionAudit.studies = previousStudiesAudit;
      } else if (state.meta?.sectionAudit) {
        delete state.meta.sectionAudit.studies;
      }
      if (hadUpdatedAt) {
        state.meta ??= {};
        state.meta.updatedAt = previousUpdatedAt;
      } else if (state.meta) {
        delete state.meta.updatedAt;
      }
      storeClinicalStateLocally();
      renderStudyList();
    };
    const asset = study.imageAssets.find((item) => item.id === activeStudyImageContext.image.id || item.originalUrl === activeStudyImageContext.image.sourceUrl);
    if (!asset) throw new Error("No se encontro el original de la imagen");
    const audit = buildAuditStamp("cargado");
    const version = {
      id: makeId("image-version"),
      url: result.url,
      createdAt: audit.at,
      width: canvas.width,
      height: canvas.height,
      mime: result.mime || "image/png",
      kind: bitmapEdit ? "bitmap-edit" : "annotation",
      rasterized: true,
      commandCount: annotationCommands.length,
      tools: [...new Set(annotationCommands.map((command) => command.type === "text" ? "text" : (command.tool || "draw")))],
      audit
    };
    asset.versions.push(version);
    asset.activeVersionId = version.id;
    asset.currentUrl = result.url;
    asset.updatedAt = audit.at;
    study.updatedAt = audit.at;
    if (bitmapEdit) study.bitmapEditedAt = audit.at;
    state.meta ??= {};
    state.meta.sectionAudit ??= {};
    state.meta.sectionAudit.studies = audit;
    state.meta.updatedAt = audit.at;
    storeClinicalStateLocally();
    const persisted = await persistClinicalState({ silent: true });
    if (!persisted) {
      rollback();
      rollback = null;
      throw new Error(getClinicalPersistenceFailureMessage("No se pudo confirmar el guardado de la imagen"));
    }
    activeStudyImageContext = findStudyImageContext(study.id, activeStudyImageContext.image.id);
    annotationMode = false;
    renderStudyImages();
    openStudyImageModal(activeStudyImageContext);
    rollback = null;
    toast(bitmapEdit ? "Imagen actualizada" : "Copia anotada guardada; el original permanece intacto");
  } catch (error) {
    rollback?.();
    toast(error.message || "No se pudo guardar la anotacion");
  } finally {
    setStudyImageLoading(false);
  }
}

function loadCanvasImage(source) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("No se pudo abrir la imagen para anotar"));
    image.src = source;
  });
}

function printStudyImageContext(context) {
  if (!context?.image?.url) return;
  const popup = window.open("", "_blank", "width=1000,height=760");
  if (!popup) {
    toast("El navegador bloqueo la ventana de impresion");
    return;
  }
  const title = context.study?.title || context.attachment?.title || "Imagen de estudio";
  const meta = [context.study?.date ? formatDateOptional(context.study.date) : "", context.study?.type || "", context.image.annotated ? "Copia anotada" : "Imagen original"].filter(hasText).join(" - ");
  const caption = context.attachment?.caption || context.study?.summary || "";
  const templateSource = normalizeStudyTemplateSource(context.study?.templateSource || context.attachment?.templateSource);
  const templateCredit = templateSource
    ? `<p class="credit"><strong>Plantilla:</strong> ${escapeHtml(templateSource.attribution || [templateSource.author, templateSource.license].filter(hasText).join(" · "))}${templateSource.sourceUrl ? ` · <a href="${escapeAttr(templateSource.sourceUrl)}">Fuente</a>` : ""}</p>`
    : "";
  popup.document.write(`<html><head><title>${escapeHtml(title)}</title><style>@page{size:auto;margin:12mm}*{box-sizing:border-box}body{margin:0;font:12px Arial,sans-serif;color:#2f4050}header{margin-bottom:8px;border-bottom:1px solid #d7e0e7;padding-bottom:8px}h1{margin:0 0 4px;font-size:17px}p{margin:3px 0;white-space:pre-wrap}.credit{color:#647582;font-size:10px}.credit a{color:#087fc4}.image{display:flex;align-items:center;justify-content:center;width:100%;height:calc(100vh - 115px)}img{display:block;max-width:100%;max-height:100%;object-fit:contain}@media print{.image{height:calc(100vh - 100px)}}</style></head><body><header><h1>${escapeHtml(title)}</h1><p>${escapeHtml(meta)}</p>${caption ? `<p>${escapeHtml(caption)}</p>` : ""}${templateCredit}</header><div class="image"><img src="${escapeAttr(context.image.url)}" alt="${escapeAttr(title)}"></div><script>window.addEventListener('load',()=>window.print())<\/script></body></html>`);
  popup.document.close();
}

function printActiveStudyImage() {
  printStudyImageContext(activeStudyImageContext);
}

async function attachStudyImageContextToEvolution(context) {
  setStudyImageLoading(Boolean($("#studyImageModal")?.classList.contains("open")), "Guardando referencia local...");
  try {
    const localContext = await ensureLocalStudyImage(context);
    const audit = buildAuditStamp("cargado");
    const attachment = normalizeEvolutionAttachment({
      id: makeId("evo-image"),
      studyId: localContext.study.id,
      imageId: localContext.image.id,
      versionId: localContext.image.versionId || "original",
      url: localContext.image.url,
      thumbnailUrl: localContext.image.url,
      title: localContext.study.title || "Imagen de estudio",
      studyDate: localContext.study.date || "",
      studyType: localContext.study.type || localContext.study.modality || "Imagen",
      templateSource: localContext.study.templateSource || null,
      caption: "",
      audit,
      createdAt: audit.at
    });
    closeStudyImageModal();
    openEvolutionModal("evolucion", null, { attachment });
    const prefix = `Imagen del estudio ${attachment.title}${attachment.studyDate ? ` del ${formatDateOptional(attachment.studyDate)}` : ""}. Hallazgo / comentario: `;
    $("#evolutionText").value = prefix;
    $("#evolutionText").focus();
    $("#evolutionText").setSelectionRange(prefix.length, prefix.length);
    toast("Imagen adjunta; complete el texto y cargue la evolucion");
  } catch (error) {
    toast(error.message || "No se pudo adjuntar la imagen");
  } finally {
    setStudyImageLoading(false);
  }
}

async function attachActiveStudyImageToEvolution() {
  if (activeStudyImageContext) await attachStudyImageContextToEvolution(activeStudyImageContext);
}

function renderEvolutionAttachmentBand() {
  const band = $("#evolutionAttachmentBand");
  if (!band) return;
  const attachments = normalizeEvolutionAttachments(evolutionDraftAttachments);
  band.hidden = !attachments.length;
  band.innerHTML = attachments.map((attachment) => `
    <article class="evolution-draft-attachment">
      <button class="evolution-draft-thumbnail" type="button" data-evolution-attachment-action="view" data-id="${escapeAttr(attachment.id)}" title="Ampliar imagen"><img src="${escapeAttr(attachment.thumbnailUrl || attachment.url)}" alt="${escapeAttr(attachment.title)}"></button>
      <div><span>Imagen adjunta</span><strong>${escapeHtml(attachment.title)}</strong><small>${escapeHtml([attachment.studyType, attachment.studyDate ? formatDateOptional(attachment.studyDate) : "", attachment.templateSource?.license].filter(hasText).join(" - "))}</small></div>
      <button class="icon-button" type="button" data-evolution-attachment-action="remove" data-id="${escapeAttr(attachment.id)}" title="Quitar imagen"><i data-lucide="x"></i></button>
    </article>
  `).join("");
  refreshIcons();
}

function handleEvolutionAttachmentAction(event) {
  const button = event.target.closest("[data-evolution-attachment-action]");
  if (!button) return;
  const attachment = evolutionDraftAttachments.find((item) => item.id === button.dataset.id);
  if (!attachment) return;
  if (button.dataset.evolutionAttachmentAction === "remove") {
    evolutionDraftAttachments = evolutionDraftAttachments.filter((item) => item.id !== attachment.id);
    renderEvolutionAttachmentBand();
  }
  if (button.dataset.evolutionAttachmentAction === "view") openEvolutionAttachmentViewer(attachment);
}

function openEvolutionAttachmentViewer(attachment) {
  openStudyImageModal({
    readOnly: true,
    attachment,
    study: { title: attachment.title, date: attachment.studyDate, type: attachment.studyType, summary: attachment.caption || "", templateSource: attachment.templateSource || null },
    image: { id: attachment.imageId, url: attachment.url, label: attachment.title, annotated: attachment.versionId !== "original" }
  });
}

async function persistStateSilently() {
  state.meta.updatedAt ||= new Date().toISOString();
  storeClinicalStateLocally();
  return persistClinicalState({ silent: true });
}

function renderStudyViewer() {
  renderStudyImages();
  const viewer = $("#studyViewer");
  if (!viewer) return;
  const study = getRepositoryStudies().find((item) => item.id === selectedStudyId);
  if (!study) {
    viewer.innerHTML = `<div class="viewer-empty">Sin estudio seleccionado</div>`;
    return;
  }

  if (study.dataUrl && study.fileType?.startsWith("image/")) {
    viewer.innerHTML = `<img src="${study.dataUrl}" alt="${escapeAttr(study.title)}">`;
    return;
  }

  if (study.dataUrl && study.fileType === "application/pdf") {
    viewer.innerHTML = `<embed src="${study.dataUrl}" type="application/pdf">`;
    return;
  }

  viewer.innerHTML = `
    <div class="viewer-note">
      <span class="study-type">${escapeHtml(getStudyBadge(study))}</span>
      <h3>${escapeHtml(study.title || "Estudio")}</h3>
      <p class="study-meta">${escapeHtml(getStudyMetaLine(study))}</p>
      <p class="study-summary">${escapeHtml(study.summary || "Estudio disponible en el repositorio externo.")}</p>
      <div class="study-link-actions">${renderStudyExternalLinks(study)}</div>
    </div>
  `;
}

async function loadResearchTemplates(force = false) {
  if (researchTemplatesLoaded && !force) {
    renderResearchTemplateSelector();
    return;
  }
  try {
    const response = await fetch(`/api/clinical/configuration/research-form?t=${Date.now()}`, { cache: "no-store" });
    const payload = await response.json();
    if (!response.ok) throw new Error(payload.error || "No se pudieron cargar los formularios configurables.");
    researchFormTemplates = (payload.items || []).filter((item) => item.active);
    researchTemplatesLoaded = true;
    if (activeResearchTemplateId && !researchFormTemplates.some((item) => item.id === activeResearchTemplateId)) activeResearchTemplateId = "";
    renderResearchTemplateSelector();
  } catch (error) {
    researchFormTemplates = [];
    researchTemplatesLoaded = false;
    renderResearchTemplateSelector();
  }
}

function activeResearchTemplate() {
  return researchFormTemplates.find((item) => item.id === String(activeResearchTemplateId)) || null;
}

function renderResearchTemplateSelector() {
  const select = $("#researchTemplateSelect");
  if (!select) return;
  const selected = activeResearchTemplate();
  select.innerHTML = `<option value="">Registro general de investigacion</option>${researchFormTemplates.map((item) => `<option value="${escapeAttr(item.id)}">${escapeHtml(item.name)}</option>`).join("")}`;
  select.value = selected?.id || "";
  $("#researchTemplateTitle").textContent = selected?.name || "Registro general de investigacion";
  $("#researchTemplateInstructions").textContent = selected?.definition?.instructions || "Formulario estructurado habitual.";
  $$(".research-section", $("#researchForm")).forEach((section) => { section.hidden = Boolean(selected); });
  $("#researchCustomFields").hidden = !selected;
  if (selected) renderCustomResearchFields(selected);
}

function setResearchTemplate(templateId) {
  activeResearchTemplateId = String(templateId || "");
  renderResearchTemplateSelector();
  resetResearchForm({ preserveProtocol: true });
}

function renderCustomResearchFields(template) {
  const container = $("#researchCustomFields");
  if (!container) return;
  const fields = template?.definition?.fields || [];
  container.innerHTML = fields.map((field) => {
    if (field.type === "section") return `<h3 class="research-custom-section">${escapeHtml(field.label)}</h3>`;
    const required = field.required ? " required" : "";
    const help = field.help ? `<small>${escapeHtml(field.help)}</small>` : "";
    const placeholder = escapeAttr(field.placeholder || "");
    if (field.type === "textarea") return `<label class="research-custom-field wide"><span>${escapeHtml(field.label)}${field.required ? " *" : ""}</span><textarea data-research-custom-key="${escapeAttr(field.key)}" rows="4" placeholder="${placeholder}"${required}></textarea>${help}</label>`;
    if (field.type === "select") return `<label class="research-custom-field"><span>${escapeHtml(field.label)}${field.required ? " *" : ""}</span><select data-research-custom-key="${escapeAttr(field.key)}"${required}><option value="">Seleccione</option>${(field.options || []).map((option) => `<option value="${escapeAttr(option.value)}">${escapeHtml(option.label)}</option>`).join("")}</select>${help}</label>`;
    if (field.type === "checkbox") return `<label class="research-custom-field checkbox"><input data-research-custom-key="${escapeAttr(field.key)}" type="checkbox"><span>${escapeHtml(field.label)}</span></label>`;
    return `<label class="research-custom-field"><span>${escapeHtml(field.label)}${field.required ? " *" : ""}</span><input data-research-custom-key="${escapeAttr(field.key)}" type="${field.type === "number" ? "number" : field.type === "date" ? "date" : "text"}" placeholder="${placeholder}"${field.min != null ? ` min="${escapeAttr(field.min)}"` : ""}${field.max != null ? ` max="${escapeAttr(field.max)}"` : ""}${required}>${help}</label>`;
  }).join("") || `<div class="research-template-empty">Este formulario no tiene campos activos.</div>`;
}

function collectCustomResearchRecord(template) {
  const audit = buildAuditStamp("cargado");
  const schema = (template.definition?.fields || []).map((field) => ({ ...field, options: Array.isArray(field.options) ? field.options.map((option) => ({ ...option })) : [] }));
  const values = Object.fromEntries(schema.filter((field) => field.type !== "section").map((field) => {
    const control = $(`[data-research-custom-key="${CSS.escape(field.key)}"]`, $("#researchCustomFields"));
    return [field.key, field.type === "checkbox" ? Boolean(control?.checked) : String(control?.value || "")];
  }));
  const firstDate = schema.find((field) => field.type === "date" && values[field.key])?.key;
  return {
    id: makeId("research"),
    date: firstDate ? values[firstDate] : today(),
    type: template.name,
    protocol: { name: template.name, code: `FORM-${template.id}`, phase: "", sponsor: "", center: "" },
    participant: { code: state.patient?.dni || state.patient?.medicalRecord || "", status: "Registrado", arm: "", randomizationCode: "", consentStatus: "", consentDate: "", consentVersion: "", eligibility: "", ineligibilityReason: "" },
    customForm: {
      templateId: template.id,
      templateKey: template.key,
      templateRevision: template.revision,
      templateName: template.name,
      schemaSnapshot: schema,
      values,
    },
    audit,
    createdAt: audit.at,
    updatedAt: audit.at,
  };
}

function validateCustomResearchRecord(record) {
  const custom = record.customForm;
  for (const field of custom?.schemaSnapshot || []) {
    if (field.type === "section" || !field.required) continue;
    const value = custom.values?.[field.key];
    if (field.type === "checkbox" ? value !== true : !String(value || "").trim()) {
      return { message: `Complete ${field.label}.`, customKey: field.key };
    }
  }
  return null;
}

function resetResearchForm({ preserveProtocol = false } = {}) {
  const form = $("#researchForm");
  if (!form || !state) return;
  if (activeResearchTemplate()) {
    $$('[data-research-custom-key]', $("#researchCustomFields")).forEach((control) => {
      if (control.type === "checkbox") control.checked = false;
      else control.value = control.type === "date" ? today() : "";
    });
    return;
  }
  const preserved = preserveProtocol ? {
    name: $("#researchProtocolName")?.value || "",
    code: $("#researchProtocolCode")?.value || "",
    phase: $("#researchPhase")?.value || "",
    sponsor: $("#researchSponsor")?.value || "",
    center: $("#researchCenter")?.value || "",
    participantCode: $("#researchParticipantCode")?.value || ""
  } : {};
  form.reset();
  $("#researchEventDate").value = today();
  $("#researchProtocolName").value = preserved.name || "";
  $("#researchProtocolCode").value = preserved.code || "";
  $("#researchPhase").value = preserved.phase || "";
  $("#researchSponsor").value = preserved.sponsor || "";
  $("#researchCenter").value = preserved.center || "";
  $("#researchParticipantCode").value = preserved.participantCode || "";
  $("#researchDiagnosis").value = [state.oncology.diagnosis, state.oncology.topography].filter(hasText).join(" - ");
  $("#researchHistology").value = state.oncology.histology || "";
  $("#researchStage").value = state.oncology.stage || "";
  $("#researchBiomarkers").value = state.oncology.biomarkers || "";
  const ecog = String(state.oncology.performanceStatus || "").match(/\b[0-5]\b/)?.[0] || "";
  $("#researchEcog").value = ecog;
}

function collectResearchRecord() {
  const template = activeResearchTemplate();
  if (template) return collectCustomResearchRecord(template);
  const audit = buildAuditStamp("cargado");
  return {
    id: makeId("research"),
    date: $("#researchEventDate").value,
    type: $("#researchRecordType").value,
    protocol: {
      name: $("#researchProtocolName").value.trim(),
      code: $("#researchProtocolCode").value.trim(),
      phase: $("#researchPhase").value,
      sponsor: $("#researchSponsor").value.trim(),
      center: $("#researchCenter").value.trim()
    },
    participant: {
      code: $("#researchParticipantCode").value.trim(),
      status: $("#researchParticipantStatus").value,
      arm: $("#researchArm").value.trim(),
      randomizationCode: $("#researchRandomizationCode").value.trim(),
      consentStatus: $("#researchConsentStatus").value,
      consentDate: $("#researchConsentDate").value,
      consentVersion: $("#researchConsentVersion").value.trim(),
      eligibility: $("#researchEligibility").value,
      ineligibilityReason: $("#researchIneligibilityReason").value.trim()
    },
    clinical: {
      diagnosis: $("#researchDiagnosis").value.trim(),
      histology: $("#researchHistology").value.trim(),
      stage: $("#researchStage").value.trim(),
      biomarkers: $("#researchBiomarkers").value.trim(),
      ecog: $("#researchEcog").value,
      intervention: $("#researchIntervention").value.trim(),
      treatmentLine: $("#researchTreatmentLine").value.trim(),
      cycle: $("#researchCycle").value.trim(),
      day: $("#researchDay").value.trim()
    },
    assessment: {
      criteria: $("#researchResponseCriteria").value,
      response: $("#researchResponse").value,
      date: $("#researchAssessmentDate").value,
      results: $("#researchResults").value.trim()
    },
    safety: {
      event: $("#researchAdverseEvent").value.trim(),
      grade: $("#researchAdverseGrade").value,
      relation: $("#researchAdverseRelation").value,
      action: $("#researchAdverseAction").value.trim()
    },
    followUp: {
      samples: $("#researchSamples").value.trim(),
      deviations: $("#researchDeviations").value.trim(),
      nextVisit: $("#researchNextVisit").value,
      pending: $("#researchPending").value.trim(),
      notes: $("#researchNotes").value.trim()
    },
    audit,
    createdAt: audit.at,
    updatedAt: audit.at
  };
}

function validateResearchRecord(record) {
  if (record.customForm) return validateCustomResearchRecord(record);
  if (!record.protocol.name || !record.protocol.code || !record.participant.code || !record.type || !record.date || !record.participant.status) {
    return { message: "Complete protocolo, codigo, participante, tipo de registro, fecha y estado.", field: !record.protocol.name ? "researchProtocolName" : !record.protocol.code ? "researchProtocolCode" : !record.participant.code ? "researchParticipantCode" : !record.date ? "researchEventDate" : "researchParticipantStatus" };
  }
  if (record.participant.consentStatus === "Firmado" && (!record.participant.consentDate || !record.participant.consentVersion)) {
    return { message: "El consentimiento firmado requiere fecha y version.", field: !record.participant.consentDate ? "researchConsentDate" : "researchConsentVersion" };
  }
  const includedStatuses = new Set(["Incluido", "Aleatorizado", "En tratamiento"]);
  if (includedStatuses.has(record.participant.status) && record.participant.eligibility !== "Cumple criterios") {
    return { message: "Para incluir al participante debe constar que cumple los criterios de elegibilidad.", field: "researchEligibility" };
  }
  if (record.participant.status === "Aleatorizado" && !record.participant.randomizationCode) {
    return { message: "La aleatorizacion requiere un codigo de asignacion.", field: "researchRandomizationCode" };
  }
  if (record.participant.eligibility === "No cumple criterios" && !record.participant.ineligibilityReason) {
    return { message: "Indique el motivo de no elegibilidad.", field: "researchIneligibilityReason" };
  }
  if ((record.safety.event && !record.safety.grade) || (!record.safety.event && record.safety.grade)) {
    return { message: "Consigne el evento adverso y su grado CTCAE.", field: record.safety.event ? "researchAdverseGrade" : "researchAdverseEvent" };
  }
  if (record.assessment.response === "Progresion" && !record.assessment.date) {
    return { message: "La progresion requiere fecha de evaluacion.", field: "researchAssessmentDate" };
  }
  return null;
}

async function saveResearchRecord(event) {
  event.preventDefault();
  collectBindableFields();
  const record = collectResearchRecord();
  const validation = validateResearchRecord(record);
  if (validation) {
    toast(validation.message);
    if (validation.customKey) $(`[data-research-custom-key="${CSS.escape(validation.customKey)}"]`)?.focus();
    else $("#" + validation.field)?.focus();
    return;
  }
  state.researchRecords.push(record);
  state.meta.updatedAt = record.audit.at;
  invalidateAiTimeline();
  storeClinicalStateLocally();
  renderResearchPanel();
  renderPreview();
  renderPatientOutputs();
  resetResearchForm({ preserveProtocol: true });
  await saveState();
  toast("Registro de investigacion incorporado a la historia clinica");
}

function formatResearchRecordLines(record) {
  if (record.customForm) {
    return (record.customForm.schemaSnapshot || []).filter((field) => field.type !== "section").map((field) => {
      const raw = record.customForm.values?.[field.key];
      const option = field.type === "select" ? (field.options || []).find((item) => String(item.value) === String(raw)) : null;
      const value = field.type === "checkbox" ? (raw ? "Si" : "No") : option?.label || raw;
      return { label: field.label, value: String(value || "") };
    }).filter((line) => hasText(line.value));
  }
  const protocol = record.protocol || {};
  const participant = record.participant || {};
  const clinical = record.clinical || {};
  const assessment = record.assessment || {};
  const safety = record.safety || {};
  const followUp = record.followUp || {};
  const lines = [];
  const add = (label, parts) => {
    const value = (Array.isArray(parts) ? parts : [parts]).filter(hasText).join("; ");
    if (value) lines.push({ label, value });
  };
  add("Evento", [record.type, `Participante: ${participant.code || ""}`, `Estado: ${participant.status || ""}`]);
  add("Protocolo", [protocol.phase, protocol.sponsor, protocol.center]);
  add("Consentimiento", [participant.consentStatus, participant.consentDate ? formatDateOptional(participant.consentDate) : "", participant.consentVersion ? `Version ${participant.consentVersion}` : ""]);
  add("Elegibilidad", [participant.eligibility, participant.ineligibilityReason]);
  add("Situacion oncologica", [clinical.diagnosis, clinical.histology, clinical.stage ? `Estadio ${clinical.stage}` : "", clinical.ecog ? `ECOG ${clinical.ecog}` : "", clinical.biomarkers]);
  add("Asignacion y tratamiento", [participant.arm ? `Brazo/cohorte ${participant.arm}` : "", participant.randomizationCode ? `Aleatorizacion ${participant.randomizationCode}` : "", clinical.intervention, clinical.treatmentLine ? `Linea ${clinical.treatmentLine}` : "", clinical.cycle ? `Ciclo ${clinical.cycle}` : "", clinical.day ? `Dia ${clinical.day}` : ""]);
  add("Evaluacion", [assessment.criteria, assessment.response, assessment.date ? formatDateOptional(assessment.date) : "", assessment.results]);
  add("Seguridad", [safety.event, safety.grade ? `CTCAE grado ${safety.grade}` : "", safety.relation, safety.action]);
  add("Muestras / estudios", followUp.samples);
  add("Desvios del protocolo", followUp.deviations);
  add("Seguimiento", [followUp.nextVisit ? `Proxima visita ${formatDateOptional(followUp.nextVisit)}` : "", followUp.pending ? `Pendientes: ${followUp.pending}` : ""]);
  add("Observaciones", followUp.notes);
  return lines;
}

function formatResearchRecordText(record) {
  const protocol = record.protocol || {};
  const heading = `INVESTIGACION CLINICA - ${protocol.code || "PROTOCOLO"}${protocol.name ? ` - ${protocol.name}` : ""}`;
  return [heading, ...formatResearchRecordLines(record).map((line) => `${line.label}: ${line.value}`)].join("\n");
}

function renderResearchPanel() {
  if (!state || !$("#researchRecordList")) return;
  const records = [...(state.researchRecords || [])].sort((a, b) => {
    const dateOrder = (b.date || "").localeCompare(a.date || "");
    return dateOrder || (b.createdAt || "").localeCompare(a.createdAt || "");
  });
  $("#researchRecordCount").textContent = `${records.length} ${records.length === 1 ? "registro" : "registros"}`;
  $("#researchRecordList").innerHTML = records.length ? records.map((record) => `
    <article class="research-record-card">
      <div class="research-record-date"><i data-lucide="microscope"></i><span>${escapeHtml(formatDateOptional(record.date))}</span></div>
      <div class="research-record-copy">
        <small>${escapeHtml(record.protocol?.code || "Protocolo")} - ${escapeHtml(record.type || "Registro")}</small>
        <strong>${escapeHtml(record.protocol?.name || "Investigacion oncologica")}</strong>
        <p>${escapeHtml(formatResearchRecordLines(record).map((line) => `${line.label}: ${line.value}`).join("\n"))}</p>
      </div>
      <button class="icon-button" type="button" data-action="focus-research-record" data-id="${escapeAttr(record.id)}" title="Ver en historia clinica"><i data-lucide="locate-fixed"></i></button>
    </article>
  `).join("") : `<div class="research-records-empty"><i data-lucide="microscope"></i><span>Todavia no hay registros incorporados.</span></div>`;
  refreshIcons();
}

function focusResearchRecord(id) {
  setActiveTab("historia");
  const target = $(`[data-research-id="${CSS.escape(id)}"]`, $("#clinicalDocument"));
  if (!target) return;
  target.scrollIntoView({ behavior: "smooth", block: "center" });
  target.classList.add("clinical-record-focus");
  window.setTimeout(() => target.classList.remove("clinical-record-focus"), 3500);
}

function renderRightTimeline() {
  const timeline = $("#rightTimeline");
  if (!timeline || !state) return;
  syncClinicalSearchInput();

  const aiEntries = getAiTimelineEntries();
  const timelineCurrent = isTimelineAiCurrent();
  const usingLocalTimeline = !timelineCurrent || !aiEntries.length;
  const allEntries = usingLocalTimeline ? getDeterministicTimelineEntries() : aiEntries;
  if (!timelineCurrent && !timelineAiLoading && !timelineAiAttempted) window.setTimeout(generateTimelineWithLlm, 0);
  if (!allEntries.length) {
    timeline.innerHTML = `
      <div class="right-empty-state timeline-ai-state">
        <i data-lucide="${timelineAiLoading ? "loader-circle" : timelineAiError ? "triangle-alert" : "sparkles"}"></i>
        <strong>${timelineAiLoading ? "Analizando historia clinica" : timelineAiError ? "No se pudo analizar la historia" : "Sin registros para la linea de tiempo"}</strong>
        <span>${timelineAiLoading ? "Se estan extrayendo fechas, categorias e hitos." : timelineAiError || "Todavia no hay evoluciones, estudios, tratamientos ni indicaciones con los que construirla."}</span>
        ${timelineAiError ? `<button class="ghost-button" type="button" data-action="retry-timeline-ai">Reintentar</button>` : ""}
      </div>`;
    refreshIcons();
    highlightTimelineSearchMatches();
    return;
  }
  const entries = allEntries
    .filter((entry) => [entry.date, entry.title, entry.body].some(hasText))
    .filter((entry) => {
      const query = normalizeSearchText(timelineSearchQuery);
      if (!query) return true;
      const searchable = normalizeSearchText([entry.date, entry.kind, entry.title, entry.body, entry.phase, entry.clinicalStatus].filter(Boolean).join(" "));
      return searchable.includes(query);
    })
    .filter((entry) => !timelineFilters.size || timelineFilters.has(getTimelineFilterKey(entry)))
    .filter((entry) => !timelineMilestonesOnly || entry.milestone)
    .sort((a, b) => {
      if (!a.date) return 1;
      if (!b.date) return -1;
      return a.date.localeCompare(b.date);
    });

  const years = entries.reduce((result, entry) => {
    const year = entry.date ? entry.date.slice(0, 4) : "Sin fecha";
    if (!result.has(year)) result.set(year, []);
    result.get(year).push(entry);
    return result;
  }, new Map());

  timeline.innerHTML = `
    ${usingLocalTimeline ? `
      <div class="timeline-local-status${timelineAiError ? " is-warning" : ""}" role="status">
        <i data-lucide="${timelineAiLoading ? "loader-circle" : timelineAiError ? "triangle-alert" : "database"}"></i>
        <div>
          <strong>${timelineAiLoading ? "Actualizando el analisis" : timelineAiError ? "Cronologia local disponible" : "Cronologia local"}</strong>
          <span>${timelineAiLoading ? "Puede consultar los registros mientras se completa el analisis automatico." : timelineAiError ? `El analisis automatico no pudo actualizarse: ${escapeHtml(timelineAiError)}` : "Los registros clinicos se muestran directamente desde la historia."}</span>
        </div>
        ${timelineAiError ? `<button class="ghost-button" type="button" data-action="retry-timeline-ai"><i data-lucide="refresh-cw"></i><span>Reintentar</span></button>` : ""}
      </div>` : ""}
    <div class="right-timeline-toolbar">
      <div class="right-timeline-toolbar-head">
        <div><strong>Recorrido clinico</strong><span>Un clic ubica · + / − despliega o pliega</span></div>
        <div class="right-timeline-toolbar-actions">
          <button class="ghost-button timeline-reset-button" type="button" data-action="reset-timeline" title="Restablecer vista" aria-label="Restablecer vista">
            <i data-lucide="list-restart"></i>
          </button>
          <button class="ghost-button" type="button" data-action="expand-timeline" data-expanded="false">
            <i data-lucide="unfold-vertical"></i><span>Desplegar todo</span>
          </button>
        </div>
      </div>
      <div class="right-timeline-controls">
      <label class="timeline-search">
        <i data-lucide="search"></i>
        <input type="search" data-timeline-search value="${escapeAttr(timelineSearchQuery)}" placeholder="Buscar PSA, CEA, CA 125, farmaco..." autocomplete="off">
        ${timelineSearchQuery ? `<span>${entries.length} resultados</span>` : ""}
      </label>
      <button class="timeline-filter ${timelineMilestonesOnly ? "active" : ""}" type="button" data-action="toggle-milestones"><i data-lucide="sparkles"></i>Solo hitos</button>
      ${renderTimelineFilters(allEntries)}
      </div>
    </div>
    ${!entries.length ? `<div class="right-empty-state timeline-search-empty"><i data-lucide="search-x"></i><strong>Sin coincidencias</strong><span>Pruebe con otro marcador, tratamiento o hallazgo.</span></div>` : ""}
  ` + Array.from(years.entries()).map(([year, yearEntries]) => {
    const yearStart = yearEntries.find((entry) => entry.date)?.date || "";
    const yearEnd = [...yearEntries].reverse().find((entry) => entry.date)?.date || yearStart;
    const yearInitiallyOpen = years.size === 1;
    const yearFlowId = `timeline-year-${year}-flow`;
    const months = yearEntries.reduce((result, entry) => {
      const month = entry.date ? entry.date.slice(0, 7) : "sin-fecha";
      if (!result.has(month)) result.set(month, []);
      result.get(month).push(entry);
      return result;
    }, new Map());

    return `
    <details class="right-timeline-year" data-period-label="a&ntilde;o ${escapeAttr(year)}" ${yearInitiallyOpen ? "open" : ""}>
      <summary class="right-timeline-year-head" data-period-key="year-${escapeAttr(year)}" data-start="${escapeAttr(yearStart)}" data-end="${escapeAttr(yearEnd)}" aria-label="Seleccionar a&ntilde;o ${escapeAttr(year)}">
        <div>
          <span>Año</span>
          <strong>${escapeHtml(year)}</strong>
        </div>
        ${renderTimelineYearCategories(yearEntries)}
        <button class="timeline-period-toggle" type="button" data-action="toggle-timeline-period" aria-controls="${escapeAttr(yearFlowId)}" aria-expanded="${String(yearInitiallyOpen)}" aria-label="${yearInitiallyOpen ? "Plegar" : "Desplegar"} a&ntilde;o ${escapeAttr(year)}" title="${yearInitiallyOpen ? "Plegar" : "Desplegar"} a&ntilde;o ${escapeAttr(year)}">
          <span class="timeline-toggle-plus" aria-hidden="true">+</span><span class="timeline-toggle-minus" aria-hidden="true">−</span>
        </button>
      </summary>
      <div class="right-timeline-year-flow" id="${escapeAttr(yearFlowId)}">
      ${Array.from(months.entries()).map(([month, monthEntries]) => {
        const monthDays = monthEntries.reduce((result, entry) => {
          const date = entry.date || "sin-fecha";
          if (!result.has(date)) result.set(date, []);
          result.get(date).push(entry);
          return result;
        }, new Map());
        const monthStart = monthEntries.find((entry) => entry.date)?.date || "";
        const monthEnd = [...monthEntries].reverse().find((entry) => entry.date)?.date || monthStart;
        const monthLabel = formatTimelineMonth(month);
        const monthFlowId = `timeline-month-${month}-flow`;
        return `
        <details class="right-timeline-month" data-period-label="${escapeAttr(monthLabel)}">
          <summary class="right-timeline-month-head" data-period-key="month-${escapeAttr(month)}" data-start="${escapeAttr(monthStart)}" data-end="${escapeAttr(monthEnd)}" aria-label="Seleccionar ${escapeAttr(monthLabel)}">
            <div><strong>${escapeHtml(monthLabel)}</strong><span>${monthEntries.length} eventos</span></div>
            ${renderTimelineSummary(monthEntries, `month-${month}`)}
            <button class="timeline-period-toggle" type="button" data-action="toggle-timeline-period" aria-controls="${escapeAttr(monthFlowId)}" aria-expanded="false" aria-label="Desplegar ${escapeAttr(monthLabel)}" title="Desplegar ${escapeAttr(monthLabel)}">
              <span class="timeline-toggle-plus" aria-hidden="true">+</span><span class="timeline-toggle-minus" aria-hidden="true">−</span>
            </button>
          </summary>
          <div class="right-timeline-month-flow" id="${escapeAttr(monthFlowId)}">
      ${Array.from(monthDays.entries()).map(([date, dayEntries]) => `
        <section class="right-timeline-day" data-clinical-date="${escapeAttr(date === "sin-fecha" ? "" : date)}">
          <div class="right-timeline-date">
            <i data-lucide="calendar-days"></i>
            <span>${escapeHtml(date === "sin-fecha" ? "Sin fecha" : formatTimelineDateByPrecision(date, dayEntries[0]?.datePrecision))}</span>
          </div>
          ${dayEntries.map((entry) => `
            <article class="right-timeline-item right-timeline-item--${escapeAttr(entry.tone)}${entry.highlighted ? " is-highlighted" : ""}" data-clinical-date="${escapeAttr(entry.date || "")}" data-timeline-event-id="${escapeAttr(entry.id || "")}" data-source-record-type="${escapeAttr(entry.sourceRecordType || "")}" data-source-record-id="${escapeAttr(entry.sourceRecordId || "")}" role="button" tabindex="0" aria-label="Ubicar en la historia: ${escapeAttr(entry.title)}">
              <div class="right-timeline-marker" aria-hidden="true">
                <i data-lucide="${escapeAttr(entry.icon)}"></i>
              </div>
              <div class="right-timeline-card">
                <div class="right-timeline-head">
                  <div>
                    <strong>${escapeHtml(entry.title)}</strong>
                    <span>${escapeHtml(entry.kind)}</span>
                  </div>
                  ${entry.highlighted ? `<i data-lucide="star" aria-label="Destacado"></i>` : `<i data-lucide="more-horizontal" aria-hidden="true"></i>`}
                </div>
                <div class="right-timeline-context"><span>${escapeHtml(entry.phase)}</span>${entry.clinicalStatus ? `<span>${escapeHtml(entry.clinicalStatus)}</span>` : ""}${entry.milestone ? `<span>Hito</span>` : ""}${dayEntries.length > 1 ? `<span>${dayEntries.length} eventos relacionados</span>` : ""}</div>
                ${hasText(entry.body) ? `<p>${escapeHtml(entry.body)}</p>` : ""}
              </div>
            </article>
          `).join("")}
        </section>
      `).join("")}
          </div>
        </details>`;
      }).join("")}
      </div>
    </details>
  `;
  }).join("");
  applyTimelinePeriodSelection();
  refreshIcons();
  highlightTimelineSearchMatches();
  renderRememberedAgentHighlights([timeline]);
}

function toggleEvolutionMilestone(id) {
  const entry = state.evolutions.find((item) => item.id === id);
  if (!entry) return;
  if (entry.immutable) return;
  entry.highlighted = !Boolean(entry.highlighted || entry.featured || entry.destacada);
  entry.updatedAt = new Date().toISOString();
  state.meta.updatedAt = entry.updatedAt;
  renderPreview();
  renderPatientOutputs();
  persistStateSilently();
  toast(entry.highlighted ? "Evolucion marcada como hito" : "Hito eliminado");
}

function toggleSectionMilestone(sectionKey) {
  if (!sectionKey) return;
  state.meta.sectionMilestones ||= {};
  state.meta.sectionMilestones[sectionKey] = !Boolean(state.meta.sectionMilestones[sectionKey]);
  state.meta.updatedAt = new Date().toISOString();
  renderPreview();
  renderPatientOutputs();
  persistStateSilently();
  toast(state.meta.sectionMilestones[sectionKey] ? "Seccion marcada como importante" : "Marca importante eliminada");
}

function toggleClinicalSearch(button) {
  const shell = button.closest("[data-clinical-search-shell]");
  if (!shell) return;
  const opening = !shell.classList.contains("open");
  shell.classList.toggle("open", opening);
  button.setAttribute("aria-expanded", String(opening));
  if (opening) {
    const input = $("[data-clinical-search]", shell);
    input.value = timelineSearchQuery;
    input.focus();
    input.setSelectionRange(input.value.length, input.value.length);
  }
}

function syncClinicalSearchInput() {
  const shell = $("[data-clinical-search-shell]");
  const input = $("[data-clinical-search]", shell || document);
  if (input && input.value !== timelineSearchQuery) input.value = timelineSearchQuery;
  shell?.classList.toggle("has-query", Boolean(timelineSearchQuery.trim()));
}

function highlightTimelineSearchMatches({ scrollToFirst = false } = {}) {
  const markedParents = new Set();
  $$("mark.timeline-search-mark", document).forEach((mark) => {
    if (mark.parentNode) markedParents.add(mark.parentNode);
    mark.replaceWith(document.createTextNode(mark.textContent));
  });
  markedParents.forEach((parent) => parent.normalize());
  const terms = [normalizeSearchText(timelineSearchQuery).trim()].filter((term) => term.length > 1);
  if (!terms.length) return;
  let firstClinicalMark = null;
  let firstTimelineMark = null;
  [$("#rightTimeline"), $("#clinicalDocument")].filter(Boolean).forEach((root) => {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodes = [];
    while (walker.nextNode()) {
      const node = walker.currentNode;
      if (node.parentElement?.closest("input, button, svg, style, script, mark.timeline-search-mark, mark.agent-highlight")) continue;
      const normalized = normalizeSearchText(node.nodeValue);
      if (terms.some((term) => normalized.includes(term))) nodes.push(node);
    }
    nodes.forEach((node) => {
      const original = node.nodeValue;
      const normalized = normalizeSearchText(original);
      const ranges = [];
      terms.forEach((term) => {
        let start = 0;
        while ((start = normalized.indexOf(term, start)) !== -1) {
          ranges.push([start, start + term.length]);
          start += term.length;
        }
      });
      ranges.sort((a, b) => a[0] - b[0]);
      const merged = ranges.reduce((result, range) => {
        const previous = result[result.length - 1];
        if (previous && range[0] <= previous[1]) previous[1] = Math.max(previous[1], range[1]);
        else result.push([...range]);
        return result;
      }, []);
      const fragment = document.createDocumentFragment();
      let cursor = 0;
      merged.forEach(([start, end]) => {
        if (start > cursor) fragment.appendChild(document.createTextNode(original.slice(cursor, start)));
        const mark = document.createElement("mark");
        mark.className = "timeline-search-mark";
        mark.textContent = original.slice(start, end);
        fragment.appendChild(mark);
        if (root.id === "clinicalDocument") firstClinicalMark ||= mark;
        else firstTimelineMark ||= mark;
        cursor = end;
      });
      if (cursor < original.length) fragment.appendChild(document.createTextNode(original.slice(cursor)));
      node.replaceWith(fragment);
    });
  });
  if (scrollToFirst) {
    const target = firstClinicalMark || firstTimelineMark;
    target?.scrollIntoView({ behavior: "auto", block: "center" });
    target?.classList.add("timeline-search-mark--focus");
    window.setTimeout(() => target?.classList.remove("timeline-search-mark--focus"), 1800);
  }
}

function formatTimelineMonth(month) {
  if (month === "sin-fecha") return "Sin fecha";
  const [year, monthNumber] = month.split("-").map(Number);
  return new Intl.DateTimeFormat("es-AR", { month: "long", year: "numeric" }).format(new Date(year, monthNumber - 1, 1));
}

function getTimelineTreatmentSourceCategory(item) {
  const kind = getTreatmentKind(item);
  if (kind !== "systemic") return kind;
  const text = normalizeSearchText([item.scheme, item.intent, item.status, item.notes].filter(Boolean).join(" "));
  if (/\b(quimio|chemotherapy|citotox)/.test(text)) return "chemotherapy";
  if (/\b(hormono|hormonal|antiandrogen|tamoxif|anastrozol|letrozol)/.test(text)) return "hormone";
  if (/\b(inmuno|immuno|pembrol|nivol|atezol)/.test(text)) return "immunotherapy";
  if (/\b(dirigida|targeted|trastuz|ritux|inhibidor)/.test(text)) return "targeted";
  return "systemic";
}

function getTimelineClinicalSources(clinicalState = state) {
  const sources = [];
  const add = (recordType, item, categories, text) => {
    if (!item?.id) return;
    sources.push({
      recordType,
      recordId: String(item.id),
      date: item.date || String(item.createdAt || "").slice(0, 10),
      categories,
      highlighted: Boolean(item.highlighted || item.featured || item.destacada),
      text: normalizeSearchText(text)
    });
  };
  normalizeDiagnosisRecords(
    clinicalState.oncology?.diagnosisRecords,
    clinicalState.oncology || {}
  ).forEach((item) => add(
    "diagnosis",
    item,
    ["diagnosis"],
    [item.date, diagnosisRecordPlainText(item)].filter(Boolean).join(" ")
  ));
  (clinicalState.evolutions || []).filter((item) => !item.deleted).forEach((item) => add("evolution", item, ["evolution"], [item.date, item.reason, item.specialty, item.text].filter(Boolean).join(" ")));
  (clinicalState.studies || []).forEach((item) => {
    const text = [item.date, item.type, item.title, item.summary, item.source].filter(Boolean).join(" ");
    const category = /patolog|biops|histolog|citolog|inmunohisto/.test(normalizeSearchText(text)) ? "pathology" : "study";
    add("study", item, [category, "study"], text);
  });
  (clinicalState.treatments || []).forEach((item) => add("treatment", item, [getTimelineTreatmentSourceCategory(item)], [item.date, item.scheme, item.intent, item.status, item.notes].filter(Boolean).join(" ")));
  (clinicalState.prescriptions || []).forEach((item) => {
    const category = item.type === "certificate" ? "certificate" : item.type === "study" ? "study_order" : item.type === "free" ? "indication" : "prescription";
    add("prescription", item, [category], [item.date, item.title, ...getPrescriptionDetailLines(item)].filter(Boolean).join(" "));
  });
  (clinicalState.researchRecords || []).forEach((item) => add("research", item, ["research"], [item.date, item.type, item.protocol?.code, item.protocol?.name, JSON.stringify(item)].filter(Boolean).join(" ")));
  return sources;
}

function timelineTokenSet(value) {
  return new Set(normalizeSearchText(value).split(/\W+/).filter((token) => token.length > 3));
}

function timelineTokenOverlap(tokens, text) {
  if (!tokens.size) return 0;
  return Array.from(tokens).filter((token) => text.includes(token)).length / tokens.size;
}

function linkTimelineEventsToClinicalRecords(events, clinicalState = state) {
  if (!Array.isArray(events)) return [];
  const sources = getTimelineClinicalSources(clinicalState);
  const sourceById = new Map(sources.map((source) => [`${source.recordType}:${source.recordId}`, source]));
  const linked = events.map((event) => {
    let source = event.sourceRecordId ? sourceById.get(`${event.sourceRecordType}:${event.sourceRecordId}`) : null;
    if (!source) {
      const quote = normalizeSearchText(event.sourceQuote || "");
      const quoteTokens = timelineTokenSet(quote);
      const eventTokens = timelineTokenSet([event.title, event.body].filter(Boolean).join(" "));
      source = sources.map((candidate) => {
        let score = 0;
        if (candidate.categories.includes(event.category)) score += 5;
        else if (candidate.recordType === "treatment" && !["study", "pathology", "evolution", "research", "prescription", "certificate", "study_order", "indication"].includes(event.category)) score += 1;
        else score -= 3;
        if (event.date && candidate.date === event.date) score += 4;
        if (quote.length > 8 && candidate.text.includes(quote)) score += 14;
        else score += timelineTokenOverlap(quoteTokens, candidate.text) * 9;
        score += timelineTokenOverlap(eventTokens, candidate.text) * 5;
        return { candidate, score };
      }).sort((a, b) => b.score - a.score)[0];
      source = source?.score >= 6 ? source.candidate : null;
    }
    return {
      ...event,
      sourceRecordType: source?.recordType || event.sourceRecordType || "",
      sourceRecordId: source?.recordId || event.sourceRecordId || "",
      highlighted: Boolean(event.highlighted || source?.highlighted)
    };
  });

  const sectionHighlights = (clinicalState.meta?.clinicalHighlights || []).filter((highlight) => highlight.kind === "section" && !highlight.removedAt);
  linked.forEach((event) => {
    const eventText = normalizeSearchText([event.title, event.body, event.sourceQuote].filter(Boolean).join(" "));
    if (sectionHighlights.some((highlight) => {
      const exact = normalizeSearchText(highlight.exact);
      const tokens = timelineTokenSet(exact);
      return (exact.length > 8 && eventText.includes(exact)) || timelineTokenOverlap(tokens, eventText) >= 0.65;
    })) event.highlighted = true;
  });
  return linked;
}

function getAiTimelineEntries() {
  const events = state?.meta?.aiTimelineEvents;
  if (!Array.isArray(events) || !events.length) return [];
  return mapTimelineEventsToEntries(events);
}

function getTimelineEntryDefinition(category) {
  const definitions = {
    diagnosis: { kind: "Diagnóstico", tone: "diagnosis", icon: "stethoscope" },
    study: { kind: "Estudio", tone: "study", icon: "scan-line" },
    pathology: { kind: "Patologia", tone: "pathology", icon: "microscope" },
    evolution: { kind: "Evolucion", tone: "evolution", icon: "stethoscope" },
    research: { kind: "Investigacion", tone: "research", icon: "microscope" },
    prescription: { kind: "Receta", tone: "prescription", icon: "receipt-text" },
    certificate: { kind: "Certificado", tone: "certificate", icon: "file-badge" },
    study_order: { kind: "Solicitud de estudio", tone: "study-order", icon: "clipboard-plus" },
    indication: { kind: "Indicacion", tone: "indication", icon: "notebook-pen" },
    radiotherapy: { kind: "radioterapia", tone: "treatment-radiotherapy", icon: "radiation" },
    surgery: { kind: "cirugia", tone: "treatment-surgery", icon: "cross" },
    chemotherapy: { kind: "quimioterapia", tone: "treatment-chemotherapy", icon: "flask-conical" },
    hormone: { kind: "hormonoterapia", tone: "treatment-hormone", icon: "pill" },
    immunotherapy: { kind: "inmunoterapia", tone: "treatment-immunotherapy", icon: "shield-plus" },
    targeted: { kind: "terapia dirigida", tone: "treatment-targeted", icon: "target" },
    systemic: { kind: "tratamiento sistemico", tone: "treatment-systemic", icon: "heart-pulse" }
  };
  return definitions[category] || definitions.evolution;
}

function mapTimelineEventsToEntries(events) {
  return events.map((event) => {
    const definition = getTimelineEntryDefinition(event.category);
    const treatmentCategory = event.category && !["diagnosis", "study", "pathology", "evolution", "research", "prescription", "certificate", "study_order", "indication"].includes(event.category)
      ? { key: event.category, label: definition.kind, plural: `${definition.kind}s` }
      : null;
    return {
      id: event.id,
      date: event.date || "",
      datePrecision: ["day", "month", "year"].includes(event.datePrecision) ? event.datePrecision : "day",
      kind: definition.kind,
      icon: definition.icon,
      tone: definition.tone,
      treatmentCategory,
      highlighted: Boolean(event.highlighted),
      title: event.title || "Evento clinico",
      body: event.body || "",
      phase: event.phase || "Seguimiento",
      clinicalStatus: event.clinicalStatus || "",
      milestone: Boolean(event.highlighted),
      sourceQuote: event.sourceQuote || "",
      sourceRecordType: event.sourceRecordType || "",
      sourceRecordId: event.sourceRecordId || ""
    };
  });
}

function getDeterministicTimelineEntries(clinicalState = state) {
  if (!clinicalState) return [];
  const events = [];
  const add = (recordType, item, category, title, body, phase = "Seguimiento") => {
    if (!item || item.deleted) return;
    const date = String(item.date || item.startDate || item.createdAt || "").slice(0, 10);
    if (![date, title, body].some(hasText)) return;
    events.push({
      id: `local-${recordType}-${item.id || events.length + 1}`,
      date,
      datePrecision: ["day", "month", "year"].includes(item.datePrecision) ? item.datePrecision : "day",
      category,
      title: String(title || "Evento clinico"),
      body: String(body || ""),
      phase,
      clinicalStatus: String(item.clinicalStatus || ""),
      highlighted: Boolean(item.highlighted || item.featured || item.destacada),
      sourceRecordType: recordType,
      sourceRecordId: String(item.id || "")
    });
  };

  normalizeDiagnosisRecords(
    clinicalState.oncology?.diagnosisRecords,
    clinicalState.oncology || {}
  ).forEach((item) => add(
    "diagnosis",
    item,
    "diagnosis",
    item.diagnosis || "Diagnóstico oncológico",
    diagnosisRecordPlainText(item)
  ));
  (clinicalState.evolutions || []).forEach((item) => add(
    "evolution",
    item,
    "evolution",
    item.reason || item.specialty || "Evolucion clinica",
    item.text || item.summary || ""
  ));
  (clinicalState.studies || []).forEach((item) => {
    const text = [item.type, item.title, item.summary].filter(Boolean).join(" ");
    const category = /patolog|biops|histolog|citolog|inmunohisto/.test(normalizeSearchText(text)) ? "pathology" : "study";
    add("study", item, category, item.title || item.type || "Estudio", item.summary || item.source || "");
  });
  (clinicalState.treatments || []).forEach((item) => {
    const category = getTimelineTreatmentSourceCategory(item);
    add(
      "treatment",
      item,
      category,
      item.scheme || item.type || "Tratamiento oncologico",
      [item.intent, item.status, item.notes].filter(Boolean).join(" · "),
      "Tratamiento activo"
    );
  });
  (clinicalState.prescriptions || []).forEach((item) => {
    const category = item.type === "certificate" ? "certificate" : item.type === "study" ? "study_order" : item.type === "free" ? "indication" : "prescription";
    add("prescription", item, category, item.title || getTimelineEntryDefinition(category).kind, getPrescriptionDetailLines(item).join(" · "));
  });
  (clinicalState.researchRecords || []).forEach((item) => add(
    "research",
    item,
    "research",
    item.title || item.protocol?.name || item.protocol?.code || item.type || "Investigacion clinica",
    [item.summary, item.notes, item.status, item.visit].filter(Boolean).join(" · ")
  ));
  return mapTimelineEventsToEntries(events);
}

function formatTimelineDateByPrecision(value, precision = "day") {
  const date = String(value || "");
  if (precision === "year") return date.slice(0, 4) || "Sin fecha";
  if (precision === "month") return formatTimelineMonth(date.slice(0, 7));
  return formatDate(date);
}

function isTimelineAiCurrent() {
  return state?.meta?.aiTimelineSourceHash === hashTimelineSource(getClinicalDocumentForLlm())
    && state?.meta?.aiTimelineExtractorVersion === "timeline-v4";
}

function hashTimelineSource(text) {
  let hash = 2166136261;
  const value = String(text || "");
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(16);
}

function invalidateAiTimeline() {
  timelineGenerationVersion += 1;
  timelineAiAttempted = false;
  timelineAiLoading = false;
  timelineAiError = "";
}

async function generateTimelineWithLlm() {
  if (timelineAiLoading || !state) return;
  const contextVersion = clinicalContextVersion;
  const generationVersion = timelineGenerationVersion;
  timelineAiAttempted = true;
  timelineAiError = "";
  const text = getClinicalDocumentForLlm();
  if (!hasText(text)) {
    timelineAiError = "No hay texto clinico para analizar.";
    renderRightTimeline();
    return;
  }
  timelineAiLoading = true;
  renderRightTimeline();
  try {
    const response = await fetch("/api/llm/extract-timeline", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text })
    });
    const payload = await response.json().catch(() => ({}));
    if (contextVersion !== clinicalContextVersion ||
        generationVersion !== timelineGenerationVersion) return;
    if (!response.ok || !Array.isArray(payload.events)) throw new Error(payload.error || "No se pudo generar la linea de tiempo");
    state.meta.aiTimelineEvents = linkTimelineEventsToClinicalRecords(payload.events);
    state.meta.aiTimelineModel = payload.model || "";
    state.meta.aiTimelineGeneratedAt = new Date().toISOString();
    state.meta.aiTimelineWarnings = payload.warnings || [];
    state.meta.aiTimelineExtractorVersion = payload.extractorVersion || "";
    state.meta.aiTimelineSourceHash = hashTimelineSource(text);
    queueLocalSave();
    persistClinicalState({ silent: true });
    toast(`Linea de tiempo IA: ${payload.events.length} eventos`);
  } catch (error) {
    if (contextVersion !== clinicalContextVersion ||
        generationVersion !== timelineGenerationVersion) return;
    timelineAiError = error.message || "Error al analizar la historia";
    toast(timelineAiError);
  } finally {
    if (contextVersion === clinicalContextVersion &&
        generationVersion === timelineGenerationVersion) {
      timelineAiLoading = false;
      renderRightTimeline();
    }
  }
}

async function sendAgentMessage() {
  if (agentBusy || !state) return;
  const contextVersion = clinicalContextVersion;
  const input = $("#agentChatInput");
  const message = input.value.trim();
  if (!message) return;
  input.value = "";
  appendAgentMessage("user", { answer: message });
  agentConversation.push({ role: "user", content: message });
  const safeMessage = redactDirectPatientIdentifiers(message);
  const safeHistory = agentConversation.slice(-12).map((item) => ({
    ...item,
    content: redactDirectPatientIdentifiers(item.content)
  }));
  setAgentBusy(true);
  const typing = appendAgentTyping();
  try {
    const response = await fetch("/api/agent/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        message: safeMessage,
        clinicalText: getClinicalDocumentForLlm(),
        timelineEvents: getTimelineEventsForLlm(),
        history: safeHistory,
        consultAgents: false
      })
    });
    const payload = await response.json().catch(() => ({}));
    if (contextVersion !== clinicalContextVersion) {
      typing.remove();
      return;
    }
    if (!response.ok || !payload.answer) throw new Error(payload.error || "No se pudo obtener respuesta del agente");
    typing.remove();
    appendAgentMessage("assistant", payload);
    agentConversation.push({ role: "assistant", content: payload.answer });
    agentConversation = agentConversation.slice(-20);
    if (payload.highlights?.length) applyAgentHighlights(payload.highlights);
  } catch (error) {
    if (contextVersion !== clinicalContextVersion) return;
    typing.remove();
    appendAgentMessage("assistant", { answer: `No pude completar la consulta: ${error.message}` }, { error: true });
  } finally {
    if (contextVersion === clinicalContextVersion) setAgentBusy(false);
  }
}

function setAgentBusy(busy) {
  agentBusy = busy;
  const send = $("#agentSendBtn");
  const input = $("#agentChatInput");
  if (send) send.disabled = busy;
  if (input) input.disabled = busy;
}

function clearAgentChat() {
  agentConversation = [];
  lastAgentHighlights = [];
  clearAgentHighlights();
  $("#agentChatMessages").innerHTML = `
    <article class="agent-message agent-message--assistant">
      <div class="agent-avatar"><i data-lucide="bot"></i></div>
      <div class="agent-message-content"><p>Puedo analizar esta historia, crear resumenes, tablas, graficos y resaltar datos clinicos en ambos paneles.</p></div>
    </article>`;
  refreshIcons();
}

function appendAgentTyping() {
  const container = $("#agentChatMessages");
  const element = document.createElement("article");
  element.className = "agent-message agent-message--assistant";
  element.innerHTML = `<div class="agent-avatar"><i data-lucide="bot"></i></div><div class="agent-message-content"><div class="agent-typing"><span></span><span></span><span></span></div></div>`;
  container.appendChild(element);
  container.scrollTop = container.scrollHeight;
  refreshIcons();
  return element;
}

function appendAgentMessage(role, payload, options = {}) {
  const container = $("#agentChatMessages");
  const element = document.createElement("article");
  element.className = `agent-message agent-message--${role}${options.error ? " agent-message--error" : ""}`;
  const icon = role === "user" ? "user" : "bot";
  const paragraphs = renderAgentAnswer(payload.answer || "");
  const artifacts = (payload.artifacts || []).map(renderAgentArtifact).join("");
  const followUps = payload.followUps?.length ? `<div class="agent-followups">${payload.followUps.map((item) => `<button type="button" data-agent-prompt="${escapeAttr(item)}">${escapeHtml(item)}</button>`).join("")}</div>` : "";
  element.innerHTML = `<div class="agent-avatar"><i data-lucide="${icon}"></i></div><div class="agent-message-content">${paragraphs}${artifacts}${followUps}</div>`;
  container.appendChild(element);
  container.scrollTop = container.scrollHeight;
  refreshIcons();
  return element;
}

function renderAgentArtifact(artifact) {
  if (artifact.type === "table") {
    return `<section class="agent-artifact"><h4>${escapeHtml(artifact.title || "Tabla")}</h4><div class="agent-table-wrap"><table class="agent-table"><thead><tr>${(artifact.columns || []).map((column) => `<th>${escapeHtml(column)}</th>`).join("")}</tr></thead><tbody>${(artifact.rows || []).map((row) => { const rowText = row.join(" · "); const date = extractAgentNavigationDate(rowText); return `<tr tabindex="0" ${date ? `data-agent-nav-date="${escapeAttr(date)}"` : `data-agent-nav-text="${escapeAttr(rowText)}"`} title="Ir a la historia clinica">${row.map((cell) => `<td>${escapeHtml(cell)}</td>`).join("")}</tr>`; }).join("")}</tbody></table></div></section>`;
  }
  if (artifact.type === "chart") return renderAgentChart(artifact);
  return "";
}

function renderAgentAnswer(value) {
  return String(value || "").split(/\n{2,}/).filter(Boolean).map((block) => {
    const lines = block.split(/\n/).map((line) => line.trim()).filter(Boolean);
    if (lines.length && lines.every((line) => /^[-•*]\s+/.test(line))) {
      return `<ul class="agent-response-list">${lines.map((line) => { const text = line.replace(/^[-•*]\s+/, ""); const date = extractAgentNavigationDate(text); return `<li tabindex="0" ${date ? `data-agent-nav-date="${escapeAttr(date)}"` : `data-agent-nav-text="${escapeAttr(text)}"`}>${escapeHtml(text)}</li>`; }).join("")}</ul>`;
    }
    return `<p>${escapeHtml(block)}</p>`;
  }).join("");
}

function renderAgentChart(chart) {
  const series = (chart.series || []).filter((item) => item.points?.length);
  if (!series.length) return "";
  const width = 420;
  const height = 230;
  const plot = { left: 44, top: 18, width: 350, height: 165 };
  const allPoints = series.flatMap((item) => item.points);
  if (chart.chartType === "pie") return renderAgentPieChart(chart, series[0], width, height);
  const maxY = Math.max(...allPoints.map((point) => Number(point.y)), 1);
  const minY = Math.min(0, ...allPoints.map((point) => Number(point.y)));
  const spanY = Math.max(maxY - minY, 1);
  const labels = [...new Set(allPoints.map((point) => String(point.x)))];
  const xFor = (value, index, count) => {
    const numeric = Number(value);
    const numericValues = allPoints.map((point) => Number(point.x));
    if (numericValues.every(Number.isFinite) && Number.isFinite(numeric)) {
      const minX = Math.min(...numericValues);
      const maxX = Math.max(...numericValues);
      return plot.left + (numeric - minX) / Math.max(maxX - minX, 1) * plot.width;
    }
    const position = labels.indexOf(String(value));
    return plot.left + (position + .5) / Math.max(labels.length, count, 1) * plot.width;
  };
  const yFor = (value) => plot.top + plot.height - (Number(value) - minY) / spanY * plot.height;
  const grid = Array.from({ length: 5 }, (_, index) => {
    const value = minY + spanY * (4 - index) / 4;
    const y = plot.top + plot.height * index / 4;
    return `<line x1="${plot.left}" y1="${y}" x2="${plot.left + plot.width}" y2="${y}" stroke="#e7eaec"/><text x="${plot.left - 7}" y="${y + 3}" text-anchor="end" font-size="9" fill="#718096">${formatChartNumber(value)}</text>`;
  }).join("");
  let marks = "";
  if (chart.chartType === "bar") {
    const slot = plot.width / Math.max(labels.length, 1);
    const barWidth = Math.max(4, Math.min(24, slot / Math.max(series.length + 1, 2)));
    marks = series.map((serie, seriesIndex) => serie.points.map((point) => {
      const x = xFor(point.x, 0, labels.length) - (series.length * barWidth) / 2 + seriesIndex * barWidth;
      const y = yFor(point.y);
      const baseline = yFor(0);
      return `<rect x="${x}" y="${Math.min(y, baseline)}" width="${barWidth - 1}" height="${Math.max(Math.abs(baseline - y), 1)}" rx="2" fill="${escapeAttr(serie.color)}" data-chart-tooltip="${escapeAttr(point.label || `${point.x}: ${point.y}`)}" ${extractAgentNavigationDate(point.x) ? `data-agent-nav-date="${escapeAttr(extractAgentNavigationDate(point.x))}"` : `data-agent-nav-text="${escapeAttr(point.label || String(point.x))}"`}></rect>`;
    }).join("")).join("");
  } else {
    marks = series.map((serie) => {
      const points = serie.points.map((point, index) => `${xFor(point.x, index, serie.points.length)},${yFor(point.y)}`).join(" ");
      const line = chart.chartType === "line" ? `<polyline points="${points}" fill="none" stroke="${escapeAttr(serie.color)}" stroke-width="2"/>` : "";
      const dots = serie.points.map((point, index) => `<circle cx="${xFor(point.x, index, serie.points.length)}" cy="${yFor(point.y)}" r="4" fill="#fff" stroke="${escapeAttr(serie.color)}" stroke-width="2" data-chart-tooltip="${escapeAttr(point.label || `${point.x}: ${point.y}`)}" ${extractAgentNavigationDate(point.x) ? `data-agent-nav-date="${escapeAttr(extractAgentNavigationDate(point.x))}"` : `data-agent-nav-text="${escapeAttr(point.label || String(point.x))}"`}></circle>`).join("");
      return line + dots;
    }).join("");
  }
  const xLabels = labels.slice(0, 12).map((label, index) => `<text x="${plot.left + (index + .5) / Math.max(labels.length, 1) * plot.width}" y="${plot.top + plot.height + 17}" text-anchor="middle" font-size="8" fill="#718096">${escapeHtml(truncateText(label, 12))}</text>`).join("");
  return `<section class="agent-artifact"><h4>${escapeHtml(chart.title || "Grafico")}</h4><div class="agent-chart"><svg viewBox="0 0 ${width} ${height}" role="img" aria-label="${escapeAttr(chart.title || "Grafico")}">${grid}<line x1="${plot.left}" y1="${plot.top + plot.height}" x2="${plot.left + plot.width}" y2="${plot.top + plot.height}" stroke="#a7b1c2"/>${marks}${xLabels}<text x="${plot.left + plot.width / 2}" y="${height - 4}" text-anchor="middle" font-size="9" fill="#526577">${escapeHtml(chart.xLabel || "")}</text></svg><div class="agent-chart-legend">${series.map((item) => `<span style="--legend-color:${escapeAttr(item.color)}">${escapeHtml(item.name)}</span>`).join("")}</div></div></section>`;
}

function renderAgentPieChart(chart, serie, width, height) {
  const total = serie.points.reduce((sum, point) => sum + Math.max(Number(point.y), 0), 0) || 1;
  let angle = -Math.PI / 2;
  const colors = [serie.color, "#667fa2", "#d05d4a", "#bf842c", "#83649a", "#3287a8", "#4f8e84"];
  const paths = serie.points.map((point, index) => {
    const portion = Math.max(Number(point.y), 0) / total;
    const end = angle + portion * Math.PI * 2;
    const x1 = 120 + Math.cos(angle) * 72;
    const y1 = 100 + Math.sin(angle) * 72;
    const x2 = 120 + Math.cos(end) * 72;
    const y2 = 100 + Math.sin(end) * 72;
    const large = portion > .5 ? 1 : 0;
    const path = `<path d="M120 100 L${x1} ${y1} A72 72 0 ${large} 1 ${x2} ${y2} Z" fill="${colors[index % colors.length]}" data-chart-tooltip="${escapeAttr(point.label || `${point.x}: ${point.y}`)}" ${extractAgentNavigationDate(point.x) ? `data-agent-nav-date="${escapeAttr(extractAgentNavigationDate(point.x))}"` : `data-agent-nav-text="${escapeAttr(point.label || String(point.x))}"`}></path>`;
    angle = end;
    return path;
  }).join("");
  const legend = serie.points.map((point, index) => `<span style="--legend-color:${colors[index % colors.length]}">${escapeHtml(`${point.x}: ${point.y}`)}</span>`).join("");
  return `<section class="agent-artifact"><h4>${escapeHtml(chart.title || "Grafico")}</h4><div class="agent-chart"><svg viewBox="0 0 ${width} ${height}" role="img" aria-label="${escapeAttr(chart.title || "Grafico")}">${paths}</svg><div class="agent-chart-legend">${legend}</div></div></section>`;
}

function formatChartNumber(value) {
  return Number(value).toLocaleString("es-AR", { maximumFractionDigits: 2 });
}

function setPrescriptionType(type) {
  prescriptionType = ["medication", "certificate", "study", "free", "systemic"].includes(type) ? type : "medication";
  $("#prescriptionPatientCard")?.classList.toggle("is-hidden", prescriptionType === "systemic");
  $$('[data-prescription-type]').forEach((button) => {
    const active = button.dataset.prescriptionType === prescriptionType;
    button.classList.toggle("active", active);
    button.setAttribute("aria-selected", String(active));
  });
  $$('[data-prescription-pane]').forEach((pane) => {
    const active = pane.dataset.prescriptionPane === prescriptionType;
    pane.classList.toggle("active", active);
    pane.setAttribute("aria-hidden", String(!active));
    pane.querySelectorAll("input, textarea, select, button").forEach((control) => {
      control.disabled = !active;
    });
  });
  const submitLabel = $("#rxSubmitLabel");
  const submitButton = $("#rxSubmitBtn");
  if (submitLabel) submitLabel.textContent = prescriptionType === "systemic" ? "Completar con IA" : "Prescribir";
  if (submitButton) {
    const icon = $("i, svg", submitButton);
    if (icon) icon.setAttribute("data-lucide", prescriptionType === "systemic" ? "sparkles" : "file-check-2");
  }
  if (prescriptionType === "systemic") loadSystemicForms();
  refreshIcons();
}

async function loadSystemicForms() {
  if (systemicFormCatalog.length) {
    renderSystemicTemplateOptions();
    return;
  }
  setSystemicFormStatus("Cargando formularios locales...", "loading");
  try {
    const response = await fetch("/api/systemic-forms");
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || "No se pudo abrir el catálogo de formularios");
    systemicFormCatalog = Array.isArray(payload.forms) ? payload.forms : Array.isArray(payload) ? payload : [];
    if (!systemicFormCatalog.length) throw new Error("No hay formularios sistémicos disponibles");
    renderSystemicTemplateOptions();
    setSystemicFormStatus(`${systemicFormCatalog.length} formularios disponibles`, "ready");
  } catch (error) {
    setSystemicFormStatus(error.message || "No se pudieron cargar los formularios", "error");
  }
}

function renderSystemicTemplateOptions() {
  const select = $("#systemicTemplateSelect");
  if (!select || !systemicFormCatalog.length) return;
  const selected = select.value;
  select.innerHTML = systemicFormCatalog.map((template) => `<option value="${escapeAttr(template.id)}">${escapeHtml(template.title)}</option>`).join("");
  if (systemicFormCatalog.some((template) => template.id === selected)) select.value = selected;
  renderSystemicTemplateDescription();
}

function renderSystemicTemplateDescription() {
  const template = getSelectedSystemicTemplate();
  const panel = $("#systemicTemplateDescription");
  const thumbnails = $("#systemicTemplateThumbnails");
  if (!panel) return;
  if (!template) {
    panel.classList.add("is-empty");
    if (thumbnails) {
      thumbnails.hidden = true;
      thumbnails.innerHTML = "";
    }
    return;
  }
  panel.classList.remove("is-empty");
  if ($("#systemicTemplateTitle")) $("#systemicTemplateTitle").textContent = template.shortTitle || template.title;
  if ($("#systemicTemplateMeta")) $("#systemicTemplateMeta").textContent = `${template.pages.length} ${template.pages.length === 1 ? "página" : "páginas"} · formulario local`;
  if ($("#systemicTemplateSummary")) $("#systemicTemplateSummary").textContent = template.description || "";
  if (thumbnails) {
    thumbnails.innerHTML = template.pages.map((page, index) => `
      <figure class="systemic-template-thumbnail">
        <span class="systemic-template-thumbnail-page" style="aspect-ratio:${Math.max(1, Number(page.width || 1))} / ${Math.max(1, Number(page.height || 1))}">
          <img src="${escapeAttr(page.image)}" alt="${escapeAttr(`${template.shortTitle || template.title}, página ${index + 1}`)}" loading="lazy">
        </span>
        <figcaption>Página ${index + 1}</figcaption>
      </figure>`).join("");
    thumbnails.hidden = !template.pages.length;
  }
}

function getSelectedSystemicTemplate() {
  const id = $("#systemicTemplateSelect")?.value || "";
  return systemicFormCatalog.find((template) => template.id === id) || null;
}

function setSystemicFormStatus(message, kind = "") {
  const status = $("#systemicFormStatus");
  if (!status) return;
  status.textContent = message || "";
  status.hidden = !message;
  status.dataset.status = kind;
}

async function loadProtocols() {
  if (protocolData) return;
  const overview=$("#protocolOverview");
  if(overview)overview.innerHTML='<div class="protocol-loading"><i data-lucide="loader-circle"></i><span>Cargando protocolos locales...</span></div>';
  refreshIcons();
  try{
    const source=$("#protocolSource")?.value||"coir";
    const response=await fetch(`/api/protocols?source=${encodeURIComponent(source)}`); const payload=await response.json();
    if(!response.ok)throw new Error(payload.error||"No se pudieron cargar los protocolos");
    protocolData=payload;
    $("#protocolCount").textContent=`${payload.count} esquemas`;
    $(".protocol-head strong").textContent=source==="seer"?"Esquemas SEER*Rx":"Esquemas COIR";
    $(".protocol-head small").textContent=source==="seer"?"Referencia actualizada para codificacion oncologica":"Protocolos sistemicos, drogas y preparacion";
    $("#protocolCategory").innerHTML='<option value="">Todos los grupos</option>'+payload.categories.map((item)=>`<option value="${escapeAttr(item)}">${escapeHtml(item)}</option>`).join("");
    renderProtocolSchemeOptions();
    overview.innerHTML='<div class="protocol-empty"><i data-lucide="route"></i><strong>Seleccione un esquema</strong><span>Explore ciclo, drogas, dosis, vias, preparacion y presentaciones.</span></div>';
    refreshIcons();
  }catch(error){overview.innerHTML=`<div class="protocol-empty"><i data-lucide="triangle-alert"></i><strong>No se pudo cargar</strong><span>${escapeHtml(error.message)}</span></div>`;refreshIcons()}
}

function handleProtocolSourceChange(){const seer=$("#protocolSource").value==="seer";$("#coirProtocolSelectors").hidden=seer;$("#protocolOverview").hidden=seer;$("#protocolDrugDetail").hidden=seer;$("#seerExplorer").hidden=!seer;$("#protocolCount").textContent=seer?(seerTnmData?`${seerTnmData.count} sitios`:"Cargando..."):(protocolData?`${protocolData.count} esquemas`:"Cargando...");if(seer)loadSeerTnm();else loadProtocols()}

async function loadSeerTnm(){if(seerTnmData)return;try{const response=await fetch("/api/tnm");const payload=await response.json();if(!response.ok)throw new Error(payload.error||"No se pudo cargar TNM");seerTnmData=payload;$("#protocolCount").textContent=`${payload.count} sitios`;$("#seerSite").innerHTML='<option value="">Seleccione un sitio</option>'+payload.schemas.map((item)=>`<option value="${escapeAttr(item.id)}">${escapeHtml(item.name)} (${item.inputs} campos)</option>`).join("");$("#seerDetail").innerHTML=`<div class="protocol-empty"><i data-lucide="map-pinned"></i><strong>TNM offline ${escapeHtml(payload.version)}</strong><span>Seleccione un sitio para ver campos y agrupaciones de estadio.</span></div>`;refreshIcons()}catch(error){$("#seerDetail").innerHTML=`<div class="protocol-empty"><strong>No se pudo cargar TNM</strong><span>${escapeHtml(error.message)}</span></div>`}}

async function loadSeerTnmDetail(){const id=$("#seerSite").value;if(!id){$("#seerDetail").innerHTML="";return}$("#seerDetail").innerHTML='<div class="protocol-loading"><i data-lucide="loader-circle"></i></div>';refreshIcons();try{const response=await fetch(`/api/tnm/detail?id=${encodeURIComponent(id)}`);const payload=await response.json();if(!response.ok)throw new Error(payload.error||"Sitio no disponible");const schema=payload.schema;$("#seerDetail").innerHTML=`<article class="seer-schema-card"><span>SEER TNM ${escapeHtml(schema.version)} · offline</span><h3>${escapeHtml(schema.title||schema.name)}</h3><p>${escapeHtml(String(schema.notes||"").replace(/\*\*|\*/g,"").slice(0,1200))}</p></article><section class="seer-inputs"><h4>Datos requeridos para estadificar</h4>${schema.inputs.map((input)=>`<span>${escapeHtml(input.name)} <small>${escapeHtml(input.key)}</small></span>`).join("")}</section>${(payload.stageTables||[]).map(renderSeerStageTable).join("")||'<p class="protocol-muted">Este sitio no incluye una tabla directa de agrupacion visible.</p>'}`;refreshIcons()}catch(error){$("#seerDetail").innerHTML=`<div class="protocol-empty"><strong>Error</strong><span>${escapeHtml(error.message)}</span></div>`}}

function renderSeerStageTable(table){const headings=(table.definition||[]).map((item)=>item.name);return `<details class="seer-stage-table"><summary><div><strong>${escapeHtml(table.title||table.name||"Agrupacion de estadio")}</strong><span>${(table.rows||[]).length} reglas</span></div><i data-lucide="chevron-down"></i></summary><div class="seer-table-wrap"><table><thead><tr>${headings.map((item)=>`<th>${escapeHtml(item)}</th>`).join("")}</tr></thead><tbody>${(table.rows||[]).map((row)=>`<tr>${row.map((cell)=>`<td>${escapeHtml(cell)}</td>`).join("")}</tr>`).join("")}</tbody></table></div></details>`}

async function loadSystemConfig(){try{const [configResponse,statusResponse]=await Promise.all([fetch("/api/config"),fetch("/api/catalogs/status")]);const config=await configResponse.json(),status=await statusResponse.json();$("#configLlmEndpoint").value=config.llm?.baseUrl||"";$("#configLlmModel").value=config.llm?.model||"";$("#configLlmEnabled").checked=Boolean(config.llm?.enabled);$("#configLlmApiKey").placeholder=config.llm?.hasApiKey?"Configurada · dejar vacio para conservar":"API key";$("#configAjccEndpoint").value=config.ajcc?.endpoint||"https://ajcc.3scale.net";$("#configAjccApiKey").placeholder=config.ajcc?.hasApiKey?"Configurada · dejar vacio para conservar":"API key AJCC";renderCatalogStatus(status)}catch(error){toast("No se pudo cargar la configuracion")}}

async function saveSystemConfig(event){event.preventDefault();const body={llm:{enabled:$("#configLlmEnabled").checked,baseUrl:$("#configLlmEndpoint").value.trim(),model:$("#configLlmModel").value.trim(),apiKey:$("#configLlmApiKey").value.trim()},ajcc:{endpoint:$("#configAjccEndpoint").value.trim(),apiKey:$("#configAjccApiKey").value.trim()}};const response=await fetch("/api/config",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});if(response.ok){$("#configLlmApiKey").value="";$("#configAjccApiKey").value="";toast("Configuracion guardada")}else toast("No se pudo guardar")}

function renderCatalogStatus(status){$("#catalogStatus").innerHTML=`<span><b>${status.medications||0}</b> medicamentos</span><span><b>${status.protocols||0}</b> protocolos COIR</span><span><b>${status.tnm||0}</b> sitios TNM</span><small>${escapeHtml(status.tnmVersion||"")} · ${status.offline?"disponible offline":"requiere conexion"}</small>`}

async function updateCatalog(target,button){button.disabled=true;button.classList.add("loading");try{const response=await fetch("/api/catalogs/update",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({target})});const payload=await response.json();if(!response.ok)throw new Error(payload.error||"Error");renderCatalogStatus({...payload,tnmVersion:"2.1 / TNM 7",offline:true});if(target==="all"||target==="protocols")protocolData=null;if(target==="all"||target==="tnm")seerTnmData=null;toast(payload.message||"Catalogo actualizado")}catch(error){toast(error.message)}finally{button.disabled=false;button.classList.remove("loading")}}

function renderProtocolSchemeOptions(){
  if(!protocolData)return; const category=$("#protocolCategory").value;
  const schemes=protocolData.schemes.filter((item)=>!category||item.category===category);
  $("#protocolScheme").innerHTML='<option value="">Seleccione un esquema</option>'+schemes.map((item)=>`<option value="${escapeAttr(item.id)}">${escapeHtml(item.name)}${item.durationText?` · ${escapeHtml(item.durationText)}`:""}</option>`).join("");
  $("#protocolDrug").innerHTML='<option value="">Seleccione una droga</option>';$("#protocolDrug").disabled=true;protocolDetail=null;
  $("#protocolOverview").innerHTML=`<div class="protocol-empty"><i data-lucide="list-filter"></i><strong>${schemes.length} esquemas disponibles</strong><span>Seleccione uno para ver su composicion.</span></div>`;$("#protocolDrugDetail").innerHTML="";refreshIcons();
}

async function loadProtocolDetail(){
  const id=$("#protocolScheme").value;if(!id){renderProtocolSchemeOptions();return}
  $("#protocolOverview").innerHTML='<div class="protocol-loading"><i data-lucide="loader-circle"></i><span>Abriendo esquema...</span></div>';refreshIcons();
  try{const source=$("#protocolSource")?.value||"coir";const response=await fetch(`/api/protocols/detail?id=${encodeURIComponent(id)}&source=${encodeURIComponent(source)}`);const payload=await response.json();if(!response.ok)throw new Error(payload.error||"Esquema no disponible");protocolDetail=payload;
    const scheme=payload.scheme,drugs=payload.drugs||[];
    const catalogMessage=scheme.catalogOnly?"Registro de agenda COIR incorporado al catálogo. Todavía no tiene composición clínica vinculada; la duración sí puede utilizarse para planificar turnos.":"Referencia operativa local. Verifique indicación, dosis y preparación antes de prescribir.";
    $("#protocolOverview").innerHTML=`<article class="protocol-summary${scheme.catalogOnly?" protocol-summary--catalog-only":""}"><span>${escapeHtml(scheme.category)}</span><h3>${escapeHtml(scheme.name)}</h3><div class="protocol-summary-metrics"><span><b>${scheme.cycleDays||"—"}</b><small>días por ciclo</small></span><span><b>${drugs.length}</b><small>drogas</small></span><span><b>${escapeHtml(scheme.durationText||"—")}</b><small>duración operativa</small></span></div><p>${escapeHtml(catalogMessage)}</p></article>${drugs.length?`<section class="protocol-scheme-drugs"><h4>Drogas del esquema <small>Seleccione una para ver su preparación</small></h4><div class="protocol-drug-strip">${drugs.map((drug,index)=>`<button type="button" data-protocol-drug-index="${index}"><span>Día ${escapeHtml(drug.dia||"—")}</span><strong>${escapeHtml(drug.droga||"—")}</strong><small>${escapeHtml(drug.dosisDiaria||"—")} ${escapeHtml(drug.calculoDosis||"")} · ${escapeHtml(drug.viaAdministracion||"—")}</small><i data-lucide="chevron-right"></i></button>`).join("")}</div></section>`:""}`;
    $("#protocolDrug").disabled=!drugs.length;$("#protocolDrug").innerHTML='<option value="">Seleccione una droga</option>'+drugs.map((drug,index)=>`<option value="${index}">Día ${escapeHtml(drug.dia||"—")} · ${escapeHtml(drug.droga||"—")}</option>`).join("");$("#protocolDrugDetail").innerHTML="";
  }catch(error){$("#protocolOverview").innerHTML=`<div class="protocol-empty"><i data-lucide="triangle-alert"></i><strong>No se pudo abrir</strong><span>${escapeHtml(error.message)}</span></div>`}refreshIcons();
}

function renderProtocolDrugDetail(){
  const index=Number($("#protocolDrug").value);if(!protocolDetail||!Number.isInteger(index)||!protocolDetail.drugs[index]){$("#protocolDrugDetail").innerHTML="";return}
  const drug=protocolDetail.drugs[index];const applications=drug.applications||[],presentations=drug.presentations||[];
  $$('[data-protocol-drug-index]',$("#protocolOverview")).forEach((button)=>button.classList.toggle("active",Number(button.dataset.protocolDrugIndex)===index));
  $("#protocolDrugDetail").innerHTML=`<article class="protocol-drug-card"><header><div><span>Día ${escapeHtml(drug.dia||"—")}</span><h3>${escapeHtml(drug.droga||"—")}</h3></div><b>${escapeHtml(drug.dosisDiaria||"—")} ${escapeHtml(drug.calculoDosis||"")}</b></header><dl><div><dt>Vía</dt><dd>${escapeHtml(drug.viaAdministracion||"—")}</dd></div><div><dt>Administración</dt><dd>${escapeHtml(drug.tiempoAdministracion||"—")}</dd></div><div><dt>Hospital de día</dt><dd>${String(drug.seAplicaEnHdd)==="1"?"Sí":"No"}</dd></div></dl></article>
  <section class="protocol-subsection"><h4>Preparación y aplicación <span>${applications.length}</span></h4>${applications.length?applications.map((item)=>`<article><strong>${escapeHtml(item.presentaciones||drug.droga)}</strong><p>${escapeHtml([item.viaAdministracion,item.reconstituyente?`Reconst. ${item.reconstituyente}`:"",item.diluyente?`Diluyente ${item.diluyente}`:"",item.volumenFinal?`${item.volumenFinal} ml`:"",item.concentracion?`${item.concentracion} mg/ml`:""].filter(Boolean).join(" · "))}</p>${item.observacionesPreparacion||item.observacionesEtiqueta?`<small>${escapeHtml([item.observacionesPreparacion,item.observacionesEtiqueta].filter(Boolean).join(" · "))}</small>`:""}</article>`).join(""):'<p class="protocol-muted">Sin instrucciones registradas.</p>'}</section>
  <section class="protocol-subsection"><h4>Presentaciones <span>${presentations.length}</span></h4><div class="protocol-presentations">${presentations.length?presentations.map((item)=>`<span>${escapeHtml(item.cantidad||"—")} mg · ${item.liofilizado==="1"?"Liofilizado":item.solucion==="1"?"Solución":"Presentación"}${item.frascoAmpolla==="1"?" · frasco ampolla":""}</span>`).join(""):'<span>Sin presentaciones registradas</span>'}</div></section>`;refreshIcons();
}

async function searchMedicationCatalog(query) {
  const results = $("#rxDrugResults");
  if (!results || query.trim().length < 2) { if (results) results.hidden = true; return; }
  try {
    const response = await fetch(`/api/medications/search?q=${encodeURIComponent(query.trim())}`);
    const payload = await response.json();
    results.innerHTML = (payload.results || []).map((item, index) => `<button class="prescription-drug-result" type="button" data-drug-index="${index}" data-drug="${escapeAttr(JSON.stringify(item))}"><strong>${escapeHtml(item.generic)}${item.brand ? ` · ${escapeHtml(item.brand)}` : ""}</strong><span>${escapeHtml([item.presentation,item.form,item.laboratory].filter(Boolean).join(" · "))}</span></button>`).join("") || `<div class="prescription-drug-result"><span>Sin coincidencias locales. Consulte el Vademecum AR.</span></div>`;
    results.hidden = false;
  } catch { results.hidden = true; }
}

function handleMedicationResult(event) {
  const button = event.target.closest("[data-drug]"); if (!button) return;
  const item = JSON.parse(button.dataset.drug);
  $("#rxGeneric").value = item.generic || ""; $("#rxBrand").value = item.brand || ""; $("#rxPresentation").value = item.presentation || ""; $("#rxForm").value = item.form || "";
  $("#rxDrugSearch").value = [item.generic,item.presentation].filter(Boolean).join(" · "); $("#rxDrugResults").hidden = true;
}

function handleMedicationPreset(event) {
  const key = event.target.closest("[data-rx-preset]")?.dataset.rxPreset; if (!key) return;
  const presets = {
    ondansetron:{generic:"Ondansetron",presentation:"8 mg x 20 comprimidos",form:"Comprimidos",dose:"8 mg",route:"Oral",frequency:"Cada 8 horas si nauseas",duration:"5 dias",quantity:"1 envase",indication:"Prevencion y tratamiento de nauseas",instructions:"Usar segun necesidad. Revisar interacciones y contraindicaciones."},
    paracetamol:{generic:"Paracetamol",presentation:"500 mg x 20 comprimidos",form:"Comprimidos",dose:"500 mg",route:"Oral",frequency:"Cada 8 horas si dolor",duration:"5 dias",quantity:"1 envase",indication:"Analgesia",instructions:"No superar la dosis maxima diaria. Revisar funcion hepatica."},
    omeprazol:{generic:"Omeprazol",presentation:"20 mg x 30 capsulas",form:"Capsulas",dose:"20 mg",route:"Oral",frequency:"Una vez al dia",duration:"30 dias",quantity:"1 envase",indication:"Proteccion gastrica",instructions:"Administrar antes del desayuno."},
    dexametasona:{generic:"Dexametasona",presentation:"4 mg x 20 comprimidos",form:"Comprimidos",dose:"4 mg",route:"Oral",frequency:"Segun esquema",duration:"A definir",quantity:"1 envase",indication:"Soporte oncologico",instructions:"Requiere definir pauta y descenso segun indicacion clinica."}
  }; const item=presets[key]; if(!item)return;
  $("#rxGeneric").value=item.generic;$("#rxPresentation").value=item.presentation;$("#rxForm").value=item.form;$("#rxDose").value=item.dose;$("#rxRoute").value=item.route;$("#rxFrequency").value=item.frequency;$("#rxDuration").value=item.duration;$("#rxQuantity").value=item.quantity;$("#rxIndication").value=item.indication;$("#rxInstructions").value=item.instructions;
}

function fillCertificateTemplate() {
  const type=$("#rxCertificateType").value; const name=state.patient.fullName||"el/la paciente"; const templates={attendance:`Se deja constancia que ${name} fue atendido/a en la fecha por control medico.`,rest:`Se certifica que ${name} requiere reposo laboral por el periodo indicado.`,treatment:`Se certifica que ${name} se encuentra actualmente en tratamiento medico.`,transport:`Se certifica que ${name} requiere asistencia para traslado por razones de salud.`,custom:""}; $("#rxCertificateText").value=templates[type]||"";
}

function applyStudyPreset(key) {
  const presets={laboratory:["Laboratorio","Hemograma, funcion renal, hepatograma, ionograma y marcadores segun patologia","Control oncologico y evaluacion de toxicidad","Ayuno segun determinaciones"],ct:["Imagen","TC de torax, abdomen y pelvis con contraste","Evaluacion de respuesta / estadificacion oncologica","Informar creatinina y antecedentes de alergia al contraste"],mri:["Imagen","Resonancia magnetica de region a evaluar con contraste","Caracterizacion y extension de enfermedad","Verificar contraindicaciones y funcion renal"],pathology:["Anatomia patologica","Revision de anatomia patologica y material disponible","Confirmacion diagnostica, histologia y biomarcadores","Remitir tacos, preparados e informe previo"]}; const item=presets[key];if(!item)return;$("#rxStudyCategory").value=item[0];$("#rxStudyName").value=item[1];$("#rxStudyIndication").value=item[2];$("#rxStudyNotes").value=item[3];
}

async function addPrescriptionDraft(event) {
  event.preventDefault();
  if (prescriptionType === "systemic") {
    await prepareSystemicForm();
    return;
  }
  const data=collectPrescriptionData(); if(!data)return;
  const now=new Date().toISOString();
  const item={id:makeId("rx"),type:prescriptionType,date:today(),datePrecision:"day",createdAt:now,status:"registered",audit:buildAuditStamp("cargado",{at:now}),...data};
  state.prescriptions.unshift(item);
  state.meta.updatedAt=now; queueLocalSave(); renderPrescriptionDrafts(); renderPreview(); renderPatientOutputs(); clearPrescriptionEditor(); persistClinicalState(); toast("Documento registrado en la historia clinica");
  openPrescriptionPreview(item);
}

function collectPrescriptionData() {
  if(prescriptionType==="medication"){const generic=$("#rxGeneric").value.trim(),presentation=$("#rxPresentation").value.trim(),form=$("#rxForm").value.trim(),quantity=$("#rxQuantity").value.trim();if(!generic||!presentation||!form||!quantity){toast("Complete generico, presentacion, forma y cantidad");return null}return{title:generic,summary:[presentation,$("#rxDose").value,$("#rxFrequency").value].filter(Boolean).join(" · "),data:{generic,brand:$("#rxBrand").value.trim(),presentation,form,dose:$("#rxDose").value.trim(),route:$("#rxRoute").value,frequency:$("#rxFrequency").value.trim(),duration:$("#rxDuration").value.trim(),quantity,indication:$("#rxIndication").value.trim(),instructions:$("#rxInstructions").value.trim()}}}
  if(prescriptionType==="certificate"){const text=$("#rxCertificateText").value.trim();if(!text){toast("Complete el texto del certificado");return null}return{title:$("#rxCertificateType").selectedOptions[0].text,summary:text,data:{certificateType:$("#rxCertificateType").value,from:$("#rxCertificateFrom").value,to:$("#rxCertificateTo").value,text,includeDiagnosis:$("#rxCertificateDiagnosis").checked}}}
  if(prescriptionType==="study"){const name=$("#rxStudyName").value.trim(),indication=$("#rxStudyIndication").value.trim();if(!name||!indication){toast("Complete estudio e indicacion");return null}return{title:name,summary:indication,data:{category:$("#rxStudyCategory").value,priority:$("#rxStudyPriority").value,name,indication,notes:$("#rxStudyNotes").value.trim()}}}
  if (prescriptionType === "free") { const text=$("#rxFreeText").value.trim();if(!text){toast("Complete el texto libre");return null}return{title:$("#rxFreeTitle").value.trim()||"Indicaciones medicas",summary:text,data:{title:$("#rxFreeTitle").value.trim(),text}}; }
  return null;
}

async function prepareSystemicForm() {
  if (systemicFormBusy) return;
  if (!systemicFormCatalog.length) await loadSystemicForms();
  const template = getSelectedSystemicTemplate();
  if (!template) {
    toast("Seleccione un formulario sistémico");
    $("#systemicTemplateSelect")?.focus();
    return;
  }
  const requestContextVersion = clinicalContextVersion;
  const requestPatientKey = getSystemicPatientContextKey();
  const requestClinicalText = getClinicalDocumentForLlm();
  const requestId = ++systemicFormRequestId;

  setSystemicFormBusy(true);
  setSystemicFormStatus("Analizando la historia clínica y ajustando cada campo...", "loading");
  let generatedFields = {};
  let warnings = [];
  try {
    const response = await fetch("/api/llm/fill-systemic-form", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        templateId: template.id,
        clinicalText: requestClinicalText,
        notes: redactDirectPatientIdentifiers($("#systemicFormNotes")?.value.trim() || "")
      })
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || "No se pudo completar el formulario con IA");
    if (payload.templateId !== template.id) throw new Error("La respuesta no corresponde al formulario seleccionado");
    generatedFields = payload.fields && typeof payload.fields === "object" ? payload.fields : {};
    warnings = Array.isArray(payload.warnings) ? payload.warnings.map((item) => String(item || "").trim()).filter(Boolean) : [];
    setSystemicFormStatus("Formulario preparado. Revíselo antes de imprimir.", "ready");
  } catch (error) {
    warnings = ["La información que no pudo completarse quedó en blanco para carga manual."];
    setSystemicFormStatus("Se abrió el formulario editable con los datos locales disponibles.", "warning");
  } finally {
    if (requestId === systemicFormRequestId) setSystemicFormBusy(false);
  }

  if (requestId !== systemicFormRequestId || prescriptionType !== "systemic") return;
  if (requestContextVersion !== clinicalContextVersion
    || requestPatientKey !== getSystemicPatientContextKey()
    || requestClinicalText !== getClinicalDocumentForLlm()) {
    setSystemicFormStatus("La historia clínica cambió durante el análisis. Vuelva a completar el formulario.", "warning");
    toast("El paciente cambió; se descartó el formulario anterior");
    return;
  }

  const values = {};
  template.fields.forEach((field) => {
    const rawValue = field.source === "local"
      ? getSystemicLocalValue(field.localKey)
      : generatedFields[field.id];
    values[field.id] = fitSystemicFieldValue(rawValue, field);
  });
  normalizeSystemicGroupValues(template, values);
  openSystemicFormPreview(template, values, warnings);
}

function normalizeSystemicGroupValues(template, values) {
  const groups = new Map();
  template.fields.filter((field) => field.kind === "checkbox" && field.group).forEach((field) => {
    if (!groups.has(field.group)) groups.set(field.group, []);
    groups.get(field.group).push(field.id);
  });
  groups.forEach((ids) => {
    if (ids.filter((id) => values[id] === true).length <= 1) return;
    ids.forEach((id) => { values[id] = false; });
  });
}

function getSystemicPatientContextKey() {
  const patient = state?.patient || {};
  return [patient.liraId, patient.dni, patient.medicalRecord, patient.fullName].map((value) => String(value || "").trim()).join("|");
}

function setSystemicFormBusy(busy) {
  systemicFormBusy = Boolean(busy);
  const button = $("#rxSubmitBtn");
  if (!button) return;
  button.disabled = systemicFormBusy;
  button.classList.toggle("is-loading", systemicFormBusy);
  const label = $("#rxSubmitLabel");
  if (label) label.textContent = systemicFormBusy ? "Completando..." : "Completar con IA";
  const icon = $("i, svg", button);
  if (icon) icon.setAttribute("data-lucide", systemicFormBusy ? "loader-circle" : "sparkles");
  refreshIcons();
}

function fitSystemicFieldValue(value, field) {
  if (field.kind === "checkbox") return value === true || /^(?:1|true|sí|si|x)$/i.test(String(value || "").trim());
  let fitted = String(value ?? "")
    .replace(/\r\n?/g, "\n")
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, " ")
    .split("\n")
    .map((line) => line.replace(/\s+/g, " ").trim())
    .filter(Boolean)
    .slice(0, Math.max(1, Number(field.maxLines || 1)))
    .join("\n")
    .trim();
  const maximumWords = Math.max(0, Number(field.maxWords || 0));
  if (maximumWords > 0) {
    const words = fitted.split(/\s+/).filter(Boolean);
    if (words.length > maximumWords) fitted = words.slice(0, maximumWords).join(" ");
  }
  const maximum = Math.max(1, Number(field.maxChars || 1));
  if (fitted.length <= maximum) return fitted;
  fitted = fitted.slice(0, maximum + 1);
  const cut = fitted.lastIndexOf(" ");
  return (cut >= Math.floor(maximum * 0.65) ? fitted.slice(0, cut) : fitted.slice(0, maximum)).trim();
}

function getSystemicLocalValue(key) {
  const patient = state?.patient || {};
  const oncology = state?.oncology || {};
  const exam = state?.exam || {};
  const professional = state?.meta?.currentProfessional || {};
  const names = splitSystemicPatientName(patient.fullName);
  const weight = parseSystemicNumber(exam.weightKg);
  const anthropometrics = calculateAnthropometrics(weight, exam.heightM || exam.height);
  const heightM = anthropometrics.heightM;
  const heightCm = anthropometrics.heightCm;
  const bmi = anthropometrics.bmi;
  const bodySurface = anthropometrics.bodySurface;
  const intent = normalizeSearchText(oncology.intent || "");
  const sex = normalizeSearchText(patient.sex || "");
  const tnm = oncology.tnm || {};
  const separatedTnm = parseSystemicTnm(oncology, tnm);
  const professionalName = professional.fullName || professional.name || [professional.firstName, professional.lastName].filter(hasText).join(" ") || state?.meta?.currentUser || "";
  const formattedNumber = (number, decimals = 1) => number > 0 ? Number(number).toFixed(decimals).replace(".", ",") : "";
  const values = {
    "patient.fullName": patient.fullName || "",
    "patient.firstName": names.firstName,
    "patient.lastName": names.lastName,
    "patient.dni": patient.dni || "",
    "patient.birthDate": formatDateOptional(patient.birthDate, patient.birthDatePrecision),
    "patient.age": calculateSystemicAge(patient.birthDate),
    "patient.phone": patient.phone || "",
    "patient.email": patient.email || "",
    "patient.address": patient.address || "",
    "patient.locality": patient.locality || patient.city || "",
    "patient.insurance": patient.insurance || "",
    "patient.affiliateNumber": patient.affiliateNumber || "",
    "patient.affiliateCardLast4": String(patient.affiliateNumber || "").replace(/\D/g, "").slice(-4),
    "patient.civilStatus": patient.civilStatus || patient.maritalStatus || "",
    "patient.documentTypeDni": Boolean(patient.dni),
    "patient.documentTypeCi": false,
    "patient.documentTypeLe": false,
    "patient.documentTypeLc": false,
    "patient.affiliateActive": false,
    "patient.affiliateMonotributista": false,
    "patient.affiliateRetired": false,
    "patient.sexMale": /^(?:m|masculino|varon|hombre)$/.test(sex),
    "patient.sexFemale": /^(?:f|femenino|mujer)$/.test(sex),
    "oncology.diagnosis": oncology.diagnosis || "",
    "oncology.diagnosisDate": formatDateOptional(oncology.diagnosisDate, oncology.diagnosisDatePrecision),
    "oncology.diagnosisHistology": [oncology.diagnosis, oncology.histology].filter(hasText).join(". "),
    "oncology.topography": oncology.topography || "",
    "oncology.histology": oncology.histology || "",
    "oncology.stage": oncology.stage || tnm.stage || "",
    "oncology.biomarkers": oncology.biomarkers || "",
    "oncology.performanceStatus": oncology.performanceStatus || "",
    "oncology.tnmT": separatedTnm.t,
    "oncology.tnmN": separatedTnm.n,
    "oncology.tnmM": separatedTnm.m,
    "oncology.stageGroup": separatedTnm.stage,
    "oncology.intentAdjuvant": /adjuv/.test(intent) && !/neoadjuv/.test(intent),
    "oncology.intentNeoadjuvant": /neoadjuv/.test(intent),
    "oncology.intentPalliative": /paliat|avanzad/.test(intent),
    "exam.weightKg": weight > 0 ? String(exam.weightKg || formattedNumber(weight, 1)) : "",
    "exam.heightCm": formattedNumber(heightCm, 0),
    "exam.bmi": formattedNumber(bmi, 1),
    "exam.bodySurface": formattedNumber(bodySurface, 2),
    "professional.name": professionalName,
    "professional.specialty": professional.specialty || professional.especialidad || "",
    "professional.contact": [professional.phone || professional.telephone, professional.email].filter(hasText).join(" · "),
    "professional.careLocation": professional.careLocation || professional.location || professional.institution || "",
    today: formatDate(today()),
    todayWithCity: [patient.locality || patient.city, formatDate(today())].filter(hasText).join(", "),
    mendozaToday: ["Mendoza", formatDate(today())].join(", "),
    alwaysTrue: true,
    currentYear: String(new Date().getFullYear())
  };
  return Object.prototype.hasOwnProperty.call(values, key) ? values[key] : "";
}

function splitSystemicPatientName(fullName) {
  const name = String(fullName || "").trim();
  if (!name) return { firstName: "", lastName: "" };
  if (name.includes(",")) {
    const [lastName, ...firstParts] = name.split(",");
    return { firstName: firstParts.join(" ").trim(), lastName: lastName.trim() };
  }
  const parts = name.split(/\s+/).filter(Boolean);
  if (parts.length === 1) return { firstName: parts[0], lastName: "" };
  return { firstName: parts.slice(0, -1).join(" "), lastName: parts.at(-1) };
}

function calculateSystemicAge(birthDate) {
  const match = String(birthDate || "").match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!match) return "";
  const now = new Date();
  let age = now.getFullYear() - Number(match[1]);
  const beforeBirthday = now.getMonth() + 1 < Number(match[2]) || (now.getMonth() + 1 === Number(match[2]) && now.getDate() < Number(match[3]));
  if (beforeBirthday) age -= 1;
  return age >= 0 && age <= 130 ? String(age) : "";
}

function parseSystemicNumber(value) {
  const parsed = Number(String(value ?? "").trim().replace(",", "."));
  return Number.isFinite(parsed) ? parsed : 0;
}

function parseSystemicTnm(oncology = {}, tnm = {}) {
  const combined = [tnm.t, tnm.n, tnm.m, tnm.stage, tnm.substage, oncology.stage]
    .filter(hasText)
    .join(" ");
  const extractAxis = (axis, categoryPattern) => {
    const explicit = String(tnm[axis.toLowerCase()] || "").trim();
    if (explicit) return explicit;
    const matcher = new RegExp(`(?:^|[^A-Za-z0-9])((?:yc|yp|[cpr])?${axis}(?:${categoryPattern}))(?=$|[^A-Za-z0-9])`, "i");
    return combined.match(matcher)?.[1] || "";
  };
  const explicitStage = String(tnm.stage || tnm.substage || "").trim();
  const sourceStage = String(oncology.stage || "").trim();
  const labelledStage = sourceStage.match(/(?:estadio|stage)\s*(?:clinico|clínico|patologico|patológico)?\s*[:=-]?\s*([0-9IVX]+(?:[A-C])?)/i)?.[1] || "";
  const isolatedStage = sourceStage.match(/^\s*([0-9IVX]+(?:[A-C])?)\s*$/i)?.[1] || "";
  return {
    t: extractAxis("T", "is|x|0|[1-4](?:mi|[a-d])?"),
    n: extractAxis("N", "x|0|[1-3](?:mi|[a-c])?"),
    m: extractAxis("M", "x|0|1(?:[a-d])?"),
    stage: explicitStage || labelledStage || isolatedStage
  };
}

function openSystemicFormPreview(template, values, warnings = []) {
  pendingSystemicDraft = { template: JSON.parse(JSON.stringify(template)), values: { ...values }, warnings: [...warnings] };
  prescriptionPreviewId = null;
  const modal = $("#prescriptionPreviewModal");
  const genericPreview = $("#prescriptionPdfPreview");
  const pages = $("#systemicPreviewPages");
  const toolbar = $("#systemicPreviewToolbar");
  if (!modal || !pages || !toolbar) return;

  modal.classList.add("systemic-mode");
  $("#prescriptionPreviewTitle").textContent = template.title;
  genericPreview.hidden = true;
  genericPreview.innerHTML = "";
  pages.hidden = false;
  toolbar.hidden = false;
  toolbar.querySelector(".systemic-preview-page-nav")?.remove();
  if (template.pages.length > 1) {
    toolbar.insertAdjacentHTML("beforeend", `<nav class="systemic-preview-page-nav" aria-label="Ir a página">${template.pages.map((page, index) => `<button type="button" data-systemic-page-nav="${index + 1}">Página ${index + 1}</button>`).join("")}</nav>`);
  }
  pages.innerHTML = template.pages.map((page, index) => renderSystemicFormPage(template, page, index + 1, values)).join("");
  const status = $("#systemicPreviewStatus");
  if (status) {
    status.textContent = warnings.length ? warnings.join(" ") : "Revise los datos; los campos vacíos quedan disponibles para completar.";
    status.dataset.status = warnings.length ? "warning" : "ready";
  }
  const printLabel = $("span", $("#printPrescriptionPreviewBtn"));
  if (printLabel) printLabel.textContent = "Confirmar e imprimir";
  modal.classList.add("open");
  modal.setAttribute("aria-hidden", "false");
  refreshIcons();
  window.setTimeout(() => validateSystemicFormLayout(), 80);
}

function renderSystemicFormPage(template, page, pageNumber, values) {
  const fields = template.fields.filter((field) => Number(field.page) === pageNumber);
  const pageWidth = String(page.paper || "A4").toLowerCase() === "legal" ? 216 : 210;
  // Amplía solamente la edición en pantalla; la impresión conserva el papel real.
  const previewPageWidth = Math.round(pageWidth * 1.18);
  return `<section class="systemic-form-page-shell" data-systemic-page="${pageNumber}" style="--systemic-page-width:${previewPageWidth}mm">
    <header class="systemic-form-page-caption"><span>Página ${pageNumber} de ${template.pages.length}</span><small>${escapeHtml(page.paper || "A4")}</small></header>
    <article class="systemic-form-page systemic-form-page--html" style="--systemic-page-width:${previewPageWidth}mm;--systemic-page-ratio:${Number(page.width)} / ${Number(page.height)}">
      ${renderSystemicCleanFormUnderlay(template.shortTitle || template.title, page, pageNumber, template.pages.length, fields)}
      ${fields.map((field) => renderSystemicOverlayField(field, values[field.id], page)).join("")}
    </article>
  </section>`;
}

function renderSystemicCleanFormUnderlay(title, page, pageNumber, totalPages, fields) {
  const paper = String(page.paper || "A4");
  const sortedFields = [...fields].sort((a, b) => Number(a.y) - Number(b.y) || Number(a.x) - Number(b.x));
  const detectedLines = Array.isArray(page.underlay?.lines) ? page.underlay.lines : [];
  const originalPaths = Array.isArray(page.underlay?.paths) ? page.underlay.paths : [];
  const originalTexts = Array.isArray(page.underlay?.texts) ? page.underlay.texts : [];
  const bands = buildSystemicCleanBands(sortedFields);
  const typography = getSystemicTypography(page);
  return `<div class="systemic-form-html-background systemic-clean-form" data-systemic-typography="${escapeAttr(typography.id)}" style="${escapeAttr(getSystemicTypographyStyle(typography))}" role="img" aria-label="${escapeAttr(`${title}, pagina ${pageNumber}`)}">
    ${originalTexts.length ? "" : renderSystemicFallbackHeader(title, pageNumber, totalPages, paper, typography)}
    ${renderSystemicOriginalPaths(page, originalPaths)}
    ${originalPaths.length ? "" : detectedLines.map((line) => renderSystemicDetectedLine(line)).join("")}
    ${originalTexts.map((item) => renderSystemicOriginalText(item)).join("")}
    ${originalTexts.length || detectedLines.length ? "" : bands.map((band) => renderSystemicCleanBand(band)).join("")}
    ${originalTexts.length
      ? (originalPaths.length ? "" : sortedFields.filter((field) => field.kind === "checkbox").map((field) => renderSystemicCheckboxBox(field)).join(""))
      : sortedFields.map((field) => renderSystemicCleanFieldFrame(field, !detectedLines.length)).join("")}
  </div>`;
}

function renderSystemicCheckboxBox(field) {
  return `<div class="systemic-clean-field-box systemic-clean-field-box--checkbox" style="left:${Number(field.x)}%;top:${Number(field.y)}%;width:${Number(field.width)}%;height:${Number(field.height)}%"></div>`;
}

function renderSystemicFallbackHeader(title, pageNumber, totalPages, paper, typography) {
  const sourceTitle = String(title || "");
  if (typography.id === "osecac-classic") {
    const continuity = /continuidad/i.test(sourceTitle);
    const subtitle = continuity
      ? "Formulario de Prescripción Oncológica Continuidad de Tratamiento (F.P.O.C.T.)"
      : "Formulario de Prescripción Oncológica (F.P.O.) 1º vez / Nuevo Tratamiento";
    return `<div class="systemic-clean-form-header systemic-clean-form-header--osecac">
      <span>O.S.E.C.A.C.</span>
      <strong>${escapeHtml(subtitle)}</strong>
      ${continuity ? `<em>Utilizar este FPO para pacientes que continúan con el mismo esquema terapéutico prescripto en el ciclo anterior</em><b>TODA LA INFORMACIÓN DENUNCIADA REVISTE CARÁCTER DE DECLARACIÓN JURADA</b>` : ""}
    </div>`;
  }
  return `<div class="systemic-clean-form-header">
    <span>${escapeHtml(sourceTitle)}</span>
    <strong>${escapeHtml(getSystemicCleanPageTitle(sourceTitle, pageNumber))}</strong>
    <small>Pagina ${pageNumber} de ${totalPages} - ${escapeHtml(paper)}</small>
  </div>`;
}

function renderSystemicOriginalPaths(page, paths) {
  if (!paths.length) return "";
  const viewBox = Array.isArray(page.underlay?.viewBox) ? page.underlay.viewBox : [Number(page.width), Number(page.height)];
  const width = Math.max(1, Number(viewBox[0] || page.width || 1));
  const height = Math.max(1, Number(viewBox[1] || page.height || 1));
  const content = paths.map((item) => `<path d="${escapeAttr(item.d || "")}" fill="${escapeAttr(item.fill || "none")}" stroke="${escapeAttr(item.stroke || "none")}" stroke-width="${Math.max(0.05, Number(item.strokeWidth || 0.25))}" fill-rule="${item.fillRule === "evenodd" ? "evenodd" : "nonzero"}"></path>`).join("");
  return `<svg class="systemic-original-vector" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" aria-hidden="true">${content}</svg>`;
}

function renderSystemicOriginalText(item) {
  const size = Math.max(0.35, Number(item.size || 1));
  const weight = Math.max(300, Number(item.weight || 400));
  const style = [
    `left:${Number(item.x || 0)}%`,
    `top:${Number(item.y || 0)}%`,
    `font-size:${size}cqw`,
    `font-family:${String(item.family || "Arial, Helvetica, sans-serif")}`,
    `font-weight:${weight}`,
    `font-style:${item.style === "italic" ? "italic" : "normal"}`,
    `color:${/^#[0-9a-f]{6}$/i.test(String(item.color || "")) ? item.color : "#000000"}`
  ].join(";");
  return `<span class="systemic-original-text" style="${escapeAttr(style)}">${escapeHtml(String(item.text || ""))}</span>`;
}

function getSystemicTypography(page) {
  const source = page?.typography && typeof page.typography === "object" ? page.typography : {};
  return {
    id: String(source.id || "default-arial"),
    family: String(source.family || "Arial, Helvetica, sans-serif"),
    fieldFamily: String(source.fieldFamily || source.family || "Arial, Helvetica, sans-serif"),
    headingFamily: String(source.headingFamily || source.family || "Arial, Helvetica, sans-serif"),
    labelSizePt: Math.max(6, Number(source.labelSizePt || 9)),
    fieldSizePt: Math.max(6, Number(source.fieldSizePt || 9)),
    labelWeight: Math.max(300, Number(source.labelWeight || 600)),
    fieldWeight: Math.max(300, Number(source.fieldWeight || 500)),
    headingWeight: Math.max(400, Number(source.headingWeight || 700)),
    accent: /^#[0-9a-f]{6}$/i.test(String(source.accent || "")) ? source.accent : "#0e9aef"
  };
}

function getSystemicTypographyStyle(typography) {
  return [
    `--systemic-font-family:${typography.family}`,
    `--systemic-field-font-family:${typography.fieldFamily}`,
    `--systemic-heading-font-family:${typography.headingFamily}`,
    `--systemic-label-size:${typography.labelSizePt}pt`,
    `--systemic-field-size:${typography.fieldSizePt}pt`,
    `--systemic-label-weight:${typography.labelWeight}`,
    `--systemic-field-weight:${typography.fieldWeight}`,
    `--systemic-heading-weight:${typography.headingWeight}`,
    `--systemic-accent:${typography.accent}`
  ].join(";");
}

function renderSystemicDetectedLine(line) {
  const kind = line.kind === "v" ? "v" : "h";
  const thickness = Math.max(0.06, Number(line.t || (kind === "h" ? line.h : line.w) || 0.06));
  const width = kind === "h" ? Number(line.w || 0) : thickness;
  const height = kind === "v" ? Number(line.h || 0) : thickness;
  if (width <= 0 || height <= 0) return "";
  return `<i class="systemic-clean-detected-line systemic-clean-detected-line--${kind}" style="left:${Number(line.x)}%;top:${Number(line.y)}%;width:${width}%;height:${height}%"></i>`;
}

function buildSystemicCleanBands(fields) {
  const rows = fields
    .filter((field) => field.kind !== "checkbox")
    .map((field) => ({ y: Number(field.y), bottom: Number(field.y) + Number(field.height || 1.5), x: Number(field.x) }))
    .sort((a, b) => a.y - b.y || a.x - b.x);
  if (!rows.length) return [];
  const bands = [];
  let current = { top: Math.max(6, rows[0].y - 2.4), bottom: rows[0].bottom + 2.0, count: 1 };
  rows.slice(1).forEach((row) => {
    if (row.y - current.bottom > 5.2) {
      bands.push(current);
      current = { top: Math.max(6, row.y - 2.4), bottom: row.bottom + 2.0, count: 1 };
    } else {
      current.bottom = Math.max(current.bottom, row.bottom + 2.0);
      current.count += 1;
    }
  });
  bands.push(current);
  return bands
    .filter((band) => band.bottom - band.top > 2.5 && band.count >= 2)
    .map((band, index) => ({ ...band, index, title: getSystemicCleanBandTitle(index) }));
}

function getSystemicCleanPageTitle(title, pageNumber) {
  const text = String(title || "").toLowerCase();
  if (text.includes("cuidar") || text.includes("paliativos")) return pageNumber === 1 ? "Evaluacion inicial" : "Sintomas y red de cuidado";
  if (text.includes("registro oncologico")) return pageNumber === 1 ? "Registro oncologico" : "Antecedentes y autorizacion";
  if (text.includes("plan completo")) return pageNumber === 1 ? "Consentimiento" : pageNumber === 2 ? "Historia clinica oncologica" : "Solicitud de tratamiento";
  if (text.includes("continuidad")) return "Continuidad de tratamiento";
  return "Nuevo tratamiento";
}

function getSystemicCleanBandTitle(index) {
  return ["Datos del paciente", "Datos clinicos", "Antecedentes / evaluacion", "Tratamiento solicitado", "Medicacion / esquema", "Firma y auditoria"][index] || "Seccion";
}

function renderSystemicCleanBand(band) {
  const height = Math.max(2, Number(band.bottom) - Number(band.top));
  return `<section class="systemic-clean-band" style="top:${Number(band.top)}%;height:${height}%">
    <span>${escapeHtml(band.title)}</span>
  </section>`;
}

function renderSystemicCleanFieldFrame(field, showBox = true) {
  const x = Number(field.x);
  const y = Number(field.y);
  const width = Number(field.width);
  const height = Number(field.height);
  const label = getSystemicOriginalLabel(field);
  if (field.kind === "checkbox") {
    const labelWidth = Math.min(28, Math.max(8, 96 - x - width - 1));
    return `<div class="systemic-clean-checkbox-label" style="left:${x + width + 0.45}%;top:${Math.max(5, y - 0.28)}%;width:${labelWidth}%">${escapeHtml(label)}</div>
      <div class="systemic-clean-field-box systemic-clean-field-box--checkbox" style="left:${x}%;top:${y}%;width:${width}%;height:${height}%"></div>`;
  }
  const isMedicationHeader = /^drug1(?:Name|Dose|Days|Frequency|Calculated)$/.test(String(field.id || ""));
  const naturalLabelWidth = Math.min(36, Math.max(4, label.length * 0.72));
  const labelLeft = isMedicationHeader ? x : Math.max(4, x - naturalLabelWidth - 0.45);
  const labelTop = Math.max(5.2, y - (isMedicationHeader ? 3.05 : 1.25));
  const labelWidth = isMedicationHeader ? width : Math.min(naturalLabelWidth, 96 - labelLeft);
  const labelMarkup = label ? `<div class="systemic-clean-field-label ${isMedicationHeader ? "systemic-clean-field-label--table" : ""}" style="left:${labelLeft}%;top:${labelTop}%;width:${labelWidth}%">${escapeHtml(label)}</div>` : "";
  const boxMarkup = showBox ? `<div class="systemic-clean-field-box" style="left:${x}%;top:${y}%;width:${width}%;height:${height}%"></div>` : "";
  return `${labelMarkup}${boxMarkup}`;
}

function getSystemicOriginalLabel(field) {
  const id = String(field.id || "");
  const exact = {
    sexMale: "Masculino", sexFemale: "Femenino",
    docTypeDni: "DNI", docTypeCi: "CI", docTypeLe: "LE", docTypeLc: "LC",
    affiliateActive: "Activo", affiliateMonotributista: "Monotributista", affiliateRetired: "Jubilado",
    intentAdjuvant: "Adyuvante", intentNeoadjuvant: "Neo-Adyuvante", intentPalliative: "Paliativo"
  };
  if (exact[id]) return exact[id];
  const drug = id.match(/^drug(\d+)(Name|Dose|Days|Frequency|Calculated)$/);
  if (drug) {
    if (drug[1] !== "1") return "";
    return { Name: "Medicamentos por DCI", Dose: "Dosis mg./m²", Days: "Días", Frequency: "Frecuencia", Calculated: "Dosis calculada" }[drug[2]] || "";
  }
  if (/No$/.test(id)) return "No";
  if (/Yes$/.test(id)) return "Sí";
  return simplifySystemicCleanLabel(field.label);
}

function simplifySystemicCleanLabel(label) {
  return String(label || "")
    .replace(/\s+documentado$/i, "")
    .replace(/\s+con fechas? y?/i, " ")
    .replace(/\s+por DCI/i, "")
    .replace(/Numero/g, "Nro.")
    .trim();
}

function renderSystemicOverlayField(field, value, page) {
  const fontSize = Number(field.fontSize || 1.05);
  const typography = getSystemicTypography(page);
  const minimumSize = Math.max(8.5, typography.fieldSizePt * 1.05);
  const maximumSize = Math.max(12, typography.fieldSizePt * 1.45);
  const style = `left:${Number(field.x)}%;top:${Number(field.y)}%;width:${Number(field.width)}%;height:${Number(field.height)}%;--systemic-field-font-family:${typography.fieldFamily};--systemic-field-weight:${typography.fieldWeight};--field-font-size:clamp(${minimumSize}px,${fontSize}cqw,${maximumSize}px)`;
  const className = `systemic-form-field ${field.kind === "checkbox" ? "systemic-form-field--checkbox" : ""} ${value === "" || value === false ? "is-blank" : ""}`;
  if (field.kind === "checkbox") {
    return `<input class="${className}" type="checkbox" data-systemic-field-id="${escapeAttr(field.id)}" ${field.group ? `data-systemic-group="${escapeAttr(field.group)}"` : ""} aria-label="${escapeAttr(field.label)}" title="${escapeAttr(field.label)}" style="${style}" ${value === true ? "checked" : ""}>`;
  }
  const attributes = `class="${className}" data-systemic-field-id="${escapeAttr(field.id)}" data-max-lines="${Number(field.maxLines || 1)}" ${Number(field.maxWords || 0) > 0 ? `data-max-words="${Number(field.maxWords)}"` : ""} maxlength="${Number(field.maxChars || 1)}" aria-label="${escapeAttr(field.label)}" title="${escapeAttr(field.label)}" style="${style}" spellcheck="true"`;
  if (field.kind === "textarea" || Number(field.maxLines || 1) > 1) return `<textarea ${attributes}>${escapeHtml(value || "")}</textarea>`;
  return `<input type="text" ${attributes} value="${escapeAttr(value || "")}">`;
}

function handleSystemicPreviewInput(event) {
  const field = event.target.closest("[data-systemic-field-id]");
  if (!field || !pendingSystemicDraft) return;
  if (field.type === "checkbox" && field.checked && field.dataset.systemicGroup) {
    $$('[data-systemic-group]', $("#systemicPreviewPages")).filter((other) => other.dataset.systemicGroup === field.dataset.systemicGroup).forEach((other) => {
      if (other === field) return;
      other.checked = false;
      other.classList.add("is-blank");
    });
  }
  if (field instanceof HTMLTextAreaElement) {
    const maxLines = Math.max(1, Number(field.dataset.maxLines || 1));
    const lines = field.value.replace(/\r\n?/g, "\n").split("\n");
    if (lines.length > maxLines) field.value = lines.slice(0, maxLines).join("\n");
  }
  const maxWords = Math.max(0, Number(field.dataset.maxWords || 0));
  if (maxWords > 0) {
    const words = field.value.trim().split(/\s+/).filter(Boolean);
    if (words.length > maxWords) field.value = words.slice(0, maxWords).join(" ");
  }
  field.classList.toggle("is-blank", field.type === "checkbox" ? !field.checked : !field.value.trim());
  validateSystemicFormLayout();
}

function isSystemicFieldOverflowing(field) {
  if (field.type === "checkbox") return false;
  if (field instanceof HTMLTextAreaElement) return field.scrollHeight > field.clientHeight + 2 || field.scrollWidth > field.clientWidth + 2;
  return field.scrollWidth > field.clientWidth + 2;
}

function validateSystemicFormLayout({ announce = false } = {}) {
  if (!pendingSystemicDraft) return true;
  const fields = $$('[data-systemic-field-id]', $("#systemicPreviewPages"));
  const overflowing = fields.filter((field) => {
    const invalid = isSystemicFieldOverflowing(field);
    field.classList.toggle("is-overflowing", invalid);
    return invalid;
  });
  const status = $("#systemicPreviewStatus");
  if (status && overflowing.length) {
    status.textContent = `${overflowing.length} ${overflowing.length === 1 ? "campo excede" : "campos exceden"} el espacio disponible. Acorte el texto marcado en rojo.`;
    status.dataset.status = "error";
  } else if (status && announce) {
    status.textContent = "El formulario está listo para imprimir.";
    status.dataset.status = "ready";
  }
  return overflowing.length === 0;
}

function collectSystemicPreviewValues() {
  const pages = $$(".systemic-form-page", $("#systemicPreviewPages"));
  if (!pages.length) {
    const status = $("#systemicPreviewStatus");
    if (status) {
      status.textContent = "Aún no se cargaron las páginas del formulario. Espere un momento y vuelva a intentar.";
      status.dataset.status = "error";
    }
    return null;
  }
  if (!validateSystemicFormLayout({ announce: true })) return null;
  const values = {};
  $$('[data-systemic-field-id]', $("#systemicPreviewPages")).forEach((field) => {
    values[field.dataset.systemicFieldId] = field.type === "checkbox" ? field.checked : field.value.trim();
  });
  return values;
}

function buildSystemicPrescriptionItem(values) {
  const template = pendingSystemicDraft.template;
  const now = new Date().toISOString();
  const preferredSummaryIds = ["requestedScheme", "scheme", "p3RequestedScheme", "consentPlan", "p2Plan", "justification", "diagnosis"];
  const principal = preferredSummaryIds.map((id) => values[id]).find(hasText) || "Formulario sistémico completado";
  return {
    id: makeId("rx-systemic"),
    type: "systemic",
    date: today(),
    datePrecision: "day",
    createdAt: now,
    status: "registered",
    audit: buildAuditStamp("cargado", { at: now }),
    title: template.shortTitle || template.title,
    summary: fitSystemicFieldValue(principal, { maxChars: 220, maxLines: 2 }),
    data: {
      formId: template.id,
      formVersion: template.version,
      formTitle: template.title,
      sourceFile: template.sourceFile || "",
      pages: JSON.parse(JSON.stringify(template.pages)),
      fields: template.fields.map((field) => ({ ...field, value: values[field.id] ?? (field.kind === "checkbox" ? false : "") }))
    }
  };
}

function confirmAndPrintSystemicForm() {
  if (!pendingSystemicDraft) return;
  const values = collectSystemicPreviewValues();
  if (!values) {
    toast("Revise el formulario antes de imprimir");
    $(".systemic-form-field.is-overflowing", $("#systemicPreviewPages"))?.focus();
    return;
  }
  const item = buildSystemicPrescriptionItem(values);
  if (!printSystemicForm(item)) {
    toast("El navegador bloqueó la ventana de impresión");
    return;
  }
  state.prescriptions.unshift(item);
  state.meta.updatedAt = item.createdAt;
  queueLocalSave();
  renderPrescriptionDrafts();
  renderPreview();
  renderPatientOutputs();
  persistClinicalState();
  closePrescriptionPreview();
  if ($("#systemicFormNotes")) $("#systemicFormNotes").value = "";
  toast("Formulario sistémico registrado en la historia clínica");
}

function renderPrescriptionDrafts() {
  const list=$("#rxDraftList");if(!list||!state)return;const drafts=state.prescriptions||[];$("#rxDraftCount").textContent=drafts.length;
  list.innerHTML=drafts.length?drafts.map((draft)=>`<article class="prescription-draft prescription-draft--${escapeAttr(draft.type)}"><header><div><strong>${escapeHtml(draft.title)}</strong><small>${escapeHtml(formatDateTime(draft.createdAt))}</small></div></header><p>${escapeHtml(truncateText(draft.summary,180))}</p><div class="prescription-draft-actions"><button type="button" data-rx-action="print" data-id="${escapeAttr(draft.id)}" title="Imprimir"><i data-lucide="printer"></i></button><button type="button" data-rx-action="duplicate" data-id="${escapeAttr(draft.id)}" title="Duplicar como nuevo documento"><i data-lucide="copy"></i></button><button type="button" data-rx-action="delete" data-id="${escapeAttr(draft.id)}" title="Eliminar"><i data-lucide="trash-2"></i></button></div></article>`).join(""):`<div class="right-empty-state"><i data-lucide="clipboard-list"></i><strong>Sin documentos</strong><span>Registre una receta, certificado, estudio, indicación o formulario sistémico.</span></div>`;refreshIcons();
}

function clearPrescriptionEditor(){
  const pane=$(`[data-prescription-pane="${prescriptionType}"]`);
  pane?.querySelectorAll("input, textarea").forEach((field)=>{if(field.type==="checkbox")field.checked=false;else field.value=""});
  if(prescriptionType==="certificate")fillCertificateTemplate();
  if(prescriptionType==="systemic") {
    if ($("#systemicTemplateSelect")) $("#systemicTemplateSelect").selectedIndex = systemicFormCatalog.length ? 0 : 0;
    renderSystemicTemplateDescription();
    setSystemicFormStatus(systemicFormCatalog.length ? `${systemicFormCatalog.length} formularios disponibles` : "", "ready");
  }
}

function handlePrescriptionDraftAction(action,id){
  const draft=state.prescriptions.find((item)=>item.id===id);if(!draft)return;
  if(action==="delete")state.prescriptions=state.prescriptions.filter((item)=>item.id!==id);
  if(action==="duplicate"){
    const now=new Date().toISOString();
    const copy=JSON.parse(JSON.stringify(draft));
    state.prescriptions.unshift({...copy,id:makeId("rx"),date:today(),datePrecision:"day",createdAt:now,audit:buildAuditStamp("cargado",{at:now})});
  }
  if(action==="print"){printPrescriptionDraft(draft);return}
  state.meta.updatedAt=new Date().toISOString();queueLocalSave();renderPrescriptionDrafts();renderPreview();renderPatientOutputs();persistClinicalState();
}

function getPrescriptionCoverage(patient = state?.patient || {}) {
  return {
    insurance: String(patient.insurance || "").trim(),
    affiliateNumber: String(patient.affiliateNumber || "").trim()
  };
}

function renderPrescriptionCoverageMarkup() {
  const coverage = getPrescriptionCoverage();
  return `<p class="prescription-coverage"><strong>Obra social:</strong> ${escapeHtml(coverage.insurance || "—")} · <strong>N.° de afiliado:</strong> ${escapeHtml(coverage.affiliateNumber || "—")}</p>`;
}

function openPrescriptionPreview(item){
  if (item.type === "systemic") {
    printSystemicForm(item);
    return;
  }
  pendingSystemicDraft = null;
  prescriptionPreviewId=item.id;
  const modal=$("#prescriptionPreviewModal");
  modal.classList.remove("systemic-mode");
  $("#systemicPreviewToolbar").hidden=true;
  $("#systemicPreviewPages").hidden=true;
  $("#prescriptionPdfPreview").hidden=false;
  $("#prescriptionPreviewTitle").textContent=`${getPrescriptionTypeLabel(item.type)} lista para imprimir`;
  const coverageMarkup=item.type==="medication"?renderPrescriptionCoverageMarkup():"";
  $("#prescriptionPdfPreview").innerHTML=`<article class="prescription-pdf-page"><span>${escapeHtml(getPrescriptionTypeLabel(item.type))}</span><h2>${escapeHtml(item.title)}</h2><p><strong>Fecha:</strong> ${escapeHtml(formatDate(item.date||item.createdAt))}</p><p><strong>Paciente:</strong> ${escapeHtml(state.patient.fullName||"")} · DNI ${escapeHtml(state.patient.dni||"")}</p>${coverageMarkup}<div>${getPrescriptionDetailLines(item).map((line)=>`<p>${escapeHtml(line)}</p>`).join("")}</div><footer>Profesional: ${escapeHtml(state.meta.currentProfessional?.lastName||state.meta.currentUser||"")} · Mat. ${escapeHtml(state.meta.currentProfessional?.license||"s/d")}</footer></article>`;
  const printLabel=$("span",$("#printPrescriptionPreviewBtn"));if(printLabel)printLabel.textContent="Imprimir";
  modal.classList.add("open");modal.setAttribute("aria-hidden","false");refreshIcons();
}

function closePrescriptionPreview(){
  const modal=$("#prescriptionPreviewModal");if(!modal?.classList.contains("open"))return;
  modal.classList.remove("open","systemic-mode");modal.setAttribute("aria-hidden","true");prescriptionPreviewId=null;pendingSystemicDraft=null;
  if ($("#systemicPreviewToolbar")) $("#systemicPreviewToolbar").hidden=true;
  if ($("#systemicPreviewPages")) { $("#systemicPreviewPages").hidden=true; $("#systemicPreviewPages").innerHTML=""; }
  if ($("#prescriptionPdfPreview")) $("#prescriptionPdfPreview").hidden=false;
  const printLabel=$("span",$("#printPrescriptionPreviewBtn"));if(printLabel)printLabel.textContent="Imprimir";
}

function printPrescriptionDraft(draft){
  if (draft.type === "systemic") {
    if (!printSystemicForm(draft)) toast("El navegador bloqueó la ventana de impresión");
    return;
  }
  const win=window.open("","_blank","width=900,height=650");if(!win)return;
  const labels={generic:"Nombre generico",brand:"Nombre comercial",presentation:"Presentacion",form:"Forma farmaceutica",dose:"Dosis",route:"Via",frequency:"Frecuencia",duration:"Duracion",quantity:"Cantidad",indication:"Indicacion / diagnostico",instructions:"Instrucciones",certificateType:"Tipo de certificado",from:"Desde",to:"Hasta",text:"Texto",includeDiagnosis:"Incluye diagnostico",category:"Categoria",priority:"Prioridad",name:"Estudio / practica",notes:"Preparacion / observaciones",title:"Titulo"};
  const details=Object.entries(draft.data||{}).filter(([,value])=>value!==""&&value!==false).map(([key,value])=>`<p><strong>${escapeHtml(labels[key]||key)}:</strong> ${escapeHtml(value===true?"Si":value)}</p>`).join("");
  const typeLabel=getPrescriptionTypeLabel(draft.type);
  const coverageMarkup=draft.type==="medication"?renderPrescriptionCoverageMarkup():"";
  win.document.write(`<html><head><title>${escapeHtml(typeLabel)} - ${escapeHtml(draft.title)}</title><style>@page{size:A5 landscape;margin:10mm}*{box-sizing:border-box}html,body{width:210mm;min-height:148mm;margin:0}body{display:flex;flex-direction:column;padding:10mm;font:12px Arial,sans-serif;color:#2f4050}.type{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.1em;color:#087fc4}h1{margin:5px 0 8px;border-bottom:1px solid #d7e0e7;padding-bottom:7px;font-size:18px}p{margin:4px 0;line-height:1.35}footer{margin-top:auto;border-top:1px solid #d7e0e7;padding-top:8px}.print-note{display:none}@media screen{html{margin:auto;background:#f3f3f4}body{margin:20px auto;background:#fff;box-shadow:0 8px 30px #0002}.print-note{display:block;position:fixed;right:12px;bottom:12px;border-radius:6px;background:#0e9aef;padding:7px 10px;color:#fff;font-size:11px}}@media print{html,body{width:auto;min-height:0}body{padding:0}}</style></head><body><div class="type">${escapeHtml(typeLabel)}</div><h1>${escapeHtml(draft.title)}</h1><p><strong>Fecha:</strong> ${escapeHtml(formatDate(draft.date||draft.createdAt))}</p><p><strong>Paciente:</strong> ${escapeHtml(state.patient.fullName||"")} · DNI ${escapeHtml(state.patient.dni||"")}</p>${coverageMarkup}${details}<footer>Profesional: ${escapeHtml(state.meta.currentProfessional?.lastName||state.meta.currentUser||"")} · Mat. ${escapeHtml(state.meta.currentProfessional?.license||"s/d")}</footer><div class="print-note">Formato A5 apaisado · media hoja A4</div><script>window.addEventListener('load',()=>window.print())<\/script></body></html>`);win.document.close();
}

function printSystemicForm(draft) {
  const pages = Array.isArray(draft?.data?.pages) ? draft.data.pages : [];
  const fields = Array.isArray(draft?.data?.fields) ? draft.data.fields : [];
  if (!pages.length) return false;
  const win = window.open("", "_blank", "width=1050,height=850");
  if (!win) return false;
  const title = draft.data.formTitle || draft.title || "Formulario sistemico";
  const pageMarkup = pages.map((page, index) => {
    const pageNumber = index + 1;
    const paper = String(page.paper || "A4").toLowerCase() === "legal" ? "legal" : "a4";
    const pageFields = fields.filter((field) => Number(field.page) === pageNumber);
    const typography = getSystemicTypography(page);
    const cleanUnderlay = renderSystemicCleanFormUnderlay(title, page, pageNumber, pages.length, pageFields);
    const overlays = pageFields.map((field) => {
      const value = field.value;
      if (field.kind === "checkbox") {
        return value === true ? `<span class="field checkbox" style="left:${Number(field.x)}%;top:${Number(field.y)}%;width:${Number(field.width)}%;height:${Number(field.height)}%">X</span>` : "";
      }
      const fontSize = Math.max(6.5, Number(field.fontSize || 1.05) * typography.fieldSizePt);
      return `<span class="field ${Number(field.maxLines || 1) > 1 ? "multiline" : "singleline"}" style="left:${Number(field.x)}%;top:${Number(field.y)}%;width:${Number(field.width)}%;height:${Number(field.height)}%;font-size:${fontSize}pt">${escapeHtml(String(value || ""))}</span>`;
    }).join("");
    return `<section class="form-page ${paper}" style="${escapeAttr(getSystemicTypographyStyle(typography))}">${cleanUnderlay}${overlays}</section>`;
  }).join("");
  win.document.write(`<!doctype html><html lang="es"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title><style>
    @page a4{size:A4 portrait;margin:0}@page legal{size:legal portrait;margin:0}
    *{box-sizing:border-box}html,body{margin:0;padding:0;background:#f3f3f4;font-family:Arial,sans-serif;color:#111}
    body{display:flex;flex-direction:column;align-items:center;gap:12mm;padding:10mm}
    @font-face{font-family:"HCOP Alegreya Sans";src:url("/assets/systemic-fonts/alegreya-sans-medium.ttf") format("truetype");font-style:normal;font-weight:500;font-display:swap}
    @font-face{font-family:"HCOP Alegreya Sans";src:url("/assets/systemic-fonts/alegreya-sans-bold.ttf") format("truetype");font-style:normal;font-weight:700;font-display:swap}
    .form-page{position:relative;flex:none;overflow:hidden;background:#fff;box-shadow:0 5px 24px #0003;container-type:inline-size}
    .form-page.a4{page:a4;width:210mm;height:297mm}.form-page.legal{page:legal;width:216mm;height:356mm}
    .systemic-clean-form{position:absolute;z-index:0;inset:0;overflow:hidden;background:#fff;color:#2f4050;font-family:var(--systemic-font-family,Arial,sans-serif)}
    .systemic-clean-form:before{position:absolute;inset:0;content:"";background:#fff}
    .systemic-clean-form-header{position:absolute;left:4.8%;right:4.8%;top:2.8%;height:5.7%;display:grid;grid-template-columns:minmax(0,1fr) auto;grid-template-rows:auto auto;gap:1mm 4mm;border-bottom:1.1pt solid #0e9aef;padding-bottom:1.5mm}
    .systemic-clean-form-header span{grid-column:1 / 2;color:var(--systemic-accent,#0e9aef);font-family:var(--systemic-heading-font-family,inherit);font-size:6.8pt;font-weight:var(--systemic-heading-weight,700);text-transform:uppercase;letter-spacing:.04em}.systemic-clean-form-header strong{grid-column:1 / 2;color:#2f4050;font-family:var(--systemic-heading-font-family,inherit);font-size:13pt;font-weight:var(--systemic-heading-weight,700);line-height:1}.systemic-clean-form-header small{grid-column:2;grid-row:1 / 3;align-self:center;border:1px solid #c9d9e5;border-radius:2mm;padding:1.2mm 2.2mm;color:#526577;font-family:var(--systemic-font-family,inherit);font-size:7pt;font-weight:700}
    .systemic-clean-form-header--osecac{left:5%;right:5%;top:2.7%;height:9%;display:flex;flex-direction:column;align-items:center;justify-content:flex-start;gap:.5mm;border:0;padding:0;text-align:center}.systemic-clean-form-header--osecac span{color:#050505;font-family:"Times New Roman",Times,serif;font-size:16pt;font-weight:700;line-height:1;text-transform:none;letter-spacing:.12em}.systemic-clean-form-header--osecac strong{color:#050505;font-family:Arial,Helvetica,sans-serif;font-size:13.5pt;font-weight:400;line-height:1.05;white-space:nowrap}.systemic-clean-form-header--osecac em{color:#050505;font-family:"Times New Roman",Times,serif;font-size:8pt;font-style:normal;line-height:1.1;white-space:nowrap}.systemic-clean-form-header--osecac b{margin-top:.8mm;border-top:.25mm solid #111;border-bottom:.25mm solid #111;padding:.3mm 2mm;color:#050505;font-family:Arial,Helvetica,sans-serif;font-size:8.5pt;line-height:1.1;white-space:nowrap}
    .systemic-clean-band{position:absolute;left:4.2%;right:4.2%;border:1px solid rgba(14,154,239,.28);border-radius:1.5mm;background:rgba(234,247,254,.52)}.systemic-clean-band span{position:absolute;left:1.5mm;top:-2.1mm;background:#fff;padding:0 1.4mm;color:#087fc4;font-size:6.3pt;font-weight:700;text-transform:uppercase;letter-spacing:.035em}
    .systemic-clean-detected-line{position:absolute;z-index:1;display:block;background:#111;opacity:1}.systemic-clean-detected-line--h{min-height:.15mm}.systemic-clean-detected-line--v{min-width:.15mm}
    .systemic-original-vector{position:absolute;z-index:1;inset:0;width:100%;height:100%;overflow:visible}.systemic-original-text{position:absolute;z-index:2;display:block;line-height:1;white-space:pre;letter-spacing:normal;font-variant-ligatures:none}
    .systemic-clean-field-label,.systemic-clean-checkbox-label{position:absolute;z-index:1;overflow:hidden;color:#2f4050;font-family:var(--systemic-font-family,inherit);font-size:var(--systemic-label-size,9pt);font-weight:var(--systemic-label-weight,700);line-height:1.05;white-space:nowrap;text-overflow:ellipsis}.systemic-clean-checkbox-label{font-size:calc(var(--systemic-label-size,9pt) * .92)}.systemic-clean-field-label--table{text-align:center}
    [data-systemic-typography="osecac-classic"] .systemic-clean-field-label,[data-systemic-typography="osecac-classic"] .systemic-clean-checkbox-label{color:#050505}
    .systemic-clean-field-box{position:absolute;z-index:1;border:1px solid #8ea8b8;border-radius:.7mm;background:rgba(255,255,255,.72)}.systemic-clean-field-box--checkbox{border-color:#526577;border-radius:.35mm;background:#fff}
    .field{position:absolute;z-index:3;display:block;overflow:hidden;white-space:pre-wrap;line-height:1.05;padding:0 .35mm;font-family:var(--systemic-field-font-family,Arial,sans-serif);font-weight:var(--systemic-field-weight,500)}
    .field.singleline{display:flex;align-items:center;white-space:nowrap}.field.checkbox{display:flex;align-items:center;justify-content:center;padding:0;font:bold 9pt Arial}
    @media print{html,body{background:#fff}body{display:block;padding:0}.form-page{margin:0;box-shadow:none;break-after:page;page-break-after:always}.form-page:last-child{break-after:auto;page-break-after:auto}}
  </style></head><body>${pageMarkup}<script>
    window.addEventListener('load',()=>setTimeout(()=>window.print(),120));
  <\/script></body></html>`);
  win.document.close();
  return true;
}
function getClinicalPersistenceFailureMessage(fallback = "No se pudo confirmar el guardado") {
  if (clinicalPersistenceLastError?.code === "VERSION_CONFLICT") {
    return "La historia fue modificada en otra ventana. Recargue el paciente antes de reintentar.";
  }
  return clinicalPersistenceLastError?.message || fallback;
}

async function persistClinicalStateNow({ silent = false } = {}) {
  try {
    const response = await fetch(DATA_URL, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(state, null, 2)
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok === false) {
      const error = new Error(payload.error || `HTTP ${response.status}`);
      error.code = payload.code || (response.status === 409 ? "VERSION_CONFLICT" : "CLINICAL_PERSISTENCE_FAILED");
      error.statusCode = response.status;
      error.currentVersion = payload.currentVersion ?? null;
      throw error;
    }

    const linkedPatient = Boolean(getActiveLiraPatientId());
    const revision = Number(payload.unified?.revision);
    if (linkedPatient && payload.unified?.persisted !== true) {
      const error = new Error("La base clinica no confirmo el guardado de la historia.");
      error.code = "CLINICAL_PERSISTENCE_FAILED";
      throw error;
    }
    if (linkedPatient && (!Number.isSafeInteger(revision) || revision < 1)) {
      const error = new Error("La base clinica no devolvio una revision valida de la historia.");
      error.code = "INVALID_PERSISTENCE_REVISION";
      throw error;
    }
    if (linkedPatient) {
      state.meta.persistenceRevision = revision;
      storeClinicalStateLocally();
    }
    clinicalPersistenceLastError = null;
    return true;
  } catch (error) {
    clinicalPersistenceLastError = {
      code: String(error?.code || "CLINICAL_PERSISTENCE_FAILED"),
      message: String(error?.message || "No se pudo sincronizar con la base clinica."),
      statusCode: Number(error?.statusCode) || 0,
      currentVersion: error?.currentVersion ?? null
    };
    if (!silent) toast(getClinicalPersistenceFailureMessage(
      clinicalLocalCacheAllowed()
        ? "El borrador quedo guardado localmente; no se pudo sincronizar"
        : "No se pudo guardar la ficha en la base clínica"
    ));
    return false;
  }
}

function persistClinicalState(options = {}) {
  const run = () => persistClinicalStateNow(options);
  clinicalPersistenceQueue = clinicalPersistenceQueue.then(run, run);
  return clinicalPersistenceQueue;
}
function exportPrescriptionDrafts(){const blob=new Blob([JSON.stringify({patient:state.patient,professional:state.meta.currentProfessional,prescriptions:state.prescriptions},null,2)],{type:"application/json"});const url=URL.createObjectURL(blob);const link=document.createElement("a");link.href=url;link.download=`prescripciones-${state.patient.dni||"paciente"}.json`;link.click();URL.revokeObjectURL(url)}

function extractAgentNavigationDate(value) {
  const text = String(value || "");
  const iso = text.match(/\b(\d{4})-(\d{2})-(\d{2})\b/);
  if (iso) return iso[0];
  const local = text.match(/\b(\d{2})\/(\d{2})\/(\d{4})\b/);
  return local ? `${local[3]}-${local[2]}-${local[1]}` : "";
}

function navigateClinicalFromAgent(date, text) {
  $$("#clinicalDocument .agent-navigation-focus").forEach((element) => element.classList.remove("agent-navigation-focus"));
  let target = date ? $$("#clinicalDocument [data-clinical-date]").find((element) => element.dataset.clinicalDate === date) : null;
  if (!target) {
    const candidates = normalizeSearchText(text).split(/[^a-z0-9]+/i).filter((term) => term.length >= 5).sort((a, b) => b.length - a.length).slice(0, 8);
    target = $$("#clinicalDocument .doc-entry, #clinicalDocument .doc-section").find((element) => {
      const content = normalizeSearchText(element.textContent);
      return candidates.some((term) => content.includes(term));
    });
  }
  if (!target) {
    toast("No se encontro el registro en la historia clinica");
    return;
  }
  target.classList.add("agent-navigation-focus");
  target.scrollIntoView({ behavior: "smooth", block: "center" });
  window.setTimeout(() => target.classList.remove("agent-navigation-focus"), 5000);
}

function handleAgentChartPointer(event) {
  const target = event.target.closest?.("[data-chart-tooltip]");
  if (!target) return;
  const chart = target.closest(".agent-chart");
  if (!chart) return;
  let tooltip = $(".agent-chart-tooltip", chart);
  if (!tooltip) {
    tooltip = document.createElement("div");
    tooltip.className = "agent-chart-tooltip";
    chart.appendChild(tooltip);
  }
  tooltip.textContent = target.dataset.chartTooltip;
  const rect = chart.getBoundingClientRect();
  tooltip.style.left = `${clamp(event.clientX - rect.left + 10, 8, Math.max(rect.width - 190, 8))}px`;
  tooltip.style.top = `${clamp(event.clientY - rect.top - 38, 6, Math.max(rect.height - 50, 6))}px`;
  tooltip.classList.add("show");
}

function hideAgentChartTooltip(chart) {
  $(".agent-chart-tooltip", chart)?.classList.remove("show");
}

function clearAgentHighlights(roots = [document]) {
  const parents = new Set();
  roots.filter(Boolean).forEach((root) => {
    $$("mark.agent-highlight", root).forEach((mark) => {
      if (mark.parentNode) parents.add(mark.parentNode);
      mark.replaceWith(document.createTextNode(mark.textContent));
    });
  });
  parents.forEach((parent) => parent.normalize());
}

function applyAgentHighlights(highlights, { remember = true, resetTimeline = true, roots = null, scrollToFirst = true } = {}) {
  if (remember) lastAgentHighlights = Array.isArray(highlights) ? highlights.map((item) => ({ ...item, terms: [...(item.terms || [])] })) : [];
  const targetRoots = roots || [$("#clinicalDocument"), $("#rightTimeline")].filter(Boolean);
  clearAgentHighlights(resetTimeline ? [document] : targetRoots);
  if (resetTimeline) {
    timelineSearchQuery = "";
    timelineFilters.clear();
    timelineMilestonesOnly = false;
    renderRightTimeline();
  }
  const requests = highlights.flatMap((highlight) => (highlight.terms || []).map((term) => ({ term: normalizeSearchText(term), color: highlight.color || "yellow" }))).filter((item) => item.term.length > 1);
  if (!requests.length) return;
  let firstMark = null;
  targetRoots.forEach((root) => {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodes = [];
    while (walker.nextNode()) {
      const node = walker.currentNode;
      if (node.parentElement?.closest("input, button, svg, style, script, mark.timeline-search-mark, mark.agent-highlight")) continue;
      const normalized = normalizeSearchText(node.nodeValue);
      if (requests.some((request) => normalized.includes(request.term))) nodes.push(node);
    }
    nodes.forEach((node) => {
      const original = node.nodeValue;
      const normalized = normalizeSearchText(original);
      const ranges = [];
      requests.forEach((request) => {
        let start = 0;
        while ((start = normalized.indexOf(request.term, start)) !== -1) {
          ranges.push({ start, end: start + request.term.length, color: request.color });
          start += request.term.length;
        }
      });
      ranges.sort((a, b) => a.start - b.start || b.end - a.end);
      const accepted = ranges.filter((range, index) => !ranges.some((other, otherIndex) => otherIndex < index && range.start < other.end));
      const fragment = document.createDocumentFragment();
      let cursor = 0;
      accepted.forEach((range) => {
        if (range.start < cursor) return;
        if (range.start > cursor) fragment.appendChild(document.createTextNode(original.slice(cursor, range.start)));
        const mark = document.createElement("mark");
        mark.className = `agent-highlight agent-highlight--${range.color}`;
        mark.textContent = original.slice(range.start, range.end);
        fragment.appendChild(mark);
        firstMark ||= mark;
        cursor = range.end;
      });
      if (cursor < original.length) fragment.appendChild(document.createTextNode(original.slice(cursor)));
      node.replaceWith(fragment);
    });
  });
  $$("#rightTimeline mark.agent-highlight").forEach((mark) => {
    let details = mark.closest("details");
    while (details) {
      details.open = true;
      details = details.parentElement?.closest("details");
    }
  });
  if (firstMark && scrollToFirst) {
    firstMark.scrollIntoView({ behavior: "smooth", block: "center" });
  }
}

function renderRememberedAgentHighlights(roots) {
  if (!lastAgentHighlights.length) return;
  applyAgentHighlights(lastAgentHighlights, { remember: false, resetTimeline: false, roots, scrollToFirst: false });
}

function buildTimelineSummary(entries) {
  const prioritized = [...entries].sort((a, b) => Number(b.highlighted) - Number(a.highlighted));
  return prioritized.map((entry) => {
    const summaryTitle = entry.kind === "Evolucion"
      ? entry.title.split(" - ").slice(1).join(" - ") || "Evolucion clinica"
      : entry.title;
    const detail = entry.body || "";
    return [summaryTitle, detail].filter(hasText).join(": ");
  }).filter(hasText);
}

function renderTimelineYearCategories(entries) {
  const definitions = [
    { key: "diagnosis", label: "diagnóstico", plural: "diagnósticos" },
    { key: "study", label: "estudio", plural: "estudios" },
    { key: "pathology", label: "patologia", plural: "patologias" },
    { key: "evolution", label: "evolucion", plural: "evoluciones" },
    { key: "research", label: "registro de investigacion", plural: "registros de investigacion" },
    { key: "prescription", label: "receta", plural: "recetas" },
    { key: "certificate", label: "certificado", plural: "certificados" },
    { key: "study_order", label: "solicitud de estudio", plural: "solicitudes de estudio" },
    { key: "indication", label: "indicacion", plural: "indicaciones" },
    { key: "radiotherapy", label: "radioterapia", plural: "radioterapias" },
    { key: "surgery", label: "cirugia", plural: "cirugias" },
    { key: "chemotherapy", label: "quimioterapia", plural: "quimioterapias" },
    { key: "hormone", label: "hormonoterapia", plural: "hormonoterapias" },
    { key: "immunotherapy", label: "inmunoterapia", plural: "inmunoterapias" },
    { key: "targeted", label: "terapia dirigida", plural: "terapias dirigidas" },
    { key: "systemic", label: "tratamiento sistemico", plural: "tratamientos sistemicos" }
  ];
  const categories = definitions.map((definition) => ({
    ...definition,
    entries: entries.filter((entry) => getTimelineFilterKey(entry) === definition.key)
  })).filter((category) => category.entries.length);

  return `<div class="right-timeline-categories">${categories.map((category) => `
    <section class="right-timeline-category timeline-category--${escapeAttr(category.key)}">
      <span class="timeline-count timeline-count--${escapeAttr(category.key)}">${category.entries.length} ${escapeHtml(category.entries.length === 1 ? category.label : category.plural)}</span>
      <ul>
        ${category.entries.map((entry) => `<li>${escapeHtml(buildTimelineCategoryFact(entry))}</li>`).join("")}
      </ul>
    </section>
  `).join("")}</div>`;
}

function buildTimelineCategoryFact(entry) {
  return [entry.title, entry.body].filter(hasText).join(": ");
}

function renderTimelineSummary(entries) {
  const facts = buildTimelineSummary(entries);
  if (!facts.length) return `<p class="right-timeline-summary-empty">Sin hallazgos relevantes consignados.</p>`;
  return `<ul class="right-timeline-summary">${facts.map((fact) => `<li>${escapeHtml(fact)}</li>`).join("")}</ul>`;
}

function getTimelineFilterKey(entry) {
  if (entry.tone === "study") return "study";
  if (entry.tone === "pathology") return "pathology";
  if (entry.tone === "evolution") return "evolution";
  if (entry.tone === "research") return "research";
  if (["prescription", "certificate", "indication"].includes(entry.tone)) return entry.tone;
  if (entry.tone === "study-order") return "study_order";
  return entry.tone.replace("treatment-", "");
}

function renderTimelineFilters(entries) {
  const definitions = [
    ["diagnosis", "Diagnósticos"], ["study", "Estudios"], ["pathology", "Patologia"], ["evolution", "Evoluciones"], ["research", "Investigacion"],
    ["prescription", "Recetas"], ["certificate", "Certificados"], ["study_order", "Solicitudes"], ["indication", "Indicaciones"],
    ["radiotherapy", "Radioterapia"], ["surgery", "Cirugia"], ["chemotherapy", "Quimioterapia"],
    ["hormone", "Hormonoterapia"], ["immunotherapy", "Inmunoterapia"], ["targeted", "Terapia dirigida"], ["systemic", "Sistemico"]
  ];
  const available = new Set(entries.map(getTimelineFilterKey));
  return definitions.filter(([key]) => available.has(key)).map(([key, label]) => `
    <button class="timeline-filter timeline-filter--${escapeAttr(key)} ${timelineFilters.has(key) ? "active" : ""}" type="button" data-timeline-filter="${escapeAttr(key)}">${escapeHtml(label)}</button>
  `).join("");
}

function toggleTimelineFilter(key) {
  if (timelineFilters.has(key)) timelineFilters.delete(key);
  else timelineFilters.add(key);
  renderRightTimeline();
}

function focusTimelineDate(date) {
  if (!date) return;
  timelineFilters.clear();
  timelineMilestonesOnly = false;
  timelineSearchQuery = "";
  setRightTab("timeline");
  const day = $(`#rightTimeline .right-timeline-day[data-clinical-date="${CSS.escape(date)}"]`);
  if (!day) {
    toast("No se encontro esa fecha en la linea de tiempo");
    return;
  }
  day.closest("details.right-timeline-year")?.setAttribute("open", "");
  day.closest("details.right-timeline-month")?.setAttribute("open", "");
  setTimelinePeriodSelection(day);
  day.scrollIntoView({ behavior: "smooth", block: "center" });
  window.clearTimeout(timelinePeriodFocusTimer);
  day.classList.add("timeline-period-focus");
  timelinePeriodFocusTimer = window.setTimeout(() => day.classList.remove("timeline-period-focus"), 5000);
}

function toggleAllTimelineSections(button) {
  const expanded = button.dataset.expanded === "true";
  $$("#rightTimeline details").forEach((details) => { details.open = !expanded; });
  scheduleTimelineExpansionControlsSync();
}

function handleTimelineDetailsToggle(event) {
  const details = event.target;
  if (!details.matches?.("details.right-timeline-year, details.right-timeline-month")) return;
  syncTimelinePeriodToggle(details);
  scheduleTimelineExpansionControlsSync();
}

function syncTimelinePeriodToggle(details) {
  const button = details.querySelector(':scope > summary [data-action="toggle-timeline-period"]');
  if (!button) return;
  const expanded = details.open;
  const label = details.dataset.periodLabel || "periodo";
  const action = expanded ? "Plegar" : "Desplegar";
  button.setAttribute("aria-expanded", String(expanded));
  button.setAttribute("aria-label", `${action} ${label}`);
  button.title = `${action} ${label}`;
}

function scheduleTimelineExpansionControlsSync() {
  window.cancelAnimationFrame(timelineControlsSyncFrame);
  timelineControlsSyncFrame = window.requestAnimationFrame(() => {
    const details = $$("#rightTimeline details.right-timeline-year, #rightTimeline details.right-timeline-month");
    details.forEach(syncTimelinePeriodToggle);
    const button = $('#rightTimeline [data-action="expand-timeline"]');
    if (!button) return;
    const allExpanded = Boolean(details.length && details.every((item) => item.open));
    button.dataset.expanded = String(allExpanded);
    $("span", button).textContent = allExpanded ? "Plegar todo" : "Desplegar todo";
    const iconName = allExpanded ? "fold-vertical" : "unfold-vertical";
    const icon = $("i, svg", button);
    if (icon?.getAttribute("data-lucide") !== iconName) {
      icon?.setAttribute("data-lucide", iconName);
      refreshIcons();
    }
  });
}

function focusClinicalPeriod(start, end, sourceElement = null) {
  if (!start) return;
  setActiveTab("historia");
  const targets = $$("#clinicalDocument [data-clinical-date]").filter((element) => {
    const date = element.dataset.clinicalDate || "";
    return date >= start && (!end || date <= end);
  });
  $$("#clinicalDocument .clinical-period-focus").forEach((element) => element.classList.remove("clinical-period-focus"));
  setTimelinePeriodSelection(sourceElement);
  targets.forEach((element) => element.classList.add("clinical-period-focus"));
  targets[0]?.scrollIntoView({ behavior: "smooth", block: "center" });
  window.clearTimeout(clinicalPeriodFocusTimer);
  clinicalPeriodFocusTimer = window.setTimeout(() => targets.forEach((element) => element.classList.remove("clinical-period-focus")), 5000);
}

function focusClinicalRecord(recordType, recordId, sourceElement = null) {
  if (!recordType || !recordId) return;
  setActiveTab("historia");
  const target = $(`#clinicalDocument [data-highlight-kind="record"][data-highlight-record-type="${CSS.escape(recordType)}"][data-highlight-record-id="${CSS.escape(recordId)}"]`);
  if (!target) {
    const fallbackDate = sourceElement?.dataset.clinicalDate || "";
    if (fallbackDate) focusClinicalPeriod(fallbackDate, fallbackDate, sourceElement);
    return;
  }
  $$("#clinicalDocument .clinical-period-focus").forEach((element) => element.classList.remove("clinical-period-focus"));
  setTimelinePeriodSelection(sourceElement);
  target.classList.add("clinical-period-focus");
  target.scrollIntoView({ behavior: "smooth", block: "center" });
  window.clearTimeout(clinicalPeriodFocusTimer);
  clinicalPeriodFocusTimer = window.setTimeout(() => target.classList.remove("clinical-period-focus"), 5000);
}

function setTimelinePeriodSelection(sourceElement) {
  $$("#rightTimeline .is-period-selected").forEach((element) => element.classList.remove("is-period-selected"));
  const selected = sourceElement?.closest?.(".right-timeline-year-head, .right-timeline-month-head, .right-timeline-item, .right-timeline-day");
  if (!selected) return;
  selected.classList.add("is-period-selected");
  if (selected.matches(".right-timeline-year-head, .right-timeline-month-head")) {
    timelineSelection = { kind: selected.matches(".right-timeline-year-head") ? "year" : "month", key: selected.dataset.periodKey || "" };
  } else if (selected.matches(".right-timeline-item")) {
    timelineSelection = { kind: "event", eventId: selected.dataset.timelineEventId || "" };
  } else {
    timelineSelection = { kind: "day", date: selected.dataset.clinicalDate || "" };
  }
}

function applyTimelinePeriodSelection() {
  if (!timelineSelection) return;
  let selected = null;
  if (["year", "month"].includes(timelineSelection.kind)) {
    const className = timelineSelection.kind === "year" ? ".right-timeline-year-head" : ".right-timeline-month-head";
    selected = $(`#rightTimeline ${className}[data-period-key="${CSS.escape(timelineSelection.key)}"]`);
  } else if (timelineSelection.kind === "event") {
    selected = $(`#rightTimeline .right-timeline-item[data-timeline-event-id="${CSS.escape(timelineSelection.eventId)}"]`);
  } else if (timelineSelection.kind === "day") {
    selected = $(`#rightTimeline .right-timeline-day[data-clinical-date="${CSS.escape(timelineSelection.date)}"]`);
  }
  selected?.classList.add("is-period-selected");
}

function getClinicalHighlightTextNodes(scope) {
  const walker = document.createTreeWalker(scope, NodeFilter.SHOW_TEXT);
  const nodes = [];
  while (walker.nextNode()) {
    const node = walker.currentNode;
    if (!node.nodeValue || !node.nodeValue.trim()) continue;
    const parent = node.parentElement;
    if (!parent || parent.closest("button, svg, style, script, .entry-audit, .section-audit, .section-actions, .evolution-line-actions, .clinical-record-milestone")) continue;
    if (parent.closest("[data-highlight-scope]") !== scope) continue;
    nodes.push(node);
  }
  return nodes;
}

function getClinicalHighlightScopeText(scope) {
  const nodes = getClinicalHighlightTextNodes(scope);
  const offsets = new Map();
  let text = "";
  nodes.forEach((node) => {
    offsets.set(node, text.length);
    text += node.nodeValue;
  });
  return { nodes, offsets, text };
}

function getSelectedTextNodeBounds(range, node) {
  let start = node === range.startContainer ? range.startOffset : 0;
  let end = node === range.endContainer ? range.endOffset : node.nodeValue.length;
  try {
    if (node !== range.startContainer) {
      while (start < end && range.comparePoint(node, start) < 0) start += 1;
    }
    if (node !== range.endContainer) {
      while (end > start && range.comparePoint(node, end) > 0) end -= 1;
    }
  } catch (error) {
    // Los limites directos cubren las selecciones habituales sobre nodos de texto.
  }
  return { start, end };
}

function captureClinicalSelection() {
  const root = $("#clinicalDocument");
  const selection = window.getSelection();
  if (!root || !selection || selection.rangeCount === 0 || selection.isCollapsed) return [];
  const range = selection.getRangeAt(0);
  if (!root.contains(range.commonAncestorContainer)) return [];

  const groups = new Map();
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    const node = walker.currentNode;
    if (!node.nodeValue || !node.nodeValue.trim()) continue;
    try {
      if (!range.intersectsNode(node)) continue;
    } catch (error) {
      continue;
    }
    const scope = node.parentElement?.closest("[data-highlight-scope]");
    if (!scope || !root.contains(scope)) continue;
    const canonical = groups.get(scope)?.canonical || getClinicalHighlightScopeText(scope);
    if (!canonical.offsets.has(node)) continue;
    const bounds = getSelectedTextNodeBounds(range, node);
    if (bounds.end <= bounds.start) continue;
    const base = canonical.offsets.get(node);
    const group = groups.get(scope) || { canonical, start: Infinity, end: -1, removeIds: new Set() };
    const persistentMark = node.parentElement?.closest("mark.clinical-text-highlight");
    if (persistentMark) {
      persistentMark.dataset.clinicalHighlightId?.split(/\s+/).filter(Boolean).forEach((id) => group.removeIds.add(id));
    }
    group.start = Math.min(group.start, base + bounds.start);
    group.end = Math.max(group.end, base + bounds.end);
    groups.set(scope, group);
  }

  return Array.from(groups.entries()).map(([scope, group]) => {
    let start = group.start;
    let end = group.end;
    while (start < end && /\s/.test(group.canonical.text[start])) start += 1;
    while (end > start && /\s/.test(group.canonical.text[end - 1])) end -= 1;
    const exact = group.canonical.text.slice(start, end).slice(0, 10000);
    end = start + exact.length;
    if (!exact.trim()) return null;
    return {
      id: makeId("clinical-highlight"),
      kind: scope.dataset.highlightKind === "record" ? "record" : "section",
      recordType: scope.dataset.highlightRecordType || "",
      recordId: scope.dataset.highlightRecordId || "",
      sectionKey: scope.dataset.highlightSectionKey || "",
      start,
      end,
      exact,
      prefix: group.canonical.text.slice(Math.max(0, start - 48), start),
      suffix: group.canonical.text.slice(end, end + 48),
      color: "yellow",
      createdAt: new Date().toISOString(),
      removeIds: Array.from(group.removeIds)
    };
  }).filter(Boolean);
}

function highlightSelectedClinicalText() {
  const selections = pendingClinicalSelection.length ? pendingClinicalSelection : captureClinicalSelection();
  pendingClinicalSelection = [];
  if (!selections.length) {
    toast("Seleccione texto dentro de la historia clinica");
    return;
  }

  state.meta.clinicalHighlights ||= [];
  let added = 0;
  selections.forEach((highlight) => {
    const duplicate = state.meta.clinicalHighlights.some((item) => !item.removedAt
      && item.kind === highlight.kind
      && item.recordType === highlight.recordType
      && item.recordId === highlight.recordId
      && item.sectionKey === highlight.sectionKey
      && item.exact === highlight.exact
      && item.start === highlight.start);
    if (duplicate) return;
    state.meta.clinicalHighlights.push(highlight);
    markClinicalHighlightTargetImportant(highlight);
    added += 1;
  });

  if (!added) {
    toast("Ese texto ya esta resaltado");
    return;
  }
  state.meta.updatedAt = new Date().toISOString();
  renderPreview();
  renderPatientOutputs();
  persistStateSilently();
  toast(added > 1 ? "Textos resaltados y eventos destacados" : "Texto resaltado y evento destacado");
}

function removeSelectedClinicalHighlight() {
  const selections = pendingClinicalSelection.length ? pendingClinicalSelection : captureClinicalSelection();
  pendingClinicalSelection = [];
  if (!selections.length) {
    toast("Seleccione texto resaltado en amarillo");
    return;
  }
  const removalIds = new Set(selections.flatMap((selection) => selection.removeIds || []));
  if (!removalIds.size) {
    toast("La seleccion no contiene un resaltado amarillo");
    return;
  }
  const previousCount = state.meta.clinicalHighlights?.length || 0;
  state.meta.clinicalHighlights = (state.meta.clinicalHighlights || []).filter((item) => !removalIds.has(item.id));
  const removed = previousCount - state.meta.clinicalHighlights.length;
  if (!removed) {
    toast("No se encontro el resaltado seleccionado");
    return;
  }
  state.meta.updatedAt = new Date().toISOString();
  renderPreview();
  renderPatientOutputs();
  persistStateSilently();
  toast(removed > 1 ? "Resaltados eliminados" : "Resaltado eliminado");
}

function getClinicalHighlightRecord(highlight) {
  const collections = {
    evolution: state.evolutions,
    study: state.studies,
    treatment: state.treatments,
    prescription: state.prescriptions,
    research: state.researchRecords
  };
  return collections[highlight.recordType]?.find((item) => String(item.id) === String(highlight.recordId)) || null;
}

function markClinicalHighlightTargetImportant(highlight) {
  state.meta.sectionMilestones ||= {};
  let record = null;
  if (highlight.kind === "record") {
    record = getClinicalHighlightRecord(highlight);
    if (record) {
      record.highlighted = true;
      record.updatedAt = new Date().toISOString();
    }
  } else if (highlight.sectionKey) {
    state.meta.sectionMilestones[highlight.sectionKey] = true;
  }
  syncTimelineMilestoneFromClinicalHighlight(highlight, record);
}

function syncTimelineMilestoneFromClinicalHighlight(highlight, record) {
  const events = Array.isArray(state.meta.aiTimelineEvents) ? state.meta.aiTimelineEvents : [];
  if (!events.length) return;
  const categoryMap = {
    evolution: ["evolution"],
    study: ["study", "pathology"],
    treatment: record ? [getTreatmentKind(record)] : ["radiotherapy", "surgery", "systemic"],
    research: ["research"],
    prescription: record?.type === "certificate" ? ["certificate"]
      : record?.type === "study" ? ["study_order"]
      : record?.type === "free" ? ["indication"] : ["prescription"]
  };
  const date = record?.date || String(record?.createdAt || "").slice(0, 10);
  const categories = categoryMap[highlight.recordType] || [];
  const selectedTokens = new Set(normalizeSearchText(highlight.exact).split(/\W+/).filter((token) => token.length > 3));
  const candidates = events.filter((event) => {
    if (date && event.date !== date) return false;
    return !categories.length || categories.includes(event.category);
  });
  candidates.forEach((event) => {
    const eventText = normalizeSearchText([event.title, event.body, event.sourceQuote].filter(Boolean).join(" "));
    const overlap = Array.from(selectedTokens).filter((token) => eventText.includes(token)).length;
    if (candidates.length === 1 || overlap >= Math.min(2, Math.max(1, selectedTokens.size))) {
      event.highlighted = true;
      if (record?.id) event.sourceRecordId ||= record.id;
    }
  });
}

function findClinicalHighlightScope(root, highlight) {
  if (highlight.kind === "record" && highlight.recordType && highlight.recordId) {
    return root.querySelector(`[data-highlight-scope][data-highlight-kind="record"][data-highlight-record-type="${CSS.escape(highlight.recordType)}"][data-highlight-record-id="${CSS.escape(highlight.recordId)}"]`);
  }
  if (highlight.sectionKey) {
    return root.querySelector(`[data-highlight-scope][data-highlight-kind="section"][data-highlight-section-key="${CSS.escape(highlight.sectionKey)}"]`);
  }
  return null;
}

function resolveClinicalHighlightRange(text, highlight) {
  if (text.slice(highlight.start, highlight.end) === highlight.exact) return { start: highlight.start, end: highlight.end };
  const occurrences = [];
  let index = text.indexOf(highlight.exact);
  while (index !== -1) {
    const prefix = text.slice(Math.max(0, index - highlight.prefix.length), index);
    const suffix = text.slice(index + highlight.exact.length, index + highlight.exact.length + highlight.suffix.length);
    let score = -Math.abs(index - highlight.start) / Math.max(text.length, 1);
    if (highlight.prefix && prefix.endsWith(highlight.prefix)) score += 3;
    if (highlight.suffix && suffix.startsWith(highlight.suffix)) score += 3;
    occurrences.push({ start: index, end: index + highlight.exact.length, score });
    index = text.indexOf(highlight.exact, index + 1);
  }
  return occurrences.sort((a, b) => b.score - a.score)[0] || null;
}

function renderPersistentClinicalHighlights(root) {
  const highlights = (state?.meta?.clinicalHighlights || []).filter((item) => !item.removedAt);
  if (!root || !highlights.length) return;
  const byScope = new Map();
  highlights.forEach((highlight) => {
    const scope = findClinicalHighlightScope(root, highlight);
    if (!scope) return;
    const canonical = byScope.get(scope)?.canonical || getClinicalHighlightScopeText(scope);
    const resolved = resolveClinicalHighlightRange(canonical.text, highlight);
    if (!resolved) return;
    const group = byScope.get(scope) || { canonical, ranges: [] };
    group.ranges.push({ ...resolved, id: highlight.id });
    byScope.set(scope, group);
  });

  byScope.forEach(({ canonical, ranges }) => {
    const merged = ranges.sort((a, b) => a.start - b.start || a.end - b.end).reduce((result, range) => {
      const previous = result[result.length - 1];
      if (previous && range.start <= previous.end) {
        previous.end = Math.max(previous.end, range.end);
        previous.ids.push(range.id);
      } else {
        result.push({ start: range.start, end: range.end, ids: [range.id] });
      }
      return result;
    }, []);

    canonical.nodes.forEach((node) => {
      const nodeStart = canonical.offsets.get(node);
      const nodeEnd = nodeStart + node.nodeValue.length;
      const overlaps = merged.filter((range) => range.start < nodeEnd && range.end > nodeStart);
      if (!overlaps.length) return;
      const fragment = document.createDocumentFragment();
      let cursor = 0;
      overlaps.forEach((range) => {
        const start = Math.max(0, range.start - nodeStart);
        const end = Math.min(node.nodeValue.length, range.end - nodeStart);
        if (start > cursor) fragment.appendChild(document.createTextNode(node.nodeValue.slice(cursor, start)));
        const mark = document.createElement("mark");
        mark.className = "clinical-text-highlight";
        mark.dataset.clinicalHighlightId = range.ids.join(" ");
        mark.textContent = node.nodeValue.slice(start, end);
        fragment.appendChild(mark);
        cursor = end;
      });
      if (cursor < node.nodeValue.length) fragment.appendChild(document.createTextNode(node.nodeValue.slice(cursor)));
      node.replaceWith(fragment);
    });
  });
}

function renderPreviewLegacy() {
  const preview = $("#clinicalPreview");
  const latestStudies = [...state.studies]
    .sort((a, b) => (b.date || "").localeCompare(a.date || ""))
    .slice(0, 5);
  const latestEvolutions = [...state.evolutions]
    .sort((a, b) => (b.date || "").localeCompare(a.date || ""))
    .slice(0, 4);

  preview.innerHTML = `
    <h3>${escapeHtml(state.patient.fullName || "Paciente sin nombre")}</h3>
    <p><strong>HC:</strong> ${escapeHtml(state.patient.medicalRecord || "-")} - <strong>DNI:</strong> ${escapeHtml(state.patient.dni || "-")} - <strong>OS:</strong> ${escapeHtml(state.patient.insurance || "-")}</p>
    <h4>Diagnostico</h4>
    <p>${escapeHtml(state.oncology.diagnosis || "-")} ${state.oncology.stage ? `- ${escapeHtml(state.oncology.stage)}` : ""}</p>
    <p>${escapeHtml(state.oncology.histology || "")}</p>
    <p>${escapeHtml(state.oncology.biomarkers || "")}</p>
    <h4>Enfermedad actual</h4>
    <p>${escapeHtml(state.narrative.currentIllness || "-")}</p>
    <h4>Antecedentes</h4>
    <p>${escapeHtml(state.narrative.backgroundClinical || "-")}</p>
    <p>${escapeHtml(state.narrative.currentMedication || "")}</p>
    <h4>Tratamientos sistemicos</h4>
    ${renderPreviewTreatments()}
    <h4>Estudios recientes</h4>
    ${latestStudies.length ? latestStudies.map((study) => `<p><strong>${formatDate(study.date)} ${escapeHtml(study.type)}:</strong> ${escapeHtml(study.title)}. ${escapeHtml(study.summary || "")}</p>`).join("") : "<p>-</p>"}
    <h4>Resumen / conducta</h4>
    <p>${escapeHtml(state.narrative.summary || "-")}</p>
    <p>${escapeHtml(state.narrative.plan || "")}</p>
    <h4>Evoluciones</h4>
    ${latestEvolutions.length ? latestEvolutions.map((entry) => `<p><strong>${formatDate(entry.date)} ${escapeHtml(entry.author || "")}:</strong> ${escapeHtml(entry.text)}</p>`).join("") : "<p>-</p>"}
  `;
}

function renderPreviewTreatmentsLegacy() {
  const treatments = [...state.treatments].sort((a, b) => (b.date || "").localeCompare(a.date || ""));
  if (!treatments.length) return "<p>-</p>";
  return treatments.map((item) => `<p><strong>${formatDate(item.date)}:</strong> ${escapeHtml(item.scheme)} - ${escapeHtml(item.status || "")}. ${escapeHtml(item.notes || "")}</p>`).join("");
}

function renderPreview() {
  synchronizeDiagnosticClassificationUiWithState();
  $("#clinicalDocument").innerHTML = buildClinicalDocumentHtml({ editable: true });
  $("#clinicalPreview").innerHTML = buildClinicalDocumentHtml({ editable: false });
  renderPersistentClinicalHighlights($("#clinicalDocument"));
  renderPersistentClinicalHighlights($("#clinicalPreview"));
  renderRightTimeline();
  renderRememberedAgentHighlights([$("#clinicalDocument")]);
  refreshIcons();
  ensureDiagnosticClassificationCatalogs();
  ensureDiagnosticAjccSite();
  ensureDiagnosticDisplaySettings();
  window.requestAnimationFrame(updateClinicalScrollEndButton);
}

function renderEditableSectionBody(sectionKey, defaultHtml, emptyMessage, editable) {
  const rendered = renderSectionHtml(sectionKey, defaultHtml);
  if (hasText(stripHtml(rendered)) || !editable || !isLocalClinicalHistory() ||
      !supportsInlineSectionLoad(sectionKey)) {
    return rendered;
  }
  return `
    <div class="clinical-section-empty">
      <span>${escapeHtml(emptyMessage || "Sin datos cargados.")}</span>
      <button class="ghost-button" type="button" data-action="edit-section" data-section-key="${escapeAttr(sectionKey)}">
        <i data-lucide="plus"></i>
        <span>Cargar</span>
      </button>
    </div>
  `;
}

function emptyDiagnosticClassifications() {
  return Object.fromEntries(DIAGNOSTIC_CLASSIFICATION_SYSTEMS.map((system) => [
    system,
    { system: DIAGNOSTIC_CLASSIFICATION_LABELS[system], freeText: "" }
  ]));
}

function normalizeDiagnosticClassificationRecord(value, system) {
  const sourceValue = value && typeof value === "object" && !Array.isArray(value) ? value : {};
  return {
    system: DIAGNOSTIC_CLASSIFICATION_LABELS[system],
    freeText: String(sourceValue.freeText ?? sourceValue.queryText ?? "").trim().slice(0, 2000),
    code: String(sourceValue.code || "").trim().slice(0, 80),
    display: String(sourceValue.display || "").trim().slice(0, 500),
    version: String(sourceValue.version || "").trim().slice(0, 160),
    source: String(sourceValue.source || "").trim().slice(0, 240),
    sourceConceptId: String(sourceValue.sourceConceptId || "").trim().slice(0, 80),
    sourceDisplay: String(sourceValue.sourceDisplay || "").trim().slice(0, 500),
    mapAdvice: String(sourceValue.mapAdvice || "").trim().slice(0, 500)
  };
}

function normalizeOncologyTnm(value) {
  const source = value && typeof value === "object" && !Array.isArray(value) ? value : {};
  const rawValues = source.values && typeof source.values === "object" && !Array.isArray(source.values)
    ? source.values
    : {};
  const values = Object.fromEntries(
    Object.entries(rawValues)
      .filter(([key]) => /^[A-Za-z][A-Za-z0-9_]{0,39}$/.test(key))
      .map(([key, entry]) => [key, String(entry ?? "").trim().slice(0, 160)])
  );
  const t = String(source.t || values.T || "").trim().slice(0, 40);
  const n = String(source.n || values.N || "").trim().slice(0, 40);
  const m = String(source.m || values.M || "").trim().slice(0, 40);
  const rawSourceRow = source.sourceRow;
  const sourceRowNumber = Number(rawSourceRow);
  const sourceRow = rawSourceRow !== null
    && rawSourceRow !== undefined
    && rawSourceRow !== ""
    && Number.isSafeInteger(sourceRowNumber)
    && sourceRowNumber > 0
      ? sourceRowNumber
      : null;
  return {
    t,
    n,
    m,
    stage: String(source.stage || source.stageGroup || "").trim().slice(0, 120),
    substage: String(source.substage || "").trim().slice(0, 120),
    siteId: String(source.siteId || "").trim().slice(0, 80),
    siteDisplay: String(source.siteDisplay || "").trim().slice(0, 500),
    prefix: ["c", "p", "yc", "yp", "r"].includes(String(source.prefix || ""))
      ? String(source.prefix)
      : "c",
    date: /^\d{4}-\d{2}-\d{2}$/.test(String(source.date || "")) ? String(source.date) : "",
    edition: String(source.edition || "AJCC 8").trim().slice(0, 80),
    source: String(source.source || "").trim().slice(0, 240),
    guideVersion: String(source.guideVersion || "").trim().slice(0, 160),
    sourceRow,
    calculatedAt: String(source.calculatedAt || "").trim().slice(0, 80),
    values: { ...values, T: t, N: n, M: m }
  };
}

function diagnosticSnapshotIsComplete(value = {}) {
  const classifications = normalizeDiagnosticClassifications(value.diagnosticClassifications);
  const tnm = normalizeOncologyTnm(value.tnm);
  const conceptsComplete = ["ajcc", "snomed", "cie10"].every((system) =>
    hasText(classifications[system]?.code) && hasText(classifications[system]?.display)
  );
  const tnmComplete = [
    tnm.siteId,
    tnm.prefix,
    tnm.date,
    tnm.t,
    tnm.n,
    tnm.m,
    tnm.stage
  ].every(hasText);
  return conceptsComplete && tnmComplete &&
    String(tnm.siteId).toLocaleLowerCase("en") ===
      String(classifications.ajcc.code).toLocaleLowerCase("en");
}

function diagnosisRecordFingerprint(value = {}) {
  const classifications = normalizeDiagnosticClassifications(value.diagnosticClassifications);
  const tnm = normalizeOncologyTnm(value.tnm);
  return hashTimelineSource(JSON.stringify(canonicalDiagnosisFingerprintValue({
    diagnosis: String(value.diagnosis || "").trim(),
    topography: String(value.topography || "").trim(),
    histology: String(value.histology || "").trim(),
    stage: String(value.stage || "").trim(),
    date: String(value.date || tnm.date || "").trim(),
    ajcc: classifications.ajcc,
    snomed: classifications.snomed,
    cie10: classifications.cie10,
    tnm
  })));
}

function canonicalDiagnosisFingerprintValue(value) {
  if (Array.isArray(value)) return value.map(canonicalDiagnosisFingerprintValue);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(
    Object.keys(value)
      .sort((left, right) => left.localeCompare(right, "en"))
      .map((key) => [key, canonicalDiagnosisFingerprintValue(value[key])])
  );
}

function normalizeDiagnosisRecord(value, { legacyProjection = false } = {}) {
  const source = value && typeof value === "object" && !Array.isArray(value) ? value : {};
  const diagnosticClassifications = normalizeDiagnosticClassifications(
    source.diagnosticClassifications
  );
  const tnm = normalizeOncologyTnm(source.tnm);
  const resolvedLegacyProjection = Boolean(
    source.legacyProjection || legacyProjection ||
    String(source.id || "").startsWith("diagnosis-legacy-")
  );
  const date = /^\d{4}-\d{2}-\d{2}$/.test(String(source.date || ""))
    ? String(source.date)
    : tnm.date;
  const diagnosis = String(
    source.diagnosis || diagnosticClassifications.snomed.display ||
    diagnosticClassifications.ajcc.display || ""
  ).trim().slice(0, 1000);
  const topography = String(
    source.topography || tnm.siteDisplay || diagnosticClassifications.ajcc.display || ""
  ).trim().slice(0, 1000);
  const histology = String(source.histology || "").trim().slice(0, 1000);
  const stage = String(source.stage || tnm.stage || "").trim().slice(0, 120);
  const fingerprint = diagnosisRecordFingerprint({
    date,
    diagnosis,
    topography,
    histology,
    stage,
    diagnosticClassifications,
    tnm
  });
  const id = String(source.id || (
    resolvedLegacyProjection
      ? `diagnosis-legacy-${fingerprint}`
      : `diagnosis-${fingerprint}`
  )).trim().slice(0, 180);
  const audit = source.audit && typeof source.audit === "object" && !Array.isArray(source.audit)
    ? { ...source.audit }
    : null;
  return {
    id,
    date,
    datePrecision: normalizeDatePrecision(source.datePrecision),
    diagnosis,
    topography,
    histology,
    stage,
    diagnosticClassifications,
    tnm,
    legacyProjection: resolvedLegacyProjection,
    audit,
    createdAt: String(source.createdAt || audit?.at || "").trim().slice(0, 80)
  };
}

function legacyDiagnosisRecordFromOncology(oncology = {}) {
  const classifications = normalizeDiagnosticClassifications(oncology.diagnosticClassifications);
  const rawTnm = oncology.tnm && typeof oncology.tnm === "object" && !Array.isArray(oncology.tnm)
    ? oncology.tnm
    : {};
  const hasLegacyContent = [
    oncology.diagnosis,
    oncology.diagnosisDate,
    oncology.topography,
    oncology.histology,
    oncology.stage,
    ...Object.values(classifications).flatMap((value) => [
      value.freeText,
      value.code,
      value.display
    ]),
    rawTnm.siteId,
    rawTnm.siteDisplay,
    rawTnm.date,
    rawTnm.t,
    rawTnm.n,
    rawTnm.m,
    rawTnm.stage,
    rawTnm.stageGroup,
    rawTnm.sourceRow
  ].some(hasText);
  if (!hasLegacyContent) return null;
  const candidate = normalizeDiagnosisRecord({
    date: oncology.diagnosisDate || oncology.tnm?.date,
    datePrecision: oncology.diagnosisDatePrecision,
    diagnosis: oncology.diagnosis,
    topography: oncology.topography,
    histology: oncology.histology,
    stage: oncology.stage,
    diagnosticClassifications: oncology.diagnosticClassifications,
    tnm: oncology.tnm,
    createdAt: oncology.diagnosisCreatedAt || "",
    legacyProjection: true
  }, { legacyProjection: true });
  return candidate;
}

function diagnosisRecordHasMeaningfulContent(record) {
  const normalized = normalizeDiagnosisRecord(record);
  return [
    normalized.diagnosis,
    normalized.date,
    normalized.topography,
    normalized.histology,
    normalized.stage,
    ...Object.values(normalized.diagnosticClassifications).flatMap((value) => [
      value.freeText,
      value.code,
      value.display
    ]),
    normalized.tnm.siteId,
    normalized.tnm.siteDisplay,
    normalized.tnm.date,
    normalized.tnm.t,
    normalized.tnm.n,
    normalized.tnm.m,
    normalized.tnm.stage,
    normalized.tnm.sourceRow
  ].some(hasText);
}

function normalizeDiagnosisRecords(value, oncology = {}) {
  const sourceRecords = Array.isArray(value) ? value : [];
  const candidates = sourceRecords.length
    ? sourceRecords.map((item) => normalizeDiagnosisRecord(item))
    : [legacyDiagnosisRecordFromOncology(oncology)].filter(Boolean);
  const unique = new Map();
  candidates.forEach((record) => {
    const preservableLegacy = record.legacyProjection && diagnosisRecordHasMeaningfulContent(record);
    if (!record.id ||
        (!diagnosticSnapshotIsComplete(record) && !preservableLegacy) ||
        unique.has(record.id)) return;
    unique.set(record.id, record);
  });
  return [...unique.values()];
}

function getDiagnosisRecords() {
  state.oncology.diagnosisRecords = normalizeDiagnosisRecords(
    state.oncology.diagnosisRecords,
    state.oncology
  );
  return state.oncology.diagnosisRecords;
}

function diagnosisRecordProjectionKey(record) {
  if (record?.legacyProjection) return `legacy:${LEGACY_DIAGNOSIS_PROJECTION_KEY}`;
  return record?.id ? `entry:${record.id}` : "";
}

function diagnosisRecordPlainText(record) {
  const normalized = normalizeDiagnosisRecord(record);
  const classifications = normalized.diagnosticClassifications;
  const tnm = normalized.tnm;
  let t = tnm.t;
  if (t && !/^(?:c|p|yc|yp|r)T/i.test(t) && tnm.prefix) t = `${tnm.prefix}${t}`;
  const tnmText = [t, tnm.n, tnm.m]
    .filter(hasText)
    .join(" ");
  const diagnosis = normalized.diagnosis || classifications.snomed.display;
  return [
    diagnosis && `Diagnóstico oncológico: ${diagnosis}.`,
    normalized.topography && `Topografía: ${normalized.topography}.`,
    normalized.histology && `Histología: ${normalized.histology}.`,
    (classifications.snomed.code || classifications.snomed.display) &&
      `SNOMED CT${classifications.snomed.code ? ` ${classifications.snomed.code}` : ""}${classifications.snomed.display ? `: ${classifications.snomed.display}` : ""}.`,
    (classifications.cie10.code || classifications.cie10.display) &&
      `CIE-10${classifications.cie10.code ? ` ${classifications.cie10.code}` : ""}${classifications.cie10.display ? `: ${classifications.cie10.display}` : ""}.`,
    classifications.ajcc.display && `AJCC: ${classifications.ajcc.display}.`,
    tnmText && `TNM ${tnmText}.`,
    (tnm.stage || normalized.stage) && `Estadio ${tnm.stage || normalized.stage}.`
  ].filter(hasText).join(" ");
}

function renderDiagnosisRecordsPlainText(records = getDiagnosisRecords()) {
  return [...records]
    .sort((left, right) => {
      const leftKey = [left.date || "", left.createdAt || ""].join("|");
      const rightKey = [right.date || "", right.createdAt || ""].join("|");
      return leftKey.localeCompare(rightKey);
    })
    .map((record) => [
      formatDateOptional(record.date, record.datePrecision),
      diagnosisRecordPlainText(record)
    ].filter(hasText).join(" - "))
    .join("\n\n");
}

function hasDiagnosticTnm(value = state.oncology?.tnm) {
  const tnm = normalizeOncologyTnm(value);
  return [tnm.siteId, tnm.t, tnm.n, tnm.m, tnm.stage].some(hasText);
}

function diagnosticTnmText(value = state.oncology?.tnm) {
  const tnm = normalizeOncologyTnm(value);
  let t = tnm.t;
  if (t && !/^(?:c|p|yc|yp|r)T/i.test(t) && tnm.prefix) t = `${tnm.prefix}${t}`;
  return [
    [t, tnm.n, tnm.m].filter(hasText).join(" "),
    tnm.stage && `Estadio ${tnm.stage}`
  ].filter(hasText).join(" · ");
}

function diagnosticPatientKey() {
  return [
    state.patient?.liraId,
    state.patient?.dni,
    state.patient?.medicalRecord,
    state.patient?.fullName
  ].map((item) => String(item || "").trim()).join("|");
}

function resetDiagnosticAjccUiFromState() {
  const tnm = normalizeOncologyTnm(state.oncology?.tnm);
  diagnosticAjccUi = {
    site: null,
    siteId: tnm.siteId,
    loading: false,
    error: "",
    requestId: diagnosticAjccUi.requestId + 1,
    values: {
      prefix: tnm.prefix || "c",
      date: tnm.date || today(),
      ...tnm.values,
      T: tnm.t,
      N: tnm.n,
      M: tnm.m
    },
    stage: tnm.stage,
    stageEdited: false,
    sourceRow: tnm.sourceRow,
    calculating: false,
    calculationError: "",
    stageRequestId: diagnosticAjccUi.stageRequestId + 1
  };
}

function getVisibleDiagnosticSystems() {
  const selected = DIAGNOSTIC_CLASSIFICATION_SYSTEMS.filter((system) =>
    diagnosticVisibleSystems.includes(system)
  );
  return selected.length ? selected : [...DIAGNOSTIC_CLASSIFICATION_SYSTEMS];
}

function synchronizeDiagnosticClassificationUiWithState() {
  const patientKey = diagnosticPatientKey();
  if (diagnosticClassificationUi.patientKey === patientKey) return;
  const values = getDiagnosticClassifications();
  diagnosticClassificationUi.patientKey = patientKey;
  diagnosticClassificationUi.selectionRequestId += 1;
  diagnosticClassificationUi.activeSystem = "ajcc";
  DIAGNOSTIC_CLASSIFICATION_SYSTEMS.forEach((system) => {
    diagnosticClassificationUi.drafts[system] = normalizeDiagnosticClassificationRecord(
      values[system],
      system
    );
    diagnosticClassificationUi.queries[system] = "";
    diagnosticClassificationUi.results[system] = [];
    diagnosticClassificationUi.loadedQuery[system] = null;
    diagnosticClassificationUi.loading[system] = false;
    diagnosticClassificationUi.errors[system] = "";
    diagnosticClassificationUi.requestId[system] += 1;
  });
  resetDiagnosticAjccUiFromState();
}

function prepareDiagnosticClassificationDraftForNewEntry() {
  invalidateDiagnosticClassificationDraft();
  diagnosticClassificationDraftEntryId = makeId("diagnosis");
  diagnosticClassificationUi.patientKey =
    `${diagnosticPatientKey()}|new:${diagnosticClassificationDraftEntryId}`;
  diagnosticClassificationUi.selectionRequestId += 1;
  diagnosticClassificationUi.activeSystem = "ajcc";
  DIAGNOSTIC_CLASSIFICATION_SYSTEMS.forEach((system) => {
    diagnosticClassificationUi.drafts[system] = normalizeDiagnosticClassificationRecord(
      {},
      system
    );
    diagnosticClassificationUi.queries[system] = "";
    diagnosticClassificationUi.results[system] = [];
    diagnosticClassificationUi.loadedQuery[system] = null;
    diagnosticClassificationUi.loading[system] = false;
    diagnosticClassificationUi.errors[system] = "";
    diagnosticClassificationUi.requestId[system] += 1;
  });
  diagnosticAjccUi = {
    site: null,
    siteId: "",
    loading: false,
    error: "",
    requestId: diagnosticAjccUi.requestId + 1,
    values: { prefix: "c", date: today(), T: "", N: "", M: "" },
    stage: "",
    stageEdited: false,
    sourceRow: null,
    calculating: false,
    calculationError: "",
    stageRequestId: diagnosticAjccUi.stageRequestId + 1
  };
}

function normalizeDiagnosticSynonymText(value) {
  return normalizeSearchText(value)
    .replace(/\b(?:tumor|neoplasia)\s+malign[oa]s?\b/g, " carcinoma ")
    .replace(/\bcancer\b/g, " carcinoma ")
    .replace(/\s+/g, " ")
    .trim();
}

function diagnosticSearchTerms(value) {
  return [...new Set(normalizeDiagnosticSynonymText(value).split(/\s+/).filter(Boolean))];
}

async function loadDiagnosticDisplaySettings({ force = false } = {}) {
  if (diagnosticDisplaySettingsLoading) return diagnosticDisplaySettingsLoading;
  if (diagnosticDisplaySettingsLoaded && !force) return diagnosticVisibleSystems;
  diagnosticDisplaySettingsLoading = (async () => {
    try {
      const response = await fetch("/api/clinical/configuration/diagnosis-setting?includeInactive=0", {
        cache: "no-store",
        headers: { Accept: "application/json" }
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.error || "No se pudo leer la configuración diagnóstica.");
      const items = Array.isArray(payload.items) ? payload.items : [];
      const item = items.find((entry) => entry?.key === "diagnosis-display") || items[0];
      const requested = Array.isArray(item?.definition?.visibleSystems)
        ? item.definition.visibleSystems
        : DIAGNOSTIC_CLASSIFICATION_SYSTEMS;
      const next = DIAGNOSTIC_CLASSIFICATION_SYSTEMS.filter((system) => requested.includes(system));
      const normalized = next.length ? next : [...DIAGNOSTIC_CLASSIFICATION_SYSTEMS];
      const changed = normalized.join("|") !== diagnosticVisibleSystems.join("|");
      diagnosticVisibleSystems = normalized;
      diagnosticDisplaySettingsLoaded = true;
      if (changed && $("#clinicalDocument")) applyDiagnosticDisplaySettingsToWorkspace();
    } catch {
      diagnosticDisplaySettingsLoaded = true;
    } finally {
      diagnosticDisplaySettingsLoading = null;
    }
    return diagnosticVisibleSystems;
  })();
  return diagnosticDisplaySettingsLoading;
}

function ensureDiagnosticDisplaySettings() {
  if (!diagnosticDisplaySettingsLoaded && !diagnosticDisplaySettingsLoading) {
    void loadDiagnosticDisplaySettings();
  }
}

function normalizeDiagnosticClassifications(value) {
  const source = value && typeof value === "object" && !Array.isArray(value) ? value : {};
  return Object.fromEntries(DIAGNOSTIC_CLASSIFICATION_SYSTEMS.map((system) => [
    system,
    normalizeDiagnosticClassificationRecord(source[system], system)
  ]));
}

function getDiagnosticClassifications() {
  state.oncology.diagnosticClassifications = normalizeDiagnosticClassifications(
    state.oncology.diagnosticClassifications
  );
  return state.oncology.diagnosticClassifications;
}

function getDiagnosticDraftClassifications() {
  const persisted = getDiagnosticClassifications();
  DIAGNOSTIC_CLASSIFICATION_SYSTEMS.forEach((system) => {
    if (!diagnosticClassificationUi.drafts[system]) {
      diagnosticClassificationUi.drafts[system] = normalizeDiagnosticClassificationRecord(
        persisted[system],
        system
      );
    }
  });
  return diagnosticClassificationUi.drafts;
}

function hasDiagnosticClassification(value) {
  return Boolean(value && (
    hasText(value.freeText)
    || hasText(value.code)
    || hasText(value.display)
  ));
}

function hasAnyDiagnosticClassification() {
  const values = getDiagnosticClassifications();
  const visibleSystems = getVisibleDiagnosticSystems();
  return visibleSystems.some((system) => hasDiagnosticClassification(values[system]))
    || (visibleSystems.includes("ajcc") && hasDiagnosticTnm());
}

function diagnosticClassificationSelectionText(value, system) {
  if (!value || (!hasText(value.code) && !hasText(value.display))) return "";
  if (system === "ajcc") return String(value.display || value.code || "");
  if (system === "cie10") {
    return [
      value.code && `CIE-10 ${value.code}`,
      (value.sourceDisplay || value.display) && `mapeado desde ${value.sourceDisplay || value.display}`
    ].filter(hasText).join(" · ");
  }
  return [value.code, value.display].filter(hasText).join(" · ");
}

function diagnosticClassificationText(value, { includeSystem = false, system = "" } = {}) {
  if (!hasDiagnosticClassification(value)) return "";
  const resolvedSystem = system || (
    value.system === DIAGNOSTIC_CLASSIFICATION_LABELS.cie10
      ? "cie10"
      : value.system === DIAGNOSTIC_CLASSIFICATION_LABELS.ajcc
        ? "ajcc"
        : "snomed"
  );
  const distinctFreeText = hasText(value.freeText)
    && normalizeSearchText(value.freeText) !== normalizeSearchText(value.display || "");
  const label = [
    distinctFreeText && `Texto libre: ${value.freeText}`,
    diagnosticClassificationSelectionText(value, resolvedSystem)
  ].filter(Boolean).join(" · ");
  return includeSystem ? `${value.system || ""}: ${label}`.trim() : label;
}

function renderDiagnosticClassificationSummary({ editable = false, record = null } = {}) {
  const normalizedRecord = record ? normalizeDiagnosisRecord(record) : null;
  const values = normalizedRecord
    ? normalizedRecord.diagnosticClassifications
    : editable
      ? getDiagnosticDraftClassifications()
      : getDiagnosticClassifications();
  const tnmValue = normalizedRecord
    ? normalizedRecord.tnm
    : editable
      ? collectDiagnosticAjccState()
      : state.oncology?.tnm;
  const entries = getVisibleDiagnosticSystems()
    .filter((system) =>
      hasDiagnosticClassification(values[system])
      || (system === "ajcc" && hasDiagnosticTnm(tnmValue))
    )
    .map((system) => {
      const value = values[system];
      const tnmText = system === "ajcc"
        ? diagnosticTnmText(tnmValue)
        : "";
      return `
        <div class="diagnostic-classification-summary-row">
          <dt>${escapeHtml(DIAGNOSTIC_CLASSIFICATION_LABELS[system])}</dt>
          <dd>
            ${hasDiagnosticClassification(value)
              ? `<strong>${escapeHtml(diagnosticClassificationText(value, { system }))}</strong>`
              : ""}
            ${hasText(tnmText) ? `<strong class="diagnostic-classification-summary-tnm">${escapeHtml(tnmText)}</strong>` : ""}
            ${hasText(value.version || value.source) ? `<small>${escapeHtml([value.version, value.source].filter(hasText).join(" · "))}</small>` : ""}
          </dd>
        </div>
      `;
    });
  if (!entries.length) return "";
  return `<dl class="diagnostic-classification-summary">${entries.join("")}</dl>`;
}

function diagnosticClassificationStatus(system) {
  const selected = getDiagnosticDraftClassifications()[system];
  if (diagnosticClassificationUi.loading[system]) return { text: "Buscando coincidencias...", kind: "loading" };
  if (diagnosticClassificationUi.errors[system]) return { text: diagnosticClassificationUi.errors[system], kind: "error" };
  if (system !== "ajcc" && !hasText(getDiagnosticDraftClassifications().ajcc?.code)) {
    return { text: "Seleccione primero el sitio AJCC.", kind: "hint" };
  }
  if (hasText(selected?.code) && hasText(selected?.display)) {
    return { text: "Selección completa.", kind: "ready" };
  }
  const count = diagnosticClassificationUi.results[system].length;
  if (!count) {
    return {
      text: system === "ajcc"
        ? "Cargando sitios AJCC locales..."
        : "No hay una equivalencia configurada para este sitio AJCC.",
      kind: system === "ajcc" ? "loading" : "empty"
    };
  }
  if (system === "ajcc") {
    return {
      text: `${count} sitios AJCC disponibles. Seleccione el sitio primario.`,
      kind: "hint"
    };
  }
  return {
    text: count === 1
      ? "Equivalencia propuesta. Confirme la opción."
      : `${count} equivalencias disponibles. Seleccione la correcta.`,
    kind: "hint"
  };
}

function diagnosticMatchesQuery(item, query) {
  const terms = diagnosticSearchTerms(query);
  if (!terms.length) return true;
  const haystack = normalizeDiagnosticSynonymText([
    item?.code,
    item?.display,
    item?.group,
    item?.sourceConceptId,
    item?.sourceDisplay
  ].filter(hasText).join(" "));
  return terms.every((term) => haystack.includes(term));
}

function diagnosticCatalogItemKey(item) {
  return encodeURIComponent([
    item?.code || "",
    item?.sourceConceptId || "",
    item?.display || ""
  ].join("\u0000"));
}

function diagnosticClassificationSelectOptions(system) {
  const selected = getDiagnosticDraftClassifications()[system];
  const results = diagnosticClassificationUi.results[system];
  const items = [...results];
  if ((hasText(selected.code) || hasText(selected.display)) &&
      !items.some((item) => String(item.code) === String(selected.code))) {
    items.unshift(selected);
  }
  if (!items.length) {
    return `<option value="" disabled>${diagnosticClassificationUi.loading[system]
      ? "Cargando..."
      : system === "ajcc"
        ? "Catálogo AJCC no disponible"
        : "Seleccione primero el sitio AJCC"}</option>`;
  }
  const options = items.map((item) => {
    const code = String(item.code || "");
    const selectedItem = (hasText(selected.code) || hasText(selected.display)) &&
      String(selected.code || "") === code &&
      String(selected.display || "") === String(item.display || "");
    const label = system === "cie10"
      ? [
          code && `CIE-10 ${code}`,
          (item.sourceDisplay || item.display) && `— mapeado desde ${item.sourceDisplay || item.display}`
        ].filter(hasText).join(" ")
      : system === "ajcc"
        ? [
            item.group,
            item.display || code
          ].filter(hasText).join(" \u2013 ")
      : [
          code,
          item.display,
          item.group && `— ${item.group}`
        ].filter(hasText).join(" ");
    return `<option value="${escapeAttr(diagnosticCatalogItemKey(item))}"${selectedItem ? " selected" : ""}>${escapeHtml(label)}</option>`;
  }).join("");
  return `<option value="">Seleccione ${escapeHtml(DIAGNOSTIC_CLASSIFICATION_LABELS[system])}</option>${options}`;
}

function diagnosticAjccAxisCategories(axisKey) {
  if (!diagnosticAjccUi.site) return [];
  let categories = diagnosticAjccUi.site.axes?.[axisKey]?.categories || [];
  if (axisKey === "T" && categories.some((item) => /^[cp]T/.test(item.code))) {
    const classification = String(diagnosticAjccUi.values.prefix || "c").includes("p") ? "p" : "c";
    categories = categories.filter((item) => String(item.code || "").startsWith(`${classification}T`));
  }
  return categories;
}

function renderDiagnosticAjccAxis(axisKey, axis, { compact = false } = {}) {
  const selected = String(diagnosticAjccUi.values[axisKey] || "");
  const categories = diagnosticAjccAxisCategories(axisKey);
  const definition = categories.find((item) => String(item.code || "") === selected);
  const axisLabel = String(axis?.label || axisKey);
  const selectionDetail = definition
    ? `${definition.code} = ${definition.description || "Sin descripción adicional"}`
    : "Seleccione una categoría";
  return `
    <label class="${compact ? "diagnostic-ajcc-factor" : "diagnostic-ajcc-axis"}">
      <span>${escapeHtml(axisKey)}</span>
      <select data-diagnostic-ajcc-axis="${escapeAttr(axisKey)}" aria-label="${escapeAttr(axisLabel)}">
        <option value="">Seleccione</option>
        ${categories.map((item) => `
          <option value="${escapeAttr(item.code)}"${String(item.code) === selected ? " selected" : ""}>
            ${escapeHtml(item.code)}
          </option>
        `).join("")}
      </select>
      <small class="diagnostic-ajcc-axis-detail">
        <strong>${escapeHtml(axisLabel)}</strong>
        <span>${escapeHtml(selectionDetail)}</span>
      </small>
    </label>
  `;
}

function renderDiagnosticAjccStaging() {
  const selected = getDiagnosticDraftClassifications().ajcc;
  if (!hasText(selected.code)) {
    return `
      <section class="diagnostic-ajcc-staging is-empty">
        <strong>Estadificación TNM</strong>
        <span>Seleccione primero el sitio AJCC para cargar T, N, M y sus factores específicos.</span>
      </section>
    `;
  }
  if (diagnosticAjccUi.loading) {
    return `
      <section class="diagnostic-ajcc-staging is-loading" aria-live="polite">
        <strong>Estadificación TNM</strong>
        <span>Cargando criterios AJCC 8…</span>
      </section>
    `;
  }
  if (diagnosticAjccUi.error) {
    return `
      <section class="diagnostic-ajcc-staging is-error">
        <strong>No se pudieron cargar los criterios AJCC 8</strong>
        <span>${escapeHtml(diagnosticAjccUi.error)}</span>
        <button class="ghost-button" type="button" data-diagnostic-ajcc-retry>Reintentar</button>
      </section>
    `;
  }
  if (!diagnosticAjccUi.site) {
    return `
      <section class="diagnostic-ajcc-staging is-empty">
        <strong>Estadificación TNM</strong>
        <span>Preparando criterios para ${escapeHtml(selected.display || selected.code)}.</span>
      </section>
    `;
  }

  const site = diagnosticAjccUi.site;
  const excluded = new Set(["T", "N", "M", "Classification", "DescY", "DescR", "DescM"]);
  const extras = Object.entries(site.axes || {}).filter(([key]) => !excluded.has(key));
  const selectedTnm = ["T", "N", "M"].map((axis) => diagnosticAjccUi.values[axis]).filter(hasText).join(" ");
  const resultText = diagnosticAjccUi.stage
    ? `${selectedTnm} · Estadio ${diagnosticAjccUi.stage}`
    : selectedTnm || "Seleccione T, N y M";
  const resultHelp = diagnosticAjccUi.calculating
    ? "Calculando agrupación…"
    : diagnosticAjccUi.calculationError
      ? `${diagnosticAjccUi.calculationError} Puede ingresar el estadio manualmente.`
      : diagnosticAjccUi.stage
        ? diagnosticAjccUi.stageEdited || !diagnosticAjccUi.sourceRow
          ? "Estadio informado manualmente. Puede corregirlo antes de guardar."
          : "Estadio calculado automáticamente según la matriz local del sitio. Puede corregirlo antes de guardar."
        : "Complete T, N, M y los factores requeridos. Si la matriz no contempla la combinación, ingrese el estadio manualmente.";

  return `
    <section class="diagnostic-ajcc-staging">
      <header>
        <div>
          <small>AJCC Cancer Staging Manual · 8.ª edición</small>
          <strong>${escapeHtml(site.name || selected.display || selected.code)}</strong>
        </div>
        <span class="diagnostic-ajcc-local-badge">Cálculo local</span>
      </header>
      <div class="diagnostic-ajcc-context">
        <label>
          <span>Tipo</span>
          <select data-diagnostic-ajcc-field="prefix">
            ${[
              ["c", "c · clínico"],
              ["p", "p · patológico"],
              ["yc", "yc · clínico postratamiento"],
              ["yp", "yp · patológico postratamiento"],
              ["r", "r · recurrencia"]
            ].map(([value, label]) => `<option value="${value}"${diagnosticAjccUi.values.prefix === value ? " selected" : ""}>${label}</option>`).join("")}
          </select>
        </label>
        <label>
          <span>Fecha</span>
          <input type="date" data-diagnostic-ajcc-field="date" value="${escapeAttr(diagnosticAjccUi.values.date || today())}">
        </label>
      </div>
      <div class="diagnostic-ajcc-axes">
        ${["T", "N", "M"].map((axis) => renderDiagnosticAjccAxis(axis, site.axes?.[axis] || { label: axis })).join("")}
      </div>
      ${extras.length ? `
        <fieldset class="diagnostic-ajcc-factors">
          <legend>Factores necesarios para agrupar el estadio</legend>
          <div>${extras.map(([key, axis]) => renderDiagnosticAjccAxis(key, axis, { compact: true })).join("")}</div>
        </fieldset>
      ` : ""}
      <div class="diagnostic-ajcc-result ${diagnosticAjccUi.stage ? "is-ready" : ""}">
        <label class="diagnostic-ajcc-stage-field">
          <span>Resultado · Estadio</span>
          <input type="text"
            data-diagnostic-ajcc-stage
            value="${escapeAttr(diagnosticAjccUi.stage || "")}"
            placeholder="Ej.: IIIB"
            maxlength="40"
            autocomplete="off"
            aria-describedby="diagnosticAjccStageHelp">
        </label>
        <strong aria-live="polite">${escapeHtml(resultText)}</strong>
        <small id="diagnosticAjccStageHelp">${escapeHtml(resultHelp)}</small>
      </div>
    </section>
  `;
}

function renderDiagnosticClassificationPanelContent(system) {
  const label = DIAGNOSTIC_CLASSIFICATION_LABELS[system];
  const status = diagnosticClassificationStatus(system);
  const selected = getDiagnosticDraftClassifications()[system];
  const ajccSelected = hasText(getDiagnosticDraftClassifications().ajcc?.code);
  const selectedText = diagnosticClassificationSelectionText(selected, system) || "Sin selección";
  const controlLabel = system === "ajcc"
    ? "Sitio primario AJCC 8"
    : system === "snomed"
      ? "Concepto clínico SNOMED CT"
      : "Código CIE-10";
  return `
    <header class="diagnostic-classification-panel-header">
      <strong>${escapeHtml(label)}</strong>
      <span class="${hasText(selected.code) || hasText(selected.display) ? "is-selected" : ""}"
        data-diagnostic-selected="${escapeAttr(system)}"
        title="${escapeAttr(selectedText)}">${escapeHtml(selectedText)}</span>
    </header>
    <label class="diagnostic-classification-select-label" for="diagnosticClassificationSelect-${escapeAttr(system)}">
      <span>${escapeHtml(controlLabel)}</span>
      <select id="diagnosticClassificationSelect-${escapeAttr(system)}"
        data-diagnostic-select="${escapeAttr(system)}"
        ${system === "ajcc" ? "" : 'size="4"'}
        ${system !== "ajcc" && (!ajccSelected || !diagnosticClassificationUi.results[system].length) ? "disabled" : ""}
        required
        aria-required="true"
        aria-describedby="diagnosticClassificationStatus-${escapeAttr(system)}">
        ${diagnosticClassificationSelectOptions(system)}
      </select>
    </label>
    <p class="diagnostic-classification-status is-${escapeAttr(status.kind)}"
      id="diagnosticClassificationStatus-${escapeAttr(system)}"
      data-diagnostic-status="${escapeAttr(system)}"
      role="status"
      aria-live="polite">${escapeHtml(status.text)}</p>
  `;
}

function renderDiagnosticClassificationSection({ editable = false } = {}) {
  if (!editable) return renderDiagnosticClassificationSummary();
  const configuredSystems = getVisibleDiagnosticSystems();
  const completion = getDiagnosticCompletionState();
  const configurationNote = configuredSystems.length < DIAGNOSTIC_CLASSIFICATION_SYSTEMS.length
    ? "Para guardar un diagnóstico completo, este editor muestra los tres sistemas aunque la vista resumida tenga otra configuración."
    : "AJCC define el sitio rector; SNOMED CT y CIE-10 se vinculan con las equivalencias configuradas.";
  return `
    <div class="diagnostic-classification-workspace" data-diagnostic-workspace>
      <div data-diagnostic-summary>${renderDiagnosticClassificationSummary({ editable: true })}</div>
      <section class="diagnostic-classification-ajcc">
        <div id="diagnosticClassificationPanel-ajcc"
          class="diagnostic-classification-panel diagnostic-classification-panel--ajcc"
          data-diagnostic-panel="ajcc">
          ${renderDiagnosticClassificationPanelContent("ajcc")}
        </div>
        <div class="diagnostic-ajcc-host" data-diagnostic-ajcc-host>${renderDiagnosticAjccStaging()}</div>
      </section>
      <section class="diagnostic-classification-terminology">
        <header>
          <strong>Codificación diagnóstica obligatoria</strong>
          <span>${escapeHtml(configurationNote)}</span>
        </header>
        <div class="diagnostic-classification-terminology-panels">
          <section id="diagnosticClassificationPanel-snomed"
            class="diagnostic-classification-panel"
            data-diagnostic-panel="snomed">
            ${renderDiagnosticClassificationPanelContent("snomed")}
          </section>
          <section id="diagnosticClassificationPanel-cie10"
            class="diagnostic-classification-panel"
            data-diagnostic-panel="cie10">
            ${renderDiagnosticClassificationPanelContent("cie10")}
          </section>
        </div>
      </section>
      <footer class="diagnostic-classification-footer">
        <span data-diagnostic-draft-status class="${completion.complete ? "is-ready" : "is-incomplete"}">
          ${escapeHtml(completion.message)}
        </span>
        <button class="tool-button primary" type="button" data-diagnostic-save ${completion.complete ? "" : "disabled"}>
          <i data-lucide="save"></i>
          <span>Guardar diagnóstico</span>
        </button>
      </footer>
    </div>
  `;
}

function updateDiagnosticClassificationPanel(system) {
  const select = $(`[data-diagnostic-select="${system}"]`);
  if (select) {
    select.innerHTML = diagnosticClassificationSelectOptions(system);
    select.disabled = system !== "ajcc" && (
      !hasText(getDiagnosticDraftClassifications().ajcc?.code)
      || !diagnosticClassificationUi.results[system].length
    );
  }
  const status = diagnosticClassificationStatus(system);
  const statusElement = $(`[data-diagnostic-status="${system}"]`);
  if (statusElement) {
    statusElement.className = `diagnostic-classification-status is-${status.kind}`;
    statusElement.textContent = status.text;
  }
  const selected = getDiagnosticDraftClassifications()[system];
  const selectedText = diagnosticClassificationSelectionText(selected, system) || "Sin selección";
  const selectedElement = $(`[data-diagnostic-selected="${system}"]`);
  if (selectedElement) {
    selectedElement.textContent = selectedText;
    selectedElement.title = selectedText;
    selectedElement.classList.toggle("is-selected", hasText(selected.code) || hasText(selected.display));
  }
  const panel = $(`[data-diagnostic-panel="${system}"]`);
  if (panel) panel.setAttribute("aria-busy", String(Boolean(diagnosticClassificationUi.loading[system])));
}

function updateDiagnosticClassificationStatus(system, message) {
  const status = $(`[data-diagnostic-status="${system}"]`);
  if (!status) return;
  status.className = "diagnostic-classification-status is-hint";
  status.textContent = message;
}

function updateDiagnosticClassificationSummary() {
  const summary = $("[data-diagnostic-summary]");
  if (summary) summary.innerHTML = renderDiagnosticClassificationSummary({ editable: true });
}

function updateDiagnosticAjccStaging() {
  const host = $("[data-diagnostic-ajcc-host]");
  if (host) host.innerHTML = renderDiagnosticAjccStaging();
  updateDiagnosticClassificationSummary();
  updateDiagnosticSaveState();
}

function refreshDiagnosticClassificationWorkspace({ includeAjcc = true } = {}) {
  DIAGNOSTIC_CLASSIFICATION_SYSTEMS.forEach(updateDiagnosticClassificationPanel);
  updateDiagnosticClassificationSummary();
  if (includeAjcc) updateDiagnosticAjccStaging();
  updateDiagnosticSaveState();
}

function applyDiagnosticDisplaySettingsToWorkspace() {
  updateDiagnosticClassificationSummary();
  updateDiagnosticSaveState();
}

function ensureDiagnosticClassificationCatalogs() {
  if (!$("[data-diagnostic-workspace]")) return;
  void loadDiagnosticClassificationCatalogs();
}

function normalizeDiagnosticCatalogItem(item, system, payload = {}) {
  if (!item || typeof item !== "object") return null;
  const code = String(item.code || item.id || "").trim().slice(0, 80);
  const display = String(item.display || item.name || item.term || "").trim().slice(0, 500);
  if (!code || !display) return null;
  return {
    system: DIAGNOSTIC_CLASSIFICATION_LABELS[system],
    code,
    display,
    group: String(item.group || "").trim().slice(0, 200),
    version: String(item.version || payload.version || payload.edition || "").trim().slice(0, 160),
    source: String(item.source || payload.source || "").trim().slice(0, 240),
    sourceConceptId: String(item.sourceConceptId || "").trim().slice(0, 80),
    sourceDisplay: String(item.sourceDisplay || "").trim().slice(0, 500),
    mapAdvice: String(item.mapAdvice || "").trim().slice(0, 500)
  };
}

function updateDiagnosticTerminologyOptionsFromAjcc() {
  const selectedAjcc = getDiagnosticDraftClassifications().ajcc;
  const mappings = Array.isArray(diagnosticEquivalences) ? diagnosticEquivalences : [];
  const matches = mappings.filter((definition) =>
    diagnosticMappingCode(definition?.ajcc?.code) === diagnosticMappingCode(selectedAjcc?.code)
  );
  for (const system of ["snomed", "cie10"]) {
    const items = matches
      .map((definition) => normalizeDiagnosticCatalogItem({
        ...definition?.[system],
        mapAdvice: [
          definition?.relation && `Relación ${definition.relation}`,
          definition?.confidence && `confianza ${definition.confidence}`,
          definition?.notes
        ].filter(hasText).join(" · ")
      }, system))
      .filter(Boolean);
    diagnosticClassificationUi.results[system] = Array.from(
      new Map(items.map((item) => [`${item.code}\u0000${item.display}`, item])).values()
    );
    diagnosticClassificationUi.loadedQuery[system] = selectedAjcc?.code || null;
    diagnosticClassificationUi.loading[system] = false;
    diagnosticClassificationUi.errors[system] = "";
  }
}

function setDiagnosticAjccCatalogResults(payload) {
  diagnosticClassificationUi.results.ajcc = (Array.isArray(payload?.sites) ? payload.sites : [])
    .map((item) => normalizeDiagnosticCatalogItem({
      code: item.id,
      display: item.name,
      group: item.group,
      version: payload.edition || "AJCC 8",
      source: payload.source || "Catálogo AJCC 8 local"
    }, "ajcc", payload))
    .filter(Boolean);
  diagnosticClassificationUi.loadedQuery.ajcc = "all";
  diagnosticClassificationUi.errors.ajcc = "";
}

async function loadDiagnosticAjccCatalog({ force = false } = {}) {
  if (diagnosticAjccCatalogLoading) return diagnosticAjccCatalogLoading;
  if (diagnosticAjccCatalog && !force) {
    setDiagnosticAjccCatalogResults(diagnosticAjccCatalog);
    updateDiagnosticClassificationPanel("ajcc");
    return diagnosticAjccCatalog;
  }
  diagnosticClassificationUi.loading.ajcc = true;
  diagnosticClassificationUi.errors.ajcc = "";
  updateDiagnosticClassificationPanel("ajcc");
  diagnosticAjccCatalogLoading = (async () => {
    try {
      const response = await fetch("/api/ajcc8", {
        cache: "no-store",
        headers: { Accept: "application/json" }
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok || payload.ok === false) {
        throw new Error(payload.error || "No se pudo cargar el catálogo AJCC 8.");
      }
      diagnosticAjccCatalog = payload;
      diagnosticAjccCatalogError = "";
      setDiagnosticAjccCatalogResults(payload);
      return payload;
    } catch (error) {
      diagnosticAjccCatalog = null;
      diagnosticAjccCatalogError = String(error?.message || "Catálogo AJCC 8 no disponible.");
      diagnosticClassificationUi.results.ajcc = [];
      diagnosticClassificationUi.errors.ajcc = diagnosticAjccCatalogError;
      return null;
    } finally {
      diagnosticClassificationUi.loading.ajcc = false;
      diagnosticAjccCatalogLoading = null;
      updateDiagnosticClassificationPanel("ajcc");
      updateDiagnosticSaveState();
    }
  })();
  return diagnosticAjccCatalogLoading;
}

async function loadDiagnosticClassificationCatalogs() {
  const patientKey = diagnosticClassificationUi.patientKey;
  await Promise.all([
    loadDiagnosticAjccCatalog(),
    loadDiagnosticEquivalences()
  ]);
  if (patientKey !== diagnosticClassificationUi.patientKey) return;
  updateDiagnosticTerminologyOptionsFromAjcc();
  const selectedAjcc = getDiagnosticDraftClassifications().ajcc;
  if (hasText(selectedAjcc?.code)) {
    const selectedAjccCode = diagnosticMappingCode(selectedAjcc.code);
    const guard = () => patientKey === diagnosticClassificationUi.patientKey
      && selectedAjccCode === diagnosticMappingCode(getDiagnosticDraftClassifications().ajcc?.code);
    await applyDiagnosticEquivalence("ajcc", selectedAjcc, { guard });
    if (!guard()) return;
    ensureDiagnosticAjccSite();
  }
  refreshDiagnosticClassificationWorkspace();
}

function diagnosticAjccExtraAxes() {
  if (!diagnosticAjccUi.site?.axes) return [];
  const excluded = new Set(["T", "N", "M", "Classification", "DescY", "DescR", "DescM"]);
  return Object.entries(diagnosticAjccUi.site.axes)
    .filter(([key, axis]) => !excluded.has(key) && Array.isArray(axis?.categories) && axis.categories.length);
}

function diagnosticClassificationMatchesMapping(classification, mappedValue) {
  return diagnosticMappingCode(classification?.code) === diagnosticMappingCode(mappedValue?.code)
    && normalizeSearchText(classification?.display || "") === normalizeSearchText(mappedValue?.display || "");
}

function hasCoherentDiagnosticEquivalence(classifications) {
  if (!Array.isArray(diagnosticEquivalences) || !diagnosticEquivalences.length) return false;
  return diagnosticEquivalences.some((definition) =>
    ["ajcc", "snomed", "cie10"].every((system) =>
      diagnosticClassificationMatchesMapping(classifications?.[system], definition?.[system])
    )
  );
}

function getDiagnosticCompletionState() {
  const classifications = getDiagnosticDraftClassifications();
  const missing = [];
  const addMissing = (label, selector) => missing.push({ label, selector });
  for (const system of ["ajcc", "snomed", "cie10"]) {
    const item = classifications[system];
    if (!hasText(item?.code) || !hasText(item?.display)) {
      addMissing(DIAGNOSTIC_CLASSIFICATION_LABELS[system], `[data-diagnostic-select="${system}"]`);
    }
  }
  const classificationsComplete = ["ajcc", "snomed", "cie10"].every((system) =>
    hasText(classifications[system]?.code) && hasText(classifications[system]?.display)
  );
  if (classificationsComplete && !hasCoherentDiagnosticEquivalence(classifications)) {
    addMissing("equivalencia coherente AJCC / SNOMED CT / CIE-10", '[data-diagnostic-select="snomed"]');
  }

  const selectedAjcc = classifications.ajcc;
  const ajccReady = hasText(selectedAjcc?.code)
    && diagnosticAjccUi.site?.id === selectedAjcc.code
    && !diagnosticAjccUi.loading
    && !diagnosticAjccUi.error;
  if (hasText(selectedAjcc?.code) && !ajccReady) {
    addMissing("criterios del sitio AJCC", '[data-diagnostic-select="ajcc"]');
  }
  if (ajccReady) {
    if (!["c", "p", "yc", "yp", "r"].includes(diagnosticAjccUi.values.prefix)) {
      addMissing("tipo de estadificación", '[data-diagnostic-ajcc-field="prefix"]');
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(String(diagnosticAjccUi.values.date || ""))) {
      addMissing("fecha de estadificación", '[data-diagnostic-ajcc-field="date"]');
    }
    for (const axis of ["T", "N", "M"]) {
      const value = String(diagnosticAjccUi.values[axis] || "");
      const valid = diagnosticAjccAxisCategories(axis)
        .some((item) => String(item.code || "") === value);
      if (!valid) addMissing(axis, `[data-diagnostic-ajcc-axis="${axis}"]`);
    }
    for (const [key, axis] of diagnosticAjccExtraAxes()) {
      const value = String(diagnosticAjccUi.values[key] || "");
      const valid = axis.categories.some((item) => String(item.code || "") === value);
      if (!valid) addMissing(axis.label || key, `[data-diagnostic-ajcc-axis="${key}"]`);
    }
    if (diagnosticAjccUi.calculating && !hasText(diagnosticAjccUi.stage)) {
      addMissing("cálculo del estadio", ".diagnostic-ajcc-result");
    } else if (!hasText(diagnosticAjccUi.stage)) {
      addMissing("estadio AJCC", "[data-diagnostic-ajcc-stage]");
    }
  }

  const labels = Array.from(new Set(missing.map((item) => item.label)));
  const message = labels.length
    ? `Falta completar: ${labels.slice(0, 5).join(" · ")}${labels.length > 5 ? ` · +${labels.length - 5}` : ""}.`
    : "Diagnóstico completo. Puede guardar AJCC, TNM, SNOMED CT y CIE-10.";
  return {
    complete: missing.length === 0,
    missing,
    message,
    firstSelector: missing[0]?.selector || ""
  };
}

function updateDiagnosticSaveState() {
  const completion = getDiagnosticCompletionState();
  const button = $("[data-diagnostic-save]");
  if (button) {
    button.disabled = !completion.complete;
    button.title = completion.complete ? "Guardar diagnóstico completo" : completion.message;
  }
  const status = $("[data-diagnostic-draft-status]");
  if (status) {
    status.textContent = completion.message;
    status.classList.toggle("is-ready", completion.complete);
    status.classList.toggle("is-incomplete", !completion.complete);
  }
  $$("[data-diagnostic-select], [data-diagnostic-ajcc-field], [data-diagnostic-ajcc-axis], [data-diagnostic-ajcc-stage]")
    .forEach((control) => control.removeAttribute("aria-invalid"));
  completion.missing.forEach((item) => {
    const control = $(item.selector);
    if (control?.matches("input, select")) control.setAttribute("aria-invalid", "true");
  });
  return completion;
}

async function searchDiagnosticClassifications(system) {
  if (!DIAGNOSTIC_CLASSIFICATION_SYSTEMS.includes(system)) return;
  const query = diagnosticClassificationUi.queries[system].trim();
  if (query.length < 2) {
    diagnosticClassificationUi.results[system] = [];
    diagnosticClassificationUi.loadedQuery[system] = null;
    diagnosticClassificationUi.loading[system] = false;
    diagnosticClassificationUi.errors[system] = "";
    updateDiagnosticClassificationPanel(system);
    return;
  }

  const requestId = diagnosticClassificationUi.requestId[system] + 1;
  diagnosticClassificationUi.requestId[system] = requestId;
  diagnosticClassificationUi.loading[system] = true;
  diagnosticClassificationUi.errors[system] = "";
  updateDiagnosticClassificationPanel(system);

  try {
    const params = new URLSearchParams({ system, q: query, limit: "40" });
    const response = await fetch(`/api/diagnosis-catalogs/search?${params}`, {
      cache: "no-store",
      headers: { Accept: "application/json" }
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok === false) {
      throw new Error(payload.error || "No se pudo consultar el catálogo.");
    }
    if (requestId !== diagnosticClassificationUi.requestId[system] ||
        query !== diagnosticClassificationUi.queries[system].trim()) return;

    const items = (Array.isArray(payload.items) ? payload.items : [])
      .map((item) => normalizeDiagnosticCatalogItem(item, system, payload))
      .filter(Boolean)
      .filter((item) => diagnosticMatchesQuery(item, query));
    diagnosticClassificationUi.results[system] = Array.from(
      new Map(items.map((item) => [`${item.code}\u0000${item.display}`, item])).values()
    );
    diagnosticClassificationUi.loadedQuery[system] = query;
    diagnosticClassificationUi.errors[system] = "";
  } catch (error) {
    if (requestId !== diagnosticClassificationUi.requestId[system] ||
        query !== diagnosticClassificationUi.queries[system].trim()) return;
    diagnosticClassificationUi.results[system] = [];
    diagnosticClassificationUi.loadedQuery[system] = query;
    diagnosticClassificationUi.errors[system] = String(
      error?.message || "Servicio terminológico no disponible. Intente nuevamente."
    );
  } finally {
    if (requestId === diagnosticClassificationUi.requestId[system]) {
      diagnosticClassificationUi.loading[system] = false;
      updateDiagnosticClassificationPanel(system);
    }
  }
}

async function loadDiagnosticEquivalences({ force = false } = {}) {
  if (diagnosticEquivalencesLoading) return diagnosticEquivalencesLoading;
  if (Array.isArray(diagnosticEquivalences) && !force) return diagnosticEquivalences;
  diagnosticEquivalencesLoading = (async () => {
    try {
      const response = await fetch("/api/clinical/configuration/diagnosis-equivalence?includeInactive=0", {
        cache: "no-store",
        headers: { Accept: "application/json" }
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.error || "No se pudieron leer las equivalencias.");
      diagnosticEquivalences = (Array.isArray(payload.items) ? payload.items : [])
        .filter((item) => item?.active !== false && item?.definition)
        .map((item) => item.definition);
    } catch {
      diagnosticEquivalences = [];
    } finally {
      diagnosticEquivalencesLoading = null;
    }
    return diagnosticEquivalences;
  })();
  return diagnosticEquivalencesLoading;
}

function diagnosticMappingCode(value) {
  return String(value || "").trim().toLocaleLowerCase("es");
}

function resetDiagnosticAjccForSelection(item) {
  const siteId = String(item?.code || "").trim();
  diagnosticAjccUi = {
    site: null,
    siteId,
    loading: false,
    error: "",
    requestId: diagnosticAjccUi.requestId + 1,
    values: { prefix: "c", date: today(), T: "", N: "", M: "" },
    stage: "",
    stageEdited: false,
    sourceRow: null,
    calculating: false,
    calculationError: "",
    stageRequestId: diagnosticAjccUi.stageRequestId + 1
  };
}

async function applyDiagnosticEquivalence(system, selectedItem, {
  replaceTargets = false,
  guard = null
} = {}) {
  const mappings = await loadDiagnosticEquivalences();
  if (typeof guard === "function" && !guard()) return false;
  const selectedCode = diagnosticMappingCode(selectedItem?.code);
  if (!selectedCode || !mappings.length) return false;
  const values = getDiagnosticDraftClassifications();
  const currentAjccCode = diagnosticMappingCode(values.ajcc?.code);
  const matches = mappings.filter((definition) => {
    if (diagnosticMappingCode(definition?.[system]?.code) !== selectedCode) return false;
    return system === "ajcc"
      || !currentAjccCode
      || diagnosticMappingCode(definition?.ajcc?.code) === currentAjccCode;
  });
  if (!matches.length) return false;

  let changed = false;
  for (const targetSystem of DIAGNOSTIC_CLASSIFICATION_SYSTEMS) {
    if (typeof guard === "function" && !guard()) return false;
    if (targetSystem === system) continue;
    const uniqueTargets = Array.from(new Map(
      matches
        .map((definition) => definition?.[targetSystem])
        .filter((entry) => hasText(entry?.code))
        .map((entry) => [diagnosticMappingCode(entry.code), entry])
    ).values());
    if (uniqueTargets.length !== 1) continue;
    const current = values[targetSystem];
    if (!replaceTargets && hasText(current.code)) continue;
    const mapped = normalizeDiagnosticClassificationRecord({
      ...uniqueTargets[0],
      freeText: uniqueTargets[0].display
    }, targetSystem);
    diagnosticClassificationUi.drafts[targetSystem] = mapped;
    if (targetSystem === "ajcc") resetDiagnosticAjccForSelection(mapped);
    changed = true;
  }
  return changed;
}

function collectDiagnosticAjccState() {
  const selected = getDiagnosticDraftClassifications().ajcc;
  if (!hasText(selected.code)) return normalizeOncologyTnm({});
  const values = Object.fromEntries(
    Object.entries(diagnosticAjccUi.values)
      .filter(([key]) => !["prefix", "date"].includes(key))
  );
  return normalizeOncologyTnm({
    siteId: selected.code,
    siteDisplay: selected.display,
    prefix: diagnosticAjccUi.values.prefix || "c",
    date: diagnosticAjccUi.values.date || today(),
    edition: diagnosticAjccUi.site?.edition || "AJCC 8",
    source: diagnosticAjccUi.site?.source || "Catálogo AJCC 8 local",
    guideVersion: diagnosticAjccUi.site?.guideVersion || "",
    t: values.T,
    n: values.N,
    m: values.M,
    stage: diagnosticAjccUi.stage,
    sourceRow: diagnosticAjccUi.sourceRow,
    calculatedAt: diagnosticAjccUi.stage && diagnosticAjccUi.sourceRow ? new Date().toISOString() : "",
    values
  });
}

async function saveDiagnosticClassifications({ successMessage = "Diagnóstico agregado" } = {}) {
  if (diagnosticClassificationSaveBusy) return false;
  const completion = getDiagnosticCompletionState();
  if (!completion.complete) {
    updateDiagnosticSaveState();
    toast(completion.message);
    const firstIncomplete = completion.firstSelector ? $(completion.firstSelector) : null;
    firstIncomplete?.scrollIntoView?.({ block: "center", behavior: "smooth" });
    firstIncomplete?.focus?.();
    return false;
  }
  diagnosticClassificationSaveBusy = true;
  const saveButton = $("[data-diagnostic-save]");
  if (saveButton) {
    saveButton.disabled = true;
    const label = $("span", saveButton);
    if (label) label.textContent = "Guardando...";
  }
  const values = getDiagnosticDraftClassifications();
  let diagnosisDocumentPersisted = false;
  try {
    const diagnosticClassifications = Object.fromEntries(
      DIAGNOSTIC_CLASSIFICATION_SYSTEMS.map((system) => [
        system,
        normalizeDiagnosticClassificationRecord({
          ...values[system],
          freeText: values[system].freeText || values[system].display
        }, system)
      ])
    );
    const tnm = collectDiagnosticAjccState();
    const audit = buildAuditStamp("cargado");
    diagnosticClassificationDraftEntryId ||= makeId("diagnosis");
    state.oncology.diagnosisRecords = normalizeDiagnosisRecords(
      state.oncology.diagnosisRecords,
      state.oncology
    );
    let diagnosisRecord = state.oncology.diagnosisRecords.find(
      (record) => record.id === diagnosticClassificationDraftEntryId
    );
    const draftRecord = normalizeDiagnosisRecord({
      id: diagnosticClassificationDraftEntryId,
      date: tnm.date || today(),
      datePrecision: "day",
      diagnosis: diagnosticClassifications.snomed.display ||
        diagnosticClassifications.ajcc.display,
      topography: tnm.siteDisplay || diagnosticClassifications.ajcc.display,
      stage: tnm.stage,
      diagnosticClassifications,
      tnm,
      legacyProjection: false,
      audit,
      createdAt: audit.at
    });
    if (diagnosisRecord &&
        diagnosisRecordFingerprint(diagnosisRecord) !== diagnosisRecordFingerprint(draftRecord)) {
      closeDiagnosticClassificationModal({ restoreFocus: true, discardDraft: true });
      toast("Ese diagnóstico ya quedó registrado. Use Agregar diagnóstico para consignar una nueva alta.");
      return false;
    }
    if (!diagnosisRecord) {
      diagnosisRecord = draftRecord;
      state.oncology.diagnosisRecords.push(diagnosisRecord);
    }
    DIAGNOSTIC_CLASSIFICATION_SYSTEMS.forEach((system) => {
      state.oncology.diagnosticClassifications[system] =
        diagnosisRecord.diagnosticClassifications[system];
    });
    state.oncology.tnm = diagnosisRecord.tnm;
    state.oncology.diagnosis = diagnosisRecord.diagnosis;
    state.oncology.diagnosisDate = diagnosisRecord.date;
    state.oncology.diagnosisDatePrecision = diagnosisRecord.datePrecision;
    state.oncology.topography = diagnosisRecord.topography;
    state.oncology.stage = diagnosisRecord.stage;
    state.meta.sectionAudit ??= {};
    state.meta.sectionAudit.diagnosticClassifications = diagnosisRecord.audit || audit;
    state.meta.updatedAt = new Date().toISOString();
    invalidateAiTimeline();
    storeClinicalStateLocally();
    renderPreview();
    const persisted = await persistClinicalState({ silent: true });
    if (!persisted) {
      toast(getClinicalPersistenceFailureMessage(clinicalLocalCacheAllowed()
        ? "El diagnóstico quedó guardado localmente"
        : "No se pudo guardar el diagnóstico en la base clínica"));
      return false;
    }
    diagnosisDocumentPersisted = true;

    const patientId = getActiveLiraPatientId();
    const expectedRevision = Number(state.meta.persistenceRevision);
    const response = await fetch(`/api/clinical/patients/${encodeURIComponent(patientId)}/diagnosis`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        expectedRevision,
        diagnosisEntryId: diagnosisRecord.id
      })
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.ok === false || !payload.diagnosis?.id) {
      throw new Error(payload.error || "El diagnóstico se guardó, pero no pudo vincularse a Tratamientos.");
    }

    careTreatmentOptions = null;
    await refreshCareWorkspace().catch((error) => {
      console.warn("El diagnóstico se vinculó, pero Hospital de día no pudo actualizarse.", error);
    });
    diagnosticClassificationDraftEntryId = "";
    closeDiagnosticClassificationModal({ restoreFocus: true, discardDraft: true });
    toast(successMessage);
    return true;
  } catch (error) {
    if (diagnosisDocumentPersisted) {
      careTreatmentOptions = null;
      diagnosticClassificationDraftEntryId = "";
      closeDiagnosticClassificationModal({ restoreFocus: true, discardDraft: true });
      toast(`${error.message || "No se pudo actualizar Tratamientos."} El diagnóstico quedó guardado y se reintentará la vinculación.`);
      return true;
    }
    toast(error.message || "No se pudo completar el guardado del diagnóstico");
    return false;
  } finally {
    diagnosticClassificationSaveBusy = false;
    const currentButton = $("[data-diagnostic-save]");
    if (currentButton) {
      const label = $("span", currentButton);
      if (label) label.textContent = "Guardar diagnóstico";
      updateDiagnosticSaveState();
    }
  }
}

async function selectDiagnosticClassification(system, itemKey) {
  if (!DIAGNOSTIC_CLASSIFICATION_SYSTEMS.includes(system) || !hasText(itemKey)) return;
  const item = diagnosticClassificationUi.results[system]
    .find((entry) => diagnosticCatalogItemKey(entry) === String(itemKey));
  if (!item) return;
  const current = getDiagnosticDraftClassifications()[system];
  const changed = String(current.code || "") !== String(item.code) ||
    String(current.display || "") !== String(item.display);
  if (!changed) return;
  const patientKey = diagnosticClassificationUi.patientKey;
  const selectionRequestId = diagnosticClassificationUi.selectionRequestId + 1;
  diagnosticClassificationUi.selectionRequestId = selectionRequestId;
  const selectedCode = diagnosticMappingCode(item.code);
  const guard = () => patientKey === diagnosticClassificationUi.patientKey
    && selectionRequestId === diagnosticClassificationUi.selectionRequestId
    && selectedCode === diagnosticMappingCode(getDiagnosticDraftClassifications()[system]?.code);

  diagnosticClassificationUi.drafts[system] = normalizeDiagnosticClassificationRecord({
    ...item,
    freeText: item.display
  }, system);
  if (system === "ajcc") {
    diagnosticClassificationUi.drafts.snomed = normalizeDiagnosticClassificationRecord({}, "snomed");
    diagnosticClassificationUi.drafts.cie10 = normalizeDiagnosticClassificationRecord({}, "cie10");
    resetDiagnosticAjccForSelection(item);
    await loadDiagnosticEquivalences();
    if (!guard()) return;
    updateDiagnosticTerminologyOptionsFromAjcc();
    await applyDiagnosticEquivalence(system, item, { replaceTargets: true, guard });
  } else {
    await applyDiagnosticEquivalence(system, item, { guard });
  }
  if (!guard()) return;
  ensureDiagnosticAjccSite();
  refreshDiagnosticClassificationWorkspace();
}

function ensureDiagnosticAjccSite() {
  const selected = getDiagnosticDraftClassifications().ajcc;
  if (!hasText(selected.code)) return;
  if (diagnosticAjccUi.loading) return;
  if (diagnosticAjccUi.site?.id === selected.code && diagnosticAjccUi.siteId === selected.code) return;
  void loadDiagnosticAjccSite();
}

async function loadDiagnosticAjccSite({ force = false } = {}) {
  const selected = getDiagnosticDraftClassifications().ajcc;
  const siteId = String(selected.code || "").trim();
  if (!siteId) {
    diagnosticAjccUi.site = null;
    diagnosticAjccUi.siteId = "";
    updateDiagnosticAjccStaging();
    return;
  }
  if (!force && diagnosticAjccUi.site?.id === siteId && diagnosticAjccUi.siteId === siteId) return;
  const requestId = diagnosticAjccUi.requestId + 1;
  diagnosticAjccUi.requestId = requestId;
  diagnosticAjccUi.loading = true;
  diagnosticAjccUi.error = "";
  diagnosticAjccUi.stageRequestId += 1;
  diagnosticAjccUi.calculating = false;
  updateDiagnosticAjccStaging();
  try {
    const response = await fetch(`/api/ajcc8/detail?id=${encodeURIComponent(siteId)}`, {
      cache: "no-store",
      headers: { Accept: "application/json" }
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || "Sitio AJCC 8 no disponible.");
    if (requestId !== diagnosticAjccUi.requestId ||
        siteId !== String(getDiagnosticDraftClassifications().ajcc.code || "")) return;
    const previousSiteId = diagnosticAjccUi.siteId;
    diagnosticAjccUi.site = payload;
    diagnosticAjccUi.siteId = siteId;
    diagnosticAjccUi.loading = false;
    diagnosticAjccUi.error = "";
    if (previousSiteId && previousSiteId !== siteId) {
      diagnosticAjccUi.values = { prefix: "c", date: today(), T: "", N: "", M: "" };
      diagnosticAjccUi.stage = "";
      diagnosticAjccUi.stageEdited = false;
      diagnosticAjccUi.sourceRow = null;
    }
    updateDiagnosticAjccStaging();
    if (diagnosticAjccUi.values.T && diagnosticAjccUi.values.N && diagnosticAjccUi.values.M) {
      await calculateDiagnosticAjccStage();
    }
  } catch (error) {
    if (requestId !== diagnosticAjccUi.requestId) return;
    diagnosticAjccUi.site = null;
    diagnosticAjccUi.siteId = siteId;
    diagnosticAjccUi.loading = false;
    diagnosticAjccUi.error = String(error?.message || "No se pudo cargar AJCC 8.");
    updateDiagnosticAjccStaging();
  }
}

function updateDiagnosticAjccDraft(control) {
  if (control.matches("[data-diagnostic-ajcc-field]")) {
    diagnosticAjccUi.values[control.dataset.diagnosticAjccField] = control.value;
  }
  if (control.matches("[data-diagnostic-ajcc-axis]")) {
    diagnosticAjccUi.values[control.dataset.diagnosticAjccAxis] = control.value;
  }
  diagnosticAjccUi.stage = "";
  diagnosticAjccUi.stageEdited = false;
  diagnosticAjccUi.sourceRow = null;
  diagnosticAjccUi.calculationError = "";
  diagnosticAjccUi.stageRequestId += 1;
  diagnosticAjccUi.calculating = false;
  updateDiagnosticSaveState();
  if (control.dataset.diagnosticAjccField === "prefix") {
    diagnosticAjccUi.values.T = "";
    updateDiagnosticAjccStaging();
  }
  window.clearTimeout(diagnosticAjccStageTimer);
  diagnosticAjccStageTimer = window.setTimeout(calculateDiagnosticAjccStage, 90);
}

function updateDiagnosticAjccStageDraft(control) {
  diagnosticAjccUi.stage = String(control?.value || "").trimStart().slice(0, 40);
  diagnosticAjccUi.stageEdited = true;
  diagnosticAjccUi.sourceRow = null;
  diagnosticAjccUi.stageRequestId += 1;
  diagnosticAjccUi.calculating = false;

  const result = control?.closest(".diagnostic-ajcc-result");
  result?.classList.toggle("is-ready", hasText(diagnosticAjccUi.stage));
  const selectedTnm = ["T", "N", "M"]
    .map((axis) => diagnosticAjccUi.values[axis])
    .filter(hasText)
    .join(" ");
  const summary = result?.querySelector(":scope > strong");
  if (summary) {
    summary.textContent = diagnosticAjccUi.stage
      ? `${selectedTnm} · Estadio ${diagnosticAjccUi.stage}`
      : selectedTnm || "Seleccione T, N y M";
  }
  const help = result?.querySelector(":scope > small");
  if (help) {
    help.textContent = diagnosticAjccUi.stage
      ? "Estadio informado manualmente. Puede corregirlo antes de guardar."
      : diagnosticAjccUi.calculationError
        ? `${diagnosticAjccUi.calculationError} Puede ingresar el estadio manualmente.`
        : "Ingrese el estadio para completar el diagnóstico.";
  }
  updateDiagnosticClassificationSummary();
  updateDiagnosticSaveState();
}

async function calculateDiagnosticAjccStage() {
  if (!diagnosticAjccUi.site) return;
  const prefix = diagnosticAjccUi.values.prefix || "c";
  const values = {
    ...Object.fromEntries(
      Object.entries(diagnosticAjccUi.values).filter(([key]) => !["prefix", "date"].includes(key))
    ),
    Classification: prefix.includes("p") ? "p" : "c",
    DescY: prefix.includes("y") ? "Yes" : "No",
    DescR: prefix === "r" ? "Yes" : "No",
    DescM: "No"
  };
  if (!values.T || !values.N || !values.M) {
    diagnosticAjccUi.stage = "";
    diagnosticAjccUi.stageEdited = false;
    diagnosticAjccUi.sourceRow = null;
    diagnosticAjccUi.calculationError = "";
    updateDiagnosticAjccStaging();
    return;
  }
  const siteId = diagnosticAjccUi.site.id;
  const stageRequestId = diagnosticAjccUi.stageRequestId + 1;
  diagnosticAjccUi.stageRequestId = stageRequestId;
  diagnosticAjccUi.calculating = true;
  diagnosticAjccUi.calculationError = "";
  updateDiagnosticAjccStaging();
  try {
    const response = await fetch("/api/ajcc8/stage", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ id: siteId, values })
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || "No se pudo agrupar el estadio.");
    if (diagnosticAjccUi.site?.id !== siteId ||
        diagnosticAjccUi.stageRequestId !== stageRequestId) return;
    diagnosticAjccUi.stage = String(payload.stage || "");
    diagnosticAjccUi.stageEdited = false;
    const payloadSourceRow = Number(payload.sourceRow);
    diagnosticAjccUi.sourceRow = payload.sourceRow !== null
      && payload.sourceRow !== undefined
      && payload.sourceRow !== ""
      && Number.isSafeInteger(payloadSourceRow)
      && payloadSourceRow > 0
      ? payloadSourceRow
      : null;
    diagnosticAjccUi.calculationError = payload.stage
      ? ""
      : `Faltan datos: ${(payload.missing || []).join(" · ") || "combinación no contemplada"}`;
  } catch (error) {
    if (diagnosticAjccUi.stageRequestId !== stageRequestId) return;
    diagnosticAjccUi.stage = "";
    diagnosticAjccUi.stageEdited = false;
    diagnosticAjccUi.sourceRow = null;
    diagnosticAjccUi.calculationError = String(error?.message || "No se pudo agrupar el estadio.");
  } finally {
    if (diagnosticAjccUi.stageRequestId === stageRequestId) {
      diagnosticAjccUi.calculating = false;
      updateDiagnosticAjccStaging();
    }
  }
}

function buildClinicalDocumentHtml({ editable = false, printMode = false, printedAt = null } = {}) {
  const sections = [];
  const patient = state.patient;
  const narrative = state.narrative;
  const hasPatientContext = [
    patient.liraId,
    patient.fullName,
    patient.dni,
    patient.medicalRecord
  ].some(hasText);
  if (!hasPatientContext) {
    return `<div class="clinical-document clinical-document--blank" aria-label="Hoja clinica en blanco"></div>`;
  }

  const sectionOptions = (key) => ({
    key,
    editable,
    completed: printMode ? isSectionCompletedForPrint(key) : true,
    showAudit: !editable || !supportsInlineSectionLoad(key) || isSectionCompletedForPrint(key)
  });

  appendSection(sections, "Diagnóstico oncológico", renderClinicalDiagnosisSummary(), {
    ...sectionOptions("diagnosticClassifications"),
    completed: !printMode || hasClinicalDiagnosisSummary(),
    showActions: false,
    showAudit: hasAnyDiagnosticClassification()
  });

  appendSection(sections, "Motivo de consulta", renderEditableSectionBody(
    "chiefComplaint",
    paragraph(narrative.chiefComplaint),
    "Sin motivo de consulta cargado.",
    editable
  ), sectionOptions("chiefComplaint"));

  appendSection(sections, "Antecedentes de enfermedad actual", renderEditableSectionBody(
    "currentIllness",
    paragraph(narrative.currentIllness),
    "Sin antecedentes de enfermedad actual cargados.",
    editable
  ), sectionOptions("currentIllness"));

  const personalHistoryHtml = [
    labeledParagraph("Clinicos / quirurgicos", narrative.backgroundClinical),
    labeledParagraph("Medicacion habitual", narrative.currentMedication),
    labeledParagraph("Oncofamiliares", narrative.familyOncology),
    labeledParagraph("Gineco-obstetricos", narrative.gynecology)
  ].join("");
  appendSection(sections, "Antecedentes personales", renderEditableSectionBody(
    "personalHistory",
    personalHistoryHtml,
    "Sin antecedentes personales cargados.",
    editable
  ), sectionOptions("personalHistory"));

  appendSection(sections, "Estudios complementarios", renderEditableSectionBody(
    "studies",
    renderDocumentStudies(),
    "Sin estudios complementarios cargados.",
    editable
  ), { ...sectionOptions("studies"), showAudit: false });

  const physicalExamHtml = [
    renderExamFacts(),
    renderPhysicalExam(narrative.physicalExam)
  ].join("");
  appendSection(sections, "Examen fisico", renderEditableSectionBody(
    "physicalExam",
    physicalExamHtml,
    "Sin examen fisico cargado.",
    editable
  ), sectionOptions("physicalExam"));

  appendSection(sections, "Tratamientos sistemicos", renderEditableSectionBody(
    "systemicTreatments",
    renderPreviewTreatments("systemic"),
    "Sin tratamientos sistemicos registrados.",
    editable
  ), sectionOptions("systemicTreatments"));
  appendSection(sections, "Tratamientos radioterapicos", renderSectionHtml("radiotherapyTreatments", renderPreviewTreatments("radiotherapy")), sectionOptions("radiotherapyTreatments"));
  appendSection(sections, "Cirugias oncologicas", renderEditableSectionBody(
    "oncologicSurgeries",
    renderPreviewTreatments("surgery"),
    "Sin cirugias oncologicas registradas.",
    editable
  ), sectionOptions("oncologicSurgeries"));

  const summaryPlanHtml = [
    labeledParagraph("Conclusion / resumen", narrative.summary),
    labeledParagraph("Conducta / plan", narrative.plan)
  ].join("");
  appendSection(sections, "Conclusion / resumen", renderEditableSectionBody(
    "summaryPlan",
    summaryPlanHtml,
    "Sin conclusion o resumen cargado.",
    editable
  ), sectionOptions("summaryPlan"));

  appendSection(sections, "Actividad clinica cronologica", renderClinicalChronology({ editable }), {
    ...sectionOptions("evolutions"),
    completed: !printMode || getDiagnosisRecords().length > 0 || state.evolutions.length > 0 ||
      (state.prescriptions || []).length > 0 || (state.researchRecords || []).length > 0,
    showActions: false,
    showAudit: false
  });
  if (editable) sections.push(renderEvolutionAddAction());

  const patientMeta = [
    metaLine("HC", patient.medicalRecord),
    metaLine("DNI", patient.dni),
    metaLine("ID clinico", patient.liraId),
    metaLine("Fecha nac.", formatDateOptional(patient.birthDate, patient.birthDatePrecision)),
    metaLine("Sexo", patient.sex),
    metaLine("Fecha de fallecimiento", formatDateOptional(patient.deathDate, patient.deathDatePrecision)),
    metaLine("Obra social", patient.insurance),
    metaLine("Afiliado", patient.affiliateNumber),
    metaLine("Otras coberturas", formatAdditionalCoverages(patient)),
    metaLine("Telefono", patient.phone),
    metaLine("Domicilio", patient.address),
    metaLine("Email", patient.email)
  ].filter(Boolean).join("");
  const printInfo = printMode
    ? `<div class="print-info">Fecha de impresion: ${escapeHtml(formatDateTime(printedAt || printTimestamp || new Date()))}</div>`
    : "";
  const headerImportant = Boolean(state.meta.sectionMilestones?.patientHeader);

  return `
    <div class="clinical-document">
      <header class="sheet-header">
        <div class="sheet-header-main" data-highlight-scope data-highlight-kind="section" data-highlight-section-key="patientHeader">
          <span class="sheet-kicker">Historia clinica oncologica</span>
          <div class="sheet-title-row">
            <h2>${escapeHtml(patient.fullName || "Paciente")}</h2>
            ${editable ? `
              <div class="sheet-title-actions">
                <button class="icon-button" type="button" data-action="edit-patient" title="Modificar datos del paciente" aria-label="Modificar datos del paciente">
                  <svg class="inline-pencil-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20h9"></path><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L8 18l-4 1 1-4Z"></path></svg>
                </button>
                ${headerImportant ? `<span class="clinical-record-milestone" title="Evento destacado"><span class="milestone-dot" aria-hidden="true"></span></span>` : ""}
              </div>
            ` : ""}
          </div>
          ${patientMeta ? `<div class="patient-line">${patientMeta}</div>` : ""}
          ${printInfo}
        </div>
      </header>
      ${sections.length ? sections.join("") : `<div class="doc-empty">Sin datos clinicos cargados.</div>`}
    </div>
  `;
}

function hasClinicalDiagnosisSummary() {
  const oncology = state.oncology || {};
  return [
    oncology.diagnosis,
    oncology.diagnosisDate,
    oncology.topography,
    oncology.histology
  ].some(hasText) || hasAnyDiagnosticClassification() || getDiagnosisRecords().length > 0;
}

function renderClinicalDiagnosisSummary() {
  const oncology = state.oncology || {};
  const diagnosisRecords = [...getDiagnosisRecords()].sort((left, right) =>
    String(right.date || right.createdAt || "").localeCompare(
      String(left.date || left.createdAt || "")
    )
  );
  if (diagnosisRecords.length) {
    const summaries = diagnosisRecords.map((record, index) => `
      <section class="diagnosis-record-summary" data-diagnosis-record-id="${escapeAttr(record.id)}">
        <header>
          <strong>${escapeHtml(record.diagnosis || `Diagnóstico ${diagnosisRecords.length - index}`)}</strong>
          ${record.date
            ? `<time datetime="${escapeAttr(record.date)}">${escapeHtml(formatDateOptional(record.date, record.datePrecision))}</time>`
            : ""}
        </header>
        ${hasText(record.histology)
          ? `<div class="inline-facts diagnosis-facts"><span><strong>Histología:</strong> ${escapeHtml(record.histology)}</span></div>`
          : ""}
        ${renderDiagnosticClassificationSummary({ record })}
      </section>
    `).join("");
    return `<div class="diagnosis-record-summary-list">${summaries}</div>`;
  }
  const facts = [
    ["Diagnóstico", oncology.diagnosis],
    ["Fecha", formatDateOptional(oncology.diagnosisDate, oncology.diagnosisDatePrecision)],
    ["Topografía", oncology.topography],
    ["Histología", oncology.histology]
  ].filter(([, value]) => hasText(value));
  const clinicalFacts = facts.length
    ? `<div class="inline-facts diagnosis-facts">${facts.map(([label, value]) =>
      `<span><strong>${escapeHtml(label)}:</strong> ${escapeHtml(value)}</span>`
    ).join("")}</div>`
    : "";
  return `${clinicalFacts}${renderDiagnosticClassificationSummary({ editable: false })}`;
}

function formatAdditionalCoverages(patient = {}) {
  const primaryName = normalizeSearchText(patient.insurance).replace(/\s+/g, " ").trim();
  const primaryAffiliate = String(patient.affiliateNumber || "").replace(/\W/g, "").toLowerCase();
  const values = (Array.isArray(patient.coverages) ? patient.coverages : []).map((coverage) => {
    const name = coverage.obrasocial || coverage.obraSocial || coverage.nombre || coverage.descripcion || coverage.cobertura || "";
    const affiliate = coverage.numeroAfiliado || coverage.nroAfiliado || coverage.afiliado || "";
    const sameName = normalizeSearchText(name).replace(/\s+/g, " ").trim() === primaryName;
    const sameAffiliate = String(affiliate || "").replace(/\W/g, "").toLowerCase() === primaryAffiliate;
    if (sameName && (!primaryAffiliate || sameAffiliate)) return "";
    return [name, affiliate ? `Afiliado ${affiliate}` : ""].filter(hasText).join(" - ");
  }).filter(hasText);
  return [...new Map(values.map((value) => [normalizeSearchText(value), value])).values()].join("; ");
}

function formatLiraImportedFieldLabel(value) {
  const labels = {
    fecha: "Fecha", date: "Fecha", text: "Texto", texto: "Texto", descripcion: "Descripcion",
    diagnostico: "Diagnostico", especialidad: "Especialidad", resultado: "Resultado", valor: "Valor",
    nombre: "Nombre", title: "Titulo", label: "Tipo", fileName: "Archivo",
    cie10: "CIE-10", tipoDiagnostico: "Tipo de diagnostico", fechaCreacion: "Fecha de registro",
    fechaProcedimiento: "Fecha del procedimiento", anatomiaPatologica: "Anatomia patologica",
    enfermedadRecurrente: "Enfermedad recurrente", sincronico: "Sincronico",
    numeroBiopsia: "Numero de biopsia", detalleLocalizacion: "Localizacion",
    nombreArchivoOriginal: "Archivo", estadoExamen: "Estado", tipoExamen: "Tipo de estudio"
  };
  const key = String(value || "");
  if (labels[key]) return labels[key];
  const text = key.replace(/([a-z])([A-Z])/g, "$1 $2").replaceAll("_", " ").trim();
  return text ? `${text.charAt(0).toUpperCase()}${text.slice(1)}` : "Dato";
}

function cleanImportedLiraClinicalText(value, kind = "generic") {
  const original = String(value || "").replace(/\r/g, "").trim();
  if (!original) return "";
  const hasExtractionArtifacts = /(^|\n)\s*raw\s*[·•|:.-]|(^|\n)\s*(?:detail summary|resumen lineas|texto evolucion|marked|line count|fecha iso|source(?:group|endpoint))\s*[·•|:.-]|(^|\n)\s*(?:detail|fields|primary)\s*[·•|]|localizaciones? diagnostico.*propiedades?.*valor/im.test(original);
  if (!hasExtractionArtifacts) return original;

  const result = [];
  const requested = [];
  const tnm = {};
  const addUnique = (line) => {
    const cleaned = String(line || "").replace(/\s+/g, " ").replace(/\s+([,.;:])/g, "$1").trim();
    if (!cleaned || isLiraInterfaceNoise(cleaned)) return;
    const key = normalizeSearchText(cleaned).replace(/[^a-z0-9]+/g, " ").trim();
    if (!key || result.some((item) => normalizeSearchText(item).replace(/[^a-z0-9]+/g, " ").trim() === key)) return;
    result.push(cleaned);
  };

  original.split("\n").map((line) => line.trim()).filter(Boolean).forEach((line) => {
    if (/^(?:on|off|true|false|null|undefined)$/i.test(line)) return;
    if (/^raw\s*[·•|:.-]/i.test(line)) return;
    const separator = line.indexOf(":");
    if (separator < 0) {
      addUnique(line);
      return;
    }
    const rawLabel = line.slice(0, separator).trim();
    const content = line.slice(separator + 1).trim();
    const label = normalizeSearchText(rawLabel).replace(/[·•|]+/g, " ").replace(/\s+/g, " ").trim();
    const leafLabel = rawLabel.split(/[·•|]/).pop().trim();
    const leaf = normalizeSearchText(leafLabel);
    if (!content || isLiraInterfaceNoise(content)) return;
    if (/^(?:fecha|fecha iso|titulo|autor|profesional|patologo|matricula|origen|sourcegroup|sourceendpoint|id paciente|id local)$/.test(label)) return;
    if (/^fecha (?:creacion|pedido|atencion|inicio|fin|procedimiento|realizacion|diferida)$/.test(label)) return;
    if (/^(?:detail summary)(?:\s|$)/.test(label)) return;
    if (/^(?:line count|preview|encabezado|antecedentes|examen fisico|fecha iso|sourcegroup|sourceendpoint|id paciente|id local|principal|archivado|cycles count|consentimiento|fecha creacion|fecha pedido|fecha atencion|fecha diferida|fecha realizacion)$/.test(leaf)) return;
    if (/localizaciones? diagnostico.*propiedades?.*valor/.test(label)) return;
    if (/^(?:on|off|true|false|null|undefined)$/i.test(content)) return;

    if (kind === "evolution") {
      if (/^(?:texto evolucion|evolucion)$/.test(label) || label === "resumen lineas") {
        addUnique(content);
        return;
      }
      addUnique(`${formatLiraImportedFieldLabel(leafLabel)}: ${content}`);
      return;
    }

    if (label === "marked") {
      requested.push(formatLiraImportedFieldLabel(content));
      return;
    }
    if (/^(?:diagnostico|fecha pedido|tipo examen|origen)$/.test(label)) return;
    if (["t", "n", "m"].includes(leaf)) {
      tnm[leaf] = content;
      return;
    }
    if (/^(?:estado examen)$/.test(label)) {
      addUnique(`Estado: ${content}`);
      return;
    }
    if (/^(?:primary(?:\s*[·•|]\s*|\s+)otro|otro)$/.test(label)) {
      addUnique(`Otros estudios solicitados: ${content}`);
      return;
    }
    if (/^primary(?:\s|$)/.test(label)) {
      addUnique(`${formatLiraImportedFieldLabel(leafLabel)}: ${content}`);
      return;
    }
    if (/^detail(?:\s*[·•|]\s*|\s+)data/.test(label)) {
      if (content === "1" || /^(?:si|true)$/i.test(content)) requested.push(formatLiraImportedFieldLabel(leafLabel));
      else if (!/^(?:0|no|false|off)$/i.test(content)) addUnique(`${formatLiraImportedFieldLabel(leafLabel)}: ${content}`);
      return;
    }
    addUnique(`${formatLiraImportedFieldLabel(leafLabel)}: ${content}`);
  });

  if (requested.length) {
    const uniqueRequested = [...new Map(requested.map((item) => [normalizeSearchText(item), item])).values()];
    addUnique(`Solicitado: ${uniqueRequested.join(", ")}`);
  }
  const tnmText = ["t", "n", "m"].filter((key) => hasText(tnm[key])).map((key) => `${key.toUpperCase()}${tnm[key]}`).join(" ");
  if (tnmText) addUnique(`TNM: ${tnmText}`);
  return result.join("\n");
}

function cleanImportedLiraLocation(value) {
  const original = String(value || "").replace(/\r/g, "").trim();
  if (!original) return "";
  const parts = original.split(/\n|\s*[·•|]\s*/).map((part) => part.trim()).filter(Boolean);
  const meaningful = parts.filter((part) => {
    const normalized = normalizeSearchText(part).replace(/\s+/g, " ").trim();
    if (!normalized || /^(?:on|off|true|false|null|undefined|\d+)$/.test(normalized)) return false;
    if (/localizaciones? diagnostico|propiedades?|(?:^|\s)valor\s*:/.test(normalized)) return false;
    return !isLiraInterfaceNoise(part);
  });
  return [...new Map(meaningful.map((part) => [normalizeSearchText(part), part])).values()].join(" · ");
}

function cleanImportedLiraShortText(value) {
  const text = String(value || "").replace(/\s+/g, " ").trim();
  if (!text || /^(?:on|off|true|false|null|undefined)$/i.test(text)) return "";
  if (/localizaciones? diagnostico.*propiedades?.*valor/i.test(text)) return "";
  return isLiraInterfaceNoise(text) ? "" : text;
}

function isLiraInterfaceNoise(value) {
  const text = normalizeSearchText(value).replace(/\s+/g, " ").trim();
  if (!text) return true;
  if (/^(?:ver|x|close|archivo adjunto|adjuntos|evolucion \*|examen fisico|sintomas|ps|c)$/.test(text)) return true;
  return /^(?:fecha de atenciontipo|fecha iniciodescripcion|frecuencia cardiaca|frecuencia respiratoria|tension arterial)/.test(text);
}

function renderTreatmentsPlainText(kind) {
  return getTreatmentsByKind(kind)
    .map((item) => [formatTreatmentDateRange(item), item.scheme, item.intent, item.status, item.notes].filter(Boolean).join(" - "))
    .join("\n");
}

function renderPreviewTreatments(kind) {
  const treatments = getTreatmentsByKind(kind);
  return treatments.map((item) => {
    const head = [formatTreatmentDateRange(item), item.scheme].filter(hasText).join(" - ");
    const detail = [item.intent, item.status, item.notes].filter(hasText).join(". ");
    const content = `${head ? `<strong>${escapeHtml(head)}.</strong> ` : ""}${escapeHtml(detail)}`;
    return `
      <article class="doc-entry treatment-entry" data-clinical-date="${escapeAttr(item.date || "")}" data-highlight-scope data-highlight-kind="record" data-highlight-record-type="treatment" data-highlight-record-id="${escapeAttr(item.id || "")}">
        ${renderPassiveRecordMilestone(item)}
        <p>${content}</p>
        ${renderAuditLine(getRecordAudit(item, { action: "cargado" }), "entry-audit")}
      </article>
    `;
  }).join("");
}

function getTreatmentsByKind(kind) {
  return [...state.treatments]
    .filter((item) => getTreatmentKind(item) === kind)
    .sort((a, b) => (a.date || "").localeCompare(b.date || ""));
}

function getTreatmentKind(item) {
  const text = normalizeSearchText([item.scheme, item.intent, item.status, item.notes].filter(Boolean).join(" "));
  if (/\b(radio|radioterapia|imrt|3d|boost|ptv)\b/.test(text)) return "radiotherapy";
  if (/\b(cirugia|quirurg|prostatectomia|reseccion|mastectomia|colectomia|orquiectomia)\b/.test(text)) return "surgery";
  return "systemic";
}

function renderDocumentStudies() {
  const studies = [...state.studies].sort((a, b) => (a.date || "").localeCompare(b.date || ""));
  return studies.map((study) => {
    const head = withFinalPeriod([formatDateOptional(study.date, study.datePrecision), study.type, study.title].filter(hasText).join(" - "));
    const body = study.summary || getStudySourceLabel(study);
    return `
      <article class="doc-entry flush-entry study-entry" data-clinical-date="${escapeAttr(study.date || "")}" data-highlight-scope data-highlight-kind="record" data-highlight-record-type="study" data-highlight-record-id="${escapeAttr(study.id || "")}">
        ${renderPassiveRecordMilestone(study)}
        ${entryHead(head)}
        ${entryBody(body)}
        ${renderAuditLine(getRecordAudit(study, { action: "cargado" }), "entry-audit")}
      </article>
    `;
  }).join("");
}

function renderDocumentEvolutions({ editable = false, records = state.evolutions } = {}) {
  const evolutions = [...records].sort((a, b) => (a.date || "").localeCompare(b.date || ""));
  const entries = evolutions.map((entry) => {
    if (entry.deleted) return `
      <article class="doc-entry evolution-entry evolution-entry--deleted flush-entry" data-clinical-date="${escapeAttr(entry.date || "")}" data-highlight-scope data-highlight-kind="record" data-highlight-record-type="evolution" data-highlight-record-id="${escapeAttr(entry.id || "")}">
        <div class="evolution-deleted-notice">
          <i data-lucide="archive-x"></i>
          <span>El ${escapeHtml(formatDateTime(entry.deletedAt))} se elimino una evolucion cargada aqui.</span>
          <button class="section-action-button" type="button" data-action="view-evolution-history" data-id="${escapeAttr(entry.id)}" title="Ver historial de la evolucion"><i data-lucide="history"></i></button>
        </div>
      </article>`;
    const head = getEvolutionHead(entry);
    const actions = editable && !entry.immutable ? `
      <div class="evolution-line-actions">
        <button class="tiny-button" type="button" data-action="edit-evolution" data-id="${escapeAttr(entry.id)}" title="Modificar evolucion">
          <svg class="inline-pencil-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20h9"></path><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L8 18l-4 1 1-4Z"></path></svg>
        </button>
        ${(entry.history || []).length ? `<button class="tiny-button" type="button" data-action="view-evolution-history" data-id="${escapeAttr(entry.id)}" title="Historial de modificaciones"><i data-lucide="history"></i></button>` : ""}
        <button class="tiny-button milestone-button ${entry.highlighted ? "active" : ""}" type="button" data-action="toggle-milestone" data-id="${escapeAttr(entry.id)}" title="${entry.highlighted ? "Quitar hito" : "Marcar como hito"}" aria-pressed="${entry.highlighted ? "true" : "false"}">
          <span class="milestone-dot" aria-hidden="true"></span>
        </button>
      </div>
    ` : "";
    return `
      <article class="doc-entry evolution-entry flush-entry" data-clinical-date="${escapeAttr(entry.date || "")}" data-highlight-scope data-highlight-kind="record" data-highlight-record-type="evolution" data-highlight-record-id="${escapeAttr(entry.id || "")}">
        <div class="evolution-entry-rule">${actions}</div>
        ${entryHead(head)}
        ${entryBody(entry.text)}
        ${renderEvolutionAttachments(entry)}
        ${renderAuditLine(getRecordAudit(entry, { action: "cargado", lastName: extractLastName(entry.author) }), "entry-audit")}
      </article>
    `;
  }).join("");

  return entries;
}

function renderDiagnosisChronologyEntry(record) {
  const normalized = normalizeDiagnosisRecord(record);
  const head = withFinalPeriod([
    formatDateOptional(normalized.date, normalized.datePrecision),
    "Diagnóstico oncológico"
  ].filter(hasText).join(" - "));
  return `
    <article class="doc-entry evolution-entry diagnosis-chronology-entry flush-entry"
      data-clinical-date="${escapeAttr(normalized.date || "")}"
      data-diagnosis-record-id="${escapeAttr(normalized.id)}"
      data-highlight-scope
      data-highlight-kind="record"
      data-highlight-record-type="diagnosis"
      data-highlight-record-id="${escapeAttr(normalized.id)}">
      <div class="evolution-entry-rule" aria-hidden="true"></div>
      ${entryHead(head)}
      ${entryBody(diagnosisRecordPlainText(normalized))}
      ${renderAuditLine(getRecordAudit(normalized, { action: "cargado" }), "entry-audit")}
    </article>
  `;
}

function renderEvolutionAddAction() {
  return `
    <div class="clinical-add-action-stack">
      <button class="ghost-button evolution-add-button" type="button" data-action="open-evolution">
        <i data-lucide="plus"></i>
        <span>Agregar evolucion</span>
      </button>
      <button class="ghost-button diagnosis-add-button" type="button" data-action="open-diagnosis">
        <i data-lucide="stethoscope"></i>
        <span>Agregar diagnóstico</span>
      </button>
    </div>
  `;
}

function renderPassiveRecordMilestone(item) {
  if (!item?.highlighted) return "";
  return `<span class="clinical-record-milestone" title="Evento destacado"><span class="milestone-dot" aria-hidden="true"></span></span>`;
}

function getPrescriptionTypeLabel(type) {
  return { medication: "Receta", certificate: "Certificado", study: "Solicitud de estudio", free: "Indicacion", systemic: "Formulario sistémico" }[type] || "Prescripcion";
}

function getPrescriptionDetailLines(item) {
  const data = item.data || {};
  if (item.type === "medication") return [
    [data.generic, data.brand].filter(hasText).join(" / "),
    [data.presentation, data.form, data.quantity].filter(hasText).join(" - "),
    [data.dose, data.route, data.frequency, data.duration].filter(hasText).join(" - "),
    data.indication ? `Indicacion: ${data.indication}` : "",
    data.instructions ? `Instrucciones: ${data.instructions}` : ""
  ].filter(hasText);
  if (item.type === "certificate") return [
    [data.from ? `Desde ${formatDateOptional(data.from)}` : "", data.to ? `hasta ${formatDateOptional(data.to)}` : ""].filter(hasText).join(" "),
    data.text,
    data.includeDiagnosis ? "Incluye diagnostico" : ""
  ].filter(hasText);
  if (item.type === "study") return [
    [data.category, data.priority].filter(hasText).join(" - "), data.name,
    `Indicacion clinica: ${data.indication || ""}`,
    data.notes ? `Preparacion / observaciones: ${data.notes}` : ""
  ].filter(hasText);
  if (item.type === "systemic") {
    const fields = Array.isArray(data.fields) ? data.fields : [];
    const seen = new Set();
    const clinicalLines = fields.filter((field) => {
      if (field.kind === "checkbox") return field.value === true;
      if (!hasText(field.value)) return false;
      return !/^(?:patient|professional|exam)\./.test(String(field.localKey || ""))
        && !["today", "todayWithCity", "mendozaToday", "alwaysTrue", "currentYear", "blank"].includes(String(field.localKey || ""));
    }).map((field) => {
      const line = field.kind === "checkbox" ? field.label : `${field.label}: ${field.value}`;
      const key = normalizeSearchText(line);
      if (seen.has(key)) return "";
      seen.add(key);
      return line;
    }).filter(hasText);
    return [
      data.formTitle || item.title,
      ...clinicalLines,
      `${Array.isArray(data.pages) ? data.pages.length : 0} ${Array.isArray(data.pages) && data.pages.length === 1 ? "página" : "páginas"}`
    ].filter(hasText);
  }
  return [data.title, data.text].filter(hasText);
}

function renderDocumentPrescriptions(records = state.prescriptions || []) {
  return [...records].sort((a,b)=>(a.date||a.createdAt||"").localeCompare(b.date||b.createdAt||"")).map((item)=>`
    <article class="doc-entry flush-entry prescription-entry prescription-entry--${escapeAttr(item.type)}" data-clinical-date="${escapeAttr(item.date || String(item.createdAt || "").slice(0,10))}" data-highlight-scope data-highlight-kind="record" data-highlight-record-type="prescription" data-highlight-record-id="${escapeAttr(item.id || "")}">
      ${renderPassiveRecordMilestone(item)}
      <div class="evolution-entry-rule prescription-entry-rule" aria-hidden="true"></div>
      ${entryHead(withFinalPeriod([formatDateOptional(item.date || String(item.createdAt || "").slice(0,10), item.datePrecision), getPrescriptionTypeLabel(item.type), item.title].filter(hasText).join(" - ")))}
      <ul class="prescription-clinical-details">${getPrescriptionDetailLines(item).map((line)=>`<li>${escapeHtml(line)}</li>`).join("")}</ul>
      ${renderAuditLine(getRecordAudit(item,{action:"cargado"}),"entry-audit")}
    </article>`).join("");
}

function renderEvolutionAttachments(entry) {
  const attachments = normalizeEvolutionAttachments(entry.attachments);
  if (!attachments.length) return "";
  return `<div class="evolution-attachments" aria-label="Imagenes adjuntas">${attachments.map((attachment) => `
    <figure class="evolution-attachment">
      <button type="button" data-action="view-evolution-attachment" data-id="${escapeAttr(entry.id)}" data-attachment-id="${escapeAttr(attachment.id)}" title="Ampliar imagen adjunta">
        <img src="${escapeAttr(attachment.thumbnailUrl || attachment.url)}" alt="${escapeAttr(attachment.title)}">
        ${attachment.versionId !== "original" ? `<span><i data-lucide="pencil-line"></i>Anotada</span>` : ""}
      </button>
      <figcaption>
        <strong>${escapeHtml(attachment.title)}</strong>
        ${attachment.studyDate ? `<span>${escapeHtml(formatDateOptional(attachment.studyDate))}</span>` : ""}
        ${attachment.templateSource ? `<span>${escapeHtml(attachment.templateSource.license)}${attachment.templateSource.sourceUrl ? ` · <a href="${escapeAttr(attachment.templateSource.sourceUrl)}" target="_blank" rel="noopener">Fuente</a>` : ""}</span>` : ""}
      </figcaption>
    </figure>
  `).join("")}</div>`;
}

function renderDocumentResearchRecords(records = state.researchRecords || []) {
  return [...records].sort((a, b) => {
    const dateOrder = (a.date || "").localeCompare(b.date || "");
    return dateOrder || (a.createdAt || "").localeCompare(b.createdAt || "");
  }).map((record) => {
    const head = withFinalPeriod([formatDateOptional(record.date), "Investigacion clinica", record.protocol?.code, record.type].filter(hasText).join(" - "));
    return `
      <article class="doc-entry flush-entry research-clinical-entry" data-clinical-date="${escapeAttr(record.date || "")}" data-research-id="${escapeAttr(record.id)}" data-highlight-scope data-highlight-kind="record" data-highlight-record-type="research" data-highlight-record-id="${escapeAttr(record.id || "")}">
        ${renderPassiveRecordMilestone(record)}
        ${entryHead(head)}
        ${record.protocol?.name ? `<p class="research-clinical-title">${escapeHtml(record.protocol.name)}</p>` : ""}
        <ul class="research-clinical-details">${formatResearchRecordLines(record).map((line) => `<li><strong>${escapeHtml(line.label)}:</strong> ${escapeHtml(line.value)}</li>`).join("")}</ul>
        ${renderAuditLine(getRecordAudit(record, { action: "cargado" }), "entry-audit")}
      </article>
    `;
  }).join("");
}

function formatTreatmentDateRange(item = {}) {
  const start = formatDateOptional(item.date, item.datePrecision);
  const end = formatDateOptional(item.endDate, item.endDatePrecision);
  if (start && end && item.endDate !== item.date) return `Inicio ${start} · Fin ${end}`;
  return start || end;
}

function renderClinicalChronology({ editable = false } = {}) {
  return [
    ...getDiagnosisRecords().map((item)=>({kind:"diagnosis",item})),
    ...state.evolutions.map((item)=>({kind:"evolution",item})),
    ...(state.prescriptions || []).map((item)=>({kind:"prescription",item})),
    ...(state.researchRecords || []).map((item)=>({kind:"research",item}))
  ].sort((a,b)=>{
    const aKey=[a.item.date || String(a.item.createdAt || "").slice(0,10),a.item.createdAt || a.item.updatedAt || ""].join("|");
    const bKey=[b.item.date || String(b.item.createdAt || "").slice(0,10),b.item.createdAt || b.item.updatedAt || ""].join("|");
    return aKey.localeCompare(bKey);
  }).map(({kind,item})=>kind === "diagnosis"
    ? renderDiagnosisChronologyEntry(item)
    : kind === "evolution"
      ? renderDocumentEvolutions({editable,records:[item]})
      : kind === "research" ? renderDocumentResearchRecords([item]) : renderDocumentPrescriptions([item])).join("");
}

function renderPrescriptionsPlainText() {
  return [...(state.prescriptions || [])].sort((a,b)=>(a.date||a.createdAt||"").localeCompare(b.date||b.createdAt||"")).map((item)=>[
    [formatDateOptional(item.date || String(item.createdAt || "").slice(0,10), item.datePrecision), getPrescriptionTypeLabel(item.type), item.title].filter(hasText).join(" - "),
    ...getPrescriptionDetailLines(item)
  ].filter(hasText).join("\n")).join("\n\n");
}

function renderResearchRecordsPlainText() {
  return [...(state.researchRecords || [])]
    .sort((a, b) => (a.date || "").localeCompare(b.date || "") || (a.createdAt || "").localeCompare(b.createdAt || ""))
    .map((record) => [formatDateOptional(record.date), formatResearchRecordText(record)].filter(hasText).join("\n"))
    .join("\n\n");
}

function getEvolutionHead(entry) {
  const date = formatDateOptional(entry.date, entry.datePrecision);
  const author = entry.author || "";
  const specialty = getEvolutionSpecialty(entry);
  return withFinalPeriod([date, author, specialty].filter(hasText).join(" - "));
}

function getEvolutionSpecialty(entry) {
  return entry.specialty || entry.reason || "";
}

function renderExamFacts() {
  const facts = [
    metaLine("Peso", state.exam.weightKg ? `${state.exam.weightKg} kg` : ""),
    metaLine("Talla", state.exam.heightM ? `${normalizeHeightCm(state.exam.heightM)} cm` : ""),
    metaLine("IMC", $("#bmiOutput").value),
    metaLine("Superficie corporal", $("#bsaOutput").value)
  ].filter(Boolean).join("");

  return facts ? `<div class="inline-facts">${facts}</div>` : "";
}

function renderPhysicalExam(value) {
  return getPhysicalExamRows(value)
    .map((row) => `<p class="physical-exam-line"><strong>${escapeHtml(row.label)}:</strong> ${escapeHtml(row.text)}</p>`)
    .join("");
}

function formatPhysicalExamPlainText(value) {
  return getPhysicalExamRows(value)
    .map((row) => `${row.label}: ${row.text}`)
    .join("\n");
}

function getPhysicalExamRows(value) {
  const text = cleanPhysicalExamText(value);
  if (!text) return [];

  const markers = findPhysicalExamMarkers(text);
  if (!markers.length) {
    return [{ label: "Estado general", text }];
  }

  const rows = [];
  const general = cleanPhysicalExamSegment(text.slice(0, markers[0].start));
  if (general) rows.push({ label: "Estado general", text: general });

  markers.forEach((marker, index) => {
    const nextStart = markers[index + 1]?.start ?? text.length;
    const segment = cleanPhysicalExamSegment(text.slice(marker.end, nextStart));
    if (segment) rows.push({ label: marker.label, text: segment });
  });

  return rows.length ? rows : [{ label: "Estado general", text }];
}

function findPhysicalExamMarkers(text) {
  const patterns = [
    { label: "T\u00f3rax", regex: /\b(?:aparato respiratorio|respiratorio|t[o\u00f3]rax)\b\s*:?\s*/gi },
    { label: "Coraz\u00f3n", regex: /\b(?:aparato cardiovascular|cardiovascular|coraz[o\u00f3]n)\b\s*:?\s*/gi },
    { label: "Abdomen", regex: /\babdomen\b\s*:?\s*/gi },
    { label: "SNC", regex: /\b(?:sistema nervioso central|snc)\b\s*:?\s*/gi },
    { label: "Tacto rectal", regex: /\btacto rectal\b\s*:?\s*/gi }
  ];

  const matches = patterns.flatMap(({ label, regex }) => {
    const found = [];
    let match = regex.exec(text);
    while (match) {
      found.push({ label, start: match.index, end: regex.lastIndex });
      match = regex.exec(text);
    }
    regex.lastIndex = 0;
    return found;
  });

  return matches
    .sort((a, b) => a.start - b.start || b.end - a.end)
    .filter((match, index, sorted) => !sorted.slice(0, index).some((previous) => match.start < previous.end));
}

function cleanPhysicalExamText(value) {
  return String(value ?? "")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/^examen f[i\u00ed]sico(?: al ingreso)?\s*:?\s*/i, "");
}

function cleanPhysicalExamSegment(value) {
  return String(value ?? "")
    .trim()
    .replace(/^[.:;\-\s]+/, "")
    .replace(/^(?:estado general|general|aparato respiratorio|respiratorio|t[o\u00f3]rax|aparato cardiovascular|cardiovascular|coraz[o\u00f3]n|abdomen|sistema nervioso central|snc|tacto rectal)\s*:?\s*/i, "")
    .trim();
}

function appendSection(sections, title, body, { key = "", editable = false, completed = true, showActions = true, showAudit = true } = {}) {
  if (!completed) return;
  if (!hasText(stripHtml(body))) return;
  const actions = editable && key && showActions ? renderSectionActions(key) : "";
  const audit = key && showAudit ? renderAuditLine(getSectionAudit(key), "section-audit") : "";
  sections.push(`
    <section class="doc-section" ${key ? `data-section-key="${escapeAttr(key)}" data-highlight-scope data-highlight-kind="section" data-highlight-section-key="${escapeAttr(key)}"` : ""}>
      ${(hasText(title) || actions) ? `
        <div class="section-heading">
          ${hasText(title) ? `<h3>${escapeHtml(title)}</h3>` : "<span></span>"}
          ${actions}
        </div>
      ` : ""}
      ${audit}
      ${body}
    </section>
  `);
}

function renderSectionActions(sectionKey) {
  const hasHistory = getSectionVersions(sectionKey).length > 0;
  const isImportant = Boolean(state.meta.sectionMilestones?.[sectionKey]);
  const isEmptyInlineLoadSection = isLocalClinicalHistory() &&
    supportsInlineSectionLoad(sectionKey) &&
    !isSectionCompletedForPrint(sectionKey);
  const actionLabel = isEmptyInlineLoadSection ? "Cargar seccion" : "Modificar seccion";
  return `
    <div class="section-actions">
      ${isEmptyInlineLoadSection ? "" : `
        <button class="section-action-button" type="button" data-action="edit-section" data-section-key="${escapeAttr(sectionKey)}" title="${actionLabel}" aria-label="${actionLabel}">
          <svg class="inline-pencil-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20h9"></path><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L8 18l-4 1 1-4Z"></path></svg>
        </button>
      `}
      ${isEmptyInlineLoadSection ? "" : `
        <button class="section-action-button section-milestone-button ${isImportant ? "active" : ""}" type="button" data-action="toggle-section-milestone" data-section-key="${escapeAttr(sectionKey)}" title="${isImportant ? "Quitar marca importante" : "Marcar seccion como importante"}" aria-pressed="${isImportant ? "true" : "false"}">
          <span class="milestone-dot" aria-hidden="true"></span>
        </button>
      `}
      ${hasHistory ? `
        <button class="section-action-button" type="button" data-action="view-section-history" data-section-key="${escapeAttr(sectionKey)}" title="Historial de modificaciones">
          <i data-lucide="history"></i>
        </button>
      ` : ""}
    </div>
  `;
}

function isSectionCompletedForPrint(sectionKey) {
  if (!usesStructuredSectionEditor(sectionKey) && hasText(getLatestSectionVersion(sectionKey)?.content)) {
    return true;
  }

  const oncology = state.oncology;
  const narrative = state.narrative;

  const bySection = {
    diagnosis: () => [oncology.diagnosis, oncology.diagnosisDate, oncology.stage].some(hasText),
    chiefComplaint: () => hasText(narrative.chiefComplaint),
    currentIllness: () => hasText(narrative.currentIllness),
    personalHistory: () => [
      narrative.backgroundClinical,
      narrative.currentMedication,
      narrative.familyOncology,
      narrative.gynecology
    ].some(hasText),
    studies: () => state.studies.some((study) => [
      study.date,
      study.type,
      study.title,
      study.summary,
      getStudySourceLabel(study)
    ].some(hasText)),
    physicalExam: () => [
      state.exam.weightKg,
      state.exam.heightM,
      narrative.physicalExam
    ].some(hasText),
    systemicTreatments: () => getTreatmentsByKind("systemic").length > 0,
    radiotherapyTreatments: () => getTreatmentsByKind("radiotherapy").length > 0,
    oncologicSurgeries: () => getTreatmentsByKind("surgery").length > 0,
    summaryPlan: () => [narrative.summary, narrative.plan].some(hasText),
    evolutions: () => state.evolutions.length > 0 || (state.researchRecords || []).length > 0,
    prescriptions: () => (state.prescriptions || []).length > 0,
    diagnosticClassifications: () => hasAnyDiagnosticClassification()
  };

  return bySection[sectionKey]?.() || false;
}

function touchSectionAuditForField(path) {
  const sectionKey = getSectionKeyForField(path);
  if (!sectionKey) return;
  state.meta.sectionAudit ??= {};
  state.meta.sectionAudit[sectionKey] = buildAuditStamp("modificado");
}

function getSectionKeyForField(path) {
  const byPath = {
    "oncology.diagnosis": "diagnosis",
    "oncology.diagnosisDate": "diagnosis",
    "oncology.stage": "diagnosis",
    "oncology.topography": "diagnosis",
    "narrative.chiefComplaint": "chiefComplaint",
    "narrative.currentIllness": "currentIllness",
    "narrative.backgroundClinical": "personalHistory",
    "narrative.currentMedication": "personalHistory",
    "narrative.familyOncology": "personalHistory",
    "narrative.gynecology": "personalHistory",
    "narrative.physicalExam": "physicalExam",
    "exam.weightKg": "physicalExam",
    "exam.heightM": "physicalExam",
    "narrative.summary": "summaryPlan",
    "narrative.plan": "summaryPlan"
  };
  return byPath[path] || "";
}

function getSectionAudit(sectionKey) {
  const latest = getLatestSectionVersion(sectionKey);
  if (latest) return getVersionAudit(latest);
  const stored = state.meta.sectionAudit?.[sectionKey];
  if (stored) return normalizeAudit(stored, { action: "modificado" });
  return buildAuditStamp("cargado", { at: state.meta.createdAt || state.meta.updatedAt });
}

function getVersionAudit(version) {
  return normalizeAudit(version.audit || {
    lastName: version.author,
    license: version.license,
    at: version.createdAt,
    action: "modificado"
  }, { action: "modificado" });
}

function getRecordAudit(record, fallback = {}) {
  return normalizeAudit(record?.audit || {
    lastName: record?.professionalLastName || fallback.lastName,
    license: record?.license || fallback.license,
    at: record?.updatedAt || record?.createdAt || fallback.at || state.meta.updatedAt || state.meta.createdAt,
    action: fallback.action || "cargado"
  }, fallback);
}

function buildAuditStamp(action = "modificado", overrides = {}) {
  const professional = getCurrentProfessional();
  return {
    action,
    lastName: overrides.lastName || professional.lastName,
    license: overrides.license || professional.license,
    at: overrides.at || new Date().toISOString()
  };
}

function getCurrentProfessional() {
  const professional = state?.meta?.currentProfessional || {};
  return {
    lastName: professional.lastName || extractLastName(state?.meta?.currentUser) || "Profesional",
    license: professional.license || state?.meta?.currentLicense || "s/d"
  };
}

function normalizeAudit(audit = {}, fallback = {}) {
  const professional = getCurrentProfessional();
  return {
    action: audit.action || fallback.action || "cargado",
    lastName: audit.lastName || fallback.lastName || professional.lastName,
    license: audit.license || fallback.license || professional.license || "s/d",
    at: audit.at || fallback.at || state?.meta?.updatedAt || state?.meta?.createdAt || ""
  };
}

function renderAuditLine(audit, className = "") {
  const text = formatAuditLine(audit);
  return text ? `<p class="audit-line ${className}">${escapeHtml(text)}</p>` : "";
}

function formatAuditLine(audit) {
  if (!audit) return "";
  const normalized = normalizeAudit(audit);
  const actionLabel = normalized.action === "cargado" ? "Cargado por" : "Modificado por";
  const parts = [
    normalized.lastName,
    `Mat. ${normalized.license || "s/d"}`,
    normalized.at ? formatDateTime(normalized.at) : ""
  ].filter(hasText);
  return `${actionLabel}: ${parts.join(" - ")}`;
}

function extractLastName(value) {
  const text = String(value || "").trim();
  if (!text) return "";
  const withoutPrefix = text.replace(/^(dr\.?|dra\.?|prof\.?|comite)\s+/i, "").trim();
  if (!withoutPrefix) return text;
  if (withoutPrefix.includes(",")) return withoutPrefix.split(",")[0].trim();
  const parts = withoutPrefix.split(/\s+/);
  return parts.length > 1 ? parts.slice(1).join(" ") : parts[0];
}

function renderSectionHtml(sectionKey, defaultHtml) {
  const version = usesStructuredSectionEditor(sectionKey) ? null : getLatestSectionVersion(sectionKey);
  return version ? `<p class="section-free-text">${escapeHtml(version.content)}</p>` : defaultHtml;
}

function usesStructuredSectionEditor(sectionKey) {
  return sectionKey === "studies" ||
    state?.meta?.sectionFormModes?.[sectionKey] === "structured";
}

function getSectionTitle(sectionKey) {
  return {
    diagnosis: "Diagnostico oncologico",
    chiefComplaint: "Motivo de consulta",
    currentIllness: "Antecedentes de enfermedad actual",
    personalHistory: "Antecedentes personales",
    studies: "Estudios complementarios",
    physicalExam: "Examen fisico",
    systemicTreatments: "Tratamientos sistemicos",
    radiotherapyTreatments: "Tratamientos radioterapicos",
    oncologicSurgeries: "Cirugias oncologicas",
    summaryPlan: "Conclusion / resumen",
    evolutions: "Evoluciones"
  }[sectionKey] || "";
}

function getSectionCurrentText(sectionKey) {
  if (usesStructuredSectionEditor(sectionKey)) return getSectionDefaultText(sectionKey);
  return getLatestSectionVersion(sectionKey)?.content || getSectionDefaultText(sectionKey);
}

function getLatestSectionVersion(sectionKey) {
  return getSectionVersions(sectionKey).at(-1) || null;
}

function getSectionVersions(sectionKey) {
  state.meta.sectionVersions ??= {};
  if (!Array.isArray(state.meta.sectionVersions[sectionKey])) {
    state.meta.sectionVersions[sectionKey] = [];
  }
  return state.meta.sectionVersions[sectionKey];
}

function getSectionDefaultText(sectionKey) {
  const oncology = state.oncology;
  const narrative = state.narrative;
  const bySection = {
    diagnosis: () => [
      plainMeta("Diagnostico", oncology.diagnosis),
      plainMeta("Fecha de diagnostico", formatDateOptional(oncology.diagnosisDate, oncology.diagnosisDatePrecision)),
      plainMeta("Topografia / localizacion", oncology.topography),
      plainMeta("Histologia / anatomia patologica", oncology.histology),
      plainMeta("Estadificacion", oncology.stage),
      plainMeta("Biomarcadores", oncology.biomarkers),
      plainMeta("Estado", oncology.status),
      plainMeta("Intencion", oncology.intent),
      plainMeta("Performance status", oncology.performanceStatus)
    ].filter(Boolean).join("\n"),
    chiefComplaint: () => narrative.chiefComplaint,
    currentIllness: () => narrative.currentIllness,
    personalHistory: () => [
      plainMeta("Clinicos / quirurgicos", narrative.backgroundClinical),
      plainMeta("Medicacion habitual", narrative.currentMedication),
      plainMeta("Oncofamiliares", narrative.familyOncology),
      plainMeta("Gineco-obstetricos", narrative.gynecology)
    ].filter(Boolean).join("\n"),
    studies: () => studiesSectionText(state.studies),
    physicalExam: () => [
      plainMeta("Peso", state.exam.weightKg ? `${state.exam.weightKg} kg` : ""),
      plainMeta("Talla", state.exam.heightM ? `${normalizeHeightCm(state.exam.heightM)} cm` : ""),
      formatPhysicalExamPlainText(narrative.physicalExam)
    ].filter(Boolean).join("\n"),
    systemicTreatments: () => renderTreatmentsPlainText("systemic"),
    radiotherapyTreatments: () => renderTreatmentsPlainText("radiotherapy"),
    oncologicSurgeries: () => renderTreatmentsPlainText("surgery"),
    summaryPlan: () => [
      plainMeta("Conclusion / resumen", narrative.summary),
      plainMeta("Conducta / plan", narrative.plan)
    ].filter(Boolean).join("\n"),
    evolutions: () => [
      ...getDiagnosisRecords().map((record) => ({
        kind: "diagnosis",
        date: record.date,
        createdAt: record.createdAt,
        record
      })),
      ...state.evolutions.filter((entry) => !entry.deleted).map((entry) => ({
        kind: "evolution",
        date: entry.date,
        createdAt: entry.createdAt,
        record: entry
      }))
    ]
      .sort((left, right) =>
        [left.date || "", left.createdAt || ""].join("|")
          .localeCompare([right.date || "", right.createdAt || ""].join("|"))
      )
      .map(({ kind, record }) => kind === "diagnosis"
        ? [
            [formatDateOptional(record.date, record.datePrecision), "Diagnóstico oncológico"].filter(hasText).join(" - "),
            diagnosisRecordPlainText(record)
          ].filter(hasText).join("\n")
        : [
            getEvolutionHead(record),
            record.text,
            ...normalizeEvolutionAttachments(record.attachments).map((attachment) => `Imagen adjunta: ${attachment.title}${attachment.caption ? ` - ${attachment.caption}` : ""}`)
          ].filter(hasText).join("\n"))
      .join("\n\n")
  };

  return bySection[sectionKey]?.() || "";
}

function paragraph(value) {
  return hasText(value) ? `<p>${escapeHtml(value)}</p>` : "";
}

function mutedParagraph(value) {
  return hasText(value) ? `<p class="doc-muted">${escapeHtml(value)}</p>` : "";
}

function labeledParagraph(label, value) {
  return hasText(value) ? `<p><strong>${escapeHtml(label)}:</strong> ${escapeHtml(value)}</p>` : "";
}

function entryHead(value) {
  return hasText(value) ? `<p class="entry-head"><strong>${escapeHtml(value)}</strong></p>` : "";
}

function entryBody(value) {
  if (!hasText(value)) return "";
  const points = String(value).split(/\r?\n/).map((line) => line.replace(/^\s*[-•]\s*/, "").trim()).filter(Boolean);
  if (points.length <= 1) return `<p class="entry-body">${escapeHtml(points[0] || value)}</p>`;
  return `<ul class="entry-points">${points.map((point) => `<li>${escapeHtml(point)}</li>`).join("")}</ul>`;
}

function withFinalPeriod(value) {
  const text = String(value ?? "").trim();
  if (!text) return "";
  return /[.!?]$/.test(text) ? text : `${text}.`;
}

function detailBlock(label, value) {
  return hasText(value) ? `<p class="detail-block"><strong>${escapeHtml(label)}:</strong> ${escapeHtml(value)}</p>` : "";
}

function metaLine(label, value) {
  return hasText(value) ? `<span><strong>${escapeHtml(label)}:</strong> ${escapeHtml(value)}</span>` : "";
}

function formatDateOptional(value, precision = "day") {
  return hasText(value) ? formatTimelineDateByPrecision(value, precision) : "";
}

function formatDayDateTime(value) {
  if (!hasText(value)) return "";
  return new Intl.DateTimeFormat("es-AR", {
    weekday: "long",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function hasText(value) {
  return String(value ?? "").trim() !== "";
}

function stripHtml(value) {
  return String(value || "").replace(/<[^>]*>/g, "").trim();
}

function truncateText(value, maxLength = 220) {
  const text = String(value ?? "").replace(/\s+/g, " ").trim();
  if (text.length <= maxLength) return text;
  return `${text.slice(0, maxLength - 3).trim()}...`;
}

function normalizeSearchText(value) {
  return String(value ?? "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
}

function copySummary() {
  collectBindableFields();
  copyText(getClinicalDocumentText(), "Hoja copiada");
}

function createEmptyState() {
  return {
    meta: {
      version: 1,
      currentUser: "Oncologia",
      currentProfessional: {
        lastName: "Oncologia",
        license: "s/d"
      },
      createdAt: null,
      updatedAt: null,
      sectionAudit: {},
      sectionVersions: {},
      sectionFormModes: {},
      sectionMilestones: {},
      clinicalHighlights: []
    },
    patient: {
      fullName: "",
      dni: "",
      medicalRecord: "",
      birthDate: "",
      birthDatePrecision: "day",
      phone: "",
      insurance: "",
      affiliateNumber: "",
      address: "",
      email: "",
      sex: "",
      deathDate: "",
      deathDatePrecision: "day",
      liraId: "",
      coverages: []
    },
    oncology: {
      diagnosis: "",
      topography: "",
      histology: "",
      diagnosisDate: "",
      diagnosisDatePrecision: "day",
      stage: "",
      intent: "En estudio",
      status: "En estudio",
      performanceStatus: "",
      biomarkers: "",
      diagnosisRecords: [],
      diagnosticClassifications: emptyDiagnosticClassifications(),
      tnm: normalizeOncologyTnm({})
    },
    exam: {
      weightKg: "",
      heightM: ""
    },
    narrative: {
      chiefComplaint: "",
      currentIllness: "",
      backgroundClinical: "",
      currentMedication: "",
      familyOncology: "",
      gynecology: "",
      physicalExam: "",
      summary: "",
      plan: ""
    },
    evolutions: [],
    treatments: [],
    studies: [],
    externalStudies: [],
    prescriptions: [],
    researchRecords: []
  };
}

function normalizeState(raw) {
  const merged = deepMerge(createEmptyState(), raw || {});
  const rawProfessional = raw?.meta?.currentProfessional || null;
  const liraImported = Boolean(merged.meta?.liraImport?.patientId);
  if (liraImported) {
    canonicalizeLiraImportedState(merged);
    merged.meta.liraImport.presentationVersion = Math.max(Number(merged.meta.liraImport.presentationVersion || 0), LIRA_PRESENTATION_VERSION);
    merged.oncology.diagnosis = cleanImportedLiraClinicalText(merged.oncology.diagnosis, "generic");
    merged.oncology.topography = cleanImportedLiraLocation(merged.oncology.topography);
    merged.oncology.histology = cleanImportedLiraClinicalText(merged.oncology.histology, "generic");
    merged.oncology.stage = cleanImportedLiraShortText(merged.oncology.stage);
    merged.oncology.biomarkers = cleanImportedLiraClinicalText(merged.oncology.biomarkers, "generic");
    Object.keys(merged.narrative).forEach((key) => {
      merged.narrative[key] = cleanImportedLiraClinicalText(merged.narrative[key], "generic");
    });
  }
  merged.patient.birthDatePrecision = normalizeDatePrecision(merged.patient.birthDatePrecision);
  merged.patient.deathDatePrecision = normalizeDatePrecision(merged.patient.deathDatePrecision);
  if (hasText(merged.exam?.heightM)) {
    const normalizedHeight = normalizeHeightMeters(merged.exam.heightM);
    merged.exam.heightM = normalizedHeight > 0 ? String(normalizedHeight) : "";
  }
  merged.oncology.diagnosisDatePrecision = normalizeDatePrecision(merged.oncology.diagnosisDatePrecision);
  merged.oncology.diagnosticClassifications = normalizeDiagnosticClassifications(
    merged.oncology.diagnosticClassifications
  );
  merged.oncology.tnm = normalizeOncologyTnm(merged.oncology.tnm);
  merged.oncology.diagnosisRecords = normalizeDiagnosisRecords(
    raw?.oncology?.diagnosisRecords || merged.oncology.diagnosisRecords,
    merged.oncology
  ).map((record) => normalizeRecordForAudit(
    record,
    merged,
    { action: "cargado" }
  ));
  merged.meta.currentProfessional = normalizeProfessional(rawProfessional || {}, merged.meta.currentUser);
  merged.meta.createdAt ||= merged.meta.updatedAt || null;
  merged.meta.sectionAudit = merged.meta.sectionAudit && typeof merged.meta.sectionAudit === "object"
    ? merged.meta.sectionAudit
    : {};
  merged.meta.sectionVersions = merged.meta.sectionVersions && typeof merged.meta.sectionVersions === "object"
    ? merged.meta.sectionVersions
    : {};
  merged.meta.sectionFormModes = merged.meta.sectionFormModes && typeof merged.meta.sectionFormModes === "object"
    ? merged.meta.sectionFormModes
    : {};
  if (String(merged.meta?.liraImport?.origin || "") === "local") {
    LOCAL_STRUCTURED_SECTION_KEYS.forEach((sectionKey) => {
      const versions = merged.meta.sectionVersions[sectionKey];
      if (merged.meta.sectionFormModes[sectionKey] !== "structured" &&
          (!Array.isArray(versions) || versions.length === 0)) {
        merged.meta.sectionFormModes[sectionKey] = "structured";
      }
    });
  }
  merged.meta.sectionMilestones = merged.meta.sectionMilestones && typeof merged.meta.sectionMilestones === "object"
    ? merged.meta.sectionMilestones
    : {};
  merged.meta.clinicalHighlights = normalizeClinicalHighlights(raw?.meta?.clinicalHighlights || raw?.clinicalHighlights || merged.meta.clinicalHighlights);
  merged.evolutions = Array.isArray(merged.evolutions)
    ? merged.evolutions.map((item) => {
      const normalized = normalizeRecordForAudit(item, merged, { lastName: extractLastName(item.author), action: "cargado" });
      normalized.id ||= makeId("evolution");
      normalized.datePrecision = normalizeDatePrecision(normalized.datePrecision);
      normalized.text = cleanImportedLiraClinicalText(normalized.text, "evolution");
      normalized.attachments = normalizeEvolutionAttachments(normalized.attachments);
      return normalized;
    })
    : [];
  merged.treatments = Array.isArray(merged.treatments)
    ? merged.treatments.map((item) => {
      const normalized = normalizeRecordForAudit(item, merged, { action: "cargado" });
      normalized.id ||= makeId("treatment");
      normalized.datePrecision = normalizeDatePrecision(normalized.datePrecision);
      normalized.endDatePrecision = normalizeDatePrecision(normalized.endDatePrecision);
      if (liraImported) {
        normalized.scheme = cleanImportedLiraShortText(normalized.scheme);
        normalized.intent = cleanImportedLiraShortText(normalized.intent);
        normalized.status = cleanImportedLiraShortText(normalized.status);
        normalized.notes = cleanImportedLiraClinicalText(normalized.notes, "generic");
      }
      return normalized;
    })
    : [];
  merged.studies = Array.isArray(merged.studies)
    ? merged.studies.map((item) => {
      const normalized = normalizeRecordForAudit(item, merged, { action: "cargado" });
      normalized.id ||= makeId("study");
      normalized.datePrecision = normalizeDatePrecision(normalized.datePrecision);
      normalized.summary = cleanImportedLiraClinicalText(normalized.summary, "study");
      if (liraImported) {
        normalized.title = cleanImportedLiraShortText(normalized.title) || "Estudio complementario";
        normalized.type = cleanImportedLiraShortText(normalized.type) || "Estudio complementario";
      }
      normalized.templateSource = normalizeStudyTemplateSource(normalized.templateSource);
      normalized.imageAssets = normalizeStudyImageAssets(normalized.imageAssets);
      return normalized;
    })
    : [];
  merged.externalStudies = Array.isArray(merged.externalStudies)
    ? merged.externalStudies.map((item) => {
      const normalized = normalizeExternalStudy(item);
      normalized.datePrecision = normalizeDatePrecision(normalized.datePrecision);
      if (liraImported) {
        normalized.title = cleanImportedLiraShortText(normalized.title) || "Documento clinico";
        normalized.summary = cleanImportedLiraClinicalText(normalized.summary, "study");
      }
      return normalized;
    })
    : [];
  merged.prescriptions = Array.isArray(merged.prescriptions)
    ? merged.prescriptions.map((item) => ({
      ...item,
      id: item.id || makeId("prescription"),
      datePrecision: normalizeDatePrecision(item.datePrecision),
      ...(liraImported ? {
        title: cleanImportedLiraShortText(item.title),
        summary: cleanImportedLiraClinicalText(item.summary, "generic")
      } : {})
    }))
    : [];
  merged.researchRecords = Array.isArray(merged.researchRecords)
    ? merged.researchRecords.map((item) => normalizeResearchRecord(item, merged))
    : [];
  merged.meta.sectionVersions = merged.meta.sectionVersions && typeof merged.meta.sectionVersions === "object"
    ? merged.meta.sectionVersions
    : {};
  Object.entries(merged.meta.sectionVersions).forEach(([sectionKey, versions]) => {
    if (!Array.isArray(versions)) {
      merged.meta.sectionVersions[sectionKey] = [];
      return;
    }
    merged.meta.sectionVersions[sectionKey] = versions.map((version) => normalizeSectionVersionForAudit(version, merged));
  });
  merged.meta.aiTimelineEvents = linkTimelineEventsToClinicalRecords(merged.meta.aiTimelineEvents || [], merged);
  selectedStudyId = (merged.externalStudies.length ? merged.externalStudies : merged.studies)[0]?.id || null;
  return merged;
}

function canonicalizeLiraImportedState(value) {
  const importMeta = value?.meta?.liraImport;
  if (!importMeta || typeof importMeta !== "object") return value;
  const mirrorKeys = [
    "antecedents", "admissionEvents", "previousTreatments", "practices", "referrals",
    "support", "scales", "attachments"
  ];
  const hasArchivedSources = Boolean(importMeta.auditRef || (importMeta.sourceTables && typeof importMeta.sourceTables === "object"));
  if (!hasArchivedSources) {
    const legacy = importMeta.legacyClinicalSources && typeof importMeta.legacyClinicalSources === "object"
      ? importMeta.legacyClinicalSources
      : {};
    mirrorKeys.forEach((key) => {
      if (Array.isArray(value[key]) && value[key].length && !Array.isArray(legacy[key])) legacy[key] = value[key];
    });
    if (Array.isArray(value.oncology?.diagnoses) && value.oncology.diagnoses.length && !Array.isArray(legacy.diagnoses)) {
      legacy.diagnoses = value.oncology.diagnoses;
    }
    if (Object.keys(legacy).length) importMeta.legacyClinicalSources = legacy;
  }
  if (!importMeta.auditRef && Array.isArray(value.sourcePages) && value.sourcePages.length && !Array.isArray(importMeta.sourcePages)) {
    importMeta.sourcePages = value.sourcePages;
  }
  mirrorKeys.forEach((key) => delete value[key]);
  delete value.sourcePages;
  if (value.oncology && typeof value.oncology === "object") delete value.oncology.diagnoses;
  importMeta.nativeMappingVersion = LIRA_PRESENTATION_VERSION;
  return value;
}

function normalizeDatePrecision(value) {
  return ["day", "month", "year"].includes(value) ? value : "day";
}

function normalizeClinicalHighlights(value) {
  if (!Array.isArray(value)) return [];
  return value.slice(0, 1000).map((item) => {
    const kind = item?.kind === "record" ? "record" : item?.kind === "section" ? "section" : "";
    const exact = String(item?.exact || "").slice(0, 10000);
    const start = Math.max(0, Number.parseInt(item?.start, 10) || 0);
    const end = Math.max(start, Number.parseInt(item?.end, 10) || start + exact.length);
    if (!kind || !exact) return null;
    return {
      id: String(item.id || makeId("clinical-highlight")),
      kind,
      recordType: String(item.recordType || ""),
      recordId: String(item.recordId || ""),
      sectionKey: String(item.sectionKey || ""),
      start,
      end,
      exact,
      prefix: String(item.prefix || "").slice(-64),
      suffix: String(item.suffix || "").slice(0, 64),
      color: "yellow",
      createdAt: item.createdAt || "",
      removedAt: item.removedAt || ""
    };
  }).filter((item) => item && ((item.kind === "record" && item.recordType && item.recordId) || (item.kind === "section" && item.sectionKey)));
}

function normalizeExternalStudy(study = {}, index = 0) {
  const links = Array.isArray(study.links) ? study.links : [];
  const reportLink = links.find((link) => /informe/i.test(link.label || ""));
  const studyLink = links.find((link) => /estudio/i.test(link.label || ""));
  const attachmentBytes = Number(study.bytes ?? study.attachmentBytes ?? study.liraAttachment?.bytes ?? 0);

  return {
    id: study.id || `ext-study-${index + 1}`,
    date: study.date || "",
    datePrecision: study.datePrecision || "day",
    type: study.type || study.modality || "Otro",
    modality: study.modality || study.type || "",
    title: study.title || "Estudio",
    source: study.source || "",
    summary: study.summary || "",
    reportUrl: absolutizePangeaUrl(study.reportUrl || reportLink?.href || ""),
    studyUrl: absolutizePangeaUrl(study.studyUrl || studyLink?.href || ""),
    previewImageUrl: study.previewImageUrl || "",
    displayImageUrls: Array.isArray(study.displayImageUrls) ? study.displayImageUrls : [],
    imageUrls: Array.isArray(study.imageUrls) ? study.imageUrls : [],
    documentImageUrls: Array.isArray(study.documentImageUrls) ? study.documentImageUrls : [],
    imageCount: Number(study.imageCount || 0),
    wadoImageCount: Number(study.wadoImageCount || 0),
    documentImageCount: Number(study.documentImageCount || 0),
    imageImportedAt: study.imageImportedAt || "",
    imageAssets: normalizeStudyImageAssets(study.imageAssets),
    liraAttachmentId: study.liraAttachmentId || study.liraAttachment?.liraAttachmentId || "",
    fileName: study.fileName || study.attachmentFileName || study.liraAttachment?.fileName || "",
    contentType: study.contentType || study.attachmentContentType || study.liraAttachment?.contentType || "",
    bytes: Number.isSafeInteger(attachmentBytes) && attachmentBytes > 0 ? attachmentBytes : 0
  };
}

function normalizeStudyTemplateSource(source) {
  if (!source || typeof source !== "object" || Array.isArray(source)) return null;
  const originValue = String(source.origin || "").trim().toLowerCase();
  const configurationIdValue = String(source.configurationId || "").trim();
  const revisionValue = Number(source.revision);
  const normalized = {
    id: String(source.id || ""),
    category: String(source.category || ""),
    origin: originValue === "bundled" ? "builtin" : ["builtin", "custom"].includes(originValue) ? originValue : "",
    configurationId: /^[1-9]\d{0,17}$/.test(configurationIdValue) ? configurationIdValue : "",
    revision: Number.isSafeInteger(revisionValue) && revisionValue > 0 ? revisionValue : null,
    sourceLabel: String(source.sourceLabel || ""),
    sourceUrl: /^https:\/\//i.test(String(source.sourceUrl || "")) ? String(source.sourceUrl) : "",
    author: String(source.author || ""),
    license: String(source.license || ""),
    licenseUrl: /^https:\/\//i.test(String(source.licenseUrl || "")) ? String(source.licenseUrl) : "",
    attribution: String(source.attribution || ""),
    sha256: /^[a-f0-9]{64}$/i.test(String(source.sha256 || "")) ? String(source.sha256).toLowerCase() : ""
  };
  return Object.values(normalized).some(hasText) ? normalized : null;
}

function normalizeEvolutionAttachment(attachment = {}) {
  return {
    id: attachment.id || makeId("evo-image"),
    studyId: attachment.studyId || "",
    imageId: attachment.imageId || "",
    versionId: attachment.versionId || "original",
    url: attachment.url || "",
    thumbnailUrl: attachment.thumbnailUrl || attachment.url || "",
    title: attachment.title || "Imagen de estudio",
    studyDate: attachment.studyDate || "",
    studyType: attachment.studyType || "Imagen",
    templateSource: normalizeStudyTemplateSource(attachment.templateSource),
    caption: attachment.caption || "",
    audit: attachment.audit || null,
    createdAt: attachment.createdAt || attachment.audit?.at || ""
  };
}

function normalizeEvolutionAttachments(attachments) {
  return Array.isArray(attachments) ? attachments.filter((attachment) => attachment?.url).map(normalizeEvolutionAttachment) : [];
}

function normalizeResearchRecord(record = {}, metaState) {
  const normalized = deepMerge({
    id: "",
    date: "",
    type: "",
    protocol: { name: "", code: "", phase: "", sponsor: "", center: "" },
    participant: { code: "", status: "", arm: "", randomizationCode: "", consentStatus: "", consentDate: "", consentVersion: "", eligibility: "", ineligibilityReason: "" },
    clinical: { diagnosis: "", histology: "", stage: "", biomarkers: "", ecog: "", intervention: "", treatmentLine: "", cycle: "", day: "" },
    assessment: { criteria: "", response: "", date: "", results: "" },
    safety: { event: "", grade: "", relation: "", action: "" },
    followUp: { samples: "", deviations: "", nextVisit: "", pending: "", notes: "" }
  }, record);
  normalized.id ||= makeId("research");
  return normalizeRecordForAudit(normalized, metaState, { action: "cargado" });
}

function absolutizePangeaUrl(url) {
  if (!url) return "";
  try {
    const parsed = new URL(url, "http://pangeasystem.com/buscar_estudios_externos/");
    return ["http:", "https:"].includes(parsed.protocol) ? parsed.href : "";
  } catch (error) {
    return "";
  }
}

function normalizeProfessional(professional = {}, currentUser = "") {
  return {
    lastName: professional.lastName || extractLastName(currentUser) || "Oncologia",
    license: professional.license || professional.matricula || "s/d"
  };
}

function normalizeRecordForAudit(record, metaState, fallback = {}) {
  const item = record || {};
  const audit = normalizeAuditWithMeta(item.audit || {
    lastName: item.professionalLastName || fallback.lastName,
    license: item.license || fallback.license,
    at: item.updatedAt || item.createdAt || metaState.meta.updatedAt || metaState.meta.createdAt,
    action: fallback.action || "cargado"
  }, metaState, fallback);
  item.audit = audit;
  item.createdAt ||= audit.at;
  item.updatedAt ||= audit.at;
  return item;
}

function normalizeSectionVersionForAudit(version, metaState) {
  const item = version || {};
  item.audit = normalizeAuditWithMeta(item.audit || {
    lastName: item.author,
    license: item.license,
    at: item.createdAt || metaState.meta.updatedAt || metaState.meta.createdAt,
    action: "modificado"
  }, metaState, { action: "modificado" });
  item.author ||= item.audit.lastName;
  item.license ||= item.audit.license;
  item.createdAt ||= item.audit.at;
  return item;
}

function normalizeAuditWithMeta(audit = {}, metaState, fallback = {}) {
  const professional = normalizeProfessional(metaState?.meta?.currentProfessional || {}, metaState?.meta?.currentUser || "");
  return {
    action: audit.action || fallback.action || "cargado",
    lastName: audit.lastName || fallback.lastName || professional.lastName,
    license: audit.license || fallback.license || professional.license || "s/d",
    at: audit.at || fallback.at || metaState?.meta?.updatedAt || metaState?.meta?.createdAt || ""
  };
}

function deepMerge(target, source) {
  for (const [key, value] of Object.entries(source || {})) {
    if (value && typeof value === "object" && !Array.isArray(value)) {
      target[key] = deepMerge(target[key] || {}, value);
    } else {
      target[key] = value;
    }
  }
  return target;
}

function getByPath(object, path) {
  return path.split(".").reduce((current, key) => current?.[key], object);
}

function setByPath(object, path, value) {
  const keys = path.split(".");
  const last = keys.pop();
  const target = keys.reduce((current, key) => {
    current[key] ??= {};
    return current[key];
  }, object);
  target[last] = value;
}

function parseNumber(value) {
  return Number(String(value || "").replace(",", "."));
}

function normalizeHeightCm(value) {
  const numeric = parseNumber(value);
  if (!Number.isFinite(numeric) || numeric <= 0) return 0;
  const centimeters = numeric <= 3 ? numeric * 100 : numeric;
  return Math.round(centimeters * 10) / 10;
}

function normalizeHeightMeters(value) {
  const centimeters = normalizeHeightCm(value);
  return centimeters > 0 ? Math.round((centimeters / 100) * 10_000) / 10_000 : 0;
}

function calculateAnthropometrics(weightValue, heightValue) {
  const weightKg = parseNumber(weightValue);
  const heightCm = normalizeHeightCm(heightValue);
  const heightM = heightCm > 0 ? heightCm / 100 : 0;
  const valid = Number.isFinite(weightKg) && weightKg > 0 && heightM > 0;
  return {
    weightKg: valid ? weightKg : 0,
    heightCm,
    heightM,
    bmi: valid ? weightKg / (heightM * heightM) : 0,
    bodySurface: valid ? 0.007184 * Math.pow(weightKg, 0.425) * Math.pow(heightCm, 0.725) : 0
  };
}

function round(value, decimals) {
  return Number(value).toFixed(decimals);
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function formatDate(dateValue) {
  if (!dateValue) return "Sin fecha";
  const [year, month, day] = String(dateValue).slice(0, 10).split("-");
  if (!year || !month || !day) return dateValue;
  return `${day}/${month}/${year}`;
}

function formatDateTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Sin fecha";
  return date.toLocaleString("es-AR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function extractDate(text) {
  const match = String(text || "").match(/(\d{4})[-/](\d{2})[-/](\d{2})|(\d{2})[-/](\d{2})[-/](\d{4})/);
  if (!match) return "";
  if (match[1]) return `${match[1]}-${match[2]}-${match[3]}`;
  return `${match[6]}-${match[5]}-${match[4]}`;
}

function makeId(prefix) {
  const random = crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${random}`;
}

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

function copyText(text, message) {
  navigator.clipboard?.writeText(text).then(() => toast(message)).catch(() => {
    const area = document.createElement("textarea");
    area.value = text;
    document.body.appendChild(area);
    area.select();
    document.execCommand("copy");
    area.remove();
    toast(message);
  });
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeAttr(value) {
  return escapeHtml(value);
}

function toast(message) {
  const element = $("#toast");
  element.textContent = message;
  element.classList.add("show");
  clearTimeout(element.hideTimer);
  element.hideTimer = setTimeout(() => element.classList.remove("show"), 2200);
}

function refreshIcons() {
  if (window.lucide) window.lucide.createIcons();
}

function setButtonIcon(button, iconName) {
  const icon = button.querySelector("svg, i");
  if (!icon) {
    button.insertAdjacentHTML("afterbegin", `<i data-lucide="${iconName}"></i>`);
    return;
  }

  icon.outerHTML = `<i data-lucide="${iconName}"></i>`;
}
