package org.jdkxx.commons.filesystem.utils;

import org.jdkxx.commons.filesystem.config.Messages;

import java.nio.file.FileSystem;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class PathMatcherSupport {
    private PathMatcherSupport() {
        throw new IllegalStateException("cannot create instances of " + getClass().getName()); //$NON-NLS-1$
    }

    /**
     * Creates a {@code Pattern} for the given syntax and pattern combination. This follows the rules of {@link FileSystem#getPathMatcher(String)}.
     * <p>
     * This method supports two syntaxes: {@code glob} and {@code regex}. If the syntax is {@code glob}, this method delegates to
     * {@link #toGlobPattern(String)}. Otherwise, it will call {@link Pattern#compile(String)}.
     *
     * @param syntaxAndPattern The syntax and pattern.
     * @return A {@code Pattern} based on the given syntax and pattern.
     * @throws IllegalArgumentException      If the parameter does not take the form {@code syntax:pattern}.
     * @throws PatternSyntaxException        If the pattern is invalid.
     * @throws UnsupportedOperationException If the pattern syntax is not {@code glob} or {@code regex}.
     */
    public static Pattern toPattern(String syntaxAndPattern) {
        final int index = syntaxAndPattern.indexOf(':');
        if (index == -1) {
            throw Messages.pathMatcher().syntaxNotFound(syntaxAndPattern);
        }
        String syntax = syntaxAndPattern.substring(0, index);
        String expression = syntaxAndPattern.substring(index + 1);

        if ("glob".equals(syntax)) {
            return toGlobPattern(expression);
        }
        if ("regex".equals(syntax)) {
            return Pattern.compile(expression);
        }
        throw Messages.pathMatcher().unsupportedPathMatcherSyntax(syntax);
    }

    /**
     * Converts the given glob into a {@code Pattern}.
     * <p>
     * Note that this method uses a single forward slash ({@code /}) as path separator.
     *
     * @param glob The glob to convert.
     * @return A {@code Pattern} built from the given glob.
     * @throws PatternSyntaxException If the given glob is invalid.
     * @see FileSystem#getPathMatcher(String)
     */
    public static Pattern toGlobPattern(String glob) {
        return toGlobPattern(glob, 0);
    }

    /**
     * Converts the given glob into a {@code Pattern}.
     * <p>
     * Note that this method uses a single forward slash ({@code /}) as path separator.
     *
     * @param glob  The glob to convert.
     * @param flags {@link Pattern#compile(String, int) Match flags} for the {@code Pattern}.
     * @return A {@code Pattern} built from the given glob.
     * @throws PatternSyntaxException   If the given glob is invalid.
     * @throws IllegalArgumentException If the match flags are invalid.
     * @see FileSystem#getPathMatcher(String)
     */
    public static Pattern toGlobPattern(String glob, int flags) {
        StringBuilder regex = new StringBuilder();
        regex.append('^');

        buildPattern(glob, 0, regex, false);

        regex.append('$');
        return Pattern.compile(regex.toString(), flags);
    }

    private static int buildPattern(String glob, int i, StringBuilder regex, boolean inGroup) {
        while (i < glob.length()) {
            char c = glob.charAt(i++);
            switch (c) {
                case '\\':
                    ensureGlobMetaChar(glob, i);
                    appendLiteral(glob.charAt(i++), regex);
                    break;
                case '*':
                    if (isCharAt(glob, i, '*')) {
                        // anything including separators
                        regex.append(".*");
                        i++;
                    } else {
                        // anything but a separator
                        regex.append("[^/]*");
                    }
                    break;
                case '?':
                    // anything but a separator
                    regex.append("[^/]");
                    break;
                case '[':
                    // a class
                    i = appendClass(glob, i, regex);
                    break;
                case ']':
                    throw Messages.pathMatcher().glob().unexpectedClassEnd(glob, i - 1);
                case '{':
                    if (inGroup) {
                        throw Messages.pathMatcher().glob().nestedGroupsNotSupported(glob, i - 1);
                    }
                    i = appendGroup(glob, i, regex);
                    break;
                case '}':
                    if (!inGroup) {
                        throw Messages.pathMatcher().glob().unexpectedGroupEnd(glob, i - 1);
                    }
                    // Return out of this method to appendGroup
                    return i;
                case ',':
                    if (inGroup) {
                        regex.append(")|(?:");
                    } else {
                        appendLiteral(c, regex);
                    }
                    break;
                default:
                    appendLiteral(c, regex);
                    break;
            }
        }
        if (inGroup) {
            throw Messages.pathMatcher().glob().missingGroupEnd(glob);
        }
        return i;
    }

    private static void appendLiteral(char c, StringBuilder regex) {
        if (isRegexMetaChar(c)) {
            regex.append('\\');
        }
        regex.append(c);
    }

    private static int appendClass(String glob, int i, StringBuilder regex) {
        regex.append("[[");
        if (isCharAt(glob, i, '^')) {
            // If ^ is the first char in the class, escape it
            regex.append("\\^");
            i++;
        } else if (isCharAt(glob, i, '!')) {
            regex.append('^');
            i++;
        }
        boolean inClass = true;
        while (i < glob.length() && inClass) {
            char c = glob.charAt(i++);
            switch (c) {
                case '\\':
                    ensureGlobMetaChar(glob, i);
                    appendLiteral(glob.charAt(i++), regex);
                    break;
                case '/':
                    throw Messages.pathMatcher().glob().separatorNotAllowedInClass(glob, i - 1);
                case '[':
                    throw Messages.pathMatcher().glob().nestedClassesNotSupported(glob, i - 1);
                case ']':
                    inClass = false;
                    break;
                default:
                    appendLiteral(c, regex);
                    break;
            }
        }
        if (inClass) {
            throw Messages.pathMatcher().glob().missingClassEnd(glob);
        }
        regex.append("]&&[^/]]");
        return i;
    }

    private static int appendGroup(String glob, int i, StringBuilder regex) {
        // Open two groups: an inner for the content, and an outer in case there are multiple sub patterns
        regex.append("(?:(?:");
        i = buildPattern(glob, i, regex, true);
        regex.append("))");
        return i;
    }

    private static void ensureGlobMetaChar(String glob, int i) {
        if (!isGlobMetaChar(glob, i)) {
            throw Messages.pathMatcher().glob().unescapableChar(glob, i - 1);
        }
    }

    private static boolean isCharAt(String s, int index, char c) {
        return index < s.length() && s.charAt(index) == c;
    }

    private static boolean isRegexMetaChar(char c) {
        final String chars = ".^\\$+{[]|()";
        return chars.indexOf(c) != -1;
    }

    private static boolean isGlobMetaChar(String s, int index) {
        return index < s.length() && isGlobMetaChar(s.charAt(index));
    }

    private static boolean isGlobMetaChar(char c) {
        final String chars = "*?\\[]{}";
        return chars.indexOf(c) != -1;
    }
}
