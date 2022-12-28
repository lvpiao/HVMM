package proposed.hvmm.core;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;

/**
 * @author PiAo
 * @date 2021/12/2
 */
@Slf4j
@Setter
@Getter
public class TrafficPowerVm extends Vm {

    private static int vmId = 0;

    private Map<Integer, Integer> connVms;
    private double[] resource;
    private int maxBandwidth;
    private int stateCnt = 0;
    // 性能降低总时长
    private double overSlavr;

    /**
     * Creates a new Vm object.
     *
     * @param userId ID of the VM's owner
     * @pre id >= 0
     * @pre userId >= 0
     * @pre size > 0
     * @pre ram > 0
     * @pre bw > 0
     * @pre cpus > 0
     * @pre priority >= 0
     * @pre cloudletScheduler != null
     */
    public TrafficPowerVm(int userId, double[] resource) {
        super(vmId++, userId, resource[Manager.MIPS], (int) resource[Manager.PE],
                (int) resource[Manager.RAM], (long) resource[Manager.BW], 100, "Xen",
                new TrafficPowerCloudletScheduler(resource[Manager.MIPS],
                        (int) resource[Manager.PE]));
        connVms = new HashMap<>();
        this.resource = resource;
        this.maxBandwidth = (int) resource[Manager.BW];
        overSlavr = 0;
        // log.info("创建了虚拟机" + Arrays.toString(getRequestResource()) + " " + getUid());
    }

    public TrafficPowerVm(int id, int userId, double[] resource) {
        super(id, userId, resource[Manager.MIPS], (int) resource[Manager.PE],
                (int) resource[Manager.RAM], (long) resource[Manager.BW], 100, "Xen",
                new TrafficPowerCloudletScheduler(resource[Manager.MIPS],
                        (int) resource[Manager.PE]));
        connVms = new HashMap<>();
        this.resource = resource;
        this.maxBandwidth = (int) resource[Manager.BW];
        log.info("创建了虚拟机" + Arrays.toString(getResource()) + " " + getUid());
    }

    public List<Double> getCurrentRequestedMips() {
        List<Double> currentRequestedMips = new ArrayList<>();
        for (int i = 0; i < getNumberOfPes(); i++) {
            currentRequestedMips.add(getMips());
        }
        return currentRequestedMips;
    }

    public double[] getDataForCosineSimilarity() {
        // return new double[]{getTotalRequestMips(), getRam()};
        return new double[] {getMips(), getTotalRequestMips(), getRam()};
    }

    public double getTotalRequestMips() {
        return getMips() * getNumberOfPes();
    }

    public double getRealUsedMips() {
        return getTotalRequestMips() * getTotalUtilizationOfCpu(CloudSim.clock());
    }

    public double updateVmProcessing(double currentTime, List<Double> allocatedMips) {
        if (allocatedMips != null) {
            return getCloudletScheduler().updateVmProcessing(currentTime, allocatedMips);
        }
        return 0.0;
    }

    public void addCommunicateVm(int vmId, int weight) {
        connVms.put(vmId, weight);
    }

    public int trafficCount() {
        return getConnVms().values().stream().mapToInt(x -> x).sum();
    }

    public TrafficPowerHost getHost() {
        return (TrafficPowerHost) super.getHost();
    }

    public TrafficPowerCloudletScheduler getCloudletScheduler() {
        return (TrafficPowerCloudletScheduler) super.getCloudletScheduler();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TrafficPowerVm that = (TrafficPowerVm) o;
        return getId() == that.getId();
    }

    @Override
    public int hashCode() {
        return getId();
    }

    public void increaseSlavRate(double slavr) {
        overSlavr += slavr;
        stateCnt++;
    }

    public double getSlavRate() {
        if (stateCnt == 0) {
            return 0;
        }
        return overSlavr / stateCnt;
    }
}
