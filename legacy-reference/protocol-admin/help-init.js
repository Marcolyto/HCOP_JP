(function initializeProtocolAdminHelp(global) {
  "use strict";

  document.addEventListener("DOMContentLoaded", () => {
    const help = global.HcopHelp;
    const navigation = global.HcopProtocolAdminHelpNavigation;
    if (!help) return;

    help.init({
      page: "protocol-admin",
      menuTrigger: "[data-help-menu]",
      context: "protocol-editor",
      getContext: () => "protocol-editor",
      startFromLocation: true,
      adapters: {
        protocolView(value, metadata) {
          if (metadata?.navigationOnly !== true || value !== "editor") return;
          navigation?.showEditor?.();
        }
      }
    });
  });
})(typeof window !== "undefined" ? window : globalThis);
