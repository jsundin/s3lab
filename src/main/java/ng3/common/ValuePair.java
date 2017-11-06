package ng3.common;

/**
 * @author johdin
 * @since 2017-11-06
 */
public class ValuePair <A, B> {
  private final A left;
  private final B right;

  public ValuePair(A left, B right) {
    this.left = left;
    this.right = right;
  }

  public A getLeft() {
    return left;
  }

  public B getRight() {
    return right;
  }
}
