package com.qtekfun.ultimatealarm.feature.backup

import java.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class WebDavTest {
    private val server = MockWebServer()
    private lateinit var dav: WebDav

    @Before
    fun start() {
        server.start()
        dav = WebDav(server.url("/remote.php/dav/files/zoe/"), "zoe", "app-pass", requireHttps = false)
    }

    @After
    fun stop() = server.close()

    private val listing = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:">
          <d:response><d:href>/remote.php/dav/files/zoe/UltimateAlarm/</d:href>
            <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
          <d:response><d:href>/remote.php/dav/files/zoe/UltimateAlarm/ultimatealarm-20261016-080000.zip</d:href>
            <d:propstat><d:prop><d:resourcetype/><d:getcontentlength>1234</d:getcontentlength>
            <d:getlastmodified>Fri, 16 Oct 2026 06:00:00 GMT</d:getlastmodified></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
          <d:response><d:href>/remote.php/dav/files/zoe/UltimateAlarm/con%20espacio.zip</d:href>
            <d:propstat><d:prop><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun `lists files of a folder, authenticated`() = runTest {
        server.enqueue(MockResponse.Builder().code(207).body(listing).build())
        val files = dav.list("UltimateAlarm")
        assertEquals(listOf("ultimatealarm-20261016-080000.zip", "con espacio.zip"), files.map { it.name })
        assertEquals(1234, files[0].size)
        assertEquals(Instant.parse("2026-10-16T06:00:00Z"), files[0].modified)
        assertNull(files[1].modified)
        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.headers["Depth"])
        assertEquals("/remote.php/dav/files/zoe/UltimateAlarm/", request.url.encodedPath)
        assertEquals(okhttp3.Credentials.basic("zoe", "app-pass"), request.headers["Authorization"])
    }

    @Test
    fun `missing folder is empty, put get delete and mkcol`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertEquals(emptyList<DavFile>(), dav.list("nope"))
        server.enqueue(MockResponse.Builder().code(201).build())
        dav.put("UltimateAlarm/a.zip", byteArrayOf(1, 2))
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(byteArrayOf(1, 2))).build())
        assertArrayEquals(byteArrayOf(1, 2), dav.get("UltimateAlarm/a.zip"))
        server.enqueue(MockResponse.Builder().code(204).build())
        dav.delete("UltimateAlarm/a.zip")
        server.enqueue(MockResponse.Builder().code(201).build())
        server.enqueue(MockResponse.Builder().code(405).build())
        dav.mkcol("Backups/UltimateAlarm")
        server.takeRequest()
        assertEquals("PUT", server.takeRequest().method)
        assertEquals("GET", server.takeRequest().method)
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals("/remote.php/dav/files/zoe/Backups/", server.takeRequest().url.encodedPath)
        assertEquals("/remote.php/dav/files/zoe/Backups/UltimateAlarm/", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `auth errors, doctype and plain http are refused`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).build())
        assertEquals(DavError.AUTH, assertThrows(DavException::class.java) { kotlinx.coroutines.runBlocking { dav.get("x") } }.error)
        server.enqueue(MockResponse.Builder().code(207).body("<!DOCTYPE x [<!ENTITY a \"b\">]><x/>").build())
        assertEquals(DavError.INVALID, assertThrows(DavException::class.java) { kotlinx.coroutines.runBlocking { dav.list("x") } }.error)
        assertEquals(DavError.INSECURE_URL, assertThrows(DavException::class.java) { WebDav(server.url("/"), "a", "b") }.error)
    }

    @Test
    fun `nextcloud root from what the user types`() {
        assertEquals("https://cloud.example.org/remote.php/dav/files/zoe/", WebDav.nextcloudRoot("cloud.example.org", "zoe").toString())
        assertEquals("https://cloud.example.org/nc/remote.php/dav/files/zoe/", WebDav.nextcloudRoot("https://cloud.example.org/nc/", "zoe").toString())
        assertEquals("https://x.org/remote.php/dav/files/zoe/", WebDav.nextcloudRoot("https://x.org/remote.php/dav/files/zoe/", "other").toString())
        assertNull(WebDav.nextcloudRoot("not a url", "zoe"))
    }
}
