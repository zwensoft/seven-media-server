package org.zwen.media.file.mts;

import org.zwen.media.AVStream;

public class MTSWriter {
	private AVStream[] avstreams;
	
	private int[] pids;
	
	public void setStrams(AVStream[] avstreams) {
		pids = new int[avstreams.length];
	}
}
