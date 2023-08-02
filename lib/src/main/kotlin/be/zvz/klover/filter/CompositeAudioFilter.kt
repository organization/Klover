package be.zvz.klover.filter

import org.slf4j.LoggerFactory

/**
 * An audio filter which may consist of a number of other filters.
 */
abstract class CompositeAudioFilter : UniversalPcmAudioFilter {
    override fun seekPerformed(requestedTime: Long, providedTime: Long) {
        for (filter in filters!!) {
            try {
                filter!!.seekPerformed(requestedTime, providedTime)
            } catch (e: Exception) {
                log.error("Notifying filter {} of seek failed with exception.", filter!!.javaClass, e)
            }
        }
    }

    @Throws(InterruptedException::class)
    override fun flush() {
        for (filter in filters!!) {
            try {
                filter!!.flush()
            } catch (e: Exception) {
                log.error("Flushing filter {} failed with exception.", filter!!.javaClass, e)
            }
        }
    }

    override fun close() {
        for (filter in filters!!) {
            try {
                filter!!.close()
            } catch (e: Exception) {
                log.error("Closing filter {} failed with exception.", filter!!.javaClass, e)
            }
        }
    }

    protected abstract val filters: List<AudioFilter?>?

    companion object {
        private val log = LoggerFactory.getLogger(CompositeAudioFilter::class.java)
    }
}
