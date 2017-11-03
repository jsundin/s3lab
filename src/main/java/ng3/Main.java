package ng3;

import ng3.agent.BackupAgent;
import ng3.agent.BackupAgentParams;
import org.apache.commons.cli.*;
import s5lab.Settings;

import java.io.File;

public class Main {
  private static final String OPT_HELP = "h";
  private static final String OPT_TEST_CONFIGURATION = "tc";
  private static final String OPT_CONFIGURATION = "c";
  private static final String OPT_FAIL_ON_BAD_DIRECTORIES = "fobd";
  private static final String OPT_FORCE_BACKUP_NOW = "f";
  private static final String OPT_RUN_ONCE = "o";
  private static final String OPT_PIDFILE = "pf";

  public static void main(String[] args) {
    CommandLine commandLine = null;
    try {
      commandLine = parseCommandline(args);
      if (commandLine == null) {
        System.exit(0);
      }
    } catch (ParseException e) {
      System.err.println("Could not parse command line: " + e);
      System.exit(1);
    }

    BackupAgentParams agentParams = new BackupAgentParams();
    agentParams.setConfigurationFile(new File(commandLine.getOptionValue(OPT_CONFIGURATION)));
    agentParams.setOnlyTestConfiguration(commandLine.hasOption(OPT_TEST_CONFIGURATION));
    agentParams.setFailOnBadDirectories(commandLine.hasOption(OPT_FAIL_ON_BAD_DIRECTORIES));
    agentParams.setForceBackupNow(commandLine.hasOption(OPT_FORCE_BACKUP_NOW));
    agentParams.setRunOnce(commandLine.hasOption(OPT_RUN_ONCE));
    if (commandLine.hasOption(OPT_PIDFILE)) {
      agentParams.setPidFile(new File(commandLine.getOptionValue(OPT_PIDFILE)));
    }

    BackupAgent backupAgent = new BackupAgent(agentParams);
    boolean success;

    try {
      success = backupAgent.run();
    } catch (Throwable t) {
      System.err.println("Unhandled error");
      t.printStackTrace();
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
            .required()
            .build();

    Option opt_failOnBadDirectories = Option.builder(OPT_FAIL_ON_BAD_DIRECTORIES)
            .longOpt("fail-on-bad-directories")
            .desc("Fail on startup if one or more directories does not exist")
            .build();

    Option opt_forceBackupNow = Option.builder(OPT_FORCE_BACKUP_NOW)
            .longOpt("force-now")
            .desc("Force backup immediately, ignoring when last backup was made")
            .build();

    Option opt_runOnce = Option.builder(OPT_RUN_ONCE)
            .longOpt("run-once")
            .desc("Run only once, then exit")
            .build();

    Option opt_pidfile = Option.builder(OPT_PIDFILE)
            .longOpt("pidfile")
            .hasArg()
            .argName("filename")
            .desc("Save pid to a file")
            .build();

    Options helpOptions = new Options();
    helpOptions.addOption(opt_help);

    Options mainOptions = new Options();
    mainOptions.addOption(opt_testConf);
    mainOptions.addOption(opt_conf);
    mainOptions.addOption(opt_failOnBadDirectories);
    mainOptions.addOption(opt_forceBackupNow);
    mainOptions.addOption(opt_runOnce);
    mainOptions.addOption(opt_pidfile);

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
