package com.ecom360.catalog.infrastructure.web;

import com.ecom360.catalog.infrastructure.storage.ProductImageStorageService;
import com.ecom360.shared.infrastructure.web.ApiConstants;
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

/**
 * Sert les images produits sans authentification (affichage &lt;img&gt; POS /
 * catalogue).
 */
@RestController
@RequestMapping(ApiConstants.API_BASE + "/public/product-images")
@Tag(name = "Public", description = "Fichiers publics")
public class PublicProductImageController {

  private final ProductImageStorageService productImageStorageService;

  public PublicProductImageController(ProductImageStorageService productImageStorageService) {
    this.productImageStorageService = productImageStorageService;
  }

  @GetMapping("/{businessId}/{filename:.+}")
  @Operation(summary = "Télécharger l'image produit (fichier uploadé)")
  public ResponseEntity<Resource> getImage(
      @PathVariable UUID businessId, @PathVariable String filename) {
    Resource resource = productImageStorageService.loadAsResource(businessId, filename);
    if (resource == null || !resource.exists()) {
      return ResponseEntity.notFound().build();
    }
    Path path;
    try {
      path = resource.getFile().toPath();
    } catch (IOException e) {
      return ResponseEntity.notFound().build();
    }
    String contentType = productImageStorageService.probeContentType(path);
    MediaType mediaType = MediaType.parseMediaType(contentType != null ? contentType : "image/png");
    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
        .body(resource);
  }
}
