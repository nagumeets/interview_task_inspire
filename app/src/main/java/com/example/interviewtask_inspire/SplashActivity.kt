package com.example.interviewtask_inspire

import android.content.Intent
import android.os.Bundle
import android.text.Html
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_splash.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    val activityScope = CoroutineScope(Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        titleTxt.text = Html.fromHtml(getString(R.string.title))

        activityScope.launch {
            delay(3000)
            startActivity(
                Intent(this@SplashActivity, LoginActivity::class.java)
            )
            finish()
        }
    }

    override fun onPause() {
        activityScope.cancel()
        super.onPause()
    }
}