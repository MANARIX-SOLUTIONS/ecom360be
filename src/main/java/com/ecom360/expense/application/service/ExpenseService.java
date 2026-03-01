package com.ecom360.expense.application.service;

import com.ecom360.expense.application.dto.*;
import com.ecom360.expense.domain.model.*;
import com.ecom360.expense.domain.repository.*;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.*;
import com.ecom360.tenant.application.service.SubscriptionService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ExpenseService {
  private final ExpenseRepository expenseRepo;
  private final ExpenseCategoryRepository catRepo;
  private final SubscriptionService subscriptionService;
  private final RolePermissionService permissionService;

  public ExpenseService(
      ExpenseRepository expenseRepo,
      ExpenseCategoryRepository catRepo,
      SubscriptionService subscriptionService,
      RolePermissionService permissionService) {
    this.expenseRepo = expenseRepo;
    this.catRepo = catRepo;
    this.subscriptionService = subscriptionService;
    this.permissionService = permissionService;
  }

  // ── Categories ──
  public ExpenseCategoryResponse createCategory(ExpenseCategoryRequest r, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.EXPENSES_CREATE);
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!Boolean.TRUE.equals(plan.getFeatureExpenses())) {
                throw new BusinessRuleException(
                    "Suivi des dépenses non inclus dans votre plan. Passez à un plan supérieur.");
              }
            });
    if (catRepo.existsByBusinessIdAndName(p.businessId(), r.name()))
      throw new ResourceAlreadyExistsException("Expense category", r.name());
    ExpenseCategory c = new ExpenseCategory();
    c.setBusinessId(p.businessId());
    c.setName(r.name());
    c.setColor(r.color());
    c.setSortOrder(r.sortOrder());
    return mapCat(catRepo.save(c));
  }

  public List<ExpenseCategoryResponse> listCategories(UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.EXPENSES_READ);
    return catRepo.findByBusinessIdOrderBySortOrderAsc(p.businessId()).stream()
        .map(this::mapCat)
        .toList();
  }

  public ExpenseCategoryResponse updateCategory(
      UUID id, ExpenseCategoryRequest r, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.EXPENSES_UPDATE);
    ExpenseCategory c =
        catRepo
            .findByBusinessIdAndId(p.businessId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("Expense category", id));
    c.setName(r.name());
    c.setColor(r.color());
    c.setSortOrder(r.sortOrder());
    return mapCat(catRepo.save(c));
  }

  public void deleteCategory(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.EXPENSES_DELETE);
    ExpenseCategory c =
        catRepo
            .findByBusinessIdAndId(p.businessId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("Expense category", id));
    long expenseCount = expenseRepo.countByBusinessIdAndCategoryId(p.businessId(), id);
    if (expenseCount > 0) {
      throw new BusinessRuleException(
          "Impossible de supprimer cette catégorie : " + expenseCount + " dépense(s) l'utilisent.");
    }
    catRepo.delete(c);
  }

  // ── Expenses ──
  public ExpenseResponse create(ExpenseRequest r, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.EXPENSES_CREATE);
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!Boolean.TRUE.equals(plan.getFeatureExpenses())) {
                throw new BusinessRuleException(
                    "Suivi des dépenses non inclus dans votre plan. Passez à un plan supérieur.");
              }
            });
    catRepo
        .findByBusinessIdAndId(p.businessId(), r.categoryId())
        .orElseThrow(() -> new ResourceNotFoundException("Expense category", r.categoryId()));
    Expense e = new Expense();
    e.setBusinessId(p.businessId());
    e.setUserId(p.userId());
    e.setStoreId(r.storeId());
    e.setCategoryId(r.categoryId());
    e.setAmount(r.amount());
    e.setDescription(r.description());
    e.setExpenseDate(r.expenseDate());
    e.setReceiptUrl(r.receiptUrl());
    return mapExp(expenseRepo.save(e));
  }

  public Page<ExpenseResponse> list(
      UserPrincipal p,
      UUID categoryId,
      UUID storeId,
      Integer month,
      Integer year,
      Pageable pg) {
    requireBiz(p);
    permissionService.require(p, Permission.EXPENSES_READ);
    LocalDate startDate = null;
    LocalDate endDate = null;
    if (month != null && year != null && month >= 1 && month <= 12) {
      YearMonth ym = YearMonth.of(year, month);
      startDate = ym.atDay(1);
      endDate = ym.atEndOfMonth();
    }
    if (startDate != null && endDate != null) {
      if (categoryId != null)
        return expenseRepo
            .findByBusinessIdAndCategoryIdAndExpenseDateBetweenOrderByExpenseDateDesc(
                p.businessId(), categoryId, startDate, endDate, pg)
            .map(this::mapExp);
      if (storeId != null)
        return expenseRepo
            .findByBusinessIdAndStoreIdAndExpenseDateBetweenOrderByExpenseDateDesc(
                p.businessId(), storeId, startDate, endDate, pg)
            .map(this::mapExp);
      return expenseRepo
          .findByBusinessIdAndExpenseDateBetweenOrderByExpenseDateDesc(
              p.businessId(), startDate, endDate, pg)
          .map(this::mapExp);
    }
    if (categoryId != null)
      return expenseRepo
          .findByBusinessIdAndCategoryIdOrderByExpenseDateDesc(p.businessId(), categoryId, pg)
          .map(this::mapExp);
    if (storeId != null)
      return expenseRepo
          .findByBusinessIdAndStoreIdOrderByExpenseDateDesc(p.businessId(), storeId, pg)
          .map(this::mapExp);
    return expenseRepo.findByBusinessIdOrderByExpenseDateDesc(p.businessId(), pg).map(this::mapExp);
  }

  public ExpenseResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.EXPENSES_READ);
    return mapExp(
        expenseRepo
            .findByBusinessIdAndId(p.businessId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("Expense", id)));
  }

  public ExpenseResponse update(UUID id, ExpenseRequest r, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.EXPENSES_UPDATE);
    Expense e =
        expenseRepo
            .findByBusinessIdAndId(p.businessId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("Expense", id));
    e.setStoreId(r.storeId());
    e.setCategoryId(r.categoryId());
    e.setAmount(r.amount());
    e.setDescription(r.description());
    e.setExpenseDate(r.expenseDate());
    e.setReceiptUrl(r.receiptUrl());
    return mapExp(expenseRepo.save(e));
  }

  public void delete(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.EXPENSES_DELETE);
    expenseRepo.delete(
        expenseRepo
            .findByBusinessIdAndId(p.businessId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("Expense", id)));
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
  }

  private ExpenseCategoryResponse mapCat(ExpenseCategory c) {
    return new ExpenseCategoryResponse(
        c.getId(),
        c.getBusinessId(),
        c.getName(),
        c.getColor(),
        c.getSortOrder(),
        c.getCreatedAt());
  }

  private ExpenseResponse mapExp(Expense e) {
    return new ExpenseResponse(
        e.getId(),
        e.getBusinessId(),
        e.getStoreId(),
        e.getUserId(),
        e.getCategoryId(),
        e.getAmount(),
        e.getDescription(),
        e.getExpenseDate(),
        e.getReceiptUrl(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}
