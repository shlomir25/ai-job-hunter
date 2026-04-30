package com.jobhunter.core.jpa

import org.junit.jupiter.api.Test
import java.sql.PreparedStatement
import java.sql.ResultSet
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import com.pgvector.PGvector
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PgVectorTypeTest {
    private val type = PgVectorType()

    @Test
    fun `nullSafeGet returns null for null column`() {
        val rs = mockk<ResultSet>()
        every { rs.getObject(1) } returns null
        every { rs.wasNull() } returns true
        assertNull(type.nullSafeGet(rs, 1, mockk(relaxed = true), null))
    }

    @Test
    fun `nullSafeGet maps PGvector to FloatArray`() {
        val rs = mockk<ResultSet>()
        every { rs.getObject(1) } returns PGvector(floatArrayOf(0.1f, 0.2f, 0.3f))
        every { rs.wasNull() } returns false
        val result = type.nullSafeGet(rs, 1, mockk(relaxed = true), null)
        assertEquals(listOf(0.1f, 0.2f, 0.3f), (result as FloatArray).toList())
    }

    @Test
    fun `nullSafeSet writes PGvector for non-null FloatArray`() {
        val ps = mockk<PreparedStatement>(relaxed = true)
        val value = floatArrayOf(0.5f, 0.6f)
        type.nullSafeSet(ps, value, 1, mockk(relaxed = true))
        verify { ps.setObject(1, match<PGvector> { it.toArray().toList() == listOf(0.5f, 0.6f) }) }
    }
}
