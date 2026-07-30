(function exposeHcopHelp(global) {
  "use strict";

  const VERSION = "1.0.0";
  const MENU_ID = "hcopHelpMenu";
  const TOUR_ID = "hcopHelpTour";
  const SAFE_ADAPTERS = new Set([
    "rightTab",
    "toolTab",
    "careView",
    "scheduleMode",
    "hospitalTab",
    "configTab",
    "modal",
    "protocolView",
    "calculatorView"
  ]);
  const DEFAULT_STEP_DURATION = 5200;
  const EDGE_GAP = 10;
  const TARGET_GAP = 7;

  const runtime = {
    initialized: false,
    page: "",
    context: "",
    getContext: null,
    menuTrigger: null,
    menuAnchor: null,
    menuTopicId: "",
    menu: null,
    tourRoot: null,
    tour: null,
    adapters: new Map(),
    autoPlay: true,
    stepDuration: DEFAULT_STEP_DURATION,
    sequence: 0,
    timer: 0,
    clickTimer: 0,
    animationFrame: 0,
    resizeObserver: null,
    previousFocus: null,
    reducedMotion: false,
    bound: {}
  };

  function content() {
    return global.HcopHelpContent || { version: "0", manualHref: "/docs/", topics: [] };
  }

  function topicsForPage(page = runtime.page) {
    const allTopics = Array.isArray(content().topics) ? content().topics : [];
    return allTopics.filter((topic) => !page || topic.page === page);
  }

  function getTopic(topicId) {
    return (content().topics || []).find((topic) => topic.id === topicId) || null;
  }

  function resolveContextTopic(topicId) {
    if (topicId !== "scheduler-chairs") return topicId;
    const hospitalModal = resolveElement("#careTreatmentManagerModal.open");
    if (!hospitalModal) return topicId;
    const activeTab = hospitalModal.querySelector("[data-care-hospital-tab][aria-selected=\"true\"]")?.dataset?.careHospitalTab || "";
    if (activeTab === "chairs") {
      const chairMode = hospitalModal.querySelector("[data-care-chair-mode][aria-selected=\"true\"]")?.dataset?.careChairMode || "agenda";
      return chairMode === "room" ? "scheduler-room" : "scheduler-agenda";
    }
    return {
      pharmacy: "scheduler-pharmacy",
      triage: "scheduler-triage",
      preparation: "scheduler-preparation",
      "new-treatment": "treatment-new",
      treatments: "treatment-detail"
    }[activeTab] || topicId;
  }

  function inferPage() {
    const path = String(global.location?.pathname || "").toLowerCase();
    if (path.includes("/configuration")) return "configuration";
    if (path.includes("/protocol-admin")) return "protocol-admin";
    if (path.includes("/herramientas")) return "tools";
    return "main";
  }

  function isElement(value) {
    return Boolean(value && value.nodeType === 1 && typeof value.getBoundingClientRect === "function");
  }

  function resolveElement(value, root = global.document) {
    if (isElement(value)) return value;
    if (typeof value !== "string" || !value.trim() || !root?.querySelector) return null;
    try {
      return root.querySelector(value);
    } catch {
      return null;
    }
  }

  function resolveElements(value, root = global.document) {
    if (isElement(value)) return [value];
    if (Array.isArray(value)) return value.flatMap((item) => resolveElements(item, root));
    if (typeof value !== "string" || !value.trim() || !root?.querySelectorAll) return [];
    try {
      return [...root.querySelectorAll(value)];
    } catch {
      return [];
    }
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function icon(name) {
    const paths = {
      close: '<path d="M18 6 6 18"></path><path d="m6 6 12 12"></path>',
      play: '<polygon points="6 3 20 12 6 21 6 3"></polygon>',
      pause: '<rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect>',
      previous: '<path d="m15 18-6-6 6-6"></path>',
      next: '<path d="m9 18 6-6-6-6"></path>',
      arrow: '<path d="M5 12h14"></path><path d="m13 6 6 6-6 6"></path>'
    };
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || ""}</svg>`;
  }

  function dispatch(name, detail = {}) {
    if (!global.document?.dispatchEvent || typeof global.CustomEvent !== "function") return;
    global.document.dispatchEvent(new global.CustomEvent(`hcop-help:${name}`, { detail }));
  }

  function init(options = {}) {
    if (!global.document?.body) return api;

    runtime.page = options.page || runtime.page || inferPage();
    runtime.getContext = typeof options.getContext === "function" ? options.getContext : runtime.getContext;
    runtime.autoPlay = options.autoPlay !== undefined ? Boolean(options.autoPlay) : runtime.autoPlay;
    runtime.stepDuration = Math.max(1800, Number(options.stepDuration) || runtime.stepDuration);
    runtime.reducedMotion = Boolean(global.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches);

    if (options.context) setContext(options.context);
    if (options.adapters) registerAdapters(options.adapters);

    ensureMenu();
    ensureTour();

    if (!runtime.initialized) {
      runtime.bound.documentClick = handleDocumentClick;
      runtime.bound.keydown = handleKeydown;
      runtime.bound.viewport = queueTourPosition;
      runtime.bound.menuViewport = () => {
        if (runtime.menu && !runtime.menu.hidden) positionMenu(runtime.menuAnchor);
        queueTourPosition();
      };
      global.document.addEventListener("click", runtime.bound.documentClick);
      global.document.addEventListener("keydown", runtime.bound.keydown);
      global.addEventListener?.("resize", runtime.bound.menuViewport);
      global.addEventListener?.("scroll", runtime.bound.viewport, true);
      runtime.initialized = true;
    }

    if (options.menuTrigger) attachMenuTrigger(options.menuTrigger);
    if (options.startFromLocation) {
      global.requestAnimationFrame?.(() => startFromLocation({ autoPlay: options.autoPlay }));
    }

    return api;
  }

  function destroy() {
    stop({ restoreFocus: false });
    closeMenu({ restoreFocus: false });
    if (runtime.initialized) {
      global.document?.removeEventListener("click", runtime.bound.documentClick);
      global.document?.removeEventListener("keydown", runtime.bound.keydown);
      global.removeEventListener?.("resize", runtime.bound.menuViewport);
      global.removeEventListener?.("scroll", runtime.bound.viewport, true);
    }
    runtime.menu?.remove();
    runtime.tourRoot?.remove();
    runtime.menu = null;
    runtime.tourRoot = null;
    runtime.menuTrigger = null;
    runtime.menuAnchor = null;
    runtime.tour = null;
    runtime.adapters.clear();
    runtime.initialized = false;
    return api;
  }

  function attachMenuTrigger(target) {
    const triggers = resolveElements(target);
    triggers.forEach((trigger) => {
      trigger.setAttribute("aria-haspopup", "dialog");
      trigger.setAttribute("aria-controls", MENU_ID);
      trigger.dataset.helpMenu = "";
    });
    runtime.menuTrigger = triggers[0] || runtime.menuTrigger;
    return triggers;
  }

  function setContext(topicId) {
    if (topicId && getTopic(topicId)) runtime.context = topicId;
    return runtime.context;
  }

  function getContext() {
    let external = "";
    try {
      external = String(runtime.getContext?.() || "");
    } catch {
      external = "";
    }
    const externalTopic = resolveContextTopic(external);
    if (externalTopic && getTopic(externalTopic)) return externalTopic;
    const runtimeTopic = resolveContextTopic(runtime.context);
    if (runtimeTopic && getTopic(runtimeTopic)) return runtimeTopic;
    return topicsForPage()[0]?.id || "";
  }

  function registerAdapter(name, handler) {
    if (!SAFE_ADAPTERS.has(name)) {
      throw new Error(`Adaptador de ayuda no permitido: ${name}`);
    }
    if (typeof handler !== "function") {
      throw new TypeError(`El adaptador ${name} debe ser una función`);
    }
    runtime.adapters.set(name, handler);
    return api;
  }

  function registerAdapters(adapters) {
    Object.entries(adapters || {}).forEach(([name, handler]) => registerAdapter(name, handler));
    return api;
  }

  function unregisterAdapter(name) {
    runtime.adapters.delete(name);
    return api;
  }

  function ensureMenu() {
    if (runtime.menu?.isConnected) return runtime.menu;
    const menu = global.document.createElement("section");
    menu.id = MENU_ID;
    menu.className = "hcop-help-menu";
    menu.hidden = true;
    menu.setAttribute("role", "dialog");
    menu.setAttribute("aria-modal", "false");
    menu.setAttribute("aria-labelledby", `${MENU_ID}Title`);
    menu.innerHTML = `
      <header class="hcop-help-menu__header">
        <div class="hcop-help-menu__heading">
          <small>Ayuda contextual</small>
          <strong id="${MENU_ID}Title">Ayuda</strong>
        </div>
        <button class="hcop-help-menu__close" type="button" data-help-menu-action="close" aria-label="Cerrar ayuda">${icon("close")}</button>
      </header>
      <section class="hcop-help-menu__context" data-help-menu-context></section>
      <div class="hcop-help-menu__topics" data-help-menu-topics role="menu" aria-label="Recorridos disponibles"></div>
      <footer class="hcop-help-menu__footer">
        <span>Ayuda local · no registra datos</span>
        <a data-help-manual-link href="${escapeHtml(content().manualHref || "/docs/")}" target="_blank" rel="noopener">Manual completo</a>
      </footer>`;
    global.document.body.append(menu);
    runtime.menu = menu;
    return menu;
  }

  function openMenu(options = {}) {
    init();
    const normalized = isElement(options) || typeof options === "string"
      ? { anchor: options }
      : (options || {});
    const anchor = resolveElement(normalized.anchor) || runtime.menuTrigger || global.document.activeElement;
    const requestedTopic = normalized.topicId || anchor?.dataset?.helpTopic || getContext();
    const topic = getTopic(requestedTopic) || topicsForPage()[0] || null;

    runtime.menuTopicId = topic?.id || "";
    runtime.menuAnchor = isElement(anchor) ? anchor : null;
    renderMenu(topic);
    runtime.menu.hidden = false;
    runtime.menu.setAttribute("aria-modal", "false");
    runtime.menuAnchor?.setAttribute("aria-expanded", "true");
    positionMenu(runtime.menuAnchor);
    runtime.menu.querySelector(".hcop-help-menu__close")?.focus({ preventScroll: true });
    dispatch("menu-open", { topicId: runtime.menuTopicId, page: runtime.page });
    return api;
  }

  function closeMenu(options = {}) {
    if (!runtime.menu || runtime.menu.hidden) return api;
    const focusTarget = runtime.menuAnchor;
    runtime.menu.hidden = true;
    runtime.menuAnchor?.setAttribute("aria-expanded", "false");
    runtime.menuAnchor = null;
    if (options.restoreFocus !== false && isElement(focusTarget)) {
      focusTarget.focus({ preventScroll: true });
    }
    dispatch("menu-close", { page: runtime.page });
    return api;
  }

  function renderMenu(topic) {
    const contextNode = runtime.menu.querySelector("[data-help-menu-context]");
    const topicsNode = runtime.menu.querySelector("[data-help-menu-topics]");
    const manualLink = runtime.menu.querySelector("[data-help-manual-link]");
    const pageTopics = topicsForPage();

    if (topic) {
      contextNode.innerHTML = `
        <div class="hcop-help-menu__context-copy">
          <span>Está en</span>
          <strong>${escapeHtml(topic.label)}</strong>
          <p>${escapeHtml(topic.summary || "")}</p>
        </div>
        <div class="hcop-help-menu__context-actions">
          <button class="hcop-help-primary" type="button" data-help-menu-action="start-context">${icon("play")}<span>Iniciar recorrido</span></button>
          <a class="hcop-help-secondary" href="${escapeHtml(topic.docsHref || content().manualHref || "/docs/")}" target="_blank" rel="noopener">Leer esta sección</a>
          ${topic.videoHref ? `<a class="hcop-help-video" href="${escapeHtml(topic.videoHref)}" target="_blank" rel="noopener">${icon("play")}<span>Ver circuito paso a paso</span></a>` : ""}
        </div>`;
    } else {
      contextNode.innerHTML = '<div class="hcop-help-menu__context-copy"><strong>Ayuda general</strong><p>No hay un recorrido asociado a esta pantalla.</p></div>';
    }

    const grouped = pageTopics.reduce((result, item) => {
      const category = item.category || "Secciones";
      (result[category] ||= []).push(item);
      return result;
    }, {});

    topicsNode.innerHTML = Object.entries(grouped).map(([category, entries]) => `
      <section class="hcop-help-topic-group">
        <strong>${escapeHtml(category)}</strong>
        ${entries.map((item) => `
          <button class="hcop-help-topic-button" type="button" role="menuitem" data-help-menu-topic="${escapeHtml(item.id)}" aria-current="${String(item.id === topic?.id)}">
            <strong>${escapeHtml(item.label)}</strong>
            <small>${escapeHtml(item.summary || "")}</small>
            ${icon("arrow")}
          </button>`).join("")}
      </section>`).join("");

    manualLink.href = content().manualHref || "/docs/";
  }

  function positionMenu(anchor) {
    const menu = runtime.menu;
    if (!menu || menu.hidden) return;
    const viewportWidth = global.innerWidth || global.document.documentElement.clientWidth;
    const viewportHeight = global.innerHeight || global.document.documentElement.clientHeight;
    const anchorRect = isElement(anchor)
      ? anchor.getBoundingClientRect()
      : { top: EDGE_GAP, right: viewportWidth - EDGE_GAP, bottom: EDGE_GAP, left: viewportWidth - EDGE_GAP };
    const menuRect = menu.getBoundingClientRect();
    let left = anchorRect.right - menuRect.width;
    let top = anchorRect.bottom + 7;

    left = clamp(left, EDGE_GAP, Math.max(EDGE_GAP, viewportWidth - menuRect.width - EDGE_GAP));
    if (top + menuRect.height > viewportHeight - EDGE_GAP) {
      top = Math.max(EDGE_GAP, anchorRect.top - menuRect.height - 7);
    }

    menu.style.left = `${Math.round(left)}px`;
    menu.style.top = `${Math.round(top)}px`;
  }

  function ensureTour() {
    if (runtime.tourRoot?.isConnected) return runtime.tourRoot;
    const root = global.document.createElement("section");
    root.id = TOUR_ID;
    root.className = "hcop-help-tour";
    root.hidden = true;
    root.setAttribute("aria-label", "Recorrido guiado");
    root.innerHTML = `
      <div class="hcop-help-tour__spotlight" aria-hidden="true"></div>
      <div class="hcop-help-tour__cursor" aria-hidden="true">
        <svg viewBox="0 0 32 32" fill="none">
          <path d="M4 3.5v22.2l5.5-5.2 4 8.2 4.6-2.2-4-8.1 7.6-.6L4 3.5Z" fill="currentColor" stroke="#173746" stroke-width="1.7" stroke-linejoin="round"></path>
        </svg>
      </div>
      <article class="hcop-help-tour__card" role="dialog" aria-modal="true" aria-labelledby="${TOUR_ID}Title" aria-describedby="${TOUR_ID}Description" tabindex="-1">
        <header class="hcop-help-tour__header">
          <span class="hcop-help-tour__eyebrow" data-help-tour-eyebrow>Recorrido guiado</span>
          <h2 class="hcop-help-tour__title" id="${TOUR_ID}Title" data-help-tour-title>Ayuda</h2>
          <button class="hcop-help-tour__close" type="button" data-help-tour-action="close" aria-label="Cerrar recorrido">${icon("close")}</button>
        </header>
        <p class="hcop-help-tour__description" id="${TOUR_ID}Description" data-help-tour-description aria-live="polite"></p>
        <a class="hcop-help-tour__video hcop-help-video" data-help-tour-video href="#" target="_blank" rel="noopener" hidden>${icon("play")}<span>Ver circuito paso a paso</span></a>
        <div class="hcop-help-tour__missing" role="status">Este control aparece cuando se cumplen las condiciones indicadas. Puede continuar con el recorrido.</div>
        <div class="hcop-help-tour__progress">
          <div class="hcop-help-tour__progress-copy"><span data-help-tour-progress-text></span><span data-help-tour-state>Reproduciendo</span></div>
          <div class="hcop-help-tour__progress-track" role="progressbar" aria-label="Progreso del recorrido" aria-valuemin="1" aria-valuemax="1" aria-valuenow="1"><span class="hcop-help-tour__progress-bar"></span></div>
        </div>
        <footer class="hcop-help-tour__actions">
          <button class="hcop-help-tour__button" type="button" data-help-tour-action="pause" aria-label="Pausar recorrido">${icon("pause")}<span>Pausa</span></button>
          <span></span>
          <button class="hcop-help-tour__button" type="button" data-help-tour-action="previous" aria-label="Paso anterior">${icon("previous")}<span>Anterior</span></button>
          <button class="hcop-help-tour__button" type="button" data-help-tour-action="next" aria-label="Paso siguiente"><span>Siguiente</span>${icon("next")}</button>
        </footer>
      </article>`;
    global.document.body.append(root);
    runtime.tourRoot = root;
    return root;
  }

  async function start(topicId, options = {}) {
    init();
    const topic = getTopic(topicId || getContext());
    if (!topic || !Array.isArray(topic.steps) || !topic.steps.length) return false;

    if (runtime.tour) stop({ restoreFocus: false });
    closeMenu({ restoreFocus: false });
    clearTourTimers();

    runtime.previousFocus = isElement(options.returnFocus)
      ? options.returnFocus
      : (isElement(global.document.activeElement) ? global.document.activeElement : null);
    runtime.sequence += 1;
    runtime.tour = {
      topic,
      index: 0,
      paused: options.autoPlay !== undefined ? !Boolean(options.autoPlay) : !runtime.autoPlay,
      target: null,
      cursorPoint: null
    };

    runtime.tourRoot.hidden = false;
    runtime.tourRoot.classList.remove("is-with-target", "is-without-target");
    global.document.body.classList.add("hcop-help-tour-open");
    runtime.tourRoot.querySelector(".hcop-help-tour__card")?.focus({ preventScroll: true });
    dispatch("start", { topicId: topic.id, page: topic.page });
    await showStep(0);
    return true;
  }

  function startFromLocation(options = {}) {
    let topicId = "";
    try {
      topicId = new global.URLSearchParams(global.location.search).get("tour") || "";
    } catch {
      topicId = "";
    }
    return topicId ? start(topicId, options) : false;
  }

  function stop(options = {}) {
    if (!runtime.tour) return api;
    const topicId = runtime.tour.topic.id;
    const previousFocus = runtime.previousFocus;
    clearTourTimers();
    disconnectTargetObserver();
    runtime.sequence += 1;
    runtime.tour = null;
    runtime.tourRoot.hidden = true;
    runtime.tourRoot.classList.remove("is-with-target", "is-without-target");
    global.document.body.classList.remove("hcop-help-tour-open");
    if (options.restoreFocus !== false && isElement(previousFocus) && previousFocus.isConnected) {
      previousFocus.focus({ preventScroll: true });
    }
    runtime.previousFocus = null;
    dispatch("end", { topicId, page: runtime.page });
    return api;
  }

  async function showStep(index) {
    const tour = runtime.tour;
    if (!tour) return false;
    const nextIndex = clamp(Number(index) || 0, 0, tour.topic.steps.length - 1);
    const sequence = ++runtime.sequence;
    tour.index = nextIndex;
    clearTourTimers();
    disconnectTargetObserver();

    const step = tour.topic.steps[nextIndex];
    await runPrepare(tour.topic.prepare, tour.topic, step);
    await runPrepare(step.prepare, tour.topic, step);
    if (!runtime.tour || sequence !== runtime.sequence) return false;

    let target = findStepTarget(step);
    if (target) {
      await revealTarget(target);
      if (!runtime.tour || sequence !== runtime.sequence) return false;
      target = findStepTarget(step) || target;
    }

    tour.target = target;
    renderTourStep(tour.topic, step, target);
    positionTour(target);
    observeTarget(target);
    animateCursor(target, step);
    scheduleAutoAdvance(step);
    dispatch("step", {
      topicId: tour.topic.id,
      stepId: step.id,
      index: tour.index,
      total: tour.topic.steps.length,
      targetFound: Boolean(target)
    });
    return true;
  }

  async function runPrepare(definition, topic, step) {
    const instructions = Array.isArray(definition) ? definition : (definition ? [definition] : []);
    for (const instruction of instructions) {
      if (!instruction || !SAFE_ADAPTERS.has(instruction.adapter)) continue;
      const adapter = runtime.adapters.get(instruction.adapter);
      if (!adapter) {
        dispatch("adapter-missing", { adapter: instruction.adapter, topicId: topic.id, stepId: step.id });
        continue;
      }
      try {
        await Promise.resolve(adapter(instruction.value, {
          topicId: topic.id,
          stepId: step.id,
          navigationOnly: true
        }));
      } catch (error) {
        dispatch("adapter-error", {
          adapter: instruction.adapter,
          topicId: topic.id,
          stepId: step.id,
          message: String(error?.message || error)
        });
      }
    }
  }

  function findStepTarget(step) {
    const candidates = Array.isArray(step.target) ? step.target : [step.target];
    for (const candidate of candidates) {
      const element = resolveElement(candidate);
      if (element && isVisible(element)) return element;
    }
    return null;
  }

  function isVisible(element) {
    if (!isElement(element) || !element.isConnected) return false;
    const style = global.getComputedStyle?.(element);
    if (style && (style.display === "none" || style.visibility === "hidden")) return false;
    return element.getClientRects().length > 0;
  }

  async function revealTarget(target) {
    const rect = target.getBoundingClientRect();
    const viewportHeight = global.innerHeight || global.document.documentElement.clientHeight;
    const viewportWidth = global.innerWidth || global.document.documentElement.clientWidth;
    const outside = rect.bottom < 32 || rect.top > viewportHeight - 32 || rect.right < 32 || rect.left > viewportWidth - 32;
    const clipped = rect.top < 8 || rect.bottom > viewportHeight - 8 || rect.left < 8 || rect.right > viewportWidth - 8;
    if (!outside && !clipped) return;

    try {
      target.scrollIntoView({
        behavior: runtime.reducedMotion ? "auto" : "smooth",
        block: "center",
        inline: "center"
      });
    } catch {
      target.scrollIntoView();
    }
    await delay(runtime.reducedMotion ? 20 : 340);
  }

  function renderTourStep(topic, step, target) {
    const root = runtime.tourRoot;
    const total = topic.steps.length;
    const current = runtime.tour.index + 1;
    root.classList.toggle("is-with-target", Boolean(target));
    root.classList.toggle("is-without-target", !target);
    root.querySelector("[data-help-tour-eyebrow]").textContent = `${topic.label} · ${current} de ${total}`;
    root.querySelector("[data-help-tour-title]").textContent = step.title;
    root.querySelector("[data-help-tour-description]").textContent = step.body;
    root.querySelector("[data-help-tour-progress-text]").textContent = `Paso ${current} de ${total}`;
    const videoLink = root.querySelector("[data-help-tour-video]");
    if (videoLink) {
      videoLink.hidden = !topic.videoHref;
      if (topic.videoHref) videoLink.href = topic.videoHref;
      else videoLink.removeAttribute("href");
    }

    const track = root.querySelector(".hcop-help-tour__progress-track");
    const progress = root.querySelector(".hcop-help-tour__progress-bar");
    track.setAttribute("aria-valuemax", String(total));
    track.setAttribute("aria-valuenow", String(current));
    progress.style.width = `${(current / total) * 100}%`;

    const previousButton = root.querySelector('[data-help-tour-action="previous"]');
    const nextButton = root.querySelector('[data-help-tour-action="next"]');
    previousButton.disabled = current === 1;
    nextButton.querySelector("span").textContent = current === total ? "Finalizar" : "Siguiente";
    nextButton.setAttribute("aria-label", current === total ? "Finalizar recorrido" : "Paso siguiente");
    updatePauseButton();
  }

  function positionTour(target = runtime.tour?.target) {
    if (!runtime.tour || runtime.tourRoot.hidden) return;
    const root = runtime.tourRoot;
    const spotlight = root.querySelector(".hcop-help-tour__spotlight");
    const card = root.querySelector(".hcop-help-tour__card");
    const viewportWidth = global.innerWidth || global.document.documentElement.clientWidth;
    const viewportHeight = global.innerHeight || global.document.documentElement.clientHeight;
    let targetRect = null;

    if (isVisible(target)) {
      const raw = target.getBoundingClientRect();
      const left = clamp(raw.left - TARGET_GAP, EDGE_GAP, viewportWidth - EDGE_GAP);
      const top = clamp(raw.top - TARGET_GAP, EDGE_GAP, viewportHeight - EDGE_GAP);
      const right = clamp(raw.right + TARGET_GAP, EDGE_GAP, viewportWidth - EDGE_GAP);
      const bottom = clamp(raw.bottom + TARGET_GAP, EDGE_GAP, viewportHeight - EDGE_GAP);
      targetRect = {
        left,
        top,
        right,
        bottom,
        width: Math.max(2, right - left),
        height: Math.max(2, bottom - top)
      };
      spotlight.style.left = `${Math.round(targetRect.left)}px`;
      spotlight.style.top = `${Math.round(targetRect.top)}px`;
      spotlight.style.width = `${Math.round(targetRect.width)}px`;
      spotlight.style.height = `${Math.round(targetRect.height)}px`;
      spotlight.style.borderRadius = inferredRadius(target);
      root.classList.add("is-with-target");
      root.classList.remove("is-without-target");
    } else {
      root.classList.remove("is-with-target");
      root.classList.add("is-without-target");
    }

    positionTourCard(card, targetRect, viewportWidth, viewportHeight);
  }

  function positionTourCard(card, targetRect, viewportWidth, viewportHeight) {
    const rect = card.getBoundingClientRect();
    let left = Math.max(EDGE_GAP, (viewportWidth - rect.width) / 2);
    let top = Math.max(EDGE_GAP, (viewportHeight - rect.height) / 2);

    if (targetRect) {
      const below = targetRect.bottom + 12;
      const above = targetRect.top - rect.height - 12;
      const right = targetRect.right + 12;
      const leftSide = targetRect.left - rect.width - 12;

      if (below + rect.height <= viewportHeight - EDGE_GAP) {
        top = below;
        left = targetRect.left + (targetRect.width - rect.width) / 2;
      } else if (above >= EDGE_GAP) {
        top = above;
        left = targetRect.left + (targetRect.width - rect.width) / 2;
      } else if (right + rect.width <= viewportWidth - EDGE_GAP) {
        left = right;
        top = targetRect.top + (targetRect.height - rect.height) / 2;
      } else if (leftSide >= EDGE_GAP) {
        left = leftSide;
        top = targetRect.top + (targetRect.height - rect.height) / 2;
      }
    }

    left = clamp(left, EDGE_GAP, Math.max(EDGE_GAP, viewportWidth - rect.width - EDGE_GAP));
    top = clamp(top, EDGE_GAP, Math.max(EDGE_GAP, viewportHeight - rect.height - EDGE_GAP));
    card.style.left = `${Math.round(left)}px`;
    card.style.top = `${Math.round(top)}px`;
  }

  function animateCursor(target, step) {
    const cursor = runtime.tourRoot.querySelector(".hcop-help-tour__cursor");
    global.clearTimeout(runtime.clickTimer);
    cursor.classList.remove("is-clicking", "is-dragging");
    if (!isVisible(target)) {
      cursor.style.opacity = "0";
      return;
    }

    const rect = target.getBoundingClientRect();
    const end = pointInRect(rect, step.cursor?.to || "center");
    const previous = runtime.tour.cursorPoint;
    const gesture = step.cursor?.gesture || "click";
    let startPoint = previous;

    if (gesture === "drag") {
      startPoint = pointInRect(rect, step.cursor?.from || "left");
      cursor.classList.add("is-dragging");
    }

    if (!startPoint || runtime.reducedMotion) {
      cursor.style.transition = "none";
      cursor.style.transform = `translate3d(${Math.round((startPoint || end).x)}px, ${Math.round((startPoint || end).y)}px, 0)`;
      cursor.getBoundingClientRect();
      cursor.style.transition = "";
    }

    global.requestAnimationFrame?.(() => {
      cursor.style.opacity = "1";
      cursor.style.transform = `translate3d(${Math.round(end.x)}px, ${Math.round(end.y)}px, 0)`;
    });

    runtime.tour.cursorPoint = end;
    runtime.clickTimer = global.setTimeout(() => {
      if (!runtime.tour) return;
      cursor.classList.remove("is-clicking");
      cursor.getBoundingClientRect();
      cursor.classList.add("is-clicking");
    }, runtime.reducedMotion ? 10 : 650);
  }

  function pointInRect(rect, position) {
    const positions = {
      left: { x: rect.left + Math.min(18, rect.width * 0.18), y: rect.top + rect.height / 2 },
      right: { x: rect.right - Math.min(18, rect.width * 0.18), y: rect.top + rect.height / 2 },
      top: { x: rect.left + rect.width / 2, y: rect.top + Math.min(15, rect.height * 0.2) },
      bottom: { x: rect.left + rect.width / 2, y: rect.bottom - Math.min(15, rect.height * 0.2) },
      center: { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }
    };
    const point = positions[position] || positions.center;
    return {
      x: clamp(point.x, EDGE_GAP, Math.max(EDGE_GAP, (global.innerWidth || 0) - 32)),
      y: clamp(point.y, EDGE_GAP, Math.max(EDGE_GAP, (global.innerHeight || 0) - 32))
    };
  }

  function inferredRadius(target) {
    try {
      const value = global.getComputedStyle(target).borderRadius;
      if (!value || value === "0px") return "5px";
      return value;
    } catch {
      return "5px";
    }
  }

  function scheduleAutoAdvance(step) {
    clearAutoTimer();
    if (!runtime.tour || runtime.tour.paused || runtime.tour.index >= runtime.tour.topic.steps.length - 1) return;
    const duration = Math.max(1800, Number(step.duration) || runtime.stepDuration);
    runtime.timer = global.setTimeout(() => next(), duration);
  }

  function next() {
    if (!runtime.tour) return false;
    if (runtime.tour.index >= runtime.tour.topic.steps.length - 1) {
      stop();
      return true;
    }
    showStep(runtime.tour.index + 1);
    return true;
  }

  function previous() {
    if (!runtime.tour || runtime.tour.index <= 0) return false;
    showStep(runtime.tour.index - 1);
    return true;
  }

  function pause() {
    if (!runtime.tour) return false;
    runtime.tour.paused = true;
    clearAutoTimer();
    updatePauseButton();
    dispatch("pause", { topicId: runtime.tour.topic.id, index: runtime.tour.index });
    return true;
  }

  function resume() {
    if (!runtime.tour) return false;
    runtime.tour.paused = false;
    updatePauseButton();
    scheduleAutoAdvance(runtime.tour.topic.steps[runtime.tour.index]);
    dispatch("resume", { topicId: runtime.tour.topic.id, index: runtime.tour.index });
    return true;
  }

  function togglePause() {
    return runtime.tour?.paused ? resume() : pause();
  }

  function updatePauseButton() {
    if (!runtime.tourRoot || !runtime.tour) return;
    const button = runtime.tourRoot.querySelector('[data-help-tour-action="pause"]');
    const state = runtime.tourRoot.querySelector("[data-help-tour-state]");
    const paused = runtime.tour.paused;
    button.innerHTML = `${icon(paused ? "play" : "pause")}<span>${paused ? "Continuar" : "Pausa"}</span>`;
    button.setAttribute("aria-label", paused ? "Continuar recorrido" : "Pausar recorrido");
    button.setAttribute("aria-pressed", String(paused));
    state.textContent = paused ? "En pausa" : "Reproduciendo";
  }

  function handleDocumentClick(event) {
    const menuAction = event.target.closest?.("[data-help-menu-action]");
    if (menuAction && runtime.menu?.contains(menuAction)) {
      event.preventDefault();
      const action = menuAction.dataset.helpMenuAction;
      if (action === "close") closeMenu();
      if (action === "start-context" && runtime.menuTopicId) start(runtime.menuTopicId);
      return;
    }

    const topicButton = event.target.closest?.("[data-help-menu-topic]");
    if (topicButton && runtime.menu?.contains(topicButton)) {
      event.preventDefault();
      start(topicButton.dataset.helpMenuTopic);
      return;
    }

    const tourAction = event.target.closest?.("[data-help-tour-action]");
    if (tourAction && runtime.tourRoot?.contains(tourAction)) {
      event.preventDefault();
      const action = tourAction.dataset.helpTourAction;
      if (action === "close") stop();
      if (action === "pause") togglePause();
      if (action === "previous") previous();
      if (action === "next") next();
      return;
    }

    const directStart = event.target.closest?.("[data-help-start]");
    if (directStart) {
      event.preventDefault();
      start(directStart.dataset.helpStart || directStart.dataset.helpTopic || getContext(), {
        returnFocus: directStart
      });
      return;
    }

    const contextualTrigger = event.target.closest?.("[data-help-trigger]");
    if (contextualTrigger) {
      event.preventDefault();
      openMenu({ anchor: contextualTrigger, topicId: contextualTrigger.dataset.helpTopic || getContext() });
      return;
    }

    const menuTrigger = event.target.closest?.("[data-help-menu]");
    if (menuTrigger) {
      event.preventDefault();
      if (!runtime.menu?.hidden && runtime.menuAnchor === menuTrigger) closeMenu();
      else openMenu({ anchor: menuTrigger, topicId: menuTrigger.dataset.helpTopic || getContext() });
      return;
    }

    if (runtime.menu && !runtime.menu.hidden && !runtime.menu.contains(event.target)) {
      closeMenu({ restoreFocus: false });
    }
  }

  function handleKeydown(event) {
    if (runtime.tour) {
      if (event.key === "Escape") {
        event.preventDefault();
        stop();
        return;
      }
      if (event.key === "ArrowRight" && !isTextEntry(event.target)) {
        event.preventDefault();
        next();
        return;
      }
      if (event.key === "ArrowLeft" && !isTextEntry(event.target)) {
        event.preventDefault();
        previous();
        return;
      }
      if ((event.key === " " || event.key.toLowerCase() === "p") && event.target === runtime.tourRoot.querySelector(".hcop-help-tour__card")) {
        event.preventDefault();
        togglePause();
        return;
      }
      if (event.key === "Tab") trapTourFocus(event);
      return;
    }

    if (runtime.menu && !runtime.menu.hidden) {
      if (event.key === "Escape") {
        event.preventDefault();
        closeMenu();
        return;
      }
      if (event.key === "Tab") trapMenuFocus(event);
    }
  }

  function isTextEntry(target) {
    return Boolean(target?.matches?.("input, textarea, select, [contenteditable=\"true\"]"));
  }

  function trapTourFocus(event) {
    trapFocus(event, runtime.tourRoot.querySelector(".hcop-help-tour__card"));
  }

  function trapMenuFocus(event) {
    trapFocus(event, runtime.menu);
  }

  function trapFocus(event, container) {
    if (!container) return;
    const focusable = [...container.querySelectorAll('button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])')]
      .filter((element) => isVisible(element));
    if (!focusable.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && global.document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && global.document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  function observeTarget(target) {
    if (!target || typeof global.ResizeObserver !== "function") return;
    runtime.resizeObserver = new global.ResizeObserver(queueTourPosition);
    runtime.resizeObserver.observe(target);
  }

  function disconnectTargetObserver() {
    runtime.resizeObserver?.disconnect();
    runtime.resizeObserver = null;
  }

  function queueTourPosition() {
    if (!runtime.tour || runtime.animationFrame) return;
    runtime.animationFrame = global.requestAnimationFrame?.(() => {
      runtime.animationFrame = 0;
      positionTour();
    }) || 0;
  }

  function clearAutoTimer() {
    global.clearTimeout(runtime.timer);
    runtime.timer = 0;
  }

  function clearTourTimers() {
    clearAutoTimer();
    global.clearTimeout(runtime.clickTimer);
    runtime.clickTimer = 0;
    if (runtime.animationFrame) global.cancelAnimationFrame?.(runtime.animationFrame);
    runtime.animationFrame = 0;
  }

  function delay(milliseconds) {
    return new Promise((resolve) => global.setTimeout(resolve, milliseconds));
  }

  function clamp(value, minimum, maximum) {
    return Math.min(Math.max(Number(value) || 0, minimum), maximum);
  }

  function getTopics(options = {}) {
    return topicsForPage(options.page || runtime.page);
  }

  function isActive() {
    return Boolean(runtime.tour);
  }

  const api = {
    version: VERSION,
    safeAdapters: Object.freeze([...SAFE_ADAPTERS]),
    init,
    destroy,
    attachMenuTrigger,
    openMenu,
    closeMenu,
    start,
    startFromLocation,
    stop,
    next,
    previous,
    pause,
    resume,
    togglePause,
    setContext,
    getContext,
    getTopics,
    registerAdapter,
    registerAdapters,
    unregisterAdapter,
    isActive
  };

  global.HcopHelp = Object.freeze(api);
})(typeof window !== "undefined" ? window : globalThis);
