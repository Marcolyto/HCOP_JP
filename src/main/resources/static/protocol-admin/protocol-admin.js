"use strict";

if (new URLSearchParams(window.location.search).get("embedded") === "1") {
  document.body.classList.add("embedded-protocol-admin");
}

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const state = {
  protocols: [],
  coirCatalog: [],
  current: null,
  selectedId: "",
  loading: false,
};
const componentMeta = new WeakMap();
let searchTimer = 0;

function icons(root = document) {
  if (window.lucide?.createIcons) window.lucide.createIcons({ root });
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"]/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;",
  })[char]);
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    cache: "no-store",
    headers: { Accept: "application/json", ...(options.body ? { "Content-Type": "application/json" } : {}) },
    ...options,
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.error || "No se pudo completar la operación.");
  return payload;
}

function toast(message, type = "success") {
  const item = document.createElement("div");
  item.className = `toast${type === "error" ? " error" : ""}`;
  item.innerHTML = `<i data-lucide="${type === "error" ? "triangle-alert" : "circle-check"}"></i><span>${escapeHtml(message)}</span>`;
  $("#toastRegion").append(item);
  icons(item);
  setTimeout(() => item.remove(), 4200);
}

function notifyCatalogChanged() {
  try { localStorage.setItem("hcop-protocol-catalog-updated", String(Date.now())); } catch {}
}

function setServiceStatus(message, type = "ready") {
  const target = $("#serviceStatus");
  target.className = `header-status ${type}`;
  target.innerHTML = `<i data-lucide="${type === "error" ? "database-zap" : "database"}"></i><span>${escapeHtml(message)}</span>`;
  icons(target);
}

function formatMinutes(value) {
  const minutes = Number(value);
  if (!Number.isInteger(minutes) || minutes < 1) return "—";
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return [hours ? `${hours} h` : "", rest ? `${rest} min` : ""].filter(Boolean).join(" ");
}

async function loadCatalog({ keepSelection = true } = {}) {
  state.loading = true;
  try {
    const [protocolPayload, coirPayload] = await Promise.all([
      api("/api/clinical/protocols?includeArchived=1&includeCatalog=1"),
      api("/api/clinical/coir-catalog"),
    ]);
    state.protocols = protocolPayload.protocols || [];
    state.coirCatalog = coirPayload.catalog || [];
    const customCount = Number(protocolPayload.currentCount) || 0;
    const catalogCount = state.protocols.filter((item) => item.catalogOnly).length;
    const totalCount = customCount + catalogCount;
    $("#protocolCount").textContent = `${totalCount} disponibles`;
    populateCoirOptions();
    renderProtocolList();
    setServiceStatus(`${customCount} propios · ${catalogCount} COIR disponibles`);
    if (keepSelection && state.selectedId && state.protocols.some((item) => item.id === state.selectedId)) {
      await openProtocol(state.selectedId, { scroll: false });
    }
  } catch (error) {
    setServiceStatus("La base clínica local no responde", "error");
    $("#protocolList").innerHTML = `<div class="list-empty"><strong>No se pudo cargar el catálogo</strong><span>${escapeHtml(error.message)}</span></div>`;
  } finally {
    state.loading = false;
  }
}

function populateCoirOptions() {
  const select = $("#protocolCoirLink");
  const currentValue = select.value;
  select.innerHTML = '<option value="">Sin vínculo operativo</option>' + state.coirCatalog
    .filter((item) => item.entryType === "treatment")
    .map((item) => `<option value="${escapeHtml(item.coirSchemeId)}">${escapeHtml(item.schemeName)} · ${escapeHtml(item.durationText)}</option>`)
    .join("");
  select.value = currentValue;
}

function visibleProtocols() {
  const needle = $("#protocolSearch").value.trim().toLocaleLowerCase("es");
  const showArchived = $("#showArchived").checked;
  const showCoir = $("#showCoir").checked;
  return state.protocols.filter((item) => {
    if (item.catalogOnly && !showCoir) return false;
    if (!item.catalogOnly && !item.active && !showArchived) return false;
    return !needle || `${item.name} ${item.category}`.toLocaleLowerCase("es").includes(needle);
  });
}

function renderProtocolList() {
  const list = $("#protocolList");
  const rows = visibleProtocols();
  if (!rows.length) {
    list.innerHTML = '<div class="list-empty"><i data-lucide="search-x"></i><strong>No hay resultados</strong><span>Pruebe otra búsqueda o active los filtros.</span></div>';
    icons(list);
    return;
  }
  list.innerHTML = rows.map((item) => {
    const classes = ["protocol-item", item.id === state.selectedId ? "active" : "", !item.active ? "is-archived" : "", item.catalogOnly ? "is-catalog" : ""].filter(Boolean).join(" ");
    const meta = item.catalogOnly
      ? `${item.componentCount || 0} drogas · ${item.durationText || formatMinutes(item.durationMinutes)}${item.componentCount ? "" : " · pendiente de completar"}`
      : `${item.componentCount || 0} drogas · ${item.durationText || "sin duración"}${item.active ? "" : " · archivado"}`;
    return `<button class="${classes}" type="button" role="option" aria-selected="${item.id === state.selectedId}" data-protocol-id="${escapeHtml(item.id)}"><small>${escapeHtml(item.category)}</small><strong>${escapeHtml(item.name)}</strong><em>${escapeHtml(meta)}</em><span>${item.catalogOnly ? "COIR" : escapeHtml(item.id)}</span></button>`;
  }).join("");
  icons(list);
}

async function openProtocol(id, { scroll = true } = {}) {
  state.selectedId = String(id);
  renderProtocolList();
  try {
    setEditorBusy(true);
    const payload = await api(`/api/clinical/protocols/${encodeURIComponent(id)}`);
    state.current = payload.protocol;
    renderEditor(payload.protocol);
    if (scroll) $(".editor-panel").scrollTop = 0;
  } catch (error) {
    toast(error.message, "error");
  } finally {
    setEditorBusy(false);
  }
}

function setEditorBusy(busy) {
  state.loading = busy;
  $$(".editor-actions button").forEach((button) => { button.disabled = busy; });
}

function blankProtocol() {
  return {
    id: null,
    name: "",
    category: "",
    description: "",
    cycleDays: 21,
    durationMinutes: 120,
    active: true,
    catalogOnly: false,
    coirLinks: [],
    components: [],
  };
}

function createNewProtocol(seed = null) {
  state.selectedId = "";
  state.current = {
    ...blankProtocol(),
    ...(seed || {}),
    id: null,
    catalogOnly: false,
    components: (seed?.components || []).map((item) => ({ ...item, id: "" })),
  };
  renderProtocolList();
  renderEditor(state.current);
  $(".editor-panel").scrollTop = 0;
  setTimeout(() => $("#protocolName").focus(), 0);
}

window.HcopProtocolAdminHelpNavigation = Object.freeze({
  showEditor() {
    if ($("#protocolForm")?.hidden) createNewProtocol();
  },
  currentView() {
    return $("#protocolForm")?.hidden ? "catalog" : "editor";
  }
});

function renderEditor(protocol) {
  $("#emptyEditor").hidden = true;
  $("#protocolForm").hidden = false;
  const catalogOnly = Boolean(protocol.catalogOnly);
  $("#catalogPromotion").hidden = !catalogOnly;
  $("#editorEyebrow").textContent = catalogOnly ? "Catálogo operativo COIR" : protocol.id ? `Protocolo ${protocol.id}` : "Nuevo protocolo";
  $("#editorTitle").textContent = protocol.name || "Nuevo protocolo";
  $("#editorSubtitle").textContent = catalogOnly
    ? "Revise las drogas, la preparación y la duración importadas antes de convertirlo."
    : "Los cambios se aplican a Protocolos y al alta de tratamientos.";
  $("#protocolName").value = protocol.name || "";
  $("#protocolCategory").value = protocol.category === "COIR sin vincular" ? "" : protocol.category || "";
  $("#protocolCycleDays").value = protocol.cycleDays || "";
  $("#protocolDuration").value = protocol.durationMinutes || "";
  $("#protocolDescription").value = protocol.description || "";
  $("#protocolActive").checked = protocol.active !== false;
  $("#protocolCoirLink").value = protocol.coirLinks?.[0]?.coirSchemeId || protocol.coirSchemeId || "";
  if (protocol.coirSchemeId && !$("#protocolCoirLink").value) {
    const option = document.createElement("option");
    option.value = protocol.coirSchemeId;
    option.textContent = `${protocol.name} · ${protocol.durationText || formatMinutes(protocol.durationMinutes)}`;
    $("#protocolCoirLink").append(option);
    $("#protocolCoirLink").value = protocol.coirSchemeId;
  }
  const stateBadge = $("#protocolStateBadge");
  stateBadge.textContent = protocol.active === false ? "Archivado" : catalogOnly ? "Sin vincular" : "Activo";
  stateBadge.classList.toggle("archived", protocol.active === false || catalogOnly);
  $("#duplicateBtn").hidden = !protocol.id || catalogOnly;
  $("#archiveBtn").hidden = !protocol.id || catalogOnly;
  $("#archiveBtn span").textContent = protocol.active === false ? "Restaurar" : "Archivar";
  $("#saveProtocolBtn").disabled = catalogOnly;
  $$("#protocolForm input, #protocolForm select, #protocolForm textarea").forEach((control) => {
    if (!control.closest("#catalogPromotion")) control.disabled = catalogOnly;
  });
  $("#components").innerHTML = "";
  for (const component of protocol.components || []) addComponent(component);
  if (!catalogOnly && !(protocol.components || []).length && !protocol.id) addComponent();
  renumberComponents();
  renderPreview();
  icons($("#protocolForm"));
}

function addComponent(data = {}) {
  const fragment = $("#componentTemplate").content.cloneNode(true);
  const card = $(".component-card", fragment);
  card.draggable = true;
  const applications = data.applications || data.instructions || [];
  const preparation = applications[0] || null;
  componentMeta.set(card, { sourcePayload: data.sourcePayload || null, preparation });
  $(".drug-id", card).value = data.drugId || "";
  $(".drug-name", card).value = data.drugName || "";
  $(".drug-name", card).dataset.linkedName = data.drugName || "";
  $(".drug-day", card).value = data.day || "1";
  $(".drug-dose", card).value = data.prescribedDoseText || "";
  $(".drug-unit", card).value = data.doseUnit || data.unidadDosis || data.unidad || "";
  $(".drug-calculation", card).value = data.doseCalculationMethod || "Fija";
  $(".drug-route", card).value = data.route || "Endovenosa";
  $(".drug-time", card).value = data.administrationTime || "";
  $(".drug-day-hospital", card).checked = data.dayHospital !== false;
  card.dataset.componentId = data.id || "";
  fillPreparation(card, preparation);
  updateLinkedDrugInfo(card, {
    id: data.drugId,
    name: data.drugName,
    instructions: applications,
    presentations: data.presentations || [],
  });
  wireComponent(card);
  $("#components").append(fragment);
  renumberComponents();
  renderPreview();
  icons(card);
  return card;
}

function fillPreparation(card, preparation) {
  const data = preparation || {};
  $(".preparation-id", card).value = data.id || "";
  $(".prep-presentation", card).value = data.presentationReferences || "";
  $(".prep-reconstituent", card).value = data.reconstituent || "";
  $(".prep-concentration", card).value = data.concentration || "";
  $(".prep-diluent", card).value = data.diluent || "";
  $(".prep-volume", card).value = data.finalVolume || "";
  $(".prep-route", card).value = data.route || $(".drug-route", card).value || "";
  $(".prep-room-stability", card).value = data.stabilityRoomTemperature || "";
  $(".prep-cold-stability", card).value = data.stabilityRefrigerated || "";
  $(".prep-laboratory", card).value = data.laboratory || "";
  $(".prep-photosensitive", card).checked = Boolean(data.photosensitive);
  $(".prep-guide", card).value = data.infusionGuide || "";
  $(".prep-observations", card).value = data.preparationObservations || "";
  $(".prep-label", card).value = data.labelObservations || "";
  $(".preparation-dirty", card).checked = false;
  $(".preparation-summary", card).textContent = preparation ? "Preparación vinculada" : "Sin preparación registrada";
}

function updateLinkedDrugInfo(card, drug) {
  const linked = Boolean(drug?.id && drug?.name);
  $(".component-title", card).textContent = drug?.name || $(".drug-name", card).value || "Nueva droga";
  $(".component-link-state", card).textContent = linked ? `Vinculada a droga ${drug.id}` : "Droga escrita manualmente, sin vínculo de catálogo";
  const info = $(".linked-drug-info", card);
  info.hidden = !linked;
  if (linked) {
    const instructionCount = drug.instructions?.length || 0;
    const presentationCount = drug.presentations?.length || 0;
    info.innerHTML = `<span><b>${instructionCount}</b> ${instructionCount === 1 ? "preparación" : "preparaciones"}</span><span><b>${presentationCount}</b> ${presentationCount === 1 ? "presentación" : "presentaciones"}</span>`;
  }
}

function wireComponent(card) {
  const drugInput = $(".drug-name", card);
  drugInput.addEventListener("input", () => {
    if (drugInput.value !== drugInput.dataset.linkedName) {
      $(".drug-id", card).value = "";
      updateLinkedDrugInfo(card, null);
    }
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => searchDrugs(card, drugInput.value), 250);
    renderPreview();
  });
  drugInput.addEventListener("focus", () => {
    if (drugInput.value.trim().length >= 2) searchDrugs(card, drugInput.value);
  });
  $(".drug-results", card).addEventListener("click", (event) => {
    const button = event.target.closest("[data-drug-index]");
    if (!button) return;
    const results = componentMeta.get(card)?.searchResults || [];
    const drug = results[Number(button.dataset.drugIndex)];
    if (drug) selectDrug(card, drug);
  });
  $(".remove-component", card).addEventListener("click", () => {
    card.remove();
    if (!$(".component-card", $("#components"))) addComponent();
    renumberComponents();
    renderPreview();
  });
  $(".move-up", card).addEventListener("click", () => {
    if (card.previousElementSibling) card.parentElement.insertBefore(card, card.previousElementSibling);
    renumberComponents(); renderPreview();
  });
  $(".move-down", card).addEventListener("click", () => {
    if (card.nextElementSibling) card.parentElement.insertBefore(card.nextElementSibling, card);
    renumberComponents(); renderPreview();
  });
  card.addEventListener("dragstart", (event) => { event.dataTransfer.setData("text/plain", "component"); card.classList.add("dragging"); });
  card.addEventListener("dragend", () => { card.classList.remove("dragging"); renumberComponents(); renderPreview(); });
  card.addEventListener("dragover", (event) => {
    event.preventDefault();
    const dragging = $(".component-card.dragging", $("#components"));
    if (dragging && dragging !== card) card.parentElement.insertBefore(dragging, event.offsetY > card.offsetHeight / 2 ? card.nextElementSibling : card);
  });
  $$("input, select, textarea", card).forEach((control) => control.addEventListener("input", () => {
    if (control.closest(".preparation-content") && !control.classList.contains("preparation-dirty")) {
      $(".preparation-dirty", card).checked = true;
      $(".preparation-summary", card).textContent = "Cambios pendientes";
    }
    renderPreview();
  }));
  card.addEventListener("focusout", () => setTimeout(() => { $(".drug-results", card).hidden = true; }, 160));
}

async function searchDrugs(card, query) {
  const term = String(query || "").trim();
  const target = $(".drug-results", card);
  if (term.length < 2) { target.hidden = true; return; }
  try {
    const payload = await api(`/api/clinical/drugs?q=${encodeURIComponent(term)}`);
    const results = payload.drugs || [];
    const meta = componentMeta.get(card) || {};
    meta.searchResults = results;
    componentMeta.set(card, meta);
    target.innerHTML = results.length ? results.map((drug, index) => `<button class="drug-result" type="button" data-drug-index="${index}"><strong>${escapeHtml(drug.name)}</strong><small>ID ${escapeHtml(drug.id)} · ${drug.instructions?.length || 0} preparaciones · ${drug.presentations?.length || 0} presentaciones</small></button>`).join("") : '<div class="list-empty"><span>No hay coincidencias. Puede conservar el nombre sin vincular.</span></div>';
    target.hidden = false;
  } catch (error) {
    target.innerHTML = `<div class="list-empty"><span>${escapeHtml(error.message)}</span></div>`;
    target.hidden = false;
  }
}

function selectDrug(card, drug) {
  $(".drug-id", card).value = drug.id || "";
  $(".drug-name", card).value = drug.name || "";
  $(".drug-name", card).dataset.linkedName = drug.name || "";
  const preparation = drug.instructions?.[0] || null;
  const meta = componentMeta.get(card) || {};
  meta.preparation = preparation;
  componentMeta.set(card, meta);
  fillPreparation(card, preparation);
  if (!$(".drug-route", card).value && preparation?.route) $(".drug-route", card).value = preparation.route;
  updateLinkedDrugInfo(card, drug);
  $(".drug-results", card).hidden = true;
  renderPreview();
}

function renumberComponents() {
  $$(".component-card", $("#components")).forEach((card, index, cards) => {
    $(".component-order", card).textContent = String(index + 1).padStart(2, "0");
    $(".move-up", card).disabled = index === 0;
    $(".move-down", card).disabled = index === cards.length - 1;
  });
}

function componentDraft(card) {
  const meta = componentMeta.get(card) || {};
  return {
    id: card.dataset.componentId || "",
    drugId: $(".drug-id", card).value,
    drugName: $(".drug-name", card).value.trim(),
    day: $(".drug-day", card).value.trim(),
    prescribedDoseText: $(".drug-dose", card).value.trim(),
    doseUnit: $(".drug-unit", card).value.trim(),
    doseCalculationMethod: $(".drug-calculation", card).value,
    route: $(".drug-route", card).value.trim(),
    administrationTime: $(".drug-time", card).value.trim(),
    dayHospital: $(".drug-day-hospital", card).checked,
    sourcePayload: meta.sourcePayload || undefined,
  };
}

function preparationDraft(card) {
  if (!$(".preparation-dirty", card).checked) return null;
  const component = componentDraft(card);
  if (!component.drugId) throw new Error(`Vincule ${component.drugName || "la droga"} antes de guardar su preparación.`);
  const meta = componentMeta.get(card) || {};
  return {
    id: $(".preparation-id", card).value,
    drugId: component.drugId,
    drugName: component.drugName,
    presentationReferences: $(".prep-presentation", card).value.trim(),
    reconstituent: $(".prep-reconstituent", card).value.trim(),
    concentration: $(".prep-concentration", card).value.trim(),
    diluent: $(".prep-diluent", card).value.trim(),
    finalVolume: $(".prep-volume", card).value.trim(),
    route: $(".prep-route", card).value.trim(),
    stabilityRoomTemperature: $(".prep-room-stability", card).value.trim(),
    stabilityRefrigerated: $(".prep-cold-stability", card).value.trim(),
    laboratory: $(".prep-laboratory", card).value.trim(),
    photosensitive: $(".prep-photosensitive", card).checked,
    infusionGuide: $(".prep-guide", card).value.trim(),
    preparationObservations: $(".prep-observations", card).value.trim(),
    labelObservations: $(".prep-label", card).value.trim(),
    sourcePayload: meta.preparation?.sourcePayload || undefined,
  };
}

function formDraft() {
  const cards = $$(".component-card", $("#components"));
  const preparations = cards.map(preparationDraft).filter(Boolean);
  return {
    name: $("#protocolName").value.trim(),
    category: $("#protocolCategory").value.trim(),
    description: $("#protocolDescription").value.trim(),
    cycleDays: $("#protocolCycleDays").value || null,
    durationMinutes: $("#protocolDuration").value || null,
    coirSchemeId: $("#protocolCoirLink").value || null,
    active: $("#protocolActive").checked,
    components: cards.map(componentDraft),
    preparations,
  };
}

function renderPreview() {
  const target = $("#protocolPreview");
  if (!target || $("#protocolForm").hidden) return;
  const name = $("#protocolName").value.trim() || "Protocolo sin nombre";
  const category = $("#protocolCategory").value.trim() || "Grupo por definir";
  const duration = formatMinutes($("#protocolDuration").value);
  const cycle = $("#protocolCycleDays").value || "—";
  const components = $$(".component-card", $("#components")).map(componentDraft);
  const summary = `<article class="preview-summary"><span>${escapeHtml(category)}</span><h3>${escapeHtml(name)}</h3><div class="preview-metrics"><span><b>${escapeHtml(cycle)}</b><small>días por ciclo</small></span><span><b>${components.length}</b><small>drogas</small></span><span><b>${escapeHtml(duration)}</b><small>duración operativa</small></span></div></article>`;
  const drugs = components.length
    ? `<div class="preview-drugs">${components.map((item) => `<article class="preview-drug"><span>Día ${escapeHtml(item.day || "—")}</span><div><strong>${escapeHtml(item.drugName || "Droga sin definir")}</strong><small>${escapeHtml([item.route, item.administrationTime, item.dayHospital ? "Hospital de día" : "Sin sillón"].filter(Boolean).join(" · "))}</small></div><em>${escapeHtml([item.prescribedDoseText, item.doseUnit].filter(Boolean).join(" ") || "—")}</em></article>`).join("")}</div>`
    : '<div class="preview-empty">Agregue drogas para completar la vista previa.</div>';
  target.innerHTML = summary + drugs;
}

async function saveProtocol(event) {
  event.preventDefault();
  if (!$("#protocolForm").reportValidity()) return;
  try {
    setEditorBusy(true);
    const input = formDraft();
    const isNew = !state.current?.id;
    const payload = await api(isNew ? "/api/clinical/protocols" : `/api/clinical/protocols/${encodeURIComponent(state.current.id)}`, {
      method: isNew ? "POST" : "PUT",
      body: JSON.stringify(input),
    });
    state.current = payload.protocol;
    state.selectedId = payload.protocol.id;
    notifyCatalogChanged();
    toast(isNew ? "Protocolo creado y disponible en el sistema." : "Protocolo actualizado correctamente.");
    await loadCatalog({ keepSelection: true });
  } catch (error) {
    toast(error.message, "error");
  } finally {
    setEditorBusy(false);
  }
}

async function archiveOrRestore() {
  if (!state.current?.id) return;
  if (state.current.active === false) {
    $("#protocolActive").checked = true;
    $("#protocolForm").requestSubmit();
    return;
  }
  if (!window.confirm("El protocolo dejará de aparecer al crear tratamientos. Sus datos y tratamientos históricos se conservarán. ¿Desea archivarlo?")) return;
  try {
    setEditorBusy(true);
    const payload = await api(`/api/clinical/protocols/${encodeURIComponent(state.current.id)}`, { method: "DELETE" });
    state.current = payload.protocol;
    notifyCatalogChanged();
    toast("Protocolo archivado sin eliminar datos históricos.");
    await loadCatalog({ keepSelection: true });
  } catch (error) {
    toast(error.message, "error");
  } finally {
    setEditorBusy(false);
  }
}

function promoteCurrentCatalogEntry() {
  if (!state.current?.catalogOnly) return;
  const source = state.current;
  createNewProtocol({
    name: source.name,
    durationMinutes: source.durationMinutes,
    cycleDays: source.cycleDays || 21,
    coirSchemeId: source.coirSchemeId,
    category: source.category || "",
    description: source.description || "",
    components: source.components || [],
  });
  toast(source.components?.length
    ? "Revise las drogas importadas y guarde el protocolo clínico."
    : "Complete al menos una droga para crear el protocolo clínico.");
}

function duplicateCurrent() {
  if (!state.current || state.current.catalogOnly) return;
  createNewProtocol({
    ...state.current,
    name: `Copia de ${state.current.name}`,
    coirLinks: [],
    coirSchemeId: null,
  });
}

function wirePage() {
  $("#protocolList").addEventListener("click", (event) => {
    const item = event.target.closest("[data-protocol-id]");
    if (item) openProtocol(item.dataset.protocolId);
  });
  $("#protocolSearch").addEventListener("input", renderProtocolList);
  $("#clearSearchBtn").addEventListener("click", () => { $("#protocolSearch").value = ""; renderProtocolList(); $("#protocolSearch").focus(); });
  $("#showArchived").addEventListener("change", renderProtocolList);
  $("#showCoir").addEventListener("change", renderProtocolList);
  $("#newProtocolBtn").addEventListener("click", () => createNewProtocol());
  $("#emptyNewBtn").addEventListener("click", () => createNewProtocol());
  $("#addComponentBtn").addEventListener("click", () => addComponent());
  $("#protocolForm").addEventListener("submit", saveProtocol);
  $("#archiveBtn").addEventListener("click", archiveOrRestore);
  $("#duplicateBtn").addEventListener("click", duplicateCurrent);
  $("#promoteBtn").addEventListener("click", promoteCurrentCatalogEntry);
  $$("#protocolForm input, #protocolForm select, #protocolForm textarea").forEach((control) => control.addEventListener("input", renderPreview));
}

document.addEventListener("DOMContentLoaded", async () => {
  icons();
  wirePage();
  await loadCatalog({ keepSelection: false });
});
