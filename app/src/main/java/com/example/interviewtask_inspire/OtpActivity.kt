package com.example.interviewtask_inspire

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.interviewtask_inspire.pinView.OnPinEnteredListener
import kotlinx.android.synthetic.main.activity_otp.*


class OtpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        pin_view.setOnPinEnteredListener(object :
            OnPinEnteredListener {
            override fun onPinEntered(str: CharSequence?) {
                Toast.makeText(this@OtpActivity, "Pin : $str", Toast.LENGTH_SHORT).show()
            }

        })
        pin_view.setOnEditorActionListener(TextView.OnEditorActionListener { v, actionId, event ->
            if (event != null && event.keyCode === KeyEvent.KEYCODE_ENTER || actionId == EditorInfo.IME_ACTION_DONE) {
                startActivity(Intent(applicationContext, MainActivity::class.java))
            }
            false
        })
    }
}