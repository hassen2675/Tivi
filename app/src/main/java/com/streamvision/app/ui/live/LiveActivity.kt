package com.streamvision.app.ui.live

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.streamvision.app.R
import com.streamvision.app.data.api.ApiClient
import com.streamvision.app.data.models.Category
import com.streamvision.app.data.models.Channel
import com.streamvision.app.ui.player.PlayerActivity
import com.streamvision.app.utils.SessionManager
import kotlin.concurrent.thread

class LiveActivity : AppCompatActivity() {

    private var allChannels = listOf<Channel>()
    private var filtered = listOf<Channel>()
    private var selCat = "all"
    private var server = ""; private var username = ""; private var password = ""
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var catAdapter: CategoryAdapter
    private var miniPlayer: ExoPlayer? = null
    private var currentMiniUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)

        server   = intent.getStringExtra("server") ?: ""
        username = intent.getStringExtra("username") ?: ""
        password = intent.getStringExtra("password") ?: ""

        val rvCh    = findViewById<RecyclerView>(R.id.rvChannels)
        val rvCats  = findViewById<RecyclerView>(R.id.rvCategories)
        val pb      = findViewById<ProgressBar>(R.id.progressBar)
        val et      = findViewById<EditText>(R.id.etSearch)
        val btnBack = findViewById<View>(R.id.btnBack)
        val btnPlay = findViewById<TextView>(R.id.btnPlay)
        val tvName  = findViewById<TextView>(R.id.tvPreviewName)
        val tvQual  = findViewById<TextView>(R.id.tvPreviewQuality)
        val ivIcon  = findViewById<ImageView>(R.id.ivChannelIcon)
        val tvPlaceholder = findViewById<TextView>(R.id.tvPreviewPlaceholder)
        val pbMini  = findViewById<ProgressBar>(R.id.pbMiniPlayer)
        val tvLive  = findViewById<TextView>(R.id.tvLiveBadge)
        val miniPV  = findViewById<PlayerView>(R.id.miniPlayer)

        btnBack.setOnClickListener { finish() }

        // Setup mini ExoPlayer
        miniPlayer = ExoPlayer.Builder(this).build()
        miniPV.player = miniPlayer
        miniPV.useController = false

        miniPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        pbMini.visibility = View.VISIBLE
                        miniPV.visibility = View.GONE
                    }
                    Player.STATE_READY -> {
                        pbMini.visibility = View.GONE
                        miniPV.visibility = View.VISIBLE
                        tvPlaceholder.visibility = View.GONE
                        ivIcon.visibility = View.GONE
                        tvLive.visibility = View.VISIBLE
                    }
                    else -> {}
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                pbMini.visibility = View.GONE
                miniPV.visibility = View.GONE
                tvPlaceholder.visibility = View.VISIBLE
                tvLive.visibility = View.GONE
            }
        })

        // Category adapter
        catAdapter = CategoryAdapter { cat ->
            selCat = cat.id
            catAdapter.setSelected(cat.id)
            filterChannels(et.text.toString())
        }
        rvCats.layoutManager = LinearLayoutManager(this)
        rvCats.adapter = catAdapter

        // Mini player click -> open fullscreen directly
        miniPV.setOnClickListener {
            channelAdapter.getCurrentList().firstOrNull {
                ApiClient.getLiveUrl(server, username, password, it.streamId, true) == currentMiniUrl
            }?.let { ch -> playFull(ch) }
        }
        tvPlaceholder.setOnClickListener {
            channelAdapter.getCurrentList().firstOrNull {
                ApiClient.getLiveUrl(server, username, password, it.streamId, true) == currentMiniUrl
            }?.let { ch -> playFull(ch) }
        }

        // Channel adapter
        channelAdapter = ChannelAdapter(this, emptyList(),
            onClick = { ch ->
                tvName.text = ch.name
                btnPlay.visibility = View.VISIBLE
                if (ch.quality.isNotEmpty()) {
                    tvQual.visibility = View.VISIBLE
                    tvQual.text = " ${ch.quality} "
                    tvQual.setTextColor(if (ch.isFHD) 0xFF8B5CF6.toInt() else 0xFFFF6B9D.toInt())
                    tvQual.setBackgroundColor(if (ch.isFHD) 0x228B5CF6.toInt() else 0x22FF6B9D.toInt())
                } else tvQual.visibility = View.GONE
                if (!ch.icon.isNullOrEmpty()) {
                    ivIcon.visibility = View.VISIBLE
                    Glide.with(this).load(ch.icon).into(ivIcon)
                } else ivIcon.visibility = View.GONE
                val url = ApiClient.getLiveUrl(server, username, password, ch.streamId, true)
                currentMiniUrl = url
                pbMini.visibility = View.VISIBLE
                tvPlaceholder.visibility = View.GONE
                tvLive.visibility = View.GONE
                miniPV.visibility = View.GONE
                miniPlayer?.setMediaItem(MediaItem.fromUri(url))
                miniPlayer?.prepare()
                miniPlayer?.play()
            },
            onDblClick = { ch -> playFull(ch) },
            onFav = { ch -> SessionManager.toggleFavorite(this, ch.streamId) }
        )
        rvCh.layoutManager = LinearLayoutManager(this)
        rvCh.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        rvCh.adapter = channelAdapter

        btnPlay.setOnClickListener {
            val ch = channelAdapter.getCurrentList().firstOrNull {
                ApiClient.getLiveUrl(server, username, password, it.streamId, true) == currentMiniUrl
            }
            ch?.let { playFull(it) }
        }

        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterChannels(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Load data
        thread {
            try {
                val cats = ApiClient.getLiveCategories(server, username, password)
                val chs  = ApiClient.getLiveStreams(server, username, password)
                allChannels = chs
                runOnUiThread {
                    pb.visibility = View.GONE
                    rvCh.visibility = View.VISIBLE
                    val list = mutableListOf(
                        Category("all", "📋 ALL  ${chs.size}"),
                        Category("fav", "⭐ Favoriten")
                    )
                    cats.forEach { c ->
                        val n = chs.count { it.categoryId == c.id }
                        list.add(Category(c.id, "${c.name}  $n"))
                    }
                    catAdapter.update(list)
                    filterChannels("")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    pb.visibility = View.GONE
                    Toast.makeText(this, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun filterChannels(q: String) {
        val favIds = SessionManager.getFavorites(this)
        val base = when (selCat) {
            "all" -> allChannels
            "fav" -> allChannels.filter { favIds.contains(it.streamId) }
            else  -> allChannels.filter { it.categoryId == selCat }
        }
        filtered = if (q.isEmpty()) base else base.filter { it.name.contains(q, true) }
        channelAdapter.update(filtered)
    }

    private fun playFull(ch: Channel) {
        miniPlayer?.pause()
        val urls  = ArrayList(filtered.map { ApiClient.getLiveUrl(server, username, password, it.streamId, true) })
        val names = ArrayList(filtered.map { it.name })
        val idx   = filtered.indexOfFirst { it.streamId == ch.streamId }
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putStringArrayListExtra("urls", urls)
            putStringArrayListExtra("names", names)
            putExtra("index", if (idx >= 0) idx else 0)
        })
    }

    override fun onResume()  { super.onResume();  miniPlayer?.play() }
    override fun onPause()   { super.onPause();   miniPlayer?.pause() }
    override fun onDestroy() { super.onDestroy(); miniPlayer?.release() }
}

class CategoryAdapter(private val onClick: (Category) -> Unit)
    : RecyclerView.Adapter<CategoryAdapter.VH>() {
    private var items = listOf<Category>()
    private var selId = "all"
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvCatName)
        val tvCount: TextView = v.findViewById(R.id.tvCatCount)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_category, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = items[pos]
        val parts = c.name.split("  ")
        h.tvName.text  = parts[0]
        h.tvCount.text = if (parts.size > 1) parts[1] else ""
        val sel = c.id == selId
        h.itemView.setBackgroundResource(if (sel) R.drawable.cat_selected_bg else android.R.color.transparent)
        h.tvName.setTextColor(if (sel) 0xFF8B5CF6.toInt() else 0xFF7C7CA0.toInt())
        h.tvName.textSize = if (sel) 13.5f else 13f
        h.itemView.setOnClickListener { onClick(c) }
    }
    fun update(list: List<Category>) { items = list; notifyDataSetChanged() }
    fun setSelected(id: String) { selId = id; notifyDataSetChanged() }
}

class ChannelAdapter(
    private val ctx: android.content.Context,
    private var items: List<Channel>,
    private val onClick: (Channel) -> Unit,
    private val onDblClick: (Channel) -> Unit,
    private val onFav: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {
    private var lastClickTime = 0L
    private var lastClickId = -1
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvNum: TextView     = v.findViewById(R.id.tvNum)
        val ivIcon: ImageView   = v.findViewById(R.id.ivIcon)
        val tvName: TextView    = v.findViewById(R.id.tvName)
        val tvSub: TextView     = v.findViewById(R.id.tvSub)
        val tvQuality: TextView = v.findViewById(R.id.tvQuality)
        val ivFav: ImageView    = v.findViewById(R.id.ivFav)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_channel, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val ch = items[pos]
        h.tvNum.text = "${pos + 1}"
        h.tvName.text = ch.name
        h.tvSub.text = ""
        if (ch.quality.isNotEmpty()) {
            h.tvQuality.visibility = View.VISIBLE
            h.tvQuality.text = ch.quality
            h.tvQuality.setTextColor(if (ch.isFHD) 0xFF8B5CF6.toInt() else 0xFFFF6B9D.toInt())
        } else h.tvQuality.visibility = View.GONE
        val isFav = SessionManager.isFavorite(ctx, ch.streamId)
        h.ivFav.setImageResource(if (isFav) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
        if (!ch.icon.isNullOrEmpty())
            Glide.with(ctx).load(ch.icon).placeholder(R.drawable.ic_logo).into(h.ivIcon)
        h.itemView.setOnClickListener {
            val now = System.currentTimeMillis()
            if (ch.streamId == lastClickId && now - lastClickTime < 500) onDblClick(ch)
            else onClick(ch)
            lastClickTime = now; lastClickId = ch.streamId
        }
        h.ivFav.setOnClickListener { onFav(ch); notifyItemChanged(pos) }
    }
    fun update(list: List<Channel>) { items = list; notifyDataSetChanged() }
    fun getCurrentList() = items
}
