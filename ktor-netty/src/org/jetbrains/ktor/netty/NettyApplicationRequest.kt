package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.io.*
import java.util.*

class NettyApplicationRequest(override val application: Application,
                              val context: ChannelHandlerContext,
                              val request: HttpRequest) : ApplicationRequest {
    override val headers by lazy {
        request.headers().toMap({ it.key }, { it.value })
    }
    override val requestLine: HttpRequestLine by lazy {
        HttpRequestLine(request.method.name(), request.uri, request.protocolVersion.text())
    }

    override val parameters: Map<String, List<String>> by lazy {
        QueryStringDecoder(request.uri).parameters()
    }

    var async : Boolean = false
    fun continueAsync() {
        this.async = true
    }

    var response: Response? = null
    override fun respond(handle: ApplicationResponse.() -> ApplicationRequestStatus): ApplicationRequestStatus {
        val currentResponse = response
        if (currentResponse != null)
            throw IllegalStateException("There should be only one response for a single request. Make sure you haven't called response more than once.")
        response = Response(context)
        return response!!.handle()
    }

    inner class Response(val context: ChannelHandlerContext) : ApplicationResponse {
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

        override fun header(name: String, value: String): ApplicationResponse {
            response.headers().set(name, value)
            return this
        }

        override fun header(name: String, value: Int): ApplicationResponse {
            response.headers().set(name, value)
            return this
        }

        override fun status(code: Int): ApplicationResponse {
            response.setStatus(HttpResponseStatus(code, "$code"))
            return this
        }

        override fun content(text: String, encoding: String): ApplicationResponse {
            return content(text.toByteArray(encoding))
        }

        override fun content(bytes: ByteArray): ApplicationResponse {
            response.content().writeBytes(bytes)
            return this
        }

        override fun contentStream(streamer: Writer.() -> Unit): ApplicationResponse {
            val writer = StringWriter()
            writer.streamer()
            return content(writer.toString())
        }

        override fun send(): ApplicationRequestStatus {
            context.write(response)
            context.flush()
            if (async)
                context.close()
            return ApplicationRequestStatus.Handled
        }

        override fun sendRedirect(url: String) = throw UnsupportedOperationException()
    }

}