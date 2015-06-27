package org.zwen.media.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class CountableByteChannel implements ReadableByteChannel{
	private long position = 0;
	private ReadableByteChannel r;
	public CountableByteChannel(ReadableByteChannel r) {
		this.r = r;
	}
	
	@Override
	public int read(ByteBuffer dst) throws IOException {
		int rst = r.read(dst);
		if (rst > 0){
			position += rst;
		}
		
		return rst;
	}

	@Override
	public void close() throws IOException {
		r.close();
	}
	
	public long position() {
		return position;
	}

	@Override
	public boolean isOpen() {
		return r.isOpen();
	}

}
