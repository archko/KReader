package com.archko.reader.viewer

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.archko.reader.pdf.viewmodel.PdfViewModel
import com.archko.reader.viewer.navigation.MainDestinations
import com.archko.reader.viewer.navigation.rememberKNavController
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.home
import kreader.composeapp.generated.resources.setting
import org.jetbrains.compose.resources.stringResource

val LocalNavController = compositionLocalOf<NavHostController> {
    error("No NavHostController provided in LocalNavController")
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun KApp(
    screenWidthInPixels: Int,
    screenHeightInPixels: Int,
    viewModel: PdfViewModel,
    externalPath: String? = null
) {
    // 在顶层管理 externalPath 状态，确保关闭后不会重新打开
    var currentExternalPath by remember { mutableStateOf(externalPath) }

    // 只在首次接收到 externalPath 时设置
    LaunchedEffect(externalPath) {
        if (externalPath != null && currentExternalPath == null) {
            currentExternalPath = externalPath
        }
    }

    Theme {
        val jetsnackNavController = rememberKNavController()
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides jetsnackNavController.navController,
                LocalSharedTransitionScope provides this
            ) {
                NavHost(
                    navController = jetsnackNavController.navController,
                    startDestination = MainDestinations.HOME_SCREEN
                ) {
                    composable(
                        route = MainDestinations.HOME_SCREEN
                    ) { backStackEntry ->
                        MainContainer(
                            screenWidthInPixels,
                            screenHeightInPixels,
                            viewModel,
                            modifier = Modifier,
                            externalPath = currentExternalPath,
                            onExternalPathConsumed = {
                                // 当外部路径被处理后，清除它以防止重复打开
                                currentExternalPath = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainContainer(
    screenWidthInPixels: Int,
    screenHeightInPixels: Int,
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier,
    externalPath: String? = null,
    onExternalPathConsumed: () -> Unit = {}
) {
    val nestedNavController = rememberKNavController()
    val navBackStackEntry by nestedNavController.navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var showBottomBar by remember { mutableStateOf(true) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                KBottomBar(
                    tabs = HomeSections.entries.toTypedArray(),
                    currentRoute = currentRoute ?: HomeSections.FILE.route,
                    navigateToRoute = nestedNavController::navigateToBottomBarRoute,
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        NavHost(
            navController = nestedNavController.navController,
            startDestination = HomeSections.FILE.route
        ) {
            addHomeGraph(
                screenWidthInPixels,
                screenHeightInPixels,
                viewModel,
                modifier = Modifier.consumeWindowInsets(padding),
                onShowBottomBarChanged = { showBottomBar = it },
                externalPath = externalPath,
                onExternalPathConsumed = onExternalPathConsumed
            )
        }
    }
}

fun NavGraphBuilder.addHomeGraph(
    screenWidthInPixels: Int,
    screenHeightInPixels: Int,
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier,
    onShowBottomBarChanged: (Boolean) -> Unit = {},
    externalPath: String? = null,
    onExternalPathConsumed: () -> Unit = {}
) {
    composable(HomeSections.FILE.route) { from ->
        FileScreen(
            viewModel,
            modifier = modifier,
            onShowBottomBarChanged = onShowBottomBarChanged,
            externalPath = externalPath,
            onExternalPathConsumed = onExternalPathConsumed
        )
    }
    composable(HomeSections.SETTING.route) { from ->
        SettingScreen(
            modifier
        )
    }
}

enum class HomeSections(
    val title: String,
    //val icon: Int,
    //val iconNormal: Int,
    val route: String
) {
    FILE(
        "Home",
        //R.drawable.ic_tab_home_selected,
        //R.drawable.ic_tab_home_normal,
        "home/file"
    ),
    SETTING(
        "Setting",
        //R.drawable.ic_tab_user_selected,
        //R.drawable.ic_tab_user_normal,
        "home/setting"
    ),
}

@Composable
fun KBottomBar(
    tabs: Array<HomeSections>,
    currentRoute: String,
    navigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        modifier = modifier,
        color = color,
        contentColor = contentColor
    ) {
        NavigationBar(
            modifier = modifier
                .navigationBarsPadding()
                .height(56.dp), // 调小高度
            containerColor = color,
            contentColor = contentColor,
        ) {
            tabs.forEach { section ->
                val selected = currentRoute == section.route
                val text = when (section.route) {
                    "home/file" -> stringResource(Res.string.home)
                    "home/setting" -> stringResource(Res.string.setting)
                    else -> section.title
                }

                NavigationBarItem(
                    icon = {
                        /*Icon(
                            modifier = Modifier
                                .height(28.dp)
                                .width(28.dp),
                            // 根据选中状态切换图标
                            painter = painterResource(id = if (selected) section.icon else section.iconNormal),
                            contentDescription = text,
                            // 设置图标颜色为原始颜色
                            tint = Color.Unspecified
                        )*/
                    },
                    label = {
                        Text(
                            text = text,
                            style = TextStyle(
                                color = if (selected) Color(0xff895FFD) else Color.Black,
                                fontSize = 15.sp
                            ),
                            maxLines = 1
                        )
                    },
                    selected = selected,
                    onClick = { navigateToRoute(section.route) },
                    // 移除选中时的指示器
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Unspecified,
                        unselectedIconColor = Color.Unspecified,
                        selectedTextColor = Color(0xff895FFD),
                        unselectedTextColor = Color.Black,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}