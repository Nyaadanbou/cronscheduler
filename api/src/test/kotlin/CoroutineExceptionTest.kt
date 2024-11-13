import kotlinx.coroutines.*
import kotlin.test.Test

class CoroutineExceptionTest {
    @Test
    fun `exception propagation`() = runBlocking {
        val supervisorJob = SupervisorJob()

        // 父协程
        val parentJob = launch(supervisorJob) {
            val child1 = launch {
                println("Child 1 starting")
                delay(1000)
                println("Child 1 done")
            }

            val child2 = launch {
                println("Child 2 starting")
                delay(500)
                throw CancellationException("Child 2 canceled")
            }

            // 等待子协程完成
            joinAll(child1, child2)
        }

        // 等待父协程完成
        parentJob.join()
        println("Parent job completed")
    }
}