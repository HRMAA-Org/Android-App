package com.devsoc.hrmaa.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.devsoc.hrmaa.R
import com.devsoc.hrmaa.databinding.ActivityEcghomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

@SuppressLint("MissingPermission")
class ECGHome : AppCompatActivity() {

    private lateinit var binding: ActivityEcghomeBinding
    var apnaSocket: BluetoothSocket? = null
    var btDevice: BluetoothDevice? = null
    var apnaServerSocket: BluetoothServerSocket? = null

    var bluetoothAdapter: BluetoothAdapter? = null
    var inStream: InputStream? = null
    var outStream: OutputStream? = null

    val SELECT_DEVICE = 0
    val TAG = "Tag1"
    var currStr = ""
    var ECGDataList = CopyOnWriteArrayList<Int>()
    var timeStampList = CopyOnWriteArrayList<Long>()
    var rec = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_ecghome)

        //       asking for permissions
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        )

        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(
                this,
                "Bluetooth not available not this device",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnSelectDevice.setOnClickListener {
            val intent = Intent(this, AvailableDevicesActivity::class.java)
            startActivityForResult(intent, SELECT_DEVICE)
        }


        binding.btnPauseResume.setOnClickListener {
            rec = !rec
            if (rec) {
                binding.btnReconnect.performClick()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        if (requestCode == SELECT_DEVICE && resultCode == RESULT_OK) {
            val name = data?.getStringExtra("devName")
            val address = data!!.getStringExtra("devAddress")
            btDevice = bluetoothAdapter?.getRemoteDevice(address)
            if (btDevice == null) {
                Toast.makeText(this, "btDevice is null", Toast.LENGTH_SHORT).show()
            }
            Toast.makeText(this, name + "\n" + address, Toast.LENGTH_SHORT).show()
        }

        //connect to the device
        lifecycleScope.launch(Dispatchers.IO) {
            if (apnaServerSocket != null) {
                apnaServerSocket!!.close()
            }
            apnaSocket?.close()
            bluetoothAdapter?.cancelDiscovery()

            btDevice?.let {
                apnaSocket =
                    it.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                try {
                    apnaSocket?.connect()
                    Log.d("Log", apnaSocket.toString())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ECGHome,
                            "Connected to ${apnaSocket?.remoteDevice?.name} \n ${apnaSocket.toString()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ECGHome, e.message, Toast.LENGTH_SHORT).show()
                    }
                }

                inStream = apnaSocket?.inputStream
                outStream = apnaSocket?.outputStream

                try {
                    outStream?.write(0)
                    if (outStream == null) {
                        Log.d(TAG, "outStream is null")
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error occurred when sending data", e)
                }

                val reader = BufferedReader(InputStreamReader(inStream), 8000*1024)
                var beforeLoopTime = System.currentTimeMillis()
                rec = true
                val folder =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                try {
                    val fileName1 = "HR_${
                        SimpleDateFormat(" yyyy-MM-dd-HHmmssSSS", Locale.US)
                            .format(beforeLoopTime)
                    }.txt"
                    val file1 = File(folder, fileName1)
                    val fos1 = FileOutputStream(file1, true)

                    val fileName2 = "Time_${
                        SimpleDateFormat(" yyyy-MM-dd-HHmmssSSS", Locale.US)
                            .format(beforeLoopTime)
                    }.txt"
                    val file2 = File(folder, fileName2)
                    val fos2 = FileOutputStream(file2, true)

                    var prevTime = 0.toLong()
                    while (true) {
                        try {
                            currStr = reader.readLine()
                            withContext(Dispatchers.Main) {
                                val idxComma = currStr.indexOf(',')
                                val timeStr = try {
                                    currStr.substring(IntRange(0, idxComma - 1))
                                } catch (e: StringIndexOutOfBoundsException) {
                                    "-1"
                                }
                                val hrStr: String = try {
                                    currStr.substring(IntRange(idxComma + 1, currStr.length - 1))
                                } catch (e: java.lang.Exception) {
                                    "-1"
                                }

                                try {
                                    fos1.write("${hrStr.toInt()}, ".toByteArray() )
                                    try{
                                        fos2.write("${timeStr.toLong()}, ".toByteArray())
                                        prevTime = timeStr.toLong()
                                    }
                                    catch (e: java.lang.NumberFormatException){
                                        fos2.write("-1, ".toByteArray())
                                    }
                                }
                                catch (e: java.lang.NumberFormatException) {
                                    Log.d("Bad number : ", hrStr)
                                }
                            }
                        }  catch (e: IOException) {
                            Log.d(TAG, "Input stream was disconnected", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ECGHome, e.message, Toast.LENGTH_LONG).show()
                            }
                            break
                        }
                        if (!rec) {
                            runOnUiThread{
                                Toast.makeText(
                                    baseContext,
                                    "File saved at $folder",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            break
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this@ECGHome,
                        "Unable to export file, $e",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                try {
                    outStream?.write(0)
                    if (outStream == null) {
                        Log.d(TAG, "outStream is null")
                    } else {
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error occurred when sending data", e)
                }
            }
            if (btDevice == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ECGHome, "No Bluetooth Device Selected", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)

    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        apnaSocket?.close()
        bluetoothAdapter?.cancelDiscovery()
        bluetoothAdapter?.disable()
    }


    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        {

            if ((it[Manifest.permission.BLUETOOTH] == false
                        &&
                        (it[Manifest.permission.BLUETOOTH_CONNECT] == false
                                || it[Manifest.permission.BLUETOOTH_ADVERTISE] == false
                                || it[Manifest.permission.BLUETOOTH_SCAN] == false))
                || (it[Manifest.permission.ACCESS_COARSE_LOCATION] == false && it[Manifest.permission.ACCESS_FINE_LOCATION] == false)
            ) {
                Toast.makeText(this, "Please provide required permissions", Toast.LENGTH_SHORT)
                    .show()
                finish()
            } else {
                Toast.makeText(this, "Permissions granted successfully", Toast.LENGTH_SHORT).show()
                bluetoothAdapter?.enable()
            }
        }
}
