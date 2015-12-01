package com.statnlp.experiment.smsnp;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SMSNPIOUtil {
	
	public static boolean COMBINE_OUTSIDE_CHARS = true;
	public static boolean USE_SINGLE_OUTSIDE_TAG = false;
	
	public static void print(String message, PrintStream... outstream){
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
	 * @param isLabeled
	 * @param wordBoundariesOnly Whether to learn the word boundaries only
	 * @return
	 * @throws IOException
	 */
	public static SMSNPInstance[] readData(String fileName, boolean isLabeled, boolean withPrediction) throws IOException{
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
			if(isLabeled){
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
			Label label = Label.get(startend_label[1]);
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
			Label label = span.label;
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
			Label outsideLabel = null;
			if(USE_SINGLE_OUTSIDE_TAG){
				outsideLabel = Label.get("O");
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
								outsideLabel = Label.get("O"); // Case |<cur>|
							} else {
								outsideLabel = Label.get("O-B"); // Case |<cur>###
							}
						} else {
							if(curEnd == length){
								outsideLabel = Label.get("O-A"); // Case ###<cur>|
							} else {
								outsideLabel = Label.get("O-I"); // Case ###<cur>###
							}
						}
					} else { // Start not immediately: this is before tags (found space before)
						if(curEnd == length){
							outsideLabel = Label.get("O"); // Case ### <cur>|
						} else {
							outsideLabel = Label.get("O-B"); // Case ### <cur>###
						}
					}
				} else if(curStart == curEnd){ // It is immediately a space
					curEnd += 1;
					outsideLabel = Label.get("O"); // Tag space as a single outside token
				} else if(curStart < curEnd){ // Found a non-immediate space
					if(curStart == start){ // Start immediately after previous tag: this is after tag
						if(curStart == 0){
							outsideLabel = Label.get("O"); // Case |<cur> ###
						} else {
							outsideLabel = Label.get("O-A"); // Case ###<cur> ###
						}
					} else { // Start not immediately: this is a separate outside token
						outsideLabel = Label.get("O"); // Case ### <cur> ###
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
		String input = null;
		List<Span> output = null;
		int instanceId = 1;
		int start = -1;
		int end = 0;
		Label prevLabel = null;
		List<Span> wordSpans = new ArrayList<Span>();
		List<Label> predLabels = new ArrayList<Label>();
		while(reader.ready()){
			if(input == null){
				input = "";
				output = new ArrayList<Span>();
				if(withPrediction){
					predLabels.clear();
				}
				start = -1;
				end = 0;
				prevLabel = null;
			}
			String line = reader.readLine().trim();
			if(line.length() == 0){
				input = input.trim();
				end = input.length();
				if(start != -1){
					createSpan(output, start, end, prevLabel);
				}
				SMSNPInstance instance = new SMSNPInstance(instanceId, 1);
				instance.input = input;
				instance.output = output;
				instance.prediction = labelsToSpans(predLabels, wordSpans);
				if(isLabeled){
					instance.setLabeled();
				} else {
					instance.setUnlabeled();
				}
				instanceId++;
				result.add(instance);
				input = null;
			} else {
				String[] tokens = line.split(" ");
				String word = tokens[0];
				String form = tokens[1];
				if(withPrediction){
					predLabels.add(Label.get(tokens[2]));
				}
				Label label = null;
				end = input.length();
				if(form.startsWith("B")){
					if(start != -1){
						createSpan(output, start, end, prevLabel);
					}
					if(prevLabel != null && !prevLabel.form.matches("O-[BI]")){
						// Assumption: consecutive non-outside tags are separated by a space
						input += " ";
						createSpan(output, end, end+1, Label.get("O"));
						end += 1;
					}
					start = end;
					input += word;
					createSpan(wordSpans, end, end+word.length(), null);
					label = Label.get(form.substring(form.indexOf("-")+1));
				} else if(form.startsWith("I")){
					input += " "+word;
					createSpan(wordSpans, end+1, end+1+word.length(), null);
					label = Label.get(form.substring(form.indexOf("-")+1));
				} else if(form.startsWith("O")){
					if(start != -1){
						createSpan(output, start, end, prevLabel);
					}
					if(prevLabel != null && form.matches("O(-B)?")){
						input += " ";
						createSpan(output, end, end+1, Label.get("O"));
						end += 1;
					}
					start = end;
					input += word;
					createSpan(wordSpans, end, end+word.length(), null);
					if(USE_SINGLE_OUTSIDE_TAG){
						label = Label.get("O");
					} else {
						label = Label.get(form);
					}
				}
				prevLabel = label;
			}
		}
		return result;
	}
	
	private static void createSpan(List<Span> output, int start, int end, Label label){
		if(label != null && label.form.startsWith("O")){
			if(COMBINE_OUTSIDE_CHARS){
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
	
	private static List<Span> labelsToSpans(List<Label> labels, List<Span> wordSpans){
		List<Span> result = new ArrayList<Span>();
		for(int i=0; i<labels.size(); i++){
			// TODO
		}
		return result;
	}

}
