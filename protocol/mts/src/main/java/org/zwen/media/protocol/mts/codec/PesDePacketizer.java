package org.zwen.media.protocol.mts.codec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.media.Buffer;

import org.jcodec.containers.mps.MPSDemuxer.PESPacket;
import org.zwen.media.AVPacket;
import org.zwen.media.AVStream;

public abstract class PesDePacketizer {
	private List<AVPacket> bufOut = new ArrayList<AVPacket>();

	public final void dePacket(AVStream av, PESPacket pes,
			Collection<AVPacket> out) {
		try {
			deProcess(av, pes, bufOut);

			for (int i = 0; i < bufOut.size(); i++) {
				AVPacket pkt = bufOut.get(i);
				AVPacket lastPkt = av.getLastPkt();
				if (null != lastPkt) {
					if (lastPkt.getDuration() == Buffer.TIME_UNKNOWN) {
						lastPkt.setDuration(pkt.getTimeStamp() - lastPkt.getTimeStamp());
					}
					out.add(lastPkt);
				}
				av.setLastPkt(pkt);
			}
		} finally {
			bufOut.clear();
		}
	}

	public abstract void deProcess(AVStream av, PESPacket pes,
			Collection<AVPacket> out);

	final public void flush(AVStream av, Collection<AVPacket> out) {
		
		
		try {
			doFlush(av, bufOut);

			for (int i = 0; i < bufOut.size(); i++) {
				AVPacket pkt = bufOut.get(i);
				AVPacket lastPkt = av.getLastPkt();
				if (null != lastPkt) {
					if (lastPkt.getDuration() == Buffer.TIME_UNKNOWN) {
						lastPkt.setDuration(pkt.getTimeStamp() - lastPkt.getTimeStamp());
					}
					out.add(lastPkt);
				}
				av.setLastPkt(pkt);
			}

			// last pkt
			AVPacket lastPkt = av.getLastPkt();
			if (null != lastPkt) {
				out.add(lastPkt);
				av.setLastPkt(null);
			}
		} finally {
			bufOut.clear();
		}
	}
	
	protected void doFlush(AVStream av, Collection<AVPacket> out) {
		
	}
}
