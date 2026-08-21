package com.defang.launcher.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.defang.launcher.data.local.db.entity.AdaptiveGateStateEntity

@Dao
interface AdaptiveGateStateDao {

    @Query("SELECT * FROM adaptive_gate_state WHERE gateKey = :gateKey LIMIT 1")
    suspend fun get(gateKey: String): AdaptiveGateStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: AdaptiveGateStateEntity)

    /** Manual override: wipe every gate's escalation back to baseline. */
    @Query("DELETE FROM adaptive_gate_state")
    suspend fun deleteAll()
}
