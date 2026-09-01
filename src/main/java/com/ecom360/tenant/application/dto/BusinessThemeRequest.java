package com.ecom360.tenant.application.dto;

import jakarta.validation.constraints.Size;

/**
 * Remplace les couleurs de thème. Les deux champs sont envoyés ensemble : null
 * ou vide = token
 * plateforme. Plan Business requis si au moins une couleur est définie.
 */
public record BusinessThemeRequest(
        @Size(max = 16) String themePrimaryColor, @Size(max = 16) String themeAccentColor) {
}
