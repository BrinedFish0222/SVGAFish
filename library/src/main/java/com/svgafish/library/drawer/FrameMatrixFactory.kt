package com.svgafish.library.drawer

import android.graphics.Matrix
import com.svgafish.library.utils.SVGAScaleInfo
import kotlin.math.abs
import kotlin.math.sqrt

internal class FrameMatrixFactory(
    private val scaleInfo: SVGAScaleInfo,
    private val sharedValues: ShareValues
) {

    private val matrixScaleTempValues = FloatArray(16)

    fun createFrameMatrix(transform: Matrix): Matrix {
        val matrix = sharedValues.sharedMatrix()
        matrix.postScale(scaleInfo.scaleFx, scaleInfo.scaleFy)
        matrix.postTranslate(scaleInfo.tranFx, scaleInfo.tranFy)
        matrix.preConcat(transform)
        return matrix
    }

    fun matrixScale(matrix: Matrix): Float {
        matrix.getValues(matrixScaleTempValues)
        if (matrixScaleTempValues[0] == 0f) {
            return 0f
        }
        var a = matrixScaleTempValues[0].toDouble()
        var b = matrixScaleTempValues[3].toDouble()
        var c = matrixScaleTempValues[1].toDouble()
        var d = matrixScaleTempValues[4].toDouble()
        if (a * d == b * c) {
            return 0f
        }
        var scaleX = sqrt(a * a + b * b)
        a /= scaleX
        b /= scaleX
        var skew = a * c + b * d
        c -= a * skew
        d -= b * skew
        val scaleY = sqrt(c * c + d * d)
        c /= scaleY
        d /= scaleY
        skew /= scaleY
        if (a * d < b * c) {
            scaleX = -scaleX
        }
        return if (scaleInfo.ratioX) abs(scaleX.toFloat()) else abs(scaleY.toFloat())
    }
}
