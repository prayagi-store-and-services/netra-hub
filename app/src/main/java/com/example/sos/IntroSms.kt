package com.example.sos

import android.content.Context
import android.telephony.SmsManager

/**
 * The optional introduction SMS (v1.4.32): after saving emergency contacts the user can tell each contact,
 * with one explicit tap per contact, that they were added. This is NOT an emergency message and is marked as
 * such. A send attempt is recorded honestly; delivery to the recipient is never claimed. Carriers may charge
 * for SMS. The wording is a single constant so the owner-approved template lands in exactly one place.
 */
object IntroSms {

    /** The message template. [name] is the user's own entered name, never auto-filled. */
    fun text(name: String): String =
        "Namaste! ${name.trim()} ne aapko Netra Human Safety app mein apna emergency contact banaya hai. " +
        "Kabhi unhe emergency ho to aapko unki location wala SMS aa sakta hai. " +
        "Yeh sirf introduction message hai, koi emergency NAHI hai. " +
        "Agar aap apna number hatwana chahte hain to unhe bata dein. - Netra Human Safety"

    /** One explicit attempt. Returns true when the SMS was handed to the system without an error. */
    fun send(c: Context, number: String, name: String): Boolean = try {
        @Suppress("DEPRECATION")
        SmsManager.getDefault().sendTextMessage(number, null, text(name), null, null)
        OnboardingStore.setIntroAction(c, number, OnboardingStore.INTRO_SENT)
        true
    } catch (_: Exception) { false }
}
