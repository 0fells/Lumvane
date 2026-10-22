package com.lumvane.android.importer

object IncomingPayload {
    const val MAX_LENGTH = 512 * 1024

    private const val ACTION_VIEW = "android.intent.action.VIEW"
    private const val ACTION_SEND = "android.intent.action.SEND"

    fun extract(action: String?, dataString: String?, text: CharSequence?): String? {
        val raw = when (action) {
            ACTION_VIEW -> dataString?.trim()?.takeIf { LinkParser.looksLikeLink(it) && !it.startsWith("http", ignoreCase = true) }
            ACTION_SEND -> text?.toString()?.trim()
            else -> null
        }
        return raw?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }
    }
}
