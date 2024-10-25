package com.skz.microphone

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.mediarouter.media.MediaRouter.RouteInfo

class AudioRouteAdapter(context: Context, routes: List<RouteInfo>) : ArrayAdapter<RouteInfo>(context, 0, routes) {

    private var routesList: List<RouteInfo> = routes

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_spinner_item, parent, false)
        val route = getItem(position)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = route?.name
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_spinner_dropdown_item, parent, false)
        val route = getItem(position)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = route?.name
        return view
    }

    fun updateRoutes(newRoutes: List<RouteInfo>) {
        routesList = newRoutes
        clear()
        addAll(newRoutes)
        notifyDataSetChanged()
    }
}
