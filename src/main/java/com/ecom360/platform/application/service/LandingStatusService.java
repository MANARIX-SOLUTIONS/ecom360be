package com.ecom360.platform.application.service;

import com.ecom360.platform.application.dto.LandingStatusResponse;
import com.ecom360.platform.domain.model.PlatformConfig;
import com.ecom360.platform.domain.repository.PlatformConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LandingStatusService {

  public static final String LANDING_LOADING_MODE_KEY = "landing_loading_mode";

  private final PlatformConfigRepository platformConfigRepository;

  public LandingStatusService(PlatformConfigRepository platformConfigRepository) {
    this.platformConfigRepository = platformConfigRepository;
  }

  @Transactional(readOnly = true)
  public LandingStatusResponse getStatus() {
    return new LandingStatusResponse(isLoading());
  }

  @Transactional
  public LandingStatusResponse update(boolean loading) {
    PlatformConfig config =
        platformConfigRepository
            .findByKey(LANDING_LOADING_MODE_KEY)
            .orElseGet(this::newConfig);
    config.setValue(Boolean.toString(loading));
    platformConfigRepository.save(config);
    return new LandingStatusResponse(loading);
  }

  private PlatformConfig newConfig() {
    PlatformConfig created = new PlatformConfig();
    created.setKey(LANDING_LOADING_MODE_KEY);
    return created;
  }

  /** Absent row keeps the landing gated, so a missing seed cannot publish the site. */
  private boolean isLoading() {
    return platformConfigRepository
        .findByKey(LANDING_LOADING_MODE_KEY)
        .map(PlatformConfig::getValue)
        .map(value -> value != null && value.trim().equalsIgnoreCase("true"))
        .orElse(true);
  }
}
