package com.jobhunter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Minimal Spring Boot bootstrap for tests across all backend modules.
 * Lives in com.jobhunter (not com.jobhunter.core) so Spring's package-walk-up
 * search from any test class — under com.jobhunter.core, .ingestion, etc. —
 * discovers it. The production @SpringBootApplication is in the app module,
 * which is not on the test classpath of feature modules.
 */
@SpringBootApplication
@EntityScan("com.jobhunter.core.domain")
@EnableJpaRepositories("com.jobhunter.core.repository")
class TestApplication
