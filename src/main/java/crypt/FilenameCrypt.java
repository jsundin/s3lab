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
    int ivl = Crypt.KEY_LENGTH / 8;

    test("a", key, getIV(ivl));
    test("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", key, getIV(ivl));
    test("xy", key, getIV(ivl));
    test("FilenameCrypt.java", key, getIV(ivl));
    test("a really really long filename, probably the longest filename you will ever encounter.. if you are lucky, that is..", key, getIV(ivl));
  }

  private static byte[] static_iv;
  static byte[] getIV(int bytes) {
    /*if (static_iv == null) {
      static_iv = new byte[bytes];
      Crypt.SECURE_RANDOM.nextBytes(static_iv);
    }
    return static_iv;*/
    byte[] iv = new byte[bytes];
    Crypt.SECURE_RANDOM.nextBytes(iv);
    return iv;
  }

  static void test(String fn, Key key, byte[] iv) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchProviderException, InvalidAlgorithmParameterException, IOException {
    String efn = encryptFilename(fn, key, iv);
    String dfn = decryptFilename(efn, key);
    System.out.println(fn + ": " + fn.length() + " -> " + efn.length() + "  [" + efn + "]");
    System.out.println();
  }

  static String encryptFilename(String filename, Key key, byte[] iv) throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchProviderException, InvalidKeyException, IOException {
    //byte[] x = padString(filename);
    byte[] x = filename.getBytes();
    x = compress(x);
    x = Crypt.encrypt(Crypt.CIPHER, key, iv, x);
    byte[] toEncode = new byte[iv.length + x.length + 1];
    toEncode[0] = (byte) iv.length;
    System.arraycopy(iv, 0, toEncode, 1, iv.length);
    System.arraycopy(x, 0, toEncode, 1 + iv.length, x.length);
    String encoded = Base64.getEncoder().encodeToString(toEncode);
    return encoded.replaceAll("=", ".");
  }

  static String decryptFilename(String encryptedFilename, Key key) throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchProviderException, InvalidKeyException, IOException {
    byte[] y = Base64.getDecoder().decode(encryptedFilename.replaceAll("\\.", "="));
    int ivSize = y[0];
    byte[] iv = new byte[ivSize];
    System.arraycopy(y, 1, iv, 0, iv.length);
    byte[] x = new byte[y.length - ivSize - 1];
    System.arraycopy(y, 1 + iv.length, x, 0, x.length);

    x = Crypt.decrypt(Crypt.CIPHER, key, iv, x);
    x = decompress(x);
    return new String(x);
    //return depadString(x);
  }

  static byte[] compress(byte[] data) throws IOException {
/*
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GZIPOutputStream gos = new GZIPOutputStream(baos);
    gos.write(data);
    gos.close();
    baos.close();
    return baos.toByteArray();
*/
    return data;
  }

  static byte[] decompress(byte[] data) throws IOException {
/*
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
*/
    return data;
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
