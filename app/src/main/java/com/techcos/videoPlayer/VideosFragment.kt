package com.techcos.videoPlayer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.techcos.videoPlayer.databinding.FragmentVideosBinding


class VideosFragment : Fragment() {

    lateinit var adapter: VideoAdapter
    private lateinit var binding: FragmentVideosBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        requireContext().theme.applyStyle(MainActivity.themesList[MainActivity.themeIndex], true)
        val view = inflater.inflate(R.layout.fragment_videos, container, false)
        binding = FragmentVideosBinding.bind(view)
        binding.VideoRV.setHasFixedSize(true)
        binding.VideoRV.setItemViewCacheSize(10)
        binding.VideoRV.layoutManager = LinearLayoutManager(requireContext())
        adapter = VideoAdapter(requireContext(), MainActivity.videoList)
        binding.VideoRV.adapter = adapter
        binding.totalVideos.text = "Total Videos: ${MainActivity.videoList.size}"

        //for refreshing layout
        binding.root.setOnRefreshListener {
            MainActivity.videoList = getAllVideos(requireContext())
            adapter.updateList(MainActivity.videoList)
            binding.totalVideos.text = "Total Videos: ${MainActivity.videoList.size}"

            binding.root.isRefreshing = false
        }

        binding.nowPlayingLayout.setOnClickListener{
            val intent = Intent(requireContext(), PlayerActivity::class.java)
            intent.putExtra("class", "NowPlaying")
            startActivity(intent)
        }

        binding.nowPlayingBtn.setOnClickListener{
            val intent = Intent(requireContext(), PlayerActivity::class.java)
            intent.putExtra("class", "NowPlaying")
            startActivity(intent)
        }
        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.search_view, menu)
        val searchView = menu.findItem(R.id.searchView)?.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean = true

            override fun onQueryTextChange(newText: String?): Boolean {
                if(newText != null){
                    MainActivity.searchList = ArrayList()
                    for(video in MainActivity.videoList){
                        if(video.title.lowercase().contains(newText.lowercase()))
                            MainActivity.searchList.add(video)
                    }
                    MainActivity.search = true
                    adapter.updateList(searchList = MainActivity.searchList)
                }
                return true
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        if(PlayerActivity.position != -1) {
//            binding.nowPlayingBtn.visibility = View.VISIBLE
            binding.nowPlayingLayout.visibility = View.VISIBLE

            var pos = PlayerActivity.position
            binding.lastPlayvideoName.text = PlayerActivity.playerList[pos].title
            binding.duration.text = DateUtils.formatElapsedTime(PlayerActivity.playerList[pos].duration/1000)
            context?.let {
                Glide.with(it)
                    .asBitmap()
                    .load(PlayerActivity.playerList[pos].artUri)
                    .apply(RequestOptions().placeholder(R.mipmap.ic_video_player).centerCrop())
                    .into(binding.lastPlayImg)
            }




        }
//        if(MainActivity.adapterChanged) adapter.notifyDataSetChanged()
//        MainActivity.adapterChanged = false
    }

}