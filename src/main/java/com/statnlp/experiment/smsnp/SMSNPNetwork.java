package com.statnlp.experiment.smsnp;

import com.statnlp.commons.types.Instance;
import com.statnlp.hybridnetworks.LocalNetworkParam;
import com.statnlp.hybridnetworks.TableLookupNetwork;

public class SMSNPNetwork extends TableLookupNetwork {
	
	private static final long serialVersionUID = -8384557055081197941L;
	public int numNodes = -1;

	public SMSNPNetwork() {}

	public SMSNPNetwork(int networkId, Instance inst, LocalNetworkParam param) {
		super(networkId, inst, param);
	}

	public SMSNPNetwork(int networkId, Instance inst, long[] nodes, int[][][] children, LocalNetworkParam param, int numNodes) {
		super(networkId, inst, nodes, children, param);
		this.numNodes = numNodes;
	}
	
	public int countNodes(){
		if(numNodes < 0){
			return super.countNodes();
		}
		return numNodes;
	}
	
	public void remove(int k){}
	
	public boolean isRemoved(int k){
		return false;
	}

}
