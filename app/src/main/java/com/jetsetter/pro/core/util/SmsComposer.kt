package com.jetsetter.pro.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the native SMS composer prefilled with recipients and a body (spec §3.3). Uses
 * `ACTION_SENDTO` on an `smsto:` URI so no SEND_SMS permission is required and nothing is ever
 * sent silently — the user reviews and taps send in their own messaging app.
 */
object SmsComposer {

    /**
     * Launches the composer for [phoneNumbers] with [body] prefilled. Multiple recipients are
     * joined with `;` per the `smsto:` multi-recipient convention. Safe from any [Context]
     * (FLAG_ACTIVITY_NEW_TASK). No-op when [phoneNumbers] is empty or no messaging app exists.
     */
    fun composeSms(context: Context, phoneNumbers: List<String>, body: String) {
        if (phoneNumbers.isEmpty()) return
        val intent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("smsto:" + phoneNumbers.joinToString(";")),
        )
            .putExtra("sms_body", body)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // ActivityNotFoundException on devices with no SMS app (e.g. wifi tablets) — swallow.
        runCatching { context.startActivity(intent) }
    }
}
