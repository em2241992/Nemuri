package org.koitharu.kotatsu.core.db

import androidx.sqlite.db.SimpleSQLiteQuery
import java.util.LinkedList

/**
 * Preserved and enhanced Kotatsu dynamic SQLite query builder for fast library filtering and sorting.
 */
class MangaQueryBuilder(
	private val table: String,
) {
	private val whereConditions = LinkedList<String>()
	private var orderBy: String? = null
	private var groupBy: String? = null
	private var extraJoins: String? = null
	private var limit: Int = 0

	fun where(condition: String) = apply {
		if (condition.isNotBlank()) {
			whereConditions.add(condition)
		}
	}

	fun orderBy(orderBy: String?) = apply {
		this.orderBy = orderBy
	}

	fun groupBy(groupBy: String?) = apply {
		this.groupBy = groupBy
	}

	fun limit(limit: Int) = apply {
		this.limit = limit
	}

	fun join(join: String?) = apply {
		this.extraJoins = join
	}

	fun build(): SimpleSQLiteQuery = buildString {
		append("SELECT * FROM ")
		append(table)
		extraJoins?.let {
			append(' ')
			append(it)
		}
		if (whereConditions.isNotEmpty()) {
			whereConditions.joinTo(
				buffer = this,
				prefix = " WHERE ",
				separator = " AND ",
			)
		}
		groupBy?.let {
			append(" GROUP BY ")
			append(it)
		}
		orderBy?.let {
			append(" ORDER BY ")
			append(it)
		}
		if (limit > 0) {
			append(" LIMIT ")
			append(limit)
		}
	}.let { SimpleSQLiteQuery(it) }
}
