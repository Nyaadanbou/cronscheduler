@file:Suppress("UnstableApiUsage")

package cc.mewcraft.cronutils

import com.cronutils.model.Cron
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
    private val executableUnits: MutableList<ExecutableUnit> = CopyOnWriteArrayList()

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

                    // 执行任务必须满足两个条件:
                    // 1. 任务不存在于[执行中]列表
                    // 2. 当前时间匹配任务的触发时间
                    //
                    // 对于 1, 这也就意味着如果一个任务无限执行下去 (极端情况),
                    // 并且轮到了它的下一个执行时间, 那么这个任务将不会再次执行.
                    if (nextJobId !in executingJobIds && next.trigger.matchTime(now)) {
                        workingScope.launch(CoroutineName(nextJobId)) {
                            try {
                                yield() // Stop here if the job is cancelled
                                executingJobIds.add(nextJobId)
                                next.job.execute()
                            } catch (e: CancellationException) {
                                println("Job $nextJobId is cancelled")
                            } finally {
                                // 执行完毕后, 从[执行中]列表移除任务 ID
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