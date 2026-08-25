package ar.com.hexium.hcop.catalog;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.config.HcopProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AjccCatalogService {
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("ampulla", "Ampolla de Vater"),
        Map.entry("bladder", "Vejiga"),
        Map.entry("breast", "Mama"),
        Map.entry("cholangio_intrahepatic", "Colangiocarcinoma intrahepático"),
        Map.entry("colon", "Colon y recto"),
        Map.entry("corpus", "Cuerpo uterino"),
        Map.entry("esophagus_adeno", "Esófago, adenocarcinoma"),
        Map.entry("esophagus_scc", "Esófago, carcinoma escamoso"),
        Map.entry("headskin", "Carcinoma cutáneo de cabeza y cuello"),
        Map.entry("hypopharynx", "Hipofaringe"),
        Map.entry("kidney", "Riñón"),
        Map.entry("larynx_glottis", "Laringe glótica"),
        Map.entry("larynx_subglottis", "Laringe subglótica"),
        Map.entry("larynx_supraglottis", "Laringe supraglótica"),
        Map.entry("liver", "Hígado"),
        Map.entry("nasopharynx", "Nasofaringe"),
        Map.entry("oral_cavity", "Cavidad oral"),
        Map.entry("oropharynx_hpv", "Orofaringe HPV/p16 positiva"),
        Map.entry("oropharynx_p16neg", "Orofaringe p16 negativa"),
        Map.entry("ovary", "Ovario, trompa y peritoneo"),
        Map.entry("pancreas", "Páncreas"),
        Map.entry("prostate", "Próstata"),
        Map.entry("stomach", "Estómago"),
        Map.entry("testis", "Testículo"),
        Map.entry("thyroid_anaplastic", "Tiroides anaplásico"),
        Map.entry("thyroid_differentiated", "Tiroides diferenciado"),
        Map.entry("ureter", "Uréter"),
        Map.entry("gallbladder", "Vesícula biliar"),
        Map.entry("bile_duct_perihilar", "Vía biliar perihiliar"),
        Map.entry("bile_duct_distal", "Vía biliar distal"),
        Map.entry("melanoma_cutaneous", "Melanoma cutáneo"),
        Map.entry("penile", "Pene"),
        Map.entry("lung", "Pulmón"),
        Map.entry("bone_appendicular", "Hueso: esqueleto apendicular, tronco y cráneo"),
        Map.entry("mesothelioma_pleural", "Mesotelioma pleural"),
        Map.entry("small_intestine", "Intestino delgado"),
        Map.entry("merkel", "Carcinoma de células de Merkel"),
        Map.entry("uveal_melanoma", "Melanoma uveal")
    );
    private static final Set<String> HEAD_NECK = Set.of(
        "headskin", "hypopharynx", "larynx_glottis", "larynx_subglottis",
        "larynx_supraglottis", "nasopharynx", "oral_cavity", "oropharynx_hpv",
        "oropharynx_p16neg");
    private static final Set<String> DIGESTIVE = Set.of(
        "ampulla", "cholangio_intrahepatic", "colon", "esophagus_adeno",
        "esophagus_scc", "liver", "pancreas", "stomach", "gallbladder",
        "bile_duct_perihilar", "bile_duct_distal", "small_intestine");
    private static final Set<String> GENITOURINARY = Set.of(
        "bladder", "kidney", "prostate", "testis", "ureter", "penile");

    private final Path root;
    private final ObjectMapper mapper;
    private volatile Map<String, Site> cachedSites;

    public AjccCatalogService(HcopProperties properties, ObjectMapper mapper) {
        this.root = properties.catalogRoot().resolve("tnm-ajcc8").normalize();
        this.mapper = mapper;
    }

    public List<Map<String, Object>> list() {
        return sites().values().stream()
            .sorted(Comparator.comparing(Site::group).thenComparing(Site::name))
            .map(site -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", site.id());
                item.put("name", site.name());
                item.put("group", site.group());
                return item;
            })
            .toList();
    }

    public Map<String, Object> detail(String id) {
        Site site = required(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("id", site.id());
        result.put("name", site.name());
        result.put("edition", site.criteria().path("edition").asText("AJCC 8"));
        result.put("source", site.criteria().path("source").asText("Catálogo local validado"));
        result.put("guideVersion", site.criteria().path("guideVersion").asText(""));
        result.put("axes", site.criteria().path("axes"));
        return result;
    }

    public Map<String, Object> stage(String id, Map<String, Object> inputValues) {
        Site site = required(id);
        Map<String, String> values = new LinkedHashMap<>();
        (inputValues == null ? Map.<String, Object>of() : inputValues)
            .forEach((key, value) -> values.put(key, value == null ? "" : String.valueOf(value).trim()));
        for (Rule rule : site.rules()) {
            boolean matches = site.columns().stream().allMatch(column -> {
                String expected = rule.values().getOrDefault(column, "");
                return "ANY".equals(expected) || expected.equals(values.getOrDefault(column, ""));
            });
            if (matches) {
                return Map.of("ok", true, "stage", rule.values().getOrDefault("Stage", ""), "sourceRow", rule.row());
            }
        }
        List<String> missing = site.columns().stream()
            .filter(column -> values.getOrDefault(column, "").isBlank())
            .filter(column -> site.rules().stream().anyMatch(rule -> !"ANY".equals(rule.values().get(column))))
            .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("stage", "");
        result.put("missing", missing);
        return result;
    }

    public List<Map<String, Object>> search(String query, int limit) {
        List<String> terms = DiagnosisCatalogService.normalizedTerms(query);
        return list().stream()
            .filter(item -> DiagnosisCatalogService.matchesAll(
                item.get("id") + " " + item.get("name") + " " + item.get("group")
                    + " carcinoma tumor maligno neoplasia cáncer", terms))
            .limit(limit)
            .map(item -> Map.<String, Object>of(
                "system", "AJCC",
                "code", item.get("id"),
                "display", item.get("name"),
                "group", item.get("group"),
                "version", "AJCC 8",
                "source", "Catálogo AJCC 8 local"))
            .toList();
    }

    private Site required(String id) {
        Site site = sites().get(id == null ? "" : id.trim());
        if (site == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Sitio AJCC 8 no encontrado");
        }
        return site;
    }

    private Map<String, Site> sites() {
        Map<String, Site> snapshot = cachedSites;
        if (snapshot != null) return snapshot;
        synchronized (this) {
            if (cachedSites == null) cachedSites = load();
            return cachedSites;
        }
    }

    private Map<String, Site> load() {
        if (!Files.isDirectory(root)) return Map.of();
        Map<String, Site> loaded = new LinkedHashMap<>();
        try (var paths = Files.list(root)) {
            paths.filter(Files::isDirectory).forEach(directory -> {
                Path criteriaPath = directory.resolve("criteria.json");
                Path rulesPath = directory.resolve("rules.csv");
                if (!Files.isRegularFile(criteriaPath) || !Files.isRegularFile(rulesPath)) return;
                try {
                    String id = directory.getFileName().toString();
                    JsonNode criteria = mapper.readTree(
                        Files.readString(criteriaPath, StandardCharsets.UTF_8));
                    List<List<String>> csv = parseCsv(Files.readString(rulesPath, StandardCharsets.UTF_8));
                    if (csv.isEmpty()) return;
                    List<String> headers = csv.removeFirst();
                    List<String> columns = headers.stream().filter(name -> !"Stage".equals(name)).toList();
                    List<Rule> rules = new ArrayList<>();
                    for (int rowIndex = 0; rowIndex < csv.size(); rowIndex++) {
                        List<String> row = csv.get(rowIndex);
                        Map<String, String> values = new LinkedHashMap<>();
                        for (int index = 0; index < headers.size(); index++) {
                            values.put(headers.get(index), index < row.size() ? row.get(index).trim() : "");
                        }
                        rules.add(new Rule(values, rowIndex + 2));
                    }
                    loaded.put(id, new Site(id, LABELS.getOrDefault(id, id.replace('_', ' ')),
                        group(id), criteria, columns, rules));
                } catch (IOException exception) {
                    throw new IllegalStateException("Catálogo AJCC inválido en " + directory, exception);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer el catálogo AJCC", exception);
        }
        return Map.copyOf(loaded);
    }

    static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                row.add(value.toString());
                value.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                row.add(value.toString());
                value.setLength(0);
                if (row.stream().anyMatch(cell -> !cell.isEmpty())) rows.add(new ArrayList<>(row));
                row.clear();
            } else {
                value.append(current);
            }
        }
        if (!value.isEmpty() || !row.isEmpty()) {
            row.add(value.toString());
            rows.add(row);
        }
        return rows;
    }

    private static String group(String id) {
        if (HEAD_NECK.contains(id)) return "Cabeza y cuello";
        if (DIGESTIVE.contains(id)) return "Aparato digestivo";
        if (GENITOURINARY.contains(id)) return "Genitourinario";
        if (Set.of("corpus", "ovary").contains(id)) return "Ginecológico";
        if (Set.of("thyroid_anaplastic", "thyroid_differentiated").contains(id)) return "Endocrino";
        if ("breast".equals(id)) return "Mama";
        if (Set.of("lung", "mesothelioma_pleural").contains(id)) return "Tórax";
        if (Set.of("melanoma_cutaneous", "merkel").contains(id)) return "Piel y melanoma";
        if ("bone_appendicular".equals(id)) return "Hueso y sarcoma";
        if ("uveal_melanoma".equals(id)) return "Ojo";
        return "Otros";
    }

    private record Site(String id, String name, String group, JsonNode criteria,
                        List<String> columns, List<Rule> rules) {}
    private record Rule(Map<String, String> values, int row) {}
}
