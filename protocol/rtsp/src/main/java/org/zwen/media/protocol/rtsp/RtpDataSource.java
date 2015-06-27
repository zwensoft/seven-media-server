package org.zwen.media.protocol.rtsp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.format.AudioFormat;
import javax.media.format.VideoFormat;
import javax.media.rtp.SessionAddress;
import javax.sdp.Media;
import javax.sdp.MediaDescription;
import javax.sdp.SdpParseException;

import jlibrtp.Participant;
import jlibrtp.RTPAppIntf;
import jlibrtp.RTPSession;
import jlibrtp.RtpPkt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zwen.media.AVDispatcher;
import org.zwen.media.AVPacket;
import org.zwen.media.AVStream;
import org.zwen.media.AVTimeUnit;
import org.zwen.media.URLUtils;
import org.zwen.media.rtp.codec.AbstractDePacketizer;
import org.zwen.media.rtp.codec.audio.aac.Mpeg4GenericCodec;
import org.zwen.media.rtp.codec.video.h264.H264DePacketizer;

public class RtpDataSource implements RTPAppIntf {

	private static final Logger logger = LoggerFactory
			.getLogger(RtpDataSource.class);

	private AVStream av;
	private MediaDescription md;

	private int rtpBufferLength = 1;
	private int payloadType = 0;
	private String payloadName = "UNKNOWN";
	
	private boolean connected = false;
	private int ssrc;
	private SessionAddress localAddress;
	private SessionAddress remoteAddress;
	private RTPSession rtpSession = null;

	// RTP-Info=url=rtsp://127.0.0.1:8554/trackID=0;seq=10268;rtptime=5875310,
	// url=rtsp://127.0.0.1:8554/trackID=1;seq=15231;rtptime=23980860
	private boolean started = false;
	private int seq = AVStream.UNKNOWN;
	private long rtpTime = AVStream.UNKNOWN;
	private AVDispatcher dispatcher;
	private List<AVPacket> out = new ArrayList<AVPacket>();

	// Range=npt=7.143000-
	private long npt;

	private AbstractDePacketizer dePacketizer;

	public RtpDataSource(AVStream stream, MediaDescription md) {
		this.av = stream;
		this.md = md;

		try {
			init(md);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/**
	 * for: <br>
	 * a=control:rtsp://127.0.0.1:8554/trackID=2
	 * 
	 * @param base
	 * @return
	 */
	public String getControlUrl(String base) {
		try {
			String control = md.getAttribute("control");
			return URLUtils.getAbsoluteUrl(base, control);
		} catch (Exception e) {
			logger.warn("fail get control for {}", e.getMessage());
			return null;
		}
	}

	/**
	 * @param md
	 *            <pre>
	 * m=video 0 RTP/AVP 96
	 * a=control:trackID=0
	 * a=framerate:25.000000
	 * a=rtpmap:96 H264/90000
	 * a=fmtp:96 packetization-mode=1;profile-level-id=4D001F;sprop-parameter-sets=Z00AH9oBQBbpUgAAAwACAAADAGTAgAC7fgAD9H973wvCIRqA,aM48gA==
	 * 
	 * OR:
	 * 
	 * m=audio 0 RTP/AVP 8
	 * a=control:trackID=1
	 * a=rtpmap:8 PCMA/8000
	 * @throws SdpParseException
	 */
	public void init(MediaDescription md) throws SdpParseException {
		Media media = md.getMedia();
		
		// set rtp buffer size
		if ("audio".equals(media.getMediaType())) {
			rtpBufferLength = 4;
		} else if ("video".equals(media.getMediaType())) {
			rtpBufferLength = 11; // 11 * 1400 = 16KB
		} else {
			logger.warn("unknown media {}", media.getMediaType());
			return;
		}

		// set rtpmap
		String rtpmap = md.getAttribute("rtpmap");
		if (null == rtpmap) {
			logger.warn("NOT found rtpmap");
			return;
		}
		Matcher matcher = Pattern.compile(
				"^(\\d+) ([^/]+)(/(\\d+)(/([^/]+))?)?(.*)?").matcher(rtpmap);
		if (!matcher.matches()) {
			logger.warn("{} decode fail", rtpmap);
			return;
		}
		String payloadType = matcher.group(1);
		String name = matcher.group(2);
		String clockRate = matcher.group(4);
		String ext = matcher.group(6);
		this.payloadType = Integer.parseInt(payloadType);
		this.payloadName = name;
		this.av.setTimeUnit(AVTimeUnit.valueOf(Integer.parseInt(clockRate)));
		if ("audio".equals(media.getMediaType())) {
			this.av.setNumChannels(1);
			try{
				if (null != ext) {
					this.av.setNumChannels(Integer.parseInt(ext));
				}
			}catch (Exception e) {
				logger.warn("unknown numChannels: {}", ext);
			}
		}
		
		if (!md.getMedia().getMediaFormats(true).contains(payloadType)) {
			throw new IllegalArgumentException(payloadType + "NOT EXISTS in" + md.getMedia().getMediaFormats(true));
		}
		
		// set frame rate
		String frameRate = md.getAttribute("framerate");
		if (null != frameRate) {
			try {
			this.av.setFrameRate(Double.parseDouble(frameRate));
			} catch (Exception e) {
				logger.warn("unknown framerate: {}", frameRate);
			}
		}
		
		// init DePacketizer
		if ("H264".equalsIgnoreCase(name)) {
			this.dePacketizer = new H264DePacketizer();
			this.av.setFormat(new VideoFormat("H264"));
		} else if ("MPEG4-GENERIC".equalsIgnoreCase(name)) {
			this.dePacketizer = new Mpeg4GenericCodec();
			this.av.setFormat(new AudioFormat("AAC"));
		} else if ("audio".equals(media.getMediaType())) {
			this.av.setFormat(new AudioFormat(name.toUpperCase()));
		} else if ("video".equals(media.getMediaType())) {
			this.av.setFormat(new VideoFormat(name.toUpperCase()));
		} else {
			this.av.setFormat(new Format("UNKNOWN"));
		}
		
		String fmtpValue = md.getAttribute("fmtp");
		if (null != fmtpValue) {
			matcher = Pattern.compile("([\\d]+)([\\s]+)(.+)").matcher(fmtpValue);
			if (!matcher.matches()) {
				throw new IllegalArgumentException(fmtpValue + " is unknown");
			} if (null != this.dePacketizer && null != fmtpValue) {
				this.dePacketizer.init(av, fmtpValue);
			}
		}
		
		
	}

	public boolean connect()  {
		SessionAddress local = this.localAddress;
		SessionAddress remote = this.remoteAddress;
		DatagramSocket rtpSocket = null;
		DatagramSocket rtcpSocket = null;

		try {
			rtpSocket = new DatagramSocket(local.getDataPort());
			rtcpSocket = new DatagramSocket(local.getControlPort());
			rtpSocket.setReceiveBufferSize(1500 * 64);
			rtcpSocket.setReceiveBufferSize(1024);
			
			rtpSession = new RTPSession(rtpSocket, rtcpSocket);
			rtpSession.RTPSessionRegister(this, null, null);
			rtpSession.addParticipant(new Participant(remote.getDataAddress()
					.getHostAddress(), remote.getDataPort(), remote
					.getControlPort()));

			// send check rtp packet for NAT, must TWO rtp for check we meet
			// Symmetric NAT or NOT
			byte[] buf = "abcdef".getBytes("UTF-8");
			rtpSocket.send(new DatagramPacket(buf, buf.length,
					new InetSocketAddress(remote.getDataAddress(), remote
							.getDataPort())));
			rtpSocket.send(new DatagramPacket(buf, buf.length,
					new InetSocketAddress(remote.getDataAddress(), remote
							.getDataPort())));

			// send rtcp
			ByteBuffer rtcpRR = ByteBuffer.allocate(32);
			rtcpRR.putInt(0x80c90001); // receive port
			rtcpRR.putInt(ssrc);
			rtcpRR.putInt(0x81ca0005); // source description
			rtcpRR.putInt(ssrc);
			rtcpRR.put((byte) 0x01);
			buf = "res@0.0.0.0".getBytes();
			rtcpRR.put((byte) buf.length);
			rtcpRR.put(buf);
			rtcpRR.put((byte) 0x00);
			buf = rtcpRR.array();
			rtcpSocket.send(new DatagramPacket(buf, buf.length,
					new InetSocketAddress(remote.getDataAddress(), remote
							.getControlPort())));
			rtcpSocket.send(new DatagramPacket(buf, buf.length,
					new InetSocketAddress(remote.getDataAddress(), remote
							.getControlPort())));

			connected = true;
			return true;
		} catch (Exception e) {
			closeQuietly(rtpSocket);
			closeQuietly(rtcpSocket);
			logger.error(e.getMessage(), e);
			return false;
		}
	}
	
	public void disconnect() {
		if (null != rtpSession) {
			rtpSession.endSession();
			rtpSession = null;
		}
		
		if (null != localAddress) {
			PortManager.removePort(localAddress.getDataPort());
			PortManager.removePort(localAddress.getControlPort());
			localAddress = null;
		}
	}

	public void start() {
		if (!connected) {
			throw new IllegalStateException("please call connect first");
		}
		if (started) {
			throw new IllegalStateException("its started");
		}
		if (null == dispatcher) {
			throw new IllegalStateException("dispatcher is NULL");
		}
		
		started = true;
	}

	private void closeQuietly(DatagramSocket socket) {
		if (null != socket) {
			socket.close();
		}
	}

	@Override
	public void userEvent(int type, Participant[] participant) {
		System.out.println(type);
	}

	@Override
	public void receiveData(RtpPkt frame, Participant participant) {
		if (!started) {
			logger.info("unstart, ignore {}", frame);
			return;
		}
		
		if (frame.getPayloadType() != payloadType) {
			logger.debug("except payloadType {}, but real is {}", payloadType, frame.getPayloadType());
			return;
		}
		
		if (null == this.dePacketizer) {
			logger.debug("dePacketizer is NULL");
			return;
		}
		
		byte[] data = frame.getPayload();
		
		Buffer inBuffer = new Buffer();
		inBuffer.setFormat(av.getFormat());
		inBuffer.setSequenceNumber(frame.getSeqNumber());
		inBuffer.setData(data);
		inBuffer.setOffset(0);
		inBuffer.setLength(frame.getPayloadLength());
		inBuffer.setTimeStamp(frame.getTimeStamp());
		if (rtpTime != AVStream.UNKNOWN) {
			inBuffer.setTimeStamp(frame.getTimeStamp() - rtpTime);
		}
		
		this.dePacketizer.depacket(av, inBuffer, out);
		while(!out.isEmpty()) {
			AVPacket pkt = out.remove(0);
			dispatcher.firePacket(av, pkt);
		}
	}

	@Override
	public int getBufferSize() {
		return rtpBufferLength;
	}
	
	@Override
	public int getFirstSeqNumber() {
		return this.seq;
	}

	public void setRtpTime(long rtptime) {
		this.rtpTime = rtptime;
	}

	public void setSeq(int seq) {
		this.seq = seq;
	}

	public AVStream getAvStream() {
		return av;
	}
	
	public void setSsrc(int ssrc) {
		this.ssrc = ssrc;
	}
	public void setDispatcher(AVDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}
	
	public void setLocalAddress(SessionAddress local) {
		this.localAddress = local;
	}
	
	public void setRemoteAddress(SessionAddress remote) {
		this.remoteAddress = remote;
	}
}