package uz.selerown.fevapp.core.model

/** Lightweight info shown on the Home screen grid. */
data class ProjectSummary(
    val id: String,
    val name: String,
    val updatedAtMillis: Long,
    val durationUs: Long,
    val thumbnailUri: String?,
)
