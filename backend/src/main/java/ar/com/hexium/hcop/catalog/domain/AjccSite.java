package ar.com.hexium.hcop.catalog.domain;

import java.util.List;

/** {@code axes} es el árbol opaco de criterios de estadificación (mapas/listas/escalares). */
public record AjccSite(
    String id,
    String name,
    String group,
    String edition,
    String source,
    String guideVersion,
    Object axes,
    List<String> columns,
    List<AjccStagingRule> rules) {
}
