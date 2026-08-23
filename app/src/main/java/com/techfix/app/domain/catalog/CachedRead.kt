package com.techfix.app.domain.catalog

/**
 * A catalog read together with where it was served from, so a screen can say
 * "offline — showing your saved catalog" instead of silently presenting
 * cached data as live. Only the catalog needs this: it is the one thing the
 * app reads from a local cache.
 */
data class CachedRead<T>(
    val value: T,
    val fromCache: Boolean,
)
