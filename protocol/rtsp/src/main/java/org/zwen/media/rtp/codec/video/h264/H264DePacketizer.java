package org.zwen.media.rtp.codec.video.h264;

import static javax.media.PlugIn.BUFFER_PROCESSED_OK;
import static javax.media.PlugIn.INPUT_BUFFER_NOT_CONSUMED;
import static javax.media.PlugIn.OUTPUT_BUFFER_NOT_FILLED;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.media.Buffer;

import org.zwen.media.AVStream;
import org.zwen.media.ByteBuffers;
import org.zwen.media.codec.video.h264.H264Extra;
import org.zwen.media.rtp.codec.AbstractDePacketizer;

public class H264DePacketizer extends AbstractDePacketizer {
	private H264Extra extra = new H264Extra();;


	 /**
     * The bytes to prefix any NAL unit to be output by this
     * <tt>DePacketizer</tt> and given to a H.264 decoder. Includes
     * start_code_prefix_one_3bytes. According to "B.1 Byte stream NAL unit
     * syntax and semantics" of "ITU-T Rec. H.264 Advanced video coding for
     * generic audiovisual services", zero_byte "shall be present" when "the
     * nal_unit_type within the nal_unit() is equal to 7 (sequence parameter
     * set) or 8 (picture parameter set)" or "the byte stream NAL unit syntax
     * structure contains the first NAL unit of an access unit in decoding
     * order".
     */
    public static final byte[] NAL_PREFIX = { 0, 0, 0, 1 };
    
    /**
     * The indicator which determines whether incomplete NAL units are output
     * from the H.264 <tt>DePacketizer</tt> to the decoder. It is advisable to
     * output incomplete NAL units because the FFmpeg H.264 decoder is able to
     * decode them. If <tt>false</tt>, incomplete NAL units will be discarded
     * and, consequently, the video quality will be worse (e.g. if the last RTP
     * packet of a fragmented NAL unit carrying a keyframe does not arrive from
     * the network, the whole keyframe will be discarded and thus all NAL units
     * upto the next keyframe will be useless).
     */
    private static final boolean OUTPUT_INCOMPLETE_NAL_UNITS = true;
    
    /**
     * The indicator which determines whether this <tt>DePacketizer</tt> has
     * successfully processed an RTP packet with payload representing a
     * "Fragmentation Unit (FU)" with its Start bit set and has not encountered
     * one with its End bit set.
     */
    private boolean fuaStartedAndNotEnded = false;
    
    
    /**
     * The time stamp of the last received key frame.
     */
    private long lastKeyFrameTime = -1;
    
    
    /**
     * Keeps track of last (input) sequence number in order to avoid
     * inconsistent data.
     */
    private long lastSequenceNumber = -1;
    
    /**
     * The <tt>nal_unit_type</tt> as defined by the ITU-T Recommendation for
     * H.264 of the last NAL unit given to this <tt>DePacketizer</tt> for
     * processing. In the case of processing a fragmentation unit, the value is
     * equal to the <tt>nal_unit_type</tt> of the fragmented NAL unit.
     */
    private int nal_unit_type;
    
    /**
     * The size of the padding at the end of the output data of this
     * <tt>DePacketizer</tt> expected by the H.264 decoder.
     */
    private final int outputPaddingSize
        = FF_INPUT_BUFFER_PADDING_SIZE;
    
    /**
     * The indicator which determines whether this <tt>DePacketizer</tt> is to
     * request a key frame from the remote peer associated with
     * {@link #keyFrameControl}.
     */
    private boolean requestKeyFrame = false;

    /**
     * The <tt>Thread</tt> which is to asynchronously request key frames from
     * the remote peer associated with {@link #keyFrameControl} on behalf of
     * this <tt>DePacketizer</tt> and in accord with {@link #requestKeyFrame}.
     */
    private Thread requestKeyFrameThread;
    
    /* (non-Javadoc)
     * @see org.zwen.media.rtp.codec.AbstractDePacketizer#init(org.zwen.media.AVStream, java.lang.String)
     */
    @Override
    public void init(AVStream av, String fmtpValue) {
		Matcher matcher;

		// packetization-mode=1;profile-level-id=4D001F;sprop-parameter-sets=Z00AH9oBQBbpUgAAAwACAAADAGTAgAC7fgAD9H973wvCIRqA,aM48gA==
		matcher = Pattern.compile("([^;\\s=]+)=(([^;\\s,]+)(,([^;\\s]+))?)").matcher(fmtpValue);
		while(matcher.find()) {
			String key = matcher.group(1).toLowerCase();
			String value = matcher.group(2);
			
			if ("profile-level-id".equals(key)) {
				ByteBuffer profile = ByteBuffers.decodeHex(matcher.group(2));
				extra.setProfile(profile);
			} else if ("sprop-parameter-sets".equals(key)) {
				ByteBuffer sps = ByteBuffers.decodeBase64(matcher.group(3));
				extra.addSps(sps);

				ByteBuffer pps = ByteBuffers.decodeBase64(matcher.group(5));
				extra.addPps(pps);
			} else {
				logger.info("ignored [{}={}]", key, matcher.group(2));
			}
		}
		
		av.setExtra(extra);
	}


    /**
     * Processes (depacketizes) a buffer.
     *
     * @param inBuffer input buffer
     * @param outBuffer output buffer
     * @return <tt>BUFFER_PROCESSED_OK</tt> if buffer has been successfully
     * processed
     */
    @Override
    @SuppressWarnings("fallthrough")
    protected int doProcess(Buffer inBuffer, Buffer outBuffer)
    {
        /*
         * We'll only be depacketizing, we'll not act as an H.264 parser.
         * Consequently, we'll only care about the rules of
         * packetizing/depacketizing. For example, we'll have to make sure that
         * no packets are lost and no other packets are received when
         * depacketizing FU-A Fragmentation Units (FUs).
         */
        long sequenceNumber = inBuffer.getSequenceNumber();
        int ret;
        boolean requestKeyFrame = (lastKeyFrameTime == -1);

        if ((lastSequenceNumber != -1)
                && ((sequenceNumber - lastSequenceNumber) != 1))
        {
            /*
             * Even if (the new) sequenceNumber is less than lastSequenceNumber,
             * we have to use it because the received sequence numbers may have
             * reached their maximum value and wrapped around starting from
             * their minimum value again.
             */
            if (logger.isTraceEnabled())
                logger.trace(
                        "Dropped RTP packets upto sequenceNumber "
                            + lastSequenceNumber
                            + " and continuing with sequenceNumber "
                            + sequenceNumber);

            /*
             * If a frame has been lost, then we may be in a need of a key
             * frame.
             */
            requestKeyFrame = true;

            ret = reset(outBuffer);

            if ((ret & OUTPUT_BUFFER_NOT_FILLED) == 0)
            {
                /*
                 * TODO Do we have to reset the nal_unit_type field of this
                 * DePacketizer to UNSPECIFIED_NAL_UNIT_TYPE here? If ret
                 * contains INPUT_BUFFER_NOT_CONSUMED, it seems safe to not
                 * reset it because the input Buffer will be returned for
                 * processing during the next call.
                 */

                setRequestKeyFrame(requestKeyFrame);

                return ret;
            }
        }

        /*
         * Ignore the RTP time stamp reported by JMF because it is not the
         * actual RTP packet time stamp send by the remote peer but some locally
         * calculated JMF value.
         */

        lastSequenceNumber = sequenceNumber;

        byte[] in = (byte[]) inBuffer.getData();
        int inOffset = inBuffer.getOffset();
        byte octet = in[inOffset];

        /*
         * NRI equal to the binary value 00 indicates that the content of the
         * NAL unit is not used to reconstruct reference pictures for inter
         * picture prediction. Such NAL units can be discarded without risking
         * the integrity of the reference pictures. However, it is not the place
         * of the DePacketizer to take the decision to discard them but of the
         * H.264 decoder.
         */

        /*
         * The nal_unit_type of the NAL unit given to this DePacketizer for
         * processing. In the case of processing a fragmentation unit, the value
         * is equal to the nal_unit_type of the fragmentation unit, not the
         * fragmented NAL unit and is thus in contrast with the value of the
         * nal_unit_type field of this DePacketizer.
         */
        int nal_unit_type = octet & 0x1F;

        // Single NAL Unit Packet
        if ((nal_unit_type >= 1) && (nal_unit_type <= 23))
        {
            fuaStartedAndNotEnded = false;
            ret
                = dePacketizeSingleNALUnitPacket(
                    nal_unit_type,
                    in, inOffset, inBuffer.getLength(),
                    outBuffer);
        }
        else if (nal_unit_type == 28) // FU-A Fragmentation unit (FU)
        {
            ret = dePacketizeFUA(in, inOffset, inBuffer.getLength(), outBuffer);
            if (outBuffer.isDiscard())
                fuaStartedAndNotEnded = false;
        }
        else
        {
            logger.warn(
                    "Dropping NAL unit of unsupported type " + nal_unit_type);
            this.nal_unit_type = nal_unit_type;

            fuaStartedAndNotEnded = false;
            outBuffer.setDiscard(true);
            ret = BUFFER_PROCESSED_OK;
        }

        outBuffer.setSequenceNumber(sequenceNumber);

        /*
         * The RTP marker bit is set for the very last packet of the access unit
         * indicated by the RTP time stamp to allow an efficient playout buffer
         * handling. Consequently, we have to output it as well.
         */
        if ((inBuffer.getFlags() & Buffer.FLAG_RTP_MARKER) != 0)
            outBuffer.setFlags(outBuffer.getFlags() | Buffer.FLAG_RTP_MARKER);

        // Should we request a key frame.
        switch (this.nal_unit_type)
        {
        case 5 /* Coded slice of an IDR picture */:
            lastKeyFrameTime = System.currentTimeMillis();
            outBuffer.setFlags(outBuffer.getFlags() | Buffer.FLAG_KEY_FRAME);
            // Do fall through to prevent the request of a key frame.

        /*
         * While it seems natural to not request a key frame in the presence of
         * 5, 7 and 8 often seem to be followed by 5 so do not request a key
         * frame if either 7 or 8 is present.
         */
        case 7 /* Sequence parameter set */:
        case 8 /* Picture parameter set */:
            requestKeyFrame = false;
            break;
        default:
            break;
        }
        setRequestKeyFrame(requestKeyFrame);

        return ret;
    }

    /**
     * Requests a key frame from the remote peer associated with this
     * <tt>DePacketizer</tt> using the logic of <tt>DePacketizer</tt>.
     *
     * @param urgent <tt>true</tt> if the caller has determined that the need
     * for a key frame is urgent and should not obey all constraints with
     * respect to time between two subsequent requests for key frames
     * @return <tt>true</tt> if a key frame was indeed requested in response to
     * the call; otherwise, <tt>false</tt>
     */
    public synchronized boolean requestKeyFrame(boolean urgent)
    {
        lastKeyFrameTime = -1;
        setRequestKeyFrame(true);
        return true;
    }
    
    /**
     * Resets the states of this <tt>DePacketizer</tt> and a specific output
     * <tt>Buffer</tt> so that they are ready to have this <tt>DePacketizer</tt>
     * process input RTP payloads. If the specified output <tt>Buffer</tt>
     * contains an incomplete NAL unit, its forbidden_zero_bit will be turned on
     * and the NAL unit in question will be output by this
     * <tt>DePacketizer</tt>.
     *
     * @param outBuffer the output <tt>Buffer</tt> to be reset
     * @return the flags such as <tt>BUFFER_PROCESSED_OK</tt> and
     * <tt>OUTPUT_BUFFER_NOT_FILLED</tt> to be returned by
     * {@link #process(Buffer, Buffer)}
     */
    private int reset(Buffer outBuffer)
    {
        /*
         * We need the octet at the very least. Additionally, it does not make
         * sense to output a NAL unit with zero payload because such NAL units
         * are only given meaning for the purposes of the network and not the
         * H.264 decoder.
         */
        if (OUTPUT_INCOMPLETE_NAL_UNITS
                && fuaStartedAndNotEnded
                && (outBuffer.getLength() >= (NAL_PREFIX.length + 1 + 1)))
        {
            Object outData = outBuffer.getData();

            if (outData instanceof byte[])
            {
                byte[] out = (byte[]) outData;
                int octetIndex = outBuffer.getOffset() + NAL_PREFIX.length;

                out[octetIndex] |= 0x80; // Turn on the forbidden_zero_bit.
                fuaStartedAndNotEnded = false;
                return (BUFFER_PROCESSED_OK | INPUT_BUFFER_NOT_CONSUMED);
            }
        }

        fuaStartedAndNotEnded = false;
        outBuffer.setLength(0);
        return OUTPUT_BUFFER_NOT_FILLED;
    }

	/**
     * Extracts a fragment of a NAL unit from a specific FU-A RTP packet
     * payload.
     *
     * @param in the payload of the RTP packet from which a FU-A fragment of a
     * NAL unit is to be extracted
     * @param inOffset the offset in <tt>in</tt> at which the payload begins
     * @param inLength the length of the payload in <tt>in</tt> beginning at
     * <tt>inOffset</tt>
     * @param outBuffer the <tt>Buffer</tt> which is to receive the extracted
     * FU-A fragment of a NAL unit
     * @return the flags such as <tt>BUFFER_PROCESSED_OK</tt> and
     * <tt>OUTPUT_BUFFER_NOT_FILLED</tt> to be returned by
     * {@link #process(Buffer, Buffer)}
     */
    private int dePacketizeFUA(
            byte[] in, int inOffset, int inLength,
            Buffer outBuffer)
    {
        byte fu_indicator = in[inOffset];

        inOffset++;
        inLength--;

        byte fu_header = in[inOffset];

        inOffset++;
        inLength--;

        int nal_unit_type = fu_header & 0x1F;

        this.nal_unit_type = nal_unit_type;

        boolean start_bit = (fu_header & 0x80) != 0;
        boolean end_bit = (fu_header & 0x40) != 0;
        int outOffset = outBuffer.getOffset();
        int newOutLength = inLength;
        int octet;

        if (start_bit)
        {
            /*
             * The Start bit and End bit MUST NOT both be set in the same FU
             * header.
             */
            if (end_bit)
            {
                outBuffer.setDiscard(true);
                return BUFFER_PROCESSED_OK;
            }

            fuaStartedAndNotEnded = true;

            newOutLength += NAL_PREFIX.length + 1 /* octet */;
            octet
                = (fu_indicator & 0xE0) /* forbidden_zero_bit & NRI */
                    | nal_unit_type;
            
        }
        else if (!fuaStartedAndNotEnded)
        {
            outBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }
        else
        {
            int outLength = outBuffer.getLength();

            outOffset += outLength;
            newOutLength += outLength;
            octet = 0; // Ignored later on.
        }

        byte[] out
            = validateByteArraySize(
                    outBuffer,
                    outBuffer.getOffset() + newOutLength + outputPaddingSize,
                    true);

        if (start_bit)
        {
            // Copy in the NAL start sequence and the (reconstructed) octet.
            System.arraycopy(NAL_PREFIX, 0, out, outOffset, NAL_PREFIX.length);
            outOffset += NAL_PREFIX.length;

            out[outOffset] = (byte) (octet & 0xFF);
            outOffset++;
        }
        System.arraycopy(in, inOffset, out, outOffset, inLength);
        outOffset += inLength;

        padOutput(out, outOffset);

        outBuffer.setLength(newOutLength);

        if (end_bit)
        {
            fuaStartedAndNotEnded = false;
            return BUFFER_PROCESSED_OK;
        }
        else
            return OUTPUT_BUFFER_NOT_FILLED;
    }

    

    /**
     * Extract a single (complete) NAL unit from RTP payload.
     *
     * @param nal_unit_type unit type of NAL
     * @param in the payload of the RTP packet
     * @param inOffset the offset in <tt>in</tt> at which the payload begins
     * @param inLength the length of the payload in <tt>in</tt> beginning at
     * <tt>inOffset</tt>
     * @param outBuffer the <tt>Buffer</tt> which is to receive the extracted
     * NAL unit
     * @return the flags such as <tt>BUFFER_PROCESSED_OK</tt> and
     * <tt>OUTPUT_BUFFER_NOT_FILLED</tt> to be returned by
     * {@link #process(Buffer, Buffer)}
     */
    private int dePacketizeSingleNALUnitPacket(
            int nal_unit_type,
            byte[] in, int inOffset, int inLength,
            Buffer outBuffer)
    {
        this.nal_unit_type = nal_unit_type;

        int outOffset = outBuffer.getOffset();
        int newOutLength = NAL_PREFIX.length + inLength;
        byte[] out
            = validateByteArraySize(
                outBuffer,
                outOffset + newOutLength + outputPaddingSize,
                true);

        System.arraycopy(NAL_PREFIX, 0, out, outOffset, NAL_PREFIX.length);
        outOffset += NAL_PREFIX.length;

        System.arraycopy(in, inOffset, out, outOffset, inLength);
        outOffset += inLength;

        padOutput(out, outOffset);

        outBuffer.setLength(newOutLength);

        return BUFFER_PROCESSED_OK;
    }
    
    /**
     * Appends {@link #outputPaddingSize} number of bytes to <tt>out</tt>
     * beginning at index <tt>outOffset</tt>. The specified <tt>out</tt> is
     * expected to be large enough to accommodate the mentioned number of bytes.
     *
     * @param out the buffer in which <tt>outputPaddingSize</tt> number of bytes
     * are to be written
     * @param outOffset the index in <tt>outOffset</tt> at which the writing of
     * <tt>outputPaddingSize</tt> number of bytes is to begin
     */
    private void padOutput(byte[] out, int outOffset)
    {
        Arrays.fill(out, outOffset, outOffset + outputPaddingSize, (byte) 0);
    }
    
    

    /**
     * Sets the indicator which determines whether this <tt>DePacketizer</tt> is
     * to request a key frame from the remote peer associated with
     * {@link #keyFrameControl}.
     *
     * @param requestKeyFrame <tt>true</tt> if this <tt>DePacketizer</tt> is to
     * request a key frame from the remote peer associated with
     * {@link #keyFrameControl}
     */
    private synchronized void setRequestKeyFrame(boolean requestKeyFrame)
    {
        if (this.requestKeyFrame != requestKeyFrame)
        {
            this.requestKeyFrame = requestKeyFrame;

            if ((this.requestKeyFrame) && (requestKeyFrameThread == null))
            {
                requestKeyFrameThread
                    = new Thread()
                    {
                        @Override
                        public void run()
                        {
                            try
                            {
                                //runInRequestKeyFrameThread();
                            }
                            finally
                            {
                                synchronized (H264DePacketizer.this)
                                {
                                    if (requestKeyFrameThread
                                            == Thread.currentThread())
                                        requestKeyFrameThread = null;
                                }
                            }
                        }
                    };
                requestKeyFrameThread.start();
            }
            notifyAll();
        }
    }
    


}
