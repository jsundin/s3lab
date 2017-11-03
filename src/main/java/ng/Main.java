package ng;

import org.apache.commons.cli.*;
import s5lab.Settings;

import java.io.File;

/**
 * @author johdin
 * @since 2017-11-03
 */
public class Main {
  public static final String OPT_HELP = "h";
  public static final String OPT_TEST_CONFIGURATION = "tc";
  public static final String OPT_CONFIGURATION = "c";

  public static void main(String[] args) {
    CommandLine commandLine = null;
    try {
      commandLine = parseCommandline(args);
    } catch (ParseException e) {
      System.err.println("Could not parse command line: " + e.getMessage());
      System.exit(1);
    }
    if (commandLine == null) { // --help for instance
      System.exit(0);
    }

    boolean success;
    try {
      BackupAgent.BackupAgentParams params = new BackupAgent.BackupAgentParams();
      if (commandLine.hasOption(OPT_CONFIGURATION)) {
        params.setConfigurationFile(new File(commandLine.getOptionValue(OPT_CONFIGURATION)));
      }
      params.setTestConfigurationOnly(commandLine.hasOption(OPT_TEST_CONFIGURATION));

      BackupAgent backupAgent = new BackupAgent(params);
      success = backupAgent.call();
    } catch (Throwable t) {
      t.printStackTrace();
      System.err.println("Exiting...");
      success = false;
    }
    System.exit(success ? 0 : 1);
  }

  private static CommandLine parseCommandline(String[] args) throws ParseException {
    Option opt_help = Option.builder(OPT_HELP)
        .longOpt("help")
        .desc("Show help")
        .build();

    Option opt_testConf = Option.builder(OPT_TEST_CONFIGURATION)
        .longOpt("testconf")
        .desc("Test if configuration is parsable, then exit")
        .build();

    Option opt_conf = Option.builder(OPT_CONFIGURATION)
        .longOpt("conf")
        .hasArg()
        .argName("filename")
        .desc("Specify configuration file")
        //.required() TODO - sen!
        .build();

    Options helpOptions = new Options();
    helpOptions.addOption(opt_help);

    Options mainOptions = new Options();
    mainOptions.addOption(opt_testConf);
    mainOptions.addOption(opt_conf);

    CommandLineParser commandLineParser = new DefaultParser();
    CommandLine commandLine = commandLineParser.parse(helpOptions, args, true);

    if (commandLine.hasOption(OPT_HELP)) {
      new HelpFormatter().printHelp(Settings.APP_NAME, mainOptions);
      return null;
    }

    commandLine = commandLineParser.parse(mainOptions, args);

    return commandLine;
  }
}
