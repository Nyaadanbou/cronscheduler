package cc.mewcraft.cronutils

import com.google.common.util.concurrent.ThreadFactoryBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * 通过 [ExecutorService] 实现的 Polling [CoroutineDispatcher] (用于定时任务).
 */
internal class PollingCoroutineDispatcher(
    private val executor: ExecutorService,
) : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        // 使用 executor 提交任务
        executor.submit(block)
    }

    fun shutdown() {
        executor.shutdown() // 关闭线程池
    }

    companion object {
        // 创建单线程的定时任务调度器，使用虚拟线程
        fun create(): PollingCoroutineDispatcher {
            val threadFactory = ThreadFactoryBuilder()
                .setNameFormat("cron-poller-%d")
                .setThreadFactory(Thread.ofVirtual().factory()) // 使用虚拟线程
                .build()

            val executor = Executors.newSingleThreadScheduledExecutor(threadFactory)
            return PollingCoroutineDispatcher(executor)
        }
    }
}

/**
 * 通过 [ExecutorService] 实现的 Worker [CoroutineDispatcher] (用于工作任务处理).
 */
internal class WorkerCoroutineDispatcher(
    private val executor: ExecutorService,
) : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        // 使用 executor 提交任务
        executor.submit(block)
    }

    fun shutdown() {
        executor.shutdown() // 关闭线程池
    }

    companion object {
        // 创建一个带缓存线程池的工作调度器
        fun create(): WorkerCoroutineDispatcher {
            val threadFactory = ThreadFactoryBuilder()
                .setNameFormat("cron-worker-%d")
                .build()

            val executor = Executors.newCachedThreadPool(threadFactory)
            return WorkerCoroutineDispatcher(executor)
        }
    }
}
