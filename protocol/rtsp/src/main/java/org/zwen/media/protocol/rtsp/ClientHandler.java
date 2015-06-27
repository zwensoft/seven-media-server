package org.zwen.media.protocol.rtsp;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author res
 */
public class ClientHandler extends SimpleChannelUpstreamHandler {
	private static Logger logger = LoggerFactory.getLogger(ClientHandler.class);
	
	private RtspConnector client;

	public ClientHandler(org.zwen.media.protocol.rtsp.RtspConnector client2) {
		this.client = client2;
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		if (e.getMessage() instanceof HttpResponse) {
			client.onResponse((HttpResponse) e.getMessage());
		} else {
			super.messageReceived(ctx, e);
		}
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		Throwable cause = e.getCause();
		if (cause instanceof java.net.SocketTimeoutException) {
			logger.warn("socket timeout {} --> {}", ctx.getChannel().getLocalAddress(), ctx.getChannel().getRemoteAddress());
			
		}
		super.exceptionCaught(ctx, e);
	}
}
