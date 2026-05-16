package com.ecom360.integration.commerce.application.service;

import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Utilisateur « porteur » des opérations automatisées (import commande web) : priorité
 * propriétaire, puis gestionnaire, puis premier membre actif.
 */
@Service
public class CommerceIntegrationActingUserResolver {

  private static final int ROLE_PROPRIETAIRE = 0;
  private static final int ROLE_GESTIONNAIRE = 1;
  private static final int ROLE_OTHER = 2;

  private final BusinessUserRepository businessUserRepository;

  public CommerceIntegrationActingUserResolver(BusinessUserRepository businessUserRepository) {
    this.businessUserRepository = businessUserRepository;
  }

  public UUID resolveActingUserId(UUID businessId) {
    List<BusinessUser> members =
        businessUserRepository.findByBusinessIdOrderByCreatedAtWithRole(businessId);
    return members.stream()
        .filter(bu -> Boolean.TRUE.equals(bu.getIsActive()))
        .sorted(Comparator.comparingInt(this::roleRank))
        .findFirst()
        .map(BusinessUser::getUserId)
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    "Aucun utilisateur actif pour cette entreprise ; impossible d'enregistrer la vente importée."));
  }

  private int roleRank(BusinessUser bu) {
    String code = bu.getBusinessRole() != null ? bu.getBusinessRole().getCode() : "";
    if ("PROPRIETAIRE".equalsIgnoreCase(code)) return ROLE_PROPRIETAIRE;
    if ("GESTIONNAIRE".equalsIgnoreCase(code)) return ROLE_GESTIONNAIRE;
    return ROLE_OTHER;
  }
}
