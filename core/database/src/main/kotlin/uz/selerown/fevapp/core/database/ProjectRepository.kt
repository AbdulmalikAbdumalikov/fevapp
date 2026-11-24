package uz.selerown.fevapp.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import uz.selerown.fevapp.core.common.IdGenerator
import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.Project
import uz.selerown.fevapp.core.model.ProjectSummary
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for saved projects. */
interface ProjectRepository {
    fun observeProjects(): Flow<List<ProjectSummary>>
    suspend fun get(id: String): Project?
    suspend fun save(project: Project)
    suspend fun create(name: String, clips: List<Clip>): Project
    suspend fun rename(id: String, name: String)
    suspend fun duplicate(id: String, newName: String): String?
    suspend fun delete(id: String)
}

@Singleton
internal class RoomProjectRepository @Inject constructor(
    private val dao: ProjectDao,
) : ProjectRepository {

    private val ids = IdGenerator.Random
    private val json = Json {
        ignoreUnknownKeys = true // older app versions can open newer files
        encodeDefaults = true
    }

    override fun observeProjects(): Flow<List<ProjectSummary>> =
        dao.observeSummaries().map { rows ->
            rows.map { ProjectSummary(it.id, it.name, it.updatedAt, it.durationUs, it.thumbnailUri) }
        }

    override suspend fun get(id: String): Project? =
        dao.get(id)?.let { entity ->
            runCatching { json.decodeFromString(Project.serializer(), entity.timelineJson) }.getOrNull()
        }

    override suspend fun save(project: Project) {
        dao.upsert(project.toEntity())
    }

    override suspend fun create(name: String, clips: List<Clip>): Project {
        val now = System.currentTimeMillis()
        val project = Project.empty(ids.newId(), name, now) { ids.newId() }
            .updateMainTrack { it.copy(clips = clips) }
        save(project)
        return project
    }

    override suspend fun rename(id: String, name: String) {
        val project = get(id) ?: return
        save(project.copy(name = name, updatedAtMillis = System.currentTimeMillis()))
    }

    override suspend fun duplicate(id: String, newName: String): String? {
        val project = get(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = project.copy(id = ids.newId(), name = newName, createdAtMillis = now, updatedAtMillis = now)
        save(copy)
        return copy.id
    }

    override suspend fun delete(id: String) = dao.delete(id)

    private fun Project.toEntity() = ProjectEntity(
        id = id,
        name = name,
        createdAt = createdAtMillis,
        updatedAt = updatedAtMillis,
        durationUs = durationUs,
        thumbnailUri = mainTrack.clips.firstOrNull()?.source?.uri,
        timelineJson = json.encodeToString(Project.serializer(), this),
    )
}
