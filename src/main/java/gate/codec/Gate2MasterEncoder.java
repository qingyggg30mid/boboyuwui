package gate.codec;


import java.net.Inet4Address;

import gate.base.chachequeue.CacheQueue;
import gate.base.constant.ConstantValue;
import gate.base.domain.ChannelData;
import gate.base.domain.GateHeader;
import gate.base.domain.SocketData;
import gate.util.CommonUtil;
import gate.util.StringUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
/**
 * 编码器 将对象 编码成字节数组  -->目的地是前置服务
 * 
 * 自定义编解码器时，当参数组装完成之后先存放到自定义的ByteBuf中，封装好之后，
 * 再统一通过参数中的ByteBuf将数据发送，否则当，前置不是通过netty写的时候，
 * 会导致每次前置收到的报文都是不完整的！
 * 
 * @author BriansPC
 */
public class Gate2MasterEncoder extends MessageToByteEncoder<ChannelData>{

	/**
	 * 当前ChannelHandlerContext  是与前置相连的channel的上下文
	 */
	@Override
	protected void encode(ChannelHandlerContext ctx, ChannelData msg, ByteBuf out) throws Exception {
		
		String rmote = ctx.channel().remoteAddress().toString();
//		System.out.println("remoteMasterIP = "+rmote);
		/**
		 * 组网关报头
		 */
		String ipAddress = msg.getIpAddress().replaceAll("\\/", "");
		System.out.println("Terminal ipAddress===="+ipAddress);
		int count = CacheQueue.ipCountRelationCache.get(ipAddress);
		
		SocketData data = msg.getSocketData();
		
		//计算整个报文（包含68和16的长度）
		int len = (data.getLenArea()[0] & 0xFF) + ((data.getLenArea()[1] & 0xFF) << 8 );
		GateHeader headBuf= new GateHeader(); 
		headBuf.writeInt8(Integer.valueOf(ConstantValue.GATE_HEAD_DATA).byteValue());
		headBuf.writeInt16(Integer.valueOf(len + 2 ));//整个长度
		headBuf.writeInt8(Integer.valueOf(1).byteValue());//type
		headBuf.writeInt8(Integer.valueOf(0).byteValue());//protocolType
		headBuf.writeInt8((byte) CommonUtil.gateNum);//网关编号
		for(int i = 0; i < 3; i++) {  //12个字节的00
			headBuf.writeInt32(0);
		}
		
		byte[] bs = Inet4Address.getByName(ipAddress.split(":")[0]).getAddress();//127.0.0.1 -->  [127, 0, 0, 1]
		headBuf.writeInt8(bs[0]);
		headBuf.writeInt8(bs[1]);
		headBuf.writeInt8(bs[2]);
		headBuf.writeInt8(bs[3]);
		headBuf.writeInt16(Integer.parseInt(ipAddress.split(":")[1]));//port  两个字节表示端口号
		headBuf.writeInt32(count);//count  4个字节的count

		ByteBuf outData = Unpooled.directBuffer();
		outData.writeBytes(headBuf.getDataBuffer());
		//真实报文
		outData.writeByte(data.getHeader());
		outData.writeBytes(data.getLenArea());
		outData.writeBytes(data.getContent());
		outData.writeByte(data.getEnd());
		out.writeBytes(outData);
		byte[] car = new byte[outData.readableBytes()];
		for(int i = 0;i<outData.readableBytes() ; i++){
			car[i] = outData.getByte(i);
		}
		System.out.println("GATE UP = "+StringUtils.encodeHex(car)+";count="+CommonUtil.recieveCount.addAndGet(1));
	}


}
