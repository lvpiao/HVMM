package proposed.hvmm.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import proposed.hvmm.compares.allocation.VmAllocationPolicyCommon;
import proposed.hvmm.interfaces.BatchVmAllocation;

/**
 * @author PiAo
 * @date 2021/12/2
 */
@Getter
@Setter
@Slf4j
public class TrafficPowerDatacenter extends Datacenter {
    private final static double KWH = 3600 * 1000;
    // 总能耗
    private final double totalConsumedEnergy = 0;
    private final Map<Integer, TrafficPowerVm> vmMap;
    private boolean migrationAble = false;

    /**
     * Instantiates a new PowerDatacenter.
     *
     * @param name the datacenter name
     * @param characteristics the datacenter characteristics
     * @param vmAllocationPolicy the vm provisioner
     * @throws Exception the exception
     */
    public TrafficPowerDatacenter(String name, DatacenterCharacteristics characteristics,
            VmAllocationPolicy vmAllocationPolicy) throws Exception {
        super(name, characteristics, vmAllocationPolicy, new ArrayList<>(),
                Manager.SCHEDULING_INTERVAL);
        vmMap = new HashMap<>();

    }

    /**
     * Process the event for an User/Broker who wants to create a VM in this Datacenter. This
     * Datacenter will then send the status back to the User/Broker.
     *
     * @param ev information about the event just happened
     * @param ack indicates if the event's sender expects to receive an acknowledge message when the
     *        event finishes to be processed
     * @pre ev != null
     * @post $none
     */
    protected void processVmCreate(SimEvent ev, boolean ack) {
        List<Vm> vmList = (List<Vm>) ev.getData();
        if (getVmAllocationPolicy() instanceof BatchVmAllocation) {
            ((BatchVmAllocation) getVmAllocationPolicy()).allocateVmList(vmList);
        } else {
            for (Vm vm : vmList) {
                getVmAllocationPolicy().allocateHostForVm(vm);
            }
        }
        for (Vm vm : vmList) {
            if (ack) {
                int[] data = new int[3];
                data[0] = getId();
                data[1] = vm.getId();
                data[2] = CloudSimTags.TRUE;
                send(vm.getUserId(), CloudSim.getMinTimeBetweenEvents(), CloudSimTags.VM_CREATE_ACK,
                        data);
            }
            vmMap.put(vm.getId(), (TrafficPowerVm) vm);
            getVmList().add(vm);
            if (vm.isBeingInstantiated()) {
                vm.setBeingInstantiated(false);
            }
            vm.updateVmProcessing(CloudSim.clock(),
                    vm.getHost().getVmScheduler().getAllocatedMipsForVm(vm));
        }
    }

    /**
     * Processes a Cloudlet submission.
     *
     * @param ev information about the event just happened
     * @param ack indicates if the event's sender expects to receive an acknowledge message when the
     *        event finishes to be processed
     * @pre ev != null
     * @post $none
     */
    protected void processCloudletSubmit(SimEvent ev, boolean ack) {
        updateCloudletProcessing();
        try {
            // gets the Cloudlet object
            Cloudlet cl = (Cloudlet) ev.getData();
            // process this Cloudlet to this CloudResource
            cl.setResourceParameter(getId(), getCharacteristics().getCostPerSecond(),
                    getCharacteristics().getCostPerBw());
            int userId = cl.getUserId();
            int vmId = cl.getVmId();
            Host host = getVmAllocationPolicy().getHost(vmId, userId);
            Vm vm = host.getVm(vmId, userId);
            CloudletScheduler scheduler = vm.getCloudletScheduler();

            // 提交Cloudlet到指定的虚拟机
            scheduler.cloudletSubmit(cl);
            if (ack) {
                int[] data = new int[3];
                data[0] = getId();
                data[1] = cl.getCloudletId();
                data[2] = CloudSimTags.TRUE;
                // unique tag = operation tag
                int tag = CloudSimTags.CLOUDLET_SUBMIT_ACK;
                sendNow(cl.getUserId(), tag, data);
            }
        } catch (ClassCastException c) {
            Log.printLine(getName() + ".processCloudletSubmit(): " + "ClassCastException error.");
            c.printStackTrace();
        } catch (Exception e) {
            Log.printLine(getName() + ".processCloudletSubmit(): " + "Exception error.");
            e.printStackTrace();
        }
        checkCloudletCompletion();
    }

    // 每个时间片执行一次这个函数
    protected void updateCloudletProcessing() {
        if (CloudSim.clock() < 0.111
                || CloudSim.clock() >= getLastProcessTime() + CloudSim.getMinTimeBetweenEvents()) {
            List<TrafficPowerHost> list = getVmAllocationPolicy().getHostList();
            double smallerTime = getLastProcessTime() + getSchedulingInterval();
            int updateVmCnt = 0;
            for (TrafficPowerHost host : list) {
                double time = host.updateVmsProcessing(CloudSim.clock());
                updateVmCnt += host.getVmList().size();
                smallerTime = Math.max(time, smallerTime);
            }
            if (updateVmCnt != getVmList().size()) {
                throw new RuntimeException("一些虚拟机状态未更新:" + updateVmCnt + "-" + getVmList().size());
            }
            setLastProcessTime(CloudSim.clock());
            // 判断是否迁移
            if (isMigrationAble()) {
                dealMigration();
            }
            schedule(getId(), smallerTime - CloudSim.clock(), CloudSimTags.VM_DATACENTER_EVENT);
        }
    }

    public void dealMigration() {
        List<Map<String, Object>> migrationMap =
                getVmAllocationPolicy().optimizeAllocation(getVmList());
        double currentTime = CloudSim.clock();
        if (migrationMap != null) {
            for (Map<String, Object> item : migrationMap) {
                TrafficPowerVm vm = (TrafficPowerVm) item.get("vm");
                TrafficPowerHost targetHost = (TrafficPowerHost) item.get("host");
                TrafficPowerHost sourceHost = (TrafficPowerHost) vm.getHost();
                vm.setInMigration(true);
                sourceHost.addMigratingOutVm(vm);
                targetHost.addMigratingInVm(vm);
            }

            for (Map<String, Object> item : migrationMap) {
                TrafficPowerVm vm = (TrafficPowerVm) item.get("vm");
                TrafficPowerHost targetHost = (TrafficPowerHost) item.get("host");
                TrafficPowerHost sourceHost = (TrafficPowerHost) vm.getHost();
                /** VM migration delay = RAM / bandwidth **/
                // we use BW / 2 to model BW available for migration purposes, the other half of BW
                // is for VM communication
                // around 16 seconds for 1024 MB using 1 Gbit/s network
                // 1Byte=8bit

                // 所有迁移的VM平分带宽，迁移时间由源主机和目标主机中带宽最小的决定
                int migratingOutVmCnt = sourceHost.getVmMigratingOutMap().size();
                int migratingInVmCnt = targetHost.getVmMigratingInMap().size();
                double migrateBindwidth0 =
                        ((double) targetHost.getBw() / (2 * 8 * 1024)) / migratingOutVmCnt;
                double migrateBindwidth1 =
                        ((double) sourceHost.getBw() / (2 * 8 * 1024)) / migratingInVmCnt;
                double availableBindwidth = Math.min(migrateBindwidth0, migrateBindwidth1);
                double migrationTime = vm.getRam() / availableBindwidth;
                log.debug(
                        " {} : Migration of VM #{} to Host #{} is started, migrate {} MB RAM need time {}s",
                        currentTime, vm.getId(), targetHost.getId(), vm.getRam(), migrationTime);
                // 这个用于处理虚拟机迁移成功
                send(getId(), migrationTime, CloudSimTags.VM_MIGRATE, item);
                // 下面两个事件用于统计虚拟机性能降低时间
                schedule(getId(), 0, CloudSimTags.VM_DATACENTER_EVENT);
                schedule(getId(), migrationTime - 0.01, CloudSimTags.VM_DATACENTER_EVENT);
            }
        }
    }

    protected void processVmMigrate(SimEvent ev, boolean ack) {
        Object tmp = ev.getData();
        if (!(tmp instanceof Map<?, ?>)) {
            throw new ClassCastException("The data object must be Map<String, Object>");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> migrate = (HashMap<String, Object>) tmp;
        TrafficPowerVm vm = (TrafficPowerVm) migrate.get("vm");
        TrafficPowerHost destHost = (TrafficPowerHost) migrate.get("host");
        destHost.removeMigratingInVm(vm);

        var vmAllocator = getVmAllocationPolicy();
        // 从源服务器中删除
        TrafficPowerHost srcHost = vm.getHost();
        srcHost.vmDestroy(vm);
        srcHost.removeMigratingOutVm(vm);
        vm.setHost(srcHost);
        // 部署到目标服务器
        boolean result;
        if (destHost.isSuitableForVm(vm)) {
            result = vmAllocator.allocateHostForVm(vm, destHost);
        } else {
            result = vmAllocator.allocateHostForVm(vm);
        }
        if (!result) {
            log.debug("VM#{} allocation to the destHost#{} failed", vm.getId(), destHost.getId());
            throw new RuntimeException("VM allocation to the destination host failed");
        }
        // 设置迁移完成
        vm.setInMigration(false);
        if (ack) {
            int[] data = new int[3];
            data[0] = getId();
            data[1] = vm.getId();
            if (result) {
                data[2] = CloudSimTags.TRUE;
            } else {
                data[2] = CloudSimTags.FALSE;
            }
            sendNow(ev.getSource(), CloudSimTags.VM_CREATE_ACK, data);
        }

    }

    public TrafficPowerVm getVm(int vmId) {
        return getVmMap().get(vmId);
    }

    /**
     * Verifies if some cloudlet inside this Datacenter already finished. If yes, send it to the
     * User/Broker
     *
     * @pre $none
     * @post $none
     */
    protected void checkCloudletCompletion() {
        List<? extends Host> list = getVmAllocationPolicy().getHostList();
        for (int i = 0; i < list.size(); i++) {
            Host host = list.get(i);
            for (Vm vm : host.getVmList()) {
                while (vm.getCloudletScheduler().isFinishedCloudlets()) {
                    Cloudlet cl = vm.getCloudletScheduler().getNextFinishedCloudlet();
                    if (cl != null) {
                        sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);
                    }
                }
            }
        }
    }

    /**
     * @return 数据中心消耗额能耗，单位：度（KWH）
     */
    public double getTotalEnergyConsumedInKWH() {
        return getHostList().stream().mapToDouble(h -> {
            TrafficPowerHost h1 = (TrafficPowerHost) h;
            return h1.getTotalEnergyConsumed();
        }).sum() / KWH;
    }

    public int getTotalTrafficCost() {
        List<TrafficPowerVm> vmList = getVmList();
        return vmList.parallelStream()
                .mapToInt(curVm -> curVm.getConnVms().entrySet().stream().mapToInt(node -> {
                    int otherVmId = node.getKey();
                    int weight = node.getValue();
                    var otherVm = getVmMap().get(otherVmId);
                    int dist = otherVm.getHost().distanceToHost(curVm.getHost());
                    return weight * dist;
                }).sum()).sum();
    }

    public int getActiveHostCnt() {
        List<TrafficPowerHost> hostList = getHostList();
        return (int) hostList.parallelStream().filter(TrafficPowerHost::isActive).count();
    }

    public double getTotalResourceWastage() {
        List<TrafficPowerHost> hostList = getHostList();
        return hostList.parallelStream().filter(TrafficPowerHost::isActive)
                .mapToDouble(TrafficPowerHost::getResourceWastage).sum();
    }

    public double getAverageSlavRate() {
        List<TrafficPowerVm> vmList = getVmList();
        return vmList.stream().mapToDouble(TrafficPowerVm::getSlavRate).average().getAsDouble();
    }

    public double getRealAverageCpuMipsUtilization() {
        List<TrafficPowerHost> hostList = getHostList();
        var t = hostList.stream().filter(TrafficPowerHost::isActive)
                .mapToDouble(x -> x.getMovingAvgLoad(3)).average();
        return t.isPresent() ? t.getAsDouble() : 0;
    }

    public VmAllocationPolicyCommon getVmAllocationPolicy() {
        return (VmAllocationPolicyCommon) super.getVmAllocationPolicy();
    }

    public int getTotalMigrationTimes() {
        if (getVmAllocationPolicy().isMigrateAble()) {
            return getVmAllocationPolicy().getVmReallocator().getMigrationCount();
        } else {
            return 0;
        }
    }

    @Override
    public void shutdownEntity() {
        log.info(CloudSim.clock() + ":" + getName() + " is shutting down...");
    }

}
