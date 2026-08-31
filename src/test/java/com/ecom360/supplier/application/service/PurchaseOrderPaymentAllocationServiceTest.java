package com.ecom360.supplier.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecom360.supplier.domain.model.PurchaseOrder;
import com.ecom360.supplier.domain.model.PurchaseOrderPayment;
import com.ecom360.supplier.domain.model.PurchaseOrderPaymentKind;
import com.ecom360.supplier.domain.model.PurchaseOrderPaymentStatus;
import com.ecom360.supplier.domain.repository.PurchaseOrderPaymentRepository;
import com.ecom360.supplier.domain.repository.PurchaseOrderRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderPaymentAllocationServiceTest {

  @Mock
  PurchaseOrderRepository poRepo;
  @Mock
  PurchaseOrderPaymentRepository poPaymentRepo;

  PurchaseOrderPaymentAllocationService service;

  UUID businessId = UUID.randomUUID();
  UUID supplierId = UUID.randomUUID();
  UUID userId = UUID.randomUUID();
  UUID supplierPaymentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new PurchaseOrderPaymentAllocationService(poRepo, poPaymentRepo);
  }

  @Test
  void oldestOrderIsSettledFirst() {
    PurchaseOrder oldest = poOf(5_000, 0);
    PurchaseOrder newest = poOf(8_000, 0);
    when(poRepo.findOutstandingBySupplier(businessId, supplierId))
        .thenReturn(List.of(oldest, newest));

    int unallocated = allocate(6_000);

    assertThat(unallocated).isZero();
    assertThat(oldest.getRemainingAmount()).isZero();
    assertThat(oldest.getPaymentStatus()).isEqualTo(PurchaseOrderPaymentStatus.PAID);
    assertThat(newest.getAmountPaid()).isEqualTo(1_000);
    assertThat(newest.getPaymentStatus()).isEqualTo(PurchaseOrderPaymentStatus.PARTIAL);
  }

  @Test
  void allocationStopsWhenAmountIsExhausted() {
    PurchaseOrder oldest = poOf(5_000, 0);
    PurchaseOrder newest = poOf(8_000, 0);
    when(poRepo.findOutstandingBySupplier(businessId, supplierId))
        .thenReturn(List.of(oldest, newest));

    allocate(2_000);

    assertThat(oldest.getAmountPaid()).isEqualTo(2_000);
    assertThat(newest.getAmountPaid()).isZero();
    verify(poPaymentRepo).save(any(PurchaseOrderPayment.class));
  }

  @Test
  void partiallyPaidOrderOnlyAbsorbsItsRemainder() {
    PurchaseOrder partiallyPaid = poOf(10_000, 7_000);
    PurchaseOrder untouched = poOf(4_000, 0);
    when(poRepo.findOutstandingBySupplier(businessId, supplierId))
        .thenReturn(List.of(partiallyPaid, untouched));

    allocate(5_000);

    assertThat(partiallyPaid.getAmountPaid()).isEqualTo(10_000);
    assertThat(untouched.getAmountPaid()).isEqualTo(2_000);
  }

  @Test
  void surplusIsReportedWhenNothingIsOutstanding() {
    when(poRepo.findOutstandingBySupplier(businessId, supplierId)).thenReturn(List.of());

    int unallocated = allocate(3_000);

    assertThat(unallocated).isEqualTo(3_000);
    verify(poPaymentRepo, never()).save(any());
  }

  @Test
  void eachAllocationIsTracedAgainstItsSupplierPayment() {
    PurchaseOrder oldest = poOf(5_000, 0);
    PurchaseOrder newest = poOf(8_000, 0);
    when(poRepo.findOutstandingBySupplier(businessId, supplierId))
        .thenReturn(List.of(oldest, newest));

    allocate(6_000);

    ArgumentCaptor<PurchaseOrderPayment> captor =
        ArgumentCaptor.forClass(PurchaseOrderPayment.class);
    verify(poPaymentRepo, org.mockito.Mockito.times(2)).save(captor.capture());
    List<PurchaseOrderPayment> saved = new ArrayList<>(captor.getAllValues());
    assertThat(saved).extracting(PurchaseOrderPayment::getAmount).containsExactly(5_000, 1_000);
    assertThat(saved)
        .allSatisfy(
            p -> {
              assertThat(p.getKind()).isEqualTo(PurchaseOrderPaymentKind.INSTALLMENT);
              assertThat(p.getSupplierPaymentId()).isEqualTo(supplierPaymentId);
            });
  }

  private int allocate(int amount) {
    return service.allocateSupplierRepayment(
        businessId, supplierId, userId, amount, "cash", supplierPaymentId, null);
  }

  private PurchaseOrder poOf(int total, int amountPaid) {
    PurchaseOrder po = new PurchaseOrder();
    po.setId(UUID.randomUUID());
    po.setBusinessId(businessId);
    po.setStoreId(UUID.randomUUID());
    po.setSupplierId(supplierId);
    po.setStatus("received");
    po.setTotalAmount(total);
    po.setAmountPaid(amountPaid);
    po.recomputePaymentStatus();
    return po;
  }
}
