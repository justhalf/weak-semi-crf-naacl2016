package com.statnlp.example.linear_ie;

import java.util.ArrayList;
import java.util.List;

import com.statnlp.example.base.BaseInstance;
import com.statnlp.example.linear_ie.LinearIEInstance.WordsAndTags;

public class LinearIEInstance extends BaseInstance<LinearIEInstance, WordsAndTags, List<Span>>{
	
	public class WordsAndTags {
		public AttributedWord[] words;
		public String[] posTags;
		public WordsAndTags(WordsAndTags other){
			this.words = other.words;
			this.posTags = other.posTags;
		}
		public WordsAndTags(AttributedWord[] words, String[] posTags){
			this.words = words;
			this.posTags = posTags;
		}
	}
	
	private static final long serialVersionUID = -9133939568122739620L;
	public AttributedWord[] words;
	public String[] posTags;
	
	public LinearIEInstance(int instanceId, double weight){
		super(instanceId, weight);
	}
	
	public WordsAndTags duplicateInput(){
		return input == null ? null : new WordsAndTags(input);
	}
	
	public List<Span> duplicateOutput(){
		return output == null ? null : new ArrayList<Span>(output);
	}

	public List<Span> duplicatePrediction(){
		return prediction == null ? null : new ArrayList<Span>(prediction);
	}

	@Override
	public int size() {
		return words.length;
	}

	public String toString(){
		StringBuilder builder = new StringBuilder();
		builder.append(getInstanceId()+":");
		for(int i=0; i<words.length; i++){
			if(i > 0) builder.append(" ");
			builder.append(words[i]+"/"+posTags[i]);
		}
		if(hasOutput()){
			builder.append("\n");
			for(Span span: output){
				builder.append(span+"|");
			}
		}
		return builder.toString();
	}

	public LinearIEInstance duplicate(){
		LinearIEInstance result = super.duplicate();
		result.words = result.input.words;
		result.posTags = result.input.posTags;
		return result;
	}
}
