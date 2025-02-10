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
package nycu.winlab.groupmeter;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// intent
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;

// group service
import org.onosproject.net.group.GroupService;
import org.onosproject.net.group.DefaultGroupBucket;
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.DefaultGroupDescription;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.core.GroupId;
// import java.lang.Object;

// import meter
import org.onosproject.net.meter.Band;
import org.onosproject.net.meter.DefaultBand;
import org.onosproject.net.meter.Meter;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterRequest;
import org.onosproject.net.meter.DefaultMeterRequest;
import org.onosproject.net.meter.MeterService;

// packet processor
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.net.PortNumber;
import org.onosproject.net.DeviceId;
import org.onlab.packet.ARP;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpAddress;
import org.onlab.packet.Ip4Address;
import java.util.Arrays;
import java.util.ArrayList;
// import java.util.List;
import java.util.Collection;
import org.onosproject.net.packet.DefaultOutboundPacket;

import java.nio.ByteBuffer;

// flow objective
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;

/** Sample Network Configuration Service Application. **/
@Component(immediate = true)
public class AppComponent {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final NameConfigListener cfgListener = new NameConfigListener();
  private NameConfig config;

  private final ConfigFactory<ApplicationId, NameConfig> factory = new ConfigFactory<ApplicationId, NameConfig>(
    APP_SUBJECT_FACTORY, NameConfig.class, "informations") {
    @Override
    public NameConfig createConfig() {
      return new NameConfig();
    }
  };

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected NetworkConfigRegistry cfgService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected CoreService coreService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected IntentService intentservice;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected PacketService packetService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected GroupService groupService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected MeterService meterService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected FlowObjectiveService flowObjectiveService;

  private ApplicationId appId;
  private ArpProcessor processor = new ArpProcessor();
  @Activate
  protected void activate() {
    appId = coreService.registerApplication("nycu.winlab.groupmeter");
    cfgService.addListener(cfgListener);
    cfgService.registerConfigFactory(factory);
    packetService.addProcessor(processor, PacketProcessor.director(2));
    TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
    selector.matchEthType(Ethernet.TYPE_ARP);
    selector.matchEthType(Ethernet.TYPE_IPV4);
    // selector.matchIPProtocol(IPv4.PROTOCOL_UDP);
    packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

    log.info("GroupMeter APP Started");
  }

  @Deactivate
  protected void deactivate() {
    cfgService.removeListener(cfgListener);
    cfgService.unregisterConfigFactory(factory);
    packetService.removeProcessor(processor);
    processor = null;
    TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
    selector.matchEthType(Ethernet.TYPE_ARP);
    packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

    log.info("GroupMeter APP Stopped");
  }

  // TODO: build intent
  private void buildIntent(PacketContext context) {
    TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
    // selectorBuilder.matchEthSrc(config.getMac1());
    InboundPacket pkt = context.inPacket();
    ConnectPoint ingressPoint = pkt.receivedFrom();
    ConnectPoint egressPoint = null;
    IPv4 ipv4Packet = (IPv4) pkt.parsed().getPayload();
    IpAddress dstIp = Ip4Address.valueOf(ipv4Packet.getDestinationAddress());
    DeviceId dstDevice = null;
    PortNumber dstPort = null;
    IpAddress ip1 = IpAddress.valueOf(config.getIp1());
    // IpAddress ip2 = IpAddress.valueOf(config.getIp2());
    // log.info("to ip `{}`", dstIp.toString());
    // log.info("Ip1 is `{}`", config.getIp1());
    if (dstIp.compareTo(ip1) == 0) {
      // log.info("intent to h1");
      String[] str = config.getHost1().split("/");
      dstDevice = DeviceId.deviceId(str[0]);
      dstPort = PortNumber.portNumber(str[1]);
      egressPoint = new ConnectPoint(dstDevice, dstPort);
      selectorBuilder.matchEthDst(MacAddress.valueOf(config.getMac1()));
    } else {  // if dstIp == ip2
      // log.info("intent to h2");
      String[] str = config.getHost2().split("/");
      dstDevice = DeviceId.deviceId(str[0]);
      dstPort = PortNumber.portNumber(str[1]);
      egressPoint = new ConnectPoint(dstDevice, dstPort);
      selectorBuilder.matchEthDst(MacAddress.valueOf(config.getMac2()));
    }
    FilteredConnectPoint fip = new FilteredConnectPoint(ingressPoint);
    FilteredConnectPoint fep = new FilteredConnectPoint(egressPoint);
    Intent intent = PointToPointIntent.builder()
      .appId(appId)
      .filteredIngressPoint(fip)
      .filteredEgressPoint(fep)
      .selector(selectorBuilder.build())
      .build();
    intentservice.submit(intent);
    log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
      ingressPoint.deviceId(), ingressPoint.port(), egressPoint.deviceId(), egressPoint.port());
  }
  // TODO: install group entry
  private void installGroup() {
    // for s1
    TrafficTreatment t1 = DefaultTrafficTreatment.builder()
      .setOutput(PortNumber.portNumber(2))
      .build();
    TrafficTreatment t2 = DefaultTrafficTreatment.builder()
      .setOutput(PortNumber.portNumber(3))
      .build();
    // treatment, watchport, watchgroup
    GroupBucket b1 = DefaultGroupBucket.createFailoverGroupBucket(t1, PortNumber.portNumber(2), GroupId.valueOf(0));
    GroupBucket b2 = DefaultGroupBucket.createFailoverGroupBucket(t2, PortNumber.portNumber(3), GroupId.valueOf(0));
    GroupBuckets buckets = new GroupBuckets(Arrays.asList(b1, b2));
    GroupDescription groupDescription = new DefaultGroupDescription(
      DeviceId.deviceId("of:0000000000000001"),
      GroupDescription.Type.FAILOVER,
      buckets,
      null,
      1,  // gorup id
      appId
    );
    groupService.addGroup(groupDescription);

    // install flowrule to s1
    TrafficSelector selector = DefaultTrafficSelector.builder()
      .matchInPort(PortNumber.portNumber(1))
      .matchEthType(Ethernet.TYPE_IPV4)
      .build();

    TrafficTreatment treatment = DefaultTrafficTreatment.builder()
      .group(GroupId.valueOf(1))
      .build();

    ForwardingObjective flowRule = DefaultForwardingObjective.builder()
      .withSelector(selector)
      .withTreatment(treatment)
      .withPriority(50000)
      .withFlag(ForwardingObjective.Flag.VERSATILE)
      .fromApp(appId)
      .makePermanent()
      .add();
    DeviceId deviceId = DeviceId.deviceId("of:0000000000000001");
    flowObjectiveService.forward(deviceId, flowRule);
  }
  // TODO: intall meter entry
  private void installMeter() {
    Band band = DefaultBand.builder()
      .withRate(512)
      .burstSize(1024)
      .ofType(Band.Type.DROP)
      .build();
    Collection<Band> bands = new ArrayList<>();
    bands.add(band);
    MeterRequest meterRequest = DefaultMeterRequest.builder()
      .forDevice(DeviceId.deviceId("of:0000000000000004"))
      .fromApp(appId)
      .withUnit(Meter.Unit.KB_PER_SEC)
      .withBands(bands)
      .burst()
      .add();

    meterService.submit(meterRequest);
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    Collection<Meter> meters = meterService.getMeters(DeviceId.deviceId("of:0000000000000004"));
    Meter mt = meters.stream().findFirst().orElse(null);
    MeterId meterId = null;
    if (mt != null) {
      meterId = mt.id();
      // log.info("from GroupMeter, meter submitted!, meterId is `{}`", meterId);
    }
    // install flowrule to s4
    TrafficSelector selector = DefaultTrafficSelector.builder()
      .matchEthSrc(MacAddress.valueOf(config.getMac1()))
      .build();

    TrafficTreatment treatment = DefaultTrafficTreatment.builder()
      .meter(meterId)
      .setOutput(PortNumber.portNumber(2))
      .build();

    ForwardingObjective flowRule = DefaultForwardingObjective.builder()
      .withSelector(selector)
      .withTreatment(treatment)
      .withPriority(50000)
      .withFlag(ForwardingObjective.Flag.VERSATILE)
      .fromApp(appId)
      .makePermanent()
      .add();
    DeviceId deviceId = DeviceId.deviceId("of:0000000000000004");
    flowObjectiveService.forward(deviceId, flowRule);
  }
  // handle config event
  private class NameConfigListener implements NetworkConfigListener {
    @Override
    public void event(NetworkConfigEvent event) {
      if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
        && event.configClass().equals(NameConfig.class)) {
        config = cfgService.getConfig(appId, NameConfig.class);
        if (config != null) {
          log.info("ConnectPoint_h1: `{}`, ConnectPoint_h2: `{}`", config.getHost1(), config.getHost2());
          log.info("MacAddress_h1: `{}`, MacAddress_h2: `{}`", config.getMac1(), config.getMac2());
          log.info("IpAddress_h1: `{}`, IpAddress_h2: `{}`", config.getIp1(), config.getIp2());
          // install group entry and meter
          installGroup();
          installMeter();
        }
      }
    }
  }

  // handle ARP packets
  private class ArpProcessor implements PacketProcessor {
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
      DeviceId recDevId = pkt.receivedFrom().deviceId();
      if (ethPkt.getEtherType() == Ethernet.TYPE_ARP) {
        MacAddress srcMac = ethPkt.getSourceMAC();
        // MacAddress dstMac = ethPkt.getDestinationMac();
        ARP arpPacket = (ARP) ethPkt.getPayload();
        // Ip4Address srcIp4 = Ip4Address.valueOf(arpPacket.getSenderProtocolAddress());
        Ip4Address destIp4 = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());
        short arpOpCode = arpPacket.getOpCode();
        if (arpOpCode == ARP.OP_REQUEST) {
          MacAddress dstMac;
          // IpAddress srcIp = IpAddress.valueOf(srcIp4.toString());
          IpAddress destIp = IpAddress.valueOf(destIp4.toString());
          IpAddress ip1 = IpAddress.valueOf(config.getIp1());
          // IpAddress ip2 = IpAddress.valueOf(config.getIp2());
          if (destIp.compareTo(ip1) == 0) {
            log.info("dst ip is 1");
            dstMac = MacAddress.valueOf(config.getMac1());
          } else {  // if destIp4 == ip2
            log.info("dst ip id 2");
            dstMac = MacAddress.valueOf(config.getMac2());
          }
          log.info("TABLE HIT. Requested MAC = {}", dstMac);
          replyArp(context, srcMac, dstMac, destIp4, arpPacket);
        }
      } else if (ethPkt.getEtherType() ==  Ethernet.TYPE_IPV4) {
        log.info("from device in packet processor `{}`, ipv4", recDevId);
        buildIntent(context);
      }
    }
    private void replyArp(PacketContext context, MacAddress srcMac,
            MacAddress destMac, Ip4Address destIp4, ARP arpPacket) {
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
}