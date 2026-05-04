plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("io.spring.dependency-management")
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
    mavenBom("org.springframework.ai:spring-ai-bom:1.0.0")
  }
}

dependencies {
  implementation(project(":core"))
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-validation")

  implementation("jakarta.mail:jakarta.mail-api:2.1.3")
  implementation("org.eclipse.angus:angus-mail:2.0.3")
  implementation("org.jsoup:jsoup:1.18.3")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation(testFixtures(project(":core")))
  testImplementation("org.testcontainers:postgresql:1.20.4")
  testImplementation("org.testcontainers:junit-jupiter:1.20.4")
  testImplementation("io.mockk:mockk:1.13.13")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testImplementation("com.icegreen:greenmail:2.1.3")
  testImplementation("com.icegreen:greenmail-junit5:2.1.3")
}

kotlin { jvmToolchain(21) }
