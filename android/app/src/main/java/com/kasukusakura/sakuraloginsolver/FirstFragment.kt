
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
            process(binding.urlOrId.text.toString())
        }
        binding.qrScan.setOnClickListener {
            qrScanLauncher.launch(Intent(requireActivity(), CaptureActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _client?.let { c ->
            c.dispatcher.cancelAll()
            c.connectionPool.evictAll()
            c.cache?.close()
        }
    }

    private class ReqContext(
        var processAlert: AlertDialog,
        val activity: Activity,
    ) {
        var complete: ((Intent) -> Unit)? = null
    }

    @Suppress("DEPRECATION")
    private fun submitBack(rspUrl: String, ticket: String) {
        submitBack(rspUrl, RequestBody.create(null, ticket))
    }

    private fun submitBack(rspUrl: String, rspbdy: RequestBody) {

        val activity = requireActivity()
        val alert = AlertDialog.Builder(activity)
            .setTitle("请稍后").setMessage("正在提交").setCancelable(false)
            .create()
        activity.runOnUiThread { alert.show() }

        client.newCall(
            Request.Builder().url(rspUrl).post(rspbdy).build()
        ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity.runOnUiThread {
                    alert.dismiss()
                    Toast.makeText(activity, e.message, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                activity.runOnUiThread {
                    alert.dismiss()
                    Toast.makeText(
                        activity,
                        "Done.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun processSakuraRequest(context: ReqContext, toplevel: JsonObject) {
        val sport = toplevel["port"].asInt
        val reqId = toplevel["id"].asString
        val servers = toplevel["server"].asJsonArray

        fun processNext(iter: Iterator<JsonElement>) {
            if (!iter.hasNext()) {
                context.activity.runOnUiThread {
                    context.processAlert.cancel()
                    Toast.makeText(requireActivity(), "No any server available....", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val serverIp = iter.next().asString
            val serverBase = "http://$serverIp:$sport"

            val urlx = "$serverBase/request/request/$reqId"
            context.processAlert.setMessage("Trying $urlx".also { Log.i(LOG_NAME, it) })

            client.newCall(
                Request.Builder()
                    .url(urlx)
                    .get()
                    .build()
            ).also { call ->
                if (InetAddress.getByName(serverIp).isSiteLocalAddress) {
                    call.timeout().timeout(3, TimeUnit.SECONDS)
                }
            }.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(LOG_NAME, "Net error", e)
                    processNext(iter)
                }

                override fun onResponse(call: Call, response: Response) {
                    val bdy = response.body
                    if (bdy == null || response.code != 200) {
                        processNext(iter)
                        return
                    }
