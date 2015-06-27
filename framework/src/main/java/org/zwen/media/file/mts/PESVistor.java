package org.zwen.media.file.mts;

import java.util.Collection;

import org.jcodec.containers.mps.MPSDemuxer.PESPacket;
import org.zwen.media.AVPacket;
import org.zwen.media.AVStream;

public interface PESVistor {
	void visit(AVStream av, PESPacket pes, Collection<AVPacket> out);
	
	void flush(AVStream av, Collection<AVPacket> out);
}
