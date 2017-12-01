package ng3.common;

import ng3.Settings;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.*;
import java.security.spec.InvalidKeySpecException;

/**
 * @author johdin
 * @since 2017-11-06
 */
public class CryptoUtils {
  private static final SecureRandom RANDOM = new SecureRandom();

  public static byte[] generateSalt() {
    byte[] salt = new byte[Settings.KEY_SALT_LENGTH];
    RANDOM.nextBytes(salt);
    return salt;
  }

  public static byte[] generateIV() {
    byte[] iv = new byte[Settings.IV_LENGTH];
    RANDOM.nextBytes(iv);
    return iv;
  }

  public static Key generateKey(char[] password, byte[] salt) {
    try {
      SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(Settings.KEY_ALGORITHM, "BC");
      PBEKeySpec keySpec = new PBEKeySpec(password, salt, Settings.KEY_ITERATIONS, Settings.KEY_LENGTH);
      return keyFactory.generateSecret(keySpec);
    } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeySpecException e) {
      throw new CryptoException(e);
    }
  }

  public static Cipher getEncryptionCipher(Key secret, byte[] iv) {
    try {
      Cipher cipher = Cipher.getInstance(Settings.CIPHER_TRANSFORMATION, "BC");
      cipher.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(iv));
      return cipher;
    } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
      throw new CryptoException(e);
    }
  }

  public static CipherOutputStream getEncryptionOutputStream(Key secret, byte[] iv, OutputStream outputStream) {
    return new CipherOutputStream(outputStream, getEncryptionCipher(secret, iv));
  }

  public static CipherInputStream getEncryptionInputStream(Key secret, byte[] iv, InputStream inputStream) {
    return new CipherInputStream(inputStream, getEncryptionCipher(secret, iv));
  }
}
