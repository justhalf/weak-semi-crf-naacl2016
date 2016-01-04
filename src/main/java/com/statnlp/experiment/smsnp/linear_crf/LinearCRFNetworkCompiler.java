/** Statistical Natural Language Processing System
    Copyright (C) 2014-2015  Lu, Wei

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
package com.statnlp.experiment.smsnp.linear_crf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.statnlp.commons.types.Instance;
import com.statnlp.experiment.smsnp.SMSNPInstance;
import com.statnlp.experiment.smsnp.SMSNPNetwork;
import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;
import com.statnlp.experiment.smsnp.WordLabel;
import com.statnlp.hybridnetworks.LocalNetworkParam;
import com.statnlp.hybridnetworks.Network;
import com.statnlp.hybridnetworks.NetworkCompiler;
import com.statnlp.hybridnetworks.NetworkIDMapper;

public class LinearCRFNetworkCompiler extends NetworkCompiler{
	
	private static final long serialVersionUID = -3829680998638818730L;
	
	public static final boolean DEBUG = false;
	
	public WordLabel[] _labels;
	public enum NodeType {LEAF, NODE, ROOT};
	private static int MAX_LENGTH = 500;
	
	private transient long[] _allNodes;
	private transient int[][][] _allChildren;
	
	private TokenizerMethod tokenizerMethod;
	
	public LinearCRFNetworkCompiler(WordLabel[] labels, TokenizerMethod tokenizerMethod){
		this._labels = labels;
		this.compile_unlabled_generic();
		this.tokenizerMethod = tokenizerMethod;
	}
	
	@Override
	public SMSNPNetwork compile(int networkId, Instance instance, LocalNetworkParam param) {
		SMSNPInstance inst = (SMSNPInstance) instance;
		if(inst.isLabeled()){
			return this.compile_labeled(networkId, inst, param);
		} else {
			return this.compile_unlabeled(networkId, inst, param);
		}
		
	}
	
	
	private SMSNPNetwork compile_labeled(int networkId, SMSNPInstance inst, LocalNetworkParam param){
		SMSNPNetwork network = new SMSNPNetwork(networkId, inst, param);
		List<WordLabel> outputs = inst.getOutputTokenized();
		
		// Add leaf
		long leaf = toNode_leaf();
		network.addNode(leaf);
		
		long prevNode = leaf;
		
		for(int i=0; i<outputs.size(); i++){
			int labelId = outputs.get(i).id;
			long node = toNode(i, labelId);
			network.addNode(node);
			network.addEdge(node, new long[]{prevNode});
			prevNode = node;
		}
		
		// Add root
		long root = toNode_root(outputs.size());
		network.addNode(root);
		network.addEdge(root, new long[]{prevNode});
		
		network.finalizeNetwork();
		
		if(DEBUG){
			SMSNPNetwork unlabeled = compile_unlabeled(networkId, inst, param);
			System.out.println(inst);
			System.out.println(inst.wordSpans);
			System.out.println(outputs);
			System.out.println(network);
			System.out.println(unlabeled.contains(network));
		}
		
		return network;
	}

	private SMSNPNetwork compile_unlabeled(int networkId, SMSNPInstance inst, LocalNetworkParam param){
		if(_allNodes == null){
			compile_unlabled_generic();
		}
		int size = inst.getInputTokenized(tokenizerMethod, false, false).length;
		long root = this.toNode_root(size);
		
		int pos = Arrays.binarySearch(this._allNodes, root);
		int numNodes = pos+1; // Num nodes should equals to (instanceSize * (numLabels+1)) + 1
//		System.out.println(String.format("Instance size: %d, Labels size: %d, numNodes: %d", size, _labels.size(), numNodes));
		
		return new SMSNPNetwork(networkId, inst, this._allNodes, this._allChildren, param, numNodes);
		
	}
	
	private void compile_unlabled_generic(){
		SMSNPNetwork network = new SMSNPNetwork();
		
		long leaf = this.toNode_leaf();
		network.addNode(leaf);
		
		ArrayList<Long> prevNodes = new ArrayList<Long>();
		ArrayList<Long> currNodes = new ArrayList<Long>();
		prevNodes.add(leaf);
		
		for(int k = 0; k <MAX_LENGTH; k++){
			for(int tag_idx = 0; tag_idx < this._labels.length; tag_idx++){
				long node = this.toNode(k, _labels[tag_idx].id);
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
	
	private long toNode_leaf(){
		int[] arr = new int[]{0, 0, 0, 0, NodeType.LEAF.ordinal()};
		return NetworkIDMapper.toHybridNodeID(arr);
	}
	
	private long toNode(int pos, int tag_id){
		int[] arr = new int[]{pos+1, tag_id+1, 0, 0, NodeType.NODE.ordinal()};
		return NetworkIDMapper.toHybridNodeID(arr);
	}
	
	private long toNode_root(int size){
		int[] arr = new int[]{size, this._labels.length+1, 0, 0, NodeType.ROOT.ordinal()};
		return NetworkIDMapper.toHybridNodeID(arr);
	}

	
	@Override
	public SMSNPInstance decompile(Network network) {
		SMSNPNetwork lcrfNetwork = (SMSNPNetwork)network;
		SMSNPInstance instance = (SMSNPInstance)lcrfNetwork.getInstance();
		int size = instance.getInputTokenized().length;
		
		SMSNPInstance result = instance.duplicate();
		List<WordLabel> predictionForms = new ArrayList<WordLabel>();
		long root = toNode_root(size);
		int node_k = Arrays.binarySearch(_allNodes, root);
		
		for(int i=size-1; i>=0; i--){
			int[] children_k = lcrfNetwork.getMaxPath(node_k);
			int child_k = children_k[0];
			long child = lcrfNetwork.getNode(child_k);
			int[] child_arr = NetworkIDMapper.toHybridNodeArray(child);
			int tag_id = child_arr[1]-1;
			predictionForms.add(0, WordLabel.get(tag_id));
			node_k = child_k;
		}
		
		result.setPredictionTokenized(predictionForms);
		
		return result;
	}
	

}
