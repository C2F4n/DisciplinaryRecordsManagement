package org.example.disciplinaryrecordsmanagement.config;

import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties({BusinessPolicy.class, ReportPolicy.class, ExportPolicy.class,
        RosterPolicy.class, RetentionPolicy.class})
class BootstrapConfig {
    @Bean
    Clock businessClock(BusinessPolicy policy) {
        return Clock.system(ZoneId.of(policy.timeZone()));
    }

    @Bean
    ApplicationRunner initializeAdmin(AccountService accounts, @Value("${app.bootstrap.username}") String username,
                                     @Value("${app.bootstrap.password}") String password,
                                     @Value("${server.address}") String bindAddress) {
        return args -> accounts.bootstrap(username, password, bindAddress);
    }
}
