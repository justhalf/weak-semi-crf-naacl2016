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

import com.statnlp.example.linear_crf.LinearCRFNetworkCompiler.NODE_TYPES;
import com.statnlp.experiment.smsnp.SMSNPInstance;
import com.statnlp.experiment.smsnp.SMSNPNetwork;
import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;
import com.statnlp.hybridnetworks.FeatureArray;
import com.statnlp.hybridnetworks.FeatureManager;
import com.statnlp.hybridnetworks.GlobalNetworkParam;
import com.statnlp.hybridnetworks.Network;
import com.statnlp.hybridnetworks.NetworkIDMapper;

/**
 * 
 * @author Aldrian Obaja <aldrianobaja.m@gmail.com>
 *
 */
public class LinearCRFFeatureManager extends FeatureManager{

	private static final long serialVersionUID = -4880581521293400351L;
	
	public int wordHalfWindowSize = 1;
	public boolean wordOnlyLeftWindow = true;
	
	public enum FeatureType {
		WORD(true),
		WORD_BIGRAM(false),
		TRANSITION(true),
		;
		
		private boolean isEnabled = true;
		
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
	
	public TokenizerMethod tokenizerMethod;

	/**
	 * @param param_g
	 */
	public LinearCRFFeatureManager(GlobalNetworkParam param_g, TokenizerMethod tokenizerMethod, String[] disabledFeatures) {
		super(param_g);
		this.tokenizerMethod = tokenizerMethod;
		for(String disabledFeature: disabledFeatures){
			FeatureType.valueOf(disabledFeature.toUpperCase()).disable();
		}
	}

	@Override
	protected FeatureArray extract_helper(Network network, int parent_k, int[] children_k) {
		SMSNPNetwork net = (SMSNPNetwork)network;
		SMSNPInstance instance = (SMSNPInstance)net.getInstance();
		
		String[] words = instance.getInputTokenized(tokenizerMethod, true, false);
		int size = words.length;
		
		long curNode = net.getNode(parent_k);
		int[] arr = NetworkIDMapper.toHybridNodeArray(curNode);
		
		int pos = arr[0]-1;
		int tag_id = arr[1]-1;
		int nodeType = arr[4];
		
		if(nodeType == NODE_TYPES.LEAF.ordinal() || (nodeType == NODE_TYPES.ROOT.ordinal() && pos < size-1)){
			return FeatureArray.EMPTY;
		}
		
		int child_tag_id = network.getNodeArray(children_k[0])[1]-1;
		
		GlobalNetworkParam param_g = this._param_g;

		FeatureArray features = new FeatureArray(new int[0]);
		// Word window features
		if(FeatureType.WORD.enabled() && nodeType != NODE_TYPES.ROOT.ordinal()){
			int wordWindowSize = wordHalfWindowSize*2+1;
			if(wordWindowSize < 0){
				wordWindowSize = 0;
			}
			int[] wordWindowFeatures = new int[wordWindowSize];
			for(int i=0; i<wordWindowFeatures.length; i++){
				String word = "***";
				int relIdx = (i-wordHalfWindowSize);
				int idx = pos + relIdx;
				if(idx >= 0 && idx < size){
					word = words[idx];
				}
				if(wordOnlyLeftWindow && idx > pos) continue;
				wordWindowFeatures[i] = param_g.toFeature(FeatureType.WORD+":"+relIdx, tag_id+"", word);
			}
			FeatureArray wordFeatures = new FeatureArray(wordWindowFeatures, features);
			features = wordFeatures;
		}
		
		// Word bigram features
		if(FeatureType.WORD_BIGRAM.enabled()){
			int[] bigramFeatures = new int[2];
			for(int i=0; i<2; i++){
				String bigram = "";
				for(int j=0; j<2; j++){
					int idx = pos+i+j-1;
					if(idx >=0 && idx < size){
						bigram += words[idx];
					} else {
						bigram += "***";
					}
					if(j<1){
						bigram += " ";
					}
				}
				bigramFeatures[i] = param_g.toFeature(FeatureType.WORD_BIGRAM+":"+i, tag_id+"", bigram);
			}
			features = new FeatureArray(bigramFeatures, features);
		}
		
		// Label transition feature
		if(FeatureType.TRANSITION.enabled()){
			if(child_tag_id == -1){
				
			} else {
				int transitionFeature = param_g.toFeature(FeatureType.TRANSITION.name(), child_tag_id+"-"+tag_id, "");
				features = new FeatureArray(new int[]{transitionFeature}, features);
			}
		}
		
//		int cheatFeature = param_g.toFeature("CHEAT", tag_id+"", instance.getInstanceId()+" "+pos+""+child_tag_id);
//		features = new FeatureArray(new int[]{cheatFeature}, features);
		
		return features;
	}

}
