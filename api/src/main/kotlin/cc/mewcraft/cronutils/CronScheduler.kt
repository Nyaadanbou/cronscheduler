@file:Suppress("UnstableApiUsage")

package cc.mewcraft.cronutils

import com.cronutils.model.Cron
import com.google.common.util.concurrent.ThreadFactoryBuilder
import kotlinx.coroutines.*
import java.time.Clock
import java.time.ZonedDateTime
import java.util.Collections
import java.util.LinkedList
import java.util.concurrent.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class CronScheduler(
    pollingScope: CoroutineScope? = null,
    private val pollingClock: Clock = Clock.systemDefaultZone(),
) {
    private companion object {
        private val pollingExecutor: ExecutorService = Executors.newSingleThreadExecutor(
            ThreadFactoryBuilder().setNameFormat("cron-poller-%d").setThreadFactory(Thread.ofVirtual().factory()).build()
        )
        private val workingExecutor: ExecutorService = Executors.newCachedThreadPool(
            ThreadFactoryBuilder().setNameFormat("cron-worker-%d").build()
        )
    }

    private val pollingScope: CoroutineScope = pollingScope ?: (CoroutineScope(pollingExecutor.asCoroutineDispatcher()) + CoroutineName("cron-poller") + SupervisorJob())
    private val workingScope: CoroutineScope = CoroutineScope(workingExecutor.asCoroutineDispatcher()) + CoroutineName("cron-worker") + SupervisorJob() + CoroutineExceptionHandler { ctx, ex ->
        println("An error occurred while running job (id: ${ctx[CoroutineName]?.name})")
        ex.printStackTrace()
    }

    private val executingJobIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val executableUnits: MutableList<ExecutableUnit> = Collections.synchronizedList(LinkedList())

    /**
     * Schedules a cron job with a given name and cron expression.
     */
    fun schedule(name: String, cron: Cron, action: () -> ExecutionStatus) {
        val tri = CronTrigger(cron, pollingClock)

        val job = object : CronJob(name) {
            override fun execute(): ExecutionStatus {
                return action()
            }
        }

        val unit = ExecutableUnit(tri, job).apply {
            this.job.addStatusHook {
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
                                next.job.run()
                            } finally {
                                executingJobIds.remove(nextJobId) // 始终移除任务 ID
                            }
                        }
                    }
                }

                delay(1.toDuration(DurationUnit.MINUTES))
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

            pollingExecutor.shutdown()
            workingExecutor.shutdown()

            if (!pollingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw TimeoutException("Polling executor did not terminate in the specified time.")
            }

            if (!workingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw TimeoutException("Working executor did not terminate in the specified time.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}