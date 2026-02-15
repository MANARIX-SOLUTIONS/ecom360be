package com.ecom360.expense.domain.repository;

import com.ecom360.expense.domain.model.ExpenseCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {
  List<ExpenseCategory> findByBusinessIdOrderBySortOrderAsc(UUID bId);

  Optional<ExpenseCategory> findByBusinessIdAndId(UUID bId, UUID id);

  boolean existsByBusinessIdAndName(UUID bId, String name);
}
