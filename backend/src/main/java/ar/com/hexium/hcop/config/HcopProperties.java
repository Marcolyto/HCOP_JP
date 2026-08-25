package ar.com.hexium.hcop.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hcop")
public record HcopProperties(
    Path runtimeRoot,
    Path catalogRoot,
    Path storageRoot,
    String publicBaseUrl,
    String sessionCookieName,
    int sessionDurationMinutes,
    long maxStudyBytes,
    long maxImageBytes,
    String qrSecret,
    String encryptionSecret) {
}
