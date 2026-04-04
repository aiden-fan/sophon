package com.sophon.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 阶段 16：WebFlux 入口；{@code java -cp … com.sophon.web.SophonWebApplication} 或 {@code mvn spring-boot:run}（需插件）。
 */
@SpringBootApplication(scanBasePackageClasses = SophonWebApplication.class)
@Import(SophonWebConfiguration.class)
public class SophonWebApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SophonWebApplication.class);
        app.setAdditionalProfiles("web");
        app.run(args);
    }
}
