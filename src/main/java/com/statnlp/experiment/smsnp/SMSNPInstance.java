package com.statnlp.experiment.smsnp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.statnlp.example.base.BaseInstance;
import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;

/**
 * The data structure to represent SMS messages with their annotations as is<br>
 * An instance represents the real-world view of the problem.<br>
 * Compare with {@link SMSNPNetwork}, which is the model view of the problem<br>
 * This is based on StatNLP framework for CRF on acyclic graphs
 * @author Aldrian Obaja <aldrianobaja.m@gmail.com>
 *
 */
public class SMSNPInstance extends BaseInstance<SMSNPInstance, String, List<Span>> {
	
	private static final long serialVersionUID = -5338701879189642344L;
	
	public List<Span> wordSpans;
	public String[] inputTokenized;
	public List<WordLabel> outputTokenized;
	public List<WordLabel> predictionTokenized;
	
	public SMSNPInstance(int instanceId, String input, List<Span> output){
		this(instanceId, 1.0, input, output);
	}
	
	public SMSNPInstance(int instanceId, double weight) {
		this(instanceId, weight, (String)null, (List<Span>)null);
	}
	
	public SMSNPInstance(int instanceId, double weight, String input, List<Span> output){
		super(instanceId, weight);
		this.input = input;
		this.output = output;
	}
	
	public SMSNPInstance(int instanceId, double weight, String[] inputTokenized, List<WordLabel> outputTokenized){
		super(instanceId, weight);
		this.inputTokenized = inputTokenized;
		this.outputTokenized = outputTokenized;
		this.wordSpans = new ArrayList<Span>();
		this.input = SMSNPUtil.tokensToString(inputTokenized, outputTokenized, wordSpans);
		if(outputTokenized == null){
			this.output = null;
		} else {
			this.output = SMSNPUtil.labelsToSpans(outputTokenized, wordSpans, input);
		}
	}
	
	public SMSNPInstance duplicate(){
		SMSNPInstance result = super.duplicate();
		result.wordSpans = this.wordSpans;
		result.inputTokenized = this.inputTokenized == null ? null : Arrays.copyOf(this.inputTokenized, this.inputTokenized.length);
		result.outputTokenized = this.outputTokenized == null ? null : new ArrayList<WordLabel>(this.outputTokenized);
		result.predictionTokenized = this.predictionTokenized == null ? null : new ArrayList<WordLabel>(this.predictionTokenized);
		return result;
	}
	
	public String duplicateInput(){
		return input == null ? null : new String(input);
	}
	
	public List<Span> duplicateOutput(){
		return output == null ? null : new ArrayList<Span>(output);
	}

	public List<Span> duplicatePrediction(){
		return prediction == null ? null : new ArrayList<Span>(prediction);
	}
	
	public String[] getInputTokenized(){
		if(inputTokenized == null){
			throw new RuntimeException("Input not yet tokenized. Please specify TokenizerMethod");
		}
		return inputTokenized;
	}
	
	/**
	 * Return the tokenized input if available, or calculate using the specified parameters
	 * @param tokenizerMethod
	 * @param useGoldTokenization Whether to use gold tokenization (make it compatible with the gold spans)
	 * @param force Whether to force re-calculation using the specified parameters, overriding existing tokenization
	 * @return
	 */
	public String[] getInputTokenized(TokenizerMethod tokenizerMethod, boolean useGoldTokenization, boolean force){
		if(inputTokenized == null || force){
			wordSpans = getWordSpans(tokenizerMethod, useGoldTokenization, force);
			inputTokenized = SMSNPUtil.spansToTokens(input, wordSpans);
		}
		return inputTokenized;
	}
	
	public List<WordLabel> getOutputTokenized(){
		if(outputTokenized == null){
			throw new RuntimeException("Output not yet tokenized. Please specify TokenizerMethod");
		}
		return outputTokenized;
	}
	
	/**
	 * Return the tokenized output if available, or calculate using the specified parameters
	 * @param tokenizerMethod
	 * @param useGoldTokenization
	 * @param force Whether to force re-calculation using the specified parameters, overriding existing tokenization
	 * @return
	 */
	public List<WordLabel> getOutputTokenized(TokenizerMethod tokenizerMethod, boolean useGoldTokenization, boolean force){
		if(outputTokenized == null || force){
			wordSpans = getWordSpans(tokenizerMethod, useGoldTokenization, force);
			outputTokenized = SMSNPUtil.spansToLabels(output, wordSpans);
		}
		return outputTokenized;
	}
	
	private List<Span> getWordSpans(TokenizerMethod tokenizerMethod, boolean useGoldTokenization, boolean force){
		if(wordSpans == null || force){
			if(useGoldTokenization){
				wordSpans = SMSNPUtil.getWordSpans(input, tokenizerMethod, output);	
			} else {
				wordSpans = SMSNPUtil.getWordSpans(input, tokenizerMethod, null);
			}
		}
		return wordSpans;
	}
	
	public void setPredictionTokenized(List<WordLabel> predictionTokenized){
		this.predictionTokenized = predictionTokenized;
		if(predictionTokenized == null){
			this.prediction = null;
		} else {
			this.prediction = SMSNPUtil.labelsToSpans(predictionTokenized, wordSpans, input);
		}
	}

	@Override
	public int size() {
		return getInput().length();
	}
	
	public String toCoNLLString(String[]... additionalFeatures){
		return SMSNPUtil.toCoNLLString(inputTokenized, outputTokenized, predictionTokenized, additionalFeatures);
	}

	public String toString(){
		StringBuilder builder = new StringBuilder();
		builder.append(getInstanceId()+":");
		builder.append(input);
		if(hasOutput()){
			builder.append("\n");
			for(Span span: output){
				builder.append(span+"|");
			}
		}
		return builder.toString();
	}
}
