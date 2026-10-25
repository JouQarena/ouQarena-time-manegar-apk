package com.timemanager.android.domain

enum class RuleType { APPLICATION, GAME, WEBSITE }
enum class Action { BLOCK, CLOSE, WAIT_FOR_SESSION_END, WARN_ONLY }
enum class Mode { NORMAL, STRICT }
enum class RuleState { NORMAL, WARNING, LIMIT_REACHED, WAITING_FOR_SESSION_END, ENFORCED, DISABLED }
enum class SessionType { FOREGROUND, WEBSITE, GAME }
enum class LimitReason { NONE, DAILY_LIMIT_REACHED, SESSION_LIMIT_REACHED, SCHEDULE_BLOCKED, MANUAL }
