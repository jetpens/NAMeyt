
/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/mirai-login-solver-sakura/blob/main/LICENSE
 */

package com.kasukusakura.mlss.resolver

import com.google.gson.JsonObject
import com.google.zxing.client.j2se.MatrixToImageConfig
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.kasukusakura.mlss.ProjMetadata
import com.kasukusakura.mlss.slovbroadcast.SakuraTransmitDaemon
import com.kasukusakura.mlss.useByteBuf
import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.network.CustomLoginFailedException
import net.mamoe.mirai.utils.*
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel

@Suppress("MemberVisibilityCanBePrivate")
class SakuraLoginSolver(
    private val daemon: SakuraTransmitDaemon,
) : LoginSolver() {
    override val isSliderCaptchaSupported: Boolean get() = true

    override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String? {
        val img = runInterruptible(Dispatchers.IO) {
            val usingCache = ImageIO.getUseCache()
            try {
                ImageIO.setUseCache(false)
                ImageIO.read(ByteArrayInputStream(data))
            } finally {
                if (usingCache) ImageIO.setUseCache(true)
            }
        }
        return onSolvePicCaptcha(bot.id, img)
    }

    override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String? {
        return onSolveSliderCaptcha(bot.id, url, bot.logger)
    }

    @Deprecated(
        "Please use onSolveDeviceVerification instead",
        replaceWith = ReplaceWith("onSolveDeviceVerification(bot, url, null)"),
        level = DeprecationLevel.WARNING
    )
    override suspend fun onSolveUnsafeDeviceLoginVerify(bot: Bot, url: String): String? {
        error("Sorry, but mirai-login-solver-sakura no longer support the legacy version of mirai-core; Please use 2.13.0 or above")
    }

    override suspend fun onSolveDeviceVerification(
        bot: Bot,
        requests: DeviceVerificationRequests
    ): DeviceVerificationResult {
        return onDeviceVerification(bot.id, requests)
    }

    internal suspend fun onSolvePicCaptcha(botid: Long, img: BufferedImage): String? {
        return openWindowCommon(JFrameWithIco(), isTopLevel = true, title = "PicCaptcha($botid)") {
            appendFillX(JLabel(ImageIcon(img)))
            optionPane.options = arrayOf(
                BTN_OK.attachToTextField(filledTextField("", "")).asInitialValue(),
                BTN_CANCEL.withValue(WindowResult.Cancelled),
            )
        }.valueAsString
    }

    internal suspend fun onSolveSliderCaptcha(botid: Long, captchaUrl: String, logger: MiraiLogger): String? {
        val rspx = openWindowCommon(JFrameWithIco(), isTopLevel = true, title = "SliderCaptcha($botid)") {
            filledTextField("url", captchaUrl)
            optionPane.options = arrayOf(
                JButton("Use TxCaptchaHelper").withActionBlocking {
                    val respx = openWindowCommon(
                        window = parentWindow,
                        isTopLevel = false,
                        blockingDisplay = true,
                        title = "SliderCaptcha($botid) - TxCaptchaHelper",
                    ) {
                        val statusBar = filledTextField("", "")
                        optionPane.options = arrayOf(
                            JButton("Open Website").withAction {
                                openBrowserOrAlert("https://github.com/mzdluo123/TxCaptchaHelper")
                            },
                            BTN_CANCEL.withValue(WindowResult.Cancelled),
                        )

                        val helper = object : MZDTxCaptchaHelper() {
                            override fun onComplete(ticket: String) {
                                response.complete(WindowResult.Confirmed(ticket))
                            }

                            override fun updateDisplay(msg: String) {
                                statusBar.text = msg
                            }
                        }
                        helper.start(subCoroutineScope, captchaUrl)
                    }
                    if (respx.cancelled) return@withActionBlocking

                    response.complete(respx)
                },
                JButton("Use SakuraCaptchaHelper").withActionBlocking {
                    val rspx = openWindowCommon(
                        window = parentWindow,
                        isTopLevel = false,