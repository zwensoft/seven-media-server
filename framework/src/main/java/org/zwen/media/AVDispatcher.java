package org.zwen.media;

import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AVDispatcher {
	private Logger logger = LoggerFactory.getLogger(getClass());

	private CopyOnWriteArrayList<AVStreamListener> listeners = new CopyOnWriteArrayList<AVStreamListener>();

	public void fireSetup(AVStream[] streams) {
		for (int i = 0; i < streams.length; i++) {
			logger.info("{}", streams[i]);
		}

		for (AVStreamListener lis : listeners) {
			try {
				lis.onSetup(streams);
			} catch (Exception e) {
				logger.warn(e.getMessage(), e);
			}
		}
	}

	public void firePacket(AVStream stream, AVPacket pkt) {
		logger.info("dispatch {}", pkt);
		
		for (AVStreamListener lis : listeners) {
			try {
				lis.onPacket(stream, pkt);
			} catch (Exception e) {
				logger.warn(e.getMessage(), e);
			}
		}
	}
	
	public void fireClosed() {
		logger.info("dispatch close");
		
		for (AVStreamListener lis : listeners) {
			try {
				lis.onClosed();
			} catch (Exception e) {
				logger.warn(e.getMessage(), e);
			}
		}
	}

	public void addListener(AVStreamListener listener) {
		listeners.add(listener);
	}

	public void remove(AVStreamListener listener) {
		listeners.remove(listener);
	}
}
