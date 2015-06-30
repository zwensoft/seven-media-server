package org.zwen.media.protocol.mts.codec.adts;

import java.nio.ByteBuffer;
import java.util.Collection;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jcodec.common.io.BitReader;
import org.jcodec.containers.mps.MPSDemuxer.PESPacket;
import org.zwen.media.AVPacket;
import org.zwen.media.AVStream;
import org.zwen.media.AVTimeUnit;
import org.zwen.media.codec.audio.aac.AACExtra;
import org.zwen.media.protocol.mts.codec.PesDePacketizer;

public class AdtsDePacketizer extends PesDePacketizer {

	int id;
	int layer;
	int crc_abs;
	int aot;
	int sr;
	int privateBit;
	int ch;
	int originalCopy;
	int home;
	int size;
	int rdb;
	int samples;
	long duration;

	private AACExtra extra = new AACExtra();

	public AdtsDePacketizer() {
	}
	
	@Override
	public void deProcess(AVStream av, PESPacket pes, Collection<AVPacket> out) {
		ByteBuffer data = pes.data.duplicate();
		ChannelBuffer buf = ChannelBuffers.wrappedBuffer(data);

		int byt;

		int aacFrameLength = 0;
		long pos = pes.pos;
		long pts = pes.pts;
		int dataStart = buf.readerIndex();
		while (buf.readableBytes() > 9) {
			byt = buf.readUnsignedByte();
			if (byt != 0xFF) {
				continue;
			}

			byt = buf.readUnsignedByte();
			if (0xF != byt >> 4) {
				continue;
			}

			int indexStart = buf.readerIndex() - 2;
			if (buf.readerIndex() - dataStart > 7) {
				AVPacket pkt = newPakcet(av, pts, pos, buf, dataStart, buf
						.readerIndex() - 2);
				out.add(pkt);

				pts += pkt.getDuration();
			}

			id = (byt >> 4) & 0x1;
			layer = (byt >> 1) & 0x3;
			crc_abs = (byt >> 0) & 0x1;

			data.position(buf.readerIndex());
			BitReader bits = new BitReader(data);
			aot = bits.readNBit(2);
			sr = bits.readNBit(4);
			privateBit = bits.readNBit(1);
			ch = bits.readNBit(3);
			originalCopy = bits.readNBit(1);
			home = bits.readNBit(1);

			bits.readNBit(2); // copy right

			size = bits.readNBit(13);
			bits.readNBit(11);
			rdb = bits.readNBit(2);
			/** num_of_raw_data_blocks_in_frame */

			aacFrameLength = size - 7;
			dataStart = indexStart + 7;
			if (0 == crc_abs) {
				aacFrameLength = size - 9;
				dataStart = indexStart + 9;
				bits.readNBit(16);
				/** CRC */
			}

			buf.readerIndex(dataStart + aacFrameLength);

			extra.setNumChannels(ch);
			extra.setSampleRateIndex(sr);
			extra.setObjectType(aot + 1);

			samples = (rdb + 1) * 1024;
			/** samples in the frame */
			duration = samples * 90 * 1000 / extra.getSampleRate();
		}

		// the aac undealed
		if (buf.writerIndex() - dataStart > 7) {
			AVPacket pkt = newPakcet(av, pts, pos, buf, dataStart - 7, buf
					.writerIndex());
			out.add(pkt);

			pts += pkt.getDuration();
		}
	}

	private AVPacket newPakcet(AVStream av, long pts, long pos,
			ChannelBuffer buf, int from, int end) {
		AVPacket packet = new AVPacket(av);
		av.setExtra(extra);

		packet.setDuration(duration);
		packet.setTimeStamp(pts);
		packet.setSequenceNumber(pos);
		packet.setTimeUnit(AVTimeUnit.MILLISECONDS_90);

		ByteBuffer data = ByteBuffer.allocate(end - from);
		buf.getBytes(from, data);
		data.flip();

		packet.setData(data);
		return packet;
	}
}
