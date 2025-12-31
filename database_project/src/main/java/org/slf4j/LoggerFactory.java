package org.slf4j;

/**
 * Very small substitute for the SLF4J {@code LoggerFactory}.
 * Always returns the same no-op {@link Logger} instance.
 */
public final class LoggerFactory {

    private static final Logger NOP_LOGGER = new Logger() {
        /* uses all default methods from the interface */
    };

    private LoggerFactory() {}      // prevent instantiation

    public static Logger getLogger(String name) {
        return NOP_LOGGER;
    }

    public static Logger getLogger(Class<?> clazz) {
        return NOP_LOGGER;
    }
}
