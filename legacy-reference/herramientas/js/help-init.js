(function initializeToolsHelp(global) {
  "use strict";

  document.addEventListener("DOMContentLoaded", () => {
    global.HcopHelp?.init({
      page: "tools",
      menuTrigger: "[data-help-menu]",
      context: "calculator-use",
      getContext: () => "calculator-use",
      startFromLocation: true
    });
  });
})(typeof window !== "undefined" ? window : globalThis);
