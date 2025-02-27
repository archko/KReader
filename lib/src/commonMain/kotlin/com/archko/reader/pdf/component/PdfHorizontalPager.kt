package com.archko.reader.pdf.component

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.archko.reader.pdf.state.PdfState

@Composable
public fun PdfHorizontalPager(
    state: PdfState,
    pagerState: PagerState = rememberPagerState { state.pageCount },
    modifier: Modifier = Modifier,
    page: @Composable (index: Int) -> Unit = {
        PdfPage(
            state = state,
            index = it,
            width = 0,
            height = 0,
        )
    },
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSize: PageSize = PageSize.Fill,
    beyondViewportPageCount: Int = 0,
    pageSpacing: Dp = 0.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    flingBehavior: TargetedFlingBehavior = PagerDefaults.flingBehavior(state = pagerState),
    userScrollEnabled: Boolean = true,
    reverseLayout: Boolean = false,
    pageNestedScrollConnection: NestedScrollConnection = PagerDefaults.pageNestedScrollConnection(
        state = pagerState,
        orientation = Orientation.Horizontal
    )
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        contentPadding = contentPadding,
        pageSize = pageSize,
        beyondViewportPageCount = beyondViewportPageCount,
        pageSpacing = pageSpacing,
        verticalAlignment = verticalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        reverseLayout = reverseLayout,
        pageNestedScrollConnection = pageNestedScrollConnection
    ) { i ->
        page(i)
    }
}