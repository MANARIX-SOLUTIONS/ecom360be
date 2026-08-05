package com.ecom360.supplier.application.service;

import com.ecom360.supplier.domain.model.PurchaseOrder;
import com.ecom360.supplier.domain.model.PurchaseOrderPayment;
import com.ecom360.supplier.domain.model.PurchaseOrderPaymentKind;
import com.ecom360.supplier.domain.repository.PurchaseOrderPaymentRepository;
import com.ecom360.supplier.domain.repository.PurchaseOrderRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Impute un paiement global du fournisseur sur ses bons réceptionnés impayés,
 * du plus ancien au plus récent. Sans cette imputation, le solde fournisseur et
 * la somme des restes dus divergeraient dès le premier paiement saisi depuis la
 * fiche fournisseur.
 */
@Service
public class PurchaseOrderPaymentAllocationService {

  private final PurchaseOrderRepository poRepo;
  private final PurchaseOrderPaymentRepository poPaymentRepo;

  public PurchaseOrderPaymentAllocationService(
      PurchaseOrderRepository poRepo, PurchaseOrderPaymentRepository poPaymentRepo) {
    this.poRepo = poRepo;
    this.poPaymentRepo = poPaymentRepo;
  }

  /**
   * @return la part du montant qui n'a pu être imputée à aucun bon (avance, ou
   *     reprise de données antérieure).
   */
  @Transactional
  public int allocateSupplierRepayment(
      UUID businessId,
      UUID supplierId,
      UUID userId,
      int amount,
      String paymentMethod,
      UUID supplierPaymentId,
      String note) {
    int left = amount;
    List<PurchaseOrder> outstanding = poRepo.findOutstandingBySupplier(businessId, supplierId);
    for (PurchaseOrder po : outstanding) {
      if (left <= 0) {
        break;
      }
      int applied = Math.min(left, po.getRemainingAmount());
      if (applied <= 0) {
        continue;
      }
      po.applyPayment(applied);
      if (!po.hasOutstandingBalance()) {
        po.setDueDate(null);
      }
      poRepo.save(po);

      PurchaseOrderPayment payment =
          PurchaseOrderPayment.record(
              po, userId, applied, paymentMethod, PurchaseOrderPaymentKind.INSTALLMENT, note);
      payment.setSupplierPaymentId(supplierPaymentId);
      poPaymentRepo.save(payment);

      left -= applied;
    }
    return left;
  }
}
