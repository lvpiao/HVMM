package proposed.hvmm.core;

import java.util.List;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;


public class TrafficPowerVmScheduler extends VmSchedulerTimeShared {

    public TrafficPowerVmScheduler(List<? extends Pe> peList) {
        super(peList);
    }

    public boolean canAddVmForPes(TrafficPowerVm vm, double migratingVmUsedTotalMips) {
        if (getPeCapacity() < vm.getMips()) {
            return false;
        }
        return getAvailableMips() >= vm.getTotalRequestMips() + migratingVmUsedTotalMips;
    }

    @Override
    public boolean allocatePesForVm(Vm vm, List<Double> mipsShareRequested) {
        boolean result = super.allocatePesForVm(vm, mipsShareRequested);
        // log.debug("成功为虚拟机#{}分配资源:{}mips", vm.getUid(),
        // getTotalAllocatedMipsForVm(vm));
        return result;
    }
}
