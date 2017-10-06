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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.time.ZonedDateTime;
import java.util.Arrays;

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

  public static void main(String[] args) throws Exception {
    Security.insertProviderAt(new BouncyCastleProvider(), 1);
    verifyFile(new File("/tmp/backups/backup-0001.tgz.encrypted"), "put-me-in-configuration", false);
  }

  private static void verifyFile(File file, String password, boolean decryptFileIfEncrypted) throws Exception {
    File metaFile = new File(file.getParent(), file.getName() + ".meta");

    Metadata.FileMeta meta;
    try (FileInputStream metaIn = new FileInputStream(metaFile)) {
      meta = Metadata.FileMeta.parseFrom(metaIn);
    }
    System.out.println("Metadata:\n" + meta);
    File targetFile = null;

    String expectedExtension;
    if ("tar".equals(meta.getArchive())) {
      expectedExtension = ".tar";
    } else if ("tar+gzip".equals(meta.getArchive())) {
      expectedExtension = ".tgz";
    } else {
      throw new IllegalArgumentException("Unknown archive type: " + meta.getArchive());
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

  private static class DigestInputStream extends InputStream {
    private final InputStream delegate;
    private final MessageDigest md;

    public DigestInputStream(InputStream delegate) throws NoSuchAlgorithmException {
      this.delegate = delegate;
      md = MessageDigest.getInstance("MD5");
    }

    public byte[] getDigest() {
      return md.digest();
    }

    @Override
    public int read() throws IOException {
      int v = delegate.read();
      md.update((byte) v);
      return v;
    }

    @Override
    public int read(byte[] b) throws IOException {
      int v = delegate.read(b);
      md.update(b, 0, v);
      return v;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int v = delegate.read(b, off, len);
      md.update(b, off, v);
      return v;
    }

    @Override
    public long skip(long n) throws IOException {
      return delegate.skip(n);
    }

    @Override
    public int available() throws IOException {
      return delegate.available();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }

    @Override
    public void mark(int readlimit) {
      delegate.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
      delegate.reset();
    }

    @Override
    public boolean markSupported() {
      return delegate.markSupported();
    }
  }
}
