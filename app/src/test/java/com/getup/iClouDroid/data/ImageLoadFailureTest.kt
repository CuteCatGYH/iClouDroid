package com.getup.iClouDroid.data

import coil.network.HttpException
import com.getup.iClouDroid.data.coil.ImageLoadFailure
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * P2-011: only a dead signature (HTTP 403/410) is worth re-minting. A decoder failure must never
 * be classified as a URL problem, otherwise the detail viewer fires pointless `/records/lookup`
 * calls that the dedup window then has to swallow.
 */
class ImageLoadFailureTest {

    @Test
    fun `expired signatures are treated as url issues`() {
        assertTrue(ImageLoadFailure.isExpiredUrl(httpError(410)))
        assertTrue(ImageLoadFailure.isExpiredUrl(httpError(403)))
    }

    @Test
    fun `other http statuses are not url issues`() {
        assertFalse(ImageLoadFailure.isExpiredUrl(httpError(404)))
        assertFalse(ImageLoadFailure.isExpiredUrl(httpError(500)))
        assertFalse(ImageLoadFailure.isExpiredUrl(httpError(200)))
    }

    @Test
    fun `non http failures are not url issues`() {
        // A BitmapFactory / ImageDecoder failure surfaces as a plain exception with no response.
        assertFalse(ImageLoadFailure.isExpiredUrl(IOException("BitmapFactory returned a null bitmap")))
        assertFalse(ImageLoadFailure.isExpiredUrl(RuntimeException("decode failed")))
        assertFalse(ImageLoadFailure.isExpiredUrl(null))
    }

    @Test
    fun `http code is only reported for http failures`() {
        assertEquals(410, ImageLoadFailure.httpCodeOf(httpError(410)))
        assertEquals(403, ImageLoadFailure.httpCodeOf(httpError(403)))
        assertNull(ImageLoadFailure.httpCodeOf(IOException("decode failed")))
        assertNull(ImageLoadFailure.httpCodeOf(null))
    }

    /** Wraps [code] in a Coil [HttpException] the way its real fetch path would. */
    private fun httpError(code: Int): HttpException {
        val response = Response.Builder()
            .request(Request.Builder().url("https://cvws.example.com/asset").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("status $code")
            .body("".toResponseBody("application/octet-stream".toMediaType()))
            .build()
        return HttpException(response)
    }
}
