package com.ecom360.tenant.payment.infrastructure.bictorys;

/** One Bictorys merchant account: the platform's (subscriptions) or a business's (POS). */
public record BictorysCredentials(
    String baseUrl, String apiKey, String webhookSecret, String country) {

  public static final String TEST_BASE_URL = "https://api.test.bictorys.com";
  public static final String LIVE_BASE_URL = "https://api.bictorys.com";

  public static BictorysCredentials fromProperties(BictorysProperties p) {
    return new BictorysCredentials(
        p.getBaseUrl(), p.getApiKey(), p.getWebhookSecret(), p.getCountry());
  }

  public static BictorysCredentials forEnvironment(
      boolean live, String apiKey, String webhookSecret, String country) {
    return new BictorysCredentials(
        live ? LIVE_BASE_URL : TEST_BASE_URL,
        apiKey,
        webhookSecret,
        country == null || country.isBlank() ? "SN" : country);
  }

  @Override
  public String toString() {
    return "BictorysCredentials[baseUrl=" + baseUrl + ", country=" + country + "]";
  }
}
