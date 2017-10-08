package s5lab;

import s5lab.agent.BackupAgentNG;

public class S5Lab {
  public static void main(String[] args) {
    BackupAgentNG backupAgent = new BackupAgentNG();
    backupAgent.run(args);
  }
}
