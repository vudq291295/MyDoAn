package com.dqv.mqtt;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import org.dna.mqtt.moquette.proto.messages.*;

import com.dqv.mqtt.parser.MQTTDecoder;
import com.dqv.mqtt.parser.MQTTEncoder;
import com.dqv.mqtt.prometheus.PromMetrics;

import java.util.Map;

import static org.dna.mqtt.moquette.proto.messages.AbstractMessage.*;

/**
 * Created by giovanni on 07/05/2014.
 * Base class for connection handling, 1 tcp connection corresponds to 1 instance of this class.
 */
public abstract class MQTTSocket implements MQTTPacketTokenizer.MqttTokenizerListener, Handler<Buffer> {
	
    private static Logger logger = LoggerFactory.getLogger(MQTTSocket.class);
    
    protected Vertx vertx;
    private MQTTDecoder decoder;
    private MQTTEncoder encoder;
    private MQTTPacketTokenizer tokenizer;
    protected MQTTSession session;
    private ConfigParser config;
    protected Map<String, MQTTSession> sessions;

    public MQTTSocket(Vertx vertx, ConfigParser config, Map<String, MQTTSession> sessions) {
        this.decoder = new MQTTDecoder();
        this.encoder = new MQTTEncoder();
        this.tokenizer = new MQTTPacketTokenizer();
        this.tokenizer.registerListener(this);
        this.vertx = vertx;
        this.config = config;
        this.sessions = sessions;
    }

    abstract protected void sendMessageToClient(Buffer bytes);
    protected void sendMessageToClient(Buffer bytes, WriteStream<Buffer> writer, ReadStream<Buffer> reader) {
        try {
            writer.write(bytes);
            if (writer.writeQueueFull()) {
                reader.pause();
                writer.drainHandler( done -> reader.resume() );
            }
        } catch(Throwable e) {
            logger.error(e.getMessage());
        }
    }
    abstract protected void closeConnection();


    public void shutdown() {
        if(tokenizer!=null) {
            tokenizer.removeAllListeners();
            tokenizer = null;
        }
        if(session!=null) {
            removeCurrentSessionFromMap();
            session.shutdown();
            session = null;
        }
        vertx = null;
    }

    @Override
    public void handle(Buffer buffer) {
        tokenizer.process(buffer.getBytes());
    }

    @Override
    public void onToken(byte[] token, boolean timeout) throws Exception {
        try {
            if(!timeout) {
                Buffer buffer = Buffer.buffer(token);
                AbstractMessage message = decoder.dec(buffer);
                onMessageFromClient(message);
            } else {
                logger.warn("Timeout occurred ...");
            }
        } catch (Throwable ex) {
            String clientInfo = getClientInfo();
            logger.error(clientInfo +", Bad error in processing the message", ex);
            closeConnection();
        }
    }

    @Override
    public void onError(Throwable e) {
        String clientInfo = getClientInfo();
        logger.error(clientInfo +", "+ e.getMessage(), e);
//        if(e instanceof CorruptedFrameException) {
            closeConnection();
//        }
    }

    private void onMessageFromClient(AbstractMessage msg) throws Exception {
        logger.info("<<< " + msg);
        switch (msg.getMessageType()) {
            case CONNECT:
                ConnectMessage connect = (ConnectMessage)msg;
                ConnAckMessage connAck = new ConnAckMessage();
                String connectedClientID = connect.getClientID();
                PromMetrics.mqtt_connect_total.inc();
                if(!connect.isCleanSession() && sessions.containsKey(connectedClientID)) {
                    session = sessions.get(connectedClientID);
                }
                if(session == null) {
                    session = new MQTTSession(vertx, config);
                    PromMetrics.mqtt_sessions_total.inc();
                    connAck.setSessionPresent(false);
                } else {
                    logger.warn("Session alredy allocated ...");
                    /*
                     The Server MUST process a second CONNECT Packet sent from a Client as a protocol violation and disconnect the Client
                      */
                    connAck.setSessionPresent(true);
//                    closeConnection();
//                    break;
                }


                session.setPublishMessageHandler(this::sendMessageToClient);
                session.setKeepaliveErrorHandler(clientID -> {
                    String cinfo = clientID;
                    if(session!=null) {
                        cinfo = session.getClientInfo();
                    }
                    logger.info("keep alive exausted! closing connection for client["+cinfo+"] ...");
                    closeConnection();
                });
                session.handleConnectMessage(connect, authenticated -> {
                    if (authenticated) {
                        connAck.setReturnCode(ConnAckMessage.CONNECTION_ACCEPTED);
                        sessions.put(session.getClientID(), session);
                        sendMessageToClient(connAck);
                        if(!session.isCleanSession()) {
                            session.sendAllMessagesFromQueue();
                        }
                    } else {
                        logger.error("Authentication failed! clientID= " + connect.getClientID() + " username=" + connect.getUsername());
//                        closeConnection();
                        connAck.setReturnCode(ConnAckMessage.BAD_USERNAME_OR_PASSWORD);
                        sendMessageToClient(connAck);
                    }
                });
                break;
            case SUBSCRIBE:
                session.resetKeepAliveTimer();
                logger.info("<<< msg.getRemainingLength: " + msg.getRemainingLength());

                SubscribeMessage subscribeMessage = (SubscribeMessage)msg;
//                PromMetrics.mqtt_subscribe_total.labels(session.getClientID()).inc();
                logger.info("<<< subscribeMessage.subscriptions().get(0).getTopicFilter(): " + subscribeMessage.subscriptions().get(0).getTopicFilter());

                session.handleSubscribeMessage(subscribeMessage, permitted -> {
	                SubAckMessage subAck = new SubAckMessage();
	                subAck.setMessageID(subscribeMessage.getMessageID());
	                int indx = 0;
	                for(SubscribeMessage.Couple c : subscribeMessage.subscriptions()) {
	                	if (permitted.getBoolean(indx++)) {
	                		QOSType qos = new QOSUtils().toQos(c.getQos());
	                		subAck.addType(qos);
                            PromMetrics.mqtt_subscribe_total.inc();
                            PromMetrics.mqtt_subscribe.labels(qos.name(), c.getTopicFilter()).inc();
	                	} else {
	                		subAck.addType(QOSType.FAILURE); 
	                	}
	                }
	                if(subscribeMessage.isRetainFlag()) {
	                    /*
	                    When a new subscription is established on a topic,
	                    the last retained message on that topic should be sent to the subscriber with the Retain flag set.
	                    If there is no retained message, nothing is sent
	                    */
	                }
	                sendMessageToClient(subAck);
                });
	            break;
            case UNSUBSCRIBE:
                session.resetKeepAliveTimer();

                UnsubscribeMessage unsubscribeMessage = (UnsubscribeMessage)msg;
                session.handleUnsubscribeMessage(unsubscribeMessage);
                UnsubAckMessage unsubAck = new UnsubAckMessage();
                unsubAck.setMessageID(unsubscribeMessage.getMessageID());
                sendMessageToClient(unsubAck);
                break;
            case PUBLISH:
                session.resetKeepAliveTimer();
                PublishMessage publish = (PublishMessage)msg;
                QOSType qos = publish.getQos();
                String topic = publish.getTopicName();
                PromMetrics.mqtt_publish_total.inc();
                PromMetrics.mqtt_publish.labels(qos.name(),topic).inc();
                switch (publish.getQos()) {
                    case RESERVED:
                        session.handlePublishMessage(publish, null);
                        break;
                    case MOST_ONE:
                        session.handlePublishMessage(publish, null);
                        break;
                    case LEAST_ONE:
//                        session.addMessageToQueue(publish);
                        session.handlePublishMessage(publish, permitted -> {
	                        PubAckMessage pubAck = new PubAckMessage();
	                        pubAck.setMessageID(publish.getMessageID());
	                        sendMessageToClient(pubAck);
                        });
                        break;
                    case EXACTLY_ONCE:
//                        session.addMessageToQueue(publish);
                        session.handlePublishMessage(publish, permitted -> {
	                        PubRecMessage pubRec = new PubRecMessage();
	                        pubRec.setMessageID(publish.getMessageID());
	                        sendMessageToClient(pubRec);
                        });
                        break;
                }
                break;
            case PUBREC:
                session.resetKeepAliveTimer();

                PubRecMessage pubRec = (PubRecMessage)msg;
                PubRelMessage prelResp = new PubRelMessage();
                prelResp.setMessageID(pubRec.getMessageID());
                prelResp.setQos(QOSType.LEAST_ONE);
                sendMessageToClient(prelResp);
                break;
            case PUBREL:
                session.resetKeepAliveTimer();
                PubRelMessage pubRel = (PubRelMessage)msg;
                PubCompMessage pubComp = new PubCompMessage();
                pubComp.setMessageID(pubRel.getMessageID());
                sendMessageToClient(pubComp);
                break;
            case PUBACK:
                session.getMessageFromQueue();
                session.resetKeepAliveTimer();
                // A PUBACK message is the response to a PUBLISH message with QoS level 1.
                // A PUBACK message is sent by a server in response to a PUBLISH message from a publishing client,
                // and by a subscriber in response to a PUBLISH message from the server.
                break;
            case PUBCOMP:
                session.getMessageFromQueue();
                session.resetKeepAliveTimer();
                break;
            case PINGREQ:
                session.resetKeepAliveTimer();
                PingRespMessage pingResp = new PingRespMessage();
                sendMessageToClient(pingResp);
                break;
            case DISCONNECT:
                PromMetrics.mqtt_disconnect_total.inc();
                session.resetKeepAliveTimer();
                DisconnectMessage disconnectMessage = (DisconnectMessage)msg;
                handleDisconnect(disconnectMessage);
                break;
            default:
                logger.warn("type of message not known: "+ msg.getClass().getSimpleName());
                break;
        }

        // TODO: forward mqtt message to backup server

    }


    public void sendMessageToClient(AbstractMessage message) {
        try {
            logger.debug(">>> " + message);
            Buffer b1 = encoder.enc(message);
            sendMessageToClient(b1);
        } catch(Throwable e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void handleDisconnect(DisconnectMessage disconnectMessage) {
        removeCurrentSessionFromMap();
        session.handleDisconnect(disconnectMessage);
        session = null;
    }

    private void removeCurrentSessionFromMap() {
        String cid = session.getClientID();
        if(sessions!=null && session.isCleanSession() && sessions.containsKey(cid)) {
            sessions.remove(cid);
        }
    }


    protected String getClientInfo() {
        String clientInfo = "Session n/a";
        if(session != null) {
            clientInfo = session.getClientInfo();
        }
        return clientInfo;
    }

    protected void handleWillMessage() {
//        logger.info("handle will message... ");
        if(session != null) {
//            logger.info("handle will message: session found!");
            session.handleWillMessage();
        }
//        logger.info("handle will message end.");
    }

}
