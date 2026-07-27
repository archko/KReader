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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archko.reader.pdf.cache.DriverFactory
import com.archko.reader.pdf.entity.DocumentInfo
import com.archko.reader.pdf.util.IntentFile
import com.archko.reader.pdf.viewmodel.AIViewModel
import com.archko.reader.pdf.viewmodel.BackupViewModel
import com.archko.reader.pdf.viewmodel.PdfViewModel
import com.archko.reader.viewer.viewmodel.FontViewModel

class ComposeViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

open class MainActivity : ComponentActivity() {

    private var permissionDialog: Dialog? = null
    private var externalDocument: DocumentInfo? = null
    private var hasStoragePermission by mutableStateOf(false)

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            hasStoragePermission = true
        } else {
            Toast.makeText(this, getString(R.string.grant_failed), Toast.LENGTH_SHORT).show()
            requestStoragePermission(false)
        }
    }

    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            hasStoragePermission = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        refreshPermissionState()
        loadView()
        checkForExternalPermission()
    }

    private fun refreshPermissionState() {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkStoragePermission()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processExternalIntent(intent)
        setIntent(null)
    }

    private fun processExternalIntent(intent: Intent?) {
        if (intent == null) return

        externalDocument = when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    val path = IntentFile.getPath(this, uri)
                    DocumentInfo(uri = uri.toString(), path = path)
                } else {
                    null
                }
            }

            else -> {
                intent.getStringExtra("path")?.takeIf { !TextUtils.isEmpty(it) }?.let { path ->
                    DocumentInfo(path = path)
                }
            }
        }

        Log.d(TAG, "处理外部intent，路径: ${externalDocument?.getDisplayPath()}")
    }

    fun loadView() {
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

            val driverFactory = DriverFactory(LocalContext.current)
            val database = driverFactory.createRoomDatabase()
            val viewModelStoreOwner = remember { ComposeViewModelStoreOwner() }
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                val viewModel: PdfViewModel = viewModel()
                val fontViewModel: FontViewModel = viewModel()
                val backupViewModel: BackupViewModel = viewModel()
                val aiViewModel: AIViewModel = viewModel()
                val bookmarkViewModel: com.archko.reader.pdf.viewmodel.BookmarkViewModel =
                    viewModel()
                val readingStatsViewModel: com.archko.reader.pdf.viewmodel.ReadingStatsViewModel =
                    viewModel()
                viewModel.database = database
                backupViewModel.database = database
                aiViewModel.database = database
                bookmarkViewModel.database = database
                readingStatsViewModel.database = database

                KApp(
                    viewModel,
                    backupViewModel,
                    aiViewModel,
                    fontViewModel,
                    bookmarkViewModel,
                    readingStatsViewModel,
                    externalDocument,
                    hasStoragePermission = hasStoragePermission
                )
            }
        }
    }
    //========================================

    private fun checkForExternalPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestAllFilesAccess()
            }
        } else {
            if (!checkStoragePermission()) {
                requestStoragePermission(true)
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return (ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED)
    }

    open fun requestStoragePermission(isInitialStart: Boolean) {
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
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
                    storagePermissionLauncher.launch(permission)
                    permissionDialog?.run {
                        permissionDialog!!.dismiss()
                    }
                }
            builder.setCancelable(false)
            builder.create().show()
        } else if (isInitialStart) {
            storagePermissionLauncher.launch(permission)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    open fun requestAllFilesAccess() {
        // 调用此方法时已经确认是 Android 11 及以上版本且没有管理权限
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.grant_all_files_permission))
            .setMessage(getString(R.string.grant_all_files_permission))
            .setPositiveButton(getString(R.string.grant_cancel)) { _, _ ->
                //finish()
            }
            .setNegativeButton(getString(R.string.grant_ok)) { _, _ ->
                try {
                    val intent =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            .setData("package:$packageName".toUri())
                    allFilesAccessLauncher.launch(intent)
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

    companion object {
        private val TAG = "ChooseFile"
    }
}