/**
 * 
 */
package org.zwen.media.protocol.mts;

import java.nio.ByteBuffer;
import java.util.Collection;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jcodec.containers.mps.MPSUtils;
import org.jcodec.containers.mps.MPSDemuxer.PESPacket;
import org.zwen.media.AVPacket;
import org.zwen.media.AVStream;
import org.zwen.media.protocol.mts.codec.MtsDePacketizer;

public class PES {
	public int pid;
	public MtsDePacketizer visitor;

	public AVStream stream;
	private PESPacket pes;
	private ChannelBuffer buf = ChannelBuffers.EMPTY_BUFFER;

	public PES(int pid, MtsDePacketizer visitor, AVStream stream) {
		this.pid = pid;
		this.visitor = visitor;
		this.stream = stream;
	}
	

	public void readPESHeader(long position, ByteBuffer seg, Collection<AVPacket> out) {
		if (null != pes) {
			visit(visitor, out);
		}

		pes = MPSUtils.readPESHeader(seg, 0);
		pes.pos = position;
		append(seg);
	}

	public void flush(Collection<AVPacket> out) {
		if (null != pes) {
			visit(visitor, out);
		}

		if (null != visitor) {
			visitor.flush(stream, out);
		}

		pes = null;
	}

	public void append(ByteBuffer seg) {
		if (buf.writableBytes() < seg.limit()) {
			ChannelBuffer newBuf = ChannelBuffers.buffer(184 + 2 * (buf
					.readableBytes() + seg.limit()));
			newBuf.writeBytes(buf);
			buf = newBuf;
		}

		buf.writeBytes(seg);
	}

	public void visit(MtsDePacketizer visitor, Collection<AVPacket> out) {
		if (null != visitor && buf.readableBytes() > 0) {
			ByteBuffer data = ByteBuffer.allocate(buf.readableBytes());
			buf.readBytes(data);
			data.flip();
			
			pes.data = data;
			visitor.dePacket(stream, pes, out);
		}
		
		pes = null;
		buf.clear();
	}

}