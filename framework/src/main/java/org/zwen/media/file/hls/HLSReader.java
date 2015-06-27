package org.zwen.media.file.hls;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.channels.Channels;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zwen.media.AVDispatcher;
import org.zwen.media.SystemClock;
import org.zwen.media.Threads;
import org.zwen.media.URLUtils;
import org.zwen.media.file.mts.MTSReader;

public class HLSReader extends AVDispatcher implements Closeable {
	private final static Logger LOGGER = LoggerFactory.getLogger(HLSReader.class);
	
	private boolean isClosed;
	private HttpMethod get = new GetMethod();
	
	private SystemClock clock = new SystemClock();
	private HttpClient client;
	final private String url;
	final private int selectIndex;
	private String selectedM3U8;
	private String baseURL;

	public HLSReader(HttpClient client, String url) {
		this(url, 0);
		
		this.client = client;
		HttpClientParams params = new HttpClientParams();
		params.setSoTimeout(10 * 1000);
		client.setParams(params);
	}
	
	public HLSReader(String url, int streamIndex) {
		this.url = url;
		this.selectIndex = streamIndex;
	}

	public void connect() throws IOException {
		String url = this.url;
		String m3u8 = readM3U8(url);
		

		Matcher matcher = Pattern.compile("#EXT-X-STREAM-INF[^\n]+\n([^\n]+)").matcher(m3u8);
		for (int i = 0; i <= selectIndex && matcher.find(); i++) {
			String uri = matcher.group(1);
			url = URLUtils.getAbsoluteUrl(this.url, uri);
		}
		
		selectedM3U8 = readM3U8(url);
		baseURL = url;
	}

	private String readM3U8(String url) throws IOException, HttpException {
		
		int status = client.executeMethod(get);
		LOGGER.info("status = {}, {}", status, url);
		ensure200(status, url);

		String m3u8 = get.getResponseBodyAsString();
		if (!isM3U8(m3u8)) {
			throw new IllegalArgumentException("Not M3U8" + m3u8);
		}
		
		LOGGER.info("\n{}", m3u8);
		return m3u8;
	}

	public void start() {
		String url;
		Matcher matcher = Pattern.compile("(#EXT-X-DISCONTINUITY[^\n]+\n)?#EXTINF[^\n]+\n([^\n]+)").matcher(selectedM3U8);
		while (matcher.find()) {
			boolean isDiscontinuity = null != matcher.group(1);
			String uri = matcher.group(2);
			url = URLUtils.getAbsoluteUrl(this.baseURL, uri);
			LOGGER.info("{} {}", isDiscontinuity ? "discontinue":"", url);
			
			boolean success = false;
			for (int i = 0; i < 5; i++) {
				try {
					readMTS(url);
					success = true;
				} catch (Exception e) {
					LOGGER.warn("FAIL Download {} {} time", url, i + 1);
					LOGGER.info(e.getMessage(), e);
				}
			}

			if (!success) {
				LOGGER.error("Ignored {}", url);
			}
		}
	}
	
	private boolean readMTS(String url) throws HttpException, IOException {
		GetMethod get = new GetMethod(url);
		int status = client.executeMethod(get);
		LOGGER.info("status = {}, {}", status, url);
		ensure200(status, url);
		
		PipedOutputStream pipedOut = new PipedOutputStream();
		PipedInputStream pipedIn = new PipedInputStream(pipedOut);
		
		InputStream in = get.getResponseBodyAsStream();
		final MTSReader reader = new MTSReader(Channels.newChannel(pipedIn), clock);
		
		Future<Boolean> future = Threads.submit(new Callable<Boolean>() {
			
			@Override
			public Boolean call() throws Exception {
				while (-1 != reader.read(HLSReader.this)) {
				}
				;
				reader.flush(HLSReader.this);
				
				return null; 
			}
		});
		
		// wait
		try {
			Boolean rst = future.get();
			
			return null != rst ? rst : false;
		} catch (ExecutionException e) {
			LOGGER.warn("Fail Download " + url, e.getCause());
		} catch (Exception e) {
			LOGGER.warn("Fail Download " + url, e);
		} finally {
			IOUtils.copy(in, pipedOut);
		}
		return false;
	}
	
	
	/**
	 * make sure the response status code is 200
	 * @param status
	 * @param url
	 * @throws IOException
	 */
	private void ensure200(int status, String url) throws IOException {
		if (status != 200) {
			throw new IOException("Error Code: " + status + "," + url);
		}
	}
	
	private boolean isM3U8(String content) {
		return StringUtils.startsWith(content, "#EXTM3U");
	}
	
	@Override
	public void close() throws IOException {
		isClosed = true;

		try {
			get.abort();
		} finally {
			get.releaseConnection();
		}
		
	}
	
	

	
}
