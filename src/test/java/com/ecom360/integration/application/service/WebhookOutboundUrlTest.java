package com.ecom360.integration.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecom360.shared.domain.exception.BusinessRuleException;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class WebhookOutboundUrlTest {

  @Test
  void rejectsLocalhost() {
    assertThatThrownBy(() -> WebhookService.validateOutboundUrl("http://localhost:8080/hook"))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void rejectsLoopbackLiteral() {
    assertThatThrownBy(() -> WebhookService.validateOutboundUrl("http://127.0.0.1/hook"))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void rejectsUserInfo() {
    assertThatThrownBy(
        () -> WebhookService.validateOutboundUrl("https://user:pass@example.com/hook"))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void rejectsNonHttpScheme() {
    assertThatThrownBy(() -> WebhookService.validateOutboundUrl("ftp://example.com/hook"))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void blocksPrivateIpv4Ranges() throws Exception {
    assertThat(WebhookService.isBlockedAddress(InetAddress.getByName("10.0.0.1"))).isTrue();
    assertThat(WebhookService.isBlockedAddress(InetAddress.getByName("192.168.1.1"))).isTrue();
    assertThat(WebhookService.isBlockedAddress(InetAddress.getByName("172.16.0.1"))).isTrue();
    assertThat(WebhookService.isBlockedAddress(InetAddress.getByName("100.64.1.1"))).isTrue();
    assertThat(WebhookService.isBlockedAddress(InetAddress.getByName("169.254.169.254"))).isTrue();
  }
}
