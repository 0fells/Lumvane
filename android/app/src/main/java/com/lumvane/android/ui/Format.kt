package com.lumvane.android.ui

import java.text.DateFormat
import java.util.Date

fun latencyText(ms: Int): String? = if (ms > 0) "$ms ms" else null

fun formatDateTime(epochSecs: Long): String =
    if (epochSecs <= 0) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochSecs * 1000))

fun formatDate(epochSecs: Long): String =
    if (epochSecs <= 0) "" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochSecs * 1000))

fun splitList(text: String, separator: Char = ','): List<String> =
    text.split(separator).map { it.trim() }.filter { it.isNotEmpty() }

