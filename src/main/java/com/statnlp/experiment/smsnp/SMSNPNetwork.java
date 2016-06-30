package com.statnlp.experiment.smsnp;

import com.statnlp.commons.types.Instance;
import com.statnlp.hybridnetworks.LocalNetworkParam;
import com.statnlp.hybridnetworks.NetworkCompiler;
import com.statnlp.hybridnetworks.TableLookupNetwork;

/**
 * The data structure to represent SMS messages with their annotations as networks/graphs<br>
 * A network represents the model view of the problem.<br>
 * Compare with {@link SMSNPInstance}, which is the real-world view of the problem<br>
 * This is based on StatNLP framework for CRF on acyclic graphs
 * @author Aldrian Obaja <aldrianobaja.m@gmail.com>
 *
 */
public class SMSNPNetwork extends TableLookupNetwork {
	
	private static final long serialVersionUID = -8384557055081197941L;
	public int numNodes = -1;

	public SMSNPNetwork() {}

	public SMSNPNetwork(int networkId, Instance inst, LocalNetworkParam param, NetworkCompiler compiler) {
		super(networkId, inst, param, compiler);
	}

	public SMSNPNetwork(int networkId, Instance inst, long[] nodes, int[][][] children, LocalNetworkParam param, int numNodes, NetworkCompiler compiler) {
		super(networkId, inst, nodes, children, param, compiler);
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
