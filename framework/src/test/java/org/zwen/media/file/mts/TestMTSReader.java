package org.zwen.media.file.mts;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.apache.commons.io.IOUtils;
import org.zwen.media.AVDispatcher;
import org.zwen.media.AVPacket;
import org.zwen.media.AVStream;
import org.zwen.media.AVStreamListener;
import org.zwen.media.AVWriter;
import org.zwen.media.SystemClock;
import org.zwen.media.file.flv.FlvWriter;

public class TestMTSReader extends TestCase {
	public void testRead() throws IOException {
		File file = new File("1015241990.ts");
		System.out.println(file.exists());

		ReadableByteChannel ch = new FileInputStream(file).getChannel();

		MTSReader client = new MTSReader(ch, new SystemClock());

		File snk = new File("ts2flv.flv");
		FileChannel c = new FileOutputStream(snk).getChannel();
		final AVWriter writer = new FlvWriter(c);

		AVDispatcher dispatcher = new AVDispatcher();
		dispatcher.addListener(new AVStreamListener() {

			@Override
			public void onSetup(AVStream[] streams) {
				writer.setStreams(streams);
				try {
					writer.writeHead();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			@Override
			public void onPacket(AVStream stream, AVPacket packet) {
				try {
					System.out.println(packet);
					writer.write(stream, packet);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			@Override
			public void onClosed() {
				try {
					writer.writeTail();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					IOUtils.closeQuietly(writer);
				}

			}
		});

		while (-1 != client.read(dispatcher)) {
		}
	}
}
