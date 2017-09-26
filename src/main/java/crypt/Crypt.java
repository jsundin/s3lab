package crypt;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * @author johdin
 * @since 2017-09-26
 */
public class Crypt {
  private static final String SALT = "abc123";
  private static final int ITERATIONS = 6000;
  private static final int KEY_LENGTH = 128;
  public static final String CIPHER = "AES/CFB" + KEY_LENGTH + "/PKCS5Padding";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  public static void main(String[] args) throws Exception {
    Security.insertProviderAt(new BouncyCastleProvider(), 1);
    String password = "abc123";
    String data = "hej hej";

    Key secret = makeKey(password);
    byte[] iv = new byte[KEY_LENGTH / 8];
    SECURE_RANDOM.nextBytes(iv);

    byte[] bytes = encryptWithDetails(CIPHER, secret, iv, data);
    System.out.println(bytes.length + ":" + new String(bytes));
    System.out.println(Base64.getEncoder().encodeToString(bytes));

    byte[] message = decryptWithDetails(secret, bytes);
    System.out.println(message.length + ":" + new String(message));
  }

  public static void omain(String[] args) throws Exception { // -Djava.security.egd=file:/dev/./urandom
    Security.insertProviderAt(new BouncyCastleProvider(), 1);

    String password = "12345678123456f";
    String data = "TJOLAHOPP!";

    Key secret = makeKey(password);

    byte[] iv = new byte[KEY_LENGTH / 8];
    SECURE_RANDOM.nextBytes(iv);

    byte[] encrypted = encrypt(CIPHER, secret, iv, data.getBytes());
    String base64 = Base64.getEncoder().encodeToString(encrypted);
    System.out.println("Encrypted: " + base64);

    secret = makeKey("abc123");

    byte[] decypted = decrypt(CIPHER, secret, iv, encrypted);
    System.out.println(new String(decypted));
  }

  static Key makeKey(String password) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256", "BC");
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), SALT.getBytes(), ITERATIONS, KEY_LENGTH);
    return factory.generateSecret(spec);
  }

  static byte[] encrypt(String cipherInstance, Key secret, byte[] iv, byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, NoSuchProviderException {
    IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

    Cipher cipher = Cipher.getInstance(cipherInstance, "BC");
    cipher.init(Cipher.ENCRYPT_MODE, secret, ivParameterSpec);
    return cipher.doFinal(data);
  }

  static byte[] encryptWithDetails(String cipherInstance, Key secret, byte[] iv, String data) throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchProviderException, InvalidKeyException {
    byte[] dataBytes = data.getBytes();
    byte[] message = encrypt(cipherInstance, secret, iv, dataBytes);
    byte[] cipherInstanceBytes = cipherInstance.getBytes();
    byte[] ivSizeBytes = Integer.toString(iv.length).getBytes();

    byte[] out = new byte[ivSizeBytes.length + 1 + cipherInstanceBytes.length + 1 + iv.length + message.length];
    int outi = 0;

    System.arraycopy(ivSizeBytes, 0, out, outi, ivSizeBytes.length);
    outi += ivSizeBytes.length;
    out[outi++] = 0;

    System.arraycopy(cipherInstanceBytes, 0, out, outi, cipherInstanceBytes.length);
    outi += cipherInstanceBytes.length;
    out[outi++] = 0;

    System.arraycopy(iv, 0, out, outi, iv.length);
    outi += iv.length;

    MessageDigest md = MessageDigest.getInstance("SHA256", "BC");
    byte[] digest = md.digest(dataBytes);
    System.out.println("digest is: " + digest.length);

    System.arraycopy(message, 0, out, outi, message.length);
    outi += message.length;

    return out;
  }

  static byte[] decrypt(String cipherInstance, Key secret, byte[] iv, byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, NoSuchProviderException {
    IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

    Cipher cipher = Cipher.getInstance(cipherInstance, "BC");
    cipher.init(Cipher.DECRYPT_MODE, secret, ivParameterSpec);
    return cipher.doFinal(data);
  }

  static byte[] decryptWithDetails(Key secret, byte[] details) throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchProviderException, InvalidKeyException {
    int i;
    for (i = 0; i < details.length; i++) {
      if (details[i] == 0) {
        break;
      }
    }
    if (i == details.length) {
      throw new IllegalStateException("Could not find ivSize");
    }
    String ivSizeStr = new String(details, 0, i);
    System.out.println(ivSizeStr);
    int ivSize = Integer.parseInt(ivSizeStr);

    int j;
    for (j = ++i; j < details.length; j++) {
      if (details[j] == 0) {
        break;
      }
    }
    if (j == details.length) {
      throw new IllegalStateException("Could not find cipher");
    }
    String cipher = new String(details, i, j - i);
    System.out.println(cipher);

    byte[] iv = new byte[ivSize]; // TODO: det här är inte jättebra, vi borde kolla lite sanity på det här
    System.arraycopy(details, ++j, iv, 0, ivSize);

    byte[] data = new byte[details.length - j - ivSize];
    System.arraycopy(details, j + ivSize, data, 0, data.length);

    return decrypt(cipher, secret, iv, data);
  }
}
