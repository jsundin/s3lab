package s4lab.agent;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.xml.ws.Holder;
import java.security.*;
import java.security.spec.InvalidKeySpecException;

/**
 * @author johdin
 * @since 2017-10-06
 */
public class SecurityManager {
  public static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA512";
  public static final String KEY_PROVIDER = "BC";
  public static final int KEY_ITERATIONS = 6000;
  public static final int ENCRYPTION_BITS = 128;
  public static final int KEY_LENGTH = ENCRYPTION_BITS;
  public static final String ENCRYPTION_ALGORITHM = "AES/CFB" + ENCRYPTION_BITS + "/PKCS5Padding";
  public static final String ENCRYPTION_PROVIDER = "BC";
  private final String password = "put-me-in-configuration";
  private final SecureRandom random = new SecureRandom();

  static {
    Security.insertProviderAt(new BouncyCastleProvider(), 1);
  }

  private SecurityManager() {
  }

  public static SecurityManager getInstance() {
    return new SecurityManager();
  }

  private SecretKey makeKey(byte[] salt) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException {
    SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM, KEY_PROVIDER);
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, KEY_ITERATIONS, KEY_LENGTH);
    return factory.generateSecret(spec);
  }

  public Cipher getEncryptionCipher(Holder<byte[]> keySalt, Holder<byte[]> iv) throws SecurityException {
    keySalt.value = new byte[ENCRYPTION_BITS / 8];
    iv.value = new byte[ENCRYPTION_BITS / 8];
    random.nextBytes(keySalt.value);
    random.nextBytes(iv.value);
    Cipher cipher;
    try {
      cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM, ENCRYPTION_PROVIDER);
      cipher.init(Cipher.ENCRYPT_MODE, makeKey(keySalt.value), new IvParameterSpec(iv.value));
    } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | InvalidKeySpecException e) {
      throw new SecurityException(e);
    }
    return cipher;
  }
}
