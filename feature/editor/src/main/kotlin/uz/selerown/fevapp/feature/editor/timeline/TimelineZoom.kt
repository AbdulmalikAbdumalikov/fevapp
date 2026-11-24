package uz.selerown.fevapp.feature.editor.timeline

/** Timeline zoom level, expressed as dp per second of video. */
object TimelineZoom {
    const val DEFAULT_DP_PER_SECOND = 56f
    private const val MIN_DP_PER_SECOND = 8f
    private const val MAX_DP_PER_SECOND = 480f

    fun clamp(value: Float): Float = value.coerceIn(MIN_DP_PER_SECOND, MAX_DP_PER_SECOND)
}
