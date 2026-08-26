package ar.com.hexium.hcop.media.application.service;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.configuration.application.service.ConfigurationFailure;
import ar.com.hexium.hcop.configuration.domain.ConfigurationDefinition;
import ar.com.hexium.hcop.media.application.port.in.ClinicalFileUseCase;
import ar.com.hexium.hcop.media.application.port.in.ClinicalFileUseCase.StoreImageCommand;
import ar.com.hexium.hcop.media.application.port.in.StudyTemplateUseCase;
import ar.com.hexium.hcop.media.application.port.out.StudyTemplateManifestStore;
import ar.com.hexium.hcop.media.domain.ClinicalFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StudyTemplateApplicationService implements StudyTemplateUseCase {
  private final StudyTemplateManifestStore manifest;
  private final ConfigurationManagementUseCase configurations;
  private final ClinicalFileUseCase files;

  public StudyTemplateApplicationService(
      StudyTemplateManifestStore manifest, ConfigurationManagementUseCase configurations, ClinicalFileUseCase files) {
    this.manifest = manifest;
    this.configurations = configurations;
    this.files = files;
  }

  @Override
  public StudyTemplateCatalog list(String scope, boolean includeInactive) {
    List<Object> bundled = "custom".equals(scope) ? List.of() : manifest.templates();
    List<ConfigurationManagementUseCase.ConfigurationView> custom;
    try {
      custom = configurations.list("study-template", includeInactive);
    } catch (ConfigurationFailure failure) {
      throw translate(failure);
    }
    return new StudyTemplateCatalog(bundled, custom);
  }

  @Override
  public CreatedStudyTemplate create(CreateStudyTemplateCommand command) {
    if (!command.rightsConfirmed()) {
      throw new MediaFailure(MediaFailure.Type.INVALID, "Debe confirmar los derechos de uso.");
    }
    ClinicalFile image = files.storeImage(new StoreImageCommand(
        command.fileName(), command.imageBytes(), command.imageContentType(), "original",
        command.actorId(), command.sessionId()));
    Map<String, Object> definition = definition(command, image);
    try {
      var item = configurations.create(new ConfigurationManagementUseCase.CreateCommand(
          "study-template", "", command.title().trim(), command.description().trim(), true,
          ConfigurationDefinition.of(definition), command.actorId()));
      return new CreatedStudyTemplate(item, image);
    } catch (ConfigurationFailure failure) {
      files.discardImage(image);
      throw translate(failure);
    } catch (RuntimeException other) {
      files.discardImage(image);
      throw other;
    }
  }

  private Map<String, Object> definition(CreateStudyTemplateCommand command, ClinicalFile image) {
    String name = image.storageKey().substring(image.storageKey().indexOf('/') + 1);
    String url = "/api/media/images/" + name;
    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put("origin", "custom");
    definition.put("category", command.category().trim());
    definition.put("author", command.author().trim());
    definition.put("attribution", command.attribution().trim());
    definition.put("license", command.license().trim());
    definition.put("sourceUrl", command.sourceUrl().trim());
    definition.put("licenseUrl", command.licenseUrl().trim());
    definition.put("rightsConfirmed", true);
    definition.put("fileName", name);
    definition.put("fileUrl", url);
    definition.put("thumbnailUrl", url);
    definition.put("mime", image.contentType());
    definition.put("bytes", image.size());
    definition.put("sha256", image.sha256());
    List<String> tags = new ArrayList<>();
    for (String tag : command.tags()) {
      String normalized = tag == null ? "" : tag.trim();
      if (!normalized.isBlank() && tags.size() < 30) tags.add(normalized);
    }
    definition.put("tags", tags);
    return definition;
  }

  private MediaFailure translate(ConfigurationFailure failure) {
    MediaFailure.Type type = switch (failure.type()) {
      case INVALID -> MediaFailure.Type.INVALID;
      case NOT_FOUND -> MediaFailure.Type.NOT_FOUND;
      case CONFLICT -> MediaFailure.Type.CONFLICT;
    };
    return new MediaFailure(type, failure.getMessage(), failure.code());
  }
}
