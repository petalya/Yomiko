package mihon.core.firebase

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object FirebaseConfig {
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        context?.let { FirebaseAnalytics.getInstance(it).setAnalyticsCollectionEnabled(enabled) }
    }

    fun setCrashlyticsEnabled(enabled: Boolean) {
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
    }
}
