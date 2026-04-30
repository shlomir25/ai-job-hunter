package com.jobhunter.core.jpa

import com.jobhunter.core.domain.Category
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.Array as SqlArray
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

class CategoryArrayType : UserType<List<Category>> {
    override fun getSqlType(): Int = Types.ARRAY

    @Suppress("UNCHECKED_CAST")
    override fun returnedClass(): Class<List<Category>> = List::class.java as Class<List<Category>>

    override fun equals(x: List<Category>?, y: List<Category>?): Boolean = x == y
    override fun hashCode(x: List<Category>?): Int = x?.hashCode() ?: 0

    override fun nullSafeGet(
        rs: ResultSet,
        position: Int,
        session: SharedSessionContractImplementor?,
        owner: Any?,
    ): List<Category>? {
        val sqlArray = rs.getArray(position) ?: return null
        @Suppress("UNCHECKED_CAST")
        val raw = sqlArray.array as Array<String>
        return raw.map { Category.valueOf(it) }
    }

    override fun nullSafeSet(
        st: PreparedStatement,
        value: List<Category>?,
        index: Int,
        session: SharedSessionContractImplementor?,
    ) {
        if (value == null) {
            st.setNull(index, Types.ARRAY)
            return
        }
        val conn = st.connection
        val arr: SqlArray = conn.createArrayOf("category", value.map { it.name }.toTypedArray())
        st.setArray(index, arr)
    }

    override fun deepCopy(value: List<Category>?): List<Category>? = value?.toList()
    override fun isMutable(): Boolean = false
    override fun disassemble(value: List<Category>?): Serializable? = value?.let { ArrayList(it) }

    @Suppress("UNCHECKED_CAST")
    override fun assemble(cached: Serializable?, owner: Any?): List<Category>? =
        (cached as? ArrayList<Category>)?.toList()
}
