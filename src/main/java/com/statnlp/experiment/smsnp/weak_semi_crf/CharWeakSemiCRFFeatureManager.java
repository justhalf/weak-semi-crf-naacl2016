package com.statnlp.experiment.smsnp.weak_semi_crf;

import static com.statnlp.experiment.smsnp.SMSNPUtil.listToArray;
import static com.statnlp.experiment.smsnp.SMSNPUtil.setupFeatures;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.statnlp.example.semi_crf.SemiCRFNetworkCompiler.NodeType;
import com.statnlp.experiment.smsnp.IFeatureType;
import com.statnlp.experiment.smsnp.SMSNPInstance;
import com.statnlp.experiment.smsnp.SMSNPNetwork;
import com.statnlp.experiment.smsnp.SMSNPTokenizer;
import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;
import com.statnlp.hybridnetworks.FeatureArray;
import com.statnlp.hybridnetworks.FeatureManager;
import com.statnlp.hybridnetworks.GlobalNetworkParam;
import com.statnlp.hybridnetworks.Network;

import edu.stanford.nlp.util.StringUtils;

/**
 * The class that defines the features to be extracted<br>
 * An attempt to use character-based model CRF.<br>
 * Discontinued after seeing that initial result was not promising<br>
 * This is based on StatNLP framework for CRF on acyclic graphs
 * @author Aldrian Obaja <aldrianobaja.m@gmail.com>
 *
 */
public class CharWeakSemiCRFFeatureManager extends FeatureManager {
	
	private static final long serialVersionUID = 6510131496948610905L;
	
	public static enum FeatureType implements IFeatureType{
		CHEAT(false),
		
		// Begin to End features (on segment)
		SEGMENT, // The string inside the segment
		SEGMENT_LENGTH, // The segment length
		NUM_WORDS(true), // Number of words
		
		INSIDE_UNIGRAM, // The characters inside the segment, the window size can be varied
		INSIDE_SUBSTRING, // The substrings found with the same start or the same end as the segment
		WORDS(true), // Words inside the segment, indexed from the segment start and from segment end
		WORD_SHAPES, // The shape of the words inside the segment
		
		// End to Begin features (on transition)

		DIST_TO_END, // Distance to end of input
		DIST_TO_BEGIN, // Distance to beginning of input
		OUTSIDE_UNIGRAM, // The character window to the left and right of transition boundary
		OUTSIDE_SUBSTRING, // The substring window to the left and right of transition boundary
		BIGRAM(true),
		
		// Any
		
		PREV_WORD(true), // The word ending at start boundary
		PREV_WORD_SHAPE, // The shape of the previous word
		START_BOUNDARY_WORD(true), // If the previous character is not space, get the word crossing the begin boundary
		END_BOUNDARY_WORD, // If the next character is not space, get the word crossing the end boundary
		NEXT_WORD, // The word starting at the end boundary
		NEXT_WORD_SHAPE, // The shape of the next word
		;
		
		private boolean isEnabled;
		
		private FeatureType(){
			this(false);
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

	private static enum Argument{
		UNIGRAM_WINDOW_SIZE(1,
				"The window size for unigram character features",
				"unigram_window_size",
				"n"),
		SUBSTRING_WINDOW_SIZE(1,
				"The window size for substring character features",
				"substring_window_size",
				"n"),
		HELP(0,
				"Print this help message",
				"h,help"),
		;
		
		final private int numArgs;
		final private String[] argNames;
		final private String[] names;
		final private String help;
		private Argument(int numArgs, String help, String names, String... argNames){
			this.numArgs = numArgs;
			this.argNames = argNames;
			this.names = names.split(",");
			this.help = help;
		}
		
		/**
		 * Return the Argument which has the specified name
		 * @param name
		 * @return
		 */
		public static Argument argWithName(String name){
			for(Argument argument: Argument.values()){
				for(String argName: argument.names){
					if(argName.equals(name)){
						return argument;
					}
				}
			}
			throw new IllegalArgumentException("Unrecognized argument: "+name);
		}
		
		/**
		 * Print help message
		 */
		private static void printHelp(){
			StringBuilder result = new StringBuilder();
			result.append("Options:\n");
			for(Argument argument: Argument.values()){
				result.append("-"+StringUtils.join(argument.names, " -"));
				result.append(" "+StringUtils.join(argument.argNames, " "));
				result.append("\n");
				if(argument.help != null && argument.help.length() > 0){
					result.append("\t"+argument.help.replaceAll("\n","\n\t")+"\n");
				}
			}
			System.out.println(result.toString());
		}
	}
	
	public int unigramWindowSize = 3;
	public int substringWindowSize = 3;
	
	public TokenizerMethod tokenizerMethod;
	public Map<String, String> brownMap;
	
	public CharWeakSemiCRFFeatureManager(GlobalNetworkParam param_g, String[] features){
		this(param_g, TokenizerMethod.WHITESPACE, null, features);
	}

	public CharWeakSemiCRFFeatureManager(GlobalNetworkParam param_g, TokenizerMethod tokenizerMethod, Map<String, String> brownMap, String[] features, String... args) {
		super(param_g);
		this.tokenizerMethod = tokenizerMethod;
		this.brownMap = brownMap;
		setupFeatures(FeatureType.class, features);
		int argIndex = 0;
		while(argIndex < args.length){
			String arg = args[argIndex];
			if(arg.length() > 0 && arg.charAt(0) == '-'){
				Argument argument = Argument.argWithName(args[argIndex].substring(1));
				switch(argument){
				case UNIGRAM_WINDOW_SIZE:
					unigramWindowSize = Integer.parseInt(args[argIndex+1]);
					break;
				case SUBSTRING_WINDOW_SIZE:
					substringWindowSize = Integer.parseInt(args[argIndex+1]);
					break;
				case HELP:
					Argument.printHelp();
					System.exit(0);
				}
				argIndex += argument.numArgs+1;
			} else {
				throw new IllegalArgumentException("Error while parsing: "+arg);
			}
		}
	}
	
	@Override
	protected FeatureArray extract_helper(Network net, int parent_k, int[] children_k) {
		SMSNPNetwork network = (SMSNPNetwork)net;
		SMSNPInstance instance = (SMSNPInstance)network.getInstance();
		
		String input = instance.input;
		char[] inputArr = input.toCharArray();
		int length = input.length();
		
		int[] parent_arr = network.getNodeArray(parent_k);
		int parentPos = parent_arr[0]-1;
		NodeType parentType = NodeType.values()[parent_arr[1]];
		int parentLabelId = parent_arr[2]-1;
		
		if(parentType == NodeType.LEAF || (parentType == NodeType.ROOT && parentPos < length-1)){
			return FeatureArray.EMPTY;
		}
		
		int[] child_arr = network.getNodeArray(children_k[0]);
		int childPos = child_arr[0]-1;
		NodeType childType = NodeType.values()[child_arr[1]];
		int childLabelId = child_arr[2]-1;

		GlobalNetworkParam param_g = this._param_g;
		
		if(FeatureType.CHEAT.enabled()){
			int instanceId = Math.abs(instance.getInstanceId());
			int cheatFeature = param_g.toFeature(FeatureType.CHEAT.name(), "", instanceId+" "+parentPos+" "+childPos+" "+parentLabelId+" "+childLabelId);
			return new FeatureArray(new int[]{cheatFeature});
		}
		
		List<Integer> commonFeatures = new ArrayList<Integer>();
		
		int startBoundary = (parentType != NodeType.BEGIN) ? childPos : childPos+1;
		int endBoundary = (childType != NodeType.END) ? parentPos : parentPos-1;
		int prevWordStart = input.lastIndexOf(' ', startBoundary-2)+1;
		int prevWordEnd = (startBoundary > 0 && inputArr[startBoundary-1] == ' ') ? startBoundary-1 : startBoundary;
		int nextWordEnd = input.indexOf(' ', endBoundary+2);
		if(nextWordEnd == -1){
			nextWordEnd = length;
		}
		int nextWordStart = (endBoundary < length-1 && inputArr[endBoundary+1] == ' ') ? endBoundary+2 : endBoundary+1;
		
		if(childType != NodeType.LEAF && FeatureType.PREV_WORD.enabled()){
			int prevWordFeature = param_g.toFeature(FeatureType.PREV_WORD.name(), parentLabelId+"", input.substring(prevWordStart, prevWordEnd));
			commonFeatures.add(prevWordFeature);
		}
		if(parentType != NodeType.ROOT && FeatureType.NEXT_WORD.enabled()){
			int nextWordFeature = param_g.toFeature(FeatureType.NEXT_WORD.name(), parentLabelId+"", input.substring(nextWordStart, nextWordEnd));
			commonFeatures.add(nextWordFeature);
		}
		if(childType != NodeType.LEAF && FeatureType.PREV_WORD_SHAPE.enabled()){
			int prevWordShapeFeature = param_g.toFeature(FeatureType.PREV_WORD_SHAPE.name(), parentLabelId+"", wordShape(input.substring(nextWordStart, nextWordEnd)));
			commonFeatures.add(prevWordShapeFeature);
		}
		if(parentType != NodeType.ROOT && FeatureType.NEXT_WORD_SHAPE.enabled()){
			int nextWordShapeFeature = param_g.toFeature(FeatureType.NEXT_WORD_SHAPE.name(), parentLabelId+"", wordShape(input.substring(nextWordStart, nextWordEnd)));
			commonFeatures.add(nextWordShapeFeature);
		}

		if(FeatureType.START_BOUNDARY_WORD.enabled()){
			if(startBoundary > length-1 || (startBoundary > 0 && inputArr[startBoundary] != ' ' && inputArr[startBoundary-1] != ' ')){
				int startWordBoundaryEnd = input.indexOf(' ', childPos);
				if(startWordBoundaryEnd == -1){
					startWordBoundaryEnd = nextWordStart-1;
				}
				int startBoundaryWordFeature = param_g.toFeature(FeatureType.START_BOUNDARY_WORD.name(), parentLabelId+"", input.substring(prevWordStart, startWordBoundaryEnd));
				commonFeatures.add(startBoundaryWordFeature);
			}
		}
		
		if(FeatureType.END_BOUNDARY_WORD.enabled()){
			if(endBoundary < 0 || (endBoundary < length-1 && inputArr[endBoundary] != ' ' && inputArr[endBoundary+1] != ' ')){
				int endWordBoundaryStart = input.lastIndexOf(' ', parentPos);
				if(endWordBoundaryStart == -1){
					endWordBoundaryStart = prevWordEnd;
				}
				endWordBoundaryStart += 1;
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
		if(parentType == NodeType.BEGIN || childType == NodeType.END){
			List<Integer> transitionFeatures = new ArrayList<Integer>();
			
			if(FeatureType.BIGRAM.enabled()){
				int bigramFeature = param_g.toFeature(FeatureType.BIGRAM.name(), childLabelId+"-"+parentLabelId, "");
				transitionFeatures.add(bigramFeature);
			}
			
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
			
			// Are these features really needed?
			if(parentType != NodeType.ROOT && FeatureType.DIST_TO_END.enabled()){
				int dist = length-parentPos;
				if(dist >= 3){
					dist = 3;
				}
				int distToEndFeature = param_g.toFeature(FeatureType.DIST_TO_END.name(), parentLabelId+"", dist+"");
				transitionFeatures.add(distToEndFeature);
			}
			
			if(childType != NodeType.LEAF && FeatureType.DIST_TO_BEGIN.enabled()){
				int dist = childPos+1;
				if(dist >= 3){
					dist = 3;
				}
				int distToBeginFeature = param_g.toFeature(FeatureType.DIST_TO_BEGIN.name(), parentLabelId+"", dist+"");
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
	
	public static void main(String[] args){
		for(String word: new String[]{"I", "went", "'m", "WHERE?", "HERE", "!", ".", "123-5678", "<#>", "Result", "do", "a0a0a0a0", "1234-5678-9012-1234-5678"}){
			System.out.println(word+" "+wordShape(word));
		}
	}

}
