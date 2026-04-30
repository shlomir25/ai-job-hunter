plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("org.springframework.boot")
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
  implementation(project(":ingestion"))
  implementation(project(":processing"))
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.retry:spring-retry")
  implementation("org.springframework:spring-aspects")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.testcontainers:postgresql:1.20.4")
  testImplementation("org.testcontainers:junit-jupiter:1.20.4")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testImplementation("io.mockk:mockk:1.13.13")
}

kotlin { jvmToolchain(21) }

springBoot {
  mainClass.set("com.jobhunter.app.JobHunterApplicationKt")
}
