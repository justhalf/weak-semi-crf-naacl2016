package com.statnlp.experiment.smsnp;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;

import edu.stanford.nlp.util.StringUtils;

public class SMSNPUtil {
	
	public static boolean COMBINE_OUTSIDE_CHARS = true;
	public static boolean USE_SINGLE_OUTSIDE_TAG = true;
	
	public static void print(String message, boolean printEndline, PrintStream... outstream){
		if(outstream.length == 0){
			outstream = new PrintStream[]{System.out};
		}
		for(PrintStream stream: outstream){
			if(stream != null){
				if(printEndline){
					stream.println(message);
				} else {
					stream.print(message);
				}
			}
		}
	}
	
	/**
	 * Read data from a file with three-line format:<br>
	 * - First line the input string<br>
	 * - Second line the list of spans in the format "start,end Label" separated by pipe "|"<br>
	 * - Third line an empty line
	 * @param fileName
	 * @param setLabeled Whether to set the instances read as labeled
	 * @return
	 * @throws IOException
	 */
	public static SMSNPInstance[] readData(String fileName, boolean setLabeled, boolean withPrediction) throws IOException{
		InputStreamReader isr = new InputStreamReader(new FileInputStream(fileName), "UTF-8");
		BufferedReader br = new BufferedReader(isr);
		ArrayList<SMSNPInstance> result = new ArrayList<SMSNPInstance>();
		String input = null;
		List<Span> output = null;
		int instanceId = 1;
		while(br.ready()){
			input = br.readLine();
			output = createSpans(br.readLine(), input);
			SMSNPInstance instance = new SMSNPInstance(instanceId, 1.0, input, output);
			if(setLabeled){
				instance.setLabeled();
			} else {
				instance.setUnlabeled();
			}

			if(withPrediction){
				List<Span> predictions = createSpans(br.readLine(), input);
				instance.setPrediction(predictions);
			}
			
			result.add(instance);
			instanceId += 1;
			br.readLine();
		}
		br.close();
		return result.toArray(new SMSNPInstance[result.size()]);
	}

	private static List<Span> createSpans(String line, String input) throws IOException {
		List<Span> result = new ArrayList<Span>();
		String[] spansStr = line.split("\\|");
		List<Span> spans = new ArrayList<Span>();
		for(String span: spansStr){
			if(span.length() == 0){
				continue;
			}
			String[] startend_label = span.split(" ");
			SpanLabel label = SpanLabel.get(startend_label[1]);
			String[] start_end = startend_label[0].split(",");
			int start = Integer.parseInt(start_end[0]);
			int end = Integer.parseInt(start_end[1]);
			spans.add(new Span(start, end, label));
		}
		Collections.sort(spans); // Ensure it is sorted
		
		int prevEnd = 0;
		for(Span span: spans){
			int start = span.start;
			int end = span.end;
			SpanLabel label = span.label;
			if(prevEnd < start){
				createOutsideSpans(input, result, prevEnd, start);
			}
			prevEnd = end;
			result.add(new Span(start, end, label));
		}
		createOutsideSpans(input, result, prevEnd, input.length());
		return result;
	}
	
	/**
	 * Create the outside spans in the specified substring
	 * @param input
	 * @param output
	 * @param start
	 * @param end
	 */
	private static void createOutsideSpans(String input, List<Span> output, int start, int end){
		int length = input.length();
		int curStart = start;
		while(curStart < end){
			int curEnd = input.indexOf(' ', curStart);
			SpanLabel outsideLabel = null;
			if(USE_SINGLE_OUTSIDE_TAG){
				outsideLabel = SpanLabel.get("O");
				if(curEnd == -1 || curEnd > end){
					curEnd = end;
				} else if(curStart == curEnd){
					curEnd += 1;
				}
			} else {
				if(curEnd == -1 || curEnd > end){ // No space
					curEnd = end;
					if(curStart == start){ // Start directly after previous tag: this is between tags
						if(curStart == 0){ // Unless this is the start of the string
							if(curEnd == length){
								outsideLabel = SpanLabel.get("O"); // Case |<cur>|
							} else {
								outsideLabel = SpanLabel.get("O-B"); // Case |<cur>###
							}
						} else {
							if(curEnd == length){
								outsideLabel = SpanLabel.get("O-A"); // Case ###<cur>|
							} else {
								outsideLabel = SpanLabel.get("O-I"); // Case ###<cur>###
							}
						}
					} else { // Start not immediately: this is before tags (found space before)
						if(curEnd == length){
							outsideLabel = SpanLabel.get("O"); // Case ### <cur>|
						} else {
							outsideLabel = SpanLabel.get("O-B"); // Case ### <cur>###
						}
					}
				} else if(curStart == curEnd){ // It is immediately a space
					curEnd += 1;
					outsideLabel = SpanLabel.get("O"); // Tag space as a single outside token
				} else if(curStart < curEnd){ // Found a non-immediate space
					if(curStart == start){ // Start immediately after previous tag: this is after tag
						if(curStart == 0){
							outsideLabel = SpanLabel.get("O"); // Case |<cur> ###
						} else {
							outsideLabel = SpanLabel.get("O-A"); // Case ###<cur> ###
						}
					} else { // Start not immediately: this is a separate outside token
						outsideLabel = SpanLabel.get("O"); // Case ### <cur> ###
					}
				}
			}
			output.add(new Span(curStart, curEnd, outsideLabel));
			curStart = curEnd;
		}
	}
	
	/**
	 * Read data from file in a CoNLL format 
	 * @param fileName
	 * @param isLabeled
	 * @return
	 * @throws IOException
	 */
	public static SMSNPInstance[] readCoNLLData(String fileName, boolean isLabeled, boolean withPrediction) throws IOException{
		InputStreamReader isr = new InputStreamReader(new FileInputStream(fileName), "UTF-8");
		BufferedReader br = new BufferedReader(isr);
		ArrayList<SMSNPInstance> result = readCoNLLData(br, isLabeled, withPrediction);
		br.close();
		return result.toArray(new SMSNPInstance[result.size()]);
	}

	/**
	 * Read data from file in a CoNLL format
	 * @param reader
	 * @param isLabeled
	 * @return
	 * @throws IOException
	 */
	public static ArrayList<SMSNPInstance> readCoNLLData(BufferedReader reader, boolean isLabeled, boolean withPrediction) throws IOException {
		ArrayList<SMSNPInstance> result = new ArrayList<SMSNPInstance>();
		List<String> inputTokens = null;
		List<WordLabel> outputTokens = null;
		List<WordLabel> predTokens = null;
		int instanceId = 1;
		while(reader.ready()){
			if(inputTokens == null){
				inputTokens = new ArrayList<String>();
				if(isLabeled){
					outputTokens = new ArrayList<WordLabel>();
				}
				if(withPrediction){
					predTokens = new ArrayList<WordLabel>();
				}
			}
			String line = reader.readLine();
			if(line == null){
				break;
			}
			line = line.trim();
			if(line.length() == 0){
				SMSNPInstance instance = new SMSNPInstance(instanceId, 1, inputTokens.toArray(new String[inputTokens.size()]), outputTokens);
				if(withPrediction){
					instance.setPredictionTokenized(predTokens);
				}
				if(isLabeled){
					instance.setLabeled();
				} else {
					instance.setUnlabeled();
				}
				instanceId++;
				result.add(instance);
				inputTokens = null;
			} else {
				String[] tokens = line.split("[ \t]");
				inputTokens.add(tokens[0]);
				if(isLabeled){
					String labelForm = null;
					if(withPrediction){
						labelForm = tokens[tokens.length-2];	
					} else {
						labelForm = tokens[tokens.length-1];
					}
					if(USE_SINGLE_OUTSIDE_TAG){
						if(labelForm.startsWith("O")){
							labelForm = "O";
						}
					}
					outputTokens.add(WordLabel.get(labelForm));
				}
				if(withPrediction){
					String labelForm = tokens[tokens.length-1];
					if(USE_SINGLE_OUTSIDE_TAG){
						if(labelForm.startsWith("O")){
							labelForm = "O";
						}
					}
					predTokens.add(WordLabel.get(labelForm));
				}
			}
		}
		return result;
	}
	
	/**
	 * Return the CoNLL format of the specified input tokens, output tokens, and optional prediction tokens.
	 * @param inputTokenized
	 * @param outputTokenized
	 * @param predictionTokenized
	 * @return
	 */
	public static String toCoNLLString(String[] inputTokenized, List<WordLabel> outputTokenized, List<WordLabel> predictionTokenized, String[]... additionalFeatures){
		StringBuilder builder = new StringBuilder();
		for(int i=0; i<inputTokenized.length; i++){
			builder.append(inputTokenized[i]);
			for(int additionalFeatureIndex=0; additionalFeatureIndex<additionalFeatures.length; additionalFeatureIndex++){
				builder.append(" "+additionalFeatures[additionalFeatureIndex][i]);
			}
			builder.append(" "+outputTokenized.get(i).form);
			if(predictionTokenized != null){
				builder.append(" "+predictionTokenized.get(i).form);
			}
			builder.append("\n");
		}
		return builder.toString();
	}

	/**
	 * Read data from file in a CoNLL format
	 * @param reader
	 * @param isLabeled
	 * @return
	 * @throws IOException
	 */
	public static ArrayList<SMSNPInstance> tokensToSpans(BufferedReader reader, boolean isLabeled, boolean withPrediction) throws IOException {
		ArrayList<SMSNPInstance> result = new ArrayList<SMSNPInstance>();
		String input = null;
		List<Span> output = null;
		int instanceId = 1;
		int start = -1;
		int end = 0;
		SpanLabel prevLabel = null;
		List<Span> wordSpans = new ArrayList<Span>();
		List<WordLabel> predLabels = new ArrayList<WordLabel>();
		while(reader.ready()){
			if(input == null){
				input = "";
				output = new ArrayList<Span>();
				wordSpans.clear();
				if(withPrediction){
					predLabels.clear();
				}
				start = -1;
				end = 0;
				prevLabel = null;
			}
			String line = reader.readLine();
			if(line == null){
				break;
			}
			line = line.trim();
			if(line.length() == 0){
				input = input.trim();
				end = input.length();
				if(start != -1){
					createSpan(output, start, end, prevLabel, input);
				}
				SMSNPInstance instance = new SMSNPInstance(instanceId, 1);
				instance.input = input;
				instance.output = output;
				instance.prediction = labelsToSpans(predLabels, wordSpans, input);
				if(isLabeled){
					instance.setLabeled();
				} else {
					instance.setUnlabeled();
				}
				instanceId++;
				result.add(instance);
				input = null;
			} else {
				String[] tokens = line.split("[ \t]");
				String word = tokens[0];
				String form = tokens[1];
				if(USE_SINGLE_OUTSIDE_TAG){
					if(form.startsWith("O")){
						form = "O";
					}
				}
				if(withPrediction){
					predLabels.add(WordLabel.get(form));
				}
				SpanLabel label = null;
				end = input.length();
				if(form.startsWith("B")){
					if(start != -1){
						createSpan(output, start, end, prevLabel, input);
					}
					if(prevLabel != null && !prevLabel.form.matches("O-[BI]")){
						// Assumption: consecutive non-outside tags are separated by a space
						input += " ";
						createSpan(output, end, end+1, SpanLabel.get("O"), input);
						end += 1;
					}
					start = end;
					input += word;
					createSpan(wordSpans, end, end+word.length());
					label = SpanLabel.get(form.substring(form.indexOf("-")+1));
				} else if(form.startsWith("I")){
					input += " "+word;
					createSpan(wordSpans, end+1, end+1+word.length());
					label = SpanLabel.get(form.substring(form.indexOf("-")+1));
				} else if(form.startsWith("O")){
					if(start != -1){
						createSpan(output, start, end, prevLabel, input);
					}
					if(prevLabel != null && form.matches("O(-B)?")){
						input += " ";
						createSpan(output, end, end+1, SpanLabel.get("O"), input);
						end += 1;
					}
					start = end;
					input += word;
					createSpan(wordSpans, end, end+word.length());
					if(USE_SINGLE_OUTSIDE_TAG){
						label = SpanLabel.get("O");
					} else {
						label = SpanLabel.get(form);
					}
				}
				prevLabel = label;
			}
		}
		return result;
	}
	
	private static void createSpan(List<Span> output, int start, int end){
		createSpan(output, start, end, null, null);
	}
	
	private static void createSpan(List<Span> output, int start, int end, SpanLabel label, String input){
		if(label != null && label.form.startsWith("O")){
			if(COMBINE_OUTSIDE_CHARS){
				if(output.size() > 0){
					Span prevOutput = output.get(output.size()-1);
					String curString = input.substring(start, end);
					String prevString = input.substring(prevOutput.start, prevOutput.end);
					if(!curString.equals(" ") && prevOutput.label.form.equals(label.form) && !prevString.equals(" ")){
						prevOutput.end = end;
						return;
					}
				}
				output.add(new Span(start, end, label));
			} else {
				for(int i=start; i<end; i++){
					output.add(new Span(i, i+1, label));
				}
			}
		} else {
			output.add(new Span(start, end, label));
		}
	}
	
	public static List<Span> labelsToSpans(List<WordLabel> labels, List<Span> wordSpans, String input){
		List<Span> result = new ArrayList<Span>();
		int startIdx = 0;
		int endIdx = 0;
		int wordStartIdx = -1;
		for(int i=0; i<labels.size(); i++){
			SpanLabel label = null;
			String form = labels.get(i).form;
			Span curSpan = wordSpans.get(i);
			if(form.startsWith("B")){
				startIdx = curSpan.start;
				endIdx = curSpan.end;
				wordStartIdx = i;
				label = SpanLabel.get(form.substring(form.indexOf("-")+1));
			} else if (form.startsWith("I")){
				endIdx = curSpan.end;
				label = SpanLabel.get(form.substring(form.indexOf("-")+1));
			} else if (form.startsWith("O")){
				startIdx = curSpan.start;
				endIdx = curSpan.end;
				wordStartIdx = i;
				if(USE_SINGLE_OUTSIDE_TAG && form.indexOf("-") >= 0){
					label = SpanLabel.get(form.substring(0, form.indexOf("-")));
				} else {
					label = SpanLabel.get(form);
				}
			}
			if(i+1 >= labels.size() || !labels.get(i+1).form.startsWith("I")){
				if(wordStartIdx > 0){
					int prevEnd = wordSpans.get(wordStartIdx-1).end;
					if(prevEnd < startIdx){
						result.add(new Span(prevEnd, startIdx, SpanLabel.get("O")));
					}
				}
				createSpan(result, startIdx, endIdx, label, input);
			}
		}
		return result;
	}

	public static List<Span> getWordSpans(String input, TokenizerMethod tokenizerMethod, List<Span> output) {
		List<Span> wordSpans = new ArrayList<Span>();
		String[] words = SMSNPTokenizer.tokenize(input, tokenizerMethod);
		int prevEnd = 0;
		for(int i=0; i<words.length; i++){
			String curWord = words[i];
			// Using char-based matching, still not 100% correct
//			int wordCharIdx = 0;
//			int start = prevEnd;
//			while(input.charAt(start) == ' ') start++;
//			int end = start;
//			while(wordCharIdx < curWord.length()){
//				int lastGoodEnd = end;
//				while(input.charAt(end) == ' ') end++;
//				int skippedCount = 0;
//				if(input.charAt(end) != curWord.charAt(wordCharIdx)){
//					while(input.charAt(end) != curWord.charAt(wordCharIdx)){
//						end += 1;
//						if(input.charAt(end) != ' '){
//							skippedCount += 1;
//						}
//						if(skippedCount >= 3){
//							System.err.println(String.format("Character '%c' in \"%s\" does not align with input \"%s\"", curWord.charAt(wordCharIdx), curWord, input.substring(lastGoodEnd)));
//							end = lastGoodEnd-1;
//							break;
//						}
//					}
//				}
//				wordCharIdx += 1;
//				end += 1;
//			}
			// Using word-based matching, cannot handle extra character inserted by tokenizer
			int start = input.indexOf(curWord, prevEnd);
			if(start == -1){
				System.err.println("Cannot find \""+curWord+"\" in \""+input+"\"");
			}
			int end = start + curWord.length();
			wordSpans.add(new Span(start, end, null));
			prevEnd = end;
		}
		if(output != null){
			int wordSpanIdx = 0;
			int outputIdx = 0;
			Span wordSpan = null;
			if(wordSpans.size() > 0){
				wordSpan = wordSpans.get(0);
			}
			Span outputSpan = null;
			while(outputIdx < output.size()){
				outputSpan = output.get(outputIdx);
				if(input.substring(outputSpan.start, outputSpan.end).equals(" ")){
					outputIdx += 1;
					continue;
				}
				while(wordSpan.end <= outputSpan.start){
					wordSpanIdx += 1;
					if(wordSpanIdx >= wordSpans.size()){
						throw new IllegalArgumentException("Output span: "+outputSpan+" lies beyond the input "+Arrays.asList(words));
					}
					wordSpan = wordSpans.get(wordSpanIdx);
				}
				if(outputSpan.start > wordSpan.start){
					wordSpans.remove(wordSpanIdx);
					Span back = new Span(outputSpan.start, wordSpan.end, null);
					Span front = new Span(wordSpan.start, outputSpan.start, null);
					wordSpans.add(wordSpanIdx, back);
					wordSpans.add(wordSpanIdx, front);
					wordSpanIdx += 1;
					wordSpan = back;
				} else if (outputSpan.start < wordSpan.start){
					System.err.println("Output span: "+outputSpan+" starts outside word. It will be clipped");
					outputSpan.start = wordSpan.start;
				}
				while(wordSpan.end < outputSpan.end){
					wordSpanIdx += 1;
					if(wordSpanIdx >= wordSpans.size()){
						System.err.println("Output span: "+outputSpan+" extends beyond the input "+Arrays.asList(words)+". It will be clipped");
						outputSpan.end = wordSpan.end;
						wordSpanIdx -= 1;
					}
					wordSpan = wordSpans.get(wordSpanIdx);
				}
				if(outputSpan.end < wordSpan.end){
					wordSpans.remove(wordSpanIdx);
					Span back = new Span(outputSpan.end, wordSpan.end, null);
					Span front = new Span(wordSpan.start, outputSpan.end, null);
					wordSpans.add(wordSpanIdx, back);
					wordSpans.add(wordSpanIdx, front);
					wordSpanIdx += 1;
					wordSpan = back;
				} else if (outputSpan.end < wordSpan.start){
					System.err.println("Output span: "+outputSpan+" ends outside word. It will be clipped");
					outputSpan.end = wordSpans.get(wordSpanIdx-1).end;
				}
				outputIdx += 1;
			}
		}
		return wordSpans;
	}

	/**
	 * Convert tokenized input and output into single input string, while also
	 * producing list of word spans into <code>wordSpans</code> argument
	 * @param inputTokenized The tokenized input
	 * @param outputTokenized The output labels matching each input token
	 * @param wordSpans The non-null list object to which the word spans will be appended
	 * @return
	 */
	public static String tokensToString(String[] inputTokenized, List<WordLabel> outputTokenized, List<Span> wordSpans) {
		String input = "";
		wordSpans.clear();
		String prevForm = null;
		for(int i=0; i<inputTokenized.length; i++){
			String word = inputTokenized[i];
			String form = outputTokenized.get(i).form;
			if(form.startsWith("B")){
				if(prevForm != null && !prevForm.matches("O-[BI]")){
					// Assumption: consecutive non-outside tags are separated by a space
					input += " ";
				}
				int len = input.length();
				input += word;
				createSpan(wordSpans, len, len+word.length());
			} else if(form.startsWith("I")){
				int len = input.length();
				input += " "+word;
				createSpan(wordSpans, len+1, len+1+word.length());
			} else if(form.startsWith("O")){
				if(prevForm != null && form.matches("O(-B)?")){
					input += " ";
				}
				int len = input.length();
				input += word;
				createSpan(wordSpans, len, len+word.length());
			}
			prevForm = form;
		}
		input = input.trim();
		return input;
	}

	/**
	 * Converts word spans and input into tokenized input.
	 * @param input
	 * @param wordSpans
	 * @return
	 */
	public static String[] spansToTokens(String input, List<Span> wordSpans) {
		List<String> result = new ArrayList<String>();
		for(Span wordSpan: wordSpans){
			result.add(input.substring(wordSpan.start, wordSpan.end));
		}
		return result.toArray(new String[result.size()]);
	}
	
	private static Span nextMainSpan(Iterator<Span> outputIter){
		Span outputSpan = null;
		while(outputIter.hasNext()){
			outputSpan = outputIter.next();
			if(outputSpan.label.form.startsWith("O")){
				outputSpan = null;
				continue;
			} else {
				break;
			}
		}
		return outputSpan;
	}

	/**
	 * Convert output (or predicted) spans into list of labels based on the words in wordSpans.<br>
	 * If the word spans are not compatible with the output spans, the output spans will be extended to 
	 * the overlapping word spans, prioritizing begin tags (B-) over inside tags (I-)<br>
	 * For example, if we have:<br>
	 * <ul>
	 * <li>Word spans: [0,4], [5,11], [12,15], [16,20]</li>
	 * <li>Output spans: [0,9,NP], [9,15,NP]</li>
	 * </ul>
	 * The result will be:<br>
	 * <ul>
	 * <li>word1 B-NP</li>
	 * <li>word2 B-NP</li>
	 * <li>word3 I-NP</li>
	 * <li>word4 O</li>
	 * </ul>
	 * @param output
	 * @param wordSpans
	 * @return
	 */
	public static List<WordLabel> spansToLabels(List<Span> output, List<Span> wordSpans) {
		List<WordLabel> result = new ArrayList<WordLabel>();
		Collections.sort(output);
		Collections.sort(wordSpans);
		Iterator<Span> outputIter = output.iterator();
		Span outputSpan = nextMainSpan(outputIter);
		int lastEnd = -1;
		for(Span wordSpan: wordSpans){
			if(outputSpan != null){
				if(wordSpan.start < outputSpan.start){
					if(wordSpan.end <= outputSpan.start){
						if(USE_SINGLE_OUTSIDE_TAG){
							result.add(WordLabel.get("O"));
						} else {
							if(wordSpan.start == lastEnd && wordSpan.end == outputSpan.start){
								result.add(WordLabel.get("O-I"));
							} else if (wordSpan.start == lastEnd){
								result.add(WordLabel.get("O-A"));
							} else if (wordSpan.end == outputSpan.start){
								result.add(WordLabel.get("O-B"));
							} else {
								result.add(WordLabel.get("O"));
							}
						}
					} else {
						// Output span starts at the middle of this word span
						// Label this whole word as the output
						result.add(WordLabel.get("B-"+outputSpan.label.form));
//						System.out.println("START"+outputSpan);
					}
				} else if(wordSpan.start == outputSpan.start){
					result.add(WordLabel.get("B-"+outputSpan.label.form));
				} else {
					result.add(WordLabel.get("I-"+outputSpan.label.form));
				}
				if(wordSpan.end >= outputSpan.end){
					if(wordSpan.end > outputSpan.end){
						// Output span ends at the middle of this word span
//						System.out.println("END"+outputSpan);
					}
					lastEnd = outputSpan.end;
					outputSpan = nextMainSpan(outputIter);
				}
			} else {
				if(USE_SINGLE_OUTSIDE_TAG){
					result.add(WordLabel.get("O"));
				} else {
					if(wordSpan.start == lastEnd){
						result.add(WordLabel.get("O-A"));
					} else {
						result.add(WordLabel.get("O"));
					}
				}
			}
		}
		return result;
	}
	
	public static void setupFeatures(Class<? extends IFeatureType> featureTypeClass, String[] features){
		try {
			Method valueOf = featureTypeClass.getMethod("valueOf", String.class);
			IFeatureType[] featureTypes = (IFeatureType[])featureTypeClass.getMethod("values").invoke(null);
			if(features != null && features.length > 0){
				for(IFeatureType feature: featureTypes){
					feature.disable();
				}
				for(String feature: features){
					((IFeatureType)valueOf.invoke(null, feature.toUpperCase())).enable();
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static int[] listToArray(List<Integer> list){
		int[] result = new int[list.size()];
		for(int i=0; i<list.size(); i++){
			result[i] = list.get(i);
		}
		return result;
	}

	private static enum Argument{
		GET_STATS(0,
				"Print statistics on the input dataset specified by -input",
				"getStats"),
		INCLUDE_PERCENTILE(1,
				"The list of percentiles to be included in the statistics",
				"includePercentile",
				"<comma-separated values>"),
		LABELS(1,
				"The list of labels to be included in calculation",
				"labels",
				"<comma-separated labels>"),
		INPUT(1,
				"Input file",
				"input",
				"inputFile"),
		IN_CONLL(0,
				"Whether the input file is in CoNLL format",
				"inCoNLL"),
		TOKENIZER(1,
				"The tokenizer to be used to tokenize. Default to regex",
				"tokenizer",
				"[regex|whitespace]"),
		INPUT_HAS_PREDICTION(0,
				"Whether the input file contains the prediction",
				"inputHasPrediction"),
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
	
	public static void main(String[] args) throws Exception{
		boolean getStats = false;
		String inputPath = null;
		boolean inCoNLL = false;
		boolean hasPrediction = false;
		TokenizerMethod tokenizerMethod = TokenizerMethod.REGEX;
		double[] includePercentiles = new double[0];
		Set<String> labels = null;
		
		int argIndex = 0;
		while(argIndex < args.length){
			String arg = args[argIndex];
			if(arg.length() > 0 && arg.charAt(0) == '-'){
				Argument argument = Argument.argWithName(args[argIndex].substring(1));
				switch(argument){
				case GET_STATS:
					getStats = true;
					break;
				case INPUT:
					inputPath = args[argIndex+1];
					break;
				case IN_CONLL:
					inCoNLL = true;
					break;
				case LABELS:
					labels = new HashSet<String>(Arrays.asList(args[argIndex+1].split(",")));
					break;
				case INCLUDE_PERCENTILE:
					String[] tokens = args[argIndex+1].split(",");
					includePercentiles = new double[tokens.length];
					for(int i=0; i<tokens.length; i++){
						includePercentiles[i] = Double.parseDouble(tokens[i]);
					}
					break;
				case INPUT_HAS_PREDICTION:
					hasPrediction = true;
				case TOKENIZER:
					tokenizerMethod = TokenizerMethod.valueOf(args[argIndex+1].toUpperCase());
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
		if(!getStats){
			Argument.printHelp();
			System.exit(0);
		}
		if(inputPath == null){
			System.err.println("-getStats is specified but no -input file specified");
			System.exit(0);
		}
		SMSNPInstance[] instances = null;
		if(inCoNLL){
			instances = readCoNLLData(inputPath, true, hasPrediction);
		} else {
			instances = readData(inputPath, true, hasPrediction);
		}
		List<Integer> numChars = new ArrayList<Integer>();
		List<Integer> numTokens = new ArrayList<Integer>();
		List<Integer> spanCharLengths = new ArrayList<Integer>();
		List<Integer> spanTokenLengths = new ArrayList<Integer>();
		Set<String> tokenLabels = new HashSet<String>();
		Set<String> spanLabels = new HashSet<String>();
		for(SMSNPInstance instance: instances){
			numChars.add(instance.size());
			instance.getInputTokenized(tokenizerMethod, false, false);
			instance.getOutputTokenized(tokenizerMethod, false, false);
			numTokens.add(instance.getInputTokenized().length);
			for(Span span: instance.output){
				if(labels == null || labels.contains(span.label.form)){
					spanCharLengths.add(span.end-span.start);
					spanLabels.add(span.label.form);
				}
			}
			List<WordLabel> outputTokenized = instance.getOutputTokenized();
			int start = 0;
			for(int pos=0; pos<outputTokenized.size(); pos++){
				WordLabel label = outputTokenized.get(pos);
				if(pos == outputTokenized.size()-1 || label.form.startsWith("O") || outputTokenized.get(pos+1).id != label.id){
					if(labels == null || labels.contains(label.form)){
						spanTokenLengths.add(pos-start+1);
						tokenLabels.add(label.form);
					}
					start = pos+1;
				}
			}
		}
		Collections.sort(numChars);
		Collections.sort(numTokens);
		Collections.sort(spanCharLengths);
		Collections.sort(spanTokenLengths);
		printStatistics(numChars, includePercentiles, "instance length (in char)");
		printStatistics(numTokens, includePercentiles, "instance length (in tokens)");
		printStatistics(spanCharLengths, includePercentiles, "span length (in char) "+spanLabels);
		printStatistics(spanTokenLengths, includePercentiles, "span length (in tokens) "+tokenLabels);
	}
	
	private static void printStatistics(List<Integer> nums, double[] includePercentiles, String name){
		System.out.println("Statistics for "+name);
		System.out.println(String.format("Total: %d", nums.size()));
		Statistics stat = new Statistics(nums);
		System.out.println(String.format("Max: %d", stat.max));
		System.out.println(String.format("Min: %d", stat.min));
		System.out.println(String.format("Mode: %d (%d)", stat.mode, stat.modeCount));
		System.out.println(String.format("50%% percentile: %d", getPercentileAt(nums, 0.50)));
		System.out.println(String.format("75%% percentile: %d", getPercentileAt(nums, 0.75)));
		System.out.println(String.format("80%% percentile: %d", getPercentileAt(nums, 0.80)));
		System.out.println(String.format("90%% percentile: %d", getPercentileAt(nums, 0.90)));
		System.out.println(String.format("95%% percentile: %d", getPercentileAt(nums, 0.95)));
		System.out.println(String.format("99%% percentile: %d", getPercentileAt(nums, 0.99)));
		for(double percentile: includePercentiles){
			System.out.println(String.format("%s%% percentile: %d", 100*percentile, getPercentileAt(nums, percentile)));
		}
	}
	
	private static int getPercentileAt(List<Integer> num, double percentile){
		return num.get((int)Math.round(percentile*num.size()));
	}
	
	private static class Statistics{
		List<Integer> nums;
		int max;
		int min;
		int mode;
		int modeCount;
		public Statistics(List<Integer> nums){
			this.nums = nums;
			getMode();
			getMinMax();
		}
		
		private void getMinMax(){
			min = Integer.MAX_VALUE;
			max = Integer.MIN_VALUE;
			for(int num: nums){
				min = Math.min(num, min);
				max = Math.max(num, max);
			}
		}
		
		private void getMode(){
			HashMap<Integer, Integer> counts = new HashMap<Integer, Integer>();
			modeCount = 0;
			mode = -1;
			for(int num: nums){
				int count = counts.getOrDefault(num, 0);
				counts.put(num, count+1);
				if(count+1 > modeCount){
					modeCount = count+1;
					mode = num;
				}
			}
			
		}
	}

}
