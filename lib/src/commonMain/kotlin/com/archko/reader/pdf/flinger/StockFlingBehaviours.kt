package com.archko.reader.pdf.flinger

/**
 * @author: archko 2025/2/13 :15:43
 */
/*
* MIT License
*
* Copyright (c) 2021 Joseph James
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
*
*/

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable

/**
 * This is a helper class for selecting the predefined set of scroll behaviours.
 *
 * @author Joseph James
 */
public object StockFlingBehaviours {

    @Composable
    public fun getAndroidNativeScroll(): FlingBehavior = ScrollableDefaults.flingBehavior()


    @Composable
    public fun smoothScroll(): FlingBehavior = flingBehavior(
        scrollConfiguration = FlingConfiguration.Builder()
            .build()
    )

    @Composable
    public fun presetOne(): FlingBehavior = flingBehavior(
        scrollConfiguration = FlingConfiguration.Builder()
            .scrollViewFriction(0.04f)
            .build()
    )

    @Composable
    public fun presetTwo(): FlingBehavior = flingBehavior(
        scrollConfiguration = FlingConfiguration.Builder()
            .splineInflection(0.16f)
            .build()
    )

    @Composable
    public fun presetThree(): FlingBehavior = flingBehavior(
        scrollConfiguration = FlingConfiguration.Builder()
            .decelerationFriction(0.5f)
            .build()
    )

    @Composable
    public fun presetFour(): FlingBehavior = flingBehavior(
        scrollConfiguration = FlingConfiguration.Builder()
            .decelerationFriction(0.6f)
            .splineInflection(.4f)
            .build()
    )

    @Composable
    public fun presetFive(): FlingBehavior = flingBehavior(
        scrollConfiguration = FlingConfiguration.Builder()
            .scrollViewFriction(.09f)
            .decelerationFriction(0.015f)
            .build()
    )

    @Composable
    public fun custom(): FlingBehavior = flingBehavior(
        scrollConfiguration = FlingConfiguration.Builder()
            .scrollViewFriction(0.008f)  // 减小摩擦力，使滑动更流畅
            // 减小这个值可以增加滚动速度，建议范围 0.01f - 0.02f
            .numberOfSplinePoints(100)  // 提高采样率
            // 增加这个值可以使滚动更平滑，但会略微增加计算量，建议范围 100 - 200
            .splineInflection(0.3f)     // 控制曲线拐点位置
            // 减小这个值可以使滚动更快减速，建议范围 0.1f - 0.3f
            .splineStartTension(0.5f)   // 控制曲线起始张力
            // 增加这个值可以使滚动初速度更快，建议范围 0.5f - 1.0f
            .splineEndTension(0.7f)       // 控制曲线结束张力
            // 增加这个值可以使滚动持续时间更长，建议范围 0.8f - 1.2f
            .build()
    )

}