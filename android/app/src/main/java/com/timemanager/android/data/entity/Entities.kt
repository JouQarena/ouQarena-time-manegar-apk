package com.timemanager.android.data.entity

import androidx.room.*

@Entity(tableName = "rules", indices = [Index("type"), Index("enabled")])
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // APPLICATION|GAME|WEBSITE
    val target: String,
    val packageName: String? = null, // Android (replaces executable)
    val extraPackages: String = "[]", // JSON array
    val executable: String? = null, // legacy desktop compat
    val extraExecutables: String = "[]",
    val domain: String? = null,
    val dailyLimitSeconds: Int? = null,
    val sessionLimitSeconds: Int? = null,
    val warningSeconds: String = "[600,300,60]",
    val action: String, // BLOCK|CLOSE|WAIT_FOR_SESSION_END|WARN_ONLY
    val mode: String, // NORMAL|STRICT
    val schedule: String? = null, // JSON
    val enabled: Int = 1,
    val createdAt: String,
    val updatedAt: String
)

@Entity(tableName = "usage_sessions", indices = [Index(value = ["ruleId", "startedAt"]), Index("completed")])
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val startedAt: String,
    val endedAt: String? = null,
    val durationSeconds: Int = 0,
    val monotonicStart: Double? = null,
    val sessionType: String = "FOREGROUND",
    val completed: Int = 0,
    val createdFromRecovery: Int = 0
)

@Entity(tableName = "daily_usage", primaryKeys = ["ruleId", "day"])
data class DailyUsageEntity(
    val ruleId: Long,
    val day: String, // YYYY-MM-DD
    val totalSeconds: Int = 0,
    val updatedAt: String
)

@Entity(tableName = "enforcement_state")
data class EnforcementStateEntity(
    @PrimaryKey val ruleId: Long,
    val day: String,
    val state: String,
    val warnedThresholds: String = "[]",
    val updatedAt: String
)

@Entity(tableName = "browser_events", indices = [Index(value = ["domain", "receivedAt"])])
data class BrowserEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val browser: String, // chrome|firefox|samsung etc
    val domain: String,
    val active: Int,
    val receivedAt: String,
    val monotonicAt: Double,
    val processed: Int = 0
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String, // JSON-encoded
    val updatedAt: String
)

@Entity(tableName = "clock_log")
data class ClockLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordedAt: String,
    val monotonic: Double,
    val note: String? = null
)

@Entity(tableName = "enforcement_log", indices = [Index(value = ["ruleId", "day"])])
data class EnforcementLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val day: String,
    val action: String,
    val mode: String,
    val outcome: String, // EXECUTED|SKIPPED|DEFERRED|FAILED|PROTECTED
    val detail: String? = null,
    val createdAt: String,
    val monotonicAt: Double
)

@Entity(tableName = "agent_audit", indices = [Index("kind"), Index("recordedAt")])
data class AgentAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordedAt: String,
    val monotonic: Double,
    val kind: String, // START|STOP|UNEXPECTED_STOP|USAGE_TAMPERED|...
    val severity: String = "info",
    val detail: String? = null
)

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val uri: String, // SAF persisted URI string
    val sizeBytes: Long = 0,
    val importedAt: String,
    val lastOpenedAt: String? = null
)
