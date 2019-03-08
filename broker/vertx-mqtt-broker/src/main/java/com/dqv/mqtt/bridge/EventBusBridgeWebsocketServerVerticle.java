package com.dqv.mqtt.bridge;

import com.dqv.mqtt.MQTTSession;
import com.dqv.mqtt.security.CertInfo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.parsetools.RecordParser;

/**
 * Created by Giovanni Baleani on 15/07/2015.
 */
public class EventBusBridgeWebsocketServerVerticle extends AbstractVerticle {

    private static Logger logger = LoggerFactory.getLogger(EventBusBridgeWebsocketServerVerticle.class);

    private String address;
    private HttpServer netServer;
    private int localBridgePort;
    private int idleTimeout;
    private String ssl_cert_key;
    private String ssl_cert;
    private String ssl_trust;

    @Override
    public void start() throws Exception {
        address = MQTTSession.ADDRESS;

        JsonObject conf = config();

        localBridgePort = conf.getInteger("local_bridge_port", 7007);
        idleTimeout = conf.getInteger("socket_idle_timeout", 120);
        ssl_cert_key = conf.getString("ssl_cert_key");
        ssl_cert = conf.getString("ssl_cert");
        ssl_trust = conf.getString("ssl_trust");


        // [WebSocket -> BUS] listen WebSocket publish to BUS
        HttpServerOptions opt = new HttpServerOptions()
                .setTcpKeepAlive(true)
                .setIdleTimeout(idleTimeout)
                .setPort(localBridgePort)
        ;

        if(ssl_cert_key != null && ssl_cert != null && ssl_trust != null) {
            opt.setSsl(true).setClientAuth(ClientAuth.REQUIRED)
                .setPemKeyCertOptions(new PemKeyCertOptions()
                    .setKeyPath(ssl_cert_key)
                    .setCertPath(ssl_cert)
                )
                .setPemTrustOptions(new PemTrustOptions()
                    .addCertPath(ssl_trust)
                )
            ;
        }

        netServer = vertx.createHttpServer(opt);
        netServer.requestHandler(httpServerRequest -> httpServerRequest.response().end() );
        netServer.websocketHandler(sock -> {
            final EventBusWebsocketBridge ebnb = new EventBusWebsocketBridge(sock, vertx.eventBus(), address);
            sock.closeHandler(aVoid -> {
                logger.info("Bridge Server - closed connection from client ip: " + sock.remoteAddress());
                ebnb.stop();
            });
            sock.exceptionHandler(throwable -> {
                logger.error("Bridge Server - Exception: " + throwable.getMessage(), throwable);
                ebnb.stop();
            });

            logger.info("Bridge Server - new connection from client ip: " + sock.remoteAddress());

            RecordParser parser = ebnb.initialHandhakeProtocolParser();
            sock.handler(parser::handle);

        }).listen();
    }

    @Override
    public void stop() throws Exception {
        netServer.close();
    }

}
