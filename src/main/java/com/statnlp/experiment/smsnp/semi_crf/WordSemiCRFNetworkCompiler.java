package com.statnlp.experiment.smsnp.semi_crf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.statnlp.commons.types.Instance;
import com.statnlp.experiment.smsnp.SMSNPInstance;
import com.statnlp.experiment.smsnp.SMSNPNetwork;
import com.statnlp.experiment.smsnp.SpanLabel;
import com.statnlp.experiment.smsnp.WordLabel;
import com.statnlp.hybridnetworks.LocalNetworkParam;
import com.statnlp.hybridnetworks.Network;
import com.statnlp.hybridnetworks.NetworkCompiler;
import com.statnlp.hybridnetworks.NetworkException;
import com.statnlp.hybridnetworks.NetworkIDMapper;

import edu.stanford.nlp.util.StringUtils;

/**
 * The class that defines the network/graph structure<br>
 * This is based on StatNLP framework for CRF on acyclic graphs
 * @author Aldrian Obaja <aldrianobaja.m@gmail.com>
 *
 */
public class WordSemiCRFNetworkCompiler extends NetworkCompiler {
	
	private final static boolean DEBUG = false;
	
	private static final long serialVersionUID = 6585870230920484539L;
	public SpanLabel[] labels;
	public int maxLength = 20;
	public int maxSegmentLength = 1;
	public transient long[] allNodes;
	public transient int[][][] allChildren;
	
	public enum NodeType {
		LEAF,
		ROOT,
		INNER,
	}
	
	static {
		NetworkIDMapper.setCapacity(new int[]{10000, 10, 100});
	}

	public WordSemiCRFNetworkCompiler(SpanLabel[] labels, int maxLength, int maxSegmentLength) {
		this.labels = labels;
		this.maxLength = Math.max(maxLength, this.maxLength);
		this.maxSegmentLength = Math.max(maxSegmentLength, this.maxSegmentLength);
		System.out.println(String.format("Max size: %s, Max segment length: %s", maxLength, maxSegmentLength));
		System.out.println("Labels: "+Arrays.asList(labels));
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
	
	private SpanLabel wordLabelToSpanLabel(WordLabel label){
		String form = label.form;
		if(form.startsWith("O")){
			return SpanLabel.get("O");
		} else {
			return SpanLabel.get(form.substring(form.indexOf("-")+1));
		}
	}
	
	private SMSNPNetwork compileLabeled(int networkId, SMSNPInstance instance, LocalNetworkParam param){
		SMSNPNetwork network = new SMSNPNetwork(networkId, instance, param, this);
		
		List<WordLabel> output = instance.getOutputTokenized();
		int size = output.size();
		
		long leaf = toNode_leaf();
		network.addNode(leaf);
		long prevNode = leaf;
		int prevPos = 0;
		for(int pos=0; pos<size; pos++){
			WordLabel label = output.get(pos);
			SpanLabel spanLabel = wordLabelToSpanLabel(label);
			WordLabel nextLabel = pos+1 < size ? output.get(pos+1) : null;
			if(nextLabel == null || nextLabel.form.startsWith("B") || nextLabel.form.startsWith("O") || maxSegmentLength == 1){
				if(pos-prevPos > maxSegmentLength){
					throw new IndexOutOfBoundsException(String.format("\n"
							+ "The segment %s of type %s is longer than max segment length %d",
							StringUtils.join(Arrays.copyOfRange(instance.getInputTokenized(), prevPos+1, pos), " "),
							label,
							maxSegmentLength));
				}
				long node = toNode_inner(pos, spanLabel.id);
				network.addNode(node);
				network.addEdge(node, new long[]{prevNode});
				prevNode = node;
				prevPos = pos;
			}
		}
		long root = toNode_root(size);
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
		if(allNodes == null){
			buildUnlabeled();
		}
		int size = instance.getInputTokenized().length;
		long root = toNode_root(size);
		int root_k = Arrays.binarySearch(allNodes, root);
		int numNodes = root_k + 1;
		return new SMSNPNetwork(networkId, instance, allNodes, allChildren, param, numNodes, this);
	}
	
	private void buildUnlabeled(){
		SMSNPNetwork network = new SMSNPNetwork();
		
		long leaf = toNode_leaf();
		network.addNode(leaf);
		for(int pos=0; pos<maxLength; /* pos is incremented when we see root */){
			for(int labelIdx=0; labelIdx <= labels.length; labelIdx++){
				long node;
				if(labelIdx < labels.length){
					node = toNode_inner(pos, labels[labelIdx].id);
				} else {
					pos += 1;
					node = toNode_root(pos);
				}
				
				network.addNode(node);
				
				for(int prevPos=pos-1; prevPos >= pos-maxSegmentLength && prevPos >= -1; prevPos--){
					if(prevPos == -1){
						network.addEdge(node, new long[]{leaf});
					} else {
						for(int prevLabelIdx=0; prevLabelIdx<labels.length; prevLabelIdx++){
							long prevNode = toNode_inner(prevPos, labels[prevLabelIdx].id);
							network.addEdge(node, new long[]{prevNode});
						}
					}
					if(labelIdx == labels.length){ // Is root, connect just to last layer, so break now
						break;
					}
				}
			}
		}
		network.finalizeNetwork();
		allNodes = network.getAllNodes();
		allChildren = network.getAllChildren();
	}
	
	private long toNode_leaf(){
		return toNode(0, 0, NodeType.LEAF);
	}
	
	private long toNode_root(int pos){
		return toNode(pos+1, 0, NodeType.ROOT);
	}
	
	private long toNode_inner(int pos, int labelId){
		return toNode(pos+1, labelId+1, NodeType.INNER);
	}
	
	private long toNode(int pos, int labelId, NodeType type){
		return NetworkIDMapper.toHybridNodeID(new int[]{pos, type.ordinal(), labelId});
	}

	@Override
	public SMSNPInstance decompile(Network net) {
		SMSNPNetwork network = (SMSNPNetwork)net;
		SMSNPInstance result = (SMSNPInstance)network.getInstance().duplicate();
		int size = result.getInputTokenized().length;
		List<WordLabel> predictionTokenized = new ArrayList<WordLabel>();
		int node_k = network.countNodes()-1;
		int labelId = -1;
		int end = size;
		while(node_k > 0){
			int[] children_k = network.getMaxPath(node_k);
			node_k = children_k[0];
			int[] child_arr = network.getNodeArray(node_k);
			int start = child_arr[0]-1;
			for(int pos=end-1; pos>=start+1; pos--){
				WordLabel wordLabel;
				String form = SpanLabel.get(labelId).form;
				if(form.startsWith("O")){
					wordLabel = WordLabel.get("O");
				} else {
					if(pos > start+1){
						wordLabel = WordLabel.get("I-"+form);
					} else {
						wordLabel = WordLabel.get("B-"+form);
					}
				}
				predictionTokenized.add(0, wordLabel);
			}
			labelId = child_arr[2]-1;
			end = start+1;
		}
		result.setPredictionTokenized(predictionTokenized);
		return result;
	}

}
