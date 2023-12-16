package com.techcos.videoPlayer

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.techcos.videoPlayer.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(MainActivity.themesList[MainActivity.themeIndex])
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "About"
        binding.twitterText.movementMethod = LinkMovementMethod.getInstance()
        binding.youtubeText.movementMethod = LinkMovementMethod.getInstance()
        binding.instaText.movementMethod = LinkMovementMethod.getInstance()
        binding.mailText.setOnClickListener{
            sendEmail("techcosincorporated@gmail.com", "Report Bug ", "")

        }

    }
    private fun sendEmail(recipient: String, subject: String, message: String) {
        /*ACTION_SEND action to launch an email client installed on your Android device.*/
        val mIntent = Intent(Intent.ACTION_SEND)
        /*To send an email you need to specify mailto: as URI using setData() method
        and data type will be to text/plain using setType() method*/
        mIntent.data = Uri.parse("mailto:")
        mIntent.type = "text/plain"
        val gmailPackage = "com.google.android.gm"
//        val isGmailInstalled = isAppInstalled(gmailPackage)
        // put recipient email in intent
        /* recipient is put as array because you may wanna send email to multiple emails
           so enter comma(,) separated emails, it will be stored in array*/
        mIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
        //put the Subject in the intent
        mIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
        //put the message in the intent
        mIntent.putExtra(Intent.EXTRA_TEXT, message)



        try {
            if (true) {
                mIntent.type = "text/html"
                mIntent.setPackage(gmailPackage)
                startActivity(mIntent)
            } else {
                // allow user to choose a different app to send email with
                mIntent.type = "message/rfc822"
                startActivity(Intent.createChooser(mIntent, "choose an email client"))
            }
            //start email intent
//            startActivity(Intent.createChooser(mIntent, "Choose Email Client..."))
        }
        catch (e: Exception){
            //if any thing goes wrong for example no email client application or any exception
            //get and show exception message
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }

    }

}