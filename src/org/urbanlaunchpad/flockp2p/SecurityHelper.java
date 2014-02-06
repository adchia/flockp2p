package org.urbanlaunchpad.flockp2p;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import android.util.Base64;

public class SecurityHelper {
	
	// symmetric key encryption
	public static String encryptMessage(String message, String key) {
		Cipher cipher;
		String encryptedString = null;
		try {
			cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");
			cipher.init(Cipher.ENCRYPT_MODE, secretKey);
			encryptedString = Base64.encodeToString(
					cipher.doFinal(message.getBytes()), Base64.DEFAULT);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return encryptedString;
	}

	// symmetric key decryption
	public static String decryptMessage(String message, String key) {
		Cipher cipher;
		String decryptedString = null;
		try {
			cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");
			cipher.init(Cipher.DECRYPT_MODE, secretKey);
			decryptedString = new String(Base64.decode(
					cipher.doFinal(message.getBytes()), Base64.DEFAULT));
		} catch (Exception e) {
			e.printStackTrace();
		}

		return decryptedString;
	}
}
