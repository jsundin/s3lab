package ng3;

import ng3.agent.BackupAgent;
import ng3.common.LatchSynchronizer;
import ng3.common.PidfileWriter;
import ng3.common.SimpleThreadFactory;
import ng3.conf.Configuration;
import ng3.conf.ConfigurationParser;
import ng3.db.DbHandler;
import org.apache.commons.cli.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import s5lab.Settings;

import java.io.File;
import java.security.Security;
import java.util.UUID;

public class Main {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private static final String OPT_HELP = "h";
  private static final String OPT_TEST_CONFIGURATION = "tc";
  private static final String OPT_CONFIGURATION = "c";
  private static final String OPT_FAIL_ON_BAD_DIRECTORIES = "fobd";
  private static final String OPT_FORCE_BACKUP_NOW = "f";
  private static final String OPT_RUN_ONCE = "o";
  private static final String OPT_PIDFILE = "pf";

  public static void main(String[] args) {
    Security.insertProviderAt(new BouncyCastleProvider(), 1);
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

    boolean success;
    try {
      success = new Main().run(commandLine);
    } catch (Throwable t) {
      t.printStackTrace();
      success = false;
    }
    System.exit(success ? 0 : 1);
  }

  private boolean run(CommandLine commandLine) throws Exception {
    File confFile = new File(commandLine.getOptionValue(OPT_CONFIGURATION));
    logger.debug("Loading configuration from '{}'", confFile);
    Configuration conf = new ConfigurationParser().parseConfiguration(confFile);
    if (commandLine.hasOption(OPT_TEST_CONFIGURATION)) {
      logger.debug("Configuration is ok");
      return true;
    }

    DbHandler dbHandler = new DbHandler(conf.getDatabase());
    if (!dbHandler.isInstalled()) {
      logger.info("Installing database");
      dbHandler.install();
    }

    UUID planId;
    UUID _planId = dbHandler.getClient().buildQuery("select plan_id from plan")
            .executeQueryForObject(rs -> rs.getUuid(1));
    if (_planId != null) {
      planId = _planId;
    } else {
      planId = UUID.randomUUID();
      dbHandler.getClient().buildQuery("insert into plan (plan_id) values (?)")
              .withParam().uuidValue(1, planId)
              .executeUpdate();
    }

    LatchSynchronizer shutdownSynchronizer = new LatchSynchronizer();

    PidfileWriter pidfileWriter = null;
    if (commandLine.hasOption(OPT_PIDFILE)) {
      File pidfile = new File(commandLine.getOptionValue(OPT_PIDFILE));
      pidfileWriter = new PidfileWriter(pidfile, shutdownSynchronizer);
      pidfileWriter.start();
    }

    BackupAgent backupAgent = new BackupAgent(shutdownSynchronizer, planId, dbHandler.getClient(), conf);
    final boolean[] success = new boolean[1];

    Thread agentThread = new SimpleThreadFactory("BackupAgent").newThread(() -> {
      try {
        MDC.put("plan", confFile.getName());
        success[0] = backupAgent.run(
                commandLine.hasOption(OPT_FAIL_ON_BAD_DIRECTORIES),
                commandLine.hasOption(OPT_FORCE_BACKUP_NOW),
                commandLine.hasOption(OPT_RUN_ONCE));
      } catch (Throwable error) {
        logger.error("Unhandled error in plan '{}'", planId);
        logger.error("", error);
        success[0] = false;
      }
    });

    agentThread.start();
    while (true) {
      try {
        agentThread.join();
        break;
      } catch (InterruptedException e) {
        agentThread.interrupt();
      }
    }

    if (pidfileWriter != null) {
      pidfileWriter.finish();
    }
    dbHandler.close();

    return success[0];
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
