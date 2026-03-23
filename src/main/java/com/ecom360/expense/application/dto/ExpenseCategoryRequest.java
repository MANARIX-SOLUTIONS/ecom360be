package com.ecom360.expense.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExpenseCategoryRequest(
    @NotBlank @Size(max = 255) String name, @Size(max = 20) String color, Integer sortOrder) {
  public ExpenseCategoryRequest {
    if (sortOrder == null) sortOrder = 0;
  }
}
