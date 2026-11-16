package org.example.disciplinaryrecordsmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
class ViolationEmailConfig {
    @Bean(defaultCandidate = false)
    ThreadPoolTaskScheduler violationEmailScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("violation-email-");
        return scheduler;
    }
}
