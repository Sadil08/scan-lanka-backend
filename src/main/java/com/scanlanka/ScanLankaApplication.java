package com.scanlanka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ScanLanka e-commerce backend.
 * Modular monolith, package-by-feature under com.scanlanka.* (see global/09_ENGINEERING_STANDARDS.md).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ScanLankaApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScanLankaApplication.class, args);
    }
}
