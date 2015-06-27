package com.flazr.rtmp;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.HexDump;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import com.flazr.util.Utils;

import junit.framework.TestCase;

public class HandsharkTest extends TestCase {
	private ChannelBuffer c;
	private ChannelBuffer s;

	@Override
	protected void setUp() throws Exception {
		byte[] c0_c1 = FileUtils.readFileToByteArray(new File(
				"src/test/resources/c0+c1"));
		byte[] s0_s1_s2 = FileUtils.readFileToByteArray(new File(
				"src/test/resources/s0+s1+s2"));
		;
		byte[] c2 = FileUtils.readFileToByteArray(new File(
				"src/test/resources/c2"));
		;

		c = ChannelBuffers.wrappedBuffer(new byte[]{3}, c0_c1, c2);
		s = ChannelBuffers.wrappedBuffer(s0_s1_s2);
	}

	public void testC0() {
		RtmpHandshake shake = new RtmpHandshake();

		shake.decodeClient0And1(c);

		ChannelBuffer s0 = shake.encodeServer0();
		ChannelBuffer s1 = shake.encodeServer1();
		ChannelBuffer s2 = shake.encodeServer2();
		System.out.println(ChannelBuffers.hexDump(s0) + ChannelBuffers.hexDump(s1) + ChannelBuffers.hexDump(s2));
		System.out.println(ChannelBuffers.hexDump(s));
		
		
		shake.decodeClient2(c);
		shake.decodeServerAll(s);
	}
}
