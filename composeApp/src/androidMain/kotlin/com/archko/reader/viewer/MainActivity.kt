package com.archko.reader.viewer

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archko.reader.pdf.cache.DriverFactory
import com.archko.reader.pdf.util.IntentFile
import com.archko.reader.pdf.viewmodel.PdfViewModel

class ComposeViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

open class MainActivity : ComponentActivity(), OnPermissionGranted {

    private val permissionCallbacks = arrayOfNulls<OnPermissionGranted>(PERMISSION_LENGTH)
    private var permissionDialog: Dialog? = null
    private var externalPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        checkForExternalPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 处理新的intent
        processExternalIntent(intent)
    }

    private fun processExternalIntent(intent: Intent?) {
        if (intent == null) return

        externalPath = when (intent.action) {
            Intent.ACTION_VIEW -> {
                // 处理ACTION_VIEW，从intent.data获取文件路径
                val uri = intent.data
                if (uri != null) {
                    val path = IntentFile.getPath(this, uri)
                    if (!TextUtils.isEmpty(path)) {
                        path
                    } else {
                        uri.toString()
                    }
                } else {
                    null
                }
            }

            else -> {
                // 处理自定义路径参数
                intent.getStringExtra("path")?.takeIf { !TextUtils.isEmpty(it) }
            }
        }

        Log.d(TAG, "处理外部intent，路径: $externalPath")
    }

    fun loadView() {
        // 处理外部intent
        processExternalIntent(intent)
        val activity = this as? ComponentActivity

        setContent {
            val isDarkTheme = isSystemInDarkTheme()

            // 根据系统主题切换应用主题
            LaunchedEffect(isDarkTheme) {
                activity?.let {
                    if (isDarkTheme) {
                        it.setTheme(R.style.Theme_KReader_FullScreen_Dark)
                    } else {
                        it.setTheme(R.style.Theme_KReader_FullScreen)
                    }
                }
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                val window = (view.context as? ComponentActivity)?.window
                window?.let {
                    // 确保状态栏在首页时显示
                    WindowCompat.setDecorFitsSystemWindows(it, true)
                    WindowCompat.getInsetsController(it, view).apply {
                        // 显示状态栏
                        show(WindowInsetsCompat.Type.statusBars())
                        show(WindowInsetsCompat.Type.navigationBars())
                        // 根据主题设置状态栏文字颜色
                        isAppearanceLightStatusBars = !isDarkTheme
                    }
                }
            }

            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            val screenWidthInPixels = with(density) { configuration.screenWidthDp.dp.toPx() }
            val screenHeightInPixels = with(density) { configuration.screenHeightDp.dp.toPx() }
            println("app.screenHeight:$screenWidthInPixels-$screenHeightInPixels")

            val driverFactory = DriverFactory(LocalContext.current)
            val database = driverFactory.createRoomDatabase()
            val viewModelStoreOwner = remember { ComposeViewModelStoreOwner() }
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                val viewModel: PdfViewModel = viewModel()
                viewModel.database = database

                KApp(
                    screenWidthInPixels.toInt(),
                    screenHeightInPixels.toInt(),
                    viewModel,
                    externalPath
                )
            }
        }
    }
    //========================================

    private fun checkForExternalPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30) 及以上版本，只需要申请"管理所有文件访问权限"
            if (!Environment.isExternalStorageManager()) {
                requestAllFilesAccess(this)
            } else {
                loadView()
            }
        } else { // Android 6.0 (API 23) 到 Android 10 (API 29)，需要申请 WRITE_EXTERNAL_STORAGE 权限
            if (!checkStoragePermission()) {
                requestStoragePermission(this, true)
            } else {
                loadView()
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return (ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED)
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
            builder.setTitle(getString(R.string.grant_files_permission))
                .setMessage(getString(R.string.grant_files_permission))
                .setPositiveButton(getString(R.string.grant_cancel)) { _, _ ->
                    finish()
                }
                .setNegativeButton(getString(R.string.grant_ok)) { _, _ ->
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

    @RequiresApi(Build.VERSION_CODES.R)
    open fun requestAllFilesAccess(onPermissionGranted: OnPermissionGranted) {
        // 调用此方法时已经确认是 Android 11 及以上版本且没有管理权限
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.grant_all_files_permission))
            .setMessage(getString(R.string.grant_all_files_permission))
            .setPositiveButton(getString(R.string.grant_cancel)) { _, _ ->
                finish()
            }
            .setNegativeButton(getString(R.string.grant_ok)) { _, _ ->
                permissionCallbacks[ALL_FILES_PERMISSION] = onPermissionGranted
                try {
                    val intent =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            .setData("package:$packageName".toUri())
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Failed to initial activity to grant all files access",
                        e
                    )
                    Toast.makeText(
                        this,
                        getString(R.string.no_sdcard_permission),
                        Toast.LENGTH_LONG
                    )
                        .show()
                }
            }
        builder.setCancelable(false)
        builder.create().show()
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
                    getString(R.string.grant_failed),
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

    override fun onResume() {
        super.onResume()
        // 检查用户是否从设置页面返回并授予了权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager() && permissionCallbacks[ALL_FILES_PERMISSION] != null) {
                permissionCallbacks[ALL_FILES_PERMISSION]!!.onPermissionGranted()
                permissionCallbacks[ALL_FILES_PERMISSION] = null
            }
        }
    }

    companion object {

        private val TAG = "ChooseFile"

        const val PERMISSION_LENGTH = 2
        var STORAGE_PERMISSION = 0
        const val ALL_FILES_PERMISSION = 1
    }
}