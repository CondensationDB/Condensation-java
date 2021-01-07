package condensation.crypto;

import androidx.annotation.NonNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA256 {
	@NonNull
	public static MessageDigest createInstance() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			// This is a fatal error
			System.exit(1);
			return null;
		}
	}
}
