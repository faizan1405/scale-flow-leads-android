package com.scaleflow.leads.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

object CallLogHelper {

    fun hasCallLogPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Queries Android's Call Log for the most recent outgoing call matching [rawPhone]
     * placed around or after [sinceMillis]. Returns a clean human-readable duration string
     * such as "2m 14s", "42s", or "0s (No Answer)".
     */
    fun findRecentCallDuration(
        context: Context,
        rawPhone: String,
        sinceMillis: Long
    ): String {
        val targetDigits = rawPhone.filter { it.isDigit() }.takeLast(10)
        if (!hasCallLogPermission(context) || targetDigits.isEmpty()) {
            val approxSec = ((System.currentTimeMillis() - sinceMillis) / 1000L).coerceAtLeast(0L)
            return if (approxSec > 0) "${formatSeconds(approxSec)} (approx)" else "0s"
        }

        return runCatching {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DURATION,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE
            )
            val selection = "${CallLog.Calls.DATE} >= ?"
            val selectionArgs = arrayOf((sinceMillis - 5_000L).toString())
            val sortOrder = "${CallLog.Calls.DATE} DESC"

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                while (cursor.moveToNext()) {
                    val number = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""
                    val digits = number.filter { it.isDigit() }.takeLast(10)
                    if (digits == targetDigits || (digits.length >= 7 && targetDigits.endsWith(digits))) {
                        val sec = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                        return@runCatching if (sec <= 0L) "0s (No Answer)" else formatSeconds(sec)
                    }
                }
            }
            "0s (Not Dialed)"
        }.getOrElse {
            val approxSec = ((System.currentTimeMillis() - sinceMillis) / 1000L).coerceAtLeast(0L)
            formatSeconds(approxSec)
        }
    }

    fun formatSeconds(totalSeconds: Long): String {
        if (totalSeconds <= 0L) return "0s"
        val hrs = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return when {
            hrs > 0 -> "${hrs}h ${mins}m ${secs}s"
            mins > 0 -> "${mins}m ${secs}s"
            else -> "${secs}s"
        }
    }
}
