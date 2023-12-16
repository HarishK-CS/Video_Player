package com.techcos.videoPlayer

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.*
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.TimeBar
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.techcos.videoPlayer.databinding.ActivityPlayerBinding
import com.techcos.videoPlayer.databinding.BoosterBinding
import com.techcos.videoPlayer.databinding.MoreFeaturesBinding
import com.techcos.videoPlayer.databinding.SpeedDialogBinding
import java.io.File
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs
import kotlin.system.exitProcess


class PlayerActivity : AppCompatActivity(), AudioManager.OnAudioFocusChangeListener, GestureDetector.OnGestureListener {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var playPauseLayout: LinearLayout
    private lateinit var playPauseIcon: ImageView
    private lateinit var fullScreenBtn: ImageView
    private lateinit var videoTitle: TextView
    private lateinit var gestureDetectorCompat: GestureDetectorCompat
    private var minSwipeY: Float = 0f
    private val swipeThreshold = 100
    private val swipeVelocityThreshold = 100
    private var startX: Float = 0f
    private var startTime: Long = 0
    private lateinit var subtitleUri : Uri

    companion object{
        private var audioManager: AudioManager? = null
        private var timer: Timer? = null
        private lateinit var player: ExoPlayer
        lateinit var playerList: ArrayList<Video>
        var position: Int = -1
        private var repeat: Boolean = false
        private var eye: View? = null
        private var isFullscreen: Boolean = false
        private var isEyeMode: Boolean = false
        private var isMute: Boolean = false
        private var isLocked: Boolean = false
        private lateinit var trackSelector: DefaultTrackSelector
        private lateinit var loudnessEnhancer: LoudnessEnhancer
        private var speed: Float = 1.0f
        var pipStatus: Int = 0
        var nowPlayingId: String = ""
        private var brightness: Int = 0
        private var volume: Int = 0
        private var isSpeedChecked: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setTheme(R.style.playerActivityTheme)
        setContentView(binding.root)

        playPauseIcon = findViewById(R.id.playPauseIco)
        playPauseLayout = findViewById(R.id.playPauseLayout)

//      eye = findViewById(R.id.eye_protect)

        videoTitle = findViewById(R.id.videoTitle)
//        playPauseBtn = findViewById(R.id.playPauseBtn)
        fullScreenBtn = findViewById(R.id.fullScreenBtn)

        gestureDetectorCompat = GestureDetectorCompat(this, this)

        //for immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }



        try {
            //for handling video file intent (Improved Version)
            if(intent.data?.scheme.contentEquals("content")){
                playerList = ArrayList()
                position = 0
                val cursor = contentResolver.query(intent.data!!, arrayOf(MediaStore.Video.Media.DATA), null, null,
                    null)
                cursor?.let {
                    it.moveToFirst()
                    try {
                        val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
                        val file = File(path)
                        val video = Video(id = "", title = file.name, duration = 0L, artUri = Uri.fromFile(file), path = path, size = "", folderName = "")
                        playerList.add(video)
                        cursor.close()
                    }catch (e: Exception){
                        val tempPath = getPathFromURI(context = this, uri = intent.data!!)
                        val tempFile = File(tempPath)
                        val video = Video(id = "", title = tempFile.name, duration = 0L, artUri = Uri.fromFile(tempFile), path = tempPath, size = "", folderName = "")
                        playerList.add(video)
                        cursor.close()
                    }
                }
                createPlayer()
                initializeBinding()
            }
            else{
                initializeLayout()
                initializeBinding()
            }
        }catch (e: Exception){Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show()}

        Orientation()

    }

    @SuppressLint("PrivateResource")
    private fun initializeLayout(){
        when(intent.getStringExtra("class")){
            "AllVideos" -> {
                playerList = ArrayList()
                playerList.addAll(MainActivity.videoList)
                createPlayer()
            }
            "FolderActivity" -> {
                playerList = ArrayList()
                playerList.addAll(FoldersActivity.currentFolderVideos)
                createPlayer()

            }
            "SearchedVideos" ->{
                playerList = ArrayList()
                playerList.addAll(MainActivity.searchList)
                createPlayer()
            }
            "NowPlaying" ->{
                speed = 1.0f
                videoTitle.text = playerList[position].title
                videoTitle.isSelected = true
                doubleTapEnable()
                playVideo()
                playInFullscreen(enable = isFullscreen)
                seekBarFeature()
            }
        }
//        if(repeat) findViewById<ImageButton>(R.id.repeatBtn).setImageResource(R.drawable.exo_controls_repeat_all)
//        else findViewById<ImageButton>(R.id.repeatBtn).setImageResource(R.drawable.exo_controls_repeat_off)
    }

    @SuppressLint("SetTextI18n", "SourceLockedOrientationActivity", "PrivateResource")
    private fun initializeBinding(){

        findViewById<ImageView>(R.id.orientationBtn).setOnClickListener {
            requestedOrientation = if(resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            finish()
        }
//        playPauseBtn.setOnClickListener {
//            if(player.isPlaying) pauseVideo()
//            else playVideo()
//        }
        findViewById<ImageView>(R.id.nextBtn).setOnClickListener { nextPrevVideo() }
        findViewById<ImageView>(R.id.prevBtn).setOnClickListener { nextPrevVideo(isNext = false) }
//        findViewById<ImageButton>(R.id.repeatBtn).setOnClickListener {
//            if(repeat){
//                repeat = false
//                player.repeatMode = Player.REPEAT_MODE_OFF
//                findViewById<ImageButton>(R.id.repeatBtn).setImageResource(R.drawable.exo_controls_repeat_off)
//            }
//            else{
//                repeat = true
//                player.repeatMode = Player.REPEAT_MODE_ONE
//                findViewById<ImageButton>(R.id.repeatBtn).setImageResource(R.drawable.exo_controls_repeat_all)
//            }
//        }
        fullScreenBtn.setOnClickListener {
            if(isFullscreen){
                isFullscreen = false
                playInFullscreen(enable = false)
            }else{
                isFullscreen = true
                playInFullscreen(enable = true)
            }
        }
        binding.lockButton.setOnClickListener {
            if(!isLocked){
                //for hiding
                isLocked = true
                binding.playerView.hideController()
                binding.playerView.useController = false
                binding.lockButton.setImageResource(R.drawable.close_lock_icon)
            }
            else{
                //for showing
                isLocked = false
                binding.playerView.useController = true
                binding.playerView.showController()
                binding.lockButton.setImageResource(R.drawable.lock_open_icon)
            }
        }

        //for auto hiding & showing lock button
        binding.playerView.setControllerVisibilityListener {
            when{
                isLocked -> binding.lockButton.visibility = View.VISIBLE
                binding.playerView.isControllerVisible -> binding.lockButton.visibility = View.VISIBLE
                else -> binding.lockButton.visibility = View.INVISIBLE
            }
        }
//        findViewById<MaterialButton>(R.id.mute).setOnClickListener {
//                player.volume = 0f
//        }
//        findViewById<MaterialButton>(R.id.eye_protect).setOnClickListener {
//            eye?.visibility ?:   View.VISIBLE
//        }

        findViewById<MaterialButton>(R.id.audioTrack).setOnClickListener {
            playVideo()
            val audioTrack = ArrayList<String>()
            val audioList = ArrayList<String>()
            for(group in player.currentTracksInfo.trackGroupInfos){
                if(group.trackType == C.TRACK_TYPE_AUDIO){
                    val groupInfo = group.trackGroup
                    for (i in 0 until groupInfo.length){
                        audioTrack.add(groupInfo.getFormat(i).language.toString())
                        audioList.add("${audioList.size + 1}. " + Locale(groupInfo.getFormat(i).language.toString()).displayLanguage
                                + " (${groupInfo.getFormat(i).label})")
                    }
                }
            }

            if(audioList[0].contains("null")) audioList[0] = "1. Default Track"

            val tempTracks = audioList.toArray(arrayOfNulls<CharSequence>(audioList.size))
            val audioDialog = MaterialAlertDialogBuilder(this, R.style.alertDialog)
                .setTitle("Select Language")
                .setOnCancelListener { playVideo() }
                .setPositiveButton("Off Audio"){ self, _ ->
                    trackSelector.setParameters(trackSelector.buildUponParameters().setRendererDisabled(
                        C.TRACK_TYPE_AUDIO, true
                    ))
                    self.dismiss()
                }
                .setItems(tempTracks){_, position ->
                    Snackbar.make(binding.root, audioList[position] + " Selected", 3000).show()
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setRendererDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setPreferredAudioLanguage(audioTrack[position]))
                }
                .create()
            audioDialog.show()
            audioDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE)
            audioDialog.window?.setBackgroundDrawable(ColorDrawable(0x99000000.toInt()))
        }

        findViewById<MaterialButton>(R.id.subtitlesBtn).setOnClickListener {
            playVideo()
            val subtitles = ArrayList<String>()
            val subtitlesList = ArrayList<String>()
            for(group in player.currentTracksInfo.trackGroupInfos){
                if(group.trackType == C.TRACK_TYPE_TEXT){
                    val groupInfo = group.trackGroup
                    for (i in 0 until groupInfo.length){
                        subtitles.add(groupInfo.getFormat(i).language.toString())
                        subtitlesList.add("${subtitlesList.size + 1}. " + Locale(groupInfo.getFormat(i).language.toString()).displayLanguage
                                + " (${groupInfo.getFormat(i).label})")
                    }
                }
            }

            val tempTracks = subtitlesList.toArray(arrayOfNulls<CharSequence>(subtitlesList.size))
            val sDialog = MaterialAlertDialogBuilder(this, R.style.alertDialog)
                .setTitle("Select Subtitles")
                .setOnCancelListener { playVideo() }
                .setNeutralButton("Select Subtitle"){ _, _ ->
                    val extraMimeTypes = arrayOf("application/dvbsubs", "application/x-subrip","application/vobsub")
                    val intent = Intent()
                        .setType("*/*")
                        .putExtra(Intent.EXTRA_MIME_TYPES, extraMimeTypes)
                        .setAction(Intent.ACTION_GET_CONTENT)

                    startActivityForResult(Intent.createChooser(intent, "Select a file"), 111)
                }
                .setPositiveButton("Disable Subtitles"){ self, _ ->
                    trackSelector.setParameters(trackSelector.buildUponParameters().setRendererDisabled(
                        C.TRACK_TYPE_VIDEO, true
                    ))
                    self.dismiss()
                }
                .setItems(tempTracks){_, position ->
                    Snackbar.make(binding.root, subtitlesList[position] + " Selected", 3000).show()
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setRendererDisabled(C.TRACK_TYPE_VIDEO, false)
                        .setPreferredTextLanguage(subtitles[position]))
                }
                .create()
            sDialog.show()
            sDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE)
            sDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.WHITE)
            sDialog.window?.setBackgroundDrawable(ColorDrawable(0x99000000.toInt()))
        }
        findViewById<MaterialButton>(R.id.audioBooster).setOnClickListener {
            val customDialogB = LayoutInflater.from(this).inflate(R.layout.booster, binding.root, false)
            val bindingB = BoosterBinding.bind(customDialogB)
            val dialogB = MaterialAlertDialogBuilder(this).setView(customDialogB)
                .setOnCancelListener { playVideo() }
                .setPositiveButton("OK"){self, _ ->
                    loudnessEnhancer.setTargetGain(bindingB.verticalBar.progress * 100)
                    playVideo()
                    self.dismiss()
                }
                .setBackground(ColorDrawable(0x803700B3.toInt()))
                .create()
            dialogB.show()
            bindingB.verticalBar.progress = loudnessEnhancer.targetGain.toInt()/100
            bindingB.progressText.text = "Audio Boost\n\n${loudnessEnhancer.targetGain.toInt()/10} %"
            bindingB.verticalBar.setOnProgressChangeListener {
                bindingB.progressText.text = "Audio Boost\n\n${it*10} %"
            }
        }
        findViewById<MaterialButton>(R.id.speedBtn).setOnClickListener {
            playVideo()
            val customDialogS = LayoutInflater.from(this).inflate(R.layout.speed_dialog, binding.root, false)
            val bindingS = SpeedDialogBinding.bind(customDialogS)
            val dialogS = MaterialAlertDialogBuilder(this).setView(customDialogS)
                .setCancelable(false)
                .setPositiveButton("OK"){self, _ ->
                    self.dismiss()
                }
                .setBackground(ColorDrawable(0x803700B3.toInt()))
                .create()
            dialogS.show()
            bindingS.speedText.text = "${DecimalFormat("#.##").format(speed)} X"
            bindingS.minusBtn.setOnClickListener {
                changeSpeed(isIncrement = false)
                bindingS.speedText.text = "${DecimalFormat("#.##").format(speed)} X"
            }
            bindingS.plusBtn.setOnClickListener {
                changeSpeed(isIncrement = true)
                bindingS.speedText.text = "${DecimalFormat("#.##").format(speed)} X"
            }

            bindingS.speedCheckBox.isChecked = isSpeedChecked
            bindingS.speedCheckBox.setOnClickListener {
                it as CheckBox
                isSpeedChecked = it.isChecked
            }
        }

        findViewById<MaterialButton>(R.id.sleepTimer).setOnClickListener {
            if(timer != null) Toast.makeText(this, "Timer Already Running!!\nClose App to Reset Timer!!", Toast.LENGTH_SHORT).show()
            else{
                var sleepTime = 15
                val customDialogS = LayoutInflater.from(this).inflate(R.layout.speed_dialog, binding.root, false)
                val bindingS = SpeedDialogBinding.bind(customDialogS)
                val dialogS = MaterialAlertDialogBuilder(this).setView(customDialogS)
                    .setCancelable(false)
                    .setPositiveButton("OK"){self, _ ->
                        timer = Timer()
                        val task = object: TimerTask(){
                            override fun run() {
                                moveTaskToBack(true)
                                exitProcess(1)
                            }
                        }
                        timer!!.schedule(task, sleepTime*60*1000.toLong())
                        self.dismiss()
                        playVideo()
                    }
                    .setBackground(ColorDrawable(0x803700B3.toInt()))
                    .create()
                dialogS.show()
                bindingS.speedText.text = "$sleepTime Min"
                bindingS.minusBtn.setOnClickListener {
                    if(sleepTime > 15) sleepTime -=15
                    bindingS.speedText.text = "$sleepTime Min"
                }
                bindingS.plusBtn.setOnClickListener {
                    if(sleepTime < 120) sleepTime += 15
                    bindingS.speedText.text = "$sleepTime Min"
                }
            }
        }
        findViewById<MaterialButton>(R.id.pipModeBtn).setOnClickListener {
            enterIntoPIPMode()
        }


        findViewById<ImageButton>(R.id.moreFeaturesBtn).setOnClickListener {
            pauseVideo()
            val customDialog = LayoutInflater.from(this).inflate(R.layout.more_features, binding.root, false)
            val bindingMF = MoreFeaturesBinding.bind(customDialog)
            val dialog = MaterialAlertDialogBuilder(this).setView(customDialog)
                .setOnCancelListener { playVideo() }
                .setBackground(ColorDrawable(0x803700B3.toInt()))
                .create()
            dialog.show()

            bindingMF.audioTrack.setOnClickListener {
                dialog.dismiss()
                playVideo()
                val audioTrack = ArrayList<String>()
                val audioList = ArrayList<String>()
                for(group in player.currentTracksInfo.trackGroupInfos){
                    if(group.trackType == C.TRACK_TYPE_AUDIO){
                        val groupInfo = group.trackGroup
                        for (i in 0 until groupInfo.length){
                            audioTrack.add(groupInfo.getFormat(i).language.toString())
                            audioList.add("${audioList.size + 1}. " + Locale(groupInfo.getFormat(i).language.toString()).displayLanguage
                                    + " (${groupInfo.getFormat(i).label})")
                        }
                    }
                }

                if(audioList[0].contains("null")) audioList[0] = "1. Default Track"

                val tempTracks = audioList.toArray(arrayOfNulls<CharSequence>(audioList.size))
                val audioDialog = MaterialAlertDialogBuilder(this, R.style.alertDialog)
                    .setTitle("Select Language")
                    .setOnCancelListener { playVideo() }
                    .setPositiveButton("Off Audio"){ self, _ ->
                        trackSelector.setParameters(trackSelector.buildUponParameters().setRendererDisabled(
                            C.TRACK_TYPE_AUDIO, true
                        ))
                        self.dismiss()
                    }
                    .setItems(tempTracks){_, position ->
                        Snackbar.make(binding.root, audioList[position] + " Selected", 3000).show()
                        trackSelector.setParameters(trackSelector.buildUponParameters()
                            .setRendererDisabled(C.TRACK_TYPE_AUDIO, false)
                            .setPreferredAudioLanguage(audioTrack[position]))
                    }
                    .create()
                audioDialog.show()
                audioDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE)
                audioDialog.window?.setBackgroundDrawable(ColorDrawable(0x99000000.toInt()))
            }
            bindingMF.subtitlesBtn.setOnClickListener {
                dialog.dismiss()
                playVideo()
                val subtitles = ArrayList<String>()
                val subtitlesList = ArrayList<String>()
                for(group in player.currentTracksInfo.trackGroupInfos){
                    if(group.trackType == C.TRACK_TYPE_TEXT){
                        val groupInfo = group.trackGroup
                        for (i in 0 until groupInfo.length){
                            subtitles.add(groupInfo.getFormat(i).language.toString())
                            subtitlesList.add("${subtitlesList.size + 1}. " + Locale(groupInfo.getFormat(i).language.toString()).displayLanguage
                                    + " (${groupInfo.getFormat(i).label})")
                        }
                    }
                }

                val tempTracks = subtitlesList.toArray(arrayOfNulls<CharSequence>(subtitlesList.size))
                val sDialog = MaterialAlertDialogBuilder(this, R.style.alertDialog)
                    .setTitle("Select Subtitles")
                    .setOnCancelListener { playVideo() }
                    .setPositiveButton("Off Subtitles"){ self, _ ->
                        trackSelector.setParameters(trackSelector.buildUponParameters().setRendererDisabled(
                            C.TRACK_TYPE_VIDEO, true
                        ))
                        self.dismiss()
                    }
                    .setItems(tempTracks){_, position ->
                        Snackbar.make(binding.root, subtitlesList[position] + " Selected", 3000).show()
                        trackSelector.setParameters(trackSelector.buildUponParameters()
                            .setRendererDisabled(C.TRACK_TYPE_VIDEO, false)
                            .setPreferredTextLanguage(subtitles[position]))
                    }
                    .create()
                sDialog.show()
                sDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE)
                sDialog.window?.setBackgroundDrawable(ColorDrawable(0x99000000.toInt()))
            }
            bindingMF.audioBooster.setOnClickListener {
                dialog.dismiss()
                val customDialogB = LayoutInflater.from(this).inflate(R.layout.booster, binding.root, false)
                val bindingB = BoosterBinding.bind(customDialogB)
                val dialogB = MaterialAlertDialogBuilder(this).setView(customDialogB)
                    .setOnCancelListener { playVideo() }
                    .setPositiveButton("OK"){self, _ ->
                        loudnessEnhancer.setTargetGain(bindingB.verticalBar.progress * 100)
                        playVideo()
                        self.dismiss()
                    }
                    .setBackground(ColorDrawable(0x803700B3.toInt()))
                    .create()
                dialogB.show()
                bindingB.verticalBar.progress = loudnessEnhancer.targetGain.toInt()/100
                bindingB.progressText.text = "Audio Boost\n\n${loudnessEnhancer.targetGain.toInt()/10} %"
                bindingB.verticalBar.setOnProgressChangeListener {
                    bindingB.progressText.text = "Audio Boost\n\n${it*10} %"
                }
            }
            bindingMF.speedBtn.setOnClickListener {
                dialog.dismiss()
                playVideo()
                val customDialogS = LayoutInflater.from(this).inflate(R.layout.speed_dialog, binding.root, false)
                val bindingS = SpeedDialogBinding.bind(customDialogS)
                val dialogS = MaterialAlertDialogBuilder(this).setView(customDialogS)
                    .setCancelable(false)
                    .setPositiveButton("OK"){self, _ ->
                        self.dismiss()
                    }
                    .setBackground(ColorDrawable(0x803700B3.toInt()))
                    .create()
                dialogS.show()
                bindingS.speedText.text = "${DecimalFormat("#.##").format(speed)} X"
                bindingS.minusBtn.setOnClickListener {
                    changeSpeed(isIncrement = false)
                    bindingS.speedText.text = "${DecimalFormat("#.##").format(speed)} X"
                }
                bindingS.plusBtn.setOnClickListener {
                    changeSpeed(isIncrement = true)
                    bindingS.speedText.text = "${DecimalFormat("#.##").format(speed)} X"
                }

                bindingS.speedCheckBox.isChecked = isSpeedChecked
                bindingS.speedCheckBox.setOnClickListener {
                    it as CheckBox
                    isSpeedChecked = it.isChecked
                }
            }

            bindingMF.sleepTimer.setOnClickListener {
                dialog.dismiss()
                if(timer != null) Toast.makeText(this, "Timer Already Running!!\nClose App to Reset Timer!!", Toast.LENGTH_SHORT).show()
                else{
                    var sleepTime = 15
                    val customDialogS = LayoutInflater.from(this).inflate(R.layout.speed_dialog, binding.root, false)
                    val bindingS = SpeedDialogBinding.bind(customDialogS)
                    val dialogS = MaterialAlertDialogBuilder(this).setView(customDialogS)
                        .setCancelable(false)
                        .setPositiveButton("OK"){self, _ ->
                            timer = Timer()
                            val task = object: TimerTask(){
                                override fun run() {
                                    moveTaskToBack(true)
                                    exitProcess(1)
                                }
                            }
                            timer!!.schedule(task, sleepTime*60*1000.toLong())
                            self.dismiss()
                            playVideo()
                        }
                        .setBackground(ColorDrawable(0x803700B3.toInt()))
                        .create()
                    dialogS.show()
                    bindingS.speedText.text = "$sleepTime Min"
                    bindingS.minusBtn.setOnClickListener {
                        if(sleepTime > 15) sleepTime -=15
                        bindingS.speedText.text = "$sleepTime Min"
                    }
                    bindingS.plusBtn.setOnClickListener {
                        if(sleepTime < 120) sleepTime += 15
                        bindingS.speedText.text = "$sleepTime Min"
                    }
                }
            }
            bindingMF.pipModeBtn.setOnClickListener {
                val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), packageName)==
                            AppOpsManager.MODE_ALLOWED
                } else { false }

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    if (status) {
                        this.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                        dialog.dismiss()
                        binding.playerView.hideController()
                        playVideo()
                        pipStatus = 0
                    }
                    else{
                        val intent = Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS",
                            Uri.parse("package:$packageName"))
                        startActivity(intent)
                    }
                }else{
                    Toast.makeText(this, "Feature Not Supported!!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    playVideo()
                }
            }
        }
    }

    private fun enterIntoPIPMode() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), packageName)==
                    AppOpsManager.MODE_ALLOWED
        } else { false }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            if (status) {
                this.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                binding.playerView.hideController()
                playVideo()
                pipStatus = 0
            }
            else{
                val intent = Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS",
                    Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }else{
            Toast.makeText(this, "Feature Not Supported!!", Toast.LENGTH_SHORT).show()
            playVideo()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 111 && resultCode == RESULT_OK) {

             subtitleUri = data?.data!! // The URI with the location of the file
            val mediaItem = MediaItem.Builder()
                .setUri(playerList[position].artUri)
                .setSubtitles(listOf(MediaItem.Subtitle(
                    subtitleUri, MimeTypes.APPLICATION_SUBRIP,
                    getFileName(subtitleUri)

                    )))
                .build()

// Set the new MediaItem to the player
            player.setMediaItem(mediaItem)
            Snackbar.make(binding.root,"Subtitle Added ! Select the subtitle Now ",3000).show()


// Prepare the player
            player.prepare()
            playVideo()
        }
    }
    @SuppressLint("Range", "Recycle")
    fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            } finally {
                cursor!!.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result!!.lastIndexOf('/')
            if (cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }
    private fun createPlayer(){
        try { player.release() }catch (e: Exception){}

        if(!isSpeedChecked) speed = 1.0f

        trackSelector = DefaultTrackSelector(this)
        videoTitle.text = playerList[position].title
        videoTitle.isSelected = true
        player = ExoPlayer.Builder(this).setTrackSelector(trackSelector).build()
        doubleTapEnable()
        val mediaItem = MediaItem.fromUri(playerList[position].artUri)
        player.setMediaItem(mediaItem)

        player.setPlaybackSpeed(speed)

        player.prepare()
        playVideo()
        player.addListener(object : Player.Listener{
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                if(playbackState == Player.STATE_ENDED) nextPrevVideo()
//                if (playbackState == Player.COMMAND_PLAY_PAUSE)
//                    playPauseBtn.setImageResource(R.drawable.pause_icon)
//                else
//                    playPauseBtn.setImageResource(R.drawable.play_icon)
            }
        })
        playInFullscreen(enable = isFullscreen)
        loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
        loudnessEnhancer.enabled = true
        nowPlayingId = playerList[position].id
        seekBarFeature()

    }
    private fun playVideo(){
        playPauseLayout.visibility = View.VISIBLE
        playPauseIcon.setImageResource(R.drawable.play_icon)
        Handler(Looper.getMainLooper()).postDelayed({
            playPauseLayout.visibility = View.GONE
//            binding.playerView.showController()
        }, 500)
        player.play()
    }
    private fun pauseVideo(){
        playPauseLayout.visibility = View.VISIBLE
        playPauseIcon.setImageResource(R.drawable.pause_icon)
        Handler(Looper.getMainLooper()).postDelayed({
            playPauseLayout.visibility = View.GONE
//            binding.playerView.showController()
        }, 500)

        player.pause()
    }
    private fun nextPrevVideo(isNext: Boolean = true){
        if(isNext) setPosition()
        else setPosition(isIncrement = false)
        createPlayer()
    }
    private fun setPosition(isIncrement: Boolean = true){
        if(!repeat){
            if(isIncrement){
                if(playerList.size -1 == position)
                    position = 0
                else ++position
            }else{
                if(position  == 0)
                    position = playerList.size - 1
                else --position
            }
        }
    }
    private fun playInFullscreen(enable: Boolean){
        if(enable){
            binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            fullScreenBtn.setImageResource(R.drawable.fullscreen_exit_icon)
        }else{
            binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            fullScreenBtn.setImageResource(R.drawable.fullscreen_icon)
        }
    }

    private fun changeSpeed(isIncrement: Boolean){
        if(isIncrement){
            if(speed <= 2.9f){
                speed += 0.10f //speed = speed + 0.10f
            }
        }
        else{
            if(speed > 0.20f){
                speed -= 0.10f
            }
        }
        player.setPlaybackSpeed(speed)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        if (newConfig != null) {
            super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        }
        if(pipStatus != 0){
            finish()
            val intent = Intent(this, PlayerActivity::class.java)
            when(pipStatus){
                1 -> intent.putExtra("class","FolderActivity")
                2 -> intent.putExtra("class","SearchedVideos")
                3 -> intent.putExtra("class","AllVideos")
            }
            startActivity(intent)
        }
        if(!isInPictureInPictureMode) pauseVideo()

    }

    override fun onDestroy() {
        super.onDestroy()
        player.pause()
        audioManager?.abandonAudioFocus(this)
    }

    override fun onPause() {
        super.onPause()
        enterIntoPIPMode()
//        player.pause()
//        audioManager?.abandonAudioFocus(this)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if(focusChange <= 0) pauseVideo()
    }

    override fun onResume() {
        super.onResume()
        if(audioManager == null) audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager!!.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        if(brightness != 0) setScreenBrightness(brightness)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun doubleTapEnable(){
        binding.playerView.player = player
        binding.playerView.setOnTouchListener { v, event ->
            if (player.isPlaying) pauseVideo()
            else playVideo()

            v?.onTouchEvent(event) ?: true
        }
//        binding.ytOverlay.performListener(object: YouTubeOverlay.PerformListener{
//            override fun onAnimationEnd() {
//                binding.ytOverlay.visibility = View.GONE
//            }
//
//            override fun onAnimationStart() {
//                binding.ytOverlay.visibility = View.VISIBLE
//            }
//        })
//        binding.ytOverlay.player(player)
        binding.playerView.setOnTouchListener { _, motionEvent ->
//            binding.playerView.isDoubleTapEnabled = false
            if(!isLocked){
                gestureDetectorCompat.onTouchEvent(motionEvent)
                if(motionEvent.action == MotionEvent.ACTION_UP) {
                    binding.brightnessIcon.visibility = View.GONE
                    binding.volumeIcon.visibility = View.GONE
                    //for immersive mode
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    WindowInsetsControllerCompat(window, binding.root).let { controller ->
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }

            }
            return@setOnTouchListener false
        }
        binding.playerView.setOnClickListener(object : DoubleClickListener() {
            override fun onDoubleClick(v: View?) {
                if(player.isPlaying) pauseVideo()
                else playVideo()
//                Toast.makeText(applicationContext,"Double Click",Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun seekBarFeature(){
        findViewById<DefaultTimeBar>(R.id.exo_progress).addListener(object: TimeBar.OnScrubListener{
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                pauseVideo()
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                player.seekTo(position)
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                playVideo()
            }

        })
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
            super.onTouchEvent(event)
//             Toast.makeText(applicationContext, "Tap gesture", Toast.LENGTH_SHORT).show()
        return true

    }
    override fun onDown(p0: MotionEvent): Boolean {
        startX = p0?.x ?: 0f
        startTime = System.currentTimeMillis()
        minSwipeY = 0f
        return false
    }
    override fun onShowPress(p0: MotionEvent) = Unit
    override fun onSingleTapUp(p0: MotionEvent): Boolean = false
    override fun onLongPress(p0: MotionEvent) = Unit




    override fun onFling(p0: MotionEvent, p1: MotionEvent, p2: Float, p3: Float): Boolean {
//        val swipeThreshold = 100
//        val swipeVelocityThreshold = 100
//
//        val deltaX = p1?.x?.minus(p0?.x ?: 0f) ?: 0f
//        val deltaY = p1?.y?.minus(p0?.y ?: 0f) ?: 0f
//        val deltaXAbs = deltaX.absoluteValue
//
//        if (deltaXAbs > swipeThreshold && deltaXAbs > swipeVelocityThreshold) {
//            if (deltaX > 0) {
//                // Swipe to the right, forward
//                player.seekTo(player.currentPosition + 10000) // Adjust the seek duration as needed
//            } else {
//                // Swipe to the left, rewind
//                player.seekTo(player.currentPosition - 10000) // Adjust the seek duration as needed
//            }
//        }

        return true
//        try {
//            val diffY = p1.x - p0.y
//            val diffX = p1.x - p0.x
//            if (abs(diffX) > abs(diffY)) {
//                if (abs(diffX) > swipeThreshold && abs(p2) > swipeVelocityThreshold) {
//
//                    if (diffX > 0) {
//
////                        Toast.makeText(applicationContext, "Left to Right swipe gesture", Toast.LENGTH_SHORT).show()
//                    }
//                    else {
//                        adjustVideoPosition(diffX.toDouble())
////                        Toast.makeText(applicationContext, "Right to Left swipe gesture", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            }
//        }
//        catch (exception: Exception) {
//            exception.printStackTrace()
//        }
//        return true
    }
    private fun adjustVideoPosition( a: Double){
        var ap = a
        if(ap < -1.0f){
            ap  = (-1.0f).toDouble()
        }else if(ap>1.0f){
            ap = (1.0f).toDouble()
        }

        var totTime : Int = player.duration.toInt()

        var startVidTime : Int = player.contentPosition.toInt()

        var positiveAdjustPercent : Double = Math.max(ap,-ap)

        var targetTime : Long = startVidTime + ((100.0).toLong() + ap * positiveAdjustPercent / 0.1).toLong()

        if (targetTime > totTime){
            totTime = totTime
        }
        if (targetTime < 0){
            targetTime = 0
        }
//        var targetTimeString : String = forwardDirection(targetTime / 1000)

        player.seekTo(player.currentPosition+targetTime.toLong())


    }

    override fun onScroll(event: MotionEvent, event1: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        minSwipeY += distanceY

        val sWidth = Resources.getSystem().displayMetrics.widthPixels
        val sHeight = Resources.getSystem().displayMetrics.heightPixels

        val border = 100 * Resources.getSystem().displayMetrics.density.toInt()
        if(event.x < border || event.y < border || event.x > sWidth - border || event.y > sHeight - border)
            return false

        //minSwipeY for slowly increasing brightness & volume on swipe --> try changing 50 (<50 --> quick swipe & > 50 --> slow swipe
        // & test with your custom values
        if(abs(distanceX) < abs(distanceY) && abs(minSwipeY) > 50){
            if(event.x < sWidth/2){
                //brightness
                binding.brightnessIcon.visibility = View.VISIBLE
                binding.volumeIcon.visibility = View.GONE
                val increase = distanceY > 0
                val newValue = if(increase) brightness + 1 else brightness - 1
                if(newValue in 0..30) brightness = newValue
                binding.brightnessIcon.text = brightness.toString()
                setScreenBrightness(brightness)
            }
            else{
                //volume
                binding.brightnessIcon.visibility = View.GONE
                binding.volumeIcon.visibility = View.VISIBLE
                val maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val increase = distanceY > 0
                val newValue = if(increase) volume + 1 else volume - 1
                if(newValue in 0..maxVolume) volume = newValue
                binding.volumeIcon.text = volume.toString()
                audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            }
            minSwipeY = 0f
        }

//        val sensitivityMultiplier = 5.0 // Increase this value for higher sensitivity
//        val swipeThreshold = 50
//        val swipeVelocityThreshold = 1000
//
//        // Calculate swipe distance
//        val deltaX = event1?.x?.minus(startX) ?: 0f
//        val deltaXAbs = deltaX.absoluteValue
//
//        // Calculate time duration
//        val currentTime = System.currentTimeMillis()
//        val duration = currentTime - startTime
//
//        // Calculate swipe velocity
//        val velocityX = deltaX / duration
//
//        if (deltaXAbs > swipeThreshold && velocityX > swipeVelocityThreshold) {
//            val seekDelta = deltaX * sensitivityMultiplier
//            val seekPosition = player.currentPosition + seekDelta.toLong()
//
//            // Ensure seekPosition is within valid bounds
//            val duration = player.duration
//            val newPosition = seekPosition.coerceIn(0, duration)
//
//            player.seekTo(newPosition)
////            val seekDuration = deltaXAbs / velocityX * sensitivityMultiplier
////
////            if (deltaX > 0) {
////                // Swipe to the right, forward
////                player.seekTo(player.currentPosition + seekDuration.toLong())
////            } else {
////                // Swipe to the left, rewind
////                player.seekTo(player.currentPosition - seekDuration.toLong())
////            }
//        }
//
//        // Update start position and time for the next scroll event
//        startX = event1?.x ?: 0f
//        startTime = currentTime

        val sensitivityMultiplier = 100.0 // Adjust this value for sensitivity

        // Calculate swipe distance
        val deltaX = event1?.x?.minus(startX) ?: 0f

        // Adjust the seek position based on scroll distance
        val seekDelta = deltaX * sensitivityMultiplier
        player.seekTo((player.currentPosition + seekDelta).coerceAtLeast(0.0).toLong())

        // Update start position for the next scroll event
        startX = event1?.x ?: 0f
        return true
    }

    private fun setScreenBrightness(value: Int){
        val d = 1.0f/30
        val lp = this.window.attributes
        lp.screenBrightness = d * value
        this.window.attributes = lp
    }
    private fun Orientation() {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(playerList[position].path)
//        val width =
//
//        val height =
//            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
//        retriever.release()
        if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0> retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0){
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }else{
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

    }

    //used to get path of video selected by user (if column data fails to get path)
    private fun getPathFromURI(context: Context , uri : Uri): String {
        var filePath = ""
        // ExternalStorageProvider
        val docId = DocumentsContract.getDocumentId(uri)
        val split = docId.split(':')
        val type = split[0]

        return if ("primary".equals(type, ignoreCase = true)) {
            "${Environment.getExternalStorageDirectory()}/${split[1]}"
        } else {
            //getExternalMediaDirs() added in API 21
            val external = context.externalMediaDirs
            if (external.size > 1) {
                filePath = external[1].absolutePath
                filePath = filePath.substring(0, filePath.indexOf("Android")) + split[1]
            }
            filePath
        }
    }
    abstract class DoubleClickListener : View.OnClickListener {
        var lastClickTime: Long = 0
        override fun onClick(v: View?) {
            val clickTime = System.currentTimeMillis()
            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                onDoubleClick(v)
            }
            lastClickTime = clickTime
        }

        abstract fun onDoubleClick(v: View?)

        companion object {
            private const val DOUBLE_CLICK_TIME_DELTA: Long = 300 //milliseconds
        }
    }
}


