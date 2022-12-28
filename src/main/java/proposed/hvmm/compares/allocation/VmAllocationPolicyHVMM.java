package proposed.hvmm.compares.allocation;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;

import lombok.Getter;
import lombok.Setter;
import proposed.hvmm.core.Helper;
import proposed.hvmm.core.Main;
import proposed.hvmm.core.TrafficPowerHost;
import proposed.hvmm.core.TrafficPowerVm;

/**
 * @author PiAo
 * @date 2021/12/3
 */
@Setter
@Getter
public class VmAllocationPolicyHVMM extends VmAllocationPolicyCommon {
    private final Map<Integer, Vm> vmMap;
    private final Map<Integer, TrafficPowerHost> hostTable;

    public VmAllocationPolicyHVMM(List<? extends Host> hostList) {
        super(hostList);
        hostTable = new HashMap<>();
        for (var host : hostList)
            hostTable.put(host.getId(), (TrafficPowerHost) host);
        vmMap = new HashMap<>();
    }

    public List<TrafficPowerHost> hostIdsToHosts(Collection<Integer> hostIdList) {
        return hostIdList.stream().map(this::getHost).collect(Collectors.toList());
    }

    public TrafficPowerHost getSuitableHost(TrafficPowerVm vm, final List<TrafficPowerHost> hostList) {
        var bestHostOptional = hostList.stream().filter(TrafficPowerHost::isActive)
                .filter(x -> x.isSuitableForVm(vm))
                .max(Comparator.comparingDouble(
                        h -> Helper.cosSim(h.getAvailableResourceList(), vm.getDataForCosineSimilarity())));
        // 如果无法从活跃的服务器中选择，那就从空闲的中选择
        return bestHostOptional.orElseGet(() -> {
            var bestIdleHostOptional = hostList.stream().filter(TrafficPowerHost::isIdle)
                    .filter(x -> x.isSuitableForVm(vm))
                    .max(Comparator.comparingDouble(h ->
                    h.getPowerEfficiencyAtUtilization(Main.datacenter.getRealAverageCpuMipsUtilization())));
                    // .max(Comparator.comparingDouble(TrafficPowerHost::getPeakPeff));
            return bestIdleHostOptional.orElse(null);
        });
    }

    @Override
    public boolean allocateHostForVm(Vm vm, Host host1) {
        boolean result = super.allocateHostForVm(vm, host1);
        if (result) {
            vmMap.put(vm.getId(), vm);
        }
        return result;
    }

    public TrafficPowerHost getHost(int hostId) {
        return hostTable.get(hostId);
    }

    public TrafficPowerVm getVm(int vmId) {
        return (TrafficPowerVm) vmMap.get(vmId);
    }

}
