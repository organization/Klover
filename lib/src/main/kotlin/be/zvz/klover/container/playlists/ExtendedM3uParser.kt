package be.zvz.klover.container.playlists

import java.util.regex.Pattern

/**
 * Parser for extended M3U lines, handles the format where directives have named argumentsm, for example:
 * #SOMETHING:FOO="thing",BAR=4
 */
object ExtendedM3uParser {
    private val directiveArgumentPattern = Pattern.compile("([A-Z-]+)=(?:\"([^\"]*)\"|([^,]*))(?:,|\\z)")

    /**
     * Parses one line.
     * @param line Line.
     * @return Line object describing the directive or data on the line.
     */
    fun parseLine(line: String): Line {
        val trimmed = line.trim { it <= ' ' }
        return if (trimmed.isEmpty()) {
            Line.EMPTY_LINE
        } else if (!trimmed.startsWith("#")) {
            Line(trimmed, null, emptyMap(), null)
        } else {
            parseDirectiveLine(trimmed)
        }
    }

    private fun parseDirectiveLine(line: String): Line {
        val parts = line.split(":".toRegex(), limit = 2).toTypedArray()
        if (parts.size == 1) {
            return Line(null, line.substring(1), emptyMap(), "")
        }
        val matcher = directiveArgumentPattern.matcher(parts[1])
        val arguments: MutableMap<String, String> = HashMap()
        while (matcher.find()) {
            arguments[matcher.group(1)] = if (matcher.group(2) != null) matcher.group(2) else matcher.group(3)
        }
        return Line(null, parts[0].substring(1), arguments, parts[1])
    }

    /**
     * Parsed extended M3U line info. May be either an empty line (isDirective() and isData() both false), a directive
     * or a data line.
     */
    class Line(
        /**
         * The data of a data line.
         */
        val lineData: String?,
        /**
         * Directive name of a directive line.
         */
        val directiveName: String?,
        /**
         * Directive arguments of a directive line.
         */
        val directiveArguments: Map<String, String>?,
        /**
         * Raw unprocessed directive extra data (where arguments are parsed from).
         */
        val extraData: String?,
    ) {
        val isDirective: Boolean
            /**
             * @return True if it is a directive line.
             */
            get() = directiveName != null
        val isData: Boolean
            /**
             * @return True if it is a data line.
             */
            get() = lineData != null

        companion object {
            val EMPTY_LINE = Line(null, null, null, null)
        }
    }
}
