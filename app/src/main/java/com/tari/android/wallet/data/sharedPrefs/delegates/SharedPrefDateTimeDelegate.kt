package com.tari.android.wallet.data.sharedPrefs.delegates

import android.content.SharedPreferences
import com.tari.android.wallet.data.repository.CommonRepository
import org.joda.time.DateTime
import kotlin.reflect.KProperty

class SharedPrefDateTimeDelegate(
    val prefs: SharedPreferences,
    val commonRepository: CommonRepository,
    val name: String,
    val defValue: DateTime? = null
) {
    init {
        commonRepository.updateNotifier.onNext(Unit)
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): DateTime? =
        prefs.getLong(name, defValue?.millis ?: -1L)
            .let { if (it == -1L) null else DateTime(it) }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: DateTime?) {
        prefs.edit().apply {
            if (value == null) remove(name)
            else putLong(name, value.millis)
        }.apply()
        commonRepository.updateNotifier.onNext(Unit)
    }
}