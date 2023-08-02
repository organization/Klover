package be.zvz.klover.tools.exception

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.Volatile

/**
 * Contains common helper methods for dealing with exceptions.
 */
object ExceptionTools {
    private val log = LoggerFactory.getLogger(ExceptionTools::class.java)

    @Volatile
    private var debugInfoHandler: ErrorDebugInfoHandler = DefaultErrorDebugInfoHandler()

    /**
     * Sometimes it is necessary to catch Throwable instances for logging or reporting purposes. However, unless for
     * specific known cases, Error instances should not be blocked from propagating, so rethrow them.
     *
     * @param throwable The Throwable to check, it is rethrown if it is an Error
     */
    @JvmStatic
    fun rethrowErrors(throwable: Throwable?) {
        if (throwable is Error) {
            throw (throwable as Error?)!!
        }
    }

    /**
     * If the exception is not a FriendlyException, wrap with a FriendlyException with the given message
     *
     * @param message Message of the new FriendlyException if needed
     * @param severity Severity of the new FriendlyException
     * @param throwable The exception to potentially wrap
     * @return Original or wrapped exception
     */
    @JvmStatic
    fun wrapUnfriendlyExceptions(
        message: String?,
        severity: FriendlyException.Severity,
        throwable: Throwable?,
    ): FriendlyException {
        return if (throwable is FriendlyException) {
            throwable
        } else {
            FriendlyException(message, severity, throwable)
        }
    }

    /**
     * If the exception is not a FriendlyException, wrap with a RuntimeException
     *
     * @param throwable The exception to potentially wrap
     * @return Original or wrapped exception
     */
    @JvmStatic
    fun wrapUnfriendlyExceptions(throwable: Throwable?): RuntimeException {
        return if (throwable is FriendlyException) {
            throwable
        } else {
            RuntimeException(throwable)
        }
    }

    @JvmStatic
    fun toRuntimeException(e: Exception?): RuntimeException {
        return if (e is RuntimeException) {
            e
        } else {
            RuntimeException(e)
        }
    }

    /**
     * Finds the first exception which is an instance of the specified class from the throwable cause chain.
     *
     * @param throwable Throwable to scan.
     * @param klass The throwable class to scan for.
     * @param <T> The throwable class to scan for.
     * @return The first exception in the cause chain (including itself) which is an instance of the specified class.
     </T> */
    @JvmStatic
    fun <T : Throwable?> findDeepException(throwable: Throwable?, klass: Class<T>): T? {
        var throwable = throwable
        while (throwable != null) {
            if (klass.isAssignableFrom(throwable.javaClass)) {
                return throwable as T
            }
            throwable = throwable.cause
        }
        return null
    }

    /**
     * Makes sure thread is set to interrupted state when the throwable is an InterruptedException
     * @param throwable Throwable to check
     */
    @JvmStatic
    fun keepInterrupted(throwable: Throwable?) {
        if (throwable is InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Log a FriendlyException appropriately according to its severity.
     * @param log Logger instance to log it to
     * @param exception The exception itself
     * @param context An object that is included in the log
     */
    @JvmStatic
    fun log(log: Logger, exception: FriendlyException?, context: Any?) {
        when (exception!!.severity) {
            FriendlyException.Severity.COMMON -> log.debug("Common failure for {}: {}", context, exception.message)
            FriendlyException.Severity.SUSPICIOUS -> log.warn("Suspicious exception for {}", context, exception)
            else -> log.error("Error in {}", context, exception)
        }
    }

    @JvmStatic
    fun setDebugInfoHandler(debugInfoHandler: ErrorDebugInfoHandler) {
        ExceptionTools.debugInfoHandler = debugInfoHandler
    }

    @JvmStatic
    fun throwWithDebugInfo(
        log: Logger?,
        cause: Throwable?,
        message: String,
        name: String,
        value: String?,
    ): RuntimeException {
        val debugInfo = ErrorDebugInfo(log, UUID.randomUUID().toString(), cause, message, name, value)
        debugInfoHandler.handle(debugInfo)
        return RuntimeException(message + " EID: " + debugInfo.errorId + ", " + name + "<redacted>", cause)
    }

    /**
     * Encode an exception to an output stream
     * @param output Data output
     * @param exception Exception to encode
     * @throws IOException On IO error
     */
    @JvmStatic
    @Throws(IOException::class)
    fun encodeException(output: DataOutput, exception: FriendlyException) {
        val causes: MutableList<Throwable> = ArrayList()
        var next = exception.cause
        while (next != null) {
            causes.add(next)
            next = next.cause
        }
        for (i in causes.indices.reversed()) {
            val cause = causes[i]
            output.writeBoolean(true)
            val message = if (cause is DecodedException) {
                output.writeUTF(cause.className)
                cause.originalMessage
            } else {
                output.writeUTF(cause.javaClass.name)
                cause.message
            }
            output.writeBoolean(message != null)
            if (message != null) {
                output.writeUTF(message)
            }
            encodeStackTrace(output, cause)
        }
        output.writeBoolean(false)
        output.writeUTF(exception.message)
        output.writeInt(exception.severity.ordinal)
        encodeStackTrace(output, exception)
    }

    /**
     * Closes the specified closeable object. In case that throws an error, logs the error with WARN level, but does not
     * rethrow.
     *
     * @param closeable Object to close.
     */
    @JvmStatic
    fun closeWithWarnings(closeable: AutoCloseable) {
        try {
            closeable.close()
        } catch (e: Exception) {
            log.warn("Failed to close.", e)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    private fun encodeStackTrace(output: DataOutput, throwable: Throwable) {
        val trace = throwable.stackTrace
        output.writeInt(trace.size)
        for (element in trace) {
            output.writeUTF(element.className)
            output.writeUTF(element.methodName)
            val fileName = element.fileName
            output.writeBoolean(fileName != null)
            if (fileName != null) {
                output.writeUTF(fileName)
            }
            output.writeInt(element.lineNumber)
        }
    }

    /**
     * Decode an exception from an input stream
     * @param input Data input
     * @return Decoded exception
     * @throws IOException On IO error
     */
    @JvmStatic
    @Throws(IOException::class)
    fun decodeException(input: DataInput): FriendlyException {
        var cause: DecodedException? = null
        while (input.readBoolean()) {
            cause = DecodedException(input.readUTF(), if (input.readBoolean()) input.readUTF() else null, cause)
            cause.stackTrace = decodeStackTrace(input)
        }
        val exception =
            FriendlyException(
                input.readUTF(),
                FriendlyException.Severity::class.java.enumConstants[input.readInt()],
                cause,
            )
        exception.stackTrace = decodeStackTrace(input)
        return exception
    }

    @JvmStatic
    @Throws(IOException::class)
    private fun decodeStackTrace(input: DataInput): Array<StackTraceElement?> {
        val trace = arrayOfNulls<StackTraceElement>(input.readInt())
        for (i in trace.indices) {
            trace[i] = StackTraceElement(
                input.readUTF(),
                input.readUTF(),
                if (input.readBoolean()) input.readUTF() else null,
                input.readInt(),
            )
        }
        return trace
    }

    class ErrorDebugInfo(
        val log: Logger?,
        val errorId: String,
        val cause: Throwable?,
        val message: String,
        val name: String,
        val value: String?,
    )

    fun interface ErrorDebugInfoHandler {
        fun handle(payload: ErrorDebugInfo)
    }

    class DefaultErrorDebugInfoHandler : ErrorDebugInfoHandler {
        override fun handle(debugInfo: ErrorDebugInfo) {
            log.warn("{} EID: {}, {}: {}", debugInfo.message, debugInfo.errorId, debugInfo.name, debugInfo.value)
        }
    }
}
