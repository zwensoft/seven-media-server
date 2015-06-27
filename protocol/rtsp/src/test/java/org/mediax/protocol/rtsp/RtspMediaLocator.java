package org.mediax.protocol.rtsp;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.media.MediaLocator;

public class RtspMediaLocator extends MediaLocator {

	private String url;
	private String host;
	private int port = 554;
	private String file;

	private String userAndPassword;
	
	public RtspMediaLocator(String url) {
		super(url);
		
		if (null == url || !url.startsWith("rtsp://")) {
			throw new IllegalArgumentException(url + " NOT rtsp url!");
		}

		String urlBase;
		int portEnd = url.indexOf("/", 7);
		if (portEnd < 0) {
			urlBase = url.substring(7);
		} else {
			urlBase = url.substring(7, portEnd);
		}

		Matcher matcher = Pattern.compile(
				"^(([^@]+)@)?(([^:]+)(:([\\d]+))?)(/(.*))?$").matcher(urlBase);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Unknown How to use " + url);
		}

		String userAndPass = matcher.group(2);
		String host = matcher.group(4);
		String port = matcher.group(6);
		String file = matcher.group(7);

		this.userAndPassword = userAndPass;
		this.host = host;
		this.file = null != file ? file : "";
		if (null != port && port.length() != 0) {
			try {
				this.port = Integer.parseInt(port);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("error port " + url);
			}
		}
	}

	@Override
	public String getRemainder() {
		return String.format("//%s:%s/%s", getHost(), getPort(), getFile());
	}
	
	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getFile() {
		return file;
	}

	public String getUserAndPassword() {
		return userAndPassword;
	}

	public List<String[]> getAuths() {
		List<String[]> auths = new ArrayList<String[]>();

		if (null != userAndPassword && userAndPassword.length() > 0) {
			int fromIndex = 0;
			int splitIndex = 0;
			while ((splitIndex = userAndPassword.indexOf(':', fromIndex)) > 0) {
				String name = userAndPassword.substring(0, splitIndex);
				if (splitIndex + 1 < userAndPassword.length()) {
					String passwd = userAndPassword.substring(splitIndex + 1);
					auths.add(new String[] { name, passwd });
				} else {
					auths.add(new String[] { name, "" });
				}
				
				fromIndex = splitIndex + 1;
			}
		}

		return auths;
	}
}
