package proposed.hvmm.compares;

import org.cloudbus.cloudsim.Host;

import proposed.hvmm.compares.allocation.VmAllocationPolicyFFD;
import proposed.hvmm.compares.reallocation.VmReallocationPolicyMMT;

import java.util.*;


public class VmManagePolicyFFDMMT extends VmAllocationPolicyFFD {


    public VmManagePolicyFFDMMT(List<? extends Host> list) {
        super(list);
        vmReallocator = new VmReallocationPolicyMMT(this);
    }
    

}
