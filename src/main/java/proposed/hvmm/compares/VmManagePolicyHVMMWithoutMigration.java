package proposed.hvmm.compares;

import java.util.List;
import org.cloudbus.cloudsim.Host;
import proposed.hvmm.compares.allocation.VmAllocationPolicyHVMM;

public class VmManagePolicyHVMMWithoutMigration extends VmAllocationPolicyHVMM {


    public VmManagePolicyHVMMWithoutMigration(List<? extends Host> hostList) {
        super(hostList);
    }

}
