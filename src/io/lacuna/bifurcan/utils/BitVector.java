package io.lacuna.bifurcan.utils;

import static io.lacuna.bifurcan.utils.Bits.maskBelow;

/**
 * Static methods which implement bit-range operations over a bit-vector stored within a long[].
 *
 * @author ztellman
 */
public final class BitVector {

  /**
   * @param length the bit length of the vector
   * @return a bit vector which can hold the specified number of bits
   */
  public static long[] create(int length) {
    return new long[((length - 1) >> 6) + 1];
  }

  /**
   * @param a a bit-vector
   * @param b a bit-vector
   * @return the lexicographic comparison of the two vectors
   */
  public static long compareTo(long[] a, long[] b) {
    for (int i = 0; i < a.length; i++) {
      long diff = a[i] - b[i];
      if (diff != 0) {
        return diff;
      }
    }
    return 0;
  }

  /**
   * @param bitsPerVector the number of significant bits in vector
   * @param vectors       a list of bit-vectors
   * @return the bit-wise interleaving of the values, starting with the topmost bit of the 0th vector, followed by the
   * topmost bit of the 1st vector, and downward from there
   */
  public static long[] interleave(int bitsPerVector, long[] vectors) {
    long[] interleaved = create(bitsPerVector * vectors.length);

    int offset = (interleaved.length << 6) - 1;
    for (int i = 0; i < bitsPerVector; i++) {
      long mask = 1L << i;

      for (int j = vectors.length - 1; j >= 0; j--) {
        long val = (vectors[j] & mask) >>> i;
        interleaved[offset >> 6] |= val << (63 - (offset & 63));
        offset--;
      }
    }

    return interleaved;
  }

  private static void throwLenException(int len) {
    throw new IndexOutOfBoundsException("len must be in [0, 64], but was: " + len);
  }

  /**
   * Reads a bit range from the vector, which cannot be longer than 64 bits.
   *
   * @param vector the bit vector
   * @param offset the bit offset
   * @param len    the bit length
   * @return a number representing the bit range
   */
  public static long get(long[] vector, int offset, int len) {

    if (len < 0 || len > 64) {
      throwLenException(len);
    }

    int idx = offset >> 6;
    int bitIdx = offset & 63;

    int truncatedLen = Math.min(len, 63 - bitIdx);
    long val = (vector[idx] >>> bitIdx) & maskBelow(len);

    if (len != truncatedLen) {
      val |= vector[idx + 1] & maskBelow(len - truncatedLen);
    }

    return val;
  }

  /**
   * Overwrites a bit range within the vector.
   *
   * @param vector the bit vector
   * @param val    the value to overwrite
   * @param offset the offset of the write
   * @param len    the bit length of the value
   */
  public static void overwrite(long[] vector, long val, int offset, int len) {

    if (len < 0 || len > 64) {
      throwLenException(len);
    }

    int idx = offset >> 6;

    int bitIdx = offset & 63;
    int truncatedValLen = Math.min(len, 64 - bitIdx);

    vector[idx] &= ~(maskBelow(truncatedValLen) << bitIdx);
    vector[idx] |= val << bitIdx;

    if (len != truncatedValLen) {
      long mask = maskBelow(len - truncatedValLen);
      vector[idx + 1] &= ~mask;
      vector[idx + 1] |= (val >>> truncatedValLen);
    }
  }

  /**
   * Copies a bit range from one vector to another.
   *
   * @param src       the source vector
   * @param srcOffset the bit offset within src
   * @param dst       the destination vector
   * @param dstOffset the bit offset within dst
   * @param len       the length of the bit range
   */
  public static void copy(long[] src, int srcOffset, long[] dst, int dstOffset, int len) {

    int srcLimit = srcOffset + len;

    while (srcOffset < srcLimit) {

      int srcIdx = srcOffset & 63;
      int dstIdx = dstOffset & 63;
      int srcRemainder = 64 - srcIdx;
      int dstRemainder = 64 - dstIdx;

      int chunkLen = Math.min(srcRemainder, dstRemainder);
      long mask = maskBelow(chunkLen) << srcIdx;
      dst[dstOffset >> 6] |= ((src[srcOffset >> 6] & mask) >>> srcIdx) << dstOffset;

      srcOffset += chunkLen;
      dstOffset += chunkLen;
    }
  }

  /**
   * Returns a copy of the vector, with an empty bit range inserted at the specified location.
   *
   * @param vector    the bit vector
   * @param vectorLen the length of the bit vector
   * @param offset    the offset within the bit vector
   * @param len       the length of the empty bit range
   * @return an updated copy of the vector
   */
  public static long[] insert(long[] vector, int vectorLen, int offset, int len) {
    long[] updated = create(vectorLen + len);

    int idx = offset >> 6;
    System.arraycopy(vector, 0, updated, 0, idx);

    int delta = offset & 63;
    updated[idx] |= vector[idx] & maskBelow(delta);

    copy(vector, offset, updated, offset + len, vectorLen - offset);

    return updated;
  }

  /**
   * Returns a copy of the vector, with a bit range excised from the specified location.
   *
   * @param vector    the bit vector
   * @param vectorLen the length of the bit vector
   * @param offset    the offset within the bit vector
   * @param len       the length of the excised bit range
   * @return an updated copy of the vector
   */
  public static long[] remove(long[] vector, int vectorLen, int offset, int len) {
    long[] updated = create(vectorLen - len);

    int idx = offset >> 6;
    System.arraycopy(vector, 0, updated, 0, idx);

    int delta = offset & 63;
    updated[idx] |= vector[idx] & maskBelow(delta);

    copy(vector, offset + len, updated, offset, vectorLen - (offset + len));

    return updated;
  }
}
