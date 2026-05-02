package com.ecom360;

import com.ecom360.identity.infrastructure.security.JwtProperties;
import com.ecom360.shared.infrastructure.config.AppFilesProperties;
import com.ecom360.shared.infrastructure.config.CorsProperties;
import com.ecom360.tenant.infrastructure.payment.PayDunyaProperties;
import com.ecom360.tenant.infrastructure.payment.PaymentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
    JwtProperties.class,
    CorsProperties.class,
    AppFilesProperties.class,
    PaymentProperties.class,
    PayDunyaProperties.class
})
public class Ecom360Application {

  public static void main(String[] args) {
    SpringApplication.run(Ecom360Application.class, args);
  }
}
