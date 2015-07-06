package org.zwen.media.protocol.mts;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import javax.media.format.VideoFormat;

import mts.api.SITableFactory;
import mts.api.Section;
import mts.api.StreamType;
import mts.api.psi.PAT;
import mts.api.psi.PMT;

import org.zwen.media.AVPacket;
import org.zwen.media.AVStream;

import crc32.Crc32Mpeg2;

import flavor.BuffBitstream;
import flavor.IBitstream;

public class MTSPacketizer {
	private AVStream[] avs;

	// mpeg ts packetizer
	private int pmtId = 4095; /* 1024 */
	private int[] pids;
	private int pcr_pid;
	private int pidSeqNo = 256;
	private int continuity_counter = 1;
	private Crc32Mpeg2 crc32 = new Crc32Mpeg2();
	

	public void process(AVStream[] avs, List<ByteBuffer> out) {
		this.avs = avs;
		this.pids = new int[avs.length];

		ByteBuffer buf = null;
		int program_num = 0x1;
		int pmt_ver = 0x3;
		int pmt_pid = pmtId++/* 3EA */;

		// PAT
		PAT pat = SITableFactory.createPAT(0x00, 1);
		pat.addProgram(SITableFactory.createPATProgram(0x01, pmt_pid));
		Section[] sections = pat.toSection();
		for (int i = 0; i < sections.length; i++) {
			Section sec = sections[i];
			buf = ByteBuffer.allocate(188);
			packetSection(pmt_pid, 0x00, sec.getSectionBytes(), buf);
			buf.flip();
			out.add(buf);
		}
		
		// PMT
		PMT table;
		table = SITableFactory.createPMT(pmt_pid, pmt_ver, program_num, 0x0ABC);
		for (int i = 0; i < avs.length; i++) {
			AVStream stream = avs[i];
			StreamType type = StreamType.ISO_IEC_Reserved;
			do {
				if (null == stream.getFormat()) {
					break;
				}

				String encoding = stream.getFormat().getEncoding();
				if ("AVC".equalsIgnoreCase(encoding)) {
					type = StreamType.AVC;
					break;
				}

				if ("ADTS".equalsIgnoreCase(encoding)) {
					type = StreamType.ADTS;
					break;
				}
			} while (false);

			int pmt_stream_pid = pidSeqNo++;
			pids[i] = pmt_stream_pid;
			table.addStream(SITableFactory
					.createPMTStream(type, pmt_stream_pid));
			
			if (0 == i || stream.getFormat() instanceof VideoFormat) {
				pcr_pid = pids[i];
			}
		}
		

		table.setPCR_PID(pcr_pid);
		table.setVersionNumber(0);
		sections = table.toSection();
		for (int sn = 0; sn < sections.length; sn++) {
			buf = ByteBuffer.allocate(188);
			packetSection(pmt_pid, 0x02, sections[sn].getSectionBytes(), buf);
			buf.flip();
			out.add(buf);
		}
	}

	public void process(AVPacket pkt, List<ByteBuffer> out) {
		int pid = pids[pkt.getStreamIndex()];
		
		boolean isPCR = pcr_pid == pid;
		
		if (isPCR) {
			
		} else {
			
		}
	}

	public void close(List<ByteBuffer> out) {

	}

	private void packetSection(int pid, int tableId, byte[] section, ByteBuffer out) {
		// afLength = 188 - ts_header - table id  - payload - crc32_length
		int adaptation_field_length = (188 - 4 - 1 - 4 - section.length);

		int adaptation_field_control = 0;
		if (section.length > 0) {
			adaptation_field_control |= 0x10; // has data
		}
		if (adaptation_field_length > 0) {
			adaptation_field_control |= 0x20; // has payload
		}

		out.put((byte) 0x47);
		out.putShort((short) (0x4000 | pid));
		out.put((byte) (adaptation_field_control | nextCC()));
		if (adaptation_field_length > 0) {
			out.put((byte) adaptation_field_length);
			for (int i = 0; i < adaptation_field_length - 1; i++) {
				out.put((byte) 0xFF);
			}
		}

		// tableId, section + CRC 32
		out.put((byte)tableId);
		out.put(section);
		crc32.update(section);
		out.putInt((int)crc32.getValue());
	}

	private int nextCC() {
		int cc = continuity_counter;
		continuity_counter = 0xF & (continuity_counter + 1);

		return cc;
	}
}
