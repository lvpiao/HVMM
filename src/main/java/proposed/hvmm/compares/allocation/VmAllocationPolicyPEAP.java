package proposed.hvmm.compares.allocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;

import lombok.Setter;
import proposed.hvmm.core.TrafficPowerHost;
import proposed.hvmm.core.TrafficPowerVm;

@Setter
public class VmAllocationPolicyPEAP extends VmAllocationPolicyCommon {
    public final Map<Integer, List<TrafficPowerHost>> optimalOfferList;
    public final static double CRU = 1000;

    public VmAllocationPolicyPEAP(List<? extends Host> list) {
        super(list);
        optimalOfferList = new HashMap<>();
    }

    public int getHostCRUsOffer(TrafficPowerHost host) {
        double totalMips = host.getTotalMips();
        double u = host.getCpuUtilization();
        double Uopt = host.getUopt();
        return (int) Math.round(totalMips / CRU * Math.max(Uopt - u, 0f));
    }

    public int getHostCRUsOfferAfterPlaceVm(TrafficPowerHost host, TrafficPowerVm vm) {
        double totalMips = host.getTotalMips();
        double u = host.getCpuUtilizationAfterPlaceVm(vm);
        double Uopt = host.getUopt();
        return (int) Math.round(totalMips / CRU * Math.max(Uopt - u, 0f));
    }

    public int getVmCRUsRequest(TrafficPowerVm vm) {
        double totalMips = vm.getTotalRequestMips();
        return (int) Math.round(totalMips / CRU);
    }

    public void buildLists() {
        List<TrafficPowerHost> hostList = getHostList();
        // 按照最高能量效率降序排序
        hostList.sort((h1, h2) -> {
            // 过载的放在最后
            if (h1.isOverLoad() || h2.isOverLoad()) {
                return Double.compare(h1.getRealTimeWorkLoad(), h2.getRealTimeWorkLoad());
            }
            return Double.compare(h2.getPeakPeff(), h1.getPeakPeff());
        });
        // 生成最优offer CRU列表
        optimalOfferList.clear();
        for (var host : hostList) {
            int CRU_offer = getHostCRUsOffer(host);
            if (!optimalOfferList.containsKey(CRU_offer))
                optimalOfferList.put(CRU_offer, new ArrayList<>());
            optimalOfferList.get(CRU_offer).add(host);
        }
    }

    public TrafficPowerHost getSuitableHost(TrafficPowerVm vm, List<TrafficPowerHost> hostList) {
        int r = getVmCRUsRequest(vm);
        // case1
        if (optimalOfferList.containsKey(r)) {
            for (var host : optimalOfferList.get(r)) {
                if (host.isSuitableForVm(vm)) {
                    optimalOfferList.get(r).remove(host);
                    int CRUs_offer_new = getHostCRUsOfferAfterPlaceVm(host, vm);
                    if (!optimalOfferList.containsKey(CRUs_offer_new))
                        optimalOfferList.put(CRUs_offer_new, new ArrayList<>());
                    optimalOfferList.get(CRUs_offer_new).add(host);
                    return host;
                }
            }
        }
        // case2
        for (var host : hostList) {
            if (host.isIdle())
                continue;
            if (host.isSuitableForVm(vm)) {
                int CRUs_offer_old = getHostCRUsOffer(host);
                int CRUs_offer_new = getHostCRUsOfferAfterPlaceVm(host, vm);
                // 更新optimalOfferList
                if (optimalOfferList.containsKey(CRUs_offer_old))
                    optimalOfferList.get(CRUs_offer_old).remove(host);
                if (!optimalOfferList.containsKey(CRUs_offer_new))
                    optimalOfferList.put(CRUs_offer_new, new ArrayList<>());
                optimalOfferList.get(CRUs_offer_new).add(host);
                return host;
            }
        }
        // case3
        for (var host : hostList) {
            if (host.isActive())
                continue;
            if (host.isSuitableForVm(vm)) {
                int CRUs_offer = getHostCRUsOfferAfterPlaceVm(host, vm);
                if (!optimalOfferList.containsKey(CRUs_offer))
                    optimalOfferList.put(CRUs_offer, new ArrayList<>());
                optimalOfferList.get(CRUs_offer).add(host);
                return host;
            }
        }
        return null;
    }

    @Override
    public boolean allocateHostForVm(Vm vm) {
        buildLists();
        return super.allocateHostForVm(vm);
    }

}
