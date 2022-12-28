package proposed.hvmm.core;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.RandomUtils;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 一种可以延迟提交Vm和Cloudlet的Broker
 *
 * @author PiAo
 * @date 2021/12/2
 */
@Slf4j
@Setter
@Getter
public class TrafficPowerBroker extends DatacenterBroker {
    public final static String savePath = Manager.baseDataPath + "brokers";
    private List<TrafficPowerVm> requestVmList;
    private List<TrafficPowerCloudlet> requestCloudletList;
    private double delay;

    public TrafficPowerBroker() throws Exception {
        super("TrafficPowerBroker_" + CloudSim.getEntityList().size());
        this.delay = RandomUtils.nextDouble(0, Manager.ONE_DAY_SECOND * Manager.SIMULATE_DAYS);
        requestVmList = new ArrayList<>();
        requestCloudletList = new ArrayList<>();
        log.debug("Broker{}将在{}s后开始提交虚拟机", getId(), delay);
    }

    public TrafficPowerBroker(List<TrafficPowerVm> requestVmList, List<TrafficPowerCloudlet> requestCloudletList,
            double delay) throws Exception {
        super("TrafficPowerBroker_" + CloudSim.getEntityList().size());
        setDelay(delay);
        this.requestVmList = requestVmList;
        this.requestCloudletList = requestCloudletList;
    }

    /**
     * Creates the vm list.
     *
     * @param brokerId the broker id
     * @return the list< vm>
     */
    public static List<TrafficPowerVm> createVmList(int brokerId) {
        List<TrafficPowerVm> vms = new ArrayList<>();
        int vmsNumber = RandomUtils.nextInt(1, 17);
        for (int i = 0; i < vmsNumber; i++) {
            double[] vmSpec = Helper.getVmConfByRandom();
            // 调整资源单位
            // 确保内存单位为MB
            if (vmSpec[2] < 1024) {
                vmSpec[2] *= 1024;
            }
            TrafficPowerVm vm = new TrafficPowerVm(brokerId, vmSpec);
            vms.add(vm);
        }
        return vms;
    }

    public static void randomConnectEachOther(List<TrafficPowerVm> requestVmList) {
        int n = requestVmList.size();
        for (int i = 0; i < RandomUtils.nextInt(0, n); i++) {
            var curVm = requestVmList.get(i);
            var targetVm = Helper.choice(requestVmList);
            int weight = RandomUtils.nextInt(0, curVm.getMaxBandwidth());
            curVm.addCommunicateVm(targetVm.getId(), weight);
        }
    }

    public static void randomConnectWithDcVms(List<TrafficPowerVm> requestVmList, List<TrafficPowerVm> dcVmList) {
        int maxConnCnt = 8;
        if (dcVmList.size() >= maxConnCnt) {
            for (var curVm : Helper.sample(requestVmList, RandomUtils.nextInt(0, requestVmList.size()))) {
                // for (var curVm : requestVmList) {
                var connVmList = Helper.sample(dcVmList, RandomUtils.nextInt(1, maxConnCnt + 1));
                log.debug("{}:vm#{}与{}个数据中心的虚拟机通信", CloudSim.clock(), curVm.getId(), connVmList.size());
                for (var t : connVmList) {
                    int weight = RandomUtils.nextInt(0, curVm.getMaxBandwidth());
                    curVm.addCommunicateVm(t.getId(), weight);
                }
            }
        }
    }

    public static List<TrafficPowerCloudlet> createCloudletList(List<TrafficPowerVm> vmList) {
        List<TrafficPowerCloudlet> list = new LinkedList<>();
        try {
            for (Vm vm : vmList) {
                int lvl = RandomUtils.nextInt(Manager.WORKLOAD_DOWN_THR, Manager.WORKLOAD_UP_THR) / 10 * 10;
                String workloadPath = Helper.getWorkloadByRandom(lvl).getAbsolutePath();
                TrafficPowerCloudlet cloudlet = new TrafficPowerCloudlet(vm.getId(), workloadPath, Integer.MAX_VALUE,
                        vm.getNumberOfPes());
                cloudlet.setUserId(vm.getUserId());
                cloudlet.setVmId(vm.getId());
                list.add(cloudlet);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void bindVmsAndCloudlets(List<TrafficPowerVm> vmList, List<TrafficPowerCloudlet> cloudletList) {
        setVmList(vmList);
        setCloudletList(cloudletList);
    }

    /**
     * Create the submitted virtual machines in a datacenter.
     *
     * @param datacenterId id of the chosen Datacenter
     */
    protected void createVmsInDatacenter(int datacenterId) {
        // send as much vms as possible for this datacenter before trying the next one
        int requestedVms = 0;
        // String datacenterName = CloudSim.getEntityName(datacenterId);
        List<Vm> requestVmList = new ArrayList<>();
        for (Vm vm : getVmList()) {
            if (!getVmsToDatacentersMap().containsKey(vm.getId())) {
                requestVmList.add(vm);
                requestedVms++;
            }
        }
        // log.debug(CloudSim.clock() + ": " + getName() + ": Trying to Create VM
        // Count#" + requestVmList.size() + " in " + datacenterName);
        sendNow(datacenterId, CloudSimTags.VM_CREATE_ACK, requestVmList);
        getDatacenterRequestedIdsList().add(datacenterId);
        setVmsRequested(requestedVms);
        setVmsAcks(0);
    }

    @Override
    public void processOtherEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case CustomTags.CREATE_BROKER:
                // log.debug("{}: 当前数据中心的虚拟机的数量：{}", CloudSim.clock(),
                // Main.datacenter.getVmList().size());
                List<TrafficPowerVm> dcVmList = Main.datacenter.getVmList();
                if (!Manager.LOAD_DATA_FROM_FILES) {
                    requestVmList = createVmList(getId());
                    // 本次请求的虚拟机之间相互通信
                    randomConnectEachOther(requestVmList);
                    // 随机和数据中心的其他的Vm通信
                    randomConnectWithDcVms(requestVmList, dcVmList);
                    requestCloudletList = createCloudletList(requestVmList);
                    Helper.saveBroker(this);
                }
                if (dcVmList.size() < Manager.MAX_VM_CNT) {
                    // 确保请求的虚拟机总数不超过设定的最大值
                    if (requestVmList.size() + dcVmList.size() > Manager.MAX_VM_CNT) {
                        requestVmList = requestVmList.subList(0,
                                requestVmList.size() + dcVmList.size() - Manager.MAX_VM_CNT);
                    }
                    bindVmsAndCloudlets(requestVmList, requestCloudletList);
                }
                this.scheduleNow(getId(), CloudSimTags.RESOURCE_CHARACTERISTICS_REQUEST);
                break;
            default:
                Log.printLine(getName() + ": unknown event type");
                System.exit(0);
                break;
        }
    }

    @Override
    public void startEntity() {
        schedule(getId(), delay, CustomTags.CREATE_BROKER);
    }

    @Override
    public void shutdownEntity() {
        // log.debug("关闭{},", getName());
        CloudSim.cancelAll(getId(), CloudSim.SIM_ANY);
    }
}
