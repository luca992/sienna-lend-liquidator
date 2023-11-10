package utils

import msg.overseer.PaginatedResponse
import msg.overseer.Pagination

suspend fun <T> fetchAllPages(
    query: suspend (pagination: Pagination) -> PaginatedResponse<T>?,
    limit: UInt,
    filter: ((x: T) -> Boolean)?,
): List<T> {
    var start = 0u
    var total = 0u

    val result = mutableListOf<T>()

    do {
        val page = query(Pagination(limit, start))

        if (page == null) {
            start += limit
            continue
        }

        total = page.total
        start += limit

        result.addAll(if (filter != null) page.entries.filter(filter) else page.entries)

    } while (start <= total)

    return result
}

