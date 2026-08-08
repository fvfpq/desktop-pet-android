package com.example.lolipet.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lolipet.Prefs
import com.example.lolipet.R
import com.example.lolipet.ai.DeepSeekClient
import com.example.lolipet.service.PetService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var etBaseUrl: EditText
    private lateinit var etModel: EditText
    private lateinit var etPersona: EditText
    private lateinit var swTts: Switch
    private lateinit var swWander: Switch
    private lateinit var swBubble: Switch
    private lateinit var swAutoStart: Switch
    private lateinit var rgPetModel: RadioGroup
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etApiKey = findViewById(R.id.et_api_key)
        etBaseUrl = findViewById(R.id.et_base_url)
        etModel = findViewById(R.id.et_model)
        etPersona = findViewById(R.id.et_persona)
        swTts = findViewById(R.id.sw_tts)
        swWander = findViewById(R.id.sw_wander)
        swBubble = findViewById(R.id.sw_bubble)
        swAutoStart = findViewById(R.id.sw_auto_start)
        rgPetModel = findViewById(R.id.rg_pet_model)
        tvStatus = findViewById(R.id.tv_status)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnTest = findViewById<Button>(R.id.btn_test)
        val btnToggle = findViewById<Button>(R.id.btn_toggle)
        val btnChat = findViewById<Button>(R.id.btn_chat)

        loadPrefs()

        btnSave.setOnClickListener { savePrefs() }
        btnTest.setOnClickListener { testConnection() }
        btnToggle.setOnClickListener { togglePet() }
        btnChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun loadPrefs() {
        etApiKey.setText(Prefs.apiKey)
        etBaseUrl.setText(Prefs.baseUrl)
        etModel.setText(Prefs.modelName)
        etPersona.setText(Prefs.persona)
        swTts.isChecked = Prefs.ttsEnabled
        swWander.isChecked = Prefs.wanderEnabled
        swBubble.isChecked = Prefs.showBubble
        swAutoStart.isChecked = Prefs.autoStart
        when (Prefs.petModel) {
            "haru" -> rgPetModel.check(R.id.rb_haru)
            "tsumiki" -> rgPetModel.check(R.id.rb_tsumiki)
            "nico" -> rgPetModel.check(R.id.rb_nico)
            "koharu" -> rgPetModel.check(R.id.rb_koharu)
            "lisa" -> rgPetModel.check(R.id.rb_lisa)
            else -> rgPetModel.check(R.id.rb_shizuku)
        }
    }

    private fun savePrefs() {
        Prefs.apiKey = etApiKey.text.toString()
        Prefs.baseUrl = etBaseUrl.text.toString()
        Prefs.modelName = etModel.text.toString()
        Prefs.persona = etPersona.text.toString()
        Prefs.ttsEnabled = swTts.isChecked
        Prefs.wanderEnabled = swWander.isChecked
        Prefs.showBubble = swBubble.isChecked
        Prefs.autoStart = swAutoStart.isChecked
        Prefs.petModel = when (rgPetModel.checkedRadioButtonId) {
            R.id.rb_haru -> "haru"
            R.id.rb_tsumiki -> "tsumiki"
            R.id.rb_nico -> "nico"
            R.id.rb_koharu -> "koharu"
            R.id.rb_lisa -> "lisa"
            else -> "shizuku"
        }
        Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show()
    }

    private fun testConnection() {
        savePrefs()
        if (Prefs.apiKey.isBlank()) {
            Toast.makeText(this, "请先填写 API Key", Toast.LENGTH_SHORT).show()
            return
        }
        tvStatus.text = "正在测试连接…"
        CoroutineScope(Dispatchers.Main).launch {
            val ok = withContext(Dispatchers.IO) { DeepSeekClient.testConnection() }
            tvStatus.text = if (ok) getString(R.string.test_ok) else getString(R.string.test_fail)
        }
    }

    private fun updateStatus() {
        tvStatus.text = if (Prefs.isRunning) getString(R.string.pet_running) else getString(R.string.pet_stopped)
    }

    private fun togglePet() {
        if (Prefs.isRunning) {
            PetService.stop(this)
            Prefs.isRunning = false
            updateStatus()
            Toast.makeText(this, R.string.pet_stopped, Toast.LENGTH_SHORT).show()
        } else {
            if (!canDrawOverlays()) {
                Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                requestOverlayPermission()
                return
            }
            savePrefs()
            PetService.start(this)
            Prefs.isRunning = true
            updateStatus()
            Toast.makeText(this, R.string.pet_running, Toast.LENGTH_SHORT).show()
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}
