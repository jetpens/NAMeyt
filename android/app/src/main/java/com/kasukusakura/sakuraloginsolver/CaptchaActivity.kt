package com.kasukusakura.sakuraloginsolver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import com.google.gson.JsonParser
import com.kasukusakura.sakuraloginsolver.databinding.ActivityCaptchaBinding
import java.util.concurrent.Executor

class CaptchaActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCaptchaBinding

    private val dummyExecutor = Executor {}
    private val dummyListener = Runnable {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCaptchaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val webview = binding.webview
        webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest
            ): Boolean {
                return onJsBridgeInvoke(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                if (!intent.hasExtra("raw-direct")) return

                webview.evaluateJavascript("document.body.textContent") { rspx ->
                    kotlin.runCatching {
                        val roxitx = JsonParser.parseString(rspx)
                        if (roxitx.isJsonPrimitive) {
                            roxitx.asString
                        } else {
                            rspx
                        }.also { JsonParser.parseString(it).asJsonObject }
                    }.onSuccess { erx ->
                        val intent = Intent()
                            .putExtras(int