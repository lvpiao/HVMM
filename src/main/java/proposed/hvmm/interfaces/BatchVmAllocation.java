package proposed.hvmm.interfaces;

import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * @author PiAo
 * @date 2021/12/12
 */
public interface BatchVmAllocation {
    void allocateVmList(List<Vm> vmList);
}
