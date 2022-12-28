package proposed.hvmm.compares.allocation;

import java.util.List;
import java.util.stream.Collectors;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;

import proposed.hvmm.core.TrafficPowerHost;
import proposed.hvmm.core.TrafficPowerVm;
import proposed.hvmm.interfaces.BatchVmAllocation;

/**
 * @author PiAo
 * @date 2021/12/12
 */
public class VmAllocationPolicyFFD extends VmAllocationPolicyCommon implements BatchVmAllocation {

    public VmAllocationPolicyFFD(List<? extends Host> list) {
        super(list);
    }

    public TrafficPowerHost getSuitableHost(TrafficPowerVm vm, List<TrafficPowerHost> hostList) {
        for (TrafficPowerHost host : hostList) {
            if (host.isSuitableForVm(vm))
                return host;
        }
        return null;
    }

    public void allocateVmList(List<Vm> vmList) {
        if (vmList.isEmpty())
            return;
        List<TrafficPowerVm> vms = vmList.stream().map(x -> (TrafficPowerVm) x).sorted(
                (vm1, vm2) -> Double.compare(vm2.getTotalRequestMips(), vm1.getTotalRequestMips()))
                .collect(Collectors.toList());
        for (Vm vm : vms) {
            allocateHostForVm(vm);
        }
    }

}
