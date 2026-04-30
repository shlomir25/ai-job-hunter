package com.jobhunter.core

import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * Minimal Spring Boot bootstrap for tests in the core module.
 * The actual production application class lives in the app module
 * (which depends on core, not the other way), so @DataJpaTest needs
 * its own discoverable @SpringBootApplication on the test classpath.
 */
@SpringBootApplication
class TestApplication
