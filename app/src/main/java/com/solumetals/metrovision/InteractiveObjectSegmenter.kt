package com.solumetals.metrovision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenterOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.Stroke

/** Local, offline object selection. No photo or measurement leaves the phone. */
class InteractiveObjectSegmenter(context: Context) : AutoCloseable {
    private val segmenter = InteractiveSegmenter.createFromOptions(
        context,
        InteractiveSegmenterOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("interactive_segmentation.task")
                    .build()
            )
            .build()
    )

    data class MaskBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val confidence: Float
    ) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
    }

    fun select(bitmap: Bitmap, normalizedX: Float, normalizedY: Float): MaskBounds? {
        segmenter.setImage(BitmapImageBuilder(bitmap).build())
        val stroke = Stroke.builder()
            .setBrushMode(Stroke.BrushMode.POSITIVE)
            .setPoints(listOf(NormalizedKeypoint.create(normalizedX, normalizedY)))
            .setCompleted(true)
            .build()
        val mask = segmenter.segment(listOf(stroke))
        val values = ByteBufferExtractor.extract(mask).asFloatBuffer()
        values.rewind()

        var left = mask.width
        var top = mask.height
        var right = -1
        var bottom = -1
        var sum = 0f
        var count = 0
        repeat(mask.width * mask.height) { index ->
            val confidence = values.get()
            if (confidence >= MASK_THRESHOLD) {
                val x = index % mask.width
                val y = index / mask.width
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
                sum += confidence
                count++
            }
        }
        if (count < MIN_MASK_PIXELS || right <= left || bottom <= top) return null
        return MaskBounds(left, top, right, bottom, sum / count)
    }

    override fun close() = segmenter.close()

    private companion object {
        const val MASK_THRESHOLD = 0.55f
        const val MIN_MASK_PIXELS = 250
    }
}
