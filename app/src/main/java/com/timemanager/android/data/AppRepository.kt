package com.timemanager.android.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.timemanager.android.data.entity.*
import com.timemanager.android.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant

val Context.dataStore by preferencesDataStore(name = "settings")

class AppRepository(private val context: Context) {
    val db: TimeManagerDatabase = Room.databaseBuilder(context, TimeManagerDatabase::class.java, "timemanager.db")
        .addMigrations(TimeManagerDatabase.MIGRATION_1_2, TimeManagerDatabase.MIGRATION_2_3)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()

    // ---- Rules ----
    suspend fun listRules(): List<Rule> = db.ruleDao().listAll().map { it.toDomain() }
    suspend fun listEnabledRules(): List<Rule> = db.ruleDao().listEnabled().map { it.toDomain() }
    suspend fun getRule(id: Long): Rule? = db.ruleDao().getById(id)?.toDomain()

    suspend fun addRule(rule: Rule): Long {
        rule.validate()
        val e = rule.toEntity()
        return db.ruleDao().insert(e)
    }
    suspend fun updateRule(rule: Rule) {
        requireNotNull(rule.id)
        rule.validate()
        db.ruleDao().update(rule.toEntity())
    }
    suspend fun deleteRule(id: Long) {
        db.ruleDao().deleteById(id)
        db.sessionDao().deleteForRule(id)
        db.dailyDao().deleteForRule(id)
    }

    // ---- Sessions / daily ----
    suspend fun openSession(ruleId: Long, sessionType: String, startedAt: String): Long {
        val monotonic = android.os.SystemClock.elapsedRealtime() / 1000.0
        return db.sessionDao().insert(UsageSessionEntity(ruleId = ruleId, startedAt = startedAt, monotonicStart = monotonic, sessionType = sessionType))
    }
    suspend fun heartbeatSession(id: Long, dur: Int) = db.sessionDao().heartbeat(id, dur)
    suspend fun closeSession(id: Long, dur: Int) = db.sessionDao().close(id, dur, Instant.now().toString())
    suspend fun openSessions() = db.sessionDao().openSessions()

    suspend fun addDaily(ruleId: Long, day: String, seconds: Int) =
        db.dailyDao().add(ruleId, day, seconds, Instant.now().toString())
    suspend fun getDaily(ruleId: Long, day: String): Int = db.dailyDao().get(ruleId, day) ?: 0

    suspend fun history(ruleId: Long) = db.dailyDao().history(ruleId)

    // ---- Enforcement state ----
    suspend fun getEnforcementState(ruleId: Long): Triple<String, RuleState, Set<Int>>? {
        val e = db.enforcementStateDao().get(ruleId) ?: return null
        val warned = try { Json.decodeFromString<List<Int>>(e.warnedThresholds).toSet() } catch (_: Exception) { emptySet() }
        val state = try { RuleState.valueOf(e.state) } catch (_: Exception) { RuleState.NORMAL }
        return Triple(e.day, state, warned)
    }
    suspend fun putEnforcementState(ruleId: Long, day: String, state: RuleState, warned: Set<Int>) {
        db.enforcementStateDao().upsert(EnforcementStateEntity(ruleId, day, state.name, Json.encodeToString(warned.toList()), Instant.now().toString()))
    }

    // ---- Settings (DataStore) ----
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val monitoringInterval = doublePreferencesKey("monitoring_interval")
        val defaultWarnings = stringPreferencesKey("default_warning_seconds")
        val enforceForegroundOnly = booleanPreferencesKey("enforce_foreground_only")
        val backgroundGrace = intPreferencesKey("background_grace_seconds")
        val idleGrace = intPreferencesKey("idle_grace_seconds")
        val launchAtStartup = booleanPreferencesKey("launch_at_startup")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val pauseUntil = stringPreferencesKey("pause_until")
        val strictWatchdog = booleanPreferencesKey("strict_watchdog")
        val websiteGrace = intPreferencesKey("website_grace_seconds")
        val websiteStale = intPreferencesKey("website_stale_seconds")
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { it[Keys.theme] ?: "system" }
    val languageFlow: Flow<String> = context.dataStore.data.map { it[Keys.language] ?: "en" }
    val pauseUntilFlow: Flow<String?> = context.dataStore.data.map { it[Keys.pauseUntil] }

    suspend fun setTheme(v: String) { context.dataStore.edit { it[Keys.theme] = v } }
    suspend fun setLanguage(v: String) { context.dataStore.edit { it[Keys.language] = v } }
    suspend fun setPauseUntil(v: String?) {
        context.dataStore.edit { if (v == null) it.remove(Keys.pauseUntil) else it[Keys.pauseUntil] = v }
    }
    suspend fun getPauseUntil(): String? = context.dataStore.data.map { it[Keys.pauseUntil] }.let {
        var out: String? = null
        // quick sync read via prefs snapshot
        kotlinx.coroutines.runBlocking {
            out = context.dataStore.data.map { p -> p[Keys.pauseUntil] }.let {
                var r: String? = null
                kotlinx.coroutines.flow.firstOrNull(it)?.let { r = it }
                r
            }
        }
        out
    }

    // ---- PDFs ----
    suspend fun listPdfs() = db.pdfDao().listAll()
    suspend fun addPdf(displayName: String, uri: String, sizeBytes: Long): Long =
        db.pdfDao().insert(PdfDocumentEntity(displayName = displayName, uri = uri, sizeBytes = sizeBytes, importedAt = Instant.now().toString()))
    suspend fun touchPdf(id: Long) = db.pdfDao().touch(id, Instant.now().toString())
    suspend fun deletePdf(id: Long) = db.pdfDao().deleteById(id)

    // ---- Audit helpers ----
    suspend fun logClock(note: String) {
        db.clockLogDao().insert(ClockLogEntity(recordedAt = Instant.now().toString(), monotonic = android.os.SystemClock.elapsedRealtime() / 1000.0, note = note))
    }
    suspend fun logEnforcement(ruleId: Long, day: String, action: String, mode: String, outcome: String, detail: String?) {
        db.enforcementLogDao().insert(EnforcementLogEntity(ruleId = ruleId, day = day, action = action, mode = mode, outcome = outcome, detail = detail, createdAt = Instant.now().toString(), monotonicAt = android.os.SystemClock.elapsedRealtime() / 1000.0))
    }
    suspend fun logAgent(kind: String, severity: String = "info", detail: String? = null) {
        db.agentAuditDao().insert(AgentAuditEntity(recordedAt = Instant.now().toString(), monotonic = android.os.SystemClock.elapsedRealtime() / 1000.0, kind = kind, severity = severity, detail = detail))
    }

    // ---- Backup helpers (JSON) ----
    suspend fun exportBackupJson(): String {
        val rules = db.ruleDao().listAll()
        val daily = db.dailyDao().allHistory()
        val sessions = db.sessionDao().openSessions()
        val settings = db.settingsDao().all()
        return Json.encodeToString(
            kotlinx.serialization.json.JsonObject(
                mapOf(
                    "version" to kotlinx.serialization.json.JsonPrimitive(3),
                    "exportedAt" to kotlinx.serialization.json.JsonPrimitive(Instant.now().toString()),
                    "rules" to Json.encodeToJsonElement(rules),
                    "dailyUsage" to Json.encodeToJsonElement(daily),
                    "settings" to Json.encodeToJsonElement(settings)
                )
            )
        )
    }

    // mapping helpers
    private fun RuleEntity.toDomain(): Rule {
        val extraP = try { Json.decodeFromString<List<String>>(extraPackages) } catch (_: Exception) { emptyList() }
        val warns = try { Json.decodeFromString<List<Int>>(warningSeconds) } catch (_: Exception) { listOf(600, 300, 60) }
        val sched = schedule?.let { Schedule.parse(it) }
        return Rule(
            id = id, name = name, type = RuleType.valueOf(type), target = target,
            action = Action.valueOf(action), mode = Mode.valueOf(mode),
            packageName = packageName, extraPackages = extraP, domain = domain,
            dailyLimitSeconds = dailyLimitSeconds, sessionLimitSeconds = sessionLimitSeconds,
            warningSeconds = warns, schedule = sched, enabled = enabled != 0,
            executable = executable, extraExecutables = try { Json.decodeFromString<List<String>>(extraExecutables) } catch (_: Exception) { emptyList() }
        )
    }
    private fun Rule.toEntity(): RuleEntity = RuleEntity(
        id = id ?: 0, name = name, type = type.name, target = target,
        packageName = packageName, extraPackages = Json.encodeToString(extraPackages),
        executable = executable, extraExecutables = Json.encodeToString(extraExecutables),
        domain = domain, dailyLimitSeconds = dailyLimitSeconds, sessionLimitSeconds = sessionLimitSeconds,
        warningSeconds = Json.encodeToString(warningSeconds), action = action.name, mode = mode.name,
        schedule = schedule?.toJson(), enabled = if (enabled) 1 else 0,
        createdAt = Instant.now().toString(), updatedAt = Instant.now().toString()
    )
}

// simple helpers for Flow firstOrNull
import kotlinx.coroutines.flow.firstOrNull
