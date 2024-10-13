package com.wazesafespace


import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.wazesafespace.databinding.SheltersBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Shelters : Fragment(R.layout.clean_fragment), SavedSheltersRvAdapter.SavedShelterActions {


    private var _binding: SheltersBinding? = null
    private val binding: SheltersBinding get() = _binding!!


    private lateinit var rvAdapter: SavedSheltersRvAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val shelters = ShelterUtils.getMyShelters()
                withContext(Dispatchers.Main) {
                    rvAdapter = SavedSheltersRvAdapter(shelters, this@Shelters)
                    binding.rvShelters.adapter = rvAdapter
                }
            } catch (e: Exception) {
                Log.d("Shelters exception", e.message.toString())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun addReport(savedShelter: SavedShelter) {
        ShelterUtils.addShelterReport(savedShelter, hasStairs = true) {
            rvAdapter.refresh()
        }
    }
}
