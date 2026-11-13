package com.dartide.app.editor

import android.os.Bundle

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.text.TextUtils
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Dart support for the code editor.
 *
 * Hand-rolled lexer over a [SimpleAnalyzeManager] — no grammar assets,
 * no network, fully offline. Token colors are the VS Code Dark+ palette
 * and are remapped onto existing sora color ids (see [colorIdOf]).
 */
internal enum class DartToken {
    KEYWORD, CONTROL, TYPE, FUNCTION, IDENT, STRING, NUMBER, COMMENT
}

private fun colorIdOf(token: DartToken): Int = when (token) {
    DartToken.KEYWORD -> EditorColorScheme.KEYWORD
    // The three slots below are remapped in CodeFragment to Dark+ colors:
    DartToken.CONTROL -> EditorColorScheme.ATTRIBUTE_VALUE  // purple #C586C0
    DartToken.TYPE -> EditorColorScheme.IDENTIFIER_NAME     // teal   #4EC9B0
    DartToken.NUMBER -> EditorColorScheme.ANNOTATION        // green  #B5CEA8
    DartToken.FUNCTION -> EditorColorScheme.FUNCTION_NAME
    DartToken.IDENT -> EditorColorScheme.IDENTIFIER_VAR
    DartToken.STRING -> EditorColorScheme.LITERAL
    DartToken.COMMENT -> EditorColorScheme.COMMENT
}

private val DECL_KEYWORDS = hashSetOf(
    "abstract", "async", "base", "class", "const", "covariant", "deferred",
    "enum", "export", "extension", "external", "factory", "final", "get",
    "hide", "implements", "interface", "late", "library", "mixin", "on",
    "operator", "part", "required", "sealed", "set", "show", "static",
    "super", "sync", "this", "typedef", "var", "with"
)

private val CONTROL_KEYWORDS = hashSetOf(
    "as", "assert", "await", "break", "case", "catch", "continue", "default",
    "do", "else", "finally", "for", "if", "in", "is", "rethrow", "return",
    "switch", "throw", "try", "when", "while", "yield"
)

private val BUILTIN_TYPES = hashSetOf(
    "void", "bool", "int", "double", "num", "String", "List", "Map", "Set",
    "Iterable", "Future", "Stream", "Object", "Function", "Never", "Null",
    "Record"
)

private fun isIdentStart(c: Char): Boolean = c.isLetter() || c == '_' || c == '$'
private fun isIdentPart(c: Char): Boolean = isIdentStart(c) || c.isDigit()

/**
 * Single-pass Dart lexer. Produces spans directly onto a [MappedSpans.Builder];
 * multiline tokens (block comments, triple-quoted strings) add one span per
 * crossed line so the span map stays aligned with the text.
 */
class DartAnalyzer : SimpleAnalyzeManager<Unit>() {

    override fun analyze(text: StringBuilder, delegate: Delegate<Unit>): Styles {
        val builder = MappedSpans.Builder()
        val n = text.length
        var i = 0
        var line = 0
        var lineStart = 0
        var lastLine = 0

        fun style(token: DartToken): Long =
            TextStyle.makeStyle(colorIdOf(token), 0, false, false, false)

        while (i < n && !delegate.isCancelled) {
            val c = text[i]
            when {
                c == '\n' -> {
                    i++
                    line++
                    lineStart = i
                    lastLine = line
                }

                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    val startCol = i - lineStart
                    while (i < n && text[i] != '\n') i++
                    builder.addIfNeeded(line, startCol, style(DartToken.COMMENT))
                }

                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    var depth = 1
                    var segStart = i
                    i += 2
                    while (i < n && depth > 0) {
                        when {
                            text[i] == '\n' -> {
                                builder.addIfNeeded(
                                    line, segStart - lineStart, style(DartToken.COMMENT)
                                )
                                i++
                                line++
                                lineStart = i
                                lastLine = line
                                segStart = i
                            }
                            i + 1 < n && text[i] == '/' && text[i + 1] == '*' -> {
                                depth++
                                i += 2
                            }
                            i + 1 < n && text[i] == '*' && text[i + 1] == '/' -> {
                                depth--
                                i += 2
                            }
                            else -> i++
                        }
                    }
                    if (segStart < i) {
                        builder.addIfNeeded(line, segStart - lineStart, style(DartToken.COMMENT))
                    }
                }

                c == '\'' || c == '"' -> {
                    val raw = i > 0 && text[i - 1] == 'r' &&
                        (i - 1 == 0 || !isIdentPart(text[i - 2]))
                    val triple = i + 2 < n && text[i + 1] == c && text[i + 2] == c
                    val quote = c
                    var segStart = i
                    i += if (triple) 3 else 1
                    while (i < n) {
                        val ch = text[i]
                        when {
                            ch == '\n' -> {
                                if (!triple) break
                                builder.addIfNeeded(
                                    line, segStart - lineStart, style(DartToken.STRING)
                                )
                                i++
                                line++
                                lineStart = i
                                lastLine = line
                                segStart = i
                            }
                            !raw && ch == '\\' && i + 1 < n && text[i + 1] != '\n' -> i += 2
                            ch == quote -> {
                                if (!triple) {
                                    i++
                                    break
                                }
                                if (i + 2 < n && text[i + 1] == quote && text[i + 2] == quote) {
                                    i += 3
                                    break
                                }
                                i++
                            }
                            else -> i++
                        }
                    }
                    if (segStart < i) {
                        builder.addIfNeeded(line, segStart - lineStart, style(DartToken.STRING))
                    }
                }

                c.isDigit() -> {
                    val startCol = i - lineStart
                    while (i < n && (text[i].isLetterOrDigit() || text[i] == '.')) i++
                    builder.addIfNeeded(line, startCol, style(DartToken.NUMBER))
                }

                isIdentStart(c) -> {
                    val startCol = i - lineStart
                    val start = i
                    while (i < n && isIdentPart(text[i])) i++
                    val word = text.substring(start, i)
                    val token = when {
                        word in CONTROL_KEYWORDS -> DartToken.CONTROL
                        word in DECL_KEYWORDS -> DartToken.KEYWORD
                        word in BUILTIN_TYPES || word[0].isUpperCase() -> DartToken.TYPE
                        else -> DartToken.IDENT
                    }
                    if (token == DartToken.IDENT) {
                        var j = i
                        while (j < n && text[j] == ' ') j++
                        if (j < n && text[j] == '(') {
                            builder.addIfNeeded(line, startCol, style(DartToken.FUNCTION))
                            continue
                        }
                    }
                    builder.addIfNeeded(line, startCol, style(token))
                }

                else -> i++
            }
        }
        builder.determine(lastLine)
        return Styles(builder.build())
    }
}

/** Bracket/quote auto-pairs, Dart flavor. */
class DartSymbolPairs : SymbolPairMatch() {
    init {
        putPair('{', SymbolPair("{", "}"))
        putPair('(', SymbolPair("(", ")"))
        putPair('[', SymbolPair("[", "]"))
        putPair('"', SymbolPair("\"", "\""))
        putPair('\'', SymbolPair("'", "'"))
    }
}

/** Enter between {} opens a properly indented block. */
class DartBraceHandler : NewlineHandler {

    override fun matchesRequirement(
        text: Content, position: CharPosition, style: Styles?
    ): Boolean {
        val line = text.getLine(position.line)
        val before = line.subSequence(0, position.column).toString()
        val after = line.subSequence(position.column, line.length).toString()
        return before.trimEnd().endsWith("{") && after.trimStart().startsWith("}")
    }

    override fun handleNewline(
        text: Content, position: CharPosition, style: Styles?, tabSize: Int
    ): NewlineHandleResult {
        val line = text.getLine(position.line)
        val before = line.subSequence(0, position.column).toString()
        val indent = TextUtils.countLeadingSpaceCount(before, tabSize)
        val inner = TextUtils.createIndent(indent + 1, tabSize, false)
        val outer = TextUtils.createIndent(indent, tabSize, false)
        val inserted = StringBuilder("\n").append(inner).append('\n').append(outer)
        return NewlineHandleResult(inserted, outer.length + 1)
    }
}

/** The editor language for Dart files. */
class DartLanguage : Language {

    private val analyzer = DartAnalyzer()

    override fun getAnalyzeManager(): AnalyzeManager = analyzer

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_STRONG

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        // Keyword completion arrives in a later phase.
    }

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        if (column <= 0) return 0
        return when (content.getLine(line)[column - 1]) {
            '{', '(', '[' -> 1
            else -> 0
        }
    }

    override fun useTab(): Boolean = false

    override fun getFormatter(): Formatter = EmptyLanguage.EmptyFormatter.INSTANCE

    override fun getSymbolPairs(): SymbolPairMatch = DartSymbolPairs()

    override fun getNewlineHandlers(): Array<NewlineHandler> = arrayOf(DartBraceHandler())

    override fun destroy() {
        analyzer.destroy()
    }
}
