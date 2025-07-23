package com.archko.reader.viewer

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingScreen(
    modifier: Modifier = Modifier,
) {
    Theme {
        Surface(
            modifier = modifier
                .statusBarsPadding()
                .fillMaxSize()
        ) {
            Box {
                Column(modifier = Modifier.padding(20.dp)) {
                    Spacer(modifier = Modifier.height(30.dp))

                    Avatar()

                    SettingCategory()
                }
            }
        }
    }
}

@Composable
fun Avatar(
) {
    Row {
        Column(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .align(alignment = Alignment.CenterVertically)
        ) {
        }
    }
}

@Composable
fun SettingCategory(
) {
    val context = LocalContext.current

    val version by remember {
        var packageInfo: PackageInfo? = null
        try {
            packageInfo =
                context.let { context.packageManager?.getPackageInfo(it.packageName, 0) }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        if (packageInfo != null) {
            mutableStateOf(packageInfo.versionName)
        } else {
            mutableStateOf("")
        }
    }
    Column(
        modifier = Modifier
            .padding(top = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Color(0xffffffff),
            )
            .padding(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(50.dp)
                .fillMaxWidth()
                //.clickable { viewModel.checkUpdateManully(context) }
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = "Version",
                style = TextStyle(
                    Color(0xff333333),
                    fontSize = 15.sp
                ),
                maxLines = 1
            )

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
            ) {
                Text(
                    text = version!!,
                    style = TextStyle(
                        Color(0xff333333),
                        fontSize = 15.sp
                    ),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

