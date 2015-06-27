package org.zwen.media;

public interface AVStreamListener {
	public void onSetup(AVStream[] streams);
	
	public void onPacket(AVStream stream, AVPacket packet);
	
	public void onClosed();
}
