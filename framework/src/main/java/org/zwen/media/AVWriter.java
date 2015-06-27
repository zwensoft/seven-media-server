package org.zwen.media;

import java.io.Closeable;
import java.io.IOException;

public interface AVWriter extends Closeable {

	public void setStreams(AVStream[] avs);
	
	public void writeHead() throws IOException;
	
	public void write(AVStream av, AVPacket pkt) throws IOException;
	
	public void writeTail() throws IOException;
	
	@Override
	public void close() throws IOException;
}
