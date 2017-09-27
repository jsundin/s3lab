package crypt;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.Key;
import java.security.Security;

/**
 * @author johdin
 * @since 2017-09-27
 */
public class StreamCrypt {
  public static void main(String[] args) throws Exception {
    File plaintextFile = new File("/etc/passwd");
    File encryptedFile = new File("/tmp/out");
    byte[] iv = new byte[Crypt.KEY_LENGTH / 8];
    Crypt.SECURE_RANDOM.nextBytes(iv);

    Security.insertProviderAt(new BouncyCastleProvider(), 1);
    crypt(plaintextFile, encryptedFile, "abc123", iv);
    decrypt(encryptedFile, "abc123", iv);
  }

  static void decrypt(File encryptedFile, String passwd, byte[] iv) throws Exception {
    Key key = Crypt.makeKey(passwd);
    Cipher cipher = Crypt.getCipher(Crypt.CIPHER);
    cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

    FileInputStream fis = new FileInputStream(encryptedFile);
    CipherInputStream cis = new CipherInputStream(fis, cipher);

    byte[] data = new byte[128];
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int len;
    while ((len = cis.read(data)) >= 0) {
      baos.write(data, 0, len);
    }
    cis.close();
    fis.close();

    System.out.println("Decrypted " + new String(baos.toByteArray()).length() + " bytes");
  }

  static void crypt(File plaintextFile, File encryptedFile, String passwd, byte[] iv) throws Exception {
    Key key = Crypt.makeKey(passwd);
    Cipher cipher = Crypt.getCipher(Crypt.CIPHER);
    cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

    FileOutputStream fos = new FileOutputStream(encryptedFile);
    CipherOutputStream cos = new CipherOutputStream(fos, cipher);

    FileInputStream fis = new FileInputStream(plaintextFile);

    byte[] data = new byte[67];
    int len;
    int srcLen = 0;
    while ((len = fis.read(data)) >= 0) {
      cos.write(data, 0, len);
      srcLen += len;
    }
    cos.close();
    fos.close();
    fis.close();

    System.out.println("Encrypted " + srcLen + " bytes");
  }
}
