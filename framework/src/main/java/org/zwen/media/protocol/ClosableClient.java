package org.zwen.media.protocol;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

public abstract class ClosableClient implements Closeable {
	protected ClientBootstrap bootstrap;
	protected ChannelFuture future;
	
	
	protected void connect(SocketAddress address) throws IOException {
        // 初始化Bootstrap和NioClientSocketChannelFactory，这一步将启动nioWorker线程，并初始化NioClientSocketPipelineSink，并将Boss线程创建
        bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        // 用户自定义的pipeline工厂
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                return pipeline();
            }
        });

        // 异步创建连接
        if (null != address) {
        	future = bootstrap.connect(address);
        } else {
        	future = bootstrap.connect();
        }
        
        boolean connected = future.awaitUninterruptibly(10, TimeUnit.SECONDS);
        if (!connected) {
        	throw new IOException("Fail Connect " + address, future.getCause());
        }
	}
	
	abstract protected ChannelPipeline pipeline();
	
	@Override
	final public void close() throws IOException {
		try {
			onClose();
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		} finally {
			//等待连接关闭
			if (null != future) {
		        future.getChannel().getCloseFuture().awaitUninterruptibly();
			}

	        // 关闭资源，线程池等
			if (null != bootstrap) {
				bootstrap.releaseExternalResources();
			}
		}
	}
	protected void onClose() throws Exception{}
}
