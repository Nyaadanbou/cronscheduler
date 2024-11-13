@file:Suppress("UnstableApiUsage")

package cc.mewcraft.cronutils

import com.cronutils.model.Cron
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap

class CronScheduler(
    private val pollingScope: CoroutineScope = CoroutineScope(PollingCoroutineDispatcher.create() + CoroutineName("cron-poller") + SupervisorJob()),
    private val pollingClock: Clock = Clock.systemDefaultZone(),
) {
    private companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }

    // we catch uncaught exceptions in the job, so we don't need to handle them here
    private val workingScope: CoroutineScope = CoroutineScope(WorkerCoroutineDispatcher.create() + CoroutineName("cron-worker"))

    private val executingJobIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val executableUnits: MutableList<ExecutableUnit> = Collections.synchronizedList(LinkedList())

    /**
     * Schedules a cron job with a given name and cron expression.
     */
    fun schedule(name: String, cron: Cron, action: suspend () -> ExecutionStatus) {
        val tri = CronTrigger(cron, pollingClock)

        val job = CronJob(name, action)

        val unit = ExecutableUnit(tri, job).apply {
            this.job.addStatusCallback {
                executingJobIds.remove(this.job.id)
            }
        }

        executableUnits.add(unit)
    }

    /**
     * Starts polling cron jobs.
     *
     * The polling will run once at every minute. For each polling, it loops through all the cron jobs and executes them
     * if current date is matched with the cron date. If the cron date is impossible to reach, it will be removed from
     * the job list permanently.
     */
    fun start() {
        pollingScope.launch {
            while (isActive) {
                println("Polling cron jobs at ${LocalDateTime.now(pollingClock).format(formatter)}")

                val now = ZonedDateTime.now(pollingClock)
                val iterator = executableUnits.listIterator()
                while (iterator.hasNext()) {
                    val next = iterator.next()

                    if (next.trigger.nextExecution() == null) {
                        // If the cron can NEVER reach, simply remove it from job list.
                        iterator.remove()
                        continue
                    }

                    val nextJobId = next.job.id
                    if (nextJobId !in executingJobIds && next.trigger.matchTime(now)) {
                        workingScope.launch(CoroutineName(nextJobId)) {
                            try {
                                yield() // Stop here if the job is cancelled
                                executingJobIds.add(nextJobId)
                                next.job.execute()
                            } catch (e: CancellationException) {
                                println("Job $nextJobId is cancelled")
                            } finally {
                                executingJobIds.remove(nextJobId) // 始终移除任务 ID
                            }
                        }
                    }
                }

                delay(60 * 1000)
            }
        }
    }

    /**
     * Shutdown the polling task and running scheduled tasks.
     */
    fun shutdown() {
        try {
            pollingScope.cancel("Shutting down polling scope")
            workingScope.cancel("Shutting down working scope")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}