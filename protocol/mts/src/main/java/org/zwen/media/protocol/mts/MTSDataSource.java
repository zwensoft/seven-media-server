package org.zwen.media.protocol.mts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zwen.media.AVDispatcher;
import org.zwen.media.Threads;
import org.zwen.media.file.mts.MTSReader;

public class MTSDataSource extends AVDispatcher {
	private static final Logger logger = LoggerFactory.getLogger(MTSDataSource.class);
	
	private String url;
	private InputStream src;
	
	private boolean isDisconnected = false;
	public MTSDataSource(String url) {
		this.url = url;
	}

	public void connect() throws IOException {
		if (null == src && null != url) {
			src = open(url);
		}
		
		if (null == src) {
			throw new IOException("Fail Read " + url);
		}
	}

	public static InputStream open(String url) {
		return null;
	}

	public void start() {
		if (null == src) {
			throw new IllegalStateException("InputStream is closed");
		}
		
		final AVDispatcher dispatcher = this;
		final MTSReader reader = new MTSReader(Channels.newChannel(src));
		Threads.submit(new Runnable() {
			
			@Override
			public void run() {
				try {
					while (!isDisconnected) {
						reader.read(dispatcher);
					}
				} catch (Exception e) {
					if (!isDisconnected) {
						disconnect();
						logger.error(e.getMessage(), e);
					}
				}
			}
		});
	}

	public void disconnect() {
		if (isDisconnected) {
			return;
		}
		
		isDisconnected = true;
		fireClosed();
		if (null != src) {
			IOUtils.closeQuietly(src);
			src = null;
		}
	}
}
