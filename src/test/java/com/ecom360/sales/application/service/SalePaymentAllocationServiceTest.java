package com.ecom360.sales.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecom360.sales.domain.model.Sale;
import com.ecom360.sales.domain.model.SalePayment;
import com.ecom360.sales.domain.model.SalePaymentKind;
import com.ecom360.sales.domain.model.SalePaymentStatus;
import com.ecom360.sales.domain.repository.SalePaymentRepository;
import com.ecom360.sales.domain.repository.SaleRepository;
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
class SalePaymentAllocationServiceTest {

  @Mock
  SaleRepository saleRepo;
  @Mock
  SalePaymentRepository salePaymentRepo;

  SalePaymentAllocationService service;

  UUID businessId = UUID.randomUUID();
  UUID clientId = UUID.randomUUID();
  UUID storeId = UUID.randomUUID();
  UUID userId = UUID.randomUUID();
  UUID clientPaymentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new SalePaymentAllocationService(saleRepo, salePaymentRepo);
  }

  @Test
  void oldestSaleIsSettledFirst() {
    Sale oldest = saleOf(5_000, 0);
    Sale newest = saleOf(8_000, 0);
    when(saleRepo.findOutstandingByClient(businessId, clientId))
        .thenReturn(List.of(oldest, newest));

    int unallocated = allocate(6_000);

    assertThat(unallocated).isZero();
    assertThat(oldest.getRemainingAmount()).isZero();
    assertThat(oldest.getPaymentStatus()).isEqualTo(SalePaymentStatus.PAID);
    assertThat(newest.getAmountPaid()).isEqualTo(1_000);
    assertThat(newest.getPaymentStatus()).isEqualTo(SalePaymentStatus.PARTIAL);
  }

  @Test
  void allocationStopsWhenAmountIsExhausted() {
    Sale oldest = saleOf(5_000, 0);
    Sale newest = saleOf(8_000, 0);
    when(saleRepo.findOutstandingByClient(businessId, clientId))
        .thenReturn(List.of(oldest, newest));

    allocate(2_000);

    assertThat(oldest.getAmountPaid()).isEqualTo(2_000);
    assertThat(newest.getAmountPaid()).isZero();
    verify(salePaymentRepo).save(any(SalePayment.class));
  }

  @Test
  void partiallyPaidSaleOnlyAbsorbsItsRemainder() {
    Sale partiallyPaid = saleOf(10_000, 7_000);
    Sale untouched = saleOf(4_000, 0);
    when(saleRepo.findOutstandingByClient(businessId, clientId))
        .thenReturn(List.of(partiallyPaid, untouched));

    allocate(5_000);

    assertThat(partiallyPaid.getAmountPaid()).isEqualTo(10_000);
    assertThat(untouched.getAmountPaid()).isEqualTo(2_000);
  }

  @Test
  void surplusIsReportedWhenNothingIsOutstanding() {
    when(saleRepo.findOutstandingByClient(businessId, clientId)).thenReturn(List.of());

    int unallocated = allocate(3_000);

    assertThat(unallocated).isEqualTo(3_000);
    verify(salePaymentRepo, never()).save(any());
  }

  @Test
  void eachAllocationIsTracedAgainstItsClientPayment() {
    Sale oldest = saleOf(5_000, 0);
    Sale newest = saleOf(8_000, 0);
    when(saleRepo.findOutstandingByClient(businessId, clientId))
        .thenReturn(List.of(oldest, newest));

    allocate(6_000);

    ArgumentCaptor<SalePayment> captor = ArgumentCaptor.forClass(SalePayment.class);
    verify(salePaymentRepo, org.mockito.Mockito.times(2)).save(captor.capture());
    List<SalePayment> saved = new ArrayList<>(captor.getAllValues());
    assertThat(saved).extracting(SalePayment::getAmount).containsExactly(5_000, 1_000);
    assertThat(saved)
        .allSatisfy(
            sp -> {
              assertThat(sp.getKind()).isEqualTo(SalePaymentKind.INSTALLMENT);
              assertThat(sp.getClientPaymentId()).isEqualTo(clientPaymentId);
              assertThat(sp.getStoreId()).isEqualTo(storeId);
            });
  }

  private int allocate(int amount) {
    return service.allocateClientRepayment(
        businessId, clientId, storeId, userId, amount, "cash", clientPaymentId, null);
  }

  private Sale saleOf(int total, int amountPaid) {
    Sale sale = new Sale();
    sale.setId(UUID.randomUUID());
    sale.setBusinessId(businessId);
    sale.setStoreId(UUID.randomUUID());
    sale.setClientId(clientId);
    sale.setStatus("completed");
    sale.setTotal(total);
    sale.setAmountPaid(amountPaid);
    sale.recomputePaymentStatus();
    return sale;
  }
}
