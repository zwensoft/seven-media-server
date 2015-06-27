package org.zwen.media.protocol.rtsp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class URLUtils {
	public static String getAbsoluteUrl(String base, String path) {
		if (null == path) {
			return base;
		}
		
		boolean isAbsolutePath = Pattern.matches("(([a-z]+)://(.*))", path);
		if (isAbsolutePath) {
			return path;
		} else {
			
			if (path.startsWith("/")) {
				Pattern pattern = Pattern.compile("([a-z]+://[^/]+)(/)?.*");
				Matcher matcher = pattern.matcher(base);
				if (!matcher.matches()) {
					throw new IllegalArgumentException(base + " NOT URL?");
				}
				
				return matcher.group(1) + path;
			} else {
				if (base.endsWith("/")) {
					return base + path;
				} else {
					return base + "/" + path;
				}
			}
		}		
	}
}
