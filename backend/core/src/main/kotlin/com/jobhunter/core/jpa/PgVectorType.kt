package com.jobhunter.core.jpa

import com.pgvector.PGvector
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

class PgVectorType : UserType<FloatArray> {
    override fun getSqlType(): Int = Types.OTHER
    override fun returnedClass(): Class<FloatArray> = FloatArray::class.java
    override fun equals(x: FloatArray?, y: FloatArray?): Boolean = x.contentEquals(y)
    override fun hashCode(x: FloatArray?): Int = x?.contentHashCode() ?: 0

    override fun nullSafeGet(
        rs: ResultSet,
        position: Int,
        session: SharedSessionContractImplementor?,
        owner: Any?,
    ): FloatArray? {
        val obj = rs.getObject(position)
        if (rs.wasNull() || obj == null) return null
        return when (obj) {
            is PGvector -> obj.toArray()
            else -> PGvector(obj.toString()).toArray()
        }
    }

    override fun nullSafeSet(
        st: PreparedStatement,
        value: FloatArray?,
        index: Int,
        session: SharedSessionContractImplementor?,
    ) {
        if (value == null) {
            st.setNull(index, Types.OTHER)
        } else {
            st.setObject(index, PGvector(value))
        }
    }

    override fun deepCopy(value: FloatArray?): FloatArray? = value?.copyOf()
    override fun isMutable(): Boolean = true
    override fun disassemble(value: FloatArray?): Serializable? = value
    override fun assemble(cached: Serializable?, owner: Any?): FloatArray? = cached as? FloatArray
}
