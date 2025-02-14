package logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class LoggingInvocationHandler implements InvocationHandler {
    private final Object target;
    private final Logger logger;

    public LoggingInvocationHandler(Object target) {
        this.target = target;
        this.logger = LoggerFactory.getLogger(target.getClass());
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        RuntimeLog logAnnotation = method.getAnnotation(RuntimeLog.class);

        // If method is not annotated, just execute it
        if (logAnnotation == null) {
            return method.invoke(target, args);
        }

        String message = logAnnotation.value().isEmpty() ?
                method.getName() : logAnnotation.value();
        LogLevel level = logAnnotation.level();

        // Log method entry
        logMessage(level, "Starting: " + message);

        long startTime = System.currentTimeMillis();
        try {
            Object result = method.invoke(target, args);
            long duration = System.currentTimeMillis() - startTime;

            // Log successful completion
            logMessage(level, String.format("Completed: %s (took %d ms)",
                    message, duration));

            return result;
        } catch (Exception e) {
            // Log any exceptions
            logMessage(LogLevel.ERROR, String.format("Exception in %s: %s",
                    message, e.getMessage()));
            throw e.getCause(); // Unwrap the invocation exception
        }
    }

    private void logMessage(LogLevel level, String message) {
        switch (level) {
            case TRACE:
                logger.trace(message);
                break;
            case DEBUG:
                logger.debug(message);
                break;
            case INFO:
                logger.info(message);
                break;
            case WARN:
                logger.warn(message);
                break;
            case ERROR:
                logger.error(message);
                break;
        }
    }

    // Factory method to create logged instances
    @SuppressWarnings("unchecked")
    public static <T> T createLoggingProxy(T target, Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[] { interfaceClass },
                new LoggingInvocationHandler(target)
        );
    }
}