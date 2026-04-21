package com.streamvision.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.streamvision.app.R
import com.streamvision.app.data.api.ApiClient
import com.streamvision.app.data.models.Category
import com.streamvision.app.data.models.Channel
import com.streamvision.app.ui.live.LiveActivity
import com.streamvision.app.ui.player.PlayerActivity
import com.streamvision.app.ui.settings.SettingsActivity
import com.streamvision.app.utils.SessionManager
import kotlin.concurrent.thread

data class NavItem(val id: String, val icon: String, val name: String, var count: Int = 0)

class HomeActivity : AppCompatActivity() {
    private val server   get() = intent.getStringExtra("server") ?: ""
    private val username get() = intent.getStringExtra("username") ?: ""
    private val password get() = intent.getStringExtra("password") ?: ""
    private var allChannels   = listOf<Channel>()
    private var allCategories = listOf<Category>()
    private lateinit var navAdapter: NavAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val saved = SessionManager.load(this)
        findViewById<TextView>(R.id.tvSideUser).text   = "👤 $username"
        findViewById<TextView>(R.id.tvSideExpiry).text = "⏰ ${SessionManager.formatExpDate(saved?.userInfo?.expDate)}"

        findViewById<LinearLayout>(R.id.btnSideSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).apply {
                putExtra("server", server); putExtra("username", username); putExtra("password", password)
            })
        }

        navAdapter = NavAdapter { nav -> navAdapter.setSelected(nav.id); buildContent(nav.id) }
        val rvNav = findViewById<RecyclerView>(R.id.rvSideNav)
        rvNav.layoutManager = LinearLayoutManager(this)
        rvNav.adapter = navAdapter

        thread {
            try {
                val cats = ApiClient.getLiveCategories(server, username, password)
                val chs  = ApiClient.getLiveStreams(server, username, password)
                allCategories = cats; allChannels = chs
                runOnUiThread { setupNav(cats, chs) }
            } catch (e: Exception) {
                runOnUiThread {
                    findViewById<View>(R.id.llLoading).visibility = View.GONE
                    Toast.makeText(this, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupNav(cats: List<Category>, chs: List<Channel>) {
        val favIds = SessionManager.getFavorites(this)
        val navItems = mutableListOf(
            NavItem("fav",    "⭐", "Favoriten", favIds.size),
            NavItem("all",    "📡", "Alle Kanäle", chs.size),
            NavItem("recent", "🕐", "Zuletzt", 0)
        )
        cats.forEach { c -> navItems.add(NavItem(c.id, "▶", c.name, chs.count { it.categoryId == c.id })) }
        navAdapter.update(navItems)
        buildContent("all")
    }

    private fun buildContent(catId: String) {
        val loading = findViewById<View>(R.id.llLoading)
        val sv      = findViewById<ScrollView>(R.id.svContent)
        val ll      = findViewById<LinearLayout>(R.id.llContent)
        loading.visibility = View.GONE; sv.visibility = View.VISIBLE; ll.removeAllViews()

        val favIds = SessionManager.getFavorites(this)
        val filtered = when (catId) {
            "all"    -> allChannels
            "fav"    -> allChannels.filter { favIds.contains(it.streamId) }
            "recent" -> allChannels.take(20)
            else     -> allChannels.filter { it.categoryId == catId }
        }

        if (filtered.isEmpty()) {
            val tv = TextView(this).apply { text = "Keine Kanäle"; textSize = 16f
                setTextColor(0xFF7C7CA0.toInt()); gravity = android.view.Gravity.CENTER; setPadding(0, 80, 0, 0) }
            ll.addView(tv); return
        }

        addBanner(ll, filtered.first())

        if (catId != "fav") {
            val favChs = allChannels.filter { favIds.contains(it.streamId) }
            if (favChs.isNotEmpty()) addRow(ll, "⭐ Favoriten", favChs)
        }

        val hdChs = filtered.filter { it.isHD || it.isFHD }
        if (hdChs.isNotEmpty()) addRow(ll, "🔥 HD & 4K", hdChs)

        filtered.chunked(20).forEachIndexed { i, chunk ->
            val title = if (filtered.size <= 20) "📡 Kanäle" else "📡 Kanäle ${i*20+1}–${i*20+chunk.size}"
            addRow(ll, title, chunk)
        }
    }

    private fun addBanner(parent: LinearLayout, ch: Channel) {
        val v = layoutInflater.inflate(R.layout.layout_banner, parent, false)
        val ivBanner = v.findViewById<ImageView>(R.id.ivBanner)
        v.findViewById<TextView>(R.id.tvBannerName).text = ch.name
        v.findViewById<TextView>(R.id.tvBannerEpg).text  = "Live"
        val tvQual = v.findViewById<TextView>(R.id.tvBannerQuality)
        if (ch.quality.isNotEmpty()) {
            tvQual.visibility = View.VISIBLE; tvQual.text = " ${ch.quality} "
            tvQual.setTextColor(if (ch.isFHD) 0xFF8B5CF6.toInt() else 0xFFFF6B9D.toInt())
        }
        if (!ch.icon.isNullOrEmpty()) Glide.with(this).load(ch.icon).centerCrop().into(ivBanner)
        v.findViewById<TextView>(R.id.btnBannerPlay).setOnClickListener { playChannel(ch) }
        parent.addView(v)
    }

    private fun addRow(parent: LinearLayout, title: String, channels: List<Channel>) {
        val v = layoutInflater.inflate(R.layout.layout_row, parent, false)
        v.findViewById<TextView>(R.id.tvRowTitle).text = title
        v.findViewById<TextView>(R.id.tvRowCount).text = "${channels.size}"
        val rv = v.findViewById<RecyclerView>(R.id.rvRow)
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = ChannelCardAdapter(channels,
            onClick    = { ch -> playChannel(ch) },
            onLongClick = { ch ->
                SessionManager.toggleFavorite(this, ch.streamId)
                Toast.makeText(this, if (SessionManager.isFavorite(this, ch.streamId)) "⭐ Favorit hinzugefügt" else "Entfernt", Toast.LENGTH_SHORT).show()
            }
        )
        parent.addView(v)
    }

    private fun playChannel(ch: Channel) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", ApiClient.getLiveUrl(server, username, password, ch.streamId, true))
            putExtra("name", ch.name)
        })
    }
}

class NavAdapter(private val onClick: (NavItem) -> Unit) : RecyclerView.Adapter<NavAdapter.VH>() {
    private var items = listOf<NavItem>(); private var selId = "all"
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val vInd: View = v.findViewById(R.id.vIndicator)
        val tvIcon: TextView = v.findViewById(R.id.tvIcon)
        val tvName: TextView = v.findViewById(R.id.tvNavName)
        val tvCnt: TextView  = v.findViewById(R.id.tvNavCount)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_nav, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val n = items[pos]; h.tvIcon.text = n.icon; h.tvName.text = n.name
        h.tvCnt.text = if (n.count > 0) "${n.count}" else ""
        val sel = n.id == selId
        h.vInd.visibility = if (sel) View.VISIBLE else View.INVISIBLE
        h.tvName.setTextColor(if (sel) 0xFFF0F4FF.toInt() else 0xFF8892B0.toInt())
        h.itemView.setBackgroundColor(if (sel) 0x1A8B5CF6.toInt() else 0)
        h.itemView.setOnClickListener { onClick(n) }
    }
    fun update(list: List<NavItem>) { items = list; notifyDataSetChanged() }
    fun setSelected(id: String) { selId = id; notifyDataSetChanged() }
}

class ChannelCardAdapter(private val items: List<Channel>, private val onClick: (Channel) -> Unit, private val onLongClick: (Channel) -> Unit) : RecyclerView.Adapter<ChannelCardAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivThumb: ImageView = v.findViewById(R.id.ivThumb)
        val tvFall: TextView   = v.findViewById(R.id.tvFallback)
        val tvName: TextView   = v.findViewById(R.id.tvName)
        val tvQual: TextView   = v.findViewById(R.id.tvQuality)
        val tvFav: TextView    = v.findViewById(R.id.tvFav)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_channel_card, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val ch = items[pos]; h.tvName.text = ch.name
        if (!ch.icon.isNullOrEmpty()) { h.tvFall.visibility = View.GONE; Glide.with(h.ivThumb).load(ch.icon).centerInside().into(h.ivThumb) }
        else h.tvFall.visibility = View.VISIBLE
        if (ch.quality.isNotEmpty()) { h.tvQual.visibility = View.VISIBLE; h.tvQual.text = ch.quality
            h.tvQual.setTextColor(if (ch.isFHD) 0xFF8B5CF6.toInt() else 0xFFFF6B9D.toInt())
        } else h.tvQual.visibility = View.GONE
        h.tvFav.visibility = if (SessionManager.isFavorite(h.itemView.context, ch.streamId)) View.VISIBLE else View.GONE
        h.itemView.setOnClickListener { onClick(ch) }
        h.itemView.setOnLongClickListener { onLongClick(ch); true }
    }
}
