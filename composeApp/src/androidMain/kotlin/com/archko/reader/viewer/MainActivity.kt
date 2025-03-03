package com.archko.reader.viewer

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archko.reader.pdf.viewmodel.PdfViewModel
import com.archko.reader.pdf.cache.AppDatabase
import com.archko.reader.pdf.cache.DatabaseDriverFactory
import com.archko.reader.pdf.cache.DriverFactory

class ComposeViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

open class MainActivity : ComponentActivity(), OnPermissionGranted {

    private val permissionCallbacks = arrayOfNulls<OnPermissionGranted>(PERMISSION_LENGTH)
    private var permissionDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        //MMKV.initialize(application)

        super.onCreate(savedInstanceState)

        checkForExternalPermission()

    }

    fun loadView() {
        setContent {
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            val screenWidthInPixels = with(density) { configuration.screenWidthDp.dp.toPx() }
            val screenHeightInPixels = with(density) { configuration.screenHeightDp.dp.toPx() }
            println("app.screenHeight:$screenWidthInPixels-$screenHeightInPixels")

            val driverFactory: DatabaseDriverFactory = DriverFactory(LocalContext.current)
            val database = AppDatabase(driverFactory.createDriver())
            val viewModelStoreOwner = remember { ComposeViewModelStoreOwner() }
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                val viewModel: PdfViewModel = viewModel()
                viewModel.database = database
                App(screenWidthInPixels.toInt(), screenHeightInPixels.toInt(), viewModel)
            }
        }
    }
    //========================================

    private fun checkForExternalPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!checkStoragePermission()) {
                requestStoragePermission(this, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requestAllFilesAccess(this)
            }
        } else {
            loadView()
        }
    }

    private fun checkStoragePermission(): Boolean {
        return (ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
                == PackageManager.PERMISSION_GRANTED)
    }

    open fun requestStoragePermission(
        onPermissionGranted: OnPermissionGranted, isInitialStart: Boolean
    ) {
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        permissionCallbacks[STORAGE_PERMISSION] = onPermissionGranted
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                permission
            )
        ) {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle("R.string.grant_files_permission")
                .setMessage("R.string.grant_files_permission")
                .setPositiveButton("R.string.grant_cancel") { _, _ ->
                    finish()
                }
                .setNegativeButton("R.string.grant_ok") { _, _ ->
                    ActivityCompat.requestPermissions(
                        this, arrayOf(permission), STORAGE_PERMISSION
                    )
                    permissionDialog?.run {
                        permissionDialog!!.dismiss()
                    }
                }
            builder.setCancelable(false)
            builder.create().show()
        } else if (isInitialStart) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                STORAGE_PERMISSION
            )
        }
    }

    open fun requestAllFilesAccess(onPermissionGranted: OnPermissionGranted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle("R.string.grant_all_files_permission")
                .setMessage("R.string.grant_all_files_permission")
                .setPositiveButton("R.string.grant_cancel") { _, _ ->
                    finish()
                }
                .setNegativeButton("R.string.grant_ok") { _, _ ->
                    permissionCallbacks[ALL_FILES_PERMISSION] = onPermissionGranted
                    try {
                        val intent =
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                .setData(Uri.parse("package:$packageName"))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Failed to initial activity to grant all files access",
                            e
                        )
                        Toast.makeText(
                            this,
                            "没有获取sdcard的读取权限",
                            Toast.LENGTH_LONG
                        )
                            .show()
                    }
                }
            builder.setCancelable(false)
            builder.create().show()
        } else {
            loadView()
        }
    }

    private fun isGranted(grantResults: IntArray): Boolean {
        return grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION) {
            if (isGranted(grantResults)) {
                permissionCallbacks[STORAGE_PERMISSION]!!.onPermissionGranted()
                permissionCallbacks[STORAGE_PERMISSION] = null
            } else {
                Toast.makeText(
                    this,
                    "R.string.grantfailed",
                    Toast.LENGTH_SHORT
                ).show()
                permissionCallbacks[STORAGE_PERMISSION]?.let {
                    requestStoragePermission(it, false)
                }
            }
        }
    }

    override fun onPermissionGranted() {
        loadView()
    }

    companion object {

        private val TAG = "ChooseFile"

        const val PERMISSION_LENGTH = 2
        var STORAGE_PERMISSION = 0
        const val ALL_FILES_PERMISSION = 1
    }
}