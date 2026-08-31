package com.ecom360.sales.application.service;

import com.ecom360.sales.domain.model.Sale;
import com.ecom360.sales.domain.model.SalePayment;
import com.ecom360.sales.domain.model.SalePaymentKind;
import com.ecom360.sales.domain.repository.SalePaymentRepository;
import com.ecom360.sales.domain.repository.SaleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Impute un remboursement global du client sur ses ventes impayées, de la plus
 * ancienne à la plus récente. Sans cette imputation, le solde client et la somme
 * des restes dus divergeraient dès le premier remboursement enregistré depuis la
 * fiche client.
 */
@Service
public class SalePaymentAllocationService {

  private final SaleRepository saleRepo;
  private final SalePaymentRepository salePaymentRepo;

  public SalePaymentAllocationService(
      SaleRepository saleRepo, SalePaymentRepository salePaymentRepo) {
    this.saleRepo = saleRepo;
    this.salePaymentRepo = salePaymentRepo;
  }

  /**
   * @return la part du montant qui n'a pu être imputée à aucune vente (avoir sur le
   *     compte client, par exemple après une reprise de données antérieure).
   */
  @Transactional
  public int allocateClientRepayment(
      UUID businessId,
      UUID clientId,
      UUID storeId,
      UUID userId,
      int amount,
      String paymentMethod,
      UUID clientPaymentId,
      String note) {
    int left = amount;
    List<Sale> outstanding = saleRepo.findOutstandingByClient(businessId, clientId);
    for (Sale sale : outstanding) {
      if (left <= 0) {
        break;
      }
      int applied = Math.min(left, sale.getRemainingAmount());
      if (applied <= 0) {
        continue;
      }
      sale.applyPayment(applied);
      if (!sale.hasOutstandingBalance()) {
        sale.setDueDate(null);
      }
      saleRepo.save(sale);

      SalePayment payment = SalePayment.record(
          sale, userId, applied, paymentMethod, SalePaymentKind.INSTALLMENT, note);
      payment.setStoreId(storeId);
      payment.setClientPaymentId(clientPaymentId);
      salePaymentRepo.save(payment);

      left -= applied;
    }
    return left;
  }
}
