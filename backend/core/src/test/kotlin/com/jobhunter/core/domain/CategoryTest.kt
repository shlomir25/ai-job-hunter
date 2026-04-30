package com.jobhunter.core.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CategoryTest {
    @Test
    fun `enum has all expected values`() {
        val names = Category.entries.map { it.name }.toSet()
        assertEquals(
            setOf(
                "SOFTWARE_BACKEND", "SOFTWARE_FULLSTACK", "SOFTWARE_FRONTEND",
                "DEVOPS", "SRE", "PLATFORM",
                "DATA_ENGINEERING", "DATA_SCIENCE", "EMBEDDED", "MOBILE",
                "QA_AUTOMATION", "SECURITY",
                "PRODUCT_MANAGEMENT", "DESIGN",
                "HUMAN_RESOURCES", "MARKETING", "SALES", "CUSTOMER_SUCCESS",
                "FINANCE", "LEGAL", "OPERATIONS", "ADMIN",
                "CONSTRUCTION", "HEALTHCARE", "EDUCATION", "OTHER",
            ),
            names,
        )
    }
}
