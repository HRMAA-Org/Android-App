package com.devsoc.hrmaa


import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.databinding.DataBindingUtil
import androidx.health.connect.client.HealthConnectClient
import com.devsoc.hrmaa.bluetooth.ECGHome
import com.devsoc.hrmaa.databinding.ActivityMainBinding
import com.devsoc.hrmaa.fitbit.FitbitActivity
import com.devsoc.hrmaa.healthConnect.HealthConnectActivity
import com.devsoc.hrmaa.ppg.PPGActivity
import com.devsoc.hrmaa.wlan.ECGActivity


class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        binding.hcCvMa.setOnClickListener {
            //launch only if Health Connect is installed on device
            if (HealthConnectClient.isAvailable(this)) {
                startActivity(Intent(this, HealthConnectActivity::class.java))
            } else {
                Toast.makeText(
                    this,
                    "Please install the Google Health Connect App first.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.fitbitCvMa.setOnClickListener {
            startActivity(Intent(this, FitbitActivity::class.java))
        }
        binding.ppgCvMa.setOnClickListener {
            startActivity(Intent(this, PPGActivity::class.java))
        }
        binding.ecgCvMa.setOnClickListener{
            val consentDialog = AlertDialog.Builder(this)
                .setTitle("DISCLAIMER")
                .setMessage(" By using this heart rate monitoring and analysis application, you confirm your good health, full consciousness, and consent for measurement. This app is for experimental purposes only and not for medical use. Results are for informational purposes and should not replace professional medical advice. Accuracy may vary due to device and user factors. Consult a healthcare professional for concerns.")
                .setPositiveButton("Accept") { dialogInterface, i ->
                    startActivity(Intent(this, ECGActivity::class.java))
                }
                .setNegativeButton("Deny") {_,_ ->
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                }
            consentDialog.show()
        }

    }

}