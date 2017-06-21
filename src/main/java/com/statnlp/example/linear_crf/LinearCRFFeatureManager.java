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

import com.statnlp.example.linear_crf.LinearCRFNetworkCompiler.NODE_TYPES;
import com.statnlp.hybridnetworks.FeatureArray;
import com.statnlp.hybridnetworks.FeatureManager;
import com.statnlp.hybridnetworks.GlobalNetworkParam;
import com.statnlp.hybridnetworks.Network;
import com.statnlp.hybridnetworks.NetworkIDMapper;

/**
 * @author wei_lu
 *
 */
public class LinearCRFFeatureManager extends FeatureManager{

	private static final long serialVersionUID = -4880581521293400351L;
	
	private static final boolean CHEAT = false;
	
	public int wordHalfWindowSize = 1;
	public int posHalfWindowSize = -1;
	public boolean productWithOutput = true;
	
	public enum FeatureType {
		WORD,
		WORD_BIGRAM(false),
		TAG(false),
		TAG_BIGRAM(false),
		TRANSITION,
		LABEL,
		;
		
		private boolean isEnabled;
		
		private FeatureType(){
			this(true);
		}
		
		private FeatureType(boolean enabled){
			this.isEnabled = enabled;
		}
		
		public void enable(){
			this.isEnabled = true;
		}
		
		public void disable(){
			this.isEnabled = false;
		}
		
		public boolean enabled(){
			return isEnabled;
		}
		
		public boolean disabled(){
			return !isEnabled;
		}
	}
	
	/**
	 * @param param_g
	 */
	public LinearCRFFeatureManager(GlobalNetworkParam param_g) {
		this(param_g, new LinearCRFConfig());
	}
	
	public LinearCRFFeatureManager(GlobalNetworkParam param_g, String[] args){
		this(param_g, new LinearCRFConfig(args));
	}
	
	/**
	 * @param param_g
	 */
	public LinearCRFFeatureManager(GlobalNetworkParam param_g, LinearCRFConfig config) {
		super(param_g);
		wordHalfWindowSize = config.wordHalfWindowSize;
		posHalfWindowSize = config.posHalfWindowSize;
		productWithOutput = config.productWithOutput;
		if(config.features != null){
			for(FeatureType feat: FeatureType.values()){
				feat.disable();
			}
			for(String feat: config.features){
				FeatureType.valueOf(feat.toUpperCase()).enable();
			}
		}
	}

	@Override
	protected FeatureArray extract_helper(Network network, int parent_k, int[] children_k) {
		
		LinearCRFNetwork net = (LinearCRFNetwork)network;
		
		LinearCRFInstance instance = (LinearCRFInstance)net.getInstance();
		int size = instance.size();
		
		ArrayList<String[]> input = instance.getInput();
		
		long curNode = net.getNode(parent_k);
		int[] arr = NetworkIDMapper.toHybridNodeArray(curNode);
		
		int pos = arr[0]-1;
		int tag_id = arr[1];
		int nodeType = arr[4];
		if(!productWithOutput){
			tag_id = -1;
		}
		
		if(nodeType == NODE_TYPES.LEAF.ordinal()){
			return FeatureArray.EMPTY;
		}
		
		int child_tag_id = network.getNodeArray(children_k[0])[1];
		int childNodeType = network.getNodeArray(children_k[0])[4];
		
		int labelSize = Label.LABELS.size();

		if(childNodeType == NODE_TYPES.LEAF.ordinal()){
			child_tag_id = labelSize;
		}
		
		GlobalNetworkParam param_g = this._param_g;
		
		if(CHEAT){
			return new FeatureArray(new int[]{param_g.toFeature(net, "CHEAT", tag_id+"", Math.abs(instance.getInstanceId())+" "+pos+" "+child_tag_id)});
		}

		ArrayList<Integer> features = new ArrayList<Integer>();
		// Word window features
		if(FeatureType.WORD.enabled() && tag_id != labelSize){
			int wordWindowSize = wordHalfWindowSize*2+1;
			if(wordWindowSize < 0){
				wordWindowSize = 0;
			}
			for(int i=0; i<wordWindowSize; i++){
				String word = "***";
				int relIdx = i-wordHalfWindowSize;
				int idx = pos + relIdx;
				if(idx >= 0 && idx < size){
					word = input.get(idx)[0];
				}
				if(idx > pos) continue; // Only consider the left window
				features.add(param_g.toFeature(network, FeatureType.WORD+":"+relIdx, tag_id+"", word));
			}
		}
		
		// POS tag window features
		if(FeatureType.TAG.enabled() && tag_id != labelSize){
			int posWindowSize = posHalfWindowSize*2+1;
			if(posWindowSize < 0){
				posWindowSize = 0;
			}
			for(int i=0; i<posWindowSize; i++){
				String postag = "***";
				int relIdx = i-posHalfWindowSize;
				int idx = pos + relIdx;
				if(idx >= 0 && idx < size){
					postag = input.get(idx)[1];
				}
				features.add(param_g.toFeature(network, FeatureType.TAG+":"+relIdx, tag_id+"", postag));
			}
		}
		
		// Word bigram features
		if(FeatureType.WORD_BIGRAM.enabled()){
			for(int i=0; i<2; i++){
				String bigram = "";
				for(int j=0; j<2; j++){
					int idx = pos+i+j-1;
					if(idx >=0 && idx < size){
						bigram += input.get(idx)[0];
					} else {
						bigram += "***";
					}
					if(j==0){
						bigram += " ";
					}
				}
				features.add(param_g.toFeature(network, FeatureType.WORD_BIGRAM+":"+i, tag_id+"", bigram));
			}
		}
		
		// POS tag bigram features
		if(FeatureType.TAG_BIGRAM.enabled()){
			for(int i=0; i<2; i++){
				String bigram = "";
				for(int j=0; j<2; j++){
					int idx = pos+i+j-1;
					if(idx >=0 && idx < size){
						bigram += input.get(idx)[1];
					} else {
						bigram += "***";
					}
					if(j==0){
						bigram += " ";
					}
				}
				features.add(param_g.toFeature(network, FeatureType.TAG_BIGRAM+":"+i, tag_id+"", bigram));
			}
		}
		
		// Label feature
		if(FeatureType.LABEL.enabled()){
			int labelFeature = param_g.toFeature(network, FeatureType.LABEL.name(), tag_id+"", "");
			features.add(labelFeature);
		}
		
		// Label transition feature
		if(FeatureType.TRANSITION.enabled()){
			if(tag_id != labelSize && child_tag_id != labelSize){
				int transitionFeature = param_g.toFeature(network, FeatureType.TRANSITION.name(), child_tag_id+" "+tag_id, "");
				features.add(transitionFeature);
			}
		}
		
		int[] featureArray = new int[features.size()];
		for(int i=0; i<featureArray.length; i++){
			featureArray[i] = features.get(i);
		}
		return new FeatureArray(featureArray);
	}

}
