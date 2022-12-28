package proposed.hvmm.compares.reallocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.cloudbus.cloudsim.Vm;
import proposed.hvmm.compares.allocation.VmAllocationPolicyCommon;
import proposed.hvmm.core.Helper;
import proposed.hvmm.core.TrafficPowerHost;
import proposed.hvmm.core.TrafficPowerVm;

public class VmReallocationPolicyMMT extends VmReallocationPolicyCommon {


    public VmReallocationPolicyMMT(VmAllocationPolicyCommon vmAllocationPolicy) {
        super(vmAllocationPolicy);
    }

    public void addVMsToMigrationList(List<TrafficPowerHost> hostList,
            List<Map<String, Object>> migrationList, List<TrafficPowerVm> vmList) {
        for (var vm : vmList) {
            addMigrationItem(migrationList, vm, vmAllocator.getSuitableHost(vm, hostList));
        }
    }

    public List<Map<String, Object>> getReallocationList(List<? extends Vm> vmList) {
        List<TrafficPowerHost> hostList = vmAllocator.getHostList();
        List<Map<String, Object>> migrationList = new ArrayList<>();
        for (TrafficPowerHost host : hostList) {
            if (host.isIdle()) {
                continue;
            }
            List<TrafficPowerVm> hostVmList = host.getVmList();
            if (host.isOverLoad()) {
                hostVmList.sort(Comparator.comparingInt(Vm::getRam));
                List<TrafficPowerVm> migrationVms = new ArrayList<>();
                for (var vm : hostVmList) {
                    if (vm.isInMigration()) {
                        continue;
                    }
                    migrationVms.add(vm);
                    double U_after =
                            (host.getRealAllocatedMips() - Helper.getVmsRealUsedMips(migrationVms))
                                    / host.getTotalMips();
                    if (U_after <= host.getUopt()) {
                        break;
                    }
                }
                addVMsToMigrationList(hostList, migrationList, migrationVms);
            } else if (host.getRealTimeWorkLoad() < 0.2) {
                addVMsToMigrationList(hostList, migrationList, hostVmList);
            }
        }
        return migrationList;
    }

}
