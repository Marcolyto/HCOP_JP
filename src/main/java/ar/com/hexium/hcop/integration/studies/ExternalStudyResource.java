package ar.com.hexium.hcop.integration.studies;

/** A verified external report, or a provider-issued portable viewer link. */
public record ExternalStudyResource(byte[] content, String contentType, String filename, String redirectUrl) {}
