package config;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import utils.filesystem.LobalancerFilesystem;
import utils.network.NetworkMethod;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

public record HealthCheckConfig(
        int duration,
        Duration timeout,
        int maxTries,
        int successiveSuccessThreshold, // In a state where a pod is suspended, successive success threshold for
                                        // redeclaring healthy
        long initialDelayMs,
        long maxDelayMs,
        NetworkMethod networkMethod,
        int port,
        Path healthCheckPath) {
    
    // Default values
    private static final int DEFAULT_DURATION = 30;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final int DEFAULT_MAX_TRIES = 3;
    private static final int DEFAULT_SUCCESSIVE_SUCCESS_THRESHOLD = 2;
    private static final long DEFAULT_INITIAL_DELAY_MS = 1000;
    private static final long DEFAULT_MAX_DELAY_MS = 30000;
    private static final NetworkMethod DEFAULT_NETWORK_METHOD = NetworkMethod.HTTP;
    private static final Path DEFAULT_HEALTH_CHECK_PATH = Path.of("/health");
    private static final org.slf4j.Logger logger = logging.LoggerFactory.getLogger(HealthCheckConfig.class);
    private static final int DEFAULT_PORT = 8080;

    
    public static HealthCheckConfig fromConfigFile(Path configFilePath) {
        // Create default config
        HealthCheckConfig defaultConfig = new HealthCheckConfig(
                DEFAULT_DURATION,
                DEFAULT_TIMEOUT,
                DEFAULT_MAX_TRIES,
                DEFAULT_SUCCESSIVE_SUCCESS_THRESHOLD,
                DEFAULT_INITIAL_DELAY_MS,
                DEFAULT_MAX_DELAY_MS,
                DEFAULT_NETWORK_METHOD,
                DEFAULT_PORT,
                DEFAULT_HEALTH_CHECK_PATH
        );
        if (configFilePath == null) {
            return defaultConfig;
        }
        // Check file extension
        var fileExtension = LobalancerFilesystem.getFileExtension(configFilePath);
        if (!fileExtension.equals("json")) {
            throw new IllegalArgumentException("Invalid file type: " + fileExtension);
        }


        try {
            // Parse JSON file using Gson
            Gson gson = new Gson();
            HealthCheckConfig parsedConfig = gson.fromJson(new FileReader(configFilePath.toFile()), 
            HealthCheckConfig.class);
            // If parsing succeeds, return the parsed config
            return parsedConfig;
        } catch (JsonSyntaxException e) {
            logger.error("Invalid JSON syntax in config file: " + e.getMessage());
            throw new IllegalArgumentException("Invalid JSON syntax in config file: " + e.getMessage(), e);
        } catch (JsonIOException | IOException e) {
            logger.error("Error reading config file: " + e.getMessage());
            throw new RuntimeException("Error reading config file: " + e.getMessage(), e);
        } catch (Exception e) {
            // For any other unexpected exceptions, fall back to default config
            logger.error("Using default health check configuration due to error: " + e.getMessage());
            return defaultConfig;
        }
    }
}
