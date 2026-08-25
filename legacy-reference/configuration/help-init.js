(function initializeConfigurationHelp(global) {
  "use strict";

  const TOPICS_BY_TAB = Object.freeze({
    protocols: "config-protocols",
    guides: "config-guides",
    "study-templates": "config-study-templates",
    calculators: "config-calculators",
    research: "config-research",
    "day-hospital": "config-day-hospital"
  });

  document.addEventListener("DOMContentLoaded", () => {
    const help = global.HcopHelp;
    const navigation = global.HcopConfigurationHelpNavigation;
    if (!help) return;

    help.init({
      page: "configuration",
      menuTrigger: "[data-help-menu]",
      getContext() {
        const activeTab = navigation?.currentTab?.()
          || document.querySelector("[data-config-tab].active")?.dataset.configTab
          || "protocols";
        return TOPICS_BY_TAB[activeTab] || "config-protocols";
      },
      startFromLocation: true,
      adapters: {
        configTab(value, metadata) {
          if (metadata?.navigationOnly !== true) return;
          navigation?.activateTab?.(value);
        }
      }
    });
  });
})(typeof window !== "undefined" ? window : globalThis);
