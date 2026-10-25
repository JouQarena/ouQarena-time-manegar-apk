package com.timemanager.android.data.dao

import androidx.room.*
import com.timemanager.android.data.entity.*

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY name") suspend fun listAll(): List<RuleEntity>
    @Query("SELECT * FROM rules WHERE enabled=1 ORDER BY name") suspend fun listEnabled(): List<RuleEntity>
    @Query("SELECT * FROM rules WHERE id=:id") suspend fun getById(id: Long): RuleEntity?
    @Insert suspend fun insert(rule: RuleEntity): Long
    @Update suspend fun update(rule: RuleEntity)
    @Query("DELETE FROM rules WHERE id=:id") suspend fun deleteById(id: Long)
    @Query("SELECT * FROM rules WHERE packageName=:pkg OR executable=:pkg LIMIT 1") suspend fun findByPackage(pkg: String): RuleEntity?
}

@Dao
interface SessionDao {
    @Insert suspend fun insert(session: UsageSessionEntity): Long
    @Query("UPDATE usage_sessions SET durationSeconds=:dur WHERE id=:id AND completed=0") suspend fun heartbeat(id: Long, dur: Int)
    @Query("UPDATE usage_sessions SET endedAt=:endedAt, durationSeconds=:dur, completed=1 WHERE id=:id AND completed=0") suspend fun close(id: Long, dur: Int, endedAt: String)
    @Query("SELECT * FROM usage_sessions WHERE completed=0 ORDER BY startedAt") suspend fun openSessions(): List<UsageSessionEntity>
    @Query("SELECT * FROM usage_sessions WHERE ruleId=:ruleId ORDER BY startedAt DESC LIMIT 20") suspend fun recentForRule(ruleId: Long): List<UsageSessionEntity>
    @Query("DELETE FROM usage_sessions WHERE ruleId=:ruleId") suspend fun deleteForRule(ruleId: Long)
}

@Dao
interface DailyDao {
    @Query("SELECT totalSeconds FROM daily_usage WHERE ruleId=:ruleId AND day=:day") suspend fun get(ruleId: Long, day: String): Int?
    @Query("""
        INSERT INTO daily_usage(ruleId, day, totalSeconds, updatedAt)
        VALUES(:ruleId, :day, :seconds, :now)
        ON CONFLICT(ruleId, day) DO UPDATE SET totalSeconds=totalSeconds+:seconds, updatedAt=:now
    """) suspend fun add(ruleId: Long, day: String, seconds: Int, now: String)
    @Query("SELECT * FROM daily_usage WHERE ruleId=:ruleId ORDER BY day DESC LIMIT 30") suspend fun history(ruleId: Long): List<DailyUsageEntity>
    @Query("DELETE FROM daily_usage WHERE ruleId=:ruleId") suspend fun deleteForRule(ruleId: Long)
    @Query("SELECT * FROM daily_usage ORDER BY day DESC LIMIT 100") suspend fun allHistory(): List<DailyUsageEntity>
}

@Dao
interface EnforcementStateDao {
    @Query("SELECT * FROM enforcement_state WHERE ruleId=:ruleId") suspend fun get(ruleId: Long): EnforcementStateEntity?
    @Query("SELECT * FROM enforcement_state") suspend fun all(): List<EnforcementStateEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(state: EnforcementStateEntity)
    @Query("DELETE FROM enforcement_state WHERE ruleId=:ruleId") suspend fun clear(ruleId: Long)
}

@Dao
interface BrowserEventDao {
    @Insert suspend fun insert(e: BrowserEventEntity): Long
    @Query("SELECT * FROM browser_events ORDER BY receivedAt DESC LIMIT 100") suspend fun recent(): List<BrowserEventEntity>
    @Query("DELETE FROM browser_events WHERE receivedAt < :before") suspend fun prune(before: String)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE key=:k") suspend fun get(k: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun set(s: SettingEntity)
    @Query("DELETE FROM settings WHERE key=:k") suspend fun delete(k: String)
    @Query("SELECT * FROM settings") suspend fun all(): List<SettingEntity>
}

@Dao
interface ClockLogDao {
    @Insert suspend fun insert(e: ClockLogEntity): Long
    @Query("SELECT * FROM clock_log ORDER BY recordedAt DESC LIMIT 50") suspend fun recent(): List<ClockLogEntity>
}

@Dao
interface EnforcementLogDao {
    @Insert suspend fun insert(e: EnforcementLogEntity): Long
    @Query("SELECT * FROM enforcement_log WHERE ruleId=:ruleId ORDER BY createdAt DESC LIMIT 100") suspend fun forRule(ruleId: Long): List<EnforcementLogEntity>
    @Query("SELECT * FROM enforcement_log ORDER BY createdAt DESC LIMIT 200") suspend fun recent(): List<EnforcementLogEntity>
}

@Dao
interface AgentAuditDao {
    @Insert suspend fun insert(e: AgentAuditEntity): Long
    @Query("SELECT * FROM agent_audit ORDER BY recordedAt DESC LIMIT 100") suspend fun recent(): List<AgentAuditEntity>
}

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdf_documents ORDER BY importedAt DESC") suspend fun listAll(): List<PdfDocumentEntity>
    @Query("SELECT * FROM pdf_documents WHERE id=:id") suspend fun getById(id: Long): PdfDocumentEntity?
    @Insert suspend fun insert(e: PdfDocumentEntity): Long
    @Query("UPDATE pdf_documents SET lastOpenedAt=:at WHERE id=:id") suspend fun touch(id: Long, at: String)
    @Query("DELETE FROM pdf_documents WHERE id=:id") suspend fun deleteById(id: Long)
}
