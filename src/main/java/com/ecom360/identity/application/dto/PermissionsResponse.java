package com.ecom360.identity.application.dto;

import java.util.List;
import java.util.Map;

/** Permissions effectives + matrice navigation (clé écran → codes requis, au moins un). */
public record PermissionsResponse(
    String role, List<String> permissions, Map<String, List<String>> navigationRules) {}
