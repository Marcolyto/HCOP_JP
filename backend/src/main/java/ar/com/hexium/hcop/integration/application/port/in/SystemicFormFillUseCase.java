package ar.com.hexium.hcop.integration.application.port.in;

public interface SystemicFormFillUseCase {

  FormFillResult fill(String templateId, String clinicalText, String notes);

  /** {@code fields} es el árbol opaco de respuesta del LLM (claves = ids del manifiesto). */
  record FormFillResult(String templateId, Object fields, String model) {
  }
}
