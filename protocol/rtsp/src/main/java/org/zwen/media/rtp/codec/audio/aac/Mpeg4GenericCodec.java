package org.zwen.media.rtp.codec.audio.aac;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.media.Buffer;
import javax.media.PlugIn;

import org.jcodec.common.io.BitReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zwen.media.AVStream;
import org.zwen.media.ByteBuffers;
import org.zwen.media.codec.audio.aac.AACExtra;
import org.zwen.media.rtp.codec.AbstractDePacketizer;

/**
 * 
 * @see http://tools.ietf.org/html/rfc3640
 * 
 * @author chenxiuheng@gmail.com
 */
public class Mpeg4GenericCodec extends AbstractDePacketizer {
	private static final Logger logger = LoggerFactory
			.getLogger(Mpeg4GenericCodec.class);


	private int streamtype = 5;
	private int profileLevelId = 15;
	private String mode = "AAC-hbr";
	private int sizeLength = 13;
	private int indexLength = 3;
	private int indexDeltaLength = 3;
	private int profile = 1;

	private int au_headers_length_bytes;
	private int nb_au_headers;
	private AUHeaders[] au_headers;

	private int cur_au_index;

	private long lastTimestamp;
	
	AACExtra extra = new AACExtra();
	
	public Mpeg4GenericCodec() {
	}


@Override
public void init(AVStream av, String fmtpValue) {
		
		// a=fmtp:96 streamtype=5; profile-level-id=15; mode=AAC-hbr;
		// config=139056e5a54800; SizeLength=13; IndexLength=3;
		// IndexDeltaLength=3; Profile=1;

		String fmtp = fmtpValue;
		Matcher matcher = Pattern.compile("([^;\\s=]+)=([^;\\s]+)")
				.matcher(fmtp);
		while (matcher.find()) {
			String key = matcher.group(1);
			String value = matcher.group(2);

			if ("streamtype".equalsIgnoreCase(key)) {
				streamtype = Integer.valueOf(value);
			} else if ("profile-level-id".equalsIgnoreCase(key)) {
				profileLevelId = Integer.valueOf(value);
			} else if ("mode".equalsIgnoreCase(key)) {
				mode = value;
			} else if ("config".equalsIgnoreCase(key)) {
				ByteBuffer config = ByteBuffers.decodeHex(value);
				
				BitReader reader = new BitReader(config);
				
				extra.setObjectType(reader.readNBit(5));
				extra.setSampleRateIndex(reader.readNBit(4));
				extra.setNumChannels(reader.readNBit(4));
			} else if ("SizeLength".equalsIgnoreCase(key)) {
				sizeLength = Integer.valueOf(value);
			} else if ("IndexLength".equalsIgnoreCase(key)) {
				indexLength = Integer.valueOf(value);
			} else if ("IndexDeltaLength".equalsIgnoreCase(key)) {
				indexDeltaLength = Integer.valueOf(value);
			} else if ("Profile".equalsIgnoreCase(key)) {
				profile = Integer.valueOf(value);
			} else {
				logger.info("ignored [{}={}]", key, value);
			}
		}

		av.setExtra(extra);
	}

	
	protected int doProcess(Buffer inBuffer, Buffer outBuffer) {
		ByteBuffer data = ByteBuffer.wrap((byte[])inBuffer.getData(),inBuffer.getOffset(), inBuffer.getLength());
		boolean readAuHeaders = readAUHeaders(data);
		if (!readAuHeaders) {
			return PlugIn.BUFFER_PROCESSED_FAILED; // fail decode au_headers
		}

		if (cur_au_index > nb_au_headers) {
			logger.error("Invalid parser state");
			return PlugIn.BUFFER_PROCESSED_FAILED;
		}

		if (cur_au_index == nb_au_headers) {
			cur_au_index = 0;
		}

		if (nb_au_headers == 1 && data.remaining() <= au_headers[0].size) {
			/* Packet is fragmented */
			ByteBuffer read = ByteBuffers.read(data, au_headers[0].size);
			outBuffer.setData(read.array());
			outBuffer.setLength(read.remaining());
			outBuffer.setDuration(inBuffer.getTimeStamp() - lastTimestamp);
			lastTimestamp = inBuffer.getTimeStamp();
			
			return PlugIn.BUFFER_PROCESSED_OK;
		}
		
		if (data.remaining() < au_headers[0].size) {
			logger.error("First AU larger than packet size");
			return PlugIn.BUFFER_PROCESSED_FAILED;
		}

		ByteBuffer read = ByteBuffers.read(data, au_headers[0].size);
		outBuffer.setData(read.array());
		outBuffer.setLength(read.remaining());
		outBuffer.setDuration(inBuffer.getTimeStamp() - lastTimestamp);
		lastTimestamp = inBuffer.getTimeStamp();
		return PlugIn.BUFFER_PROCESSED_OK;
		
	}

	private boolean readAUHeaders(ByteBuffer data) {
		int au_headers_length, au_header_size, i;

		if (data.remaining() < 2) {
			logger.error("Too Short");
			return false;
		}

		/*
		 * decode the first 2 bytes where the AUHeader sections are stored
		 * length in bits
		 */
		au_headers_length = data.getShort();
		if (au_headers_length > RTP_MAX_PACKET_LENGTH) {
			logger.error("{} > {}", au_headers_length, RTP_MAX_PACKET_LENGTH);
			return false;
		}

		au_headers_length_bytes = (au_headers_length + 7) / 8;

		if (au_headers_length_bytes > data.remaining()) {
			logger.error("{} > {}");
			return false;
		}

		ByteBuffer au_headers = ByteBuffers.read(data, au_headers_length_bytes);
		BitReader reader = new BitReader(au_headers);

		/*
		 * XXX: Wrong if optional additional sections are present (cts, dts
		 * etc...)
		 */
		au_header_size = sizeLength + indexLength;
		if (au_header_size <= 0 || (au_headers_length % au_header_size != 0)) {
			logger.error("au_headers_length % au_header_size = {} % {}",
					au_headers_length, au_header_size);
			return false;
		}

		nb_au_headers = au_headers_length / au_header_size;
		if (this.au_headers == null || this.au_headers.length < nb_au_headers) {
			this.au_headers = new AUHeaders[nb_au_headers];
			Arrays.fill(this.au_headers, new AUHeaders());
		}

		nb_au_headers = au_headers_length / au_header_size;
		for (i = 0; i < nb_au_headers; ++i) {
			this.au_headers[i].size = reader.readNBit(sizeLength);
			this.au_headers[i].index = reader.readNBit(indexLength);
		}

		return true;
	}



	public static class AUHeaders {
		public int size;
		public int index;
	}
}
