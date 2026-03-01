package com.ecom360.expense.domain.repository;

import com.ecom360.expense.domain.model.Expense;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
  Page<Expense> findByBusinessIdOrderByExpenseDateDesc(UUID bId, Pageable p);

  Page<Expense> findByBusinessIdAndCategoryIdOrderByExpenseDateDesc(UUID bId, UUID cId, Pageable p);

  Page<Expense> findByBusinessIdAndStoreIdOrderByExpenseDateDesc(UUID bId, UUID sId, Pageable p);

  Page<Expense> findByBusinessIdAndExpenseDateBetweenOrderByExpenseDateDesc(
      UUID bId, LocalDate start, LocalDate end, Pageable p);

  Page<Expense> findByBusinessIdAndCategoryIdAndExpenseDateBetweenOrderByExpenseDateDesc(
      UUID bId, UUID cId, LocalDate start, LocalDate end, Pageable p);

  Page<Expense> findByBusinessIdAndStoreIdAndExpenseDateBetweenOrderByExpenseDateDesc(
      UUID bId, UUID sId, LocalDate start, LocalDate end, Pageable p);

  Optional<Expense> findByBusinessIdAndId(UUID bId, UUID id);

  @Query(
      "SELECT COALESCE(SUM(e.amount),0) FROM Expense e WHERE e.businessId=:b AND e.expenseDate BETWEEN :startDate AND :endDate")
  long sumAmountByBusinessIdAndDateBetween(
      @Param("b") UUID b,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  @Query(
      "SELECT COALESCE(SUM(e.amount),0) FROM Expense e WHERE e.businessId=:b AND e.storeId=:storeId"
          + " AND e.expenseDate BETWEEN :startDate AND :endDate")
  long sumAmountByBusinessIdAndStoreIdAndDateBetween(
      @Param("b") UUID b,
      @Param("storeId") UUID storeId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  long countByBusinessIdAndCategoryId(UUID businessId, UUID categoryId);
}
