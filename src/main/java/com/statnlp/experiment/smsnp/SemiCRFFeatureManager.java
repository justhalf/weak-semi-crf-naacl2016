package com.statnlp.experiment.smsnp;

import com.statnlp.experiment.smsnp.SemiCRFNetworkCompiler.NodeType;
import com.statnlp.hybridnetworks.FeatureArray;
import com.statnlp.hybridnetworks.FeatureManager;
import com.statnlp.hybridnetworks.GlobalNetworkParam;
import com.statnlp.hybridnetworks.Network;

public class SemiCRFFeatureManager extends FeatureManager {
	
	private static final long serialVersionUID = 6510131496948610905L;
	
	private static final boolean CHEAT = true;

	public enum FeatureType{
		CHEAT,
		
		SEGMENT,
		START_CHAR,
		END_CHAR,
		
		UNIGRAM,
		SUBSTRING,

		ENDS_WITH_SPACE,
		NUM_SPACES,
		
		PREV_WORD,
		START_BOUNDARY_WORD,
		WORDS,
		END_BOUNDARY_WORD,
		NEXT_WORD,
		
		BIGRAM,
	}
	
	public int unigramWindowSize = 5;
	public int substringWindowSize = 5;

	public SemiCRFFeatureManager(GlobalNetworkParam param_g) {
		super(param_g);
	}
	
	@Override
	protected FeatureArray extract_helper(Network net, int parent_k, int[] children_k) {
		SMSNPNetwork network = (SMSNPNetwork)net;
		SMSNPInstance instance = (SMSNPInstance)network.getInstance();
		
		int[] parent_arr = network.getNodeArray(parent_k);
		int parentPos = parent_arr[0];
		NodeType parentType = NodeType.values()[parent_arr[1]];
		int parentLabelId = parent_arr[2];
		
		if(parentType == NodeType.LEAF){
			return FeatureArray.EMPTY;
		}

		int[] child_arr = network.getNodeArray(children_k[0]);
		int childPos = child_arr[0];
		NodeType childType = NodeType.values()[child_arr[1]];
		int childLabelId = child_arr[2];

		GlobalNetworkParam param_g = this._param_g;
		
		if(CHEAT){
			int instanceId = Math.abs(instance.getInstanceId());
			int cheatFeature = param_g.toFeature(FeatureType.CHEAT.name(), parentLabelId+"", instanceId+" "+parentPos+" "+childPos+" "+parentLabelId+" "+childLabelId);
			return new FeatureArray(new int[]{cheatFeature});
		}
		
		int bigramFeature = param_g.toFeature(FeatureType.BIGRAM.name(), parentLabelId+"", parentLabelId+" "+childLabelId);
		if(parentType == NodeType.ROOT || childType == NodeType.LEAF){
			return new FeatureArray(new int[]{
					bigramFeature,
			});
		}
		
		String input = instance.input;
		char[] inputArr = input.toCharArray();
		int length = input.length();
		int isSpaceFeature = param_g.toFeature(FeatureType.ENDS_WITH_SPACE.name(), parentLabelId+"", (inputArr[parentPos] == ' ')+"");
		int startCharFeature = param_g.toFeature(FeatureType.START_CHAR.name(), parentLabelId+"", inputArr[childPos]+"");
		int endCharFeature = param_g.toFeature(FeatureType.END_CHAR.name(), parentLabelId+"", inputArr[parentPos]+"");
		int segmentFeature = param_g.toFeature(FeatureType.SEGMENT.name(), parentLabelId+"", input.substring(childPos, parentPos));
		
		String[] words = input.split(" ");
		int numSpaces = words.length-1;
		int numSpacesFeature = param_g.toFeature(FeatureType.NUM_SPACES.name(), parentLabelId+"", numSpaces+"");
		
		int prevSpaceIdx = input.lastIndexOf(' ', childPos-1);
		if(prevSpaceIdx == -1){
			prevSpaceIdx = 0;
		}
		int firstSpaceIdx = input.indexOf(' ', childPos);
		if(firstSpaceIdx == -1){
			firstSpaceIdx = prevSpaceIdx;
		}
		int prevWordFeature = param_g.toFeature(FeatureType.PREV_WORD.name(), parentLabelId+"", input.substring(prevSpaceIdx, childPos));
		int startBoundaryWordFeature = param_g.toFeature(FeatureType.START_BOUNDARY_WORD.name(), parentLabelId+"", input.substring(prevSpaceIdx, firstSpaceIdx));
		
		int nextSpaceIdx = input.indexOf(' ', parentPos+1);
		if(nextSpaceIdx == -1){
			nextSpaceIdx = length;
		}
		int lastSpaceIdx = input.lastIndexOf(' ', parentPos);
		if(lastSpaceIdx == -1){
			lastSpaceIdx = nextSpaceIdx;
		}
		int nextWordFeature = param_g.toFeature(FeatureType.NEXT_WORD.name(), parentLabelId+"", input.substring(parentPos+1, nextSpaceIdx));
		int endBoundaryWordFeature = param_g.toFeature(FeatureType.END_BOUNDARY_WORD.name(), parentLabelId+"", input.substring(lastSpaceIdx, nextSpaceIdx));
		
		FeatureArray features = new FeatureArray(new int[]{
				bigramFeature,
				isSpaceFeature,
				startCharFeature,
				endCharFeature,
				segmentFeature,
				numSpacesFeature,
				prevWordFeature,
				nextWordFeature,
				startBoundaryWordFeature,
				endBoundaryWordFeature,
		});
		
		int[] wordFeatures = new int[2*words.length];
		for(int i=0; i<words.length; i++){
			wordFeatures[i] = param_g.toFeature(FeatureType.WORDS.name()+i, parentLabelId+"", words[i]);
			wordFeatures[2*words.length-i-1] = param_g.toFeature(FeatureType.WORDS.name()+"-"+i, parentLabelId+"", words[i]);
		}
		features = new FeatureArray(wordFeatures, features);
		
		int unigramFeatureSize = 2*unigramWindowSize;
		int[] unigramFeatures = new int[unigramFeatureSize];
		for(int i=0; i<unigramWindowSize; i++){
			String curInput = "";
			if(parentPos+i+1 < length){
				curInput = inputArr[parentPos+i+1]+"";
			}
			unigramFeatures[i] = param_g.toFeature(FeatureType.UNIGRAM+":"+i, parentLabelId+"", curInput);
			curInput = "";
			if(childPos-i-1 >= 0){
				curInput = inputArr[childPos-i-1]+"";
			}
			unigramFeatures[unigramFeatureSize-i-1] = param_g.toFeature(FeatureType.UNIGRAM+":-"+i, parentLabelId+"", curInput);
		}
		features = new FeatureArray(unigramFeatures, features);
		
		int substringFeatureSize = 2*substringWindowSize;
		int[] substringFeatures = new int[substringFeatureSize];
		for(int i=0; i<substringWindowSize; i++){
			String curInput = "";
			if(parentPos+i+1< length){
				curInput = input.substring(parentPos, parentPos+i+1);
			}
			substringFeatures[i] = param_g.toFeature(FeatureType.SUBSTRING+":"+i, parentLabelId+"", curInput);
			curInput = "";
			if(childPos-i-1 >= 0){
				curInput = input.substring(childPos-i-1, childPos);
			}
			substringFeatures[unigramFeatureSize-i-1] = param_g.toFeature(FeatureType.SUBSTRING+":-"+i, parentLabelId+"", curInput);
		}
		features = new FeatureArray(substringFeatures, features);
		
		return features;
		
	}

}
