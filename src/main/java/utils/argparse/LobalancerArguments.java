package utils.argparse;
import java.nio.file.Path;

import picocli.CommandLine.Option;

public class LobalancerArguments {
    @Option(names = {"-f", "--file"}, description = "file to load configuration file from")
    private Path configFilePath;

    @Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    public LobalancerArguments() {
        
    }

    public Path getConfigFilePath() {
        return configFilePath;
    }

    public boolean isHelpRequested() {
        return helpRequested;
    }

}
