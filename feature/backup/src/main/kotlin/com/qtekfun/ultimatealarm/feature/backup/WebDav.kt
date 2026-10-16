package com.qtekfun.ultimatealarm.feature.backup

import java.io.IOException
import java.io.StringReader
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.xml.parsers.SAXParserFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.Credentials as BasicCredentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/** Why talking to Nextcloud failed, for a message the user can act on. */
enum class DavError { INSECURE_URL, AUTH, NETWORK, TLS, SERVER, NOT_WEBDAV, TOO_LARGE, INVALID }

class DavException(val error: DavError, message: String, cause: Throwable? = null) : Exception(message, cause)

/** One file of a folder listing. */
data class DavFile(val name: String, val size: Long, val modified: Instant?)

/**
 * WebDAV over OkHttp with app-password Basic authentication, the same client design as UltimatePhone (02-diseno §9):
 * HTTPS only, redirects followed here keeping the method and never sending the password to another host, bounded
 * bodies, hardened XML. Operations: folder listing, binary GET/PUT, DELETE, MKCOL.
 */
class WebDav(
    private val root: HttpUrl,
    username: String,
    password: String,
    private val client: OkHttpClient = defaultClient(),
    private val requireHttps: Boolean = true
) {
    private val authorization = BasicCredentials.basic(username, password, Charsets.UTF_8)

    init {
        if (requireHttps && root.scheme != "https") throw DavException(DavError.INSECURE_URL, "Only https is allowed")
    }

    /** Files directly inside [folder] (not sub-folders). An absent folder is an empty list. */
    suspend fun list(folder: String): List<DavFile> {
        val body = PROPFIND_BODY.toRequestBody(XML)
        return execute("PROPFIND", urlOf(folder, trailingSlash = true), body, mapOf("Depth" to "1")).use { response ->
            when (response.code) {
                207 -> MultiStatus.parse(readText(response)).filter { !it.isCollection && it.status in 200..299 }.map {
                    DavFile(it.href.trimEnd('/').substringAfterLast('/').let(::decode), it.size, it.modified)
                }
                404 -> emptyList()
                405, 501 -> throw DavException(DavError.NOT_WEBDAV, "HTTP ${response.code}")
                else -> throw failure(response)
            }
        }
    }

    suspend fun mkcol(folder: String) {
        // Creates every level: MKCOL only makes the last one.
        val parts = folder.split('/').filter { it.isNotEmpty() }
        for (i in parts.indices) {
            execute("MKCOL", urlOf(parts.take(i + 1).joinToString("/"), trailingSlash = true), null).use { response ->
                if (response.code !in setOf(201, 405)) throw failure(response)
            }
        }
    }

    suspend fun get(path: String): ByteArray = execute("GET", urlOf(path), null).use { response ->
        if (response.code != 200) throw failure(response)
        readBytes(response)
    }

    suspend fun put(path: String, bytes: ByteArray) {
        execute("PUT", urlOf(path), bytes.toRequestBody(ZIP)).use { response ->
            if (response.code !in setOf(200, 201, 204)) throw failure(response)
        }
    }

    suspend fun delete(path: String) {
        execute("DELETE", urlOf(path), null).use { response ->
            if (response.code !in setOf(200, 204, 404)) throw failure(response)
        }
    }

    private fun urlOf(path: String, trailingSlash: Boolean = false): HttpUrl {
        val builder = root.newBuilder()
        path.split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        if (trailingSlash) builder.addPathSegment("")
        return builder.build()
    }

    private suspend fun execute(method: String, url: HttpUrl, body: RequestBody?, headers: Map<String, String> = emptyMap()): Response =
        withContext(Dispatchers.IO) {
            var target = url
            var hops = 0
            while (true) {
                if (requireHttps && target.scheme != "https") throw DavException(DavError.INSECURE_URL, "Redirected to a non-https address")
                val builder = Request.Builder().url(target).method(method, body)
                headers.forEach { (name, value) -> builder.header(name, value) }
                // The password only goes to the server the user typed.
                if (target.host == root.host && target.port == root.port) builder.header("Authorization", authorization)
                val response = try {
                    runInterruptible { client.newCall(builder.build()).execute() }
                } catch (e: IOException) {
                    throw translate(e)
                }
                val location = if (response.code in REDIRECTS) response.header("Location") else null
                if (location == null) return@withContext response
                val next = response.request.url.resolve(location)
                response.close()
                if (next == null || ++hops > MAX_REDIRECTS) throw DavException(DavError.NETWORK, "Too many or invalid redirects")
                target = next
            }
            @Suppress("UNREACHABLE_CODE") // The loop only leaves through return or throw.
            error("unreachable")
        }

    private fun readBytes(response: Response): ByteArray {
        val source = response.body.source()
        val buffer = okio.Buffer()
        var total = 0L
        while (true) {
            val read = source.read(buffer, READ_CHUNK)
            if (read < 0) break
            total += read
            if (total > MAX_BODY_BYTES) throw DavException(DavError.TOO_LARGE, "The remote file is too large")
        }
        return buffer.readByteArray()
    }

    private fun readText(response: Response) = String(readBytes(response), Charsets.UTF_8)

    private fun failure(response: Response): DavException = when (val code = response.code) {
        401, 403 -> DavException(DavError.AUTH, "HTTP $code")
        408, 423, 429, in 500..599 -> DavException(DavError.SERVER, "HTTP $code")
        else -> DavException(DavError.SERVER, "HTTP $code")
    }

    private fun translate(e: IOException): DavException = when {
        e is SSLException || e.cause is CertificateException -> DavException(DavError.TLS, "TLS: ${e.javaClass.simpleName}", e)
        e is SocketTimeoutException -> DavException(DavError.NETWORK, "Timed out", e)
        else -> DavException(DavError.NETWORK, e.javaClass.simpleName, e)
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val MAX_BODY_BYTES = 64L * 1024 * 1024
        private const val READ_CHUNK = 64L * 1024
        private val REDIRECTS = setOf(301, 302, 307, 308)
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val ZIP = "application/zip".toMediaType()
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>""" +
            """<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()

        /** The WebDAV root of a Nextcloud account: `https://host/remote.php/dav/files/<user>/`. */
        fun nextcloudRoot(server: String, username: String): HttpUrl? {
            val base = server.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }.toHttpUrlOrNull() ?: return null
            if (base.encodedPath.contains("/remote.php/dav/files/")) return base
            return base.newBuilder().addPathSegments("remote.php/dav/files").addPathSegment(username).addPathSegment("").build()
        }

        private fun decode(segment: String) = java.net.URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8")
    }
}

/** One `<d:response>` of a PROPFIND answer. */
internal data class DavResource(val href: String, val isCollection: Boolean, val size: Long, val modified: Instant?, val status: Int)

/**
 * Reads a WebDAV `207 Multi-Status` body, as UltimatePhone does: a document with a DOCTYPE or an entity declaration is
 * refused before parsing, so external entities and expansion attacks are impossible.
 */
internal object MultiStatus {
    private const val MAX_CHARS = 4 * 1024 * 1024

    fun parse(xml: String): List<DavResource> {
        if (xml.length > MAX_CHARS) throw DavException(DavError.TOO_LARGE, "Multi-Status answer too large")
        if (xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) {
            throw DavException(DavError.INVALID, "Multi-Status answer has a DOCTYPE")
        }
        val handler = Handler()
        try {
            val factory = SAXParserFactory.newInstance()
            factory.isNamespaceAware = true
            factory.isValidating = false
            val reader = factory.newSAXParser().xmlReader
            reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(StringReader("")) }
            reader.contentHandler = handler
            reader.parse(InputSource(StringReader(xml)))
        } catch (e: SAXException) {
            throw DavException(DavError.INVALID, "Multi-Status answer is not valid XML", e)
        }
        return handler.resources
    }

    private class Handler : DefaultHandler() {
        val resources = ArrayList<DavResource>()
        private val text = StringBuilder()
        private var href = ""
        private var collection = false
        private var size = 0L
        private var modified: Instant? = null
        private var status = 0
        private var inResponse = false

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            text.setLength(0)
            when (localName) {
                "response" -> {
                    inResponse = true
                    href = ""
                    collection = false
                    size = 0
                    modified = null
                    status = 0
                }
                "collection" -> if (inResponse) collection = true
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val value = text.toString().trim()
            when (localName) {
                "href" -> if (inResponse && href.isEmpty()) href = value
                "getcontentlength" -> if (inResponse) size = value.toLongOrNull() ?: 0
                "getlastmodified" -> if (inResponse) {
                    modified =
                        runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
                }
                "status" -> if (inResponse && status == 0) status = value.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                "response" -> {
                    resources += DavResource(href, collection, size, modified, status)
                    inResponse = false
                }
            }
            text.setLength(0)
        }
    }
}
