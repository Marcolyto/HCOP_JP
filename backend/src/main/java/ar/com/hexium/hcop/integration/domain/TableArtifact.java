package ar.com.hexium.hcop.integration.domain;

import java.util.List;

public record TableArtifact(String title, List<String> columns, List<List<String>> rows) implements ChatArtifact {
}
