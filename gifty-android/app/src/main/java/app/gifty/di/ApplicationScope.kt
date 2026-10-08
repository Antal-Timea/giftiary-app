package app.gifty.di

import javax.inject.Qualifier

/** A coroutine scope that lives as long as the process, for work that must not depend on a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
