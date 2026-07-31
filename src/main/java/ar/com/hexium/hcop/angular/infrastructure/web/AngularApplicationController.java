package ar.com.hexium.hcop.angular.infrastructure.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Entry point for the Angular application packaged inside the same Spring Boot
 * artifact as the legacy interface and the REST API.
 */
@Controller
public class AngularApplicationController {

  @GetMapping({"/app", "/app/"})
  String application() {
    return "forward:/app/index.html";
  }
}
