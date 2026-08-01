package ar.com.hexium.hcop.angular.infrastructure.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Entry point for the Angular application packaged inside the same Spring Boot
 * artifact as the REST API. The legacy static interface remains available only
 * as a temporary compatibility resource; it is no longer the default UI.
 */
@Controller
public class AngularApplicationController {

  @GetMapping("/")
  String root() {
    return "redirect:/app/";
  }

  @GetMapping({"/app", "/app/"})
  String application() {
    return "forward:/app/index.html";
  }

  /**
   * Lets an Angular route be refreshed or opened directly. Static Angular
   * bundles have a dot in their first segment and continue to be served by the
   * resource handler instead of this fallback.
   */
  @RequestMapping("/app/{route:[^.]+}/**")
  String angularRoute() {
    return "forward:/app/index.html";
  }
}
