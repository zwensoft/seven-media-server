package org.zwen.media.protocol.mts;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zwen.media.AVDispatcher;
import org.zwen.media.AVPacket;
import org.zwen.media.AVStream;
import org.zwen.media.AVStreamListener;

import com.jidesoft.icons.IconSet.File;

import junit.framework.TestCase;

public class TestMTSReader extends TestCase implements AVStreamListener {
	private static final Logger logger = LoggerFactory
			.getLogger(TestMTSReader.class);
	private FileChannel ch;

	private AVDispatcher dispatcher = new AVDispatcher();

	@Override
	protected void setUp() throws Exception {
		dispatcher.addListener(this);
	}

	public void testRead() throws IOException {
		String tsFile = "20150525183227.ts";
		java.io.File ts = new java.io.File(tsFile);
		System.out.println(ts.getAbsolutePath());
		
		ch = new FileInputStream(ts).getChannel();
		MTSReader reader = new MTSReader(ch);

		while (reader.read(dispatcher) != -1) {

		}
		
		reader.close();
	}

	@Override
	protected void tearDown() throws Exception {
		if (null != ch) {
			ch.close();
		}
	}

	@Override
	public void onClosed() {
		logger.debug("closed");
	}

	@Override
	public void onPacket(AVStream stream, AVPacket packet) {
		logger.debug("{}", packet);
	}

	@Override
	public void onSetup(AVStream[] streams) {
		for (int i = 0; i < streams.length; i++) {
			logger.debug("{}", streams[i]);
		}
	}

}
