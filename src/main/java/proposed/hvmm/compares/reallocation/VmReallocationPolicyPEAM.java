package proposed.hvmm.compares.reallocation;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.Vm;
import proposed.hvmm.compares.allocation.VmAllocationPolicyCommon;
import proposed.hvmm.compares.allocation.VmAllocationPolicyPEAP;
import proposed.hvmm.core.Helper;
import proposed.hvmm.core.Manager;
import proposed.hvmm.core.TrafficPowerHost;
import proposed.hvmm.core.TrafficPowerVm;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Setter
public class VmReallocationPolicyPEAM extends VmReallocationPolicyCommon {
    private final static double CRU = VmAllocationPolicyPEAP.CRU;
    private final static double UP_THR = 0.8;

    private final static int WINDOW_SIZE = 3;

    public VmReallocationPolicyPEAM(VmAllocationPolicyCommon vmAllocationPolicy) {
        super(vmAllocationPolicy);
    }

    public int getHostMaxCRUs(TrafficPowerHost host) {
        double totalMips = host.getTotalMips();
        return (int) Math.round(totalMips / CRU);
    }

    public int getVmCRUsRequest(TrafficPowerVm vm) {
        double totalMips = vm.getTotalRequestMips();
        return (int) Math.round(totalMips / CRU);
    }

    public double thresholdALIQ() {
        List<TrafficPowerHost> hostList = vmAllocator.getHostList();
        double[] hostUtilization = hostList.stream().filter(TrafficPowerHost::isActive)
                .mapToDouble(x -> x.getMovingAvgLoad(WINDOW_SIZE)).sorted().toArray();
        int q3 = (int) Math.round(0.75 * (hostUtilization.length + 1)) - 1;
        int q4 = (int) Math.round(1.0 * (hostUtilization.length));
        double sum = 0;
        for (int i = q3; i < q4; i++) {
            sum += hostUtilization[i];
        }
        return UP_THR - (sum / (q4 - q3 + 1));
    }


    @Override
    public List<Map<String, Object>> getReallocationList(List<? extends Vm> vmList) {
        Set<TrafficPowerVm> TotalMigrateVmList = new HashSet<>();
        List<TrafficPowerHost> hostList = vmAllocator.getHostList();
        double LW_THR = thresholdALIQ();
        log.debug("LW_THR:{}", LW_THR);
        for (var host : hostList.stream().filter(TrafficPowerHost::isActive)
                .collect(Collectors.toList())) {
            // case1 处理过载的服务器
            double recentLoad = host.getMovingAvgLoad(WINDOW_SIZE);
            // log.debug("recentLoad:{}：{}", host.getVmList().size(), recentLoad);
            Set<TrafficPowerVm> hostMigrateVms = new HashSet<>();
            double curLoad = recentLoad;
            while (curLoad > Manager.HOST_OVERLOAD_THR) {
                double U = host.getRealTimeWorkLoad();
                double Uopt = host.getUopt();
                double CRUs_overs = (U - Uopt) * getHostMaxCRUs(host);
                int min_ratio = Integer.MAX_VALUE;
                List<TrafficPowerVm> hostVmList = host.getVmList();
                TrafficPowerVm vmOut = null;
                for (var vm : hostVmList) {
                    if (hostMigrateVms.contains(vm) || vm.isInMigration())
                        continue;
                    int vm_cru = getVmCRUsRequest(vm);
                    int m_size = vm.getRam();
                    long bw = host.getBw();
                    int ratio = (int) (Math.abs(CRUs_overs - vm_cru) * m_size / bw);
                    if (ratio < min_ratio) {
                        min_ratio = ratio;
                        vmOut = vm;
                    }
                }
                if (vmOut == null)
                    break;
                hostMigrateVms.add(vmOut);
                curLoad = (host.getRealAllocatedMips() - Helper.getVmsRealUsedMips(hostMigrateVms))
                        / host.getTotalMips();
            }
            if (recentLoad < LW_THR) {
                // 处理欠载服务器
                List<TrafficPowerVm> vmList1 = host.getVmList();
                hostMigrateVms.addAll(vmList1.stream().filter(x -> !x.isInMigration()).collect(Collectors.toList()));
            }
            TotalMigrateVmList.addAll(hostMigrateVms);
        }
        List<Map<String, Object>> migrationList = new ArrayList<>();
        for (var vm : TotalMigrateVmList) {
            addMigrationItem(migrationList, vm, vmAllocator.getSuitableHost(vm, hostList));
        }
        return migrationList;
    }

}
