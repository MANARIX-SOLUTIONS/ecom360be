package com.ecom360.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.files")
public record AppFilesProperties(
        /** Répertoire racine pour les logos entreprise (fichiers persistés). */
        String businessLogosDir,
        /** Répertoire racine pour les images produits (fichiers persistés). */
        String productImagesDir) {
}
