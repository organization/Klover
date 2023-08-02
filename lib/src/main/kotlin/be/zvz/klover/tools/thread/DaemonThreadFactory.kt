package be.zvz.klover.tools.thread

import org.slf4j.LoggerFactory
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread factory for daemon threads.
 */
class DaemonThreadFactory @JvmOverloads constructor(name: String, exitCallback: Runnable? = null) : ThreadFactory {
    private val group: ThreadGroup
    private val threadNumber = AtomicInteger(1)
    private val namePrefix: String
    private val exitCallback: Runnable?
    /**
     * @param name Name that will be included in thread names.
     * @param exitCallback Runnable to be executed when the thread exits.
     */
    /**
     * @param name Name that will be included in thread names.
     */
    init {
        group = Thread.currentThread().threadGroup
        namePrefix = "lava-daemon-pool-" + name + "-" + poolNumber.getAndIncrement() + "-thread-"
        this.exitCallback = exitCallback
    }

    override fun newThread(runnable: Runnable): Thread {
        val thread = Thread(group, getThreadRunnable(runnable), namePrefix + threadNumber.getAndIncrement(), 0)
        thread.isDaemon = true
        thread.priority = Thread.NORM_PRIORITY
        return thread
    }

    private fun getThreadRunnable(target: Runnable): Runnable {
        return if (exitCallback == null) {
            target
        } else {
            ExitCallbackRunnable(target)
        }
    }

    private inner class ExitCallbackRunnable(original: Runnable) : Runnable {
        private val original: Runnable?

        init {
            this.original = original
        }

        override fun run() {
            try {
                original?.run()
            } finally {
                wrapExitCallback()
            }
        }

        private fun wrapExitCallback() {
            val wasInterrupted = Thread.interrupted()
            try {
                exitCallback!!.run()
            } catch (throwable: Throwable) {
                log.error("Thread exit notification threw an exception.", throwable)
            } finally {
                if (wasInterrupted) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DaemonThreadFactory::class.java)
        private val poolNumber = AtomicInteger(1)
    }
}
