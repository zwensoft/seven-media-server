package org.zwen.media.file.mts;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import junit.framework.TestCase;

import org.jcodec.containers.mps.MTSDemuxer;
import org.jcodec.containers.mps.MTSDemuxer.MTSPacket;

public class TestMTSWriter extends TestCase {
	private ByteBuffer dst = ByteBuffer.allocate(188);
	private ByteBuffer buf = ByteBuffer.allocate(188);
	private FileChannel ch;
	
	@Override
	protected void setUp() throws Exception {
		ch = new FileInputStream("test.ts").getChannel();
	}
	
	public void testDecode() throws IOException {
		int read =  ch.read(buf);
		buf.flip();
		assertEquals(188, read);
		
		MTSPacket  pkt = MTSDemuxer.parsePacket(buf);
		//MTSWriter.encode(pkt, dst);
		dst.flip();

		assertEquals(buf.array(), dst.array());
	}
	
	@Override
	protected void tearDown() throws Exception {
		ch.close();
	}
}
