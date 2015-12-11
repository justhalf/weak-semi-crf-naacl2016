package com.statnlp.experiment.smsnp.semi_crf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.statnlp.commons.types.Instance;
import com.statnlp.experiment.smsnp.SpanLabel;
import com.statnlp.experiment.smsnp.SMSNPInstance;
import com.statnlp.experiment.smsnp.SMSNPNetwork;
import com.statnlp.experiment.smsnp.Span;
import com.statnlp.hybridnetworks.LocalNetworkParam;
import com.statnlp.hybridnetworks.Network;
import com.statnlp.hybridnetworks.NetworkCompiler;
import com.statnlp.hybridnetworks.NetworkException;
import com.statnlp.hybridnetworks.NetworkIDMapper;

public class SemiCRFNetworkCompiler extends NetworkCompiler {
	
	private final static boolean DEBUG = false;
	
	private static final long serialVersionUID = 6585870230920484539L;
	public SpanLabel[] labels;
	public int maxLength = 500;
	public int maxSegmentLength = 20;
	public long[] allNodes;
	public int[][][] allChildren;
	
	public enum NodeType {
		LEAF,
		INNER,
		ROOT,
	}
	
	static {
		NetworkIDMapper.setCapacity(new int[]{10000, 10, 100});
	}

	public SemiCRFNetworkCompiler(SpanLabel[] labels, int maxLength, int maxSegmentLength) {
		this.labels = labels;
		this.maxLength = Math.max(maxLength, this.maxLength);
		this.maxSegmentLength = Math.max(maxSegmentLength, this.maxSegmentLength);
		System.out.println(String.format("Max size: %s, Max segment length: %s", maxLength, maxSegmentLength));
		System.out.println(Arrays.asList(labels));
		buildUnlabeled();
	}

	@Override
	public SMSNPNetwork compile(int networkId, Instance inst, LocalNetworkParam param) {
		try{
			if(inst.isLabeled()){
				return compileLabeled(networkId, (SMSNPInstance)inst, param);
			} else {
				return compileUnlabeled(networkId, (SMSNPInstance)inst, param);
			}
		} catch (NetworkException e){
			System.out.println(inst);
			throw e;
		}
	}
	
	private SMSNPNetwork compileLabeled(int networkId, SMSNPInstance instance, LocalNetworkParam param){
		SMSNPNetwork network = new SMSNPNetwork(networkId, instance, param);
		
		int size = instance.size();
		List<Span> output = instance.getOutput();
		Collections.sort(output);
		
		long leaf = toNode_leaf();
		network.addNode(leaf);
		long prevNode = leaf;
		for(Span span: output){
			int labelId = span.label.id;
			long node = toNode_inner(span.end-1, labelId);
			network.addNode(node);
			network.addEdge(node, new long[]{prevNode});
			prevNode = node;
		}
		long root = toNode_root(size-1);
		network.addNode(root);
		network.addEdge(root, new long[]{prevNode});
		
		network.finalizeNetwork();
		
		if(DEBUG){
			System.out.println(network);
			SMSNPNetwork unlabeled = compileUnlabeled(networkId, instance, param);
			System.out.println("Contained: "+unlabeled.contains(network));
		}
		return network;
	}
	
	private SMSNPNetwork compileUnlabeled(int networkId, SMSNPInstance instance, LocalNetworkParam param){
		int size = instance.size();
		long root = toNode_root(size-1);
		int root_k = Arrays.binarySearch(allNodes, root);
		int numNodes = root_k + 1;
		return new SMSNPNetwork(networkId, instance, allNodes, allChildren, param, numNodes);
	}
	
	private void buildUnlabeled(){
		SMSNPNetwork network = new SMSNPNetwork();
		
		long leaf = toNode_leaf();
		network.addNode(leaf);
		List<Long> currNodes = new ArrayList<Long>();
		for(int pos=0; pos<maxLength; pos++){
			for(int labelId=0; labelId<labels.length; labelId++){
				long node = toNode_inner(pos, labelId);
				
				network.addNode(node);
				
				currNodes.add(node);
				
				for(int prevPos=pos-1; prevPos > pos-maxSegmentLength && prevPos >= -1; prevPos--){
					if(prevPos == -1){
						network.addEdge(node, new long[]{leaf});
						continue;
					}
					for(int prevLabelId=0; prevLabelId<labels.length; prevLabelId++){
						long prevNode = toNode_inner(prevPos, prevLabelId);
						network.addEdge(node, new long[]{prevNode});
					}
				}
			}
			long root = toNode_root(pos);
			network.addNode(root);
			for(long currNode: currNodes){
				network.addEdge(root, new long[]{currNode});	
			}
			currNodes.clear();
		}
		network.finalizeNetwork();
		allNodes = network.getAllNodes();
		allChildren = network.getAllChildren();
	}
	
	private long toNode_leaf(){
		return toNode(0, 0, NodeType.LEAF);
	}
	
	private long toNode_inner(int pos, int labelId){
		return toNode(pos, labelId, NodeType.INNER);
	}
	
	private long toNode_root(int pos){
		return toNode(pos, labels.length, NodeType.ROOT);
	}
	
	private long toNode(int pos, int labelId, NodeType type){
		return NetworkIDMapper.toHybridNodeID(new int[]{pos, type.ordinal(), labelId});
	}

	@Override
	public SMSNPInstance decompile(Network net) {
		SMSNPNetwork network = (SMSNPNetwork)net;
		SMSNPInstance result = (SMSNPInstance)network.getInstance().duplicate();
		List<Span> prediction = new ArrayList<Span>();
		int node_k = network.countNodes()-1;
		NodeType parentNodeType = NodeType.ROOT;
		int labelId = -1;
		int curEnd = result.size();
		while(node_k > 0){
			int[] children_k = network.getMaxPath(node_k);
			node_k = children_k[0];
			int[] child_arr = network.getNodeArray(node_k);
			int prevEnd = child_arr[0];
			NodeType childNodeType = NodeType.values()[child_arr[1]];
			if(childNodeType != NodeType.LEAF){
				prevEnd += 1;
			}
			if(parentNodeType != NodeType.ROOT){
				prediction.add(new Span(prevEnd, curEnd, SpanLabel.get(labelId)));
			}
			
			// Set variables for next iteration
			if(childNodeType == NodeType.LEAF){
				break;
			}
			curEnd = prevEnd;
			labelId = child_arr[2];
			parentNodeType = childNodeType;
		}
		Collections.sort(prediction);
		result.setPrediction(prediction);
		return result;
	}

}
