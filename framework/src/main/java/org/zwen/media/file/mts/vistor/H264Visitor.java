package org.zwen.media.file.mts.vistor;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

import javax.media.format.VideoFormat;

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.containers.mps.MPSDemuxer.PESPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zwen.media.AVPacket;
import org.zwen.media.AVStream;
import org.zwen.media.AVTimeUnit;
import org.zwen.media.codec.video.h264.H264Extra;
import org.zwen.media.file.mts.PESVistor;

public class H264Visitor implements PESVistor {
	private static final Logger LOGGER = LoggerFactory
			.getLogger(H264Visitor.class);
	private static final byte[] START_CODE = new byte[] { 0, 0, 0, 1 };

	private H264Extra extra = new H264Extra();
	private PESPacket prePES;

	@Override
	public void flush(AVStream av, Collection<AVPacket> out) {
		if (null != prePES) {
			ByteBuffer data = prePES.data;
			List<ByteBuffer> nals = H264Utils.splitFrame(data);

			// maybe nal start code length is 3
			// but our start code length is 4
			ByteBuffer raw = ByteBuffer.allocate(data.limit() + nals.size());

			boolean isEOM = false;
			boolean isKey = false;
			ByteBuffer sps = null;
			ByteBuffer pps = null;
			for (ByteBuffer nal : nals) {
				NALUnit unit = NALUnit.read(nal.duplicate());
				switch (unit.type) {
				case SPS:
					sps = nal;
					break;
				case PPS:
					pps = nal;
					break;
				case END_OF_STREAM:
					isEOM = true;
					break;
				case IDR_SLICE:
					isKey = true;
					break;
				default:
					break;
				}

				raw.put(START_CODE);
				raw.put(nal.duplicate());
			}
			raw.flip();

			// the decode time before present picture
			long compositeTime = 0;
			if (prePES.dts > 0) {
				compositeTime = prePES.pts - prePES.dts;
			}

			
			
			AVPacket packet = new AVPacket(av);
			packet.setEOM(isEOM);
			packet.setKeyFrame(isKey);
			packet.setData(raw);
			packet.setTimeStamp(prePES.pts);
			packet.setCompositionTime(compositeTime);
			packet.setTimeUnit(AVTimeUnit.MILLISECONDS_90);
			packet.setSequenceNumber(prePES.pos);

			// set sps+pps for avstream
			if (null != sps && null != pps) {
				extra.addPps(pps);
				extra.addSps(sps);
				av.setExtra(extra);
				
				ByteBuffer buf = ByteBuffer.allocate(sps.limit() + pps.limit() + 2 * START_CODE.length);
				
				buf.put(START_CODE);
				buf.put(sps);
				buf.put(START_CODE);
				buf.put(pps);
				buf.flip();
				packet.setExtra(buf);
			}

			out.add(packet);
			prePES = null;
		}

	}

	@Override
	public void visit(AVStream av, PESPacket newPES, Collection<AVPacket> out) {
		ByteBuffer data = newPES.data;
		if (data.remaining() < 4) {
			LOGGER.warn("H264.Length too short");
			return;
		}

		if (null != prePES && startWithNewNal(data.duplicate())) {
			flush(av, out);
		}

		if (null != prePES) {
			prePES.data = concat(prePES.data, newPES.data);
		} else {
			prePES = newPES;
		}
	}

	private boolean startWithNewNal(ByteBuffer data) {
		data.mark();

		try {
			int position = data.position();
			H264Utils.skipToNALUnit(data);

			if (position + 4 >= data.position()) {
				return true;
			}

			return false;
		} finally {
			data.reset();
		}
	}

	private ByteBuffer concat(ByteBuffer b1, ByteBuffer b2) {
		ByteBuffer newBuf = ByteBuffer.allocate(b1.limit() + b2.limit());
		newBuf.put(b1);
		newBuf.put(b2);
		newBuf.flip();

		return newBuf;
	}

}
