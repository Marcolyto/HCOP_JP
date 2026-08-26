package ar.com.hexium.hcop.integration.application.port.in;

import java.util.List;

public interface ClinicalTimelineExtractionUseCase {

  TimelineExtraction extractTimeline(String clinicalText);

  record TimelineExtraction(List<Object> events, List<Object> warnings, String model) {
  }
}
