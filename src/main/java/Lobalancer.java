import picocli.CommandLine;
import utils.argparse.LobalancerArguments;

import java.io.IOException;

public class Lobalancer {
    private static final org.slf4j.Logger logger = logging.LoggerFactory.getLogger(Lobalancer.class);

    public static void main(String[] args) {
        
        logger.info("Initializing Lobalancer application");
        var arguments = new LobalancerArguments();
        new CommandLine(arguments).parseArgs(args);
        // ensure that config file is provided
        if (arguments.getConfigFilePath() == null) {
            logger.warn("No config file provided: using default settings");
        }
        try {
            var loadBalancerService = new LoadBalancerService(arguments);
            loadBalancerService.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
