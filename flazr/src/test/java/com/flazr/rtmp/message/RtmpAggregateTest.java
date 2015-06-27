package com.flazr.rtmp.message;

import java.io.File;

import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import com.flazr.rtmp.RtmpHeader;

public class RtmpAggregateTest extends TestCase {
	private ChannelBuffer data;
		
	@Override
	protected void setUp() throws Exception {
		File wiresharkPacket = new File("aggregate.pcapng");
		byte[] content = FileUtils.readFileToByteArray(wiresharkPacket);
		
		int offset = 0x180 + 12;
		int length = content.length - offset;
		data = ChannelBuffers.wrappedBuffer(content, offset, length);
		
	}
	
	public void testIsAggregate() throws Exception {
		assertEquals(data.getByte(0), 1);
		assertEquals(data.getByte(1), 0);
		assertEquals(data.getByte(2), 0x61);
	}
	
	public void testDecode() {
		RtmpHeader header = new RtmpHeader(data, new RtmpHeader[0]);;
		System.out.println(header);
		System.out.println(header.getMessageType());
	}
}
