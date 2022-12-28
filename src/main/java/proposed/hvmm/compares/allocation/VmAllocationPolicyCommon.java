package proposed.hvmm.compares.allocation;

import lombok.extern.slf4j.Slf4j;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import proposed.hvmm.compares.reallocation.VmReallocationPolicyCommon;
import proposed.hvmm.core.Manager;
import proposed.hvmm.core.TrafficPowerHost;
import proposed.hvmm.core.TrafficPowerVm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class VmAllocationPolicyCommon extends VmAllocationPolicy {
    protected final Map<String, Host> vmToHostMap;
    protected VmReallocationPolicyCommon vmReallocator = null;

    public VmAllocationPolicyCommon(List<? extends Host> list) {
        super(list);
        vmToHostMap = new HashMap<>();
    }

    // 子类实现这个方法，从hostList中选择一个合适的host，并将vm加入到这个host中
    public abstract TrafficPowerHost getSuitableHost(TrafficPowerVm vm,
            List<TrafficPowerHost> hostList);

    @Override
    public boolean allocateHostForVm(Vm vm) {
        Host host = getSuitableHost((TrafficPowerVm) vm, getHostList());
        if (host == null) {
            throw new RuntimeException("无法找到合适的服务器部署该虚拟机:" + getHostList().size() + " "
                    + vm.getCurrentRequestedMips());
        }
        return allocateHostForVm(vm, host);
    }

    @Override
    public boolean allocateHostForVm(Vm vm, Host host) {
        if (host.vmCreate(vm)) {
            vmToHostMap.put(vm.getUid(), host);
            return true;
        } else {
            log.debug("{}: Creation of VM #{} on the host #{} failed\n", CloudSim.clock(),
                    vm.getId(), host.getId());
            System.exit(0);
        }
        return false;
    }

    @Override
    public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) {
        if (vmReallocator == null) {
            return null;
        }
        return vmReallocator.doReallocation(vmList);
    }

    @Override
    public Host getHost(Vm vm) {
        return vmToHostMap.get(vm.getUid());
    }

    @Override
    public Host getHost(int vmId, int userId) {
        return vmToHostMap.get(Vm.getUid(userId, vmId));
    }

    @Override
    public void deallocateHostForVm(Vm vm) {
        Host host = vmToHostMap.remove(vm.getUid());
        if (host != null) {
            host.vmDestroy(vm);
        }
    }

    public VmReallocationPolicyCommon getVmReallocator() {
        return vmReallocator;
    }

    public boolean isMigrateAble() {
        return vmReallocator != null;
    }

    public boolean overloadCheck(TrafficPowerHost host, TrafficPowerVm vm) {
        double hostUsedMips = host.getRealAllocatedMips();
        double hostTotalMips = host.getTotalMips();
        double vmRealTimeMips = vm.getRealUsedMips();
        return (hostUsedMips + vmRealTimeMips) <= Manager.HOST_OVERLOAD_THR * hostTotalMips;
    }
}
