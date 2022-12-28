package proposed.hvmm.compares;

import proposed.hvmm.compares.allocation.VmAllocationPolicyPEAP;
import proposed.hvmm.compares.reallocation.VmReallocationPolicyPEAM;

import org.cloudbus.cloudsim.Host;

import java.util.*;

public class VmManagePolicyPEAS extends VmAllocationPolicyPEAP {


    public VmManagePolicyPEAS(List<? extends Host> list) {
        super(list);
        vmReallocator = new VmReallocationPolicyPEAM(this);
    }


}
