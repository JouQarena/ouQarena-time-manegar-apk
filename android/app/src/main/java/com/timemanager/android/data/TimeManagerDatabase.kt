package com.timemanager.android.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.timemanager.android.data.dao.*
import com.timemanager.android.data.entity.*

/**
 * Room DB mirroring desktop schema.sql v3
 * Tables: rules, usage_sessions, daily_usage, enforcement_state, browser_events (→ web_events on Android),
 * settings, clock_log, enforcement_log, agent_audit + plus Android-specific pdf_documents
 */
@Database(
    entities = [
        RuleEntity::class, UsageSessionEntity::class, DailyUsageEntity::class,
        EnforcementStateEntity::class, BrowserEventEntity::class, SettingEntity::class,
        ClockLogEntity::class, EnforcementLogEntity::class, AgentAuditEntity::class,
        PdfDocumentEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TimeManagerDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun sessionDao(): SessionDao
    abstract fun dailyDao(): DailyDao
    abstract fun enforcementStateDao(): EnforcementStateDao
    abstract fun browserEventDao(): BrowserEventDao
    abstract fun settingsDao(): SettingsDao
    abstract fun clockLogDao(): ClockLogDao
    abstract fun enforcementLogDao(): EnforcementLogDao
    abstract fun agentAuditDao(): AgentAuditDao
    abstract fun pdfDao(): PdfDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS enforcement_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ruleId INTEGER NOT NULL,
                        day TEXT NOT NULL,
                        action TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        outcome TEXT NOT NULL,
                        detail TEXT,
                        createdAt TEXT NOT NULL,
                        monotonicAt REAL NOT NULL,
                        FOREIGN KEY(ruleId) REFERENCES rules(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_enforcement_rule_day ON enforcement_log(ruleId, day)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS agent_audit (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recordedAt TEXT NOT NULL,
                        monotonic REAL NOT NULL,
                        kind TEXT NOT NULL,
                        severity TEXT NOT NULL DEFAULT 'info',
                        detail TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_agent_audit_kind ON agent_audit(kind)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_agent_audit_time ON agent_audit(recordedAt)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pdf_documents (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        displayName TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL DEFAULT 0,
                        importedAt TEXT NOT NULL,
                        lastOpenedAt TEXT
                    )
                """.trimIndent())
            }
        }
    }
}

class Converters {
    @TypeConverter fun fromList(list: List<Int>): String = list.joinToString(",", "[", "]")
    @TypeConverter fun toList(s: String): List<Int> = try {
        s.trim().removeSurrounding("[", "]").split(",").mapNotNull { it.trim().toIntOrNull() }
    } catch (_: Exception) { emptyList() }
}
