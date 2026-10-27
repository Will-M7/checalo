package pe.willian.yapebridge.model

/** Extracts only an explicitly labelled Yape security code. */
object YapeSecurityCodeExtractor {

    private val pattern = Regex(
        "(?i)(?:c[oó]digo(?:\\s+de\\s+seguridad)?)\\s*:?\\s*(?<!\\d)(\\d{3})(?!\\d)",
    )

    fun extract(notification: CapturedNotification): String? = extract(
        buildList {
            add(notification.title)
            add(notification.text)
            add(notification.bigText)
            add(notification.subText)
            add(notification.summaryText)
            addAll(notification.textLines)
            addAll(notification.extras.values)
        },
    )

    fun extract(fields: Iterable<String?>): String? = fields
        .asSequence()
        .filterNotNull()
        .flatMap { pattern.findAll(it).map { match -> match.groupValues[1] } }
        .distinct()
        .toList()
        .singleOrNull()
}
