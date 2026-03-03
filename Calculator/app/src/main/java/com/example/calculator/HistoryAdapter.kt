package com.example.calculator

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(private val items: List<HistoryItem>) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvExpression: TextView = itemView.findViewById(R.id.tv_expression)
        private val tvResult: TextView = itemView.findViewById(R.id.tv_result)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)

        fun bind(item: HistoryItem) {
            tvExpression.text = item.expression
            tvResult.text = "= ${item.result}"

            val dateFormat = SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault())
            tvTime.text = dateFormat.format(Date(item.timestamp))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}