package eu.kanade.tachiyomi.core.security

import tachiyomi.core.common.preference.PreferenceStore

class PrivacyPreferences(
    private val preferenceStore: PreferenceStore,
) {
    // REMOVE:
    // fun crashlytics() = preferenceStore.getBoolean("crashlytics", true)
    // fun analytics() = preferenceStore.getBoolean("analytics", true)
}
