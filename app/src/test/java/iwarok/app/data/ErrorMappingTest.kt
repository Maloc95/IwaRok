package iwarok.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** User-facing error mapping: every message must be displayable Chinese text. */
class ErrorMappingTest {

    @Test
    fun `401 asks the user to log in`() {
        val error = IwaraErrors.mapStatus(401)
        assertEquals(401, error.statusCode)
        assertTrue(error.message.orEmpty().contains("登录"))
    }

    @Test
    fun `403 explains the risk-control rejection`() {
        val error = IwaraErrors.mapStatus(403)
        assertEquals(403, error.statusCode)
        assertTrue(
            error.message.orEmpty().contains("拒绝") || error.message.orEmpty().contains("风控")
        )
    }

    @Test
    fun `404 says the content does not exist`() {
        val error = IwaraErrors.mapStatus(404)
        assertEquals(404, error.statusCode)
        assertTrue(error.message.orEmpty().contains("不存在"))
    }

    @Test
    fun `429 asks the user to slow down`() {
        val error = IwaraErrors.mapStatus(429)
        assertEquals(429, error.statusCode)
        assertTrue(error.message.orEmpty().contains("频繁"))
    }

    @Test
    fun `5xx reports a server problem and keeps the status code`() {
        for (status in listOf(500, 502, 503)) {
            val error = IwaraErrors.mapStatus(status)
            assertEquals(status, error.statusCode)
            assertTrue(error.message.orEmpty().contains("服务器"))
        }
    }

    @Test
    fun `bad login is translated from the upstream error key`() {
        val error = IwaraErrors.mapStatus(400, """{"message":"errors.invalidLogin"}""")
        assertTrue(
            error.message.orEmpty().contains("邮箱") || error.message.orEmpty().contains("密码")
        )
    }

    @Test
    fun `generic bad request is localised`() {
        val error = IwaraErrors.mapStatus(400, """{"message":"errors.somethingUnknown"}""")
        assertEquals(400, error.statusCode)
        assertTrue(error.message.orEmpty().contains("400"))
    }

    @Test
    fun `network failures become a friendly network message`() {
        val unknownHost = IwaraErrors.mapNetwork(UnknownHostException("apiq.iwara.tv"))
        assertTrue(unknownHost.message.orEmpty().contains("网络"))
        val timeout = IwaraErrors.mapNetwork(SocketTimeoutException("timeout"))
        assertTrue(timeout.message.orEmpty().contains("网络"))
        val generic = IwaraErrors.mapNetwork(IOException("boom"))
        assertTrue(generic.message.orEmpty().contains("网络"))
    }

    @Test
    fun `html responses are detected as human verification challenges`() {
        assertTrue(
            IwaraErrors.looksLikeHtml(
                "text/html; charset=utf-8",
                "<!DOCTYPE html><html><body>Cloudflare challenge</body></html>",
            )
        )
        assertTrue(IwaraErrors.looksLikeHtml(null, "  <html>"))
        assertFalse(
            IwaraErrors.looksLikeHtml(
                "application/json",
                """{"count":0,"results":[]}""",
            )
        )
        assertFalse(IwaraErrors.looksLikeHtml("application/json", ""))
    }

    @Test
    fun `unknown server message keys are not leaked to the ui`() {
        assertNull(IwaraErrors.serverMessage("""{"message":"errors.unknownThing"}"""))
        assertNull(IwaraErrors.serverMessage("not json"))
        assertNull(IwaraErrors.serverMessage(null))
        assertEquals(
            "邮箱或密码错误，请检查后重试",
            IwaraErrors.serverMessage("""{"message":"errors.invalidLogin"}"""),
        )
    }

    @Test
    fun `all error messages are non empty and chinese friendly`() {
        val statuses = listOf(400, 401, 403, 404, 408, 429, 500, 503, 418)
        statuses.forEach { status ->
            val message = IwaraErrors.mapStatus(status).message
            assertFalse(message.isNullOrBlank())
            assertTrue(
                "status $status message should not be raw english",
                message.orEmpty().any { it.code > 127 },
            )
        }
    }
}
