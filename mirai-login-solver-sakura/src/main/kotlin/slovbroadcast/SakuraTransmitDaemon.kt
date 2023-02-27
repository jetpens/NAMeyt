
/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/mirai-login-solver-sakura/blob/main/LICENSE
 */

package com.kasukusakura.mlss.slovbroadcast

import com.google.gson.JsonElement
import com.google.gson.internal.Streams
import com.google.gson.stream.JsonWriter
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.handler.codec.socksx.v4.*
import io.netty.handler.codec.socksx.v5.*
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import io.netty.resolver.DefaultAddressResolverGroup
import io.netty.util.AttributeKey
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import net.mamoe.mirai.utils.*
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.random.Random

class SakuraTransmitDaemon(
    @JvmField val eventLoopGroup: EventLoopGroup,
    private val serverChannelType: Class<out ServerChannel>,
    private val clientChannelType: Class<out Channel>,
    private val random: Random,
    private val logger: MiraiLogger,
) {
    @JvmField
    var isSocksTunnelEnabled: Boolean = !DefaultSettings.noTunnel

    @JvmField
    var tunnelLimited: Boolean = DefaultSettings.tunnelLimited

    private val requests = ConcurrentHashMap<String, ResolveRequest>()
    private lateinit var serverChannel: ServerChannel

    val serverPort: Int get() = (serverChannel.localAddress() as InetSocketAddress).port

    fun bootServer(inetPort: Int = DefaultSettings.serverPort) {
        val rspx = ServerBootstrap()
            .channel(serverChannelType)
            .group(eventLoopGroup, eventLoopGroup)
            .childHandler(object : ChannelInitializer<Channel>() {
                override fun initChannel(ch: Channel) {
                    ch.attr(DAEMON).set(this@SakuraTransmitDaemon)

                    ch.pipeline()
                        .addLast("r-timeout", ReadTimeoutHandler(2, TimeUnit.MINUTES))
                        .addLast("w-timeout", WriteTimeoutHandler(2, TimeUnit.MINUTES))
                        .addFirst("exception-caught", object : ChannelInboundHandlerAdapter() {
                            @Suppress("OVERRIDE_DEPRECATION")
                            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
                                logger.error({ "${ctx.channel()} [exceptionCaught]" }, cause)

                                ctx.fireExceptionCaught(cause)
                            }
                        })


                    if (isSocksTunnelEnabled) {
                        logger.debug { "$ch [initial] Waiting first packet" }

                        ch.pipeline().addLast("1st-prepare-handler", FstPrepareHandler())
                    } else {
                        ch.pipeline()
                            .addLast("http-req-decoder", HttpRequestDecoder())
                            .addLast("http-rsp-encoder", HttpResponseEncoder())
                            .addLast("http-handler", HttpConnectHandler())
                    }
                }
            })
            .bind(inetPort)
            .sync()

        if (rspx.isSuccess) {
            serverChannel = rspx.channel() as ServerChannel
        }
    }

    fun shutdown() {
        if (::serverChannel.isInitialized) {
            serverChannel.close().await()
        }
    }

    inner class ResolveRequest {
        fun fireCompleted() {
            if (::continuation.isInitialized) {
                val data = msgData
                msgData = Unpooled.EMPTY_BUFFER
                continuation.resumeWith(Result.success(data))
            } else {
                msgData.release()
                msgData = Unpooled.EMPTY_BUFFER
            }
        }

        var additionalHeaders: HttpHeaders = EmptyHttpHeaders.INSTANCE

        internal lateinit var requestId: String
        internal lateinit var msgData: ByteBuf

        private lateinit var continuation: CancellableContinuation<ByteBuf>

        suspend fun awaitResponse(): ByteBuf = suspendCancellableCoroutine { cont ->
            continuation = cont
            cont.invokeOnCancellation {
                if (requests.remove(requestId, this@ResolveRequest)) {
                    if (::msgData.isInitialized) {
                        msgData.release()
                        msgData = Unpooled.EMPTY_BUFFER
                    }
                }
            }
        }

        fun renderQR(): BitMatrix = Companion.renderQR(serverPort, requestId)
    }

    fun newRawRequest(
        initialReqId: String? = null,
        additionalHeaders: HttpHeaders = EmptyHttpHeaders.INSTANCE,
        dataBuilder: (ByteBufAllocator) -> ByteBuf
    ): ResolveRequest {
        val request = ResolveRequest()
        var id: String = initialReqId ?: generateNewRequestId()
        val msgData = dataBuilder(serverChannel.alloc())

        do {
            if (requests.putIfAbsent(id, request) == null) break

            id = generateNewRequestId()
        } while (true)

        request.requestId = id
        request.additionalHeaders = additionalHeaders
        request.msgData = msgData
        return request
    }

    fun newRequest(msg: JsonElement, initialReqId: String? = null): ResolveRequest {
        val request = ResolveRequest()
        var id: String = initialReqId ?: generateNewRequestId()

        do {
            if (requests.putIfAbsent(id, request) == null) break

            id = generateNewRequestId()
        } while (true)

        request.requestId = id

        val reqMsgData = serverChannel.alloc().buffer(256)

        JsonWriter(OutputStreamWriter(ByteBufOutputStream(reqMsgData))).use { jwriter ->
            jwriter.beginObject()
                .name("reqid").value(id)
                .name("rspuri").value("/request/complete/$id")
                .name("create-time").value(System.currentTimeMillis())
                .name("data")

            Streams.write(msg, jwriter)

            if (isSocksTunnelEnabled) {
                jwriter.name("tunnel").value("socks://<serverip>:$serverPort")
            }

            jwriter.endObject()
        }

        request.msgData = reqMsgData

        return request
    }

    private fun processHttpMsg(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is HttpRequest) {
            logger.debug { "${ctx.channel()} [http   ] New http request: ${msgFormat(msg)}" }
            logger.verbose { "${ctx.channel()} [http   ] $msg" }


            val uri = msg.uri()

            if (uri == "/") {
                ctx.channel().writeAndFlush(
                    DefaultFullHttpResponse(
                        msg.protocolVersion(), HttpResponseStatus.OK,
                        Unpooled.EMPTY_BUFFER,
                        serverHttpRspHeaders().add("Content-Length", 0),
                        EmptyHttpHeaders.INSTANCE,
                    )
                )
                return
            }

            if (msg.method() == HttpMethod.GET && uri.startsWith("/request/request/")) {
                requests[uri.substring(17)]?.let { request ->
                    ctx.channel().writeAndFlush(
                        DefaultFullHttpResponse(
                            msg.protocolVersion(), HttpResponseStatus.OK,
                            request.msgData.retainedDuplicate(),
                            serverHttpRspHeaders()
                                .add("Content-Length", request.msgData.readableBytes())
                                .also { headers ->
                                    val additionalHeaders = request.additionalHeaders
                                    if (!additionalHeaders.contains("Content-Type")) {
                                        headers.add("Content-Type", "application/json")
                                            .add("Content-Encoding", "UTF-8")
                                    }
                                    headers.add(additionalHeaders)
                                },
                            EmptyHttpHeaders.INSTANCE,
                        )
                    )
                    return
                }
            }

            if (msg.method() == HttpMethod.POST && uri.startsWith("/request/complete/")) {
                requests[uri.substring(18)]?.let procx@{ request ->
                    val contentLength = msg.headers().get("Content-Length", "0").toIntOrNull() ?: return@procx
                    if (contentLength > 40960) {
                        ctx.channel().writeAndFlush(
                            DefaultFullHttpResponse(
                                msg.protocolVersion(), HttpResponseStatus.FORBIDDEN,
                                Unpooled.EMPTY_BUFFER,
                                serverHttpRspHeaders().add("Content-Length", 0),
                                EmptyHttpHeaders.INSTANCE,
                            )
                        )
                        return@procx
                    }
                    // Request not found
                    if (!requests.remove(request.requestId, request)) return@procx

                    val response = request.msgData
                    response.clear()

                    val httpReq = msg

                    ctx.channel().pipeline()
                        .addBefore("http-handler", "post-msg-receiver", object : ChannelInboundHandlerAdapter() {
                            override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                                if (msg is HttpContent) {
                                    response.writeBytes(msg.content())
                                    msg.release()

                                    if (msg is LastHttpContent) {
                                        // completed
                                        logger.debug {
                                            "${ctx.channel()} [http   ] [${request.requestId}] post response: ${
                                                response.toString(
                                                    Charsets.UTF_8
                                                )
                                            }"
                                        }

                                        ctx.pipeline().remove(this)

                                        ctx.channel().writeAndFlush(
                                            DefaultFullHttpResponse(
                                                httpReq.protocolVersion(), HttpResponseStatus.OK,
                                                Unpooled.EMPTY_BUFFER,
                                                serverHttpRspHeaders().add("Content-Length", 0),
                                                EmptyHttpHeaders.INSTANCE,
                                            )
                                        )

                                        request.fireCompleted()
                                    }
                                } else {
                                    ctx.fireChannelRead(msg)
                                }
                            }
                        })

                    return
                }
            }

            ctx.channel().writeAndFlush(
                DefaultFullHttpResponse(
                    msg.protocolVersion(), HttpResponseStatus.NOT_FOUND,
                    Unpooled.EMPTY_BUFFER,
                    serverHttpRspHeaders().add("Content-Length", 0),
                    EmptyHttpHeaders.INSTANCE,
                )
            )
        } else {
            ctx.fireChannelRead(msg)
        }
    }