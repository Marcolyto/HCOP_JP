package ar.com.hexium.hcop.integration.studies;

import java.net.URI;

/** Institutional viewer link format used by the public legacy viewer launcher. */
final class ExternalStudyViewerLink {
  // This is the public client-side obfuscation constant, not a login credential.
  private static final String PUBLIC_KEY = "encrypt";

  private ExternalStudyViewerLink() {}

  static String url(String viewerBaseUrl, String studyUid) {
    URI base = ExternalStudyHttp.safeUri(viewerBaseUrl);
    if (base.getQuery() != null || base.getFragment() != null || studyUid == null
        || studyUid.isBlank() || studyUid.length() > 160 || studyUid.chars().anyMatch(Character::isISOControl)) {
      throw new ExternalStudyFailure("La fuente no entregó un identificador de estudio válido.");
    }
    var encoded = new StringBuilder(studyUid.length());
    for (int index = 0; index < studyUid.length(); index++) {
      // Preserve the launcher's indexing exactly: after the first key length,
      // the next character uses (index + 1) modulo the key length.
      int keyIndex = index + 1 > PUBLIC_KEY.length() ? (index + 1) % PUBLIC_KEY.length() : index;
      int value = studyUid.charAt(index) + PUBLIC_KEY.charAt(keyIndex) + 32;
      if (value > 127) value -= 128;
      encoded.append((char) value);
    }
    String prefix = base.toString().replaceFirst("/+$", "");
    return prefix + "/pacientes?study=" + ExternalStudyHttp.encode(encoded.toString()).replace("+", "%20");
  }
}
