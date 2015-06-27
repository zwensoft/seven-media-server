package com.flazr.rtmp;

import java.net.SocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flazr.io.flv.FlvAtom;
import com.flazr.rtmp.message.Aggregate;

/**
 * 把 Aggregate 类型的 RTMP 包，拆解成独立的数据包
 * @author chenxh
 */
public class RtmpAggregator extends SimpleChannelUpstreamHandler {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(RtmpAggregator.class);

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object m = e.getMessage();
        if (!(m instanceof Aggregate)) {
            ctx.sendUpstream(e);
            return;
        }

        Aggregate aggregate = (Aggregate) m;
        SocketAddress remoteAddress = e.getRemoteAddress();
        final ChannelBuffer in = aggregate.encode();

        int atomIndex = 0;
        boolean isStart = true;
        long rtmpStart = aggregate.getHeader().getTime();
        long firstPartTime = rtmpStart;
        while (in.readable()) {
            final FlvAtom flvAtom = new FlvAtom(in);

            if (isStart) {
                firstPartTime = flvAtom.getHeader().getTime();
                isStart = false;
            }

            // 求相对时间
            flvAtom.getHeader().setTime((int) (flvAtom.getHeader().getTime() - firstPartTime + rtmpStart));
            isStart = false;

            Channels.fireMessageReceived(ctx, flvAtom, remoteAddress);
            logger.debug("aggregate[{}]: {}", atomIndex, flvAtom.getHeader());
            atomIndex ++;
            // logger.debug("aggregate atom: {}", flvAtom);
        }
    }
}
