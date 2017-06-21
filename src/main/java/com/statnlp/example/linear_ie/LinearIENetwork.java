package com.statnlp.example.linear_ie;

import com.statnlp.commons.types.Instance;
import com.statnlp.hybridnetworks.LocalNetworkParam;
import com.statnlp.hybridnetworks.TableLookupNetwork;

public class LinearIENetwork extends TableLookupNetwork {

	private static final long serialVersionUID = 7173683038115335356L;
	
	public int numNodes = -1;

	public LinearIENetwork() {}

	public LinearIENetwork(int networkId, Instance inst, LocalNetworkParam param) {
		super(networkId, inst, param);
	}

	public LinearIENetwork(int networkId, Instance inst, long[] nodes, int[][][] children, LocalNetworkParam param, int numNodes) {
		super(networkId, inst, nodes, children, param);
		this.numNodes = numNodes;
	}
	
	public int countNodes(){
		if(numNodes < 0){
			return super.countNodes();
		}
		return numNodes;
	}

}
