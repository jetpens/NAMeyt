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
        super.onCreate(save