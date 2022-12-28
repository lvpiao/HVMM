package proposed.hvmm.core;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.models.PowerModelSpecPower;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author PiAo
 * @date 2021/12/2
 */
@Setter
@Getter
@Slf4j
public class TrafficPowerHost extends Host {
    public static final long STORAGE_SIZE = 1024 * 1024 * 1024; // host storage

    private final int[] position;
    private final double[] resource;

    private double totalMips;

    // 迁移进来VM
    private Map<Integer, TrafficPowerVm> vmMigratingInMap = new HashMap<>();
    // 迁移出去VM
    private Map<Integer, TrafficPowerVm> vmMigratingOutMap = new HashMap<>();

    // 运行该Host的总能耗
    private double totalEnergyConsumed;
    private Map<Integer, Vm> vmMap;
    private LinkedList<Double> historyCpuUtilization = new LinkedList<>();
    // 上一次处理Cloudlet的时间
    private double lastProcessTime;
    private double lastAddHistoryCpuUtilizationTime;
    // 上一次处理时cpu利用率
    private double lastCpuUtilization;

    private double peakPeff = -1;
    private double Uopt = -1;

    private PowerModelSpecPower powerModel;

    public TrafficPowerHost(int hostId, double[] resource, int[] position, List<Pe> peList,
            TrafficPowerPowerModel powerModel) {
        super(hostId, new TrafficPowerRamProvisioner((int) resource[Manager.RAM]),
                new BwProvisionerSimple((long) resource[Manager.BW]), STORAGE_SIZE, peList,
                new TrafficPowerVmScheduler(peList));
        this.resource = resource;
        this.position = position;
        this.powerModel = powerModel;
        this.totalEnergyConsumed = 0;
        this.totalMips = getPeList().stream().mapToDouble(Pe::getMips).sum();
        // 调整资源单位
        // 确保内存单位为MB
        if (resource[2] < 1024) {
            resource[2] *= 1024;
        }
        this.vmMap = new HashMap<>();
        for (double u = 0; u <= 1.0; u += 0.1) {
            double curPeff = getPowerEfficiencyAtUtilization(u);
            if (curPeff > peakPeff) {
                peakPeff = curPeff;
                Uopt = u;
            }
        }
        // log.info("在{}创建了主机:{}{}-{} , Uopt：{}", Arrays.toString(position), getId(),
        // Arrays.toString(resource),
        // powerModel.getData(), Uopt);
    }

    public double getPowerEfficiencyAtUtilization(double utilization) {
        if (utilization == 0)
            return 0;
        double mips = getTotalMips() * utilization;
        double power = getPower(utilization);
        return mips / power;
    }

    // 判断Host是否可以放置该Vm
    public boolean isSuitableForVm(Vm vm) {
        // log.debug("Host#{}的Pe有{}MIPS", getId(), getPeList().get(0).getMips());
        double migratingVmUsedTotalMips = vmMigratingInMap.values().stream()
                .mapToDouble(TrafficPowerVm::getTotalRequestMips).sum();
        double migratingVmUsedTotalRam =
                vmMigratingInMap.values().stream().mapToDouble(TrafficPowerVm::getRam).sum();
        return getVmScheduler().canAddVmForPes((TrafficPowerVm) vm, migratingVmUsedTotalMips)// 确保Pe足够
                && getRamProvisioner().isSuitableForVm(null,
                        (int) (vm.getRam() + migratingVmUsedTotalRam));// 确保Ram足够
    }

    // Vm创建是消耗该Host的CPU和Ram
    public boolean vmCreate(Vm vm1) {
        TrafficPowerVm vm = (TrafficPowerVm) vm1;
        if (!isSuitableForVm(vm)) {
            throw new RuntimeException("无法部署该虚拟机");
        }
        if (!getVmScheduler().allocatePesForVm(vm, vm.getCurrentRequestedMips())) {
            throw new RuntimeException(
                    "Allocation of VM #" + vm.getId() + " to Host #" + getId() + " failed by MIPS");
        }
        if (!getRamProvisioner().allocateRamForVm(vm, vm.getRam())) {
            throw new RuntimeException(
                    "Allocation of VM #" + vm.getId() + " to Host #" + getId() + " failed by RAM");
        }
        getVmList().add(vm);
        removeMigratingInVm(vm);
        vm.setHost(this);
        vmMap.put(vm.getId(), vm);
        log.debug("{}:VM#{} is placed into Host#{}", CloudSim.clock(), vm.getId(),
        this.getId());
        return true;
    }

    /**
     * Destroys a VM running in the host.
     */
    public void vmDestroy(TrafficPowerVm vm) {
        if (vm != null) {
            vmDeallocate(vm);
            getVmList().remove(vm);
            vm.setHost(null);
            removeMigratingOutVm(vm);
            vmMap.remove(vm.getId());
        }
    }

    // 每个时间片调用一次这个方法，用于更新VM的运行状态，统计能耗，流量等信息
    public double updateVmsProcessing(double currentTime) {
        double timeFrame = currentTime - getLastProcessTime();
        double smallerTime = Integer.MAX_VALUE;
        double hostUsedMips = 0;
        List<TrafficPowerVm> vmList = getVmList();
        for (TrafficPowerVm vm : vmList) {
            hostUsedMips += vm.getRealUsedMips();
            double time =
                    vm.updateVmProcessing(currentTime, getVmScheduler().getAllocatedMipsForVm(vm));
            if (time > 0.0 && time < smallerTime) {
                smallerTime = time;
            }
        }
        double toCpuUtilization = hostUsedMips / getTotalMips();
        double fromCpuUtilization = getLastCpuUtilization();
        totalEnergyConsumed +=
                getEnergyLinearInterpolation(fromCpuUtilization, toCpuUtilization, timeFrame);

        historyCpuUtilization.addFirst(toCpuUtilization);
        if (historyCpuUtilization.size() > 1024) {
            historyCpuUtilization.removeLast();
        }
        setLastProcessTime(CloudSim.clock());
        setLastCpuUtilization(toCpuUtilization);
        return smallerTime;
    }

    public int distanceToHost(TrafficPowerHost h1) {
        int n = position.length;
        for (int i = 0; i < n; i++) {
            if (position[i] != h1.position[i]) {
                return (3 - i) * 2;
            }
        }
        return 0;
    }

    public int getPodId() {
        return position[0];
    }

    public int getRackId() {
        return position[1] + getPodId() * Manager.FAT_TREE_POD_COUNT / 2;
    }

    public boolean isActive() {
        return getVmMap().size() > 0;
    }

    public boolean isIdle() {
        return !isActive();
    }

    /**
     * @return 分配给VM的CPU的利用率
     */
    public double getCpuUtilization() {
        if (isIdle())
            return 0;
        double totalMips = getTotalMips();
        double freeMips = getAvailableMips();
        return (totalMips - freeMips) / totalMips;
    }

    public double getCpuUtilizationAfterPlaceVm(TrafficPowerVm vm) {
        if (isIdle())
            return 0;
        double totalMips = getTotalMips();
        double freeMips = getAvailableMips() - vm.getTotalRequestMips();
        return (totalMips - freeMips) / totalMips;
    }

    public double getResourceWastage() {
        if (isIdle())
            return 0;
        double Rc = 1 - getCpuUtilization();
        double Rm = 1 - memUsage();
        double a = Math.abs(Rc - Rm) + 0.0001;
        double b = getCpuUtilization() + memUsage();
        return a / b;
    }

    public double getRealTimeWorkLoad() {
        return getRealAllocatedMips() / getTotalMips();
    }

    public double getRealAllocatedMips() {
        return Helper.getVmsRealUsedMips(getVmList());
    }

    public double getAllocatedMips() {
        return getVmList().stream().mapToDouble(x -> ((TrafficPowerVm) x).getTotalRequestMips())
                .sum();
    }

    public double memUsage() {
        RamProvisioner ramProvisioner = getRamProvisioner();
        double total = ramProvisioner.getRam();
        double free = ramProvisioner.getAvailableRam();
        return (total - free) / total;
    }

    public double getMovingAvgLoad(int w_size) {
        double[] res = new double[w_size];
        for (int i = 0; i < w_size; i++) {
            res[i] = (Math.exp(-i) * (1 - Math.exp(-1))) / (1 - Math.exp(-w_size));
        }
        List<Double> historyCpuUtilization = this.getHistoryCpuUtilization();
        int n = historyCpuUtilization.size();
        double avgUtilization = 0;
        for (int i = 0; i < Math.min(w_size, n); i++) {
            avgUtilization += res[i] * historyCpuUtilization.get(i);
        }
        return avgUtilization;
    }

    public boolean isOverLoad() {
        return getRealTimeWorkLoad() > Manager.HOST_OVERLOAD_THR;
    }


    public double getEnergyLinearInterpolation(double fromUtilization, double toUtilization,
            double time) {
        double fromPower = getPower(fromUtilization);
        double toPower = getPower(toUtilization);
        return (fromPower + (toPower - fromPower) / 2) * time;
    }

    /**
     * 获取指定cpu利用率下的功率（单位：W）
     *
     * @param workLoad 范围 [0,1]
     * @return 在该利用率下的能耗
     */
    public double getPower(double workLoad) {
        if (workLoad == 0)
            return 0;
        return getPowerModel().getPower(workLoad);
    }

    public double powerEfficiency() {
        return getPower(1.0) / resource[Manager.PE];
    }

    public double[] getAvailableResourceList() {
        double migratingVmUsedTotalMips = vmMigratingInMap.values().stream()
                .mapToDouble(TrafficPowerVm::getTotalRequestMips).sum();
        double migratingVmUsedTotalRam =
                vmMigratingInMap.values().stream().mapToDouble(TrafficPowerVm::getRam).sum();
        return new double[] {getPeList().get(0).getMips(),
                getAvailableMips() - migratingVmUsedTotalMips,
                getRamProvisioner().getAvailableRam() - migratingVmUsedTotalRam};
    }

    @Override
    public TrafficPowerVmScheduler getVmScheduler() {
        return (TrafficPowerVmScheduler) super.getVmScheduler();
    }

    public double getResourceWastageAfterPlaceVm(Vm vm) {
        if (!isSuitableForVm(vm)) {
            throw new RuntimeException("can not place the vm");
        }
        vmCreate(vm);
        double rw = getResourceWastage();
        vmDestroy(vm);
        return rw;
    }

    // add migrated in vm
    public void addMigratingInVm(TrafficPowerVm vm) {
        vmMigratingInMap.put(vm.getId(), vm);
    }

    // remove migrated in vm
    public void removeMigratingInVm(TrafficPowerVm vm) {
        vmMigratingInMap.remove(vm.getId());
    }

    // add migrated out vm
    public void addMigratingOutVm(TrafficPowerVm vm) {
        vmMigratingOutMap.put(vm.getId(), vm);
    }

    // remove migrated out vm
    public void removeMigratingOutVm(TrafficPowerVm vm) {
        vmMigratingOutMap.remove(vm.getId());
    }

}
