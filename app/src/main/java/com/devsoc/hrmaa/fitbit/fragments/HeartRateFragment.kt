package com.devsoc.hrmaa.fitbit.fragments

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.devsoc.hrmaa.R
import com.devsoc.hrmaa.databinding.FragmentHeartRateBinding
import com.devsoc.hrmaa.fitbit.adapters.HeartRateAdapter
import com.devsoc.hrmaa.fitbit.dataclasses.*
import com.devsoc.hrmaa.fitbit.interfaces.RestApi
import com.devsoc.hrmaa.fitbit.objects.ServiceBuilder
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class HeartRateFragment : Fragment() {
    private lateinit var binding: FragmentHeartRateBinding
    private val clientId: String = "238QCY"
    private val redirectUri: String = "hrmaa://www.example.com/getCode"
    private val fStore = FirebaseFirestore.getInstance()
    private val cRef = fStore.collection("oauth")
    private val dRef = cRef.document("test")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_heart_rate, container, false)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sharedPreference =
            activity?.getSharedPreferences("PREFERENCE_NAME", Context.MODE_PRIVATE)
        val code: String = sharedPreference?.getString("userId", null)!!
        val authInfo = AuthInfo(clientId, "authorization_code", redirectUri, code)

        dRef.get().addOnCompleteListener {task ->
            if(task.isSuccessful){
                val doc = task.result
                if(doc.exists()){
                    dRef.addSnapshotListener { value, _ ->
                        val time = value!!.getLong("date")!!
                        //check if access token has expired and refresh if expired
                        if (Date().time - time < 28800000) {
                            val accToken = value.getString("access_token")!!
                            getHeartRateSeries(accToken, "2019-01-01", "2020-01-01")
                        } else {
                            val refToken = value.getString("refresh_token")!!
                            refresh(refToken, authInfo)
                        }
                    }
                }
                else {
                    getTokenInfo(authInfo)
                }
            }
        }
    }

    private fun getTokenInfo(authInfo: AuthInfo) {
        val retrofit = ServiceBuilder.buildService(RestApi::class.java)
        retrofit.getTokenInfo(
            authInfo.clientId,
            authInfo.grant_type,
            authInfo.redirect_uri,
            authInfo.code
        ).enqueue(
            object : Callback<TokenData> {
                override fun onFailure(call: Call<TokenData>, t: Throwable) {
                    Log.d("Service", t.message + "")
                }

                override fun onResponse(call: Call<TokenData>, response: Response<TokenData>) {
                    val tokenData = response.body()
                    Log.d("Access Response Code", "$response")
                    if (tokenData != null && response.raw().code == 200) {
                        val accessToken = tokenData.access_token
                        val refreshToken = tokenData.refresh_token
                        val uid = tokenData.user_id
                        val timestamp = hashMapOf(
                            "date" to Date().time,
                            "access_token" to accessToken,
                            "refresh_token" to refreshToken,
                            "uid" to uid
                        )
                        dRef.set(timestamp)
                        getHeartRateSeries(accessToken, "2019-01-01", "2020-01-01")
                    }
                }
            }
        )
    }

    private fun refresh(refreshToken: String, authInfo: AuthInfo) {
        val retrofit = ServiceBuilder.buildService(RestApi::class.java)
        retrofit.refresh(
            authInfo.clientId,
            "refresh_token",
            authInfo.redirect_uri,
            refreshToken
        ).enqueue(
            object : Callback<TokenData> {
                override fun onFailure(call: Call<TokenData>, t: Throwable) {
                    Log.d("Service", t.message + "")
                }

                override fun onResponse(call: Call<TokenData>, response: Response<TokenData>) {
                    val tokenData = response.body()
                    Log.d("Refresh", "${response.code()}")
                    if (tokenData != null) {
                        val accessToken = tokenData.access_token
                        val newRefreshToken = tokenData.refresh_token
                        val uid = tokenData.user_id
                        val timestamp = hashMapOf(
                            "date" to Date().time,
                            "access_token" to accessToken,
                            "refresh_token" to newRefreshToken,
                            "uid" to uid
                        )
                        dRef.set(timestamp)
                        getHeartRateSeries(accessToken, "2019-01-01", "2020-01-01")
                    }
                }
            }
        )
    }

    private fun getHeartRateSeries(accessToken: String, startDate: String, endDate: String){
        val headerMap = mutableMapOf<String, String>()
        headerMap["authorization"] = "Bearer $accessToken"

        val retrofit = ServiceBuilder.buildService(RestApi::class.java)
        retrofit.getHeartRateSeries(headerMap, startDate, endDate).enqueue(
            object : Callback<HeartRateSeries> {
                override fun onFailure(call: Call<HeartRateSeries>, t: Throwable) {
                    Log.d("Service", t.message + "")
                }

                override fun onResponse(call: Call<HeartRateSeries>, response: Response<HeartRateSeries>) {
                    val heartRateData = response.body()
                    Log.d("Heart Rate Response Code", "".plus(response.raw().code))
                    if (response.raw().code == 200 && heartRateData != null) {
                        val activitiesHeart = heartRateData.activities_heart
                        if(activitiesHeart == null) {
                            val zones1 = mutableListOf(
                                HeartRateZone(1100.0, 120, 90, 30, "Test1"),
                                HeartRateZone(900.0, 90, 60, 15, "Test2")
                            )
                            val zones2 = mutableListOf(
                                CustomHeartRateZone(1100.0, 120, 90, 30, "Custom1"),
                                CustomHeartRateZone(1100.0, 120, 90, 30, "Custom2")
                            )
                            val activities = mutableListOf(
                                ActivitiesHeart("2023-01-31", Value(zones2, zones1, 72)),
                                ActivitiesHeart("2023-01-30", Value(zones2, zones1, 85)),
                                ActivitiesHeart("2023-01-29", Value(zones2, zones1, 65)),
                                ActivitiesHeart("2023-01-28", Value(zones2, zones1, 70))
                            )
                            val adapter = HeartRateAdapter(activities)
                            binding.noDataTvHrf.visibility = View.INVISIBLE
                            binding.heartRateRvHrf.apply {
                                visibility = View.VISIBLE
                                this.adapter = adapter
                                layoutManager = LinearLayoutManager(context)
                            }
                            adapter.onItemClick = {
                                val action = HeartRateFragmentDirections.actionHeartRateFragmentToHeartRateZonesFragment(it)
                                findNavController().navigate(action)
                            }
                        }
                        else {
                            binding.heartRateRvHrf.visibility = View.INVISIBLE
                            binding.noDataTvHrf.visibility = View.VISIBLE
                        }
                    } else {
                        binding.heartRateRvHrf.visibility = View.INVISIBLE
                        binding.noDataTvHrf.visibility = View.VISIBLE
                        Log.d("Heart Rate Response", response.raw().message)
                    }
                }
            }
        )
    }
}