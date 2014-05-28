package loggregator;

import com.sun.org.apache.xpath.internal.SourceTree;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.net.URI;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class LoggregatorClient {

	public static void main(String[] args) throws Exception {
		final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();

		final Bootstrap bootstrap = new Bootstrap()
				.group(eventLoopGroup)
				.channel(NioSocketChannel.class)
				.handler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel socketChannel) throws Exception {
						final ChannelPipeline pipeline = socketChannel.pipeline();
						final SSLEngine engine = SSLContext.getDefault().createSSLEngine();
						engine.setUseClientMode(true);

						pipeline
								.addFirst("ssl", new SslHandler(engine))
								.addLast("http-codec", new HttpClientCodec())
								.addLast("aggregator", new HttpObjectAggregator(8192))
								.addLast("ws-handler", new ChannelInboundHandlerAdapter() {
									@Override
									public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
										if (msg instanceof BinaryWebSocketFrame) {
											final BinaryWebSocketFrame frame = (BinaryWebSocketFrame) msg;
											final ByteBufInputStream in = new ByteBufInputStream(frame.content());
											final Messages.LogMessage logMessage = Messages.LogMessage.parseFrom(in);
											System.out.println(logMessage);
										} else {
											System.out.println("Received unexpected object: " + msg);
										}
									}

									@Override
									public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
										cause.printStackTrace();
									}
								});
					}
				});

		bootstrap.connect("loggregator.cf2-dev.lds.org", 4443).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()) {
					System.out.println("Connected to loggregator!");
				} else {
					future.cause().printStackTrace();
				}
			}
		}).sync();
	}

}
