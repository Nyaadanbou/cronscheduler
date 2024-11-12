package com.cronutils

import com.cronutils.model.Cron
import com.cronutils.model.field.CronFieldName
import com.cronutils.model.time.ExecutionTime
import java.time.Clock
import java.time.ZonedDateTime
import kotlin.jvm.optionals.getOrNull

class CronTrigger(
    private val cron: Cron,
    private val clock: Clock,
) {
    init {
        if (cron.cronDefinition.containsFieldDefinition(CronFieldName.SECOND))
            throw IllegalArgumentException("SECOND field is not supported")
    }

    fun matchTime(time: ZonedDateTime): Boolean =
        ExecutionTime.forCron(cron).isMatch(time)

    fun nextExecution(): ZonedDateTime? =
        ExecutionTime.forCron(cron).nextExecution(ZonedDateTime.now(clock)).getOrNull()
}