package ar.com.hexium.hcop.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HcopBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(HcopBffApplication.class, args);
    }
}
