package com.example.interviewtask_inspire

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_login.*

class LoginActivity :AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        logBtn.setOnClickListener {
            startActivity(Intent(this@LoginActivity,OtpActivity::class.java))
        }
    }
}