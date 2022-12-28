package proposed.hvmm.compares.allocation;

import java.util.List;

import org.cloudbus.cloudsim.Host;

import proposed.hvmm.core.TrafficPowerHost;
import proposed.hvmm.core.TrafficPowerVm;

public class VmAllocationPolicyPABFD extends VmAllocationPolicyCommon {

    public VmAllocationPolicyPABFD(List<? extends Host> list) {
        super(list);
    }

    public double getHostPowerIncreaseAfterAllocate(TrafficPowerHost host, TrafficPowerVm vm) {
        double curAllocatedMips = host.getAllocatedMips();
        double totalMips = host.getTotalMips();
        double curPower = host.getPower(curAllocatedMips / totalMips);
        double afterPower = host.getPower((curAllocatedMips + vm.getTotalRequestMips()) / totalMips);
        return afterPower - curPower;
    }

    public TrafficPowerHost getSuitableHost(TrafficPowerVm vm, List<TrafficPowerHost> hostList) {
        double minPowerIncrease = Double.MAX_VALUE;
        TrafficPowerHost minPowerIncreaseHost = null;
        for (TrafficPowerHost host : hostList) {
            if (host.isSuitableForVm(vm)) {
                double powerIncrease = getHostPowerIncreaseAfterAllocate(host, vm);
                if (powerIncrease < minPowerIncrease) {
                    minPowerIncrease = powerIncrease;
                    minPowerIncreaseHost = host;
                }
            }
        }
        return minPowerIncreaseHost;
    }
}
