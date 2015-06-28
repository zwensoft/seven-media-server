package org.zwen.media.rtp.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.media.Buffer;
import javax.media.PlugIn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zwen.media.AVPacket;
import org.zwen.media.AVStream;

public abstract class AbstractDePacketizer {
	private static final int MAX_BYTES_INSCREAMENT = 2048;
	protected Logger logger = LoggerFactory.getLogger(getClass());
	private AVPacket outBuffer;
	private long position;
	private AVPacket lastPkt;
	
	private Map<Integer, List<byte[]>> caches = new ConcurrentHashMap<Integer, List<byte[]>>();
	

	public void init(AVStream av, String fmtpValue) {
		
	}
	
	final public void depacket(AVStream av, Buffer inBuffer, List<AVPacket> out) {
		if (null == outBuffer) {
			outBuffer = new AVPacket(av);
			outBuffer.setTimeStamp(inBuffer.getTimeStamp());
			outBuffer.setOffset(0);
			outBuffer.setLength(0);
			outBuffer.setPosition(position);
		}
	
		position += inBuffer.getLength();
		int iret = doProcess(inBuffer, outBuffer);
		
		if (iret == PlugIn.BUFFER_PROCESSED_OK ) {
			if (!outBuffer.isDiscard()) {
				if (null != lastPkt) {
					if (lastPkt.getDuration() == Buffer.TIME_UNKNOWN) {
						lastPkt.setDuration(outBuffer.getTimeStamp() - lastPkt.getTimeStamp());
					}
					out.add(lastPkt);
				}
				lastPkt = outBuffer;
			}
			outBuffer = null;
		}
	}
	protected abstract int doProcess(Buffer inBuffer, Buffer outBuffer);
	

    /**
     * Ensures that the value of the <tt>data</tt> property of a specific
     * <tt>Buffer</tt> is an array of <tt>byte</tt>s whose length is at least a
     * specific number of bytes.
     *
     * @param buffer the <tt>Buffer</tt> whose <tt>data</tt> property value is
     * to be validated
     * @param newSize the minimum length of the array of <tt>byte</tt> which is
     * to be the value of the <tt>data</tt> property of <tt>buffer</tt>
     * @param arraycopy <tt>true</tt> to copy the bytes which are in the
     * value of the <tt>data</tt> property of <tt>buffer</tt> at the time of the
     * invocation of the method if the value of the <tt>data</tt> property of
     * <tt>buffer</tt> is an array of <tt>byte</tt> whose length is less than
     * <tt>newSize</tt>; otherwise, <tt>false</tt>
     * @return an array of <tt>byte</tt>s which is the value of the
     * <tt>data</tt> property of <tt>buffer</tt> and whose length is at least
     * <tt>newSize</tt> number of bytes
     */
    public  byte[] validateByteArraySize(
            Buffer buffer,
            int newSize,
            boolean arraycopy)
    {
        Object data = buffer.getData();
        byte[] newBytes;

        if (data instanceof byte[])
        {
            byte[] bytes = (byte[]) data;

            if (bytes.length < newSize)
            {
            	// get bytes from cache
            	int newkey = (newSize + MAX_BYTES_INSCREAMENT - 1) / MAX_BYTES_INSCREAMENT * MAX_BYTES_INSCREAMENT;
                List<byte[]> cachedItems = caches.get(newkey);
                if (null != cachedItems && !cachedItems.isEmpty()) {
                	newBytes = cachedItems.remove(0);
                } else {
                	newBytes = new byte[newkey];
                }
                
                buffer.setData(newBytes);
                if (arraycopy)
                {
                    System.arraycopy(bytes, 0, newBytes, 0, bytes.length);

                    // add to cache
                    int key = bytes.length / MAX_BYTES_INSCREAMENT * MAX_BYTES_INSCREAMENT;
                    if (key > 0) {
	                    List<byte[]> cacheItem = caches.get(key);
	                    if (null == cacheItem) {
	                    	cacheItem = new ArrayList<byte[]>();
	                    }
	                    cacheItem.add(bytes);
	                    caches.put(key, cacheItem);
                    }
                }
                
                else
                {
                    buffer.setLength(0);
                    buffer.setOffset(0);
                }
            }
            else
            {
                newBytes = bytes;
            }
        }
        else
        {
            newBytes = new byte[newSize];
            buffer.setData(newBytes);
            buffer.setLength(0);
            buffer.setOffset(0);
        }
        return newBytes;
    }

	
	/**
	 * a=fmtp:<format> <format specific parameters><br/>
	 * 
	 *  This attribute allows parameters that are specific to a
     *  particular format to be conveyed in a way that SDP does not
     *  have to understand them.  The format must be one of the formats
     *  specified for the media.  Format-specific parameters may be any
     *  set of parameters required to be conveyed by SDP and given
     *  unchanged to the media tool that will use this format.  At most
     *  one instance of this attribute is allowed for each format.
     *  It is a media-level attribute, and it is not dependent on
     *  charset.
	 */
	public static final String FMTP = "fmtp";
	
	/**
	 * a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding 
	 *   parameters>] <br/>
	 * 
	 * This attribute maps from an RTP payload type number (as used in
	 * an "m=" line) to an encoding name denoting the payload format
	 * to be used.  It also provides information on the clock rate and
	 * encoding parameters.  It is a media-level attribute that is not
	 * dependent on charset.
	 */
	public static final String RTPMAP = "rtpmap";
	
	public static final int RTP_MAX_PACKET_LENGTH = 1460;

    /**
     * Padding size for FFmpeg input buffer.
     */
    public static final int FF_INPUT_BUFFER_PADDING_SIZE = 8;

}
