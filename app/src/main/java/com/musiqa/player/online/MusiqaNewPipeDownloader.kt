package com.musiqa.player.online

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

class MusiqaNewPipeDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder().build()
) : Downloader() {

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: NewPipeRequest): NewPipeResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = Request.Builder()
            .method(httpMethod, dataToSend?.toRequestBody())
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        headers.forEach { (name, values) ->
            requestBuilder.removeHeader(name)
            values.forEach { value ->
                requestBuilder.addHeader(name, value)
            }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha challenge requested", url)
            }

            val bodyString = response.body?.string() ?: ""
            val latestUrl = response.request.url.toString()
            return NewPipeResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                bodyString,
                latestUrl
            )
        }
    }
}
