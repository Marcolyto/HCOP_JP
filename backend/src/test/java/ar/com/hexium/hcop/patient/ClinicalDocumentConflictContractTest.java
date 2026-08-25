package ar.com.hexium.hcop.patient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiExceptionHandler;
import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ClinicalDocumentConflictContractTest {
  private final PatientRepository patients = mock(PatientRepository.class);
  private final PatientDocumentRepository repository = mock(PatientDocumentRepository.class);
  private final AuthContext auth = mock(AuthContext.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-02T16:00:00Z"), ZoneOffset.UTC);
    PatientDocumentService service = new PatientDocumentService(
        patients,
        repository,
        mapper,
        mock(HcopProperties.class),
        clock);
    ClinicalDocumentController controller = new ClinicalDocumentController(
        service,
        auth,
        new ClinicalDocumentAccessPolicy(),
        new ClinicalDocumentChangeValidator(),
        new ClinicalSummaryPlanAuthority(mapper, clock),
        new ClinicalChiefComplaintAuthority(mapper, clock),
        new ClinicalCurrentIllnessAuthority(mapper, clock),
        new ClinicalPersonalHistoryAuthority(mapper, clock),
        new ClinicalPhysicalExamAuthority(mapper, clock));
    mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
  }

  @Test
  void exigePacienteActivoConCodigoEstable() throws Exception {
    when(auth.require(any(HttpServletRequest.class))).thenReturn(principal(null));

    mvc.perform(put("/api/hc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("ACTIVE_PATIENT_REQUIRED"))
        .andExpect(jsonPath("$.error").value("Abra un paciente antes de guardar."));

    verifyNoInteractions(repository);
  }

  @Test
  void exigeRevisionClinicaSinIntentarActualizar() throws Exception {
    when(auth.require(any(HttpServletRequest.class))).thenReturn(principal(42L));
    JsonNode legacy = mapper.createObjectNode()
        .set("narrative", mapper.createObjectNode().put("summary", "Resumen legado"));
    when(repository.find(42L)).thenReturn(Optional.of(stored(42L, legacy, 3L)));

    mvc.perform(put("/api/hc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CLINICAL_REVISION_REQUIRED"))
        .andExpect(jsonPath("$.status").value(409));

    verify(repository, never()).update(anyLong(), any(JsonNode.class), anyLong(), anyLong());
  }

  @Test
  void rechazaUnResumenModificadoQueNoSeaTextoAntesDeActualizar() throws Exception {
    when(auth.require(any(HttpServletRequest.class))).thenReturn(principal(42L));
    JsonNode storedDocument = mapper.readTree("""
        {"narrative":{"summary":"Resumen anterior","plan":"Plan vigente"}}
        """);
    when(repository.find(42L)).thenReturn(Optional.of(stored(42L, storedDocument, 3L)));

    mvc.perform(put("/api/hc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "meta": {"persistenceRevision": 3},
                  "narrative": {"summary": {"texto": "inválido"}, "plan": "Plan vigente"}
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("CLINICAL_SUMMARY_INVALID"));

    verify(repository, never()).update(anyLong(), any(JsonNode.class), anyLong(), anyLong());
  }

  @Test
  void conservaUnResumenLegacyInvalidoSinCambiosAlGuardarUnPlanValido() throws Exception {
    when(auth.require(any(HttpServletRequest.class))).thenReturn(principal(42L));
    JsonNode storedDocument = mapper.readTree("""
        {"narrative":{"summary":{"formato":"legado"},"plan":"Plan anterior"}}
        """);
    when(repository.find(42L)).thenReturn(Optional.of(stored(42L, storedDocument, 3L)));
    when(repository.update(eq(42L), any(JsonNode.class), eq(3L), eq(7L)))
        .thenReturn(Optional.of(stored(42L, mapper.createObjectNode(), 4L)));

    mvc.perform(put("/api/hc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "meta": {
                    "persistenceRevision": 3,
                    "sectionChangeRequests": {"summaryPlan": {"reason": "Cambio de conducta"}}
                  },
                  "narrative": {"summary": {"formato": "legado"}, "plan": "Plan actualizado"}
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.unified.persisted").value(true))
        .andExpect(jsonPath("$.unified.revision").value(4));

    verify(repository).update(
        eq(42L),
        argThat(document -> "Plan actualizado".equals(
            document.path("narrative").path("plan").asText())),
        eq(3L),
        eq(7L));
  }

  @Test
  void respondeElEstadoCanonicoFirmadoYConLaNuevaRevision() throws Exception {
    when(auth.require(any(HttpServletRequest.class))).thenReturn(principal(42L));
    JsonNode storedDocument = mapper.readTree("""
        {
          "narrative": {"summary": "", "plan": ""},
          "meta": {"sectionVersions": {}, "sectionAudit": {}, "sectionFormModes": {}}
        }
        """);
    when(repository.find(42L)).thenReturn(Optional.of(stored(42L, storedDocument, 3L)));
    when(repository.update(eq(42L), any(JsonNode.class), eq(3L), eq(7L)))
        .thenAnswer(invocation -> Optional.of(stored(
            42L,
            ((JsonNode) invocation.getArgument(1)).deepCopy(),
            4L)));

    mvc.perform(put("/api/hc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "meta": {
                    "persistenceRevision": 3,
                    "sectionVersions": {"summaryPlan": [{"id": "forged"}]},
                    "sectionAudit": {"summaryPlan": {"lastName": "forged"}},
                    "sectionChangeRequests": {"summaryPlan": {"reason": "ignorado en carga inicial"}}
                  },
                  "narrative": {"summary": "Respuesta clínica", "plan": "Continuar"}
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.unified.revision").value(4))
        .andExpect(jsonPath("$.state.meta.persistenceRevision").value(4))
        .andExpect(jsonPath("$.state.meta.sectionVersions.summaryPlan.length()").value(1))
        .andExpect(jsonPath("$.state.meta.sectionVersions.summaryPlan[0].id").value(
            org.hamcrest.Matchers.startsWith("sec-summaryPlan-")))
        .andExpect(jsonPath("$.state.meta.sectionVersions.summaryPlan[0].author").value(
            org.hamcrest.Matchers.startsWith("Oncolog")))
        .andExpect(jsonPath("$.state.meta.sectionVersions.summaryPlan[0].license").value("s/d"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.summaryPlan[0].createdAt")
            .value("2026-08-02T16:00:00Z"))
        .andExpect(jsonPath("$.state.meta.sectionChangeRequests").doesNotExist());
  }

  @Test
  void canonizaMotivoDeConsultaYDescartaMetadataFalsificadaAntesDePersistir() throws Exception {
    when(auth.require(any(HttpServletRequest.class))).thenReturn(principal(42L));
    JsonNode storedDocument = mapper.readTree("""
        {
          "narrative": {"chiefComplaint": "Control programado"},
          "meta": {"sectionVersions": {"chiefComplaint": [{
            "id": "trusted-initial",
            "content": "Control programado",
            "reason": "Carga inicial",
            "audit": {"action": "cargado", "lastName": "Profesional previo", "license": "MP-1", "at": "2026-07-01T10:00:00Z"}
          }]}}
        }
        """);
    when(repository.find(42L)).thenReturn(Optional.of(stored(42L, storedDocument, 3L)));
    when(repository.update(eq(42L), any(JsonNode.class), eq(3L), eq(7L)))
        .thenAnswer(invocation -> Optional.of(stored(
            42L,
            ((JsonNode) invocation.getArgument(1)).deepCopy(),
            4L)));

    mvc.perform(put("/api/hc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "meta": {
                    "persistenceRevision": 3,
                    "sectionVersions": {"chiefComplaint": [{"id": "forged"}]},
                    "sectionAudit": {"chiefComplaint": {"lastName": "forged"}},
                    "sectionChangeRequests": {"chiefComplaint": {"reason": "Cambio del cuadro"}}
                  },
                  "narrative": {"chiefComplaint": "Dolor abdominal"}
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.state.meta.persistenceRevision").value(4))
        .andExpect(jsonPath("$.state.meta.sectionVersions.chiefComplaint.length()").value(2))
        .andExpect(jsonPath("$.state.meta.sectionVersions.chiefComplaint[0].id")
            .value("trusted-initial"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.chiefComplaint[1].id")
            .value(org.hamcrest.Matchers.startsWith("sec-chiefComplaint-")))
        .andExpect(jsonPath("$.state.meta.sectionVersions.chiefComplaint[1].reason")
            .value("Cambio del cuadro"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.chiefComplaint[1].content")
            .value("Dolor abdominal"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.chiefComplaint[1].createdAt")
            .value("2026-08-02T16:00:00Z"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.chiefComplaint[1].author")
            .value(org.hamcrest.Matchers.startsWith("Oncolog")))
        .andExpect(jsonPath("$.state.meta.sectionVersions.chiefComplaint[1].license")
            .value("s/d"))
        .andExpect(jsonPath("$.state.meta.sectionChangeRequests").doesNotExist());

    verify(repository).update(
        eq(42L),
        argThat(document -> {
          JsonNode versions = document.path("meta").path("sectionVersions")
              .path("chiefComplaint");
          return versions.size() == 2
              && "trusted-initial".equals(versions.get(0).path("id").asText())
              && !"forged".equals(versions.get(1).path("id").asText());
        }),
        eq(3L),
        eq(7L));
  }

  @Test
  void canonizaEnfermedadActualYDescartaMetadataFalsificadaAntesDePersistir()
      throws Exception {
    when(auth.require(any(HttpServletRequest.class))).thenReturn(principal(42L));
    JsonNode storedDocument = mapper.readTree("""
        {
          "narrative": {"currentIllness": "Tos intermitente"},
          "meta": {"sectionVersions": {"currentIllness": [{
            "id": "trusted-initial",
            "content": "Tos intermitente",
            "reason": "Carga inicial",
            "audit": {"action": "cargado", "lastName": "Profesional previo", "license": "MP-1", "at": "2026-07-01T10:00:00Z"}
          }]}}
        }
        """);
    when(repository.find(42L)).thenReturn(Optional.of(stored(42L, storedDocument, 3L)));
    when(repository.update(eq(42L), any(JsonNode.class), eq(3L), eq(7L)))
        .thenAnswer(invocation -> Optional.of(stored(
            42L,
            ((JsonNode) invocation.getArgument(1)).deepCopy(),
            4L)));

    mvc.perform(put("/api/hc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "meta": {
                    "persistenceRevision": 3,
                    "sectionVersions": {"currentIllness": [{"id": "forged"}]},
                    "sectionAudit": {"currentIllness": {"lastName": "forged"}},
                    "sectionChangeRequests": {"currentIllness": {"reason": "Progresi\u00f3n documentada"}}
                  },
                  "narrative": {"currentIllness": "Tos persistente"}
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.state.meta.persistenceRevision").value(4))
        .andExpect(jsonPath("$.state.meta.sectionVersions.currentIllness.length()").value(2))
        .andExpect(jsonPath("$.state.meta.sectionVersions.currentIllness[0].id")
            .value("trusted-initial"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.currentIllness[1].id")
            .value(org.hamcrest.Matchers.startsWith("sec-currentIllness-")))
        .andExpect(jsonPath("$.state.meta.sectionVersions.currentIllness[1].reason")
            .value("Progresi\u00f3n documentada"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.currentIllness[1].content")
            .value("Tos persistente"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.currentIllness[1].createdAt")
            .value("2026-08-02T16:00:00Z"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.currentIllness[1].author")
            .value(org.hamcrest.Matchers.startsWith("Oncolog")))
        .andExpect(jsonPath("$.state.meta.sectionVersions.currentIllness[1].license")
            .value("s/d"))
        .andExpect(jsonPath("$.state.meta.sectionChangeRequests").doesNotExist());

    verify(repository).update(
        eq(42L),
        argThat(document -> {
          JsonNode versions = document.path("meta").path("sectionVersions")
              .path("currentIllness");
          return versions.size() == 2
              && "trusted-initial".equals(versions.get(0).path("id").asText())
              && !"forged".equals(versions.get(1).path("id").asText());
        }),
        eq(3L),
        eq(7L));
  }

  @Test
  void canonizaAntecedentesPersonalesComoUnaSeccionYDescartaMetadataFalsificada()
      throws Exception {
    when(auth.require(any(HttpServletRequest.class))).thenReturn(principal(42L));
    JsonNode storedDocument = mapper.readTree("""
        {
          "narrative": {
            "backgroundClinical": "HTA",
            "currentMedication": "Losartán",
            "familyOncology": "Niega",
            "gynecology": ""
          },
          "meta": {"sectionVersions": {"personalHistory": [{
            "id": "trusted-initial",
            "content": "Clínicos / quirúrgicos: HTA",
            "reason": "Carga inicial",
            "audit": {"action": "cargado", "lastName": "Profesional previo", "license": "MP-1", "at": "2026-07-01T10:00:00Z"}
          }]}}
        }
        """);
    when(repository.find(42L)).thenReturn(Optional.of(stored(42L, storedDocument, 3L)));
    when(repository.update(eq(42L), any(JsonNode.class), eq(3L), eq(7L)))
        .thenAnswer(invocation -> Optional.of(stored(
            42L,
            ((JsonNode) invocation.getArgument(1)).deepCopy(),
            4L)));

    mvc.perform(put("/api/hc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "meta": {
                    "persistenceRevision": 3,
                    "sectionVersions": {"personalHistory": [{"id": "forged"}]},
                    "sectionAudit": {"personalHistory": {"lastName": "forged"}},
                    "sectionChangeRequests": {
                      "personalHistory": {"reason": "Actualización integral"}
                    }
                  },
                  "narrative": {
                    "backgroundClinical": "HTA y colecistectomía",
                    "currentMedication": "Losartán 50 mg",
                    "familyOncology": "Madre con cáncer de mama",
                    "gynecology": "G2 P2"
                  }
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.state.meta.persistenceRevision").value(4))
        .andExpect(jsonPath("$.state.meta.sectionVersions.personalHistory.length()").value(2))
        .andExpect(jsonPath("$.state.meta.sectionVersions.personalHistory[0].id")
            .value("trusted-initial"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.personalHistory[1].id")
            .value(org.hamcrest.Matchers.startsWith("sec-personalHistory-")))
        .andExpect(jsonPath("$.state.meta.sectionVersions.personalHistory[1].reason")
            .value("Actualización integral"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.personalHistory[1].content")
            .value("""
                Clínicos / quirúrgicos: HTA y colecistectomía
                Medicación habitual: Losartán 50 mg
                Oncofamiliares: Madre con cáncer de mama
                Gineco-obstétricos: G2 P2"""))
        .andExpect(jsonPath("$.state.meta.sectionVersions.personalHistory[1].createdAt")
            .value("2026-08-02T16:00:00Z"))
        .andExpect(jsonPath("$.state.meta.sectionChangeRequests").doesNotExist());

    verify(repository).update(
        eq(42L),
        argThat(document -> {
          JsonNode versions = document.path("meta").path("sectionVersions")
              .path("personalHistory");
          return versions.size() == 2
              && "trusted-initial".equals(versions.get(0).path("id").asText())
              && !"forged".equals(versions.get(1).path("id").asText());
        }),
        eq(3L),
        eq(7L));
  }

  @Test
  void canonizaExamenFisicoConTallaEnMetrosYSnapshotEnCentimetros() throws Exception {
    when(auth.require(any(HttpServletRequest.class))).thenReturn(principal(42L));
    JsonNode storedDocument = mapper.readTree("""
        {
          "exam": {"weightKg": "70", "heightM": "1.7"},
          "narrative": {"physicalExam": "Afebril"},
          "meta": {"sectionVersions": {"physicalExam": [{
            "id": "trusted-initial",
            "content": "Peso: 70 kg\\nTalla: 170 cm\\nEstado general: Afebril",
            "reason": "Carga inicial",
            "audit": {"action": "cargado", "lastName": "Profesional previo", "license": "MP-1", "at": "2026-07-01T10:00:00Z"}
          }]}}
        }
        """);
    when(repository.find(42L)).thenReturn(Optional.of(stored(42L, storedDocument, 3L)));
    when(repository.update(eq(42L), any(JsonNode.class), eq(3L), eq(7L)))
        .thenAnswer(invocation -> Optional.of(stored(
            42L,
            ((JsonNode) invocation.getArgument(1)).deepCopy(),
            4L)));

    mvc.perform(put("/api/hc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "meta": {
                    "persistenceRevision": 3,
                    "sectionVersions": {"physicalExam": [{"id": "forged"}]},
                    "sectionAudit": {"physicalExam": {"lastName": "forged"}},
                    "sectionChangeRequests": {
                      "physicalExam": {"reason": "Control previo al tratamiento"}
                    }
                  },
                  "exam": {"weightKg": "75", "heightM": "1.75"},
                  "narrative": {
                    "physicalExam": "Buen estado. Tórax: MV conservado. Abdomen: blando."
                  }
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.state.meta.persistenceRevision").value(4))
        .andExpect(jsonPath("$.state.exam.weightKg").value("75"))
        .andExpect(jsonPath("$.state.exam.heightM").value("1.75"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.physicalExam.length()").value(2))
        .andExpect(jsonPath("$.state.meta.sectionVersions.physicalExam[0].id")
            .value("trusted-initial"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.physicalExam[1].id")
            .value(org.hamcrest.Matchers.startsWith("sec-physicalExam-")))
        .andExpect(jsonPath("$.state.meta.sectionVersions.physicalExam[1].reason")
            .value("Control previo al tratamiento"))
        .andExpect(jsonPath("$.state.meta.sectionVersions.physicalExam[1].content")
            .value("""
                Peso: 75 kg
                Talla: 175 cm
                Estado general: Buen estado.
                Tórax: MV conservado.
                Abdomen: blando."""))
        .andExpect(jsonPath("$.state.meta.sectionVersions.physicalExam[1].createdAt")
            .value("2026-08-02T16:00:00Z"))
        .andExpect(jsonPath("$.state.meta.sectionChangeRequests").doesNotExist());

    verify(repository).update(
        eq(42L),
        argThat(document -> {
          JsonNode versions = document.path("meta").path("sectionVersions")
              .path("physicalExam");
          return versions.size() == 2
              && "trusted-initial".equals(versions.get(0).path("id").asText())
              && !"forged".equals(versions.get(1).path("id").asText());
        }),
        eq(3L),
        eq(7L));
  }

  @Test
  void rechazaEditarExamenFisicoSiElContenedorLegacyNoEsUnObjeto() throws Exception {
    when(auth.require(any(HttpServletRequest.class))).thenReturn(principal(42L));
    JsonNode storedDocument = mapper.readTree("""
        {
          "exam": ["legacy"],
          "narrative": {"physicalExam": "Afebril"}
        }
        """);
    when(repository.find(42L)).thenReturn(Optional.of(stored(42L, storedDocument, 3L)));

    mvc.perform(put("/api/hc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "meta": {
                    "persistenceRevision": 3,
                    "sectionChangeRequests": {
                      "physicalExam": {"reason": "Control clínico"}
                    }
                  },
                  "exam": ["legacy"],
                  "narrative": {"physicalExam": "Normohidratado"}
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CLINICAL_PHYSICAL_EXAM_WEIGHT_INVALID"));

    verify(repository, never()).update(anyLong(), any(JsonNode.class), anyLong(), anyLong());
  }

  @Test
  void clienteAnteriorPuedeOmitirNarrativasVaciasAlEditarOtraSeccion() throws Exception {
    when(auth.require(any(HttpServletRequest.class))).thenReturn(principal(42L));
    JsonNode storedDocument = mapper.readTree("""
        {
          "narrative": {"chiefComplaint": "", "currentIllness": "   "},
          "oncology": {"status": "En estudio"}
        }
        """);
    when(repository.find(42L)).thenReturn(Optional.of(stored(42L, storedDocument, 3L)));
    when(repository.update(eq(42L), any(JsonNode.class), eq(3L), eq(7L)))
        .thenAnswer(invocation -> Optional.of(stored(
            42L,
            ((JsonNode) invocation.getArgument(1)).deepCopy(),
            4L)));

    mvc.perform(put("/api/hc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "meta": {"persistenceRevision": 3},
                  "oncology": {"status": "En seguimiento"}
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.state.meta.persistenceRevision").value(4))
        .andExpect(jsonPath("$.state.oncology.status").value("En seguimiento"));

    verify(repository).update(
        eq(42L),
        argThat(document -> "En seguimiento".equals(
            document.path("oncology").path("status").asText())),
        eq(3L),
        eq(7L));
  }

  @Test
  void rechazaUnDocumentoDeOtroPacienteSinIntentarActualizar() throws Exception {
    when(auth.require(any(HttpServletRequest.class))).thenReturn(principal(42L));
    when(repository.find(42L)).thenReturn(Optional.of(stored(42L, mapper.createObjectNode(), 3L)));

    mvc.perform(put("/api/hc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "meta": {"persistenceRevision": 3, "liraImport": {"patientId": "43"}},
                  "patient": {"liraId": "43"}
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CLINICAL_PATIENT_MISMATCH"))
        .andExpect(jsonPath("$.status").value(409));

    verify(repository, never()).update(anyLong(), any(JsonNode.class), anyLong(), anyLong());
  }

  @Test
  void informaVersionConflictSinSobrescribirLaVersionGanadora() throws Exception {
    when(auth.require(any(HttpServletRequest.class))).thenReturn(principal(42L));
    when(repository.find(42L)).thenReturn(Optional.of(stored(42L, mapper.createObjectNode(), 4L)));
    when(repository.update(eq(42L), any(JsonNode.class), eq(3L), eq(7L)))
        .thenReturn(Optional.empty());

    mvc.perform(put("/api/hc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "meta": {"persistenceRevision": 3, "liraImport": {"patientId": "42"}},
                  "patient": {"liraId": "42"},
                  "narrative": {"summary": "Borrador concurrente"}
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
        .andExpect(jsonPath("$.error").value("La historia fue modificada en otra ventana."));

    verify(repository).update(eq(42L), any(JsonNode.class), eq(3L), eq(7L));
  }

  private SessionPrincipal principal(Long activePatientId) {
    return new SessionPrincipal(
        7L,
        "oncologia",
        "",
        "Oncología",
        "",
        "",
        true,
        activePatientId,
        List.of(),
        Set.of(
            "section.history.view",
            "section.history.edit",
            "section.prescriptions.view",
            "section.prescriptions.edit"));
  }

  private StoredDocument stored(long patientId, JsonNode document, long revision) {
    Instant now = Instant.parse("2026-08-02T16:00:00Z");
    return new StoredDocument(patientId, document, revision, null, now, now);
  }
}
