
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
import com.kasukusakura.mlss.slovbroadcast.SakuraTransmitDaemon
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultHttpHeaders
import net.mamoe.mirai.Bot
import net.mamoe.mirai.utils.DeviceVerificationRequests
import net.mamoe.mirai.utils.DeviceVerificationResult
import net.mamoe.mirai.utils.LoginSolver

abstract class CommonTerminalLoginSolver(
    private val daemon: SakuraTransmitDaemon,
) : LoginSolver() {

    protected abstract fun printMsg(msg: String)
    protected open val isCtrlCSupported: Boolean get() = false
    protected abstract suspend fun requestInput(hint: String): String?

    override val isSliderCaptchaSupported: Boolean get() = true

    override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String? {
        val req = daemon.newRawRequest(
            additionalHeaders = DefaultHttpHeaders()
                .add("Content-Type", "image/png")
        ) { Unpooled.wrappedBuffer(data) }

        printMsg("需要图像验证码")
        printMsg("请使用 任意浏览器 打开 http://<ip>:${daemon.serverPort}/request/request/${req.requestId} 来查看图片")

        try {
            val rsp = requestInput("PicCaptcha > ") ?: throw UnsafeDeviceLoginVerifyCancelledException(true)

            return rsp.takeIf { it.isNotBlank() }
        } finally {
            req.fireCompleted()
        }
    }

    override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String? {
        val req = daemon.newRequest(JsonObject().also { jo ->
            jo.addProperty("type", "slider")
            jo.addProperty("url", url)
        })

        printMsg("请使用 SakuraLoginSolver 打开 http://<ip>:${daemon.serverPort}/request/request/${req.requestId} 来完成验证")

        val rsp = req.awaitResponse()
        val rspText = rsp.toString(Charsets.UTF_8)
        rsp.release()
        return rspText
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override suspend fun onSolveUnsafeDeviceLoginVerify(bot: Bot, url: String): String? {
        error("Sorry, but mirai-login-solver-sakura no longer support the legacy version of mirai-core; Please use 2.13.0 or above")
    }

    protected open fun acquireLineReaderCompleter(): Any? = null
    protected open fun setLineReaderCompleting(words: Iterable<String>) {}
    protected open fun setLineReaderCompleter(completer: Any?) {}

    override suspend fun onSolveDeviceVerification(
        bot: Bot,
        requests: DeviceVerificationRequests
    ): DeviceVerificationResult {
        val originTabCompleter = acquireLineReaderCompleter()

        val resolveMethods = mutableMapOf<String, suspend () -> DeviceVerificationResult?>()

        requests.fallback?.let { fallback ->
            resolveMethods["legacy"] = process@{

                printMsg("需要设备锁验证")
                printMsg("请在 「手机QQ」!!! 打开此链接")
                printMsg(fallback.url)

                val req = daemon.newRequest(JsonObject().also { jo ->
                    jo.addProperty("type", "browser")
                    jo.addProperty("url", fallback.url)
                })

                printMsg("")
                printMsg("或使用 「SakuraLoginSolver」 打开 http://<ip>:${daemon.serverPort}/request/request/${req.requestId}")


                try {