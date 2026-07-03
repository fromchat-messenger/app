package ru.fromchat.legal

enum class DocumentType(
    val fileName: String,
    val routeSegment: String,
) {
    Privacy("PRIVACY.md", "privacy"),
    Terms("TERMS.md", "terms"),
    ;

    companion object {
        const val ROUTE_PREFIX = "document"
        const val ROUTE = "$ROUTE_PREFIX/{documentType}"
        const val ARG_DOCUMENT_TYPE = "documentType"

        fun route(type: DocumentType): String = "$ROUTE_PREFIX/${type.routeSegment}"

        fun typeFromArg(arg: String): DocumentType? =
            entries.firstOrNull { it.routeSegment.equals(arg, ignoreCase = true) }
    }
}
