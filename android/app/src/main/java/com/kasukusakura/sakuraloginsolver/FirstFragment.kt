
package com.kasukusakura.sakuraloginsolver

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kasukusakura.sakuraloginsolver.databinding.FragmentFirstBinding
import com.king.zxing.CameraScan
import com.king.zxing.CaptureActivity
import okhttp3.*
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {
    private companion object {
        private const val LOG_NAME = "SakuraSolver"
        private const val LEGACY_ONLINE_SERVICE = "https://txhelper.glitch.me/"
    }

    private var _binding: FragmentFirstBinding? = null
    private var _client: OkHttpClient? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val client: OkHttpClient get() = _client!!
    private lateinit var crtContext: ReqContext

    private lateinit var rawRequestLauncher: ActivityResultLauncher<Intent>
    private lateinit var qrScanLauncher: ActivityResultLauncher<Intent>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        _client = OkHttpClient()

        rawRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            requireActivity().runOnUiThread {
                crtContext.processAlert.dismiss()
            }
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { crtContext.complete?.invoke(it) }
            }
        }
        qrScanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            val rsp0 = CameraScan.parseScanResult(result.data).orEmpty()
            Log.i(LOG_NAME, "QRScan Rsp: $rsp0")
            if (rsp0.isEmpty()) return@registerForActivityResult


            process(rsp0)
        }
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.reset.setOnClickListener {
            binding.urlOrId.text.clear()
        }
        binding.next.setOnClickListener {