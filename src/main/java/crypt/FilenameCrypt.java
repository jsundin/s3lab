package crypt;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.*;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author johdin
 * @since 2017-09-27
 */
public class FilenameCrypt {
  private static final int PAD_ALIGN = 2;

  public static void main(String[] args) throws Exception {
    Security.insertProviderAt(new BouncyCastleProvider(), 1);

    Key key = Crypt.makeKey("abc123");
    byte[] iv = new byte[Crypt.KEY_LENGTH / 8];
    Crypt.SECURE_RANDOM.nextBytes(iv);

    test("a", key, iv);
    test("aa", key, iv);
    test("xy", key, iv);
    test("FilenameCrypt.java", key, iv);
    test("a really really long filename, probably the longest filename you will ever encounter.. if you are lucky, that is..", key, iv);
  }

  static void test(String fn, Key key, byte[] iv) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchProviderException, InvalidAlgorithmParameterException, IOException {
    for (CompressionStrategy cs : CompressionStrategy.values()) {
      String efn = encryptFilename(fn, key, iv, cs);
      String dfn = decryptFilename(efn, key, iv, cs);
      System.out.println(cs + ": " + fn.length() + " -> " + efn.length());
    }
    System.out.println();
  }

  enum CompressionStrategy {
    BEFORE,
    AFTER,
    NOT_AT_ALL
  }

  static String encryptFilename(String filename, Key key, byte[] iv, CompressionStrategy cs) throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchProviderException, InvalidKeyException, IOException {
    byte[] x = padString(filename);
    if (CompressionStrategy.BEFORE.equals(cs)) {
      x = compress(x);
    }
    x = Crypt.encrypt(Crypt.CIPHER, key, iv, x);
    if (CompressionStrategy.AFTER.equals(cs)) {
      x = compress(x);
    }
    return Base64.getEncoder().encodeToString(x);
  }

  static String decryptFilename(String encryptedFilename, Key key, byte[] iv, CompressionStrategy cs) throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchProviderException, InvalidKeyException, IOException {
    byte[] x = Base64.getDecoder().decode(encryptedFilename);
    if (CompressionStrategy.AFTER.equals(cs)) {
      x = decompress(x);
    }
    x = Crypt.decrypt(Crypt.CIPHER, key, iv, x);
    if (CompressionStrategy.BEFORE.equals(cs)) {
      x = decompress(x);
    }
    return depadString(x);
  }

  static byte[] compress(byte[] data) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GZIPOutputStream gos = new GZIPOutputStream(baos);
    gos.write(data);
    gos.close();
    baos.close();
    return baos.toByteArray();
  }

  static byte[] decompress(byte[] data) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    GZIPInputStream gis = new GZIPInputStream(bais);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    byte[] read = new byte[128];
    int len;
    while ((len = gis.read(read)) >= 0) {
      baos.write(read, 0, len);
    }
    gis.close();
    bais.close();
    baos.close();

    return baos.toByteArray();
  }

  static byte[] padString(String filename) {
    int paddedLength = (filename.length() + 1 + PAD_ALIGN) / PAD_ALIGN * PAD_ALIGN;

    byte[] bytes = filename.getBytes();
    byte[] padding = new byte[paddedLength - bytes.length - 1];
    byte[] paddedFilename = new byte[bytes.length + padding.length + 1];

    Crypt.SECURE_RANDOM.nextBytes(padding);
    System.arraycopy(bytes, 0, paddedFilename, 0, bytes.length);
    System.arraycopy(padding, 0, paddedFilename, bytes.length + 1, padding.length);

    return paddedFilename;
  }

  static String depadString(byte[] paddedFilename) {
    int i;
    for (i = 0; i < paddedFilename.length; i++) {
      if (paddedFilename[i] == 0) {
        break;
      }
    }
    if (i == paddedFilename.length) {
      throw new IllegalArgumentException("Invalid filename");
    }

    return new String(paddedFilename, 0, i);
  }
}
