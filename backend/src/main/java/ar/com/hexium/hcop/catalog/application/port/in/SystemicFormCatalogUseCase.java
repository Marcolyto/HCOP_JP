package ar.com.hexium.hcop.catalog.application.port.in;

import java.util.List;

public interface SystemicFormCatalogUseCase {

  /** Cada elemento es el árbol opaco (mapas/listas/escalares) de un formulario sistémico. */
  List<Object> forms();

  /** Devuelve {@code null} si no existe un formulario con ese id — árbol opaco igual que arriba. */
  Object find(String id);
}
