package com.dqv.mqtt.parser;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;
import org.dna.mqtt.moquette.proto.messages.AbstractMessage;
import org.dna.mqtt.moquette.proto.messages.UnsubscribeMessage;

import java.util.List;

/**
 *
 * @author andrea
 */
class UnsubscribeDecoder extends DemuxDecoder {

    @Override
    void decode(ByteBuf in, List<Object> out) throws Exception {
        //Common decoding part
        in.resetReaderIndex();
        UnsubscribeMessage message = new UnsubscribeMessage();
        if (!decodeCommonHeader(message, in)) {
            in.resetReaderIndex();
            return;
        }
        
        //check qos level
        if (message.getQos() != AbstractMessage.QOSType.LEAST_ONE) {
            throw new CorruptedFrameException("Found an Usubscribe message with qos other than LEAST_ONE, was: " + message.getQos());
        }
            
        int start = in.readerIndex();
        //read  messageIDs
        message.setMessageID(in.readUnsignedShort());
        int readed = in.readerIndex()- start;
        while (readed < message.getRemainingLength()) {
            message.addTopicFilter(Utils.decodeString(in));
            readed = in.readerIndex()- start;
        }
        if (message.topicFilters().isEmpty()) {
            throw new CorruptedFrameException("unsubscribe MUST have got at least 1 topic");
        }
        out.add(message);
    }
    
}
