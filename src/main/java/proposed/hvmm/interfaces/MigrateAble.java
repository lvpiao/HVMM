package proposed.hvmm.interfaces;

import org.cloudbus.cloudsim.Vm;
import proposed.hvmm.core.TrafficPowerHost;
import proposed.hvmm.core.TrafficPowerVm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface MigrateAble {
    int getMigrationCount();

    //该方法中实现具体的VM迁移策略
    List<Map<String, Object>> getReallocationList(List<? extends Vm> vmList);

    //实现该接口默认可以迁移
    default boolean isMigrateAble() {
        return true;
    }
    
    default void addMigrationItem(List<Map<String, Object>> migrationList, TrafficPowerVm vm, TrafficPowerHost destHost) {
        if (destHost == null || destHost.getId() == vm.getHost().getId()) {
            return;
        }
        Map<String, Object> migrateItem = new HashMap<>();
        migrateItem.put("vm", vm);
        migrateItem.put("host", destHost);
        destHost.addMigratingInVm(vm);
        migrationList.add(migrateItem);
    }
}
