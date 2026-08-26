"use strict";

function normalizeDocText(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
}

function initDocSearch() {
  document.querySelectorAll("[data-doc-search]").forEach((input) => {
    const scope = document.querySelector(input.dataset.docSearch) || document;
    const counter = document.querySelector(input.dataset.docCounter || "");
    const items = [...scope.querySelectorAll("[data-filter-item]")];
    const apply = () => {
      const query = normalizeDocText(input.value.trim());
      let visible = 0;
      items.forEach((item) => {
        const show = !query || normalizeDocText(item.textContent).includes(query);
        item.hidden = !show;
        if (show) visible += 1;
      });
      if (counter) counter.textContent = `${visible} de ${items.length}`;
    };
    input.addEventListener("input", apply);
    apply();
  });
}

function initDocNavigation() {
  const links = [...document.querySelectorAll(".doc-sidebar a[href^='#']")];
  const sections = links.map((link) => document.querySelector(link.getAttribute("href"))).filter(Boolean);
  if (!sections.length || !("IntersectionObserver" in window)) return;
  const observer = new IntersectionObserver((entries) => {
    const active = entries.filter((entry) => entry.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0];
    if (!active) return;
    links.forEach((link) => link.classList.toggle("active", link.getAttribute("href") === `#${active.target.id}`));
  }, { rootMargin: "-20% 0px -68% 0px", threshold: 0 });
  sections.forEach((section) => observer.observe(section));
}

function initSystemicFieldCatalog() {
  const body = document.querySelector("#systemicFieldRows");
  if (!body) return;
  const status = document.querySelector("#systemicFieldStatus");
  fetch("/api/systemic-forms", { cache: "no-store" })
    .then((response) => {
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return response.json();
    })
    .then((payload) => {
      const forms = Array.isArray(payload.forms) ? payload.forms : [];
      const rows = forms.flatMap((form) => (form.fields || []).map((field) => ({ form, field })));
      body.innerHTML = rows.map(({ form, field }) => `
        <tr data-filter-item>
          <td>${escapeDocHtml(form.title || form.id)}</td>
          <td class="path">${escapeDocHtml(field.id)}</td>
          <td>${escapeDocHtml(field.label)}</td>
          <td>${escapeDocHtml(field.kind || "text")}</td>
          <td>${field.source === "local" ? "HCOP local" : "Extracción asistida"}</td>
          <td class="path">${escapeDocHtml(field.localKey || "—")}</td>
          <td>${escapeDocHtml(`p. ${field.page}; máx. ${field.maxChars || "—"} caracteres`)}</td>
        </tr>`).join("");
      if (status) status.textContent = `${forms.length} formularios · ${rows.length} campos cargados desde el catálogo vigente`;
      initDocSearch();
    })
    .catch(() => {
      body.innerHTML = '<tr><td colspan="7">El catálogo en vivo se carga al abrir esta página desde HCOP. Inicie el sistema y use <code>/docs/referencia-tecnica.html</code>.</td></tr>';
      if (status) status.textContent = "Catálogo no disponible fuera del servidor local";
    });
}

function escapeDocHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

document.addEventListener("DOMContentLoaded", () => {
  initDocNavigation();
  initDocSearch();
  initSystemicFieldCatalog();
});
