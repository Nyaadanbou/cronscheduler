package cc.mewcraft.cronutils

import kotlinx.coroutines.CancellationException
import java.util.LinkedList

class CronJob(
    val id: String,
    private val task: suspend () -> ExecutionStatus,
) {
    private var currentStatus = ExecutionStatus.WAITING
    private val statusCallbacks: MutableList<(ExecutionStatus) -> Unit> = LinkedList()

    suspend fun execute(): ExecutionStatus {
        currentStatus = ExecutionStatus.RUNNING
        currentStatus = try {
            task.invoke()
        } catch (e: CancellationException) {
            // catch cancellation exception and mark as cancelled
            e.printStackTrace()
            ExecutionStatus.FAILURE
        } catch (e: Exception) {
            // catch any uncaught exceptions and mark as failure
            e.printStackTrace()
            ExecutionStatus.FAILURE
        }
        runStatusCallbacks()
        return currentStatus
    }

    fun addStatusCallback(callback: (ExecutionStatus) -> Unit) {
        statusCallbacks.add(callback)
    }

    private fun runStatusCallbacks() {
        statusCallbacks.forEach { hook -> hook.invoke(currentStatus) }
    }
}