package com.lumvane.android.sub

data class SubscriptionInfo(val upload: Long = 0, val download: Long = 0, val total: Long = 0, val expire: Long = 0) {
    val used get() = upload + download
    val hasData get() = total > 0 || expire > 0 || used > 0

    enum class Warning { EXPIRED, QUOTA_USED_UP, EXPIRES_SOON, QUOTA_LOW }

    fun warning(nowSecs: Long): Warning? = when {
        expire in 1..nowSecs -> Warning.EXPIRED
        total > 0 && used >= total -> Warning.QUOTA_USED_UP
        expire > 0 && expire - nowSecs <= SOON_SECS -> Warning.EXPIRES_SOON
        total > 0 && used * 10 >= total * 9 -> Warning.QUOTA_LOW
        else -> null
    }

    val usedPercent: Int get() = if (total > 0) (used * 100 / total).toInt() else 0

    companion object {
        const val SOON_SECS = 3 * 24 * 3600L

        fun parse(raw: String?): SubscriptionInfo {
            if (raw.isNullOrBlank()) return SubscriptionInfo()
            val map = raw.split(';').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i).trim().lowercase() to it.substring(i + 1).trim()
            }.toMap()
            fun l(k: String) = map[k]?.toDoubleOrNull()?.toLong() ?: 0L
            return SubscriptionInfo(l("upload"), l("download"), l("total"), l("expire"))
        }
    }
}
