package org.novaos.mobile;

import java.util.Map;

/**
 * Test partner for Nova Family (plays the other phone, same encryption as the app):
 *
 *   send   RELAY CODE DEVICE JSON      seal and publish one message (from/t are added)
 *   listen RELAY CODE [SINCE]          print all messages of the family (decrypted)
 *   policy RELAY CODE DEVICE CHILD PIN JSON-POLICY   send rules to a child (with the PIN's hash)
 *
 * Run by tests/family/run.sh.
 */
public final class Peer {
    public static void main(String[] a) throws Exception {
        String relay = a[1], code = FamilyCrypto.normalize(a[2]);
        String topic = FamilyCrypto.topic(code);
        byte[] key = FamilyCrypto.key(code);
        switch (a[0]) {
            case "send": {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) MiniJson.parse(a[4]);
                m.put("from", a[3]);
                m.put("t", System.currentTimeMillis());
                FamilyRelay.publish(relay, topic, FamilyCrypto.seal(key, topic, MiniJson.write(m)));
                System.out.println("sent " + m.get("type"));
                break;
            }
            case "listen": {
                for (FamilyRelay.Message msg : FamilyRelay.poll(relay, topic, a.length > 3 ? a[3] : "all")) {
                    String plain = FamilyCrypto.open(key, topic, msg.text);
                    System.out.println(msg.id + " " + (plain == null ? "(not readable)" : plain));
                }
                break;
            }
            case "policy": {
                FamilyPolicy p = FamilyPolicy.fromJson(MiniJson.parse(a[6]));
                p.version = System.currentTimeMillis();
                p.pinSalt = "peersalt";
                p.pinHash = FamilyCrypto.pinHash(a[5], p.pinSalt);
                Map<String, Object> m = MiniJson.obj("type", "policy", "to", a[4], "policy", p.toJson(), "from", a[3], "t", System.currentTimeMillis());
                FamilyRelay.publish(relay, topic, FamilyCrypto.seal(key, topic, MiniJson.write(m)));
                System.out.println("policy sent");
                break;
            }
            default:
                System.out.println("unknown command");
        }
    }
}
