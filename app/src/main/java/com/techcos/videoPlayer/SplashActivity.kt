package com.techcos.videoPlayer

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.animation.AnimationUtils
import com.techcos.videoPlayer.databinding.ActivityPlayerBinding
import com.techcos.videoPlayer.databinding.ActivitySplashBinding
@SuppressLint("CustomSplashScreen")
@Suppress("DEPRECATION")
class SplashActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_splash)


        val top : Animation = android.view.animation.AnimationUtils.loadAnimation(this,R.anim.top)
        val bottom : Animation = android.view.animation.AnimationUtils.loadAnimation(this,R.anim.bottom)
        val fade : Animation =android.view.animation.AnimationUtils.loadAnimation(this,R.anim.fade)
        val img :ImageView = findViewById(R.id.image)
        val techcosLogo :ImageView = findViewById(R.id.techcosLogo)
        val t1 :TextView = findViewById(R.id.t1)
        val t2 :TextView = findViewById(R.id.t2)
        val t3 :TextView = findViewById(R.id.t3)

        img.startAnimation(top)
        techcosLogo.startAnimation(fade)
        t1.startAnimation(bottom)
        t2.startAnimation(bottom)
        t3.startAnimation(bottom)

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, 3000)
    }
}