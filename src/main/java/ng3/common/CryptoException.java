package ng3.common;

/**
 * @author johdin
 * @since 2017-11-06
 */
public class CryptoException extends RuntimeException {
  public CryptoException() {
    super();
  }

  public CryptoException(String message) {
    super(message);
  }

  public CryptoException(String message, Throwable cause) {
    super(message, cause);
  }

  public CryptoException(Throwable cause) {
    super(cause);
  }

  protected CryptoException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
