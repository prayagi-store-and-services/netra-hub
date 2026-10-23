package com.example.sos

import android.content.Context

/**
 * Mandatory first-run setup (v1.4.32). The app stays behind the onboarding flow until the required safety
 * profile exists: name, age and at least 3 emergency contacts. This applies to fresh installs AND to users
 * upgrading from older versions - there is no skip for existing installs. Everything already saved is kept
 * and prefilled; finishing a complete profile is a quick review. Optional permissions and any SMS/location
 * disclosure stay separate, explicit choices and are never auto-approved by finishing onboarding.
 */
object OnboardingStore {
    private const val FILE = "onboarding_prefs"
    private const val KEY_DONE = "done"
    private const val KEY_INTRO = "intro_sms"   // per-contact intro-SMS action: number=SENT or LATER
    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun markDone(c: Context) = p(c).edit().putBoolean(KEY_DONE, true).apply()
    fun done(c: Context) = p(c).getBoolean(KEY_DONE, false)

    const val INTRO_SENT = "SENT"
    const val INTRO_LATER = "LATER"
    /** Records what the user explicitly did on the intro-SMS step for one contact. An attempted send is SENT; delivery is never claimed. */
    fun setIntroAction(c: Context, number: String, action: String) =
        p(c).edit().putString(KEY_INTRO + ":" + number, action).apply()
    fun introAction(c: Context, number: String): String? = p(c).getString(KEY_INTRO + ":" + number, null)

    /** Pure, unit-testable rule: what "required setup complete" means. */
    fun requirementsMet(name: String, ageYears: Int, contactCount: Int): Boolean =
        name.isNotBlank() && ageYears in 1..120 && contactCount >= 3

    fun requirementsMet(c: Context): Boolean =
        requirementsMet(SosStore.name(c), SosStore.ageYears(c), SosStore.contacts(c).size)

    /** Gate: onboarding shows until it was finished AND the required profile still exists. */
    fun needsOnboarding(c: Context): Boolean = !done(c) || !requirementsMet(c)
}
