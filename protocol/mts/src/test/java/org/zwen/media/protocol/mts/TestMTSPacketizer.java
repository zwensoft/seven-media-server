package org.zwen.media.protocol.mts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.media.format.AudioFormat;
import javax.media.format.VideoFormat;

import org.jcodec.containers.mps.MTSDemuxer;
import org.jcodec.containers.mps.MTSDemuxer.MTSPacket;
import org.zwen.media.AVStream;

import flavor.BuffBitstream;
import flavor.IBitstream;

import junit.framework.TestCase;

public class TestMTSPacketizer extends TestCase {
	public void testPacketizer() throws IOException {
		AVStream stream1 = new AVStream(0);
		stream1.setFormat(new VideoFormat("AVC"));
		
		AVStream stream2 = new AVStream(1);
		stream2.setFormat(new AudioFormat("ADTS"));
		
		List<ByteBuffer> out = new ArrayList<ByteBuffer>();
		MTSPacketizer p = new MTSPacketizer();
		p.process(new AVStream[]{stream1, stream2}, out);
		
		IBitstream stream;
		ByteBuffer payload;
		byte[] data;
		
		// decode pat
		MTSPacket pkt1 = MTSDemuxer.parsePacket(out.get(0));
		flavor.generated.PAT pat = new flavor.generated.PAT();
		payload = pkt1.payload;
		data = new byte[payload.remaining()];
		payload.get(data);
		stream = new BuffBitstream(data);
		pat.get(stream);
		
		System.out.println(">>>>>>>>>>>>>>>>>>>>");;
		
		// decode pmt
		MTSPacket pkt2 = MTSDemuxer.parsePacket(out.get(1));
		flavor.generated.PMT parser = new flavor.generated.PMT();
		payload = pkt2.payload;
		data = new byte[payload.remaining()];
		payload.get(data);
		stream = new BuffBitstream(data);
		parser.get(stream);
		
		System.out.println(out);
	}
}
