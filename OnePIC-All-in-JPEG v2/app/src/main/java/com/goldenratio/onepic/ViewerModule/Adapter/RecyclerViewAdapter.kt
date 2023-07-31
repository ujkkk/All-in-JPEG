package com.goldenratio.onepic.ViewerModule.Adapter

import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentViewHolder
import com.bumptech.glide.Glide
import com.goldenratio.onepic.ImageToolModule
import com.goldenratio.onepic.R
import com.goldenratio.onepic.ViewerModule.Fragment.BasicViewerFragment

class RecyclerViewAdapter(var fragment: Fragment, var imageList: List<String>, var layoutManager: LinearLayoutManager) :
    RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {


    fun updateData(newData: List<String>) {
        imageList = newData
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.recyclerview_item, parent, false)
        return ViewHolder(view)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val imageUrl = imageList[position]
        Log.d("이걸 확인해야함 position:${position} - ",""+imageUrl)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            Glide.with(holder.itemView)
                .load(imageUrl)
                .into(holder.imageView)
        }
        else {
            Glide.with(holder.itemView)
                .load(ImageToolModule().getUriFromPath(fragment.requireContext(),imageUrl))
                .into(holder.imageView)
        }


        holder.imageView.setOnClickListener{
            BasicViewerFragment.currentPosition = position
            BasicViewerFragment.centerItemPosition = position
            BasicViewerFragment.isClickedRecyclerViewImage.value = true
        }

    }

    override fun getItemCount(): Int {
        return imageList.size
    }

}