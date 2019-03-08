package com.dqv.mqtt.parser;

import io.netty.buffer.ByteBuf;
import org.dna.mqtt.moquette.proto.messages.AbstractMessage;
import org.dna.mqtt.moquette.proto.messages.PingReqMessage;

/**
 *
 * @author andrea
 */
class PingReqEncoder  extends DemuxEncoder<PingReqMessage> {

    @Override
    protected void encode(PingReqMessage msg, ByteBuf out) {
//        out.writeByte(AbstractMessage.PINGREQ << 4).writeByte(0);
        byte flags = Utils.encodeFlags(msg);
        out.writeByte(AbstractMessage.PINGREQ << 4 | flags).writeByte(0);

    }
}
