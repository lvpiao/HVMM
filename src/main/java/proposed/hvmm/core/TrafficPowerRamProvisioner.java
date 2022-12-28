package proposed.hvmm.core;


import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;

public class TrafficPowerRamProvisioner extends RamProvisioner {

    public TrafficPowerRamProvisioner(int availableRam) {
        super(availableRam);
    }

    @Override
    public boolean allocateRamForVm(Vm vm, int ram) {
        if (getAvailableRam() >= ram) {
            setAvailableRam(getAvailableRam() - ram);
            return true;
        }
        return false;
    }

    @Override
    public int getAllocatedRamForVm(Vm vm) {
        return vm.getRam();
    }

    @Override
    public void deallocateRamForVm(Vm vm) {
        setAvailableRam(getAvailableRam() + vm.getRam());
    }

    @Override
    public boolean isSuitableForVm(Vm vm, int ram) {
        return getAvailableRam() >= ram;
    }
}
