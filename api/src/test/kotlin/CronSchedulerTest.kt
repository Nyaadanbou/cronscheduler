@file:Suppress("OPT_IN_USAGE")

import cc.mewcraft.cronutils.CronScheduler
import cc.mewcraft.cronutils.ExecutionStatus
import com.cronutils.model.Cron
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.test.*

class CronSchedulerTest {

    // 定义 Cron 表达式解析器
    private val definitionWithoutSeconds = CronDefinitionBuilder.defineCron()
        .withMinutes().withValidRange(0, 59).withStrictRange()
        .and()
        .withHours().withValidRange(0, 23).withStrictRange()
        .and()
        .withDayOfMonth().withValidRange(1, 31).withStrictRange()
        .and()
        .withMonth().withValidRange(1, 12).withStrictRange()
        .and()
        .withDayOfWeek().withValidRange(0, 7).withMondayDoWValue(1).withIntMapping(7, 0).withStrictRange()
        .and()
        .instance()

    private lateinit var executedTasks: MutableList<String>

    private lateinit var pollingClock: MutableClock

    @BeforeEach
    fun setUp() {
        executedTasks = mutableListOf()
        pollingClock = MutableClock(Clock.fixed(Instant.now(), ZoneId.systemDefault()))
    }

    @Test
    fun `test basic cron scheduler`() = runTest {
        val pollingScope = backgroundScope + CoroutineName("test-cron-poller")

        val scheduler = CronScheduler(
            pollingScope,
            pollingClock
        )

        // 使用自定义的 Clock 获取当前时间
        val now = ZonedDateTime.now(pollingClock).truncatedTo(ChronoUnit.MINUTES)

        val cron1 = generateCronForTime(now.plusMinutes(1))
        val cron2 = generateCronForTime(now.plusMinutes(2))
        val cron3 = generateCronForTime(now.plusMinutes(3))

        // 注册任务
        val name1 = "cron1"
        val name2 = "cron2"
        val name3 = "cron3"

        scheduler.schedule(name1, cron1) {
            ping(name1, cron1)
            ExecutionStatus.SUCCESS
        }
        scheduler.schedule(name2, cron2) {
            ping(name2, cron2)
            ExecutionStatus.SUCCESS
        }
        scheduler.schedule(name3, cron3) {
            ping(name3, cron3)
            ExecutionStatus.SUCCESS
        }

        println("Starting the scheduler")

        // 启动调度器
        scheduler.start()

        // 模拟时间流逝
        repeat(60 * 24 * 7) {
            advanceTimeBy(60 * 1000)
            pollingClock.advance(Duration.ofMinutes(1)) // 同步调整自定义 Clock 的时间
        }

        // 验证任务是否执行
        assertEquals(21, executedTasks.size, "Expected 3 tasks to be executed")
        // 验证任务触发次数
        assertEquals(7, executedTasks.count { it == name1 }, "Expected $name1 to be executed 7 times")
        assertEquals(7, executedTasks.count { it == name2 }, "Expected $name2 to be executed 7 times")
        assertEquals(7, executedTasks.count { it == name3 }, "Expected $name3 to be executed 7 times")

        println("Shutting down the scheduler")
        scheduler.shutdown()
    }

    @Test
    fun `test uncaught exceptions in job`() = runTest {
        val pollingScope = backgroundScope + CoroutineName("test-cron-poller")

        val scheduler = CronScheduler(
            pollingScope,
            pollingClock
        )

        val cron1 = generateCronForTime(ZonedDateTime.now(pollingClock).plusMinutes(1))
        val cron2 = generateCronForTime(ZonedDateTime.now(pollingClock).plusMinutes(2)) // 第二个任务的时间

        val name1 = "cron1"
        val name2 = "cron2"

        var cron1Executed = false
        var cron2Executed = false

        // 定义第一个任务：故意抛出异常
        scheduler.schedule(name1, cron1) {
            throwException()
            cron1Executed = true
            ExecutionStatus.SUCCESS
        }

        // 定义第二个任务：正常执行
        scheduler.schedule(name2, cron2) {
            println("Executing job $name2")
            cron2Executed = true
            ExecutionStatus.SUCCESS
        }

        println("Starting the scheduler")

        // 启动调度器
        scheduler.start()

        // 模拟时间流逝，触发 cron1 和 cron2
        repeat(60 * 24) {
            advanceTimeBy(60 * 1000)
            pollingClock.advance(Duration.ofMinutes(1)) // 同步调整自定义 Clock 的时间
        }

        assertFalse(cron1Executed, "Cron1 should have been executed even though it threw an exception")
        assertTrue(cron2Executed, "Cron2 should have been executed even though cron1 threw an exception")

        println("Shutting down the scheduler")
        scheduler.shutdown()
    }

    // 根据指定时间生成对应的 Cron 表达式
    private fun generateCronForTime(time: ZonedDateTime): Cron {
        val cronExpression = "0 2 * * *" // 每天 2:00AM
        println("generated cron expression: $cronExpression")
        return CronParser(definitionWithoutSeconds).parse(cronExpression)
    }

    // 处理任务执行结果
    private fun ping(name: String, cron: Cron) {
        val currentTime = ZonedDateTime.now(pollingClock).truncatedTo(ChronoUnit.MINUTES)
        println("$name - ${cron.asString()} - executed at $currentTime")
        executedTasks.add(name) // 记录执行的任务
    }

    private fun throwException() {
        throw RuntimeException("Uncaught exception in job")
    }
}

// 自定义 Clock 类, 用于同步虚拟时间
class MutableClock(
    private var base: Clock,
) : Clock() {
    private var offset: Duration = Duration.ZERO

    fun advance(duration: Duration) {
        offset = offset.plus(duration)
    }

    override fun getZone(): ZoneId {
        return base.zone
    }

    override fun withZone(zone: ZoneId): Clock {
        base = base.withZone(zone)
        return this
    }

    override fun instant(): Instant {
        return base.instant().plus(offset)
    }
}