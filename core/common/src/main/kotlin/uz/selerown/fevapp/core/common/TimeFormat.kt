package uz.selerown.fevapp.core.common

/** Human readable time strings for the UI. */
object TimeFormat {

    /** 65_300_000 us -> "01:05". Hours are added only when needed. */
    fun clock(us: Long): String {
        val totalSeconds = (us.coerceAtLeast(0) / 1_000_000L)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** 2_500_000 us -> "2.5s" (used on clips and the ruler). */
    fun short(us: Long): String {
        val tenths = (us.coerceAtLeast(0) / 100_000L)
        return if (tenths % 10 == 0L) "${tenths / 10}s" else "${tenths / 10}.${tenths % 10}s"
    }
}
