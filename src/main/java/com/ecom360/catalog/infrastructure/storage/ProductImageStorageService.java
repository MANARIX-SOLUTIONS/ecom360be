package com.ecom360.catalog.infrastructure.storage;

import com.ecom360.shared.domain.exception.DomainException;
import com.ecom360.shared.infrastructure.config.AppFilesProperties;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImageStorageService {

  private static final Logger log = LoggerFactory.getLogger(ProductImageStorageService.class);

  private static final Pattern SAFE_FILENAME = Pattern.compile(
      "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(png|jpg|jpeg|webp|gif)$");

  private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

  /** Must stay in sync with {@code spring.servlet.multipart.max-file-size}. */
  private static final long MAX_BYTES = 8 * 1024 * 1024;

  private final Path root;

  public ProductImageStorageService(AppFilesProperties props) {
    String dir = props.productImagesDir() != null && !props.productImagesDir().isBlank()
        ? props.productImagesDir()
        : "./data/uploads/product-images";
    this.root = Path.of(dir).toAbsolutePath().normalize();
  }

  @PostConstruct
  void ensureStorageRootExists() {
    try {
      Files.createDirectories(root);
      log.info("Product images storage ready at {}", root);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Cannot create or access PRODUCT_IMAGES_DIR"
              + " (app.files.product-images-dir): "
              + root,
          e);
    }
  }

  /**
   * Chemin relatif API stocké en base, ex. {@code
   * /api/v1/public/product-images/{id}/file.png}
   */
  public String saveUploadedImage(UUID businessId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new DomainException("Fichier vide");
    }
    if (file.getSize() > MAX_BYTES) {
      throw new DomainException("Image trop volumineuse (max. 8 Mo)");
    }
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
      throw new DomainException("Format non supporté (PNG, JPEG, WebP ou GIF)");
    }
    String ext = extensionForMime(contentType.toLowerCase(Locale.ROOT));
    String filename = UUID.randomUUID() + "." + ext;
    Path dir = root.resolve(businessId.toString());
    try {
      Files.createDirectories(dir);
      Path target = dir.resolve(filename);
      try (InputStream in = file.getInputStream()) {
        Files.copy(in, target);
      }
    } catch (IOException e) {
      throw new DomainException("Enregistrement du fichier impossible", e);
    }
    return ApiConstants.API_BASE
        + "/public/product-images/"
        + businessId
        + "/"
        + filename;
  }

  public void deleteManagedImageIfPresent(UUID businessId, String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) {
      return;
    }
    parseManagedFilename(businessId, imageUrl).ifPresent(this::deleteQuietly);
  }

  /** Charge le fichier pour exposition HTTP publique. */
  public Resource loadAsResource(UUID businessId, String filename) {
    if (!SAFE_FILENAME.matcher(filename).matches()) {
      return null;
    }
    Path base = root.resolve(businessId.toString()).normalize();
    Path file = base.resolve(filename).normalize();
    if (!file.startsWith(base)) {
      return null;
    }
    if (!Files.isRegularFile(file)) {
      return null;
    }
    return new FileSystemResource(file);
  }

  public String probeContentType(Path path) {
    try {
      return Files.probeContentType(path);
    } catch (IOException e) {
      return "application/octet-stream";
    }
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // best-effort cleanup
    }
  }

  private Optional<Path> parseManagedFilename(UUID businessId, String imageUrl) {
    String prefix = ApiConstants.API_BASE + "/public/product-images/" + businessId + "/";
    if (!imageUrl.startsWith(prefix)) {
      return Optional.empty();
    }
    String rest = imageUrl.substring(prefix.length());
    if (rest.contains("/") || rest.contains("..")) {
      return Optional.empty();
    }
    if (!SAFE_FILENAME.matcher(rest).matches()) {
      return Optional.empty();
    }
    return Optional.of(
        root.resolve(businessId.toString()).resolve(rest).normalize());
  }

  private static String extensionForMime(String mime) {
    return switch (mime) {
      case "image/png" -> "png";
      case "image/jpeg" -> "jpg";
      case "image/webp" -> "webp";
      case "image/gif" -> "gif";
      default -> "bin";
    };
  }
}
