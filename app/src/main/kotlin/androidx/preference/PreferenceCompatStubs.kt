package androidx.preference

import android.content.Context

open class Preference(val context: Context) {
	open var key: String? = null
	open var title: CharSequence? = null
	open var summary: CharSequence? = null
	open fun setDefaultValue(defaultValue: Any?) {}
}

open class ListPreference(context: Context) : Preference(context) {
	var entries: Array<CharSequence>? = null
	var entryValues: Array<CharSequence>? = null
	var value: String? = null
}

open class EditTextPreference(context: Context) : Preference(context) {
	var text: String? = null
}

open class SwitchPreferenceCompat(context: Context) : Preference(context) {
	var isChecked: Boolean = false
}

open class CheckBoxPreference(context: Context) : Preference(context) {
	var isChecked: Boolean = false
}

open class MultiSelectListPreference(context: Context) : Preference(context) {
	var entries: Array<CharSequence>? = null
	var entryValues: Array<CharSequence>? = null
	var values: Set<String> = emptySet()
}

open class PreferenceScreen(context: Context) : Preference(context) {
	private val children = mutableListOf<Preference>()
	fun addPreference(preference: Preference): Boolean = children.add(preference)
}
