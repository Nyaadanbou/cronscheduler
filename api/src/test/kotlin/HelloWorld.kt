import com.cronutils.CronScheduler
import com.cronutils.ExecutionStatus
import com.cronutils.model.Cron
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.Test

class HelloWorld {
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
        .withSupportedNicknameYearly()
        .withSupportedNicknameAnnually()
        .withSupportedNicknameMonthly()
        .withSupportedNicknameWeekly()
        .withSupportedNicknameMidnight()
        .withSupportedNicknameDaily()
        .withSupportedNicknameHourly()
        .instance()

    @Test
    fun test() {
        val scheduler = CronScheduler()

        // 获取当前时间和下一分钟时间
        val now = ZonedDateTime.now()
        val nextMinute = now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)

        // 动态生成3个 Cron 表达式, 分别为接下来 1 分钟、2 分钟和 3 分钟后触发
        val cron1 = generateCronForTime(nextMinute)
        val cron2 = generateCronForTime(nextMinute.plusMinutes(1))
        val cron3 = generateCronForTime(nextMinute.plusMinutes(2))

        val name1 = "cron1"
        val name2 = "cron2"
        val name3 = "cron3"

        // 注册 3 个 Cron 任务
        scheduler.scheduleCronJob(name1, cron1) {
            ping(name1, cron1)
            ExecutionStatus.SUCCESS
        }
        scheduler.scheduleCronJob(name2, cron2) {
            ping(name2, cron2)
            ExecutionStatus.SUCCESS
        }
        scheduler.scheduleCronJob(name3, cron3) {
            ping(name3, cron3)
            ExecutionStatus.SUCCESS
        }

        // 启动调度器
        scheduler.startPollingTask()

        // 等待 3 分钟即可, 避免固定时间
        Thread.sleep(Duration.ofMinutes(3).toMillis())
    }

    // 根据指定时间生成对应的 Cron 表达式
    private fun generateCronForTime(time: ZonedDateTime): Cron {
        val cronExpression = "${time.minute} ${time.hour} ${time.dayOfMonth} ${time.monthValue} ${time.dayOfWeek.value}"
        return CronParser(definitionWithoutSeconds).parse(cronExpression)
    }

    // 检查这个 Cron 有没有按照预期执行
    private fun ping(name: String, cron: Cron) {
        val currentTime = ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        println("$name - ${cron.asString()} - executed at $currentTime")
    }
}
