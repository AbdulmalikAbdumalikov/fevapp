package uz.selerown.fevapp.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per project. The full timeline is stored as JSON ([timelineJson])
 * so new timeline features never need a table migration; the other columns
 * are denormalized copies for the fast Home screen query.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val durationUs: Long,
    val thumbnailUri: String?,
    val timelineJson: String,
)

/** Columns needed by the Home grid (avoids loading the JSON). */
data class ProjectSummaryRow(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val durationUs: Long,
    val thumbnailUri: String?,
)
