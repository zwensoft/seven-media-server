package org.zwen.media.protocol.hls;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import junit.framework.TestCase;

import org.zwen.media.AVWriter;
import org.zwen.media.file.AVDataSink;
import org.zwen.media.file.flv.FlvWriter;

public class TestHLSClient extends TestCase {
	FileChannel ch;

	@Override
	protected void setUp() throws Exception {
		File file = new File("hls.flv");
		ch = new FileOutputStream(file).getChannel();
	}
	
	public void test0F() {
		System.out.println((int)0xFFFFFFFF);
	}

	public void testConnect() throws IOException {
		String url = "http://newmedia.chinacourt.org/vod/play/2015/04/20/15/fe28e38e396e2ab9877c68ed51bf3d91/playlist.m3u8";

		// ts 文件的临时存储目录
		File temp = new File("temp");
		temp.mkdirs();
		HLSRecorder.record(url, ch, new File("temp"));
	}

	public void testNetVod() throws IOException {
		String url = "http://172.16.160.143/edge/netvod/201505/25/TMNBAB/193556_194/EDS600201403250078_B1_H_0.vod.m3u8";

		// ts 文件的临时存储目录
		File temp = new File("temp");
		temp.mkdirs();
		HLSRecorder.record(url, ch, new File("temp"));
	}
}
