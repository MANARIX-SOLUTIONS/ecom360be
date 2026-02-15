package com.ecom360.tenant.application.service;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.ResourceAlreadyExistsException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.dto.BusinessProfileRequest;
import com.ecom360.tenant.application.dto.BusinessProfileResponse;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BusinessProfileService {

    private final BusinessRepository businessRepository;

    public BusinessProfileService(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    public BusinessProfileResponse get(UserPrincipal p) {
        Business b = findBusiness(p);
        return new BusinessProfileResponse(b.getId(), b.getName(), b.getEmail(), b.getPhone(), b.getAddress());
    }

    @Transactional
    public BusinessProfileResponse update(BusinessProfileRequest req, UserPrincipal p) {
        Business b = findBusiness(p);
        UUID currentId = b.getId();
        if (!req.email().equalsIgnoreCase(b.getEmail())) {
            businessRepository.findByEmail(req.email())
                    .filter(other -> !other.getId().equals(currentId))
                    .ifPresent(__ -> { throw new ResourceAlreadyExistsException("Business", req.email()); });
        }
        b.setName(req.name());
        b.setEmail(req.email());
        b.setPhone(req.phone());
        b.setAddress(req.address());
        b = businessRepository.save(b);
        return new BusinessProfileResponse(b.getId(), b.getName(), b.getEmail(), b.getPhone(), b.getAddress());
    }

    private Business findBusiness(UserPrincipal p) {
        if (p.businessId() == null) {
            throw new AccessDeniedException("Business context required");
        }
        return businessRepository.findById(p.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business", p.businessId()));
    }
}
