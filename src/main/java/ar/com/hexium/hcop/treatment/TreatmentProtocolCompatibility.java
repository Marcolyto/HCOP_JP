package ar.com.hexium.hcop.treatment;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Conservative clinical guardrail for detecting an obvious mismatch between a
 * saved diagnosis and the disease family named by a protocol.
 *
 * <p>An unknown or support protocol is intentionally considered neutral. The
 * result is a warning that can be overridden with a documented reason, not a
 * substitute for clinical judgement.</p>
 */
@Component
public class TreatmentProtocolCompatibility {
  private static final Map<String, String> LABELS = labels();

  public Assessment assess(String diagnosis, String schemeName) {
    String diagnosisGroup = diagnosisGroup(diagnosis);
    String protocolGroup = protocolGroup(schemeName);
    boolean comparable = !diagnosisGroup.isBlank() && !protocolGroup.isBlank();
    return new Assessment(
        diagnosisGroup,
        label(diagnosisGroup),
        protocolGroup,
        label(protocolGroup),
        comparable && !diagnosisGroup.equals(protocolGroup));
  }

  public String diagnosisGroup(String value) {
    String text = normalize(value);
    if (containsAny(text, "mama", "mamario", "breast")) return "breast";
    if (containsAny(text, "pulmon", "bronquio", "pleura", "mesotelioma", "torac")) return "thoracic";
    if (containsAny(text,
        "colon", "recto", "anal", "ano", "gastric", "estomago", "pancrea",
        "esofag", "hepatic", "higado", "biliar", "duodeno", "intestino")) {
      return "gastrointestinal";
    }
    if (containsAny(text,
        "prostata", "vejiga", "urotel", "renal", "rinon", "testicul", "pene",
        "genitourin")) {
      return "genitourinary";
    }
    if (containsAny(text,
        "ovario", "cervix", "cuello uterino", "endometr", "utero", "vulva",
        "vagina", "ginecolog")) {
      return "gynecologic";
    }
    if (containsAny(text, "linfoma", "leucemia", "mieloma", "hematolog")) return "hematologic";
    if (containsAny(text, "melanoma", "cutaneo", "piel")) return "skin";
    if (containsAny(text,
        "cabeza y cuello", "laringe", "faringe", "nasofaringe", "orofaringe",
        "cavidad oral", "glandula salival")) {
      return "head-neck";
    }
    if (containsAny(text, "glioma", "glioblastoma", "cerebro", "encefalo", "sistema nervioso central")) {
      return "central-nervous-system";
    }
    if (containsAny(text, "sarcoma", "partes blandas", "tumor oseo")) return "sarcoma";
    return "";
  }

  public String protocolGroup(String value) {
    String text = normalize(value);
    if (isSupportProtocol(text)) return "";
    if (containsAny(text, "mama", "breast")) return "breast";
    if (containsAny(text, "pulmon", "torax", "mesotelioma")) return "thoracic";
    if (containsAny(text,
        "digestivo", "colon", "recto", "gastrico", "estomago", "pancrea",
        "hepat", "esofag", "biliar")) {
      return "gastrointestinal";
    }
    if (startsWithAny(text, "uro ", "urolog", "genitourin")
        || containsAny(text, "prostata", "vejiga", "renal", "testiculo", "pene")) {
      return "genitourinary";
    }
    if (containsAny(text,
        "ovario", "cervix", "endometr", "utero", "vulva", "vagina", "gineco")) {
      return "gynecologic";
    }
    if (containsAny(text, "linfoma", "leucemia", "mieloma", "hematolog")) return "hematologic";
    if (containsAny(text, "melanoma", "piel", "cutaneo")) return "skin";
    if (containsAny(text,
        "cabeza y cuello", "laringe", "faringe", "nasofaringe", "orofaringe",
        "cavidad oral", "salival")) {
      return "head-neck";
    }
    if (containsAny(text, "glioma", "glioblastoma", "cerebro", "snc")) {
      return "central-nervous-system";
    }
    if (containsAny(text, "sarcoma", "partes blandas", "oseo")) return "sarcoma";
    return "";
  }

  private boolean isSupportProtocol(String text) {
    return containsAny(text,
        "soporte", "antiemet", "hidratacion", "zoledron", "denosumab",
        "bifosfon", "eritropoy", "filgrastim", "pegfilgrastim");
  }

  private String label(String group) {
    return LABELS.getOrDefault(group, "");
  }

  private static boolean containsAny(String text, String... tokens) {
    for (String token : tokens) {
      if (text.contains(token)) return true;
    }
    return false;
  }

  private static boolean startsWithAny(String text, String... prefixes) {
    for (String prefix : prefixes) {
      if (text.startsWith(prefix)) return true;
    }
    return false;
  }

  private static String normalize(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim();
  }

  private static Map<String, String> labels() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("breast", "Mama");
    values.put("thoracic", "Tórax / pulmón");
    values.put("gastrointestinal", "Digestivo");
    values.put("genitourinary", "Genitourinario");
    values.put("gynecologic", "Ginecológico");
    values.put("hematologic", "Hematológico");
    values.put("skin", "Piel / melanoma");
    values.put("head-neck", "Cabeza y cuello");
    values.put("central-nervous-system", "Sistema nervioso central");
    values.put("sarcoma", "Sarcomas");
    return Map.copyOf(values);
  }

  public record Assessment(
      String diagnosisGroup,
      String diagnosisGroupLabel,
      String protocolGroup,
      String protocolGroupLabel,
      boolean mismatch) {
  }
}
