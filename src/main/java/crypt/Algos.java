package crypt;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Provider;
import java.security.Security;
import java.util.Enumeration;

/**
 * @author johdin
 * @since 2017-09-27
 */
public class Algos {
  public static void main(String[] args) {
    Security.insertProviderAt(new BouncyCastleProvider(), 1);
    try {
      Provider p[] = Security.getProviders();
      for (int i = 0; i < p.length; i++) {
        System.out.println(p[i]);
        for (Enumeration e = p[i].keys(); e.hasMoreElements();)
          System.out.println("\t" + e.nextElement());
      }
    } catch (Exception e) {
      System.out.println(e);
    }
  }
}
