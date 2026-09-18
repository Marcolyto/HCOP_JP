package ar.com.hexium.hcop.integration.studies;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/** Tolerant HTML parsing, without executing scripts or treating markup as patient data. */
final class ExternalStudyHtml {
  record Cell(String text, List<String> links) {}
  record LiveView(String id, String session, String staticToken) {}
  final List<List<Cell>> rows = new ArrayList<>();
  final Map<String, String> inputs = new LinkedHashMap<>();
  final Map<String, String> tokens = new LinkedHashMap<>();
  final List<LiveView> liveViews = new ArrayList<>();
  String text = "";
  boolean passwordForm;

  static ExternalStudyHtml parse(String html) {
    var result = new ExternalStudyHtml();
    var allText = new StringBuilder();
    try {
      new ParserDelegator().parse(new StringReader(html == null ? "" : html), new HTMLEditorKit.ParserCallback() {
        private List<Cell> row;
        private StringBuilder cell;
        private List<String> links;
        private boolean suppressed;
        @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
          String session = attr(attributes, "data-phx-session");
          String id = attr(attributes, HTML.Attribute.ID);
          if (!session.isBlank() && !id.isBlank() && attributes.getAttribute("data-phx-main") != null) {
            result.liveViews.add(new LiveView(id, session, attr(attributes, "data-phx-static")));
          }
          if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) suppressed = true;
          if (tag == HTML.Tag.TR) { finishRow(); row = new ArrayList<>(); }
          if (tag == HTML.Tag.TD || tag == HTML.Tag.TH) { finishCell(); cell = new StringBuilder(); links = new ArrayList<>(); }
          if (tag == HTML.Tag.A && links != null) links.add(attr(attributes, HTML.Attribute.HREF));
          if (tag == HTML.Tag.INPUT || tag == HTML.Tag.META) handleSimpleTag(tag, attributes, position);
        }
        @Override public void handleEndTag(HTML.Tag tag, int position) {
          if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) suppressed = false;
          if (tag == HTML.Tag.TD || tag == HTML.Tag.TH) finishCell();
          if (tag == HTML.Tag.TR) finishRow();
          allText.append(' '); if (cell != null) cell.append(' ');
        }
        @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
          if (tag == HTML.Tag.INPUT) {
            String name = attr(attributes, HTML.Attribute.NAME);
            String value = attr(attributes, HTML.Attribute.VALUE);
            if (!name.isBlank()) result.inputs.put(name, value);
            if ("password".equalsIgnoreCase(attr(attributes, HTML.Attribute.TYPE))) result.passwordForm = true;
            if (isToken(name) && !value.isBlank()) result.tokens.put(name, value);
          }
          if (tag == HTML.Tag.META) {
            String name = attr(attributes, HTML.Attribute.NAME);
            String value = attr(attributes, HTML.Attribute.CONTENT);
            if (isToken(name) && !value.isBlank()) result.tokens.put(name, value);
          }
          if (tag == HTML.Tag.BR) { allText.append(' '); if (cell != null) cell.append(' '); }
        }
        @Override public void handleText(char[] data, int position) {
          if (suppressed) return;
          allText.append(data).append(' ');
          if (cell != null) cell.append(data).append(' ');
        }
        private void finishCell() {
          if (cell != null && row != null) row.add(new Cell(clean(cell.toString()), List.copyOf(links)));
          cell = null; links = null;
        }
        private void finishRow() {
          finishCell(); if (row != null && !row.isEmpty()) result.rows.add(List.copyOf(row)); row = null;
        }
      }, true);
    } catch (Exception invalid) { throw new ExternalStudyFailure("La fuente devolvió un documento HTML ilegible."); }
    result.text = clean(allText.toString());
    return result;
  }

  static String plain(String value) { return parse(value).text; }
  static String clean(String value) { return value.replace('\u00a0', ' ').replaceAll("[\\p{Cntrl}\\s]+", " ").trim(); }
  static boolean isToken(String name) {
    String key = name.toLowerCase(Locale.ROOT);
    return key.contains("csrf") || key.equals("_token") || key.equals("__requestverificationtoken");
  }
  boolean loginRejected() {
    String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    return passwordForm || normalized.contains("invalid credentials") || normalized.contains("credenciales incorrectas")
        || normalized.contains("contrasena incorrecta") || normalized.contains("usuario incorrecto")
        || normalized.contains("sesion expirada") || normalized.contains("acceso denegado");
  }
  private static String attr(MutableAttributeSet attributes, HTML.Attribute key) {
    Object value = attributes.getAttribute(key); return value == null ? "" : value.toString();
  }
  private static String attr(MutableAttributeSet attributes, String key) {
    Object value = attributes.getAttribute(key); return value == null ? "" : value.toString();
  }
}
