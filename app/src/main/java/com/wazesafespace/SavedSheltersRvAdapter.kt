package com.wazesafespace

import android.annotation.SuppressLint
import android.icu.util.Calendar
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wazesafespace.databinding.ShetlerItemBinding

class SavedSheltersRvAdapter(
    private val savedShelters: List<SavedShelter>,
    private val actions: SavedShelterActions
) : RecyclerView.Adapter<SavedSheltersRvAdapter.SavedSheltersViewHolder>() {


    interface SavedShelterActions {
        fun addReport(savedShelter: SavedShelter)
    }

    inner class SavedSheltersViewHolder(val binding: ShetlerItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(savedShelter: SavedShelter) {
            binding.addressTv.text = "Address: ${savedShelter.address}"
            binding.dateTv.text = formatDate(savedShelter.saveDate)

            if (savedShelter.reportAdded) {
                binding.addReportBtn.isEnabled = false
            } else {
                binding.addReportBtn.setOnClickListener {
                    actions.addReport(savedShelter)
                }
            }
        }

        private fun formatDate(saveDate: Long): CharSequence? {
            val c = Calendar.getInstance()
            c.timeInMillis = saveDate
            val day = c.get(Calendar.DAY_OF_MONTH)
            val month = c.get(Calendar.MONTH)
            val year = c.get(Calendar.YEAR)
            val hour = c.get(Calendar.HOUR_OF_DAY)
            val minute = c.get(Calendar.MINUTE)

            val dayFormatted = if (day < 10) {
                "0${day}"
            } else {
                day.toString()
            }
            val monthFormatted = if (month < 10) {
                "0${month}"
            } else {
                day.toString()
            }

            val hourFormatted = if (hour < 10) {
                "0${hour}"
            } else {
                hour.toString()
            }
            val minuteFormatted = if (minute < 10) {
                "0${minute}"
            } else {
                minute.toString()
            }
            return "${dayFormatted}/${monthFormatted}/${year} ${hourFormatted}:${minuteFormatted}"
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedSheltersViewHolder {
        val binding = ShetlerItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SavedSheltersViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SavedSheltersViewHolder, position: Int) {
        val shelter = savedShelters[position]
        holder.bind(shelter)
    }

    override fun getItemCount(): Int = savedShelters.size


    @SuppressLint("NotifyDataSetChanged")
    fun refresh() {
        notifyDataSetChanged()
    }
}