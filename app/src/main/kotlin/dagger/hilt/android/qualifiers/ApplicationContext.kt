package dagger.hilt.android.qualifiers

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(
	AnnotationTarget.FUNCTION,
	AnnotationTarget.PROPERTY_GETTER,
	AnnotationTarget.PROPERTY_SETTER,
	AnnotationTarget.VALUE_PARAMETER,
	AnnotationTarget.FIELD,
)
annotation class ApplicationContext
