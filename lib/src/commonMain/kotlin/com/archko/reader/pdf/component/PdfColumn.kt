package com.archko.reader.pdf.component

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.archko.reader.pdf.flinger.StockFlingBehaviours
import com.archko.reader.pdf.state.PdfState

@Composable
public fun PdfColumn(
    viewWidth: Int,
    viewHeight: Int,
    state: PdfState,
    modifier: Modifier = Modifier,
    page: @Composable (index: Int, width: Int, height: Int) -> Unit =
        { i: Int, w: Int, h: Int ->
            PdfPage(
                state = state,
                index = i,
                width = w,
                height = h,
            )
        },
    lazyListState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(
        space = 1.dp,
        alignment = if (!reverseLayout) Alignment.Top else Alignment.Bottom
    ),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    flingBehavior: FlingBehavior = StockFlingBehaviours.smoothScroll(),
    userScrollEnabled: Boolean = true
) {
    var width by remember { mutableIntStateOf(viewWidth) }
    var height by remember { mutableIntStateOf(viewHeight) }

    LazyColumn(
        flingBehavior = flingBehavior,
        modifier = modifier.onSizeChanged {
            width = it.width
            height = it.height
            println("LazyColumn:$width-$height")
        },
        state = lazyListState,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        userScrollEnabled = userScrollEnabled
    ) {
        items(
            count = state.pageCount,
            key = { index -> "$index" }
        ) { i ->
            page(i, width, height)
        }
    }
}