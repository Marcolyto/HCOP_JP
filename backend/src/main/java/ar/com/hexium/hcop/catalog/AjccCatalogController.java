package ar.com.hexium.hcop.catalog;

import ar.com.hexium.hcop.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ajcc8")
public class AjccCatalogController {
    private final AjccCatalogService catalog;
    private final AuthContext auth;

    public AjccCatalogController(AjccCatalogService catalog, AuthContext auth) {
        this.catalog = catalog;
        this.auth = auth;
    }

    @GetMapping
    public Map<String, Object> list(HttpServletRequest request) {
        auth.requirePermission(request, "section.tools.view");
        var sites = catalog.list();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("offline", true);
        response.put("edition", "AJCC 8");
        response.put("source", "Catálogo local validado");
        response.put("count", sites.size());
        response.put("sites", sites);
        return response;
    }

    @GetMapping("/detail")
    public Map<String, Object> detail(
        @RequestParam String id,
        HttpServletRequest request) {
        auth.requirePermission(request, "section.tools.view");
        return catalog.detail(id);
    }

    @PostMapping("/stage")
    public Map<String, Object> stage(
        @RequestBody Map<String, Object> body,
        HttpServletRequest request) {
        auth.requirePermission(request, "section.tools.use");
        @SuppressWarnings("unchecked")
        Map<String, Object> values = body.get("values") instanceof Map<?, ?> map
            ? (Map<String, Object>) map : Map.of();
        return catalog.stage(String.valueOf(body.getOrDefault("id", "")), values);
    }
}
