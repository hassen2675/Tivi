package com.streamvision.app.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.streamvision.app.R
import java.text.SimpleDateFormat
import java.util.*

class PlayerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())
    private var urls  = arrayListOf<String>()
    private var names = arrayListOf<String>()
    private var idx   = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Support both single URL and list of URLs
        val singleUrl  = intent.getStringExtra("url")
        val singleName = intent.getStringExtra("name") ?: ""

        if (singleUrl != null) {
            urls  = arrayListOf(singleUrl)
            names = arrayListOf(singleName)
            idx   = 0
        } else {
            urls  = intent.getStringArrayListExtra("urls")  ?: arrayListOf()
            names = intent.getStringArrayListExtra("names") ?: arrayListOf()
            idx   = intent.getIntExtra("index", 0)
        }

        if (urls.isEmpty()) { finish(); return }

        val playerView = findViewById<PlayerView>(R.id.playerView)
        val pbLoading  = findViewById<ProgressBar>(R.id.pbLoading)
        val tvName     = findViewById<TextView>(R.id.tvChannelName)
        val tvError    = findViewById<TextView>(R.id.tvError)
        val btnBack    = findViewById<View>(R.id.btnBack)
        val tvTime     = findViewById<TextView>(R.id.tvTime)
        val btnPrev    = findViewById<TextView>(R.id.btnPrevChannel)
        val btnNext    = findViewById<TextView>(R.id.btnNextChannel)
        val tvIndex    = findViewById<TextView>(R.id.tvChannelIndex)
        val llNav      = findViewById<View>(R.id.llNavButtons)

        // Show nav only if multiple channels
        llNav.visibility = if (urls.size > 1) View.VISIBLE else View.GONE

        btnBack.setOnClickListener { finish() }

        // Clock
        val clockR = object : Runnable {
            override fun run() {
                tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                handler.postDelayed(this, 30000)
            }
        }
        handler.post(clockR)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        playerView.useController = true

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> pbLoading.visibility = View.VISIBLE
                    Player.STATE_READY    -> { pbLoading.visibility = View.GONE; tvError.visibility = View.GONE }
                    Player.STATE_ENDED    -> if (urls.size > 1) playAt(idx + 1, tvName, tvIndex, pbLoading, tvError) else finish()
                    else -> {}
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                pbLoading.visibility = View.GONE
                if (urls.size > 1) {
                    tvError.visibility = View.VISIBLE
                    tvError.text = "⚠️ Fehler — nächster Kanal..."
                    handler.postDelayed({ playAt(idx + 1, tvName, tvIndex, pbLoading, tvError) }, 2000)
                } else {
                    tvError.visibility = View.VISIBLE
                    tvError.text = "⚠️ Stream konnte nicht geladen werden"
                }
            }
        })

        btnPrev.setOnClickListener { playAt(idx - 1, tvName, tvIndex, pbLoading, tvError) }
        btnNext.setOnClickListener { playAt(idx + 1, tvName, tvIndex, pbLoading, tvError) }

        playAt(idx, tvName, tvIndex, pbLoading, tvError)
    }

    private fun playAt(i: Int, tvName: TextView, tvIndex: TextView, pbLoading: ProgressBar, tvError: TextView) {
        if (urls.isEmpty()) return
        idx = ((i % urls.size) + urls.size) % urls.size
        tvName.text  = names.getOrElse(idx) { "Kanal ${idx + 1}" }
        tvIndex.text = "${idx + 1}/${urls.size}"
        tvError.visibility = View.GONE
        pbLoading.visibility = View.VISIBLE
        player.stop()
        player.setMediaItem(MediaItem.fromUri(urls[idx]))
        player.prepare()
        player.play()
    }

    override fun onPause()   { super.onPause();  player.pause() }
    override fun onResume()  { super.onResume(); player.play()  }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player.release()
    }
}
