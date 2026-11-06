package ec.espol.inspeccionphotos.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.LifecycleOwner

internal tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

internal fun Context.findLifecycleOwner(): LifecycleOwner? = findHostActivity() as? LifecycleOwner
