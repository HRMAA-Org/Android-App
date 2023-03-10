package com.devsoc.hrmaa.fitbit.fragments

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.devsoc.hrmaa.R
import com.devsoc.hrmaa.databinding.FragmentEcgDataBinding
import com.devsoc.hrmaa.fitbit.adapters.EcgAdapter
import com.devsoc.hrmaa.fitbit.dataclasses.*
import com.devsoc.hrmaa.fitbit.interfaces.RestApi
import com.devsoc.hrmaa.fitbit.objects.ServiceBuilder
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class EcgDataFragment : Fragment() {
    private lateinit var binding: FragmentEcgDataBinding
    private val clientId: String = "238QCY"
    private val redirectUri: String = "hrmaa://www.example.com/getCode"
    private val fStore = FirebaseFirestore.getInstance()
    private val cRef = fStore.collection("oauth")
    private val dRef = cRef.document("test")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_ecg_data, container, false)
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
                            getEcgInfo(accToken)
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

                        getEcgInfo(accessToken)
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
                    Log.d("Service", t.message.toString())
                }

                override fun onResponse(call: Call<TokenData>, response: Response<TokenData>) {
                    val tokenData = response.body()
                    Log.d("Refresh", "${response.raw().code}")
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
                        getEcgInfo(accessToken)
                    }
                }
            }
        )
    }

    fun getEcgInfo(accessToken: String) {
        val headerMap = mutableMapOf<String, String>()
        headerMap["authorization"] = "Bearer $accessToken"

        val retrofit = ServiceBuilder.buildService(RestApi::class.java)
        retrofit.getEcgData(headerMap).enqueue(
            object : Callback<EcgData> {
                override fun onFailure(call: Call<EcgData>, t: Throwable) {
                    Log.d("Service", t.message.toString())
                }

                override fun onResponse(call: Call<EcgData>, response: Response<EcgData>) {
                    val ecgData = response.body()
                    Log.d("ECG Response Code", "${response.raw().code}")
                    if (response.raw().code == 200 && ecgData != null) {
                        val ecgReadings = ecgData.ecgReadings
                        if(ecgReadings.isEmpty()){
                            binding.dataTvEdf.text = ""
                            val ecg = mutableListOf<EcgReading>(
                                EcgReading(69, "XYZ", "","", 1, 100, "","",10000, "2023-01-31 10pm",
                                    mutableListOf<Int>(0))
                            )
                            val adapter = EcgAdapter(ecg)
                            binding.ecgRvEdfg.apply {
                                this.adapter = adapter
                                layoutManager = LinearLayoutManager(context)
                            }
                            adapter.onItemClick = {
                                findNavController().navigate(R.id.action_fitbitDataFragment_to_ecgGraphFragment)
                            }
                        }
                        else {
                            binding.dataTvEdf.text = "No ECG Data found!"
                        }
                    } else {
                        Log.d("ECG Response", response.raw().message)
                    }
                }
            }
        )
    }

}