package com.jobhunter.processing.config

import com.jobhunter.core.domain.Category
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jobhunter")
data class ProcessingProperties(
    val monitoredCategories: List<Category> = listOf(
        Category.SOFTWARE_BACKEND,
        Category.SOFTWARE_FULLSTACK,
        Category.DEVOPS,
        Category.SRE,
        Category.PLATFORM,
    ),
)
