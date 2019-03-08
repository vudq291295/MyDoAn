package io.github.giovibal.mqtt.integration;

import io.github.giovibal.mqtt.test.Tester;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

/**
 * Created by giovanni on 14/06/2014.
 */
public class ProtocolTests extends BaseTest {

    @Test
    public void testPublishSubscribe() {
        try {
            String topicPrefix = "test/topic";
            int numClients = 10;
            int numTopics = 2;

            long t1,t2,t3;
            t1=System.currentTimeMillis();

            String serverURL = "tcp://localhost:1883";
            Tester c = new Tester(numClients, "Paho", serverURL);
            c.connect();
            for(int i=0; i<numTopics; i++) {
                String topic = topicPrefix + "/" + i;
                c.subscribe(topic);
            }
            for(int i=0; i<numTopics; i++) {
                String topic = topicPrefix + "/" + i;
                c.publish(topic);
            }
            for(int i=0; i<numTopics; i++) {
                String topic = topicPrefix + "/" + i;
                c.unsubcribe(topic);
            }
            c.disconnect();

            c.stats();

            t2=System.currentTimeMillis();
            t3=t2-t1;
            System.out.println("Time elapsed: "+ t3 +" millis.");


            Map<String, Integer> report = c.getMessaggiArrivatiPerClient();
            Set<String> keys = report.keySet();
            for(String clientID : keys) {
                int count = report.get(clientID);
//                assertEquals(120, count);
            }


//            testComplete();
        }
        catch(Throwable e) {
//            fail(e.getMessage());
        }
    }
}
