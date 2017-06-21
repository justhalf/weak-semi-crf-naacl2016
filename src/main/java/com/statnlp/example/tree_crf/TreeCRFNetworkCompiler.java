package com.statnlp.example.tree_crf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.statnlp.commons.types.Instance;
import com.statnlp.example.tree_crf.Label.LabelType;
import com.statnlp.hybridnetworks.LocalNetworkParam;
import com.statnlp.hybridnetworks.Network;
import com.statnlp.hybridnetworks.NetworkCompiler;
import com.statnlp.hybridnetworks.NetworkException;
import com.statnlp.hybridnetworks.NetworkIDMapper;

public class TreeCRFNetworkCompiler extends NetworkCompiler {

	private static final long serialVersionUID = -1057312710562009755L;
	
	public static final boolean DEBUG = false;
	
	public enum NodeType{
		SINK,
		NODE,
		ROOT,
		SUPER_ROOT,
	}
	
	public List<Label> labels;
	public Map<Label, Set<CNFRule>> rules;
	public Label rootLabel;
	public int maxSize;
	public TreeCRFNetwork genericUnlabeled;
	
	public TreeCRFNetworkCompiler(List<Label> labels, Map<Label, Set<CNFRule>> rules, Label rootLabel) {
		this.labels = labels;
		this.rules = rules;
		this.rootLabel = rootLabel;
		this.maxSize = 100;
		Collections.sort(this.labels);
		buildUnlabeled();
	}

	@Override
	public TreeCRFNetwork compile(int networkId, Instance inst, LocalNetworkParam param) {
		TreeCRFInstance instance = (TreeCRFInstance)inst;
		if(inst.isLabeled()){
			return compileLabeled(networkId, instance, param);
		} else {
			return compileUnlabeled(networkId, instance, param);
		}
	}
	
	public TreeCRFNetwork compileLabeled(int networkId, TreeCRFInstance instance, LocalNetworkParam param){
		TreeCRFNetwork network = new TreeCRFNetwork(networkId, instance, param);
		BinaryTree output = instance.output;
		int size = instance.size();
		
		long node = compileLabeled_helper(network, output, 0);
		long root = toNode_root(size-1);
		network.addNode(root);
		network.addEdge(root, new long[]{node});
		
		network.finalizeNetwork();

		if(DEBUG){
			TreeCRFNetwork unlabeled = compileUnlabeled(networkId, instance, param);
			if(!unlabeled.contains(network)){
				throw new NetworkException("Labeled network is not contained in the unlabeled version");
			}
		}
		
		return network;
	}
	
	private long compileLabeled_helper(TreeCRFNetwork network, BinaryTree output, int start){
		int height = output.getLeaves().length-1;
		int index = start;
		long node = toNode(height, index, output.value.label.id);
		network.addNode(node);
		if(output.left != null){
			int leftSize = output.left.getLeaves().length;
			long leftNode = compileLabeled_helper(network, output.left, start);
			long rightNode = compileLabeled_helper(network, output.right, start+leftSize);
			network.addEdge(node, new long[]{leftNode, rightNode});
		} else {
			long sink = toNode_sink();
			network.addNode(sink);
			network.addEdge(node, new long[]{sink});
		}
		return node;
	}
	
	public TreeCRFNetwork compileUnlabeled(int networkId, TreeCRFInstance instance, LocalNetworkParam param){
		int size = instance.size();
		long[] allNodes = genericUnlabeled.getAllNodes();
		int[][][] allChildren = genericUnlabeled.getAllChildren();

		long root = toNode_root(size-1);
		int root_k = Arrays.binarySearch(allNodes, root);
		int numNodes = root_k+1;
		
		TreeCRFNetwork network = new TreeCRFNetwork(networkId, instance, allNodes, allChildren, param, numNodes);
		return network;
	}
	
	private void buildUnlabeled(){
		System.err.print("Building generic unlabeled tree up to size "+maxSize+"...");
		long startTime = System.currentTimeMillis();
		TreeCRFNetwork network = new TreeCRFNetwork();
		int size = this.maxSize;
		
		long sink = toNode_sink();
		network.addNode(sink);
		for(int col=0; col<size; col++){
			for(int row=col; row>=0; row--){
				int height = col-row;
				int index = row;
				if(height == 0){ // Terminal
					for(Label label: labels){
						if(label.type != LabelType.TERMINAL) continue;
						int labelId = label.id;
						long leaf = toNode(height, index, labelId);
						network.addNode(leaf);
						network.addEdge(leaf, new long[]{sink});
					}
				} else { // Non-terminal
					for(Label label: labels){
						if(label.type != LabelType.NON_TERMINAL) continue;
						Set<CNFRule> curRules = rules.get(label);
						if(curRules == null) continue;
						int labelId = label.id;
						long node = toNode(height, index, labelId);
						network.addNode(node);
						
						// Add all possible children
						for(int childIdx=0; childIdx<height; childIdx++){
							int leftHeight = childIdx;
							int leftIndex = index;
							int rightHeight = height-1-childIdx;
							int rightIndex = index+1+childIdx;
							for(CNFRule rule: rules.get(label)){
								int leftLabelId = rule.firstRight.id;
								int rightLabelId = rule.secondRight.id;
								long leftNode = toNode(leftHeight, leftIndex, leftLabelId);
								if(!network.contains(leftNode)) continue;
								long rightNode = toNode(rightHeight, rightIndex, rightLabelId);
								if(!network.contains(rightNode)) continue;
								network.addEdge(node, new long[]{leftNode, rightNode});
							}
						}
					}
					if(index == 0){
						long root = toNode_root(height);
						network.addNode(root);
						for(Label label: labels){
							if(label.type != LabelType.NON_TERMINAL) continue;
							long node = toNode(height, 0, label.id);
							network.addEdge(root, new long[]{node});
						}
					}
				}
			}
		}
		// Create a super root to hold all the intermediate roots, so that they won't be removed by removeUnused
		long superRoot = toNode_superRoot(size);
		network.addNode(superRoot);
		for(int height=1; height<size; height++){
			long root = toNode_root(height);
			network.addEdge(superRoot, new long[]{root});
		}
		network.checkValidNodesAndRemoveUnused();
		network.remove_tmp(superRoot);
		network.finalizeNetwork();
		long endTime = System.currentTimeMillis();
		System.err.println(String.format("Done in %.3fs", (endTime-startTime)/1000.0));
		genericUnlabeled = network;
	}
	
	/**
	 * Create a sink node to connect all the leaves
	 * @return
	 */
	public long toNode_sink(){
		return toNode(0, 0, 0, NodeType.SINK);
	}
	
	/**
	 * Create a normal node
	 * @param height
	 * @param index
	 * @param labelId
	 * @return
	 */
	public long toNode(int height, int index, int labelId){
		return toNode(height, index, labelId, NodeType.NODE);
	}
	
	/**
	 * Create a root node to be the parent of the node possible to be the root of the tree
	 * @param height
	 * @return
	 */
	public long toNode_root(int height){
		return toNode(height, 0, labels.size(), NodeType.ROOT);
	}
	
	/**
	 * Create a super root node to temporarily hold root nodes during creation
	 * @param height
	 * @return
	 */
	public long toNode_superRoot(int height){
		return toNode(height, 0, labels.size(), NodeType.SUPER_ROOT);
	}
	
	/**
	 * Encode the node using (end_index, span_length-1, label_id, 0, node_type)
	 * @param height The height of the node. Leaf has height 0, root has height (size-1). This is equal to span_length-1
	 * @param index The index of the node in its layer. This plus height is equal to end_index
	 * @param labelId The label ID
	 * @param type The node type
	 * @return
	 */
	public long toNode(int height, int index, int labelId, NodeType type){
		int[] arr = new int[]{height+index, height, labelId, 0, type.ordinal()};
		return NetworkIDMapper.toHybridNodeID(arr);
	}

	@Override
	public TreeCRFInstance decompile(Network net) {
		TreeCRFNetwork network = (TreeCRFNetwork)net;
		TreeCRFInstance instance = (TreeCRFInstance)network.getInstance().duplicate();

		int root_k = network.countNodes()-1;
		
		BinaryTree prediction = decompile_helper(network, root_k);
		instance.setPrediction(prediction);
		
		return instance;
	}
	
	private BinaryTree decompile_helper(TreeCRFNetwork network, int parent_k){
		int[] children_k = network.getMaxPath(parent_k);
		int[] parent_arr = network.getNodeArray(parent_k);
		int height = parent_arr[1];
		int index = parent_arr[0]-height;
		int labelId = parent_arr[2];
		int nodeType = parent_arr[4];
		if(children_k.length == 0){
			System.err.println("Trying to evaluate a node without child. This case should not happen.");
			System.err.println(network);
			return null;
		} else if(children_k.length == 1){ // Either the network root node or leaf node
			int child_k = children_k[0];
			if(nodeType == NodeType.ROOT.ordinal()){
				return decompile_helper(network, child_k);
			} else { // Must be leaf node, with the child being the sink node
				TreeCRFInstance instance = (TreeCRFInstance)network.getInstance();
				BinaryTree result = new BinaryTree();
				result.value = new LabeledWord(Label.get(labelId), instance.input[index]);
				return result;
			}
		} else {
			int leftChild_k = children_k[0];
			int rightChild_k = children_k[1];
			
			BinaryTree leftTree = decompile_helper(network, leftChild_k);
			BinaryTree rightTree = decompile_helper(network, rightChild_k);
			BinaryTree result = new BinaryTree();
			result.left = leftTree;
			result.right = rightTree;
			result.value = new LabeledWord(Label.get(labelId), "");
			return result;
		}
	}

}
