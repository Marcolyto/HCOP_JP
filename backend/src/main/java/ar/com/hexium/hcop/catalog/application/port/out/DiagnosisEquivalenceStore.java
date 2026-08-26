package ar.com.hexium.hcop.catalog.application.port.out;

import ar.com.hexium.hcop.catalog.domain.DiagnosisEquivalence;
import java.util.List;

public interface DiagnosisEquivalenceStore {

  List<DiagnosisEquivalence> equivalences();
}
