package proposed.hvmm.compares;

import java.util.List;
import org.cloudbus.cloudsim.Host;
import proposed.hvmm.compares.allocation.VmAllocationPolicyHVMM;
import proposed.hvmm.compares.reallocation.VmReallocationPolicyHVMM;

public class VmManagePolicyHVMM extends VmAllocationPolicyHVMM {


    public VmManagePolicyHVMM(List<? extends Host> hostList) {
        super(hostList);
        vmReallocator = new VmReallocationPolicyHVMM(this);
    }

}
