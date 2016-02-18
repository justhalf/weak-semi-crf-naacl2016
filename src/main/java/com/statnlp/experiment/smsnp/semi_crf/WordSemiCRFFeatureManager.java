package com.statnlp.experiment.smsnp.semi_crf;

import static com.statnlp.experiment.smsnp.SMSNPUtil.setupFeatures;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.statnlp.experiment.smsnp.IFeatureType;
import com.statnlp.experiment.smsnp.SMSNPInstance;
import com.statnlp.experiment.smsnp.SMSNPNetwork;
import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;
import com.statnlp.experiment.smsnp.SMSNPUtil;
import com.statnlp.experiment.smsnp.semi_crf.WordSemiCRFNetworkCompiler.NodeType;
import com.statnlp.hybridnetworks.FeatureArray;
import com.statnlp.hybridnetworks.FeatureManager;
import com.statnlp.hybridnetworks.GlobalNetworkParam;
import com.statnlp.hybridnetworks.Network;

import edu.stanford.nlp.util.StringUtils;

public class WordSemiCRFFeatureManager extends FeatureManager {
	
	private static final long serialVersionUID = -4533287027022223693L;
	
	public int prefixLength = 3;
	public int suffixLength = 3;

	public static enum FeatureType implements IFeatureType{
		CHEAT(false),
		
		// Segment features
		SEGMENT, // The string inside the segment
		NUM_WORDS, // Number of words
		SEGMENT_PREFIX, // The prefix of the segment
		SEGMENT_SUFFIX, // The suffix of the segment
		
		FIRST_WORD(true), // The first word inside the segment
		FIRST_WORD_CLUSTER, // The brown cluster for the first word
		LAST_WORD(true), // The last word inside the segment
		LAST_WORD_CLUSTER, // The brown cluster for the last word
		WORDS, // Words inside the segment, indexed from the segment start and from segment end
		WORD_SHAPES, // The shape of the words inside the segment
		WORD_CLUSTERS, // The clusters of the words inside the segment
		
		// Transition features
		BIGRAM(true),

		// Any
		
		PREV_WORD, // The word ending at start boundary
		PREV_WORD_SHAPE, // The shape of the previous word
		PREV_WORD_CLUSTER, // The brown cluster for the previous word
		NEXT_WORD(true), // The word starting at the end boundary
		NEXT_WORD_SHAPE, // The shape of the next word
		NEXT_WORD_CLUSTER, // The brown cluster for the next word
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
		HELP(0,
				"Print this help message",
				"h,help"),
		PREFIX_LENGTH(1,
				"The maximum prefix lengths for word prefix features",
				"prefix_length",
				"n"),
		SUFFIX_LENGTH(1,
				"The maximum suffix lengths for word suffix features",
				"suffix_length",
				"n"),
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
	
	public TokenizerMethod tokenizerMethod;
	public Map<String, String> brownMap;
	
	public WordSemiCRFFeatureManager(GlobalNetworkParam param_g, String[] features){
		this(param_g, TokenizerMethod.REGEX, null, features);
	}

	public WordSemiCRFFeatureManager(GlobalNetworkParam param_g, TokenizerMethod tokenizerMethod, Map<String, String> brownMap, String[] features, String... args) {
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
				case HELP:
					Argument.printHelp();
					System.exit(0);
				case PREFIX_LENGTH:
					prefixLength = Integer.parseInt(args[argIndex+1]);
					break;
				case SUFFIX_LENGTH:
					suffixLength = Integer.parseInt(args[argIndex+1]);
					break;
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
		
		String[] inputTokenized = instance.getInputTokenized();
		int length = inputTokenized.length;
		
		int[] parent_arr = network.getNodeArray(parent_k);
		int parentPos = parent_arr[0]-1;
		NodeType parentType = NodeType.values()[parent_arr[1]];
		int parentLabelId = parent_arr[2]-1;
		
		if(parentType == NodeType.LEAF || (parentType == NodeType.ROOT && parentPos < length)){
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
		
		String[] wordsBefore = Arrays.copyOfRange(inputTokenized, 0, childPos+1);
		String[] wordsInside = Arrays.copyOfRange(inputTokenized, childPos+1, Math.min(parentPos+1, length));
		String[] wordsAfter = Arrays.copyOfRange(inputTokenized, Math.min(parentPos+1, length), length);
		int numWordsBefore = wordsBefore.length;
		int numWordsInside = wordsInside.length;
		int numWordsAfter = wordsAfter.length;
		String segment = StringUtils.join(wordsInside, " ");
		
		String prevWord = numWordsBefore > 0 ? wordsBefore[numWordsBefore-1] : "";
		String nextWord = numWordsAfter > 0 ? wordsAfter[0] : "";
		
		if(FeatureType.PREV_WORD.enabled()){
			int prevWordFeature = param_g.toFeature(FeatureType.PREV_WORD.name(), parentLabelId+"", normalizeWord(prevWord));
			commonFeatures.add(prevWordFeature);
		}
		if(FeatureType.PREV_WORD_SHAPE.enabled()){
			int prevWordShapeFeature = param_g.toFeature(FeatureType.PREV_WORD_SHAPE.name(), parentLabelId+"", wordShape(prevWord));
			commonFeatures.add(prevWordShapeFeature);
		}
		if(FeatureType.PREV_WORD_CLUSTER.enabled()){
			int prevWordClusterFeature = param_g.toFeature(FeatureType.PREV_WORD_CLUSTER.name(), parentLabelId+"", getBrownCluster(prevWord));
			commonFeatures.add(prevWordClusterFeature);
		}
		if(FeatureType.NEXT_WORD.enabled()){
			int nextWordFeature = param_g.toFeature(FeatureType.NEXT_WORD.name(), parentLabelId+"", normalizeWord(nextWord));
			commonFeatures.add(nextWordFeature);
		}
		if(FeatureType.NEXT_WORD_SHAPE.enabled()){
			int nextWordShapeFeature = param_g.toFeature(FeatureType.NEXT_WORD_SHAPE.name(), parentLabelId+"", wordShape(nextWord));
			commonFeatures.add(nextWordShapeFeature);
		}
		if(FeatureType.NEXT_WORD_CLUSTER.enabled()){
			int nextWordClusterFeature = param_g.toFeature(FeatureType.NEXT_WORD_CLUSTER.name(), parentLabelId+"", getBrownCluster(nextWord));
			commonFeatures.add(nextWordClusterFeature);
		}

		FeatureArray features = new FeatureArray(SMSNPUtil.listToArray(commonFeatures));
		
		// Segment features
		if(parentType != NodeType.ROOT){
			List<Integer> segmentFeatures = new ArrayList<Integer>();
	
			if(FeatureType.SEGMENT.enabled()){
				int segmentFeature = param_g.toFeature(FeatureType.SEGMENT.name(), parentLabelId+"", segment);
				segmentFeatures.add(segmentFeature);
			}
			
			if(FeatureType.SEGMENT_PREFIX.enabled()){
				for(int i=0; i<prefixLength; i++){
					String prefix = segment.substring(0, Math.min(segment.length(), i+1));
					int segmentPrefixFeature = param_g.toFeature(FeatureType.SEGMENT_PREFIX+"-"+i, parentLabelId+"", prefix);
					segmentFeatures.add(segmentPrefixFeature);
				}
			}
			
			if(FeatureType.SEGMENT_SUFFIX.enabled()){
				for(int i=0; i<prefixLength; i++){
					String suffix = segment.substring(Math.max(segment.length()-i-1, 0));
					int segmentSuffixFeature = param_g.toFeature(FeatureType.SEGMENT_SUFFIX+"-"+i, parentLabelId+"", suffix);
					segmentFeatures.add(segmentSuffixFeature);
				}
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
			
			if(FeatureType.LAST_WORD.enabled()){
				segmentFeatures.add(param_g.toFeature(FeatureType.LAST_WORD.name(), parentLabelId+"", numWordsInside > 0 ? normalizeWord(wordsInside[numWordsInside-1]) : ""));
			}
			
			if(FeatureType.LAST_WORD_CLUSTER.enabled()){
				segmentFeatures.add(param_g.toFeature(FeatureType.LAST_WORD_CLUSTER.name(), parentLabelId+"", getBrownCluster(numWordsInside > 0 ? wordsInside[numWordsInside-1] : "")));
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
			
			if(FeatureType.WORD_CLUSTERS.enabled()){
				for(int i=0; i<wordsInside.length; i++){
					segmentFeatures.add(param_g.toFeature(FeatureType.WORD_CLUSTERS.name()+":"+i, parentLabelId+"", getBrownCluster(wordsInside[i])));
					segmentFeatures.add(param_g.toFeature(FeatureType.WORD_CLUSTERS.name()+":-"+i, parentLabelId+"", getBrownCluster(wordsInside[numWordsInside-i-1])));
				}
			}
	
			features = new FeatureArray(SMSNPUtil.listToArray(segmentFeatures), features);
		}
		
		// Transition features
		List<Integer> transitionFeatures = new ArrayList<Integer>();
		if(FeatureType.BIGRAM.enabled()){
			int bigramFeature = param_g.toFeature(FeatureType.BIGRAM.name(), childLabelId+"-"+parentLabelId, "");
			transitionFeatures.add(bigramFeature);
		}
		features = new FeatureArray(SMSNPUtil.listToArray(transitionFeatures), features);

		return features;
		
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
	
	private void writeObject(ObjectOutputStream oos) throws IOException{
		oos.writeObject(tokenizerMethod);
		oos.writeObject(brownMap);
		for(FeatureType featureType: FeatureType.values()){
			oos.writeBoolean(featureType.isEnabled);
		}
		oos.writeInt(prefixLength);
		oos.writeInt(suffixLength);
	}
	
	@SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException{
		tokenizerMethod = (TokenizerMethod)ois.readObject();
		brownMap = (Map<String, String>)ois.readObject();
		for(FeatureType featureType: FeatureType.values()){
			featureType.isEnabled = ois.readBoolean();
		}
		prefixLength = ois.readInt();
		suffixLength = ois.readInt();
	}

}
