package org.zwen.media;

import java.util.concurrent.atomic.AtomicLong;

public class SystemClock {
	private AtomicLong timestamp = new AtomicLong(AVStream.UNKNOWN);
	
	public SystemClock() {
		timestamp = new AtomicLong(AVStream.UNKNOWN);
	}
	
	public SystemClock(long timestamp) {
		this.timestamp = new AtomicLong(timestamp);
	}
	
	
	public boolean isInitialed() {
		return timestamp.get() >= 0;
	}
	
	public long get() {
		return timestamp.get();
	}
	
	public void update(long execpted, long update) {
		timestamp.compareAndSet(execpted, update);
	}
}
