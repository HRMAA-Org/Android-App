package com.devsoc.hrmaa.wlan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.format.Formatter
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.devsoc.hrmaa.R
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private val RASPI_IP_OFFSET = 1

class ECGActivity : AppCompatActivity(), InternetConnectionCallback {
    private lateinit var btn : Button
    private lateinit var etIPaddr : EditText
    private lateinit var tvServerMsg: TextView
    private lateinit var data_time: ArrayList<Long>
    private lateinit var data_read: ArrayList<Int>
    private lateinit var time_stamp: String
    private lateinit var connection: CardView
    private lateinit var connText: TextView
//    private lateinit var py: Python
//    private lateinit var module :PyObjectF
//    lateinit var pb:ProgressBar
    private lateinit var etTimeInt: EditText
    val EOF = "</HRMAA>"
    val folder =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected
    }

    private val PERMISSIONS_ABOVE_Q = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        // Add other permissions as needed for Android 10 or higher
    )

    private val PERMISSIONS_BELOW_Q =
        arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        )

    override fun onDestroy() {
        super.onDestroy()
        InternetConnectionObserver.unRegister()
    }

    override fun onConnected() {
        runOnUiThread {
            connection.setCardBackgroundColor(Color.GREEN)
            connText.text = "Connected"
            Toast.makeText(this, "Internet Connection Resume", Toast.LENGTH_SHORT).show()
        }
        Log.d("Internet", "connected")
    }

    override fun onDisconnected() {
        runOnUiThread {
            connection.setCardBackgroundColor(Color.RED)
            connText.text = "Not Connected"
            Toast.makeText(this, "Internet Connection Lost", Toast.LENGTH_SHORT).show()
        }
        Log.d("Internet", "disconnected")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ecgactivity)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestPermissionLauncher.launch( PERMISSIONS_ABOVE_Q)
        } else {
            requestPermissionLauncher.launch( PERMISSIONS_BELOW_Q)
        }

        btn = findViewById(R.id.btn)
        etIPaddr = findViewById(R.id.etIPAdress)
//        pb = findViewById(R.id.pbLoading)
        data_time = arrayListOf()
        data_read = arrayListOf()
        etTimeInt = findViewById(R.id.etTimeInterval)
        val gender = findViewById<Spinner>(R.id.gender_spin_ea)
        val act = findViewById<Spinner>(R.id.act_spin_ea)
        val genderAdapter = ArrayAdapter.createFromResource(this, R.array.gender, android.R.layout.simple_spinner_item)
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gender.adapter = genderAdapter
        val actAdapter = ArrayAdapter.createFromResource(this, R.array.activity, android.R.layout.simple_spinner_item)
        actAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        act.adapter = actAdapter

//        InternetConnectionObserver
//            .instance(this)
//            .setCallback(this)
//            .register()

        connection = findViewById<CardView>(R.id.connection_cv_ea)
        connText = findViewById<TextView>(R.id.conn_tv_ea)
        val connectivityManager =
            this.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        if(connectivityManager.activeNetworkInfo?.isConnected == true){
            connection.setCardBackgroundColor(Color.GREEN)
            connText.text = "Connected"
        }


//        pb.visibility = View.INVISIBLE

        if( !isNetworkAvailable()){

        }

        val graphView = findViewById<GraphView>(R.id.ecg_gv_egf)
        val coords = findViewById<TextView>(R.id.coords_tv_egf)

//        val voltages = intArrayOf(272,286,314,289,289,296,658,184,305,324,344,402,455,467,339,274,270,277,281,288,290,305,298,305,328,312,316,292,662,345,328,352,388,431,463,407,287,263,259,268,269,281,285,283,299,316,299,301,299,664,145,307,323,360,403,457,455,320,257,252,261,269,276,282,294,280,297,316,308,288,297,628,218,322,340,361,401,462,480,348,263,260,275,273,283,291,286,290,301,327,296,293,290,665,256,325,338,374,423,479,445,313,261
//        )
//        val times = intArrayOf(12800,12832,12865,12897,12930,12962,12995,13028,13060,13092,13125,13158,13190,13222,13255,13288,13321,13352,13385,13418,13451,13483,13515,13548,13581,13614,13645,13678,13711,13744,13775,13808,13841,13874,13906,13938,13971,14004,14036,14068,14101,14134,14167,14199,14231,14264,14297,14329,14361,14394,14427,14459,14492,14524,14557,14589,14622,14654,14687,14720,14752,14785,14817,14850,14882,14915,14947,14980,15012,15045,15078,15110,15142,15175,15208,15240,15272,15305,15338,15371,15403,15435,15468,15501,15533,15565,15598,15631,15664,15695,15728,15761,15794,15826,15858,15891,15924,15956,15988,16021,16054,16087,16119
//        )
//
//        val dataPoints = mutableListOf<DataPoint>()
//        for(i in 0..voltages.size-1){
//            dataPoints.add(DataPoint((times[i]-times[0]).toDouble(), voltages[i].toDouble()))
//        }
//
//        val series: LineGraphSeries<DataPoint> = LineGraphSeries(dataPoints.toTypedArray())

        graphView.animate()
        graphView.viewport.isScrollable = true
        graphView.viewport.isScalable = true
        graphView.viewport.setScalableY(true)
        graphView.viewport.setScrollableY(true)
        graphView.viewport.isYAxisBoundsManual = true
        graphView.viewport.setMaxX(1000.0)
        graphView.viewport.setMaxY(1.0)
        graphView.viewport.setMinY(0.0)

        lifecycleScope.launch(Dispatchers.Default){
//            py = Python.getInstance()
//            module = py.getModule("heartpy_script")

            val wm = this@ECGActivity.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ip: String = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
            val parts: MutableList<String> =  ip.split(".").toMutableList()
            // Replace the last element with the new integer value
            Log.d("TAG", parts.toString() )
            parts[ parts.size - 1] = RASPI_IP_OFFSET.toString()
            // Join the elements back into a string
            val server_ip = java.lang.String.join(".", parts)
            this@ECGActivity.runOnUiThread(Runnable {
                etIPaddr.setText(server_ip)
            })
        }

        btn.setOnClickListener {
            //can't run IO calls in main thread because of os.network rules
            lifecycleScope.launch(Dispatchers.IO) {

                val name = findViewById<EditText>(R.id.name_tie_ea).text
                val age = findViewById<EditText>(R.id.age_tie_ea).text
                val gender = findViewById<Spinner>(R.id.gender_spin_ea).selectedItem.toString()
                val activity = findViewById<Spinner>(R.id.act_spin_ea).selectedItem.toString()
                var act = "1"
                if(activity.equals("Resting")){
                    act = "1"
                }
                if(activity.equals("Walking")){
                    act = "1"
                }
                if(activity.equals("Pacing")){
                    act = "1"
                }
                if(activity.equals("Climbing Stairs")){
                    act = "1"
                }


                var fileName1 = "HR_${name}_${age}_${gender}_${act}_${
                    SimpleDateFormat(" yyyy-MM - dd - HH_mm_ss", Locale.US)
                        .format(System.currentTimeMillis())
                }.txt"
//                Log.d("Filename", "HR_${name}_${age}_${gender}_$act")
//                fileName1 = "HR_${name}_${age}_${gender}_$act"

                val file1 = File(folder, fileName1)
                val fileBuffWrit =  FileOutputStream(file1, true).bufferedWriter()

                Log.d("IP addr",etIPaddr.text.toString())

                try{
                    var clientSocket = Socket(etIPaddr.text.toString(), 80)
                    var time = Integer.parseInt(etTimeInt.text.toString())
                    //this automatically binds to the server, no need to send separate connect request.
                    /*
                    IMP all write and read calls in TCP sockets are blocking
                    see: https://stackoverflow.com/questions/10574596/is-an-outputstream-in-java-blocking-sockets
                     */
                    val buffRead = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                    val buffWrit = BufferedWriter(OutputStreamWriter(clientSocket.getOutputStream()))

                    val jsonObj = getCommandJSON("send_for_time", time, "One off", Date().time.toString())
                    Log.d("JSON string",jsonObj.toString() )
                    buffWrit.write(jsonObj.toString())
                    /*
                    Must flush
                    While you are trying to write data to a Stream using the BufferedWriter object, after invoking the write() method the data will be buffered initially, nothing will be printed. The flush() method is used to push the contents of the buffer to the underlying Stream.
                     */
                    buffWrit.flush()

                    receiveForTime( buffRead, fileBuffWrit, time ,"One off", "All in smoke" )
                    buffWrit.write(getCommandJSON("stop", time, "One off", Date().time.toString()).toString())
                    buffWrit.flush()
                    val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 2000)
                    buffRead.close()
                    buffWrit.close()
                    clientSocket.close()
                    fileBuffWrit.close()

                    this@ECGActivity.runOnUiThread(Runnable {
                        Toast.makeText(this@ECGActivity, "Done with transmission, new file ${fileName1} created", Toast.LENGTH_LONG).show()
//                        pb.visibility =View.INVISIBLE
                    })
                    Log.d("readSuccessful","Read success")
                }
                catch (e : IOException){
                    Log.e("Error", e.toString());
                }
            }

        }

        findViewById<Button>(R.id.sendData).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/forms/d/e/1FAIpQLSckn4FL86qYmaHa6Odko-nqca86k9BSAllGOblaFQfQ52qJvA/viewform?usp=sf_link"))
            startActivity(intent)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        {
            Log.d("Permissions", it.toString())
            for( perm in PERMISSIONS_ABOVE_Q){
                if( it[perm] == false){
                    Toast.makeText(this,"Please provide required permissions", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
        }

    private fun receiveForTime( buffRead: BufferedReader, fileBuffWriter: BufferedWriter, time_s : Int, header : String, footer : String) : Int{
        var fileDat : String = ""
        var currStr : String? = ""
        var burstStr: String = ""
        var times = mutableListOf<Int>();
        var hr = mutableListOf<Double>();
        val graphView = findViewById<GraphView>(R.id.ecg_gv_egf)
        val coords = findViewById<TextView>(R.id.coords_tv_egf)
        try{
            while ( true) {

                if( currStr == EOF ){
                    break
                }
                if(currStr.equals("===")){
                    runOnUiThread {
                        val dataPoints = mutableListOf<DataPoint>()
                        for(i in 0..hr.size-1){
                            dataPoints.add(DataPoint((times[i]-times[0]).toDouble(), hr[i].toDouble()))
                        }

                        val series: LineGraphSeries<DataPoint> = LineGraphSeries(dataPoints.toTypedArray())
                        series.color = R.color.black
//                        series.isDrawDataPoints = true
//                        series.dataPointsRadius = 10F
                        series.setOnDataPointTapListener { series, dataPoint ->
                            coords.text = "x = ${dataPoint.x}  y = ${dataPoint.y}"
                        }
                        graphView.viewport.scrollToEnd()
                        graphView.removeAllSeries()
                        graphView.addSeries(series)
                    }

                }
                Log.d("CurrStr", currStr.toString())
                fileDat = currStr + "\n"
                fileBuffWriter.write(fileDat)
                fileBuffWriter.flush()
                /*
                Null is there when no string is received before any of the termination condition.
                The readLine() method is designed to block until one of the following conditions is met:
                It reads a line of text (terminated by a newline character \n or carriage return/line feed \r\n).
                It reaches the end of the stream (the sender closes the connection or sends an EOF).
                It encounters an exception, such as an IOException.
                 */
                currStr = buffRead.readLine()
//                burstStr += currStr
                if(currStr.indexOf(',') != -1){
                    times.add(currStr.substring(0, currStr.indexOf(',')).toInt())
                    hr.add(currStr.substring(currStr.indexOf(',')+1).toDouble())

                }

            }

            return 0
        }
        catch ( e: IOException){
            Log.e("BuffRead error", e.message.toString())
            this@ECGActivity.runOnUiThread(Runnable {
                Toast.makeText(this, e.message.toString(), Toast.LENGTH_LONG).show()
            })

            return -1
        }
    }

    private fun getCommandJSON( cmd : String, time : Int, header: String, footer: String) : JSONObject{
        var m : MutableMap< Any?, Any?> = mutableMapOf( Pair("cmd", cmd), Pair("time",time), Pair("header",header), Pair("footer",footer))
        return JSONObject( m)
    }

}
/*According to documentation (https://developer.android.com/training/data-storage/shared/media#storage-permission):
No permissions needed if you only access your own media files On devices that run Android 10 or higher, you don't need any storage-related permissions to access and modify media files that your app owns, including files in the Media Store. Downloads collection. If you're developing a camera app, for example, you don't need to request storage-related permissions because your app owns the images that you're writing to the media store.
From android 10 you can not to ask permissions to get files from storage. Works perfectly with all kinds of files (pdf, excel...) on my app on android 13 without any permissions. So you just need to ask READ_EXTERNAL_STORAGE permission for SDK, less than android 10.
But if you need special files (for example which was created by Instagram app but not stored in usual storage) you should ask for permission from your list.
Also look for types of Media storage: https://developer.android.com/reference/android/provider/MediaStore
About WRITE_EXTERNAL_STORAGE - you don`t need it since sdkVersion=29
EDIT: Was rewriting my app and want to add something:
It depends on what your app needs to do, but I have removed all kinds of storage permissions (only WRITE_EXTERNAL_STORAGE left for SDK less than 29) from my app, and just use ACTION_OPEN_DOCUMENT, ACTION_OPEN_DOCUMENT_TREE to have access for all kind of files (but not for system folders, ACTION_OPEN_DOCUMENT_TREE also have no access for download folder).
yes. if you set compileSdkVersion to 33, you should only get READ_EXTERNAL_STORAGE for android 10 and below. otherwise, your request permission will always fail. WRITE_EXTERNAL_STORAGE is also deprecated and you should remove It from your manifest. If you need to access non-your-application files, then you have to get related permissions like READ_MEDIA_VIDEO for videos. other ones are listed in the android-permission documentation. –
*/


//    btn.setOnClickListener {
//        //can't run IO calls in main thread because of os.network rules
//
//        var readSuccessful = false
//        lifecycleScope.launch(Dispatchers.IO) {
//
//            Log.d("IP addr",etIPaddr.text.toString())
//            var clientSocket = Socket(etIPaddr.text.toString(), 80)
//            //this automatically binds to the server, no need to send separate connect request.
//            /*
//            IMP all write and read calls in TCP sockets are blocking
//            see: https://stackoverflow.com/questions/10574596/is-an-outputstream-in-java-blocking-sockets
//             */
//            val buffRead = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
//            val buffWrit = BufferedWriter(OutputStreamWriter(clientSocket.getOutputStream()))
//
//            lifecycleScope.launch(Dispatchers.Main){
//                pb.visibility =View.VISIBLE
//            }
//
//           var fileDat = ""
//           var currStr : String = ""
//          //TODO: get timeList and readList from currStr
//           while( currStr != "</HARMA>"){
//               currStr = buffRead.readLine()
//               Log.d("CurrStr", currStr)
//               fileDat += currStr
//               fileDat += "\n"
//           }
//            Log.d("File data", fileDat)
//            readSuccessful = true
//            fos1.write(fileDat.toByteArray())
//            fos1.close()
//            clientSocket.close()
//            buffRead.close()
//            buffWrit.close()
//            lifecycleScope.launch(Dispatchers.Main) {
//                Toast.makeText(this@MainActivity,"Done with the file", Toast.LENGTH_SHORT).show()
//                pb.visibility =View.INVISIBLE
//            }
//            Log.d("readSuccessful","Read success")
//            try{
//                val dataList = module.callAttr("get_heart_metrics",data_read,time_stamp).asList()
//                Log.d("Heart metrics", dataList.toString())
//                withContext( Dispatchers.Main){
//                    tvServerMsg.text = dataList[0].toString() + "\n" + dataList[1].toString()
//                }
//            }
//            catch ( e: Exception){
//                lifecycleScope.launch(Dispatchers.Main) {
//                    Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
//                    e.message?.let { it1 ->
//                        Log.e("Error", it1)
//                    }
//                }
//            }
//        }

