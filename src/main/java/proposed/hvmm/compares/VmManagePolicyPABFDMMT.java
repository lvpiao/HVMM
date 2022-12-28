package proposed.hvmm.compares;

import org.cloudbus.cloudsim.Host;

import proposed.hvmm.compares.allocation.VmAllocationPolicyPABFD;
import proposed.hvmm.compares.reallocation.VmReallocationPolicyMMT;

import java.util.*;

public class VmManagePolicyPABFDMMT extends VmAllocationPolicyPABFD {


    public VmManagePolicyPABFDMMT(List<? extends Host> list) {
        super(list);
        vmReallocator = new VmReallocationPolicyMMT(this);
    }


}
