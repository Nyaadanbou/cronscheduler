package cc.mewcraft.cronutils

class ExecutableUnit(
    val trigger: CronTrigger,
    val job: CronJob,
) : Comparable<ExecutableUnit> {
    override fun compareTo(other: ExecutableUnit): Int {
        val thisObj = trigger.nextExecution()
        val otherObj = other.trigger.nextExecution()
        return when {
            thisObj == null -> -1
            otherObj == null -> 1
            else -> thisObj.compareTo(otherObj)
        }
    }
}