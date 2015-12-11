package com.statnlp.experiment.smsnp.weak_semi_crf;

import java.util.ArrayList;
import java.util.List;

import com.statnlp.example.semi_crf.SemiCRFNetworkCompiler.NodeType;
import com.statnlp.experiment.smsnp.SMSNPInstance;
import com.statnlp.experiment.smsnp.SMSNPNetwork;
import com.statnlp.experiment.smsnp.SMSNPTokenizer;
import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;
import com.statnlp.hybridnetworks.FeatureArray;
import com.statnlp.hybridnetworks.FeatureManager;
import com.statnlp.hybridnetworks.GlobalNetworkParam;
import com.statnlp.hybridnetworks.Network;

public class WeakSemiCRFFeatureManager extends FeatureManager {
	
	private static final long serialVersionUID = 6510131496948610905L;
	
	public static enum FeatureType{
		CHEAT(false),
		
		// Begin to End features (on segment)
		SEGMENT, // The string inside the segment
		SEGMENT_LENGTH, // The segment length
		NUM_WORDS, // Number of words
		
		INSIDE_UNIGRAM, // The characters inside the segment, the window size can be varied
		INSIDE_SUBSTRING, // The substrings found with the same start or the same end as the segment
		WORDS, // Words inside the segment, indexed from the segment start and from segment end
		WORD_SHAPES, // The shape of the words inside the segment
		
		// End to Begin features (on transition)

		DIST_TO_END, // Distance to end of input
		DIST_TO_BEGIN, // Distance to beginning of input
		OUTSIDE_UNIGRAM, // The character window to the left and right of transition boundary
		OUTSIDE_SUBSTRING, // The substring window to the left and right of transition boundary
		
		// Any
		
		PREV_WORD, // The word ending at start boundary
		PREV_WORD_SHAPE, // The shape of the previous word
		START_BOUNDARY_WORD, // If the previous character is not space, get the word crossing the begin boundary
		END_BOUNDARY_WORD, // If the next character is not space, get the word crossing the end boundary
		NEXT_WORD, // The word starting at the end boundary
		NEXT_WORD_SHAPE, // The shape of the next word
		
		BIGRAM,
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
	
	public int unigramWindowSize = 3;
	public int substringWindowSize = 3;
	
	public TokenizerMethod tokenizerMethod;
	
	public WeakSemiCRFFeatureManager(GlobalNetworkParam param_g, String[] disabledFeatures){
		this(param_g, TokenizerMethod.WHITESPACE, disabledFeatures);
	}

	public WeakSemiCRFFeatureManager(GlobalNetworkParam param_g, TokenizerMethod tokenizerMethod, String[] disabledFeatures) {
		super(param_g);
		this.tokenizerMethod = tokenizerMethod;
		for(String disabledFeature: disabledFeatures){
			FeatureType.valueOf(disabledFeature.toUpperCase()).disable();
		}
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
		
		if(FeatureType.CHEAT.enabled()){
			int instanceId = Math.abs(instance.getInstanceId());
			int cheatFeature = param_g.toFeature(FeatureType.CHEAT.name(), "", instanceId+" "+parentPos+" "+childPos+" "+parentLabelId+" "+childLabelId);
			return new FeatureArray(new int[]{cheatFeature});
		}
		
		List<Integer> commonFeatures = new ArrayList<Integer>();
		
		if(FeatureType.BIGRAM.enabled()){
			int bigramFeature = param_g.toFeature(FeatureType.BIGRAM.name(), childLabelId+"-"+parentLabelId, "");
			commonFeatures.add(bigramFeature);
		}
		
		if(parentType == NodeType.ROOT || childType == NodeType.LEAF){
			return new FeatureArray(listToArray(commonFeatures));
		}
		
		String input = instance.input;
		char[] inputArr = input.toCharArray();
		int length = input.length();
		
		int startBoundary = (childType == NodeType.BEGIN) ? childPos : childPos+1;
		int endBoundary = (parentType == NodeType.END) ? parentPos : parentPos-1;
		int prevWordStart = input.lastIndexOf(' ', startBoundary-2)+1;
		int prevWordEnd = (startBoundary > 0 && inputArr[startBoundary-1] == ' ') ? startBoundary-1 : startBoundary;
		int nextWordEnd = input.indexOf(' ', endBoundary+2);
		if(nextWordEnd == -1){
			nextWordEnd = length;
		}
		int nextWordStart = (endBoundary < length-1 && inputArr[endBoundary+1] == ' ') ? endBoundary+2 : endBoundary+1;
		
		if(FeatureType.PREV_WORD.enabled()){
			int prevWordFeature = param_g.toFeature(FeatureType.PREV_WORD.name(), parentLabelId+"", input.substring(prevWordStart, prevWordEnd));
			commonFeatures.add(prevWordFeature);
		}
		if(FeatureType.NEXT_WORD.enabled()){
			int nextWordFeature = param_g.toFeature(FeatureType.NEXT_WORD.name(), parentLabelId+"", input.substring(nextWordStart, nextWordEnd));
			commonFeatures.add(nextWordFeature);
		}
		if(FeatureType.PREV_WORD_SHAPE.enabled()){
			int prevWordShapeFeature = param_g.toFeature(FeatureType.PREV_WORD_SHAPE.name(), parentLabelId+"", wordShape(input.substring(nextWordStart, nextWordEnd)));
			commonFeatures.add(prevWordShapeFeature);
		}
		if(FeatureType.NEXT_WORD_SHAPE.enabled()){
			int nextWordShapeFeature = param_g.toFeature(FeatureType.NEXT_WORD_SHAPE.name(), parentLabelId+"", wordShape(input.substring(nextWordStart, nextWordEnd)));
			commonFeatures.add(nextWordShapeFeature);
		}

		if(FeatureType.START_BOUNDARY_WORD.enabled()){
			if(startBoundary > 0 && inputArr[startBoundary] != ' ' && inputArr[startBoundary-1] != ' '){
				int startWordBoundaryEnd = input.indexOf(' ', childPos);
				if(startWordBoundaryEnd == -1){
					startWordBoundaryEnd = nextWordStart-1;
				}
				int startBoundaryWordFeature = param_g.toFeature(FeatureType.START_BOUNDARY_WORD.name(), parentLabelId+"", input.substring(prevWordStart, startWordBoundaryEnd));
				commonFeatures.add(startBoundaryWordFeature);
			}
		}
		
		if(FeatureType.END_BOUNDARY_WORD.enabled()){
			if(endBoundary < length-1 && inputArr[endBoundary] != ' ' && inputArr[endBoundary+1] != ' '){
				int endWordBoundaryStart = input.lastIndexOf(' ', parentPos);
				if(endWordBoundaryStart == -1){
					endWordBoundaryStart = prevWordEnd+1;
				}
				int endBoundaryWordFeature = param_g.toFeature(FeatureType.END_BOUNDARY_WORD.name(), parentLabelId+"", input.substring(endWordBoundaryStart, nextWordEnd));
				commonFeatures.add(endBoundaryWordFeature);
			}
		}
		
		FeatureArray features = new FeatureArray(listToArray(commonFeatures));
		
		// Begin to End features (segment features)
		if(parentType == NodeType.END){
			List<Integer> segmentFeatures = new ArrayList<Integer>();
			
			if(FeatureType.SEGMENT.enabled()){
				int segmentFeature = param_g.toFeature(FeatureType.SEGMENT.name(), parentLabelId+"", input.substring(childPos, parentPos));
				segmentFeatures.add(segmentFeature);
			}
			
			if(FeatureType.SEGMENT_LENGTH.enabled()){
				int segmentLengthFeature = param_g.toFeature(FeatureType.SEGMENT_LENGTH.name(), parentLabelId+"", (parentPos-childPos+1)+"");
				segmentFeatures.add(segmentLengthFeature);
			}

			String[] words = SMSNPTokenizer.tokenize(input.substring(childPos, parentPos+1), tokenizerMethod);
			int numWords = words.length;
			
			if(FeatureType.NUM_WORDS.enabled()){
				int numWordsFeature = param_g.toFeature(FeatureType.NUM_WORDS.name(), parentLabelId+"", numWords+"");
				segmentFeatures.add(numWordsFeature);
			}
			
			if(FeatureType.WORDS.enabled()){
				for(int i=0; i<words.length; i++){
					segmentFeatures.add(param_g.toFeature(FeatureType.WORDS.name()+":"+i, parentLabelId+"", words[i]));
					segmentFeatures.add(param_g.toFeature(FeatureType.WORDS.name()+":-"+i, parentLabelId+"", words[words.length-i-1]));
				}
			}
			
			if(FeatureType.WORD_SHAPES.enabled()){
				for(int i=0; i<words.length; i++){
					segmentFeatures.add(param_g.toFeature(FeatureType.WORD_SHAPES.name()+":"+i, parentLabelId+"", wordShape(words[i])));
					segmentFeatures.add(param_g.toFeature(FeatureType.WORD_SHAPES.name()+":-"+i, parentLabelId+"", wordShape(words[words.length-i-1])));
				}
			}
			
			if(FeatureType.INSIDE_UNIGRAM.enabled()){
				for(int i=0; i<Math.min(unigramWindowSize, parentPos-childPos+1); i++){
					segmentFeatures.add(param_g.toFeature(FeatureType.INSIDE_UNIGRAM.name()+":"+i, parentLabelId+"",  normalizeCharacter(inputArr[childPos+i]+"")));
					segmentFeatures.add(param_g.toFeature(FeatureType.INSIDE_UNIGRAM.name()+":-"+i, parentLabelId+"",  normalizeCharacter(inputArr[parentPos-i]+"")));
				}
			}
			
			if(FeatureType.INSIDE_SUBSTRING.enabled()){
				for(int i=1; i<Math.min(substringWindowSize, parentPos-childPos+1); i++){
					segmentFeatures.add(param_g.toFeature(FeatureType.INSIDE_SUBSTRING.name()+":"+i, parentLabelId+"",  input.substring(childPos, childPos+i+1)));
					segmentFeatures.add(param_g.toFeature(FeatureType.INSIDE_SUBSTRING.name()+":-"+i, parentLabelId+"",  input.substring(parentPos-i, parentPos+1)));
				}
			}
			
			features = new FeatureArray(listToArray(segmentFeatures), features);
		}
		
		// End to Begin features (transition features)
		if(parentType == NodeType.BEGIN){
			List<Integer> transitionFeatures = new ArrayList<Integer>();
			
			if(FeatureType.OUTSIDE_UNIGRAM.enabled()){
				for(int i=0; i<unigramWindowSize; i++){
					String curInput = "";
					if(parentPos+i < length){
						curInput = inputArr[parentPos+i]+"";
						curInput = normalizeCharacter(curInput);
					}
					transitionFeatures.add(param_g.toFeature(FeatureType.OUTSIDE_UNIGRAM+":"+i, parentLabelId+"", curInput));
					curInput = "";
					if(childPos-i >= 0){
						curInput = inputArr[childPos-i]+"";
						curInput = normalizeCharacter(curInput);
					}
					transitionFeatures.add(param_g.toFeature(FeatureType.OUTSIDE_UNIGRAM+":-"+i, parentLabelId+"", curInput));
				}
			}
			
			if(FeatureType.OUTSIDE_SUBSTRING.enabled()){
				for(int i=1; i<substringWindowSize; i++){
					String curInput = "";
					if(parentPos+i+1 <= length){
						curInput = input.substring(parentPos, parentPos+i+1);
					}
					transitionFeatures.add(param_g.toFeature(FeatureType.OUTSIDE_SUBSTRING+":"+i, parentLabelId+"", curInput));
					curInput = "";
					if(childPos-i >= 0){
						curInput = input.substring(childPos-i, childPos+1);
					}
					transitionFeatures.add(param_g.toFeature(FeatureType.OUTSIDE_SUBSTRING+":-"+i, parentLabelId+"", curInput));
				}
			}
			
			if(FeatureType.DIST_TO_END.enabled()){
				int distToEndFeature = param_g.toFeature(FeatureType.DIST_TO_END.name(), parentLabelId+"", (length-parentPos+1)+"");
				transitionFeatures.add(distToEndFeature);
			}
			
			if(FeatureType.DIST_TO_BEGIN.enabled()){
				int distToBeginFeature = param_g.toFeature(FeatureType.DIST_TO_BEGIN.name(), parentLabelId+"", (childPos+1)+"");
				transitionFeatures.add(distToBeginFeature);
			}
			
			features = new FeatureArray(listToArray(transitionFeatures), features);
		}
		
		return features;
		
	}
	
	private static String normalizeCharacter(String character){
		if(character.matches("\\d")){
			character = "0";
		}
		return character;
	}
	
	private static String wordShape(String word){
		if(word.length() == 0){
			return word;
		}
		String result = "";
		int length = word.length();
		for(int i=0; i<length; i++){
			result += characterShape(word.substring(i, i+1));
		}
		result = result.replaceAll("(.{1,2})\\1{3,}", "$1$1$1");
		result = result.replaceAll("(.{3,})\\1{2,}", "$1$1");
		return result;
	}
	
	private static String characterShape(String character){
		if(character.matches("[A-Z]")){
			return "X";
		} else if(character.matches("[a-z]")){
			return "x";
		} else if(character.matches("[0-9]")){
			return "0";
		} else {
			return character;
		}
	}
	
	private static int[] listToArray(List<Integer> list){
		int[] result = new int[list.size()];
		for(int i=0; i<list.size(); i++){
			result[i] = list.get(i);
		}
		return result;
	}
	
	public static void main(String[] args){
		for(String word: new String[]{"I", "went", "'m", "WHERE?", "HERE", "!", ".", "123-5678", "<#>", "Result", "do", "a0a0a0a0", "1234-5678-9012-1234-5678"}){
			System.out.println(word+" "+wordShape(word));
		}
	}

}
