package com.streamvision.app.ui.series

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.streamvision.app.R
import com.streamvision.app.data.api.ApiClient
import com.streamvision.app.data.models.*
import com.streamvision.app.utils.DeviceUtils
import kotlin.concurrent.thread

class SeriesActivity : AppCompatActivity() {
    private var all = listOf<Series>()
    private var cats = listOf<Category>()
    private var selCat = "all"
    private lateinit var adapter: SeriesGridAdapter
    private var server = ""; private var username = ""; private var password = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series)
        server   = intent.getStringExtra("server") ?: ""
        username = intent.getStringExtra("username") ?: ""
        password = intent.getStringExtra("password") ?: ""

        val rv  = findViewById<RecyclerView>(R.id.rvSeries)
        val pb  = findViewById<ProgressBar>(R.id.progressBar)
        val et  = findViewById<EditText>(R.id.etSearch)
        val llC = findViewById<LinearLayout>(R.id.llCategories)

        // ✅ Auto grid columns based on device
        val cols = DeviceUtils.gridColumns(this)
        rv.layoutManager = GridLayoutManager(this, cols)

        adapter = SeriesGridAdapter(DeviceUtils.isTV(this)) { series ->
            android.app.AlertDialog.Builder(this)
                .setTitle(series.name)
                .setMessage(buildString {
                    series.releaseDate?.take(4)?.let { append("📅 $it\n") }
                    series.genre?.let { append("🎭 $it\n") }
                    series.plot?.let { append("\n$it") }
                })
                .setPositiveButton("Schließen", null)
                .show()
        }
        rv.adapter = adapter

        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filter(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        thread {
            try {
                cats = ApiClient.getSeriesCategories(server, username, password)
                all  = ApiClient.getSeries(server, username, password)
                runOnUiThread {
                    pb.visibility = View.GONE
                    rv.visibility = View.VISIBLE
                    addChip(llC, "Alle", "all", true)
                    cats.forEach { addChip(llC, it.name, it.id, false) }
                    adapter.update(all)
                }
            } catch (e: Exception) {
                runOnUiThread { pb.visibility = View.GONE }
            }
        }
    }

    private fun addChip(c: LinearLayout, name: String, id: String, sel: Boolean) {
        val isTV = DeviceUtils.isTV(this)
        val tv = TextView(this).apply {
            text = name
            textSize = if (isTV) 15f else 12f
            setPadding(if (isTV) 32 else 24, 0, if (isTV) 32 else 24, 0)
            setTextColor(if (sel) 0xFFFFFFFF.toInt() else 0xFF8892B0.toInt())
            setBackgroundResource(if (sel) R.drawable.chip_selected else R.drawable.chip_normal)
            val h = if (isTV) dp(40) else dp(32)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, h)
            lp.marginEnd = dp(8); layoutParams = lp; gravity = Gravity.CENTER
            setOnClickListener {
                selCat = id
                for (i in 0 until c.childCount) {
                    (c.getChildAt(i) as TextView).apply {
                        setBackgroundResource(R.drawable.chip_normal)
                        setTextColor(0xFF8892B0.toInt())
                    }
                }
                setBackgroundResource(R.drawable.chip_selected)
                setTextColor(0xFFFFFFFF.toInt())
                filter(null)
            }
        }
        c.addView(tv)
    }

    private fun filter(q: String?) {
        val base = if (selCat == "all") all else all.filter { it.categoryId == selCat }
        adapter.update(if (q.isNullOrEmpty()) base else base.filter { it.name.contains(q, true) })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

class SeriesGridAdapter(
    private val isTV: Boolean,
    private val onClick: (Series) -> Unit
) : RecyclerView.Adapter<SeriesGridAdapter.VH>() {

    private var items = listOf<Series>()

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val iv: ImageView      = v.findViewById(R.id.ivPoster)
        val tvTitle: TextView  = v.findViewById(R.id.tvTitle)
        val tvYear: TextView   = v.findViewById(R.id.tvYear)
        val tvRating: TextView = v.findViewById(R.id.tvRating)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_media, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val s = items[pos]
        h.tvTitle.text  = s.name
        h.tvYear.text   = s.releaseDate?.take(4) ?: ""
        h.tvRating.text = if (s.rating > 0) "⭐ ${"%.1f".format(s.rating)}" else ""

        // ✅ Larger text for TV
        if (isTV) {
            h.tvTitle.textSize  = 15f
            h.tvYear.textSize   = 13f
            h.tvRating.textSize = 13f
        }

        Glide.with(h.iv).load(s.cover).centerCrop()
            .placeholder(android.R.color.darker_gray).into(h.iv)

        h.itemView.setOnClickListener { onClick(s) }
    }

    fun update(list: List<Series>) { items = list; notifyDataSetChanged() }
}
