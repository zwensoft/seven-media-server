package org.zwen.media.protocol.mts;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeSet;

import javax.media.Format;
import javax.media.format.AudioFormat;
import javax.media.format.VideoFormat;

import org.jcodec.containers.mps.MTSDemuxer;
import org.jcodec.containers.mps.MTSDemuxer.MTSPacket;
import org.jcodec.containers.mps.MTSUtils.StreamType;
import org.jcodec.containers.mps.psi.PATSection;
import org.jcodec.containers.mps.psi.PMTSection;
import org.jcodec.containers.mps.psi.PMTSection.PMTStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zwen.media.AVDispatcher;
import org.zwen.media.AVPacket;
import org.zwen.media.AVStream;
import org.zwen.media.Constants;
import org.zwen.media.file.CountableByteChannel;
import org.zwen.media.file.mts.vistor.AACVistor;
import org.zwen.media.file.mts.vistor.H264Visitor;
import org.zwen.media.protocol.mts.codec.MtsDePacketizer;
import org.zwen.media.protocol.mts.codec.adts.AdtsDePacketizer;
import org.zwen.media.protocol.mts.codec.h264.H264DePacketizer;

public class MTSReader implements Closeable {
	private static Logger LOGGER = LoggerFactory.getLogger(MTSReader.class);

	// position of MTS start code
	private ByteBuffer startCode = ByteBuffer.allocate(1);
	private long position = 0;
	private boolean isClosed;

	final CountableByteChannel ch;

	// pat
	private HashSet<Integer> patPrograms = new HashSet<Integer>();
	private AdtsDePacketizer adts = new AdtsDePacketizer();
	private H264DePacketizer h264 = new H264DePacketizer();
	
	// pmt
	private int pmt;

	// pes streams
	final private PES[] pesStreams = new PES[256];
	private int numPesStreams = 0;

	private boolean dispatchedAVStreams = false;
	private int bufferLength = 50; // 0.5s for video which is 25fps
	private TreeSet<AVPacket> buffers = new TreeSet<AVPacket>(COMPARATOR);

	public MTSReader(ReadableByteChannel src) {
		ch = new CountableByteChannel(src);
	}

	public boolean isClosed() {
		return isClosed;
	}

	public int read(AVDispatcher dispatcher) throws IOException {
		ensureOpen();

		int pid;
		boolean payloadStart;
		boolean foundMPEGPacket = false;

		MTSPacket packet = null;
		ByteBuffer buffer = ByteBuffer.allocate(188);

		while (!foundMPEGPacket && null != (packet = readNextMTS(buffer))) {
			pid = packet.pid;
			payloadStart = packet.payloadStart;

			// pat
			if (0 == pid) {
				packet.payload.get(); // pointer
				PATSection pat = PATSection.parse(packet.payload);
				patPrograms.clear();

				int[] pmt = pat.getPrograms().values();
				for (int i = 0; i < pmt.length; i++) {
					patPrograms.add(pmt[i]);
				}
			}
			// pmt
			else if (patPrograms.contains(pid)) {
				packet.payload.get(); // pointer
				PMTSection pmt = PMTSection.parse(packet.payload);
				PMTStream[] streams = pmt.getStreams();

				if (this.pmt != pid) {
					this.pmt = pid;
					this.numPesStreams = streams.length;
					for (int i = 0; i < streams.length; i++) {
						pid = streams[i].getPid();
						StreamType streamType = streams[i].getStreamType();
						AVStream av = new AVStream(i);

						switch (streamType) {
						case AUDIO_AAC_ADTS:
							av.setFormat(new AudioFormat(Constants.AAC));
							break;
						case VIDEO_H264:
							av.setFormat(new VideoFormat(Constants.H264));
							break;
						default:
							if (streamType.isVideo()) {
								av
										.setFormat(new VideoFormat(streamType
												.name()));
							} else if (streamType.isAudio()) {
								av
										.setFormat(new AudioFormat(streamType
												.name()));
							} else {
								av.setFormat(new Format("unknown"));
							}
							break;
						}

						MtsDePacketizer  depacketizer = null;
						switch (streamType) {
						case AUDIO_AAC_ADTS:
							depacketizer = adts;
							break;
						case VIDEO_H264:
							depacketizer = h264;

						default:
							break;
						}
						if (null == pesStreams[i]) {
							pesStreams[i] = new PES(pid, depacketizer, av);
						} else if (pesStreams[i].pid != pid) {
							pesStreams[i].pid = pid;
							pesStreams[i].visitor = depacketizer;
							pesStreams[i].stream = av;
						}
					}

				}
			}

			// stream
			else if (null != pesStreams) {
				boolean foundIt = false;
				for (int i = 0; i < numPesStreams; i++) {
					PES pes = pesStreams[i];
					if (pes.pid == pid) {
						foundIt = true;
						ByteBuffer seg = packet.payload;
						if (payloadStart) {
							pes.readPESHeader(position, seg, buffers);
						} else {
							pes.append(seg);
						}

						int numPkts = dispatch(dispatcher, bufferLength);
						if (numPkts > 0) {
							foundMPEGPacket = true;
						}
					}
				}

				if (!foundIt) {
					LOGGER.warn("NOT FOUND PES[{}]", pid);
				}
			}
		}

		if (foundMPEGPacket) {
			return 1;
		} else if (!foundMPEGPacket && !isClosed) {
			int numPkt = flush(dispatcher);
			return numPkt < 1 ? -1 : numPkt;
		} else {
			return -1;
		}
	}

	private void ensureOpen() throws IOException {
		if (isClosed) {
			throw new IOException("Closed");
		}
	}

	public int flush(AVDispatcher dispatcher) {
		for (int i = 0; i < numPesStreams; i++) {
			pesStreams[i].flush(buffers);
		}

		return dispatch(dispatcher, 0);
	}

	private int dispatch(AVDispatcher dispatcher, int minBuffer) {
		int numPkt = 0;
		while (buffers.size() > minBuffer) {
			dispatchAVStreamsIfNeed(dispatcher);

			AVPacket pkt = buffers.pollFirst();
			AVStream stream = pesStreams[pkt.getStreamIndex()].stream;

			if (!stream.getFormat().equals(pkt.getFormat())) {
				LOGGER.error("AVStream[{}] with AVPacket NOT SAME Format",
						stream.getFormat(), pkt.getFormat());
			}

			dispatcher.firePacket(stream, pkt);
			numPkt++;
		}

		return numPkt;
	}

	private void dispatchAVStreamsIfNeed(AVDispatcher dispatcher) {
		if (!dispatchedAVStreams && numPesStreams > 0) {
			dispatchedAVStreams = true;
			AVStream[] avs = new AVStream[numPesStreams];
			for (int i = 0; i < avs.length; i++) {
				avs[i] = pesStreams[i].stream;
			}
			dispatcher.fireSetup(avs);
		}
	}

	private MTSPacket readNextMTS(ByteBuffer buffer) throws IOException {
		for (;;) {
			// find start code
			startCode.clear();
			while (ch.read(startCode) > 0) {
				if (startCode.get(0) == 0x47) {
					position = ch.position() - 1;
					break;
				}
			}
			startCode.flip();

			// read 187
			buffer.clear();
			buffer.put(startCode);
			while (buffer.hasRemaining() && ch.read(buffer) > 0) {

			}

			// decode
			if (!buffer.hasRemaining()) {
				buffer.flip();
				MTSPacket packet = MTSDemuxer.parsePacket(buffer);
				return packet;
			} else {
				return null; // EOF
			}
		}
	}

	@Override
	public void close() throws IOException {
		this.isClosed = true;
		ch.close();
	}

	private static final Comparator<? super AVPacket> COMPARATOR = new Comparator<AVPacket>() {
		@Override
		public int compare(AVPacket pkt1, AVPacket pkt2) {
			long a = pkt1.getSequenceNumber();
			long b = pkt2.getSequenceNumber();

			long p1 = pkt1.getTimeStamp();
			long p2 = pkt2.getTimeStamp();
			if (p1 != p2) {
				return p1 < p2 ? -1 : 1;
			}

			if (a == b) {
				return 0;
			} else if (a > b) {
				return 1;
			} else // a < b
			{
				return -1;
			}
		}
	};
}
