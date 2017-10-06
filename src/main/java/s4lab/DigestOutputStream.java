package s4lab;

import s4lab.agent.SecurityException;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author johdin
 * @since 2017-10-06
 */
public class DigestOutputStream extends OutputStream {
  private final OutputStream wrapped;
  private final MessageDigest md;
  private long bytesWritten;

  public DigestOutputStream(OutputStream wrapped) throws SecurityException {
    this.wrapped = wrapped;
    try {
      md = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new SecurityException(e);
    }
  }

  public byte[] getDigest() {
    return md.digest();
  }

  public long getBytesWritten() {
    return bytesWritten;
  }

  @Override
  public void write(int b) throws IOException {
    wrapped.write(b);
    md.update((byte) b);
    bytesWritten++;
  }

  @Override
  public void write(byte[] b) throws IOException {
    wrapped.write(b);
    md.update(b);
    bytesWritten += b.length;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    wrapped.write(b, off, len);
    md.update(b, off, len);
    bytesWritten += len;
  }

  @Override
  public void flush() throws IOException {
    wrapped.flush();
  }

  @Override
  public void close() throws IOException {
    wrapped.close();
  }
}
