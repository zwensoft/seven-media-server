package org.zwen.media.file;

import java.io.Closeable;
import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zwen.media.AVDispatcher;
import org.zwen.media.AVPacket;
import org.zwen.media.AVStream;
import org.zwen.media.AVStreamListener;
import org.zwen.media.AVWriter;

/**
 * receive {@link AVPacket}(s) from av {@link AVDispatcher}, and sink it to
 * {@link AVWriter}
 * 
 * @author chenxiuheng@gmail.com
 */
public class AVDataSink implements AVStreamListener, Closeable {
	private static Logger logger = LoggerFactory.getLogger(AVDataSink.class);
	
	private boolean hasWriteHeader;
	private boolean isClosed;
	final private AVWriter writer;

	public AVDataSink(AVWriter writer) {
		this.writer = writer;
	}

	@Override
	public void onClosed() {
		try {
			if (!isClosed) {
				writer.writeTail();
			}
		} catch (Exception e) {
			handleException(e);
		}
	}

	@Override
	public void onPacket(AVStream stream, AVPacket packet) {
		try {
			if (!isClosed) {
				writer.write(stream, packet);
			}
		} catch (IOException e) {
			handleException(e);
		}
	}

	@Override
	public void onSetup(AVStream[] streams) {
		if (!isClosed) {
			writer.setStreams(streams);

			writeHeaderIfNeed();
		}
	}

	private void writeHeaderIfNeed() {
		try {
			if (!hasWriteHeader) {
				hasWriteHeader = true;
				writer.writeHead();
			}
		} catch (Exception e) {
			handleException(e);
		}
	}

	public boolean isClosed() {
		return isClosed;
	}

	private void handleException(Exception e) {
		close();
		logger.warn("{}", e.getMessage(), e);
	}

	@Override
	public void close() {
		if (!isClosed) {
			isClosed = true;
			IOUtils.closeQuietly(writer);
		}
	}
}
