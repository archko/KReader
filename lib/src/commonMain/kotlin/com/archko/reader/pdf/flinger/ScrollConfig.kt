package com.archko.reader.pdf.flinger

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
import kotlin.math.ln

/**
 * This class defines all the fling related parameters that can be tweaked by the user.
 *
 * @author Joseph James.
 */
public class FlingConfiguration private constructor(
    public val scrollFriction: Float,
    public val absVelocityThreshold: Float,
    public val gravitationalForce: Float,
    public val inchesPerMeter: Float,
    public val decelerationFriction: Float,
    public val decelerationRate: Float,
    public val splineInflection: Float,
    public val splineStartTension: Float,
    private val splineEndTension: Float,
    public val numberOfSplinePoints: Int
) {

    public val splineP1: Float by lazy { splineStartTension * splineInflection }

    public
    val splineP2: Float by lazy { 1.0f - splineEndTension * (1.0f - splineInflection) }

    public data class Builder(
        /*
         * This variable manages the friction to the scrolls in the LazyColumn
         */
        var scrollViewFriction: Float = 0.008f,

        /*
         * This is the absolute value of a velocity threshold, below which the
         * animation is considered finished.
         */
        var absVelocityThreshold: Float = 0f,

        /*
         * Gravitational obstruction to the scroll.
         */
        var gravitationalForce: Float = 9.80665f,

        /*
         * Scroll Inches per meter
         */
        var inchesPerMeter: Float = 39.37f,

        /*
         * Rate of deceleration of the scrollView.
         */
        var decelerationRate: Float = (ln(0.78) / ln(0.9)).toFloat(),

        /*
         * Friction at the time of deceleration.
         */
        var decelerationFriction: Float = 0.09f,

        /*
         * Inflection is the place where the start and end tension lines cross each other.
         */
        var splineInflection: Float = 0.1f,

        /*
         * Spline's start tension.
         */
        var splineStartTension: Float = 0.1f,

        /*
         * Spline's end tension.
         */
        var splineEndTension: Float = 1.0f,

        /*
         * number of sampling points in the spline
         */
        var numberOfSplinePoints: Int = 100
    ) {

        public fun scrollViewFriction(scrollViewFriction: Float): Builder =
            apply { this.scrollViewFriction = scrollViewFriction }

        public fun absVelocityThreshold(absVelocityThreshold: Float): Builder =
            apply { this.absVelocityThreshold = absVelocityThreshold }

        public fun gravitationalForce(gravitationalForce: Float): Builder =
            apply { this.gravitationalForce = gravitationalForce }

        public fun inchesPerMeter(inchesPerMeter: Float): Builder =
            apply { this.inchesPerMeter = inchesPerMeter }

        public fun decelerationFriction(decelerationFriction: Float): Builder =
            apply { this.decelerationFriction = decelerationFriction }

        public fun decelerationRate(decelerationRate: Float): Builder =
            apply { this.decelerationRate = decelerationRate }

        public fun splineInflection(splineInflection: Float): Builder =
            apply { this.splineInflection = splineInflection }

        public fun splineStartTension(splineStartTension: Float): Builder =
            apply { this.splineStartTension = splineStartTension }

        public fun splineEndTension(splineEndTension: Float): Builder =
            apply { this.splineEndTension = splineEndTension }

        public fun numberOfSplinePoints(numberOfSplinePoints: Int): Builder =
            apply { this.numberOfSplinePoints = numberOfSplinePoints }

        public fun build(): FlingConfiguration = FlingConfiguration(
            scrollViewFriction,
            absVelocityThreshold,
            gravitationalForce,
            inchesPerMeter,
            decelerationFriction,
            decelerationRate,
            splineInflection,
            splineStartTension,
            splineEndTension,
            numberOfSplinePoints
        )
    }
}