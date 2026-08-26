package ar.com.hexium.hcop.media.application.port.out;

import java.util.List;

public interface StudyTemplateManifestStore {

  /** Cada elemento es el árbol opaco de una plantilla anatómica ya empaquetada. */
  List<Object> templates();
}
