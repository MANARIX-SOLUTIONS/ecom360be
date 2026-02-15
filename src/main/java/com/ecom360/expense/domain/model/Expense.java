package com.ecom360.expense.domain.model;
import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate; import java.util.UUID;
@Entity @Table(name = "expense")
public class Expense extends BaseEntity {
    @Column(name = "business_id", nullable = false) private UUID businessId;
    @Column(name = "store_id") private UUID storeId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "category_id", nullable = false) private UUID categoryId;
    @Column(nullable = false) private Integer amount;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "expense_date", nullable = false) private LocalDate expenseDate;
    @Column(name = "receipt_url") private String receiptUrl;
    public UUID getBusinessId(){return businessId;} public void setBusinessId(UUID v){this.businessId=v;} public UUID getStoreId(){return storeId;} public void setStoreId(UUID v){this.storeId=v;} public UUID getUserId(){return userId;} public void setUserId(UUID v){this.userId=v;} public UUID getCategoryId(){return categoryId;} public void setCategoryId(UUID v){this.categoryId=v;} public Integer getAmount(){return amount;} public void setAmount(Integer v){this.amount=v;} public String getDescription(){return description;} public void setDescription(String v){this.description=v;} public LocalDate getExpenseDate(){return expenseDate;} public void setExpenseDate(LocalDate v){this.expenseDate=v;} public String getReceiptUrl(){return receiptUrl;} public void setReceiptUrl(String v){this.receiptUrl=v;}
}
