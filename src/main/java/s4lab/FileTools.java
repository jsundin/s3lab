package s4lab;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import s4lab.agent.Metadata;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.security.Security;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

public class FileTools {
  public static ZonedDateTime lastModified(File file) {
    try {
      Object _fileTime = Files.getAttribute(file.toPath(), "unix:ctime");
      if (_fileTime instanceof FileTime) {
        FileTime fileTime = (FileTime) _fileTime;
        return TimeUtils.at(fileTime).toZonedDateTime();
      }
      throw new RuntimeException("Files.getAttribute() did not return a FileTime as expected: " + _fileTime.getClass());
    } catch (IllegalArgumentException | UnsupportedOperationException e) {
      // unix:ctime not available on this system, go with lastModified
      return TimeUtils.at(file.lastModified()).toZonedDateTime();
    } catch (IOException e) {
      throw new RuntimeException("Cannot read lastModified for '" + file + "'", e);
    }
  }

  public static InputStream gzipInputStream(InputStream in) throws IOException {
    PipedInputStream zipped = new PipedInputStream();
    PipedOutputStream pipe = new PipedOutputStream(zipped);
    new Thread(
            () -> {
              try(OutputStream zipper = new GZIPOutputStream(pipe)){
                IOUtils.copy(in, zipper);
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
    ).start();
    return zipped;
  }

  public static void main(String[] args) throws Exception {
    Security.insertProviderAt(new BouncyCastleProvider(), 1);
    //verifyFile(new File("/tmp/backups/backup-0001.tgz.encrypted"), "put-me-in-configuration", false);
    verifyFile(new File("/tmp/backups/nyarefil.encrypted"), "put-me-in-configuration", true);
  }

  private static void verifyFile(File file, String password, boolean decryptFileIfEncrypted) throws Exception {
    File metaFile = new File(file.getParent(), file.getName() + ".meta");

    Metadata.FileMeta meta;
    try (FileInputStream metaIn = new FileInputStream(metaFile)) {
      meta = Metadata.FileMeta.parseFrom(metaIn);
    }
    System.out.println("Metadata:\n" + meta);
    File targetFile = null;

    String expectedExtension = "";
    if ("tar".equals(meta.getArchive())) {
      expectedExtension = ".tar";
    } else if ("tar+gzip".equals(meta.getArchive())) {
      expectedExtension = ".tgz";
    } else {
      System.out.println("Unknown archive type: " + meta.getArchive());
    }

    if (meta.getEncrypted()) {
      expectedExtension += ".encrypted";
      targetFile = new File(file.getParent(), FilenameUtils.removeExtension(file.getName()));
    }
    if (!file.getName().endsWith(expectedExtension)) {
      throw new IllegalArgumentException("Invalid extension for '" + file.getName() + "', expected '" + expectedExtension + "'");
    }

    System.out.println("Source file: " + file);
    if (targetFile != null) {
      System.out.println("Target file: " + targetFile + (decryptFileIfEncrypted ? " - creating" : ""));
    }

    try (DigestInputStream fileIn = new DigestInputStream(new FileInputStream(file))) {
      OutputStream targetOut = new NullOutputStream();
      DigestInputStream cipherIn = null;

      if (meta.getEncrypted()) {
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(meta.getKeyAlgorithm(), "BC");
        PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), meta.getSalt().toByteArray(), meta.getKeyIterations(), meta.getKeyLength());
        SecretKey key = keyFactory.generateSecret(keySpec);

        Cipher cipher = Cipher.getInstance(meta.getCryptoAlgorithm(), "BC");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(meta.getIv().toByteArray()));

        cipherIn = new DigestInputStream(new CipherInputStream(fileIn, cipher));

        if (targetFile != null && decryptFileIfEncrypted) {
          targetOut = new FileOutputStream(targetFile);
        }
      }

      IOUtils.copy(cipherIn == null ? fileIn : cipherIn, targetOut);

      if (cipherIn != null) {
        boolean decryptedMatch = Arrays.equals(meta.getDecryptedMD5().toByteArray(), cipherIn.getDigest());
        System.out.println("Decrypted checksum: " + (decryptedMatch ? "OK" : "MISMATCH"));
        cipherIn.close();
      }
      boolean fileMatch = Arrays.equals(meta.getFileMD5().toByteArray(), fileIn.getDigest());
      System.out.println("File checksum: " + (fileMatch ? "OK" : "MISMATCH"));
      targetOut.close();
    }
  }

}
