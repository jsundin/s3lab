package s4lab.agent.backuptarget.localarchive;

import com.google.protobuf.ByteString;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.DigestOutputStream;
import s4lab.agent.Metadata;
import s4lab.agent.SecurityException;
import s4lab.agent.SecurityManager;

import javax.crypto.CipherOutputStream;
import javax.xml.bind.DatatypeConverter;
import javax.xml.ws.Holder;
import java.io.*;
import java.util.zip.GZIPOutputStream;

/**
 * @author johdin
 * @since 2017-10-06
 */
public class TarGzArchiver {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final File stripDirectoryPrefix;
  private final File targetDirectory;
  private final boolean compressArchive;
  private final boolean encrypt;
  private final ArchiveLimiter archiveLimiter;
  private static final String DELETE_MARKER = "#DELETED#";
  private DigestOutputStream fileOut;
  private DigestOutputStream cipherOut;
  private GZIPOutputStream gzOut;
  private TarArchiveOutputStream tarOut;
  private int filesInArchiveCounter = 0;
  private int archiveIndex = 1;
  private File metaFile;
  private Metadata.FileMeta.Builder fileMetaBuilder;

  public TarGzArchiver(File targetDirectory, File stripDirectoryPrefix, ArchiveLimiter archiveLimiter, boolean compressArchive) {
    this.targetDirectory = targetDirectory;
    this.stripDirectoryPrefix = stripDirectoryPrefix;
    this.archiveLimiter = archiveLimiter;
    this.compressArchive = compressArchive;
    this.encrypt = false;
  }

  public TarGzArchiver(File targetDirectory, File stripDirectoryPrefix, ArchiveLimiter archiveLimiter, boolean compressArchive, boolean encrypt) {
    this.targetDirectory = targetDirectory;
    this.stripDirectoryPrefix = stripDirectoryPrefix;
    this.archiveLimiter = archiveLimiter;
    this.compressArchive = compressArchive;
    this.encrypt = encrypt;
  }

  void close() {
    logger.info("Closing archiver");
    closeArchive();
  }

  void addFile(File file, int version) throws IOException {
    TarArchiveEntry tarEntry = getTarEntry(file, version);
    tarOut.putArchiveEntry(tarEntry);
    IOUtils.copy(new FileInputStream(file), tarOut);
    tarOut.closeArchiveEntry();
  }

  void addDeleteMarker(File file, int version) throws IOException {
    TarArchiveEntry tarEntry = getTarEntry(file, version);
    tarEntry.setSize(0);
    tarEntry.setUserName(DELETE_MARKER);
    tarOut.putArchiveEntry(tarEntry);
    tarOut.closeArchiveEntry();
  }

  private void writeDigest(File file, DigestOutputStream dos) {
    try (FileOutputStream fos = new FileOutputStream(file)) {
      fos.write(DatatypeConverter.printHexBinary(dos.getDigest()).toLowerCase().getBytes());
    } catch (IOException e) {
      logger.error("Could not write checksum file", e);
    }
  }

  private void closeArchive() {
    try {
      if (tarOut != null) {
        tarOut.close();
      }
    } catch (IOException ignored) {}
    tarOut = null;

    try {
      if (gzOut != null) {
        gzOut.close();
      }
    } catch (IOException ignored) {}
    gzOut = null;

    if (encrypt && cipherOut != null) {
      fileMetaBuilder.setDecryptedMD5(ByteString.copyFrom(cipherOut.getDigest()));
    }

    try {
      if (cipherOut != null) {
        cipherOut.close();
      }
    } catch (IOException ignored) {}
    cipherOut = null;

    if (fileOut != null) {
      fileMetaBuilder.setFileMD5(ByteString.copyFrom(fileOut.getDigest()));
    }

    try {
      if (fileOut != null) {
        fileOut.close();
      }
    } catch (IOException ignored) {}
    fileOut = null;

    try (FileOutputStream fos = new FileOutputStream(metaFile)) {
      fileMetaBuilder.build().writeTo(fos);
    } catch (IOException e) {
      logger.error("Could not write meta information", e);
    }
  }

  private void cycleArchive() throws IOException, SecurityException {
    long archiveSize = fileOut == null ? -1 : fileOut.getBytesWritten();
    if (fileOut != null && archiveLimiter != null && archiveLimiter.newArchive(filesInArchiveCounter, archiveSize)) {
      logger.info("Closing archive due to archive limit, {} files and {}kb", filesInArchiveCounter, archiveSize / 1024);
      closeArchive();
      archiveIndex++;
    }

    if (fileOut == null) {
      String archiveFilename = String.format("backup-%04d.%s", archiveIndex, compressArchive ? "tgz" : "tar");
      if (encrypt) {
        archiveFilename += ".encrypted";
      }
      File archiveFile = new File(targetDirectory, archiveFilename);
      metaFile = new File(targetDirectory, archiveFilename + ".meta");
      logger.info("Opening new archive '{}'", archiveFile);
      fileOut = new DigestOutputStream(new FileOutputStream(archiveFile));
      OutputStream lastOut = fileOut;
      fileMetaBuilder = Metadata.FileMeta.newBuilder()
          .setFormatVersion(1)
          .setArchive("tar" + (compressArchive ? "+gzip" : ""));

      if (encrypt) {
        Holder<byte[]> salt = new Holder<>();
        Holder<byte[]> iv = new Holder<>();
        cipherOut = new DigestOutputStream(new CipherOutputStream(lastOut, SecurityManager.getInstance().getEncryptionCipher(salt, iv)));
        lastOut = cipherOut;

        fileMetaBuilder
            .setEncrypted(true)
            .setKeyIterations(SecurityManager.KEY_ITERATIONS)
            .setKeyLength(SecurityManager.KEY_LENGTH)
            .setKeyAlgorithm(SecurityManager.KEY_ALGORITHM)
            .setCryptoAlgorithm(SecurityManager.ENCRYPTION_ALGORITHM)
            .setSalt(ByteString.copyFrom(salt.value))
            .setIv(ByteString.copyFrom(iv.value));
      }

      if (compressArchive) {
        gzOut = new GZIPOutputStream(lastOut);
        lastOut = gzOut;
      }
      tarOut = new TarArchiveOutputStream(lastOut);
      filesInArchiveCounter = 0;
    }
    filesInArchiveCounter++;
  }

  private TarArchiveEntry getTarEntry(File file, int version) throws IOException {
    String entryName = file.toString() + "," + version;
    if (!entryName.startsWith(stripDirectoryPrefix.toString())) {
      throw new IOException("File '" + file + "' does not start with '" + stripDirectoryPrefix + "'");
    }
    entryName = entryName.substring(stripDirectoryPrefix.toString().length());
    if (entryName.startsWith("/")) {
      entryName = entryName.substring(1);
    }

    try {
      cycleArchive();
    } catch (SecurityException e) {
      throw new IOException("Security exception", e);
    }
    return new TarArchiveEntry(file, entryName);
  }
}
