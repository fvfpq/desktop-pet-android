package com.example.lolipet.ui

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.lolipet.R
import com.example.lolipet.ai.ChatSession
import com.example.lolipet.ai.DeepSeekClient
import com.example.lolipet.tts.PetTts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var etInput: EditText
    private lateinit var tts: PetTts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        supportActionBar?.setTitle(R.string.app_name)

        messageContainer = findViewById(R.id.message_container)
        scrollView = findViewById(R.id.scroll_view)
        etInput = findViewById(R.id.et_input)
        val btnSend = findViewById<Button>(R.id.btn_send)
        tts = PetTts(this)

        btnSend.setOnClickListener { send() }
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send()
                true
            } else {
                false
            }
        }
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }

    private fun send() {
        val text = etInput.text.toString().trim()
        if (text.isEmpty()) return
        etInput.setText("")
        addMessage(text, isMine = true)
        ChatSession.addUser(text)
        addTyping()
        CoroutineScope(Dispatchers.Main).launch {
            var reply = ""
            try {
                reply = DeepSeekClient.chatStream(ChatSession.getMessages()) { delta ->
                    updateTyping(delta)
                }
            } catch (e: Exception) {
                reply = "唔…网络不太顺畅，请检查设置里的 API Key 哦"
            }
            removeTyping()
            ChatSession.addAssistant(reply)
            addMessage(reply, isMine = false)
            tts.speak(reply)
        }
    }

    private fun addMessage(text: String, isMine: Boolean) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(resources.getColor(R.color.text_dark, null))
        tv.textSize = 15f
        tv.setPadding(dp(12), dp(8), dp(12), dp(8))
        tv.background = resources.getDrawable(
            if (isMine) R.drawable.bubble_mine else R.drawable.bubble_other,
            null
        )
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginStart = if (isMine) dp(48) else dp(8)
        lp.marginEnd = if (isMine) dp(8) else dp(48)
        lp.topMargin = dp(6)
        lp.bottomMargin = dp(6)
        tv.layoutParams = lp
        messageContainer.addView(tv)
        scrollDown()
    }

    private fun addTyping() {
        val tv = TextView(this)
        tv.text = "…"
        tv.id = TYPING_ID
        tv.setTextColor(resources.getColor(R.color.text_gray, null))
        tv.textSize = 15f
        tv.setPadding(dp(12), dp(8), dp(12), dp(8))
        tv.background = resources.getDrawable(R.drawable.bubble_other, null)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = dp(48)
        lp.topMargin = dp(6)
        lp.bottomMargin = dp(6)
        tv.layoutParams = lp
        messageContainer.addView(tv)
        scrollDown()
    }

    private fun updateTyping(delta: String) {
        val tv = messageContainer.findViewById<TextView>(TYPING_ID) ?: return
        tv.text = tv.text.toString() + delta
        scrollDown()
    }

    private fun removeTyping() {
        messageContainer.findViewById<TextView>(TYPING_ID)?.let {
            messageContainer.removeView(it)
        }
    }

    private fun scrollDown() {
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TYPING_ID = 0x5A5A
    }
}
