package com.ecom360.client.application.service;

import com.ecom360.client.application.dto.*;
import com.ecom360.client.domain.model.*;
import com.ecom360.client.domain.repository.*;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.*;
import com.ecom360.tenant.application.service.SubscriptionService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {

  private final ClientRepository clientRepo;
  private final ClientPaymentRepository paymentRepo;
  private final SubscriptionService subscriptionService;

  public ClientService(
      ClientRepository clientRepo,
      ClientPaymentRepository paymentRepo,
      SubscriptionService subscriptionService) {
    this.clientRepo = clientRepo;
    this.paymentRepo = paymentRepo;
    this.subscriptionService = subscriptionService;
  }

  public ClientResponse create(ClientRequest r, UserPrincipal p) {
    requireBiz(p);
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!plan.isUnlimited(plan.getMaxClients())) {
                long count = clientRepo.countByBusinessId(p.businessId());
                if (count >= plan.getMaxClients()) {
                  throw new BusinessRuleException(
                      "Limite du plan atteinte : maximum "
                          + plan.getMaxClients()
                          + " client(s). Passez à un plan supérieur.");
                }
              }
            });
    Client c = new Client();
    c.setBusinessId(p.businessId());
    c.setName(r.name());
    c.setPhone(r.phone());
    c.setEmail(r.email());
    c.setAddress(r.address());
    c.setNotes(r.notes());
    c.setIsActive(r.isActive());
    return map(clientRepo.save(c));
  }

  public Page<ClientResponse> list(UserPrincipal p, Pageable pg) {
    requireBiz(p);
    return clientRepo.findByBusinessIdAndIsActive(p.businessId(), true, pg).map(this::map);
  }

  public ClientResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    return map(find(id, p));
  }

  public ClientResponse update(UUID id, ClientRequest r, UserPrincipal p) {
    requireBiz(p);
    Client c = find(id, p);
    c.setName(r.name());
    c.setPhone(r.phone());
    c.setEmail(r.email());
    c.setAddress(r.address());
    c.setNotes(r.notes());
    c.setIsActive(r.isActive());
    return map(clientRepo.save(c));
  }

  public void delete(UUID id, UserPrincipal p) {
    requireBiz(p);
    clientRepo.delete(find(id, p));
  }

  @Transactional
  public ClientPaymentResponse recordPayment(
      UUID clientId, ClientPaymentRequest r, UserPrincipal p) {
    requireBiz(p);
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!Boolean.TRUE.equals(plan.getFeatureClientCredits())) {
                throw new BusinessRuleException(
                    "Crédits clients non inclus dans votre plan. Passez à un plan supérieur.");
              }
            });
    Client c = find(clientId, p);
    c.deductCredit(r.amount());
    clientRepo.save(c);
    ClientPayment pay = new ClientPayment();
    pay.setClientId(clientId);
    pay.setStoreId(r.storeId());
    pay.setUserId(p.userId());
    pay.setAmount(r.amount());
    pay.setPaymentMethod(r.paymentMethod());
    pay.setNote(r.note());
    pay = paymentRepo.save(pay);
    return new ClientPaymentResponse(
        pay.getId(),
        pay.getClientId(),
        pay.getStoreId(),
        pay.getUserId(),
        pay.getAmount(),
        pay.getPaymentMethod(),
        pay.getNote(),
        pay.getCreatedAt());
  }

  public Page<ClientPaymentResponse> getPayments(UUID clientId, UserPrincipal p, Pageable pg) {
    requireBiz(p);
    find(clientId, p);
    return paymentRepo
        .findByClientIdOrderByCreatedAtDesc(clientId, pg)
        .map(
            pay ->
                new ClientPaymentResponse(
                    pay.getId(),
                    pay.getClientId(),
                    pay.getStoreId(),
                    pay.getUserId(),
                    pay.getAmount(),
                    pay.getPaymentMethod(),
                    pay.getNote(),
                    pay.getCreatedAt()));
  }

  private Client find(UUID id, UserPrincipal p) {
    return clientRepo
        .findByBusinessIdAndId(p.businessId(), id)
        .orElseThrow(() -> new ResourceNotFoundException("Client", id));
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
  }

  private ClientResponse map(Client c) {
    return new ClientResponse(
        c.getId(),
        c.getBusinessId(),
        c.getName(),
        c.getPhone(),
        c.getEmail(),
        c.getAddress(),
        c.getNotes(),
        c.getCreditBalance(),
        c.getIsActive(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }
}
