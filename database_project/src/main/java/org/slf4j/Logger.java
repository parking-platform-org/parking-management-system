package org.slf4j;

/**
 * Minimal no-op implementation of the SLF4J {@code Logger} API.
 * Only the methods required by sqlite-jdbc (and most common libraries)
 * are provided.  Every method is a default no-op so that linking
 * succeeds while producing no console output.
 */
public interface Logger {

    /* -------------------------------------------------
       Inquiry methods
       ------------------------------------------------- */
    default boolean isTraceEnabled() { return false; }
    default boolean isDebugEnabled() { return false; }
    default boolean isInfoEnabled()  { return false; }
    default boolean isWarnEnabled()  { return false; }
    default boolean isErrorEnabled() { return false; }

    /* -------------------------------------------------
       Trace
       ------------------------------------------------- */
    default void trace(String msg) {}
    default void trace(String format, Object arg) {}
    default void trace(String format, Object arg1, Object arg2) {}
    default void trace(String format, Object... arguments) {}
    /* -------------------------------------------------
       Debug
       ------------------------------------------------- */
    default void debug(String msg) {}
    default void debug(String format, Object arg) {}
    default void debug(String format, Object arg1, Object arg2) {}
    default void debug(String format, Object... arguments) {}
    /* -------------------------------------------------
       Info
       ------------------------------------------------- */
    default void info(String msg) {}
    default void info(String format, Object arg) {}
    default void info(String format, Object arg1, Object arg2) {}
    default void info(String format, Object... arguments) {}
    /* -------------------------------------------------
       Warn
       ------------------------------------------------- */
    default void warn(String msg) {}
    default void warn(String format, Object arg) {}
    default void warn(String format, Object arg1, Object arg2) {}
    default void warn(String format, Object... arguments) {}
    /* -------------------------------------------------
       Error
       ------------------------------------------------- */
    default void error(String msg) {}
    default void error(String format, Object arg) {}
    default void error(String format, Object arg1, Object arg2) {}
    default void error(String format, Object... arguments) {}
}
