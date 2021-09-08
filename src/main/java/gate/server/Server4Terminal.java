package gate.server;

import java.util.concurrent.TimeUnit;

import gate.base.cache.ProtocalStrategyCache;
import gate.codec.Gate2ClientDecoderMulti;
import gate.codec.Gate2ClientEncoderMulti;
import gate.codec.other.DynamicGate2ClientDecoderMulti;
import gate.codec.other.LengthParser;
import gate.server.handler.SocketInHandler;
import gate.util.CommonUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * 网关获取终端报文
 * @Description: 
 * @author  yangcheng
 * @date:   2019年3月30日
 */
public class Server4Terminal {
	/**
	 * 规约编号作为规约服务以及规约策略的唯一标识
	 */
	private  String  pId;
	private  String  serverPort;
	private  EventLoopGroup  boss;
	private  EventLoopGroup work;
	
	public Server4Terminal (String pId,String serverPort){
		this.pId = pId;
		this.serverPort = serverPort;
		this.boss = new NioEventLoopGroup(1);
		this.work = new NioEventLoopGroup();
	}
	
	
	/**
	 * 通过引导配置参数--长度域固定
	 * @return
	 */
	public  ServerBootstrap config(int pId, boolean isBigEndian, int beginHexVal, int lengthFieldOffset, int lengthFieldLength,
			boolean isDataLenthIncludeLenthFieldLenth, int exceptDataLenth){
		 ServerBootstrap serverBootstrap = new ServerBootstrap();
		 serverBootstrap
		 .group(boss, work)
		 .channel(NioServerSocketChannel.class)
		 .option(ChannelOption.SO_KEEPALIVE, true)
		 .childHandler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				//心跳检测,超时时间300秒，指定时间中没有读写操作会触发IdleStateEvent事件
				ch.pipeline().addLast(new IdleStateHandler(0, 0, 300, TimeUnit.SECONDS));
				//自定义编解码器  需要在自定义的handler的前面即pipeline链的前端,不能放在自定义handler后面，否则不起作用
				ch.pipeline().addLast("decoder",new Gate2ClientDecoderMulti(pId, isBigEndian, beginHexVal,
						lengthFieldOffset, lengthFieldLength, isDataLenthIncludeLenthFieldLenth, exceptDataLenth));//698长度域表示不包含起始符和结束符长度:1, false, -1, 1, 2, true, 1
				ch.pipeline().addLast("encoder",new Gate2ClientEncoderMulti());
				ch.pipeline().addLast(new SocketInHandler());
			}
		});
		 
		return serverBootstrap;
	}
	/**
	 * 高级功能支持--上传动态代码
	 * @param pId
	 * @param isBigEndian
	 * @param beginHexVal
	 * @param lengthFieldOffset
	 * @param isDataLenthIncludeLenthFieldLenth
	 * @param exceptDataLenth
	 * @param lengthParser
	 * @return
	 */
	public  ServerBootstrap config2(int pId, boolean isBigEndian, int beginHexVal, int lengthFieldOffset,
			boolean isDataLenthIncludeLenthFieldLenth, int exceptDataLenth , LengthParser lengthParser){
		 ServerBootstrap serverBootstrap = new ServerBootstrap();
		 serverBootstrap
		 .group(boss, work)
		 .channel(NioServerSocketChannel.class)
		 .option(ChannelOption.SO_KEEPALIVE, true)
		 .childHandler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				//心跳检测,超时时间300秒，指定时间中没有读写操作会触发IdleStateEvent事件
				ch.pipeline().addLast(new IdleStateHandler(0, 0, 300, TimeUnit.SECONDS));
				//自定义编解码器  需要在自定义的handler的前面即pipeline链的前端,不能放在自定义handler后面，否则不起作用
				ch.pipeline().addLast("decoder",new DynamicGate2ClientDecoderMulti(pId, isBigEndian, beginHexVal, lengthFieldOffset,
						isDataLenthIncludeLenthFieldLenth, exceptDataLenth, 0, lengthParser));//698长度域表示不包含起始符和结束符长度:1, false, -1, 1, 2, true, 1
				ch.pipeline().addLast("encoder",new Gate2ClientEncoderMulti());
				ch.pipeline().addLast(new SocketInHandler());
			}
		});
		 
		return serverBootstrap;
	}
	
	
	/**
	 * 绑定服务到指定端口
	 * @param serverBootstrap
	 */
	public  void bindAddress(ServerBootstrap serverBootstrap){
		ChannelFuture channelFuture;
		try {
			ProtocalStrategyCache.protocalServerCache.put(pId, this);
			channelFuture = serverBootstrap.bind(Integer.parseInt(serverPort)).sync();
			System.out.println("网关服务端已启动！！");
			channelFuture.channel().closeFuture().sync();
			
		} catch (InterruptedException e) {
			
			e.printStackTrace();
		}finally{
			CommonUtil.closeEventLoop(boss,work);
		}
	}
	/**
	 * 关闭服务
	 */
	public void close(){
		CommonUtil.closeEventLoop(boss,work);
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//删除缓存种对应网关规约服务
		ProtocalStrategyCache.protocalServerCache.remove(pId);
	}
	
	
}
