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
package com.statnlp.hybridnetworks;

import java.io.Serializable;
import java.util.Arrays;

import com.statnlp.commons.types.Instance;
import com.statnlp.hybridnetworks.NetworkConfig.ModelType;

/**
 * The base class for representing networks. This class is equipped with algorithm to calculate the 
 * inside-outside score, which is also a generalization to the forward-backward score.<br>
 * You might want to use {@link TableLookupNetwork} for more functions such as adding nodes and edges.
 * @see TableLookupNetwork
 * @author Wei Lu <luwei@statnlp.com>
 *
 */
public abstract class Network implements Serializable, HyperGraph{
	
	public static enum NODE_TYPE {sum, max};
	
	private static final long serialVersionUID = -3630379919120581209L;
	
	/** The working array for each thread for calculating inside scores */
	protected static double[][] insideSharedArray = new double[NetworkConfig._numThreads][];
	/** The working array for each thread for calculating outside scores */
	protected static double[][] outsideSharedArray = new double[NetworkConfig._numThreads][];
	/** The working array for each thread for calculating max scores */
	protected static double[][] maxSharedArray = new double[NetworkConfig._numThreads][];
	/** The working array for each thread for calculating cost */
	protected static double[][] costSharedArray = new double[NetworkConfig._numThreads][];
	/** The working array for each thread for storing max paths (for backtracking) */
	protected static int[][][] maxPathsSharedArrays = new int[NetworkConfig._numThreads][][];
	
	/** The IDs associated with the network (within the scope of the thread). */
	protected int _networkId;
	/** The id of the thread */
	protected int _threadId;
	/** The instance */
	protected transient Instance _inst;
	/** The weight */
	protected transient double _weight;
	/** The feature parameters */
	protected transient LocalNetworkParam _param;
	
	/** At each index, store the node's inside score */
	protected transient double[] _inside;
	/** At each index, store the node's outside score */
	protected transient double[] _outside;
	/** At each index, store the score of the max tree */
	protected transient double[] _max;
	/** Stores the paths associated with the above tree */
	protected transient int[][] _max_paths;
	/** To mark whether a node has been visited in one iteration */
	protected transient boolean[] _visited;
	
	/** The compiler that created this network */
	protected NetworkCompiler _compiler;
	/** The labeled version of this network, if exists, null otherwise */
	private Network _labeledNetwork;
	/** The unlabeled version of this network, if exists, null otherwise */
	private Network _unlabeledNetwork;
	
	/**
	 * Default constructor. Note that the network constructed using this default constructor is lacking 
	 * the {@link LocalNetworkParam} object required for actual use.
	 * Use this only for generating generic network, which is later actualized using another constructor.
	 * @see #Network(int, Instance, LocalNetworkParam)
	 */
	public Network(){}
	
	/**
	 * Construct a network
	 * @param networkId
	 * @param inst
	 * @param param
	 */
	public Network(int networkId, Instance inst, LocalNetworkParam param){
		this(networkId, inst, param, null);
	}
	
	/**
	 * Construct a network, specifying the NetworkCompiler that created this network
	 * @param networkId
	 * @param inst
	 * @param param
	 * @param compiler
	 */
	public Network(int networkId, Instance inst, LocalNetworkParam param, NetworkCompiler compiler){
		this._networkId = networkId;
		this._threadId = param.getThreadId();
		this._inst = inst;
		this._weight = this._inst.getWeight();
		this._param = param;
		this._compiler = compiler;
	}
	
	protected double[] getInsideSharedArray(){
		if(insideSharedArray[this._threadId] == null || this.countNodes() > insideSharedArray[this._threadId].length)
			insideSharedArray[this._threadId] = new double[this.countNodes()];
		return insideSharedArray[this._threadId];
	}
	
	protected double[] getOutsideSharedArray(){
		if(outsideSharedArray[this._threadId] == null || this.countNodes() > outsideSharedArray[this._threadId].length)
			outsideSharedArray[this._threadId] = new double[this.countNodes()];
		return outsideSharedArray[this._threadId];
	}

	protected double[] getMaxSharedArray(){
		if(maxSharedArray[this._threadId] == null || this.countNodes() > maxSharedArray[this._threadId].length)
			maxSharedArray[this._threadId] = new double[this.countNodes()];
		return maxSharedArray[this._threadId];
	}

	protected double[] getCostSharedArray(){
		if(costSharedArray[this._threadId] == null || this.countNodes() > costSharedArray[this._threadId].length)
			costSharedArray[this._threadId] = new double[this.countNodes()];
		return costSharedArray[this._threadId];
	}

	protected int[][] getMaxPathSharedArray(){
		if(maxPathsSharedArrays[this._threadId] == null || this.countNodes() > maxPathsSharedArrays[this._threadId].length)
			maxPathsSharedArrays[this._threadId] = new int[this.countNodes()][];
		return maxPathsSharedArrays[this._threadId];
	}
	
	public int getNetworkId(){
		return this._networkId;
	}
	
	public int getThreadId(){
		return this._threadId;
	}
	
	/**
	 * Returns the instance modeled by this network
	 * @return
	 */
	public Instance getInstance(){
		return this._inst;
	}
	
	/**
	 * Returns the compiler that compiled this network
	 * @return
	 */
	public NetworkCompiler getCompiler(){
		return this._compiler;
	}
	
	/**
	 * Sets the compiler that compiled this network
	 * @param compiler
	 */
	public void setCompiler(NetworkCompiler compiler){
		this._compiler = compiler;
	}
	
	/**
	 * Returns the labeled network related to this network<br>
	 * If this network represents a labeled network, this will return itself
	 * @return
	 */
	public Network getLabeledNetwork(){
		if(getInstance().isLabeled()){
			return this;
		}
		return this._labeledNetwork;
	}
	
	/**
	 * Sets the labeled network related to this network
	 * @param network
	 */
	public void setLabeledNetwork(Network network){
		this._labeledNetwork = network;
	}
	
	/**
	 * Returns the unlabeled network related to this network<br>
	 * If this network represents an unlabeled network, this will return itself
	 * @return
	 */
	public Network getUnlabeledNetwork(){
		if(!getInstance().isLabeled()){
			return this;
		}
		return this._unlabeledNetwork;
	}
	
	/**
	 * Sets the unlabeled network related to this network
	 * @param network
	 */
	public void setUnlabeledNetwork(Network network){
		this._unlabeledNetwork = network;
	}
	
	/**
	 * Returns the inside score for the root node
	 * @return
	 */
	public double getInside(){
		return this._inside[this.countNodes()-1];
	}
	
	/**
	 * Return the maximum score for this network (which is the max score for the root node)
	 * @return
	 */
	public double getMax(){
		int rootIdx = this.countNodes()-1;
		return this._max[rootIdx];
	}

	/**
	 * Return the maximum score for this network ending in the node with the specified index
	 * @param k
	 * @return
	 */
	public double getMax(int k){
		return this._max[k];
	}
	
	/**
	 * Return the children of the hyperedge which is part of the maximum path of this network
	 * @return
	 */
	public int[] getMaxPath(){
		return this._max_paths[this.countNodes()-1];
	}

	/**
	 * Return the children of the hyperedge which is part of the maximum path of this network
	 * ending at the node at the specified index
	 * @return
	 */
	public int[] getMaxPath(int k){
		return this._max_paths[k];
	}

	/**
	 * Get the sum of the network (i.e., the inside score)
	 * @return
	 */
	public double sum(){
		this.inside();
		return this.getInside();
	}
	
	/**
	 * Train the network
	 */
	public void train(){
		if(this._weight == 0)
			return;
		if(NetworkConfig.MODEL_TYPE == ModelType.SSVM){
			this.max();
		} else {
			this.inside();
			this.outside();
		}
		this.updateGradient();
		this.updateObjective();
	}
	
	/**
	 * Calculate the inside score of all nodes
	 */
	protected void inside(){
		this._inside = this.getInsideSharedArray();
		Arrays.fill(this._inside, 0.0);
		for(int k=0; k<this.countNodes(); k++){
			this.inside(k);
		}
		
		if(this.getInside()==Double.NEGATIVE_INFINITY){
			throw new RuntimeException("Error: network (ID="+_networkId+") has zero inside score");
		}
	}
	
	/**
	 * Calculate the outside score of all nodes
	 */
	protected void outside(){
		this._outside = this.getOutsideSharedArray();
		Arrays.fill(this._outside, Double.NEGATIVE_INFINITY);
		for(int k=this.countNodes()-1; k>=0; k--){
			this.outside(k);
		}
	}
	
	public void updateGradient(double[] gradientArray){
		
	}
	
	/**
	 * Calculate and update the inside-outside score of all nodes
	 */
	protected void updateGradient(){
		if(NetworkConfig.MODEL_TYPE == ModelType.SSVM){
			// Max is already calculated
			int rootIdx = this.countNodes()-1;
			resetVisitedMark();
			this.updateGradient(rootIdx);
		} else {
			for(int k=0; k<this.countNodes(); k++){
				this.updateGradient(k);
			}
		}			
	}
	
	private void resetVisitedMark(){
		this._visited = new boolean[countNodes()];
		for(int i=0; i<this._visited.length; i++){
			this._visited[i] = false;
		}
	}
	
	protected void updateObjective(){
		double objective = 0.0;
		if(NetworkConfig.MODEL_TYPE == ModelType.SSVM){
			objective = this.getMax() * this._weight;
		} else {
			objective = this.getInside() * this._weight;
		}
		this._param.addObj(objective);
	}
	
	/**
	 * Goes through each nodes in the network to gather list of features
	 */
	public synchronized void touch(){
		for(int k=0; k<this.countNodes(); k++)
			this.touch(k);
	}
	
	/**
	 * Calculate the maximum score for all nodes
	 */
	public void max(){
		this._max = this.getMaxSharedArray();
		
		this._max_paths = this.getMaxPathSharedArray();
		for(int k=0; k<this.countNodes(); k++){
			this.max(k);
		}
	}
	
	/**
	 * Calculate the inside score for the specified node
	 * @param k
	 */
	protected void inside(int k){
		if(this.isRemoved(k)){
			this._inside[k] = Double.NEGATIVE_INFINITY;
			return;
		}
		
		double inside = 0.0;
		int[][] childrenList_k = this.getChildren(k);
		
		// If this node has no child edge, assume there is one edge with no child node
		// This is done so that every node is visited in the feature extraction step below
		if(childrenList_k.length==0){ 
			childrenList_k = new int[1][0];
		}
		
		{
			int children_k_index = 0;
			int[] children_k = childrenList_k[children_k_index];

			boolean ignoreflag = false;
			for(int child_k : children_k){
				if(this.isRemoved(child_k)){
					ignoreflag = true;
				}
			}
			if(ignoreflag){
				inside = Double.NEGATIVE_INFINITY;
			} else {
				FeatureArray fa = this._param.extract(this, k, children_k, children_k_index);
				double score = fa.getScore(this._param);
				if(NetworkConfig.MODEL_TYPE == ModelType.SOFTMAX_MARGIN){
					score += this._param.cost(this, k, children_k, children_k_index, this._compiler);
				}
				for(int child_k : children_k){
					score += this._inside[child_k];
				}
				inside = score;
			}
		}
		
		for(int children_k_index = 1; children_k_index < childrenList_k.length; children_k_index++){
			int[] children_k = childrenList_k[children_k_index];

			boolean ignoreflag = false;
			for(int child_k : children_k){
				if(this.isRemoved(child_k)){
					ignoreflag = true;
				}
			}
			if(ignoreflag) continue;
			
			FeatureArray fa = this._param.extract(this, k, children_k, children_k_index);
			double score = fa.getScore(this._param);
			if(NetworkConfig.MODEL_TYPE == ModelType.SOFTMAX_MARGIN){
				score += this._param.cost(this, k, children_k, children_k_index, this._compiler);
			}
			for(int child_k : children_k){
				score += this._inside[child_k];
			}
			
			inside = sumLog(inside, score);
		}
		
		this._inside[k] = inside;
		
		if(this._inside[k]==Double.NEGATIVE_INFINITY)
			this.remove(k);
	}
	
	/**
	 * Calculate the outside score for the specified node
	 * @param k
	 */
	protected void outside(int k){
		if(this.isRemoved(k)){
			this._outside[k] = Double.NEGATIVE_INFINITY;
			return;
		}
		else
			this._outside[k] = this.isRoot(k) ? 0.0 : this._outside[k];
		
		if(this._inside[k]==Double.NEGATIVE_INFINITY)
			this._outside[k] = Double.NEGATIVE_INFINITY;
		
		int[][] childrenList_k = this.getChildren(k);
		for(int children_k_index = 0; children_k_index< childrenList_k.length; children_k_index++){
			int[] children_k = childrenList_k[children_k_index];
			
			boolean ignoreflag = false;
			for(int child_k : children_k)
				if(this.isRemoved(child_k)){
					ignoreflag = true; break;
				}
			if(ignoreflag)
				continue;
			
			FeatureArray fa = this._param.extract(this, k, children_k, children_k_index);
			double score = fa.getScore(this._param);
			if(NetworkConfig.MODEL_TYPE == ModelType.SOFTMAX_MARGIN){
				score += this._param.cost(this, k, children_k, children_k_index, this._compiler);
			}
			score += this._outside[k];
			for(int child_k : children_k){
				score += this._inside[child_k];
			}

			if(score == Double.NEGATIVE_INFINITY)
				continue;
			
			for(int child_k : children_k){
				double v1 = this._outside[child_k];
				double v2 = score - this._inside[child_k];
				this._outside[child_k] = sumLog(v1, v2);
			}
		}
		
		if(this._outside[k]==Double.NEGATIVE_INFINITY){
			this.remove(k);
		}
	}
	
	/**
	 * Calculate and update the gradient for features present at the specified node
	 * @param k
	 */
	protected void updateGradient(int k){
		if(this.isRemoved(k))
			return;
		
		int[][] childrenList_k = this.getChildren(k);
		int[] maxChildren = null;
		if(NetworkConfig.MODEL_TYPE == ModelType.SSVM){
			if(this._visited[k]) return;
			this._visited[k] = true;
			maxChildren = this.getMaxPath(k); // For Structured SVM
		}
		
		for(int children_k_index = 0; children_k_index<childrenList_k.length; children_k_index++){
			double count = 0.0;
			int[] children_k = childrenList_k[children_k_index];
			
			boolean ignoreflag = false;
			for(int child_k : children_k){
				if(this.isRemoved(child_k)){
					ignoreflag = true;
					break;
				}
			}
			if(NetworkConfig.MODEL_TYPE == ModelType.SSVM){ // Consider only max path
				if(!Arrays.equals(children_k, maxChildren)){
					continue;
				}
			}
			if(ignoreflag){
				continue;
			}
			
			FeatureArray fa = this._param.extract(this, k, children_k, children_k_index);
			if(NetworkConfig.MODEL_TYPE == ModelType.SSVM){
				count = 1;
			} else {
				double score = fa.getScore(this._param); // w*f
				if(NetworkConfig.MODEL_TYPE == ModelType.SOFTMAX_MARGIN){
					score += this._param.cost(this, k, children_k, children_k_index, this._compiler);
				}
				score += this._outside[k];  // beta(s')
				for(int child_k : children_k){
					score += this._inside[child_k]; // alpha(s)
				}
				double normalization = this.getInside();
				count = Math.exp(score-normalization); // Divide by normalization term Z
			}
			count *= this._weight;
			
			fa.update(this._param, count);
			if(NetworkConfig.MODEL_TYPE == ModelType.SSVM){
				for(int child_k: children_k){
					this.updateGradient(child_k);	
				}
			}
		}
	}
	
	/**
	 * Gather features from the specified node
	 * @param k
	 */
	protected void touch(int k){
		if(this.isRemoved(k))
			return;
		
		int[][] childrenList_k = this.getChildren(k);
		for(int children_k_index = 0; children_k_index < childrenList_k.length; children_k_index++){
			int[] children_k = childrenList_k[children_k_index];
			this._param.extract(this, k, children_k, children_k_index);
		}
	}
	
	/**
	 * Calculate the maximum score at the specified node
	 * @param k
	 */
	protected void max(int k){
		if(this.isRemoved(k)){
			this._max[k] = Double.NEGATIVE_INFINITY;
			return;
		}
		
		if(this.isSumNode(k)){

			double inside = 0.0;
			int[][] childrenList_k = this.getChildren(k);
			
			if(childrenList_k.length==0){
				childrenList_k = new int[1][0];
			}
			
			{
				int children_k_index = 0;
				int[] children_k = childrenList_k[children_k_index];
				
				boolean ignoreflag = false;
				for(int child_k : children_k)
					if(this.isRemoved(child_k))
						ignoreflag = true;
				if(ignoreflag){
					inside = Double.NEGATIVE_INFINITY;
				} else {
					FeatureArray fa = this._param.extract(this, k, children_k, children_k_index);
					double score = fa.getScore(this._param);
					try{
						score += this._param.cost(this, k, children_k, children_k_index, this._compiler);
					} catch (NullPointerException e){
						System.err.println("WARNING: Compiler was not specified during network creation, setting cost to 0.0");
					}
					for(int child_k : children_k){
						score += this._max[child_k];
					}
					inside = score;
				}
				
				//if it is a sum node, then any path is the same for such a node.
				//this is something you need to make sure when constructing such a network.
				this._max_paths[k] = children_k;
			}
			
			for(int children_k_index = 1; children_k_index < childrenList_k.length; children_k_index++){
				int[] children_k = childrenList_k[children_k_index];

				boolean ignoreflag = false;
				for(int child_k : children_k)
					if(this.isRemoved(child_k))
						ignoreflag = true;
				if(ignoreflag)
					continue;
				
				FeatureArray fa = this._param.extract(this, k, children_k, children_k_index);
				double score = fa.getScore(this._param);
				try{
					score += this._param.cost(this, k, children_k, children_k_index, this._compiler);
				} catch (NullPointerException e){
					System.err.println("WARNING: Compiler was not specified during network creation, setting cost to 0.0");
				}
				for(int child_k : children_k){
					score += this._max[child_k];
				}
				
				inside = sumLog(inside, score);
				
			}
			
			this._max[k] = inside;
		} else {
			int[][] childrenList_k = this.getChildren(k);
			this._max[k] = Double.NEGATIVE_INFINITY;
			
			for(int children_k_index = 0; children_k_index < childrenList_k.length; children_k_index++){
				int[] children_k = childrenList_k[children_k_index];
				boolean ignoreflag = false;
				for(int child_k : children_k)
					if(this.isRemoved(child_k)){
						ignoreflag = true; break;
					}
				if(ignoreflag)
					continue;
				
				FeatureArray fa = this._param.extract(this, k, children_k, children_k_index);
				double score = fa.getScore(this._param);
				try{
					score += this._param.cost(this, k, children_k, children_k_index, this._compiler);
				} catch (NullPointerException e){
					System.err.println("WARNING: Compiler was not specified during network creation, setting cost to 0.0");
				}
				for(int child_k : children_k){
					score += this._max[child_k];
				}
				if(score >= this._max[k]){
					this._max[k] = score;
					this._max_paths[k] = children_k;
				}
			}
		}
	}

	private double sumLog(double inside, double score) {
		double v1 = inside;
		double v2 = score;
		if(v1==v2 && v2==Double.NEGATIVE_INFINITY){
			return Double.NEGATIVE_INFINITY;
		} else if(v1==v2 && v2==Double.POSITIVE_INFINITY){
			return Double.POSITIVE_INFINITY;
		} else if(v1>v2){
			return Math.log1p(Math.exp(v2-v1))+v1;
		} else {
			return Math.log1p(Math.exp(v1-v2))+v2;
		}
	}

	/**
	 * Count the number of removed nodes
	 */
	public int countRemovedNodes(){
		int count = 0;
		for(int k = 0; k<this.countNodes(); k++)
			if(this.isRemoved(k))
				count++;
		return count;
	}
	
	/**
	 * Get the root node of the network.
	 * @return
	 */
	public long getRoot(){
		return this.getNode(this.countNodes()-1);
	}
	
	/**
	 * Get the array form of the node at the specified index in the node array
	 */
	public int[] getNodeArray(int k){
		long node = this.getNode(k);
		return NetworkIDMapper.toHybridNodeArray(node);
	}
	
	//this ad-hoc method is useful when performing
	//some special sum operations (in conjunction with max operations)
	//in the decoding phase.
	protected boolean isSumNode(int k){
		return false;
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i<this.countNodes(); i++)
			sb.append(Arrays.toString(NetworkIDMapper.toHybridNodeArray(this.getNode(i))));
		return sb.toString();
	}
	
}