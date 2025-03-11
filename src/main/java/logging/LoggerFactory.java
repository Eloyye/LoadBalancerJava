package logging;

import org.slf4j.Logger;

/**
 * Factory class for creating SLF4J loggers.
 * This provides a centralized way to create loggers throughout the application.
 */
public class LoggerFactory {
    
    /**
     * Get a logger for the specified class.
     *
     * @param clazz The class to get the logger for
     * @return The logger for the class
     */
    public static Logger getLogger(Class<?> clazz) {
        return org.slf4j.LoggerFactory.getLogger(clazz);
    }
    
    /**
     * Get a logger with the specified name.
     *
     * @param name The name for the logger
     * @return The logger with the specified name
     */
    public static Logger getLogger(String name) {
        return org.slf4j.LoggerFactory.getLogger(name);
    }
}
