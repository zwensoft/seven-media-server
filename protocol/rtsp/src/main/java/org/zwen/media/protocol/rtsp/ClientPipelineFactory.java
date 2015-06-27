package org.zwen.media.protocol.rtsp;


import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.rtsp.RtspRequestEncoder;
import org.jboss.netty.handler.codec.rtsp.RtspResponseDecoder;


public class ClientPipelineFactory implements ChannelPipelineFactory {
	private RtspConnector client;
	
	
	public ClientPipelineFactory(org.zwen.media.protocol.rtsp.RtspConnector rtspConnector) {
		super();
		this.client = rtspConnector;
	}


	public ChannelPipeline getPipeline() throws Exception {
		final ChannelPipeline pipeline = Channels.pipeline();
		

        pipeline.addLast("encoder", new RtspRequestEncoder());
        pipeline.addLast("decoder", new RtspResponseDecoder());
        pipeline.addLast("aggregator", new HttpChunkAggregator(9 * 1024));
        
        pipeline.addLast("handler", new ClientHandler(client));
        return pipeline;
	}

}
