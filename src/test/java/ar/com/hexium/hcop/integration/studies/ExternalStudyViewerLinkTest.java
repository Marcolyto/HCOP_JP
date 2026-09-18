package ar.com.hexium.hcop.integration.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExternalStudyViewerLinkTest {
  @Test
  void reproducesThePublicJavascriptLauncherAcrossKeyBoundaries() {
    assertThat(ExternalStudyViewerLink.url("https://institution.example/viewer/index.php", "1.2.3.4.5.6.7.8.9"))
        .isEqualTo("https://institution.example/viewer/index.php/pacientes?study=6%3C5%40L%3EH%3C8%40O%3EK3F1K");
    assertThat(ExternalStudyViewerLink.url("https://institution.example/viewer/index.php", "1.2.840.113619.2.55.3.604688.123456789.1"))
        .isEqualTo("https://institution.example/viewer/index.php/pacientes?study=6%3C5%40QDD%3C4CLFE%3E%3C5%40NEB8%3C9BMFL%3D%3C4DLDI%3BE%3BKGA");
  }

  @Test
  void keepsTheConfiguredInstitutionAndPortWithoutAnExternalLauncher() {
    assertThat(ExternalStudyViewerLink.url("https://institution.example:8443/viewer/index.php/", "0"))
        .isEqualTo("https://institution.example:8443/viewer/index.php/pacientes?study=5");
  }

  @Test
  void rejectsAmbiguousBaseAddressesAndInvalidStudyIdentifiers() {
    for (String base : new String[] {"https://institution.example/viewer?redirect=other", "https://institution.example/viewer#other"}) {
      assertThatThrownBy(() -> ExternalStudyViewerLink.url(base, "1.2.3")).isInstanceOf(ExternalStudyFailure.class);
    }
    for (String uid : new String[] {null, "", " ", "1.2\n3", "1".repeat(161)}) {
      assertThatThrownBy(() -> ExternalStudyViewerLink.url("https://institution.example/viewer/index.php", uid))
          .isInstanceOf(ExternalStudyFailure.class);
    }
  }
}
