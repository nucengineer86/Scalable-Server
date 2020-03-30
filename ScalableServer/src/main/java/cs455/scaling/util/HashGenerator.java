package cs455.scaling.util;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class used to compute the SHA-1 hash for a given <code>byte[]</code> of data
 */
public final class HashGenerator {

	private HashGenerator() {}
	
	/**
	 * Computes the SHA-1 hash for a given <code>byte[]</code> of data
	 * @param data <code>byte[]</code>
	 * @return <code>String</code> the SHA-1 hash string
	 */
	public static String computeSha1Hash(byte[] data) {
		
		BigInteger hashInt = null;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA1");
			byte[] hashData = digest.digest(data);
		    hashInt = new BigInteger(1, hashData);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		String hashString = hashInt.toString(16);
		StringBuilder hashBuilder = null;
		if (hashString.length() < 40) {
			int numPaddedZeros = 40 - hashString.length();
			hashBuilder = new StringBuilder(hashString);
			for (int i = 0; i < numPaddedZeros; i++) {
				hashBuilder.insert(0, '0');
			}
		}
		return hashBuilder == null ? hashString : hashBuilder.toString();
	}
}
