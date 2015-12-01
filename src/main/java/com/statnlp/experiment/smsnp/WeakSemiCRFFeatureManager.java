package com.statnlp.experiment.smsnp;

import java.util.ArrayList;
import java.util.List;

import com.statnlp.example.semi_crf.SemiCRFNetworkCompiler.NodeType;
import com.statnlp.hybridnetworks.FeatureArray;
import com.statnlp.hybridnetworks.FeatureManager;
import com.statnlp.hybridnetworks.GlobalNetworkParam;
import com.statnlp.hybridnetworks.Network;

import justhalf.nlp.tokenizer.Tokenizer;
import justhalf.nlp.tokenizer.WhitespaceTokenizer;

public class WeakSemiCRFFeatureManager extends FeatureManager {
	
	private static final long serialVersionUID = 6510131496948610905L;
	
	private static final boolean CHEAT = false;

	public enum FeatureType{
		CHEAT,
		
		// Begin to End features (on segment)
		SEGMENT, // The string inside the segment
		NUM_WORDS, // Number of words
		
		INSIDE_UNIGRAM, // The characters inside the segment, the window size can be varied
		INSIDE_SUBSTRING, // The substrings found with the same start or the same end as the segment
		WORDS, // Words inside the segment, indexed from the segment start and from segment end
		
		CONTAINS_AND, // Whether the segment contains the word "and"
		
		// End to Begin features (on transition)

		OUTSIDE_UNIGRAM, // The character window to the left and right of transition boundary
		OUTSIDE_SUBSTRING, // The substring window to the left and right of transition boundary
		
		// Any
		
		PREV_WORD, // The word ending at start boundary
		START_BOUNDARY_WORD, // If the previous character is not space, get the word crossing the begin boundary
		END_BOUNDARY_WORD, // If the next character is not space, get the word crossing the end boundary
		NEXT_WORD, // The word starting at the end boundary
		
		BIGRAM,
	}
	
	public int unigramWindowSize = 3;
	public int substringWindowSize = 3;
	
	public transient Tokenizer tokenizer;
	
	public WeakSemiCRFFeatureManager(GlobalNetworkParam param_g){
		this(param_g, new WhitespaceTokenizer());
	}

	public WeakSemiCRFFeatureManager(GlobalNetworkParam param_g, Tokenizer tokenizer) {
		super(param_g);
		this.tokenizer = tokenizer;
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
		
		List<Integer> commonFeatures = new ArrayList<Integer>();
		commonFeatures.add(bigramFeature);
		
		int startBoundary = (childType == NodeType.BEGIN) ? childPos : childPos+1;
		int endBoundary = (parentType == NodeType.END) ? parentPos : parentPos-1;
		int prevWordStart = input.lastIndexOf(' ', startBoundary-2)+1;
		int prevWordEnd = (startBoundary > 0 && inputArr[startBoundary-1] == ' ') ? startBoundary-1 : startBoundary;
		int nextWordEnd = input.indexOf(' ', endBoundary+2);
		if(nextWordEnd == -1){
			nextWordEnd = length;
		}
		int nextWordStart = (endBoundary < length-1 && inputArr[endBoundary+1] == ' ') ? endBoundary+2 : endBoundary+1;
		
		int prevWordFeature = param_g.toFeature(FeatureType.PREV_WORD.name(), parentLabelId+"", input.substring(prevWordStart, prevWordEnd));
		commonFeatures.add(prevWordFeature);
		int nextWordFeature = param_g.toFeature(FeatureType.NEXT_WORD.name(), parentLabelId+"", input.substring(nextWordStart, nextWordEnd));
		commonFeatures.add(nextWordFeature);

		if(startBoundary > 0 && inputArr[startBoundary] != ' ' && inputArr[startBoundary-1] != ' '){
			int startWordBoundaryEnd = input.indexOf(' ', childPos);
			if(startWordBoundaryEnd == -1){
				startWordBoundaryEnd = nextWordStart-1;
			}
			int startBoundaryWordFeature = param_g.toFeature(FeatureType.START_BOUNDARY_WORD.name(), parentLabelId+"", input.substring(prevWordStart, startWordBoundaryEnd));
			commonFeatures.add(startBoundaryWordFeature);
		}
		
		if(endBoundary < length-1 && inputArr[endBoundary] != ' ' && inputArr[endBoundary+1] != ' '){
			int endWordBoundaryStart = input.lastIndexOf(' ', parentPos);
			if(endWordBoundaryStart == -1){
				endWordBoundaryStart = prevWordEnd+1;
			}
			int endBoundaryWordFeature = param_g.toFeature(FeatureType.END_BOUNDARY_WORD.name(), parentLabelId+"", input.substring(endWordBoundaryStart, nextWordEnd));
			commonFeatures.add(endBoundaryWordFeature);
		}
		
		FeatureArray features = new FeatureArray(listToArray(commonFeatures));
		
		// Begin to End features (segment features)
		if(parentType == NodeType.END){
			int segmentFeature = param_g.toFeature(FeatureType.SEGMENT.name(), parentLabelId+"", input.substring(childPos, parentPos));

			String[] words = tokenizer.tokenizeToString(input.substring(childPos, parentPos+1));
			int numWords = words.length;
			int numWordsFeature = param_g.toFeature(FeatureType.NUM_WORDS.name(), parentLabelId+"", numWords+"");
			
			List<Integer> segmentFeatures = new ArrayList<Integer>();
			segmentFeatures.add(segmentFeature);
			segmentFeatures.add(numWordsFeature);
			
			for(int i=0; i<words.length; i++){
				segmentFeatures.add(param_g.toFeature(FeatureType.WORDS.name()+":"+i, parentLabelId+"", words[i]));
				segmentFeatures.add(param_g.toFeature(FeatureType.WORDS.name()+":-"+i, parentLabelId+"", words[i]));
			}
			
			for(int i=0; i<unigramWindowSize; i++){
				int index = Math.min(childPos+i, length-1);
				segmentFeatures.add(param_g.toFeature(FeatureType.INSIDE_UNIGRAM.name()+":"+i, parentLabelId+"",  inputArr[index]+""));
				index = Math.max(parentPos-i, 0);
				segmentFeatures.add(param_g.toFeature(FeatureType.INSIDE_UNIGRAM.name()+":-"+i, parentLabelId+"",  inputArr[index]+""));
			}
			
			for(int i=0; i<substringWindowSize; i++){
				int index = Math.min(childPos+i+1, length);
				segmentFeatures.add(param_g.toFeature(FeatureType.INSIDE_SUBSTRING.name()+":"+i, parentLabelId+"",  input.substring(childPos, index)));
				index = Math.max(parentPos-i, 0);
				segmentFeatures.add(param_g.toFeature(FeatureType.INSIDE_SUBSTRING.name()+":-"+i, parentLabelId+"",  input.substring(index, parentPos+1)));
			}
			
//			boolean containsAnd = false;
//			for(String word: words){
//				if(word.equalsIgnoreCase("and")){
//					containsAnd = true;
//				}
//				segmentFeatures.add(param_g.toFeature(FeatureType.CONTAINS_AND.name(), parentLabelId+"", containsAnd+""));
//			}
			
			features = new FeatureArray(listToArray(segmentFeatures), features);
		}
		
		// End to Begin features (transition features)
		if(parentType == NodeType.BEGIN){
			List<Integer> transitionFeatures = new ArrayList<Integer>();
			for(int i=0; i<unigramWindowSize; i++){
				int index = Math.min(parentPos+i, length-1);
				String curInput = inputArr[index]+"";
				transitionFeatures.add(param_g.toFeature(FeatureType.OUTSIDE_UNIGRAM+":"+i, parentLabelId+"", curInput));
				
				index = Math.max(childPos-i, 0);
				curInput = inputArr[index]+"";
				transitionFeatures.add(param_g.toFeature(FeatureType.OUTSIDE_UNIGRAM+":-"+i, parentLabelId+"", curInput));
			}
			
			for(int i=0; i<substringWindowSize; i++){
				int index = Math.min(parentPos+i+1, length);
				String curInput = input.substring(parentPos, index);
				transitionFeatures.add(param_g.toFeature(FeatureType.OUTSIDE_SUBSTRING+":"+i, parentLabelId+"", curInput));
				
				index = Math.max(childPos-i, 0);
				curInput = input.substring(index, childPos+1);
				transitionFeatures.add(param_g.toFeature(FeatureType.OUTSIDE_SUBSTRING+":-"+i, parentLabelId+"", curInput));
			}
			features = new FeatureArray(listToArray(transitionFeatures), features);
		}
		
		return features;
		
	}
	
	private int[] listToArray(List<Integer> list){
		int[] result = new int[list.size()];
		for(int i=0; i<list.size(); i++){
			result[i] = list.get(i);
		}
		return result;
	}

}
