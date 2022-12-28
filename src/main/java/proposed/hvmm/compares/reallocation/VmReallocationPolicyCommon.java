package proposed.hvmm.compares.reallocation;

import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

import proposed.hvmm.compares.allocation.VmAllocationPolicyCommon;
import proposed.hvmm.core.Manager;
import proposed.hvmm.core.TrafficPowerVm;
import proposed.hvmm.interfaces.MigrateAble;

public abstract class VmReallocationPolicyCommon implements MigrateAble {
    private double lastMigrationTime = 0;
    public VmAllocationPolicyCommon vmAllocator;
    private int migrationCount = 0;
    private long migrationRamSize = 0;
    public int underloadWinSize = 6;

    public VmReallocationPolicyCommon(VmAllocationPolicyCommon vmAllocator) {
        this.vmAllocator = vmAllocator;
    }

    public List<Map<String, Object>> doReallocation(List<? extends Vm> vmList) {
        if (CloudSim.clock() < lastMigrationTime + Manager.MIGRATION_INTERVAL) {
            return null;
        }
        // 进行迁移
        List<Map<String, Object>> migrationList = getReallocationList(vmList);
        // 更新信息
        lastMigrationTime = CloudSim.clock();
        migrationCount += migrationList.size();
        migrationRamSize += migrationList.stream().mapToInt(m -> ((TrafficPowerVm) m.get("vm")).getRam()).sum();
        // log.debug("{}:将要迁移的虚拟机的数量为：{}", CloudSim.clock(), migrationList.size());
        return migrationList;
    }

    public int getMigrationCount() {
        return migrationCount;
    }

    public long getMigrationRamSize() {
        return migrationRamSize;
    }

}
