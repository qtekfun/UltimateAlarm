package com.qtekfun.ultimatealarm.feature.holidays

/**
 * Tells "the app could not reach the internet" apart from other refresh errors. Some manufacturers (ColorOS, for one)
 * let the user block mobile data or Wi-Fi per app, and sideloaded apps can start that way; Android then fails every
 * lookup with `UnknownHostException` even though the phone is online. Settings shows a hint with the way to fix it.
 */
object NetworkErrors {
    private val markers = listOf(
        "UnknownHostException",
        "ConnectException",
        "NoRouteToHostException",
        "SocketTimeoutException",
        "SocketException"
    )

    /** [error] is a stored refresh error ("Source: ExceptionName: message"). */
    fun isNoInternet(error: String?): Boolean = error != null && markers.any { it in error }

    fun isNoInternet(error: Throwable?): Boolean = error != null && isNoInternet(error.javaClass.simpleName)
}
