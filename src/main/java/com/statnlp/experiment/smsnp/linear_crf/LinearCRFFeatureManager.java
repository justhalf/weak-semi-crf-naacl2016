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

import static com.statnlp.experiment.smsnp.SMSNPUtil.setupFeatures;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import com.statnlp.experiment.smsnp.IFeatureType;
import com.statnlp.experiment.smsnp.SMSNPInstance;
import com.statnlp.experiment.smsnp.SMSNPNetwork;
import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;
import com.statnlp.experiment.smsnp.linear_crf.LinearCRFNetworkCompiler.NodeType;
import com.statnlp.hybridnetworks.FeatureArray;
import com.statnlp.hybridnetworks.FeatureManager;
import com.statnlp.hybridnetworks.GlobalNetworkParam;
import com.statnlp.hybridnetworks.Network;
import com.statnlp.hybridnetworks.NetworkIDMapper;

import edu.stanford.nlp.util.StringUtils;

/**
 * 
 * @author Aldrian Obaja <aldrianobaja.m@gmail.com>
 *
 */
public class LinearCRFFeatureManager extends FeatureManager{

	private static final long serialVersionUID = -4880581521293400351L;
	
	public int wordHalfWindowSize = 1;
	public int prefixLength = 3;
	public int suffixLength = 3;
	public boolean wordOnlyLeftWindow = false;
	
	public enum FeatureType implements IFeatureType {
		CHEAT(false),
		
		WORD(true),
		WORD_BIGRAM(true),
		PREFIX(true),
		SUFFIX(true),
		BROWN_CLUSTER(false),
		WORD_SHAPE(false),
		
		TRANSITION(true),
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
		WORD_HALF_WINDOW_SIZE(1,
				"The half window size for unigram word features",
				"word_half_window_size",
				"n"),
		PREFIX_LENGTH(1,
				"The maximum prefix lengths for word prefix features",
				"prefix_length",
				"n"),
		SUFFIX_LENGTH(1,
				"The maximum suffix lengths for word suffix features",
				"suffix_length",
				"n"),
		WORD_ONLY_LEFT_WINDOW(0,
				"Whether to use only the left window for word features",
				"word_only_left_window"),
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
	
	public TokenizerMethod tokenizerMethod;
	public Map<String, String> brownMap;

	/**
	 * @param param_g
	 */
	public LinearCRFFeatureManager(GlobalNetworkParam param_g, TokenizerMethod tokenizerMethod, Map<String, String> brownMap, String[] features, String... args) {
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
				case WORD_HALF_WINDOW_SIZE:
					wordHalfWindowSize = Integer.parseInt(args[argIndex+1]);
					break;
				case PREFIX_LENGTH:
					prefixLength = Integer.parseInt(args[argIndex+1]);
					break;
				case SUFFIX_LENGTH:
					suffixLength = Integer.parseInt(args[argIndex+1]);
					break;
				case WORD_ONLY_LEFT_WINDOW:
					wordOnlyLeftWindow = true;
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
	protected FeatureArray extract_helper(Network network, int parent_k, int[] children_k) {
		SMSNPNetwork net = (SMSNPNetwork)network;
		SMSNPInstance instance = (SMSNPInstance)net.getInstance();
		String[] words = instance.getInputTokenized();
		int size = words.length;
		
		long curNode = net.getNode(parent_k);
		int[] arr = NetworkIDMapper.toHybridNodeArray(curNode);
		
		int pos = arr[0]-1;
		int tag_id = arr[1]-1;
		int nodeType = arr[4];
		
		if(nodeType == NodeType.LEAF.ordinal() || (nodeType == NodeType.ROOT.ordinal() && pos < size-1)){
			return FeatureArray.EMPTY;
		}
		
		GlobalNetworkParam param_g = this._param_g;
		
		int child_tag_id = network.getNodeArray(children_k[0])[1]-1;
		
		if(FeatureType.CHEAT.enabled()){
			int cheatFeature = param_g.toFeature(FeatureType.CHEAT.name(), tag_id+"", Math.abs(instance.getInstanceId())+" "+pos+""+child_tag_id);
			return new FeatureArray(new int[]{cheatFeature});
		}

		FeatureArray features = new FeatureArray(new int[0]);
		// Word window features
		if(FeatureType.WORD.enabled() && nodeType != NodeType.ROOT.ordinal()){
			int wordWindowSize = wordHalfWindowSize*2+1;
			if(wordWindowSize < 0){
				wordWindowSize = 0;
			}
			int[] wordWindowFeatures = new int[wordWindowSize];
			int[] wordShapeWindowFeatures = new int[wordWindowSize];
			for(int i=0; i<wordWindowFeatures.length; i++){
				String word = "***";
				int relIdx = (i-wordHalfWindowSize);
				int idx = pos + relIdx;
				if(idx >= 0 && idx < size){
					word = words[idx];
				}
				if(wordOnlyLeftWindow && idx > pos) continue;
				wordWindowFeatures[i] = param_g.toFeature(FeatureType.WORD+":"+relIdx, tag_id+"", word);
				if(FeatureType.WORD_SHAPE.enabled()){
					wordShapeWindowFeatures[i] = param_g.toFeature(FeatureType.WORD_SHAPE+":"+relIdx, tag_id+"", wordShape(word));
				}
			}
			FeatureArray wordFeatures = new FeatureArray(wordWindowFeatures, features);
			features = wordFeatures;
			if(FeatureType.WORD_SHAPE.enabled()){
				features = new FeatureArray(wordShapeWindowFeatures, features);
			}
		}
		
		if(FeatureType.BROWN_CLUSTER.enabled()){
			int brownClusterFeature = param_g.toFeature(FeatureType.BROWN_CLUSTER.name(), tag_id+"", getBrownCluster(words[pos]));
			features = new FeatureArray(new int[]{brownClusterFeature}, features);
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
		
		if(FeatureType.PREFIX.enabled()){
			String curWord = words[pos];
			int[] prefixFeatures = new int[3];
			for(int i=0; i<prefixLength; i++){
				String prefix = curWord.substring(0, Math.min(curWord.length(), i+1));
				prefixFeatures[i] = param_g.toFeature(FeatureType.PREFIX+"", tag_id+"", prefix);
			}
			features = new FeatureArray(prefixFeatures, features);
		}
		
		if(FeatureType.SUFFIX.enabled()){
			String curWord = words[pos];
			int[] suffixFeatures = new int[3];
			for(int i=0; i<suffixLength; i++){
				String suffix = curWord.substring(Math.max(0, curWord.length()-i-1), curWord.length());
				suffixFeatures[i] = param_g.toFeature(FeatureType.SUFFIX+"", tag_id+"", suffix);
			}
			features = new FeatureArray(suffixFeatures, features);
		}
		
		// Label transition feature
		if(FeatureType.TRANSITION.enabled()){
			if(child_tag_id == -1){
				
			} else {
				int transitionFeature = param_g.toFeature(FeatureType.TRANSITION.name(), child_tag_id+"-"+tag_id, "");
				features = new FeatureArray(new int[]{transitionFeature}, features);
			}
		}
		
		return features;
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
		oos.writeInt(wordHalfWindowSize);
		oos.writeInt(prefixLength);
		oos.writeInt(suffixLength);
		oos.writeBoolean(wordOnlyLeftWindow);
		for(FeatureType featureType: FeatureType.values()){
			oos.writeBoolean(featureType.isEnabled);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException{
		tokenizerMethod = (TokenizerMethod)ois.readObject();
		brownMap = (Map<String, String>)ois.readObject();
		wordHalfWindowSize = ois.readInt();
		prefixLength = ois.readInt();
		suffixLength = ois.readInt();
		wordOnlyLeftWindow = ois.readBoolean();
		for(FeatureType featureType: FeatureType.values()){
			featureType.isEnabled = ois.readBoolean();
		}
	}

}
