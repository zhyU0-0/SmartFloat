package com.zyy.smartfloat.unity.flagment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.zyy.smartfloat.R
import com.unity3d.player.UnityPlayer


/**
 * A simple [Fragment] subclass.
 * Use the [MyUnityFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MyUnityFragment : Fragment() {
    private var unityPlayer: UnityPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unityPlayer = UnityPlayer(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = unityPlayer?.view
        (view?.parent as? ViewGroup)?.removeView(view)
        view?.viewTreeObserver?.addOnWindowFocusChangeListener { hasFocus ->
            unityPlayer?.windowFocusChanged(hasFocus)
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        unityPlayer?.resume()
    }

    override fun onPause() {
        super.onPause()
        unityPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        unityPlayer?.quit()
    }

}