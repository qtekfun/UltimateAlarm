package com.qtekfun.ultimatealarm.feature.holidays

import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkErrorsTest {
    @Test
    fun `a host that does not resolve means no internet for the app`() {
        val stored = "Nager.Date ES 2026: UnknownHostException: Unable to resolve host \"date.nager.at\": No address associated with hostname"
        assertTrue(NetworkErrors.isNoInternet(stored))
        assertTrue(NetworkErrors.isNoInternet(UnknownHostException("date.nager.at")))
    }

    @Test
    fun `server and parsing errors are not network errors`() {
        assertFalse(NetworkErrors.isNoInternet("Nager.Date ES 2026: IOException: HTTP 500"))
        assertFalse(NetworkErrors.isNoInternet("ICS: no events"))
        assertFalse(NetworkErrors.isNoInternet(IOException("HTTP 404")))
        assertFalse(NetworkErrors.isNoInternet(null as String?))
    }
}
