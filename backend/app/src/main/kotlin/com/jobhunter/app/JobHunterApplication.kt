package com.jobhunter.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.retry.annotation.EnableRetry

@SpringBootApplication
@ComponentScan(basePackages = ["com.jobhunter"])
@EntityScan("com.jobhunter.core.domain")
@EnableJpaRepositories("com.jobhunter.core.repository")
@EnableScheduling
@EnableAsync
@EnableRetry
class JobHunterApplication

fun main(args: Array<String>) {
    runApplication<JobHunterApplication>(*args)
}
