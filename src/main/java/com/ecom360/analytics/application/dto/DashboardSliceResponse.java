package com.ecom360.analytics.application.dto;

import java.util.List;

/** Page de résultats pour listes dashboard (produits vendus, stock faible). */
public record DashboardSliceResponse<T>(
    List<T> content, long total, int page, int size, boolean hasNext) {}
