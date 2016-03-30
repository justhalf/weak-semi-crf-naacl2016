package com.statnlp.experiment.smsnp.semi_crf;

import static com.statnlp.experiment.smsnp.SMSNPUtil.listToArray;
import static com.statnlp.experiment.smsnp.SMSNPUtil.setupFeatures;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.statnlp.experiment.smsnp.IFeatureType;
import com.statnlp.experiment.smsnp.SMSNPInstance;
import com.statnlp.experiment.smsnp.SMSNPNetwork;
import com.statnlp.experiment.smsnp.SMSNPTokenizer;
import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;
import com.statnlp.experiment.smsnp.semi_crf.CharSemiCRFNetworkCompiler.NodeType;
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
public class CharSemiCRFFeatureManager extends FeatureManager {
	
	private static final long serialVersionUID = -4533287027022223693L;

	public static enum FeatureType implements IFeatureType{
		CHEAT(false),
		
		// Begin to End features (on segment)
		SEGMENT, // The string inside the segment
		SEGMENT_LENGTH, // The segment length
		NUM_WORDS(true), // Number of words
		
		INSIDE_UNIGRAM, // The characters inside the segment, the window size can be varied
		INSIDE_SUBSTRING, // The substrings found with the same start or the same end as the segment
		FIRST_WORD(true), // The first word inside the segment
		FIRST_WORD_CLUSTER, // The brown cluster for the first word
		WORDS, // Words inside the segment, indexed from the segment start and from segment end
		WORD_SHAPES, // The shape of the words inside the segment
		
		// End to Begin features (on transition)

		DIST_TO_END, // Distance to end of input
		DIST_TO_BEGIN, // Distance to beginning of input
		OUTSIDE_UNIGRAM, // The character window to the left and right of transition boundary
		OUTSIDE_SUBSTRING, // The substring window to the left and right of transition boundary
		
		// Any
		
		PREV_WORD(true), // The word ending at start boundary
		PREV_WORD_SHAPE, // The shape of the previous word
		PREV_WORD_CLUSTER, // The brown cluster for the previous word
		START_BOUNDARY_WORD(true), // If the previous character is not space, get the word crossing the begin boundary
		START_BOUNDARY_WORD_CLUSTER, // The brown cluster for the start boundary word
		END_BOUNDARY_WORD, // If the next character is not space, get the word crossing the end boundary
		NEXT_WORD, // The word starting at the end boundary
		NEXT_WORD_SHAPE, // The shape of the next word
		NEXT_WORD_CLUSTER, // The brown cluster for the next word
		
		BIGRAM(true),
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
	
	public CharSemiCRFFeatureManager(GlobalNetworkParam param_g, String[] disabledFeatures){
		this(param_g, TokenizerMethod.WHITESPACE, null, disabledFeatures);
	}

	public CharSemiCRFFeatureManager(GlobalNetworkParam param_g, TokenizerMethod tokenizerMethod, Map<String, String> brownMap, String[] features, String... args) {
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
		
		if(FeatureType.BIGRAM.enabled()){
			int bigramFeature = param_g.toFeature(FeatureType.BIGRAM.name(), childLabelId+"-"+parentLabelId, "");
			commonFeatures.add(bigramFeature);
		}
		
		int startSegment = childPos+1;
		int endSegment = parentPos+1;
		String segment = input.substring(startSegment, endSegment);
		
		String[] wordsBefore = SMSNPTokenizer.tokenize(input.substring(0, startSegment), tokenizerMethod);
		String[] wordsInside = SMSNPTokenizer.tokenize(input.substring(startSegment, endSegment), tokenizerMethod);
		String[] wordsAfter = SMSNPTokenizer.tokenize(input.substring(endSegment), tokenizerMethod);
		int numWordsBefore = wordsBefore.length;
		int numWordsInside = wordsInside.length;
		int numWordsAfter = wordsAfter.length;
		
		String prevWord = numWordsBefore > 0 ? wordsBefore[numWordsBefore-1] : "";
		String nextWord = numWordsAfter > 0 ? wordsAfter[0] : "";
		if(childType != NodeType.LEAF){
			if(FeatureType.PREV_WORD.enabled()){
				int prevWordFeature = param_g.toFeature(FeatureType.PREV_WORD.name(), parentType == NodeType.ROOT ? childLabelId+"" : parentLabelId+"", normalizeWord(prevWord));
				commonFeatures.add(prevWordFeature);
			}
			if(FeatureType.PREV_WORD_SHAPE.enabled()){
				int prevWordShapeFeature = param_g.toFeature(FeatureType.PREV_WORD_SHAPE.name(), parentType == NodeType.ROOT ? childLabelId+"" : parentLabelId+"", wordShape(prevWord));
				commonFeatures.add(prevWordShapeFeature);
			}
			if(FeatureType.PREV_WORD_CLUSTER.enabled()){
				int prevWordClusterFeature = param_g.toFeature(FeatureType.PREV_WORD_CLUSTER.name(), parentType == NodeType.ROOT ? childLabelId+"" : parentLabelId+"", getBrownCluster(prevWord));
				commonFeatures.add(prevWordClusterFeature);
			}
		}
		if(parentType != NodeType.ROOT){
			if(FeatureType.NEXT_WORD.enabled()){
				int nextWordFeature = param_g.toFeature(FeatureType.NEXT_WORD.name(), parentLabelId+"", normalizeWord(nextWord));
				commonFeatures.add(nextWordFeature);
			}
			if(FeatureType.NEXT_WORD_SHAPE.enabled()){
				int nextWordShapeFeature = param_g.toFeature(FeatureType.NEXT_WORD_SHAPE.name(), parentLabelId+"", wordShape(nextWord));
				commonFeatures.add(nextWordShapeFeature);
			}
			if(FeatureType.NEXT_WORD_CLUSTER.enabled()){
				int nextWordClusterFeature = param_g.toFeature(FeatureType.NEXT_WORD_CLUSTER.name(), parentType == NodeType.ROOT ? childLabelId+"" : parentLabelId+"", getBrownCluster(nextWord));
				commonFeatures.add(nextWordClusterFeature);
			}
		}

		String startBoundaryWord = null;
		if(FeatureType.START_BOUNDARY_WORD.enabled()){
			String leftWord = numWordsBefore > 0 && input.charAt(startSegment-1) != ' ' ? wordsBefore[numWordsBefore-1] : "";
			String rightWord = numWordsInside > 0 && input.charAt(startSegment) != ' ' ? wordsInside[0] : "";
			String[] startBoundaryTokens = SMSNPTokenizer.tokenize(leftWord+rightWord, tokenizerMethod);
			startBoundaryWord = startBoundaryTokens.length > 0 ? startBoundaryTokens[startBoundaryTokens.length-1] : "";
			int startBoundaryWordFeature = param_g.toFeature(FeatureType.START_BOUNDARY_WORD.name(), parentType == NodeType.ROOT ? childLabelId+"-ROOT" : parentLabelId+"", normalizeWord(startBoundaryWord));
			commonFeatures.add(startBoundaryWordFeature);
		}
		
		if(FeatureType.START_BOUNDARY_WORD_CLUSTER.enabled()){
			if(startBoundaryWord == null){
				String leftWord = numWordsBefore > 0 && input.charAt(startSegment-1) != ' ' ? wordsBefore[numWordsBefore-1] : "";
				String rightWord = numWordsInside > 0 && input.charAt(startSegment) != ' ' ? wordsInside[0] : "";
				String[] startBoundaryTokens = SMSNPTokenizer.tokenize(leftWord+rightWord, tokenizerMethod);
				startBoundaryWord = startBoundaryTokens.length > 0 ? startBoundaryTokens[startBoundaryTokens.length-1] : "";
			}
			int startBoundaryWordClusterFeature = param_g.toFeature(FeatureType.START_BOUNDARY_WORD_CLUSTER.name(), parentType == NodeType.ROOT ? childLabelId+"-ROOT" : parentLabelId+"", getBrownCluster(startBoundaryWord));
			commonFeatures.add(startBoundaryWordClusterFeature);
		}
		
		if(FeatureType.END_BOUNDARY_WORD.enabled()){
			String leftWord = numWordsInside > 0 && input.charAt(endSegment-1) != ' ' ? wordsInside[numWordsInside-1] : "";
			String rightWord = numWordsAfter > 0 && input.charAt(endSegment) != ' ' ? wordsAfter[0] : "";
			String[] endBoundaryTokens = SMSNPTokenizer.tokenize(leftWord+rightWord, tokenizerMethod);
			String endBoundaryWord = endBoundaryTokens.length > 0 ? endBoundaryTokens[0] : "";
			int endBoundaryWordFeature = param_g.toFeature(FeatureType.END_BOUNDARY_WORD.name(), parentType == NodeType.ROOT ? childLabelId+"-ROOT" : parentLabelId+"", normalizeWord(endBoundaryWord));
			commonFeatures.add(endBoundaryWordFeature);
		}
		
		FeatureArray features = new FeatureArray(listToArray(commonFeatures));
		
		// Begin to End features (segment features)
		if(parentType != NodeType.ROOT){
			List<Integer> segmentFeatures = new ArrayList<Integer>();
	
			if(FeatureType.SEGMENT.enabled()){
				int segmentFeature = param_g.toFeature(FeatureType.SEGMENT.name(), parentLabelId+"", segment);
				segmentFeatures.add(segmentFeature);
			}
	
			if(FeatureType.SEGMENT_LENGTH.enabled()){
				int segmentLengthFeature = param_g.toFeature(FeatureType.SEGMENT_LENGTH.name(), parentLabelId+"", (endSegment-startSegment)+"");
				segmentFeatures.add(segmentLengthFeature);
			}
	
			if(FeatureType.NUM_WORDS.enabled()){
				int numWordsFeature = param_g.toFeature(FeatureType.NUM_WORDS.name(), parentLabelId+"", numWordsInside+"");
				segmentFeatures.add(numWordsFeature);
			}
			
			if(FeatureType.FIRST_WORD.enabled()){
				segmentFeatures.add(param_g.toFeature(FeatureType.FIRST_WORD.name(), parentLabelId+"", numWordsInside > 0 ? normalizeWord(wordsInside[0]) : ""));
			}
			
			if(FeatureType.FIRST_WORD_CLUSTER.enabled()){
				segmentFeatures.add(param_g.toFeature(FeatureType.FIRST_WORD_CLUSTER.name(), parentLabelId+"", getBrownCluster(numWordsInside > 0 ? wordsInside[0] : "")));
			}
	
			if(FeatureType.WORDS.enabled()){
				for(int i=0; i<wordsInside.length; i++){
					segmentFeatures.add(param_g.toFeature(FeatureType.WORDS.name()+":"+i, parentLabelId+"", normalizeWord(wordsInside[i])));
					segmentFeatures.add(param_g.toFeature(FeatureType.WORDS.name()+":-"+i, parentLabelId+"", normalizeWord(wordsInside[numWordsInside-i-1])));
				}
			}
	
			if(FeatureType.WORD_SHAPES.enabled()){
				for(int i=0; i<wordsInside.length; i++){
					segmentFeatures.add(param_g.toFeature(FeatureType.WORD_SHAPES.name()+":"+i, parentLabelId+"", wordShape(wordsInside[i])));
					segmentFeatures.add(param_g.toFeature(FeatureType.WORD_SHAPES.name()+":-"+i, parentLabelId+"", wordShape(wordsInside[numWordsInside-i-1])));
				}
			}
	
			if(FeatureType.INSIDE_UNIGRAM.enabled()){
				for(int i=0; i<Math.min(unigramWindowSize, endSegment-startSegment); i++){
					segmentFeatures.add(param_g.toFeature(FeatureType.INSIDE_UNIGRAM.name()+":"+i, parentLabelId+"",  normalizeCharacter(inputArr[startSegment+i]+"")));
					segmentFeatures.add(param_g.toFeature(FeatureType.INSIDE_UNIGRAM.name()+":-"+i, parentLabelId+"",  normalizeCharacter(inputArr[endSegment-i-1]+"")));
				}
			}
	
			if(FeatureType.INSIDE_SUBSTRING.enabled()){
				for(int i=1; i<Math.min(substringWindowSize, endSegment-startSegment); i++){
					segmentFeatures.add(param_g.toFeature(FeatureType.INSIDE_SUBSTRING.name()+":"+i, parentLabelId+"",  normalizeWord(input.substring(startSegment, startSegment+i+1))));
					segmentFeatures.add(param_g.toFeature(FeatureType.INSIDE_SUBSTRING.name()+":-"+i, parentLabelId+"",  normalizeWord(input.substring(endSegment-i-1, endSegment))));
				}
			}
	
			features = new FeatureArray(listToArray(segmentFeatures), features);
		}

		// End to Begin features (transition features)
		List<Integer> transitionFeatures = new ArrayList<Integer>();

		if(FeatureType.OUTSIDE_UNIGRAM.enabled()){
			for(int i=0; i<unigramWindowSize; i++){
				String curInput = "";
				if(endSegment+i < length){
					curInput = inputArr[endSegment+i]+"";
					curInput = normalizeCharacter(curInput);
				}
				transitionFeatures.add(param_g.toFeature(FeatureType.OUTSIDE_UNIGRAM+":"+i, parentLabelId+"", curInput));
				curInput = "";
				if(startSegment-i-1 >= 0){
					curInput = inputArr[startSegment-i-1]+"";
					curInput = normalizeCharacter(curInput);
				}
				transitionFeatures.add(param_g.toFeature(FeatureType.OUTSIDE_UNIGRAM+":-"+i, parentLabelId+"", curInput));
			}
		}

		if(FeatureType.OUTSIDE_SUBSTRING.enabled()){
			for(int i=1; i<substringWindowSize; i++){
				String curInput = "";
				if(endSegment+i < length){
					curInput = input.substring(endSegment, endSegment+i+1);
				}
				curInput = normalizeWord(curInput);
				transitionFeatures.add(param_g.toFeature(FeatureType.OUTSIDE_SUBSTRING+":"+i, parentLabelId+"", curInput));
				curInput = "";
				if(startSegment-i-1 >= 0){
					curInput = input.substring(startSegment-i-1, startSegment);
				}
				curInput = normalizeWord(curInput);
				transitionFeatures.add(param_g.toFeature(FeatureType.OUTSIDE_SUBSTRING+":-"+i, parentLabelId+"", curInput));
			}
		}

		// Are these features really needed?
		if(parentType != NodeType.ROOT && FeatureType.DIST_TO_END.enabled()){
			int dist = length-endSegment;
			if(dist >= 3){
				dist = 3;
			}
			int distToEndFeature = param_g.toFeature(FeatureType.DIST_TO_END.name(), parentLabelId+"", dist+"");
			transitionFeatures.add(distToEndFeature);
		}

		if(childType != NodeType.LEAF && FeatureType.DIST_TO_BEGIN.enabled()){
			int dist = startSegment;
			if(dist >= 3){
				dist = 3;
			}
			int distToBeginFeature = param_g.toFeature(FeatureType.DIST_TO_BEGIN.name(), parentLabelId+"", dist+"");
			transitionFeatures.add(distToBeginFeature);
		}

		features = new FeatureArray(listToArray(transitionFeatures), features);

		return features;
		
	}
	
	private static String normalizeCharacter(String character){
		if(character.matches("\\d")){
			character = "0";
		}
		return character;
	}
	
	private static String normalizeWord(String word){
		return word.toLowerCase().replaceAll("(.{1,2})\\1{3,}", "$1$1$1");
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
	
	private String getBrownCluster(String word){
		if(brownMap == null){
			throw new NullPointerException("Feature requires brown clusters but no brown clusters info is provided");
		}
		String clusterId = brownMap.get(word);
		if(clusterId == null){
			clusterId = "X";
		}
		return clusterId;
	}

}
