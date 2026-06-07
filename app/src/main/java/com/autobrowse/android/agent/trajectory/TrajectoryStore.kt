package com.autobrowse.android.agent.trajectory

import com.autobrowse.android.data.local.dao.TrajectoryDao
import com.autobrowse.android.data.local.entity.TrajectoryEntity
import com.autobrowse.android.domain.model.AgentTurn
import com.autobrowse.android.domain.model.TrajectoryRecord
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class TrajectoryStore(
    private val trajectoryDao: TrajectoryDao,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val turnsAdapter = moshi.adapter<List<AgentTurn>>(
        Types.newParameterizedType(List::class.java, AgentTurn::class.java),
    )

    suspend fun save(
        sessionId: String,
        taskId: String,
        prompt: String,
        success: Boolean,
        turns: List<AgentTurn>,
        reflection: String? = null,
    ) = withContext(Dispatchers.IO) {
        trajectoryDao.insert(
            TrajectoryEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                taskId = taskId,
                prompt = prompt,
                success = success,
                turnsJson = turnsAdapter.toJson(turns),
                reflection = reflection,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun getRecentFailures(limit: Int = 5): List<TrajectoryRecord> = withContext(Dispatchers.IO) {
        trajectoryDao.getFailures(limit).map { it.toDomain() }
    }

    private fun TrajectoryEntity.toDomain() = TrajectoryRecord(
        id = id,
        sessionId = sessionId,
        taskId = taskId,
        prompt = prompt,
        success = success,
        turnsJson = turnsJson,
        reflection = reflection,
        createdAt = createdAt,
    )
}