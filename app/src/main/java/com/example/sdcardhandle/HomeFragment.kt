package com.example.sdcardhandle


import android.Manifest
import android.app.IntentService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Environment.MEDIA_MOUNTED
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.coroutines.*
import java.io.File
import java.util.Collections.replaceAll


/*https://developer.android.com/guide/topics/connectivity/usb/host*/



class HomeFragment : Fragment() {
    private val EXTERNAL_PERMS = arrayOf<String>(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
    private val EXTERNAL_REQUEST = 138
    private var destination:File?=null
    private var source:File?=null
    private val storageDir = File("/storage/")
    private var noOfFolders:Int= 0
    private var copyRunning = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var view: View = inflater.inflate(R.layout.fragment_home,container,false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        noOfFolders= findNoOfSubFolders()
        if(ActivityCompat.checkSelfPermission(context!!, Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED){
            usbConnection()
        }else{
            requestPermissions( arrayOf<String>(Manifest.permission.WRITE_EXTERNAL_STORAGE), EXTERNAL_REQUEST)
        }
    }

    private fun usbConnection() {
        destination = File(context?.getExternalFilesDir(null),"myfolder")
        var filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        activity?.registerReceiver(mUsbAttachReceiver, filter)
        filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        activity?.registerReceiver(mUsbDetachReceiver, filter)
    }

    private fun findNoOfSubFolders(): Int{
        if(storageDir.isDirectory){
            return storageDir.listFiles().size
        }
        return 0
    }

    private fun doAsyncWork(){
        CoroutineScope(Dispatchers.Main).launch {
            CoroutineScope(Dispatchers.IO).async label1@{
                searchFile(storageDir)
                if(source!=null){
                    withContext(Dispatchers.Main){
                        tv_show.text = "MyFolder Found" + "\n"
                    }
                    copyFile(source!!)
                    copyRunning= false
                }else{
                    withContext(Dispatchers.Main){
                        tv_show.text = "Folder Not Found" + "\n"
                    }
                }
                return@label1
            }.await()

        }
    }



     fun searchFile(file:File){
         if(file.isDirectory){
             if(file.name.trim().replace("\\s".toRegex(), "").toLowerCase() == "myfolder" && file.absolutePath!=destination?.absolutePath) {
                 source= file
                 return
             }
             var dirList = file.listFiles()
             if(dirList!=null){
                 for(i in dirList){
                     searchFile(i)
                 }
             }
         }
     }

    private fun copyFile(file: File) {
        //val root = File(Environment.getExternalStorageDirectory(), activity?.packageName)
        if(Environment.getExternalStorageState()==MEDIA_MOUNTED){
            destination?.mkdir()
            if(destination!=null){
                copyRunning= true
                file.copyRecursively(destination!!, true)
                CoroutineScope(Dispatchers.Main).launch {
                    tv_show.append("Copy Completed" + "\n")
                }
                return
            }
        }
    }


    private val timer = object: CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if(noOfFolders < findNoOfSubFolders()){

                    doAsyncWork()
                    noOfFolders= findNoOfSubFolders()
                }
            }

            override fun onFinish() {

            }
        }


    private var mUsbAttachReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                tv_status.text = "USB Connected"
                timer.start()
            }
        }
    }

    private var mUsbDetachReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.apply {
                    // call your method that cleans up and closes communication with the device
                    tv_status.text = "USB Disconnected"
                    timer.cancel()
                    noOfFolders -= 1
                    tv_show.text= ""
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode== EXTERNAL_REQUEST && grantResults!=null && grantResults.size>0 && grantResults.get(0)==PackageManager.PERMISSION_GRANTED){
            noOfFolders -= 1
            usbConnection()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.unregisterReceiver(mUsbAttachReceiver)
        activity?.unregisterReceiver(mUsbDetachReceiver)
    }
}
