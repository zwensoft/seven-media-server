package org.zwen.media.protocol.mts;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import org.zwen.media.AVDispatcher;
import org.zwen.media.file.CountableByteChannel;

public class MTSReader implements Closeable {
	private boolean isClosed;

	final CountableByteChannel ch;
	final MTSDePacketizer dePacketizer = new MTSDePacketizer();
	private ByteBuffer buffer = ByteBuffer.allocate(64 * MTSDePacketizer.MTS_LENGTH);

	public MTSReader(ReadableByteChannel src) {
		ch = new CountableByteChannel(src);
	}

	public boolean isClosed() {
		return isClosed;
	}

	public int read(AVDispatcher dispatcher) throws IOException {
		ensureOpen();

		int bytes = ch.read(buffer);
		if (bytes < 0) {
			dePacketizer.flush(dispatcher);
			return -1; // EOM
		}
		
		if (bytes < MTSDePacketizer.MTS_LENGTH) {
			return 0; // data not enough
		}
		
		buffer.flip();
		int numPkts = dePacketizer.dePacket(buffer, dispatcher);
		
		int remains = buffer.remaining();
		int offset = buffer.position();
		buffer.clear();
		if (remains > 0) {
			for (int i = 0; i < remains; i++) {
				buffer.put(i, buffer.get(offset + i));
			}
			buffer.position(remains);
		}
		
		return numPkts;
	}

	private void ensureOpen() throws IOException {
		if (isClosed) {
			throw new IOException("Closed");
		}
	}

	public int flush(AVDispatcher dispatcher) {
		return dePacketizer.flush(dispatcher);
	}



	@Override
	public void close() throws IOException {
		this.isClosed = true;
		ch.close();
	}

}
