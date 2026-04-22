package app.folga.auth

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

/**
 * Holds a weak reference to the currently-resumed `Activity` so
 * [CredentialManagerGoogleSignInProvider] can present the Credential Manager
 * bottom sheet. Populated from `MainActivity.onCreate` and cleared in
 * `onDestroy` to avoid leaking the Activity after a config change.
 *
 * Weak reference on purpose: even if `onDestroy` is missed (process death,
 * edge cases), the JVM is still free to reclaim the Activity and we just
 * return `null`, which the caller translates into a user-visible error.
 */
class ActivityContextHolder {
    private var ref: WeakReference<Activity>? = null

    fun set(activity: Activity) {
        ref = WeakReference(activity)
    }

    fun clear(activity: Activity) {
        // Only clear if the stored activity is the one being destroyed, to
        // avoid racing with a new activity that was just set.
        if (ref?.get() === activity) {
            ref = null
        }
    }

    fun current(): Context? = ref?.get()
}
