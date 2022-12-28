package proposed.hvmm.compares.allocation;

import java.util.List;
import java.util.stream.Collectors;

import org.cloudbus.cloudsim.Host;

import proposed.hvmm.core.Helper;
import proposed.hvmm.core.TrafficPowerHost;
import proposed.hvmm.core.TrafficPowerVm;

public class VmAllocationPolicyRandom extends VmAllocationPolicyCommon {
    public VmAllocationPolicyRandom(List<? extends Host> list) {
        super(list);
    }

    @Override
    public TrafficPowerHost getSuitableHost(TrafficPowerVm vm, List<TrafficPowerHost> hostList) {
        TrafficPowerHost t = Helper.choice(hostList.parallelStream().filter(x -> x.isActive() && x.isSuitableForVm(vm))
                .collect(Collectors.toList()));
        if (t == null) {
            return Helper.choice(hostList.parallelStream().filter(x -> x.isIdle() && x.isSuitableForVm(vm))
                    .collect(Collectors.toList()));
        }
        return t;
    }
}
