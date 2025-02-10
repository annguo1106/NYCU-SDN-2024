/*
 * Copyright 2024-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nycu.winlab.proxyarp;

import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;


import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;

import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.DefaultOutboundPacket;


import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;

import org.onosproject.net.PortNumber;


import org.onosproject.net.flow.FlowRuleService;

/* self import: */
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.ARP;

import java.nio.ByteBuffer;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    private ProxyArpProcessor processor = new ProxyArpProcessor();
    private ApplicationId appId;
    private Map<Ip4Address, MacAddress> arpTable = new HashMap<>();

    @Activate
    protected void activate() {
        // register app
        if (coreService == null) {
            log.error("CoreService not injected");
            return;
        }
        appId = coreService.registerApplication("nycu.winlab.ProxyArp");

        // add a packet procossor to packeyService
        packetService.addProcessor(processor, PacketProcessor.director(2));

        // install a flowrule for packet-in
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        // remove flowrule installed by your app
        flowRuleService.removeFlowRulesById(appId);

        // remove your packet processor
        packetService.removeProcessor(processor);
        processor = null;

        // remove flowrule you installed for packet-in
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

        log.info("Stopped");
    }
private class ProxyArpProcessor implements PacketProcessor {
    @Override
    public void process(PacketContext context) {
        // Stop processing if the packet has been handled, since we
        // can't do any more to it.
        if (context.isHandled()) {
            return;
        }
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        if (ethPkt == null) {
            return;
        }

        MacAddress srcMac = ethPkt.getSourceMAC();
        // MacAddress dstMac = ethPkt.getDestinationMac();
        ARP arpPacket = (ARP) ethPkt.getPayload();
        Ip4Address srcIp4 = Ip4Address.valueOf(arpPacket.getSenderProtocolAddress());
        Ip4Address destIp4 = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());
        short arpOpCode = arpPacket.getOpCode();

        if (arpOpCode == ARP.OP_REQUEST) {
            if (arpTable.get(srcIp4) == null) {
                arpTable.put(srcIp4, srcMac);
            }
            if (arpTable.get(destIp4) == null) {
                log.info("TABLE MISS. Send request to edge ports");
                flood(context);
            } else if (arpTable.get(destIp4) != null) {
                MacAddress dstMac = arpTable.get(destIp4);
                // TODO: generate ARP packet out to the sender
                log.info("TABLE HIT. Requested MAC = {}", dstMac);
                replyArp(context, srcMac, dstMac, destIp4, arpPacket);
            }
        } else if (arpOpCode == ARP.OP_REPLY) {
            log.info("RECV REPLY. Requested MAC = {}", srcMac);
            arpTable.put(srcIp4, srcMac);
        }
    }
}
private void flood(PacketContext context) {
    packetOut(context, PortNumber.FLOOD);
}

private void packetOut(PacketContext context, PortNumber portNumber) {
    context.treatmentBuilder().setOutput(portNumber);
    context.send();
}

private void replyArp(PacketContext context, MacAddress srcMac, MacAddress destMac, Ip4Address destIp4, ARP arpPacket) {
    ARP reply = (ARP) new ARP()
        .setHardwareType(ARP.HW_TYPE_ETHERNET)
        .setProtocolType(ARP.PROTO_TYPE_IP)
        .setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH)
        .setProtocolAddressLength((byte) Ip4Address.BYTE_LENGTH)
        .setOpCode(ARP.OP_REPLY)
        .setSenderHardwareAddress(destMac.toBytes())
        .setSenderProtocolAddress(destIp4.toInt())
        .setTargetHardwareAddress(arpPacket.getSenderHardwareAddress())
        .setTargetProtocolAddress(arpPacket.getSenderProtocolAddress());
    // packet ARP into ethernet
    Ethernet eth = new Ethernet();
    eth.setSourceMACAddress(destMac);
    eth.setDestinationMACAddress(srcMac);
    eth.setEtherType(Ethernet.TYPE_ARP);
    eth.setPayload(reply);

    packetService.emit(new DefaultOutboundPacket(
        context.inPacket().receivedFrom().deviceId(),
        DefaultTrafficTreatment.builder().setOutput(context.inPacket().receivedFrom().port()).build(),
        ByteBuffer.wrap(eth.serialize())
    ));
}

}
