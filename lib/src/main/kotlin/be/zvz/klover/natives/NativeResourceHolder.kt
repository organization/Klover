package be.zvz.klover.natives

import kotlinx.atomicfu.atomic

/**
 * Abstract instance of a class which holds native resources that must be freed.
 */
abstract class NativeResourceHolder : AutoCloseable {
    private val released = atomic(false)

    /**
     * Assert that the native resources have not been freed.
     */
    protected fun checkNotReleased() {
        check(!released.value) { "Cannot use the decoder after closing it." }
    }

    /**
     * Free up native resources of the decoder. Using other methods after this will throw IllegalStateException.
     */
    override fun close() {
        closeInternal()
    }

    /**
     * Free the native resources.
     */
    protected abstract fun freeResources()

    @Synchronized
    private fun closeInternal() {
        if (released.compareAndSet(false, true)) {
            freeResources()
        }
    }
}
