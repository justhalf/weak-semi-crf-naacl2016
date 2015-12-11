package com.statnlp.experiment.smsnp;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;

public class SMSNPUtil {
	
	public static boolean COMBINE_OUTSIDE_CHARS = true;
	public static boolean USE_SINGLE_OUTSIDE_TAG = true;
	
	public static void print(String message, PrintStream... outstream){
		if(outstream.length == 0){
			outstream = new PrintStream[]{System.out};
		}
		for(PrintStream stream: outstream){
			if(stream != null){
				stream.println(message);
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
					String labelForm = tokens[1];
					if(USE_SINGLE_OUTSIDE_TAG){
						if(labelForm.startsWith("O")){
							labelForm = "O";
						}
					}
					outputTokens.add(WordLabel.get(labelForm));
				}
				if(withPrediction){
					String labelForm = null;
					if(isLabeled){
						labelForm = tokens[2];
					} else {
						labelForm = tokens[1];
					}
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
	public static String toCoNLLString(String[] inputTokenized, List<WordLabel> outputTokenized, List<WordLabel> predictionTokenized){
		StringBuilder builder = new StringBuilder();
		for(int i=0; i<inputTokenized.length; i++){
			if(predictionTokenized != null){
				builder.append(String.format("%s %s %s\n", inputTokenized[i], outputTokenized.get(i).form, predictionTokenized.get(i).form));
			} else {
				builder.append(String.format("%s %s\n", inputTokenized[i], outputTokenized.get(i).form));
			}
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

}
