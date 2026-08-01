package ar.com.hexium.hcop.guide.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GuideFileNameTest {
  @Test
  void removesDirectoriesAndUnsafeCharacters() {
    assertThat(GuideFileName.fromRaw("../../Pulmón: guía?.pdf").value())
        .isEqualTo("Pulmón_ guía_.pdf");
    assertThat(GuideFileName.fromRaw("C:\\temporal\\mama.pdf").value())
        .isEqualTo("mama.pdf");
  }

  @Test
  void identifiesPdfCaseInsensitivelyAndRejectsEmptyNames() {
    assertThat(GuideFileName.fromRaw("guia.PDF").pdf()).isTrue();
    assertThatThrownBy(() -> GuideFileName.fromRaw("../"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
