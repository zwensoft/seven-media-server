package org.zwen.media.file.flv;

import java.nio.ByteBuffer;

import junit.framework.TestCase;

import org.jcodec.common.io.BitWriter;
import org.zwen.media.ByteBuffers;

public class TestFlvWriter extends TestCase {
	public void testAudioSpecialConfig() {
		int profile = 5;
		int sampleRateIndex = 11;
		int numChannels = 2;
		
		ByteBuffer aacHeader = ByteBuffer.allocate(2);
		BitWriter w = new BitWriter(aacHeader);
		w.writeNBit(profile, 5);
		w.writeNBit(sampleRateIndex, 4);
		w.writeNBit(numChannels, 4);
		w.writeNBit(0, 1); //frame length - 1024 samples
		w.writeNBit(0, 1); //does not depend on core coder
		w.writeNBit(0, 1); //is not extension
		w.flush();
		aacHeader.flip();
		
		System.out.println(ByteBuffers.toString(aacHeader));
	}
}
