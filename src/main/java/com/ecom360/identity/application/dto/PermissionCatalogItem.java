package com.ecom360.identity.application.dto;

/** Entrée du catalogue permissions (affichage SaaS : libellé + regroupement). */
public record PermissionCatalogItem(
    String code, String label, String category, int sortOrder) {}
