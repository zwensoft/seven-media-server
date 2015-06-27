package org.zwen.media;


public class AVTimeUnit {
	public static final AVTimeUnit MILLISECONDS = new AVTimeUnit(1, 1000);
	
	/** 1/90 毫秒 */
	public static final AVTimeUnit MILLISECONDS_90 = new AVTimeUnit(1, 90 * 1000)
	;
	final public int num;
	final public int base; 

	public AVTimeUnit(int num, int base) {
		this.num = num;
		this.base = base;
	}
	
	public long convert(long sourceDuration, AVTimeUnit sourceUnit) {
		if (sourceUnit == this) {
			return sourceDuration;
		}
		
		long dstDuration = 0;

		dstDuration = ((sourceDuration * sourceUnit.num * base) / (num * sourceUnit.base) );

		return dstDuration;
	}

	@Override
	public String toString() {
		return "TimeUnit " + num + "/" + base + "s";
	}

	public static AVTimeUnit valueOf(int base) {
		switch (base) {
		case 1000:
			return MILLISECONDS;
		case 90000:
			return MILLISECONDS_90;
		default:
			return new AVTimeUnit(1, base);
		}
	}
}
