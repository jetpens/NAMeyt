
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