package com.example.xray

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

object HistoryStore {

    data class Entry(val time: Long, val script: String, val text: String)

    private const val KEY = "items"
    private const val MAX = 20

    private fun store(c: Context) =
        c.getSharedPreferences("xray_history", Context.MODE_PRIVATE)

    private fun settings(c: Context) =
        c.getSharedPreferences("xray", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = settings(c).getBoolean("history", true)

    fun setEnabled(c: Context, on: Boolean) {
        settings(c).edit().putBoolean("history", on).apply()
    }

    fun load(c: Context): MutableList<Entry> {
        val out = ArrayList<Entry>()
        val raw = store(c).getString(KEY, null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Entry(o.getLong("t"), o.getString("s"), o.getString("x")))
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun save(c: Context, list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("t", e.time)
            o.put("s", e.script)
            o.put("x", e.text)
            arr.put(o)
        }
        store(c).edit().putString(KEY, arr.toString()).apply()
    }

    fun addIfEnabled(c: Context, r: OcrResult) {
        if (!enabled(c)) return
        val text = r.allText()
        if (text.isBlank()) return
        val list = load(c)
        if (list.isNotEmpty() && list[0].text == text) return
        list.add(0, Entry(System.currentTimeMillis(), r.script, text))
        while (list.size > MAX) {
            list.removeAt(list.size - 1)
        }
        save(c, list)
    }

    fun delete(c: Context, time: Long) {
        val list = load(c)
        val kept = ArrayList<Entry>()
        for (e in list) {
            if (e.time != time) kept.add(e)
        }
        save(c, kept)
    }

    fun clear(c: Context) {
        store(c).edit().remove(KEY).apply()
    }
}

object Sharer {
    fun send(c: Context, text: String) {
        try {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, text)
            val chooser = Intent.createChooser(i, "Share text")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(chooser)
        } catch (e: Exception) {
        }
    }
}