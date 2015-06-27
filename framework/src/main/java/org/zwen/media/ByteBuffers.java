package org.zwen.media;

import java.nio.ByteBuffer;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

public class ByteBuffers {
	public static final ByteBuffer read(ByteBuffer buf, int length) {
		ByteBuffer newBuf = ByteBuffer.allocate(length);
		for (int i = 0; i < length && buf.remaining() > 0; i++) {
			newBuf.put(buf.get());
		}
		newBuf.flip();

		return newBuf;
	}

	public static final ByteBuffer copy(ByteBuffer buf) {
		if (null == buf) {
			return null;
		}

		ByteBuffer newBuf = ByteBuffer.allocate(buf.remaining());
		newBuf.put(buf.duplicate());
		newBuf.flip();

		return newBuf;
	}

	public static final String toString(ByteBuffer buf) {
		if (null == buf) {
			return "NULL";
		}

		int position = buf.position();
		byte[] data = new byte[buf.remaining()];
		buf.get(data);
		buf.position(position);

		return new String(Hex.encodeHex(data));
	}

	public static ByteBuffer decodeBase64(String value) {
		byte[] bytes = Base64.decodeBase64(value.getBytes());
		return ByteBuffer.wrap(bytes);
	}

	public static ByteBuffer decodeHex(String value) {
		try {
			byte[] bytes = Hex.decodeHex(value.toCharArray());
			return ByteBuffer.wrap(bytes);
		} catch (Exception e) {
			throw new IllegalArgumentException(value + "NOT Hex(s)");
		}
	}
}
