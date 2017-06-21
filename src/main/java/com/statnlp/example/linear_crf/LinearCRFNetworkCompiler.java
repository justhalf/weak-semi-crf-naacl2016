/** Statistical Natural Language Processing System
    Copyright (C) 2014-2016  Lu, Wei

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * 
 */
package com.statnlp.example.linear_crf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.statnlp.commons.types.Instance;
import com.statnlp.hybridnetworks.LocalNetworkParam;
import com.statnlp.hybridnetworks.Network;
import com.statnlp.hybridnetworks.NetworkCompiler;
import com.statnlp.hybridnetworks.NetworkIDMapper;

/**
 * @author wei_lu
 *
 */
public class LinearCRFNetworkCompiler extends NetworkCompiler{
	
	private static final long serialVersionUID = -3829680998638818730L;
	
	public List<Label> _labels;
	public enum NODE_TYPES {LEAF, NODE, ROOT};
	private static int MAX_LENGTH = 300;
	
	private long[] _allNodes;
	private int[][][] _allChildren;
	
	public LinearCRFNetworkCompiler(){
		this._labels = new ArrayList<Label>();
		for(Label label: Label.LABELS.values()){
			this._labels.add(new Label(label));
		}
		int i=0;
		for(Label label: _labels){
			label.setId(i);
			i++;
		}
		this.compile_unlabled_generic();
	}
	
	@Override
	public LinearCRFNetwork compile(int networkId, Instance instance, LocalNetworkParam param) {
		LinearCRFInstance inst = (LinearCRFInstance) instance;
		if(inst.isLabeled()){
			return this.compile_labeled(networkId, inst, param);
		} else {
			return this.compile_unlabeled(networkId, inst, param);
		}
		
	}
	
	
	private LinearCRFNetwork compile_unlabeled(int networkId, LinearCRFInstance inst, LocalNetworkParam param){
		int size = inst.size();
		long root = this.toNode_root(size);
		
		int pos = Arrays.binarySearch(this._allNodes, root);
		int numNodes = pos+1; // Num nodes should equals to (instanceSize * (numLabels+1)) + 1
//		System.out.println(String.format("Instance size: %d, Labels size: %d, numNodes: %d", size, _labels.size(), numNodes));
		
		return new LinearCRFNetwork(networkId, inst, this._allNodes, this._allChildren, param, numNodes, this);
		
	}
	
	private void compile_unlabled_generic(){
		LinearCRFNetwork network = new LinearCRFNetwork();
		
		long leaf = this.toNode_leaf();
		network.addNode(leaf);
		
		ArrayList<Long> prevNodes = new ArrayList<Long>();
		ArrayList<Long> currNodes = new ArrayList<Long>();
		prevNodes.add(leaf);
		
		for(int k = 0; k <MAX_LENGTH; k++){
			for(int tag_id = 0; tag_id < this._labels.size(); tag_id++){
				long node = this.toNode(k, tag_id);
				currNodes.add(node);
				network.addNode(node);
				for(long prevNode : prevNodes){
					network.addEdge(node, new long[]{prevNode});
				}
			}
			prevNodes = currNodes;
			currNodes = new ArrayList<Long>();
			
			long root = this.toNode_root(k+1);
			network.addNode(root);
			for(long prevNode : prevNodes){
				network.addEdge(root, new long[]{prevNode});
			}
			
		}
		
		network.finalizeNetwork();
		
		this._allNodes = network.getAllNodes();
		this._allChildren = network.getAllChildren();
		
	}
	
	public long toNode_leaf(){
		int[] arr = new int[]{0, 0, 0, 0, NODE_TYPES.LEAF.ordinal()};
		return NetworkIDMapper.toHybridNodeID(arr);
	}
	
	public long toNode(int pos, int tag_id){
		int[] arr = new int[]{pos+1, tag_id, 0, 0, NODE_TYPES.NODE.ordinal()};
		return NetworkIDMapper.toHybridNodeID(arr);
	}
	
	public long toNode_root(int size){
		int[] arr = new int[]{size, this._labels.size(), 0, 0, NODE_TYPES.ROOT.ordinal()};
		return NetworkIDMapper.toHybridNodeID(arr);
	}

	
	private LinearCRFNetwork compile_labeled(int networkId, LinearCRFInstance inst, LocalNetworkParam param){
		LinearCRFNetwork network = new LinearCRFNetwork(networkId, inst, param, this);
		ArrayList<Label> outputs = inst.getOutput();
		
		// Add leaf
		long leaf = toNode_leaf();
		network.addNode(leaf);
		
		long prevNode = leaf;
		
		for(int i=0; i<outputs.size(); i++){
			Label label = outputs.get(i);
			long node = toNode(i, label.getId());
			network.addNode(node);
			network.addEdge(node, new long[]{prevNode});
			prevNode = node;
		}
		
		// Add root
		long root = toNode_root(outputs.size());
		network.addNode(root);
		network.addEdge(root, new long[]{prevNode});
		
		network.finalizeNetwork();
		
		return network;
	}
	
	@Override
	public LinearCRFInstance decompile(Network network) {
		LinearCRFNetwork lcrfNetwork = (LinearCRFNetwork)network;
		LinearCRFInstance instance = (LinearCRFInstance)lcrfNetwork.getInstance();
		int size = instance.size();
		
		LinearCRFInstance result = instance.duplicate();
		ArrayList<Label> predictions = new ArrayList<Label>();
		long root = toNode_root(size);
		int node_k = Arrays.binarySearch(_allNodes, root);
		
		for(int i=size-1; i>=0; i--){
			int[] children_k = lcrfNetwork.getMaxPath(node_k);
			if(children_k.length != 1){
				System.err.println("Child length not 1!");
			}
			int child_k = children_k[0];
			long child = lcrfNetwork.getNode(child_k);
			int[] child_arr = NetworkIDMapper.toHybridNodeArray(child);
			int pos = child_arr[0]-1;
			int tag_id = child_arr[1];
			if(pos != i){
				System.err.println("Position encoded in the node array not the same as the interpretation!");
			}
			predictions.add(0, Label.get(tag_id));
			node_k = child_k;
		}
		
		result.setPrediction(predictions);
		
		return result;
	}
	
	public double costAt(Network network, int parent_k, int[] child_k){
		return super.costAt(network, parent_k, child_k);
	}

}
