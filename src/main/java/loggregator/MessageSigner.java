/*
 *   Copyright (c) 2013 Mike Heath.  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package loggregator;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Used for signing Loggregator messages.
 *
 * @author Mike Heath <elcapo@gmail.com>
 */
class MessageSigner {

	private static final int BLOCK_SIZE = 16;

	private final SecretKey secretKey;

	public MessageSigner(String secret) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			final byte[] hash = digest.digest(secret.getBytes());

			final byte[] key = Arrays.copyOf(hash, BLOCK_SIZE);

			secretKey = new SecretKeySpec(key, "AES");
		} catch (GeneralSecurityException e) {
			throw new LoggregatorException(e);
		}
	}

	public byte[] sign(Messages.LogMessage message) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			final byte[] hash = digest.digest(message.getMessage().toByteArray());

			final byte[] iv = new byte[BLOCK_SIZE];
			ThreadLocalRandom.current().nextBytes(iv);
			final Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, iv);
			byte[] cipherText = cipher.doFinal(pad(hash));

			byte[] signature = new byte[iv.length + cipherText.length];
			System.arraycopy(iv, 0, signature, 0, iv.length);
			System.arraycopy(cipherText, 0, signature, iv.length, cipherText.length);

			return signature;
		} catch (GeneralSecurityException e) {
			throw new LoggregatorException(e);
		}
	}

	/**
	 * Creates a cipher used for signing logging events
	 *
	 * @param mode the cipher mode (encrypt/decrypt)
	 * @param iv the initialization vector
	 * @return An AES cipher
	 * @throws GeneralSecurityException
	 */
	private Cipher createCipher(int mode, byte[] iv) throws GeneralSecurityException {
		final Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
		cipher.init(mode, secretKey, new IvParameterSpec(iv));
		return cipher;
	}

	/**
	 * Apparently Go doesn't have built-in PKCS#7 padding so we're using this home baked padding.
	 *
	 * @param text the plain text to be padded.
	 * @return the padded plain text
	 */
	private byte[] pad(byte[] text) {
		final int bytesToPad = BLOCK_SIZE - text.length % BLOCK_SIZE;
		final byte[] paddedText = Arrays.copyOf(text, text.length + bytesToPad);
		paddedText[text.length] = (byte)0x80;
		for (int i = text.length + 1; i < paddedText.length; i++) {
			paddedText[i] = 0;
		}
		return paddedText;
	}

	/**
	 * Remove the padding from the plain text.
	 *
	 * @param text the padded plain text
	 * @return the plain text without the padding
	 */
	private byte[] unpad(byte[] text) {
		int unpaddedLength = text.length - 1;
		while (text[unpaddedLength] == 0) {
			unpaddedLength--;
		}
		if (text[unpaddedLength] != 0x80) {
			throw new LoggregatorException("Bad padding");
		}
		return Arrays.copyOf(text, unpaddedLength);
	}

}
