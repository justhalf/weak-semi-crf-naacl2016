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

import com.statnlp.experiment.smsnp.SMSNPInstance;
import com.statnlp.experiment.smsnp.SMSNPNetwork;
import com.statnlp.experiment.smsnp.SMSNPTokenizer;
import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;
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
	
	public enum FeatureType {
		PREV_EMISSION,
		EMISSION,
		TRANSITION,
		;
		
		private boolean isEnabled;
		private FeatureType(){
			isEnabled = true;
		}
		
		private FeatureType(boolean isEnabled){
			this.isEnabled = isEnabled;
		}
		
		public void enable(){
			isEnabled = true;
		}
		
		public void disable(){
			isEnabled = false;
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
		
		String input = instance.getInput();
		String[] words = SMSNPTokenizer.tokenize(input, tokenizerMethod);
		
		long curNode = net.getNode(parent_k);
		int[] arr = NetworkIDMapper.toHybridNodeArray(curNode);
		
		int pos = arr[0]-1;
		int tag_id = arr[1];
		int nodeType = arr[4];
		
		String curWord = null;
		String prevWord = null;
		int child_tag_id = -1;
		switch (LinearCRFNetworkCompiler.NODE_TYPES.values()[nodeType]){
		case LEAF:
			return FeatureArray.EMPTY;
		case ROOT:
			child_tag_id = NetworkIDMapper.toHybridNodeArray(net.getNode(children_k[0]))[1];
			curWord = LinearCRFNetworkCompiler.NODE_TYPES.values()[nodeType].name();
			if(pos == 0){
				prevWord = LinearCRFNetworkCompiler.NODE_TYPES.LEAF.name();
			} else {
				prevWord = words[pos-1];
			}
			break;
		case NODE:
			curWord = words[pos];
			if(pos == 0){
				child_tag_id = -1;
				prevWord = LinearCRFNetworkCompiler.NODE_TYPES.LEAF.name();
			} else {
				child_tag_id = NetworkIDMapper.toHybridNodeArray(net.getNode(children_k[0]))[1];
				prevWord = words[pos-1];
			}
			break;
		default:
			throw new RuntimeException("Should not happen");
		}
	
		GlobalNetworkParam param_g = this._param_g;
		int prevEmissionFeature = param_g.toFeature(FeatureType.PREV_EMISSION.name(), tag_id+"", prevWord);
		int emissionFeature = param_g.toFeature(FeatureType.EMISSION.name(), tag_id+"", curWord);
		int transitionFeature = param_g.toFeature(FeatureType.TRANSITION.name(), tag_id+"", child_tag_id+" "+tag_id);
		
		FeatureArray featureArr = null;
		featureArr = new FeatureArray(new int[]{
												prevEmissionFeature,
												emissionFeature,
												transitionFeature,
											});
		
		return featureArr;
	}

}
