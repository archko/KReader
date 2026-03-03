package com.archko.reader.viewer.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archko.reader.pdf.component.SearchState
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.ic_back
import kreader.composeapp.generated.resources.ic_close
import kreader.composeapp.generated.resources.ic_search
import kreader.composeapp.generated.resources.ic_toc
import kreader.composeapp.generated.resources.search
import kreader.composeapp.generated.resources.search_close
import kreader.composeapp.generated.resources.search_hint
import kreader.composeapp.generated.resources.search_next
import kreader.composeapp.generated.resources.search_no_results
import kreader.composeapp.generated.resources.search_prev
import kreader.composeapp.generated.resources.search_results
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 搜索栏组件
 * @author: archko 2026/3/1
 */
@Composable
fun SearchBar(
    searchState: SearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShowResultList: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xCC333333),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 搜索输入框
            BasicTextField(
                value = searchState.query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = Color.White
                ),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                decorationBox = { innerTextField ->
                    if (searchState.query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.search_hint),
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )

            // 搜索按钮
            IconButton(
                onClick = onSearch,
                enabled = searchState.query.isNotBlank() && !searchState.isSearching
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_search),
                    contentDescription = stringResource(Res.string.search),
                    tint = if (searchState.query.isNotBlank()) Color.White else Color.Gray
                )
            }

            // 搜索状态显示
            if (searchState.isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else if (searchState.hasResults) {
                // 上一个按钮
                IconButton(
                    onClick = onPrevious,
                    enabled = searchState.totalCount > 0
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_back),
                        contentDescription = stringResource(Res.string.search_prev),
                        tint = Color.White
                    )
                }

                // 结果计数
                Text(
                    text = stringResource(Res.string.search_results).format(
                        searchState.currentIndex + 1,
                        searchState.totalCount
                    ),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // 下一个按钮
                IconButton(
                    onClick = onNext,
                    enabled = searchState.totalCount > 0
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_back),
                        contentDescription = stringResource(Res.string.search_next),
                        tint = Color.White,
                        modifier = Modifier.graphicsLayer(rotationZ = 180f)
                    )
                }

                // 搜索结果列表按钮
                IconButton(
                    onClick = onShowResultList,
                    enabled = searchState.totalCount > 0
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_toc),
                        contentDescription = "搜索结果列表",
                        tint = Color.White
                    )
                }
            } else if (searchState.query.isNotBlank() && !searchState.isSearching) {
                // 无结果提示
                Text(
                    text = stringResource(Res.string.search_no_results),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // 关闭按钮
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.search_close),
                    tint = Color.White
                )
            }
        }
    }
}
