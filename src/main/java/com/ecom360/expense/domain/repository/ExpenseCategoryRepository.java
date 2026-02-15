package com.ecom360.expense.domain.repository;
import com.ecom360.expense.domain.model.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.stereotype.Repository;
import java.util.List; import java.util.Optional; import java.util.UUID;
@Repository public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {
    List<ExpenseCategory> findByBusinessIdOrderBySortOrderAsc(UUID bId);
    Optional<ExpenseCategory> findByBusinessIdAndId(UUID bId, UUID id);
    boolean existsByBusinessIdAndName(UUID bId, String name);
}
