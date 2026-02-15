package com.ecom360;

import com.ecom360.identity.infrastructure.security.JwtProperties;
import com.ecom360.shared.infrastructure.config.CorsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class Ecom360Application {

  public static void main(String[] args) {
    SpringApplication.run(Ecom360Application.class, args);
  }
}
