package com.techfix.app.domain.auth

/**
 * Google sign-in couldn't run for a reason the user can act on (cancelled,
 * no account on the device, sign-in prompts disabled on the account). Its
 * message is written for display, so the UI can show it as-is instead of a
 * raw Play Services error string.
 */
class GoogleSignInUnavailableException(message: String) : Exception(message)
