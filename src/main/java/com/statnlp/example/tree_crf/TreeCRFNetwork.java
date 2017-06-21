package com.statnlp.example.tree_crf;

import com.statnlp.commons.types.Instance;
import com.statnlp.hybridnetworks.LocalNetworkParam;
import com.statnlp.hybridnetworks.TableLookupNetwork;

public class TreeCRFNetwork extends TableLookupNetwork {

	private static final long serialVersionUID = 9152934404818336553L;
	
	public int numNodes = -1;

	public TreeCRFNetwork() {
	}

	public TreeCRFNetwork(int networkId, Instance inst, LocalNetworkParam param) {
		super(networkId, inst, param);
	}
	
	public TreeCRFNetwork(int networkId, Instance inst, long[] allNodes, int[][][] allChildren, LocalNetworkParam param, int numNodes){
		super(networkId, inst, allNodes, allChildren, param);
		this.numNodes = numNodes;
	}
	
	public int countNodes(){
		if(numNodes == -1){
			return super.countNodes();
		}
		return numNodes;
	}

	@Override
	public boolean isRemoved(int k) {
		return false;
	}

	@Override
	public void remove(int k) {}

}
