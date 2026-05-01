package com.ecom360.tenant.infrastructure.web;

import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.infrastructure.storage.BusinessLogoStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sert les fichiers logo sans authentification (affichage &lt;img&gt;, impression). */
@RestController
@RequestMapping(ApiConstants.API_BASE + "/public/business-logos")
@Tag(name = "Public", description = "Fichiers publics")
public class PublicBusinessLogoController {

  private final BusinessLogoStorageService businessLogoStorageService;

  public PublicBusinessLogoController(BusinessLogoStorageService businessLogoStorageService) {
    this.businessLogoStorageService = businessLogoStorageService;
  }

  @GetMapping("/{businessId}/{filename:.+}")
  @Operation(summary = "Télécharger le logo entreprise (fichier uploadé)")
  public ResponseEntity<Resource> getLogo(
      @PathVariable UUID businessId, @PathVariable String filename) {
    Resource resource = businessLogoStorageService.loadAsResource(businessId, filename);
    if (resource == null || !resource.exists()) {
      return ResponseEntity.notFound().build();
    }
    Path path;
    try {
      path = resource.getFile().toPath();
    } catch (IOException e) {
      return ResponseEntity.notFound().build();
    }
    String contentType = businessLogoStorageService.probeContentType(path);
    MediaType mediaType = MediaType.parseMediaType(contentType != null ? contentType : "image/png");
    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
        .body(resource);
  }
}
