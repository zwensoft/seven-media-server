package org.zwen.media;

import junit.framework.TestCase;

public class TestAVTimeUnit extends TestCase {
	public void testConvert() {
		AVTimeUnit mills = AVTimeUnit.MILLISECONDS;
		AVTimeUnit _90k = AVTimeUnit.MILLISECONDS_90;
		
		assertEquals(_90k.convert(12, mills), 90 * 12);
	}
}
