package s4lab;

import s4lab.fs.DirectoryConfiguration;
import s4lab.fs.FileScanner;
import s4lab.fs.PathPrefixExcludeRule;
import s4lab.fs.SymlinkExcludeRule;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class Main {
  public static void main(String[] args) {
    FileScanner fs = new FileScanner();
    List<File> files = fs.scan(
            Arrays.asList(
                    new DirectoryConfiguration("/home/jsundin/tmp/filemontest")
            ),
            new SymlinkExcludeRule(),
            new PathPrefixExcludeRule("/home/jsundin/tmp/filemontest/exclude/")
    );
    System.out.println(files);
  }
}
