package ar.com.hexium.hcop.protocol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProtocolIdTest {
  @Test
  void distinguishesLocalAndCoirIdentifiers() {
    ProtocolId local = ProtocolId.parse("42");
    ProtocolId coir = ProtocolId.parse("coir-347");

    assertThat(local.catalog()).isFalse();
    assertThat(local.customValue()).isEqualTo(42);
    assertThat(coir.catalog()).isTrue();
    assertThat(coir.coirValue()).isEqualTo("347");
  }

  @Test
  void rejectsMalformedIdentifiers() {
    assertThatThrownBy(() -> ProtocolId.parse("protocol-42"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProtocolId.parse("coir-"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
