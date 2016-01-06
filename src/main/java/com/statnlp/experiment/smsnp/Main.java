package com.statnlp.experiment.smsnp;

import static com.statnlp.experiment.smsnp.SMSNPUtil.print;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.statnlp.commons.types.Instance;
import com.statnlp.experiment.smsnp.SMSNPTokenizer.TokenizerMethod;
import com.statnlp.experiment.smsnp.linear_crf.LinearCRFFeatureManager;
import com.statnlp.experiment.smsnp.linear_crf.LinearCRFNetworkCompiler;
import com.statnlp.experiment.smsnp.semi_crf.CharSemiCRFFeatureManager;
import com.statnlp.experiment.smsnp.semi_crf.CharSemiCRFNetworkCompiler;
import com.statnlp.experiment.smsnp.semi_crf.WordSemiCRFFeatureManager;
import com.statnlp.experiment.smsnp.semi_crf.WordSemiCRFNetworkCompiler;
import com.statnlp.experiment.smsnp.weak_semi_crf.CharWeakSemiCRFFeatureManager;
import com.statnlp.experiment.smsnp.weak_semi_crf.CharWeakSemiCRFNetworkCompiler;
import com.statnlp.experiment.smsnp.weak_semi_crf.WordWeakSemiCRFFeatureManager;
import com.statnlp.experiment.smsnp.weak_semi_crf.WordWeakSemiCRFNetworkCompiler;
import com.statnlp.hybridnetworks.DiscriminativeNetworkModel;
import com.statnlp.hybridnetworks.FeatureManager;
import com.statnlp.hybridnetworks.GenerativeNetworkModel;
import com.statnlp.hybridnetworks.GlobalNetworkParam;
import com.statnlp.hybridnetworks.NetworkCompiler;
import com.statnlp.hybridnetworks.NetworkConfig;
import com.statnlp.hybridnetworks.NetworkModel;

public class Main {
	
	public enum Algorithm {
		LINEAR_CRF(true),
		CHAR_SEMI_CRF(false),
		CHAR_WEAK_SEMI_CRF(false),
		WORD_SEMI_CRF(true),
		WORD_WEAK_SEMI_CRF(true),
		TOKENIZED_GOLD(true),
		;
		
		private boolean requireTokenized = false;
		
		private Algorithm(boolean requireTokenized){
			this.requireTokenized = requireTokenized;
		}
		
		public boolean requireTokenized(){
			return requireTokenized;
		}
		
		public static String helpString(){
			return "Please specify the algorithm from the following choices:\n"
					+ "\t-LINEAR_CRF\n"
					+ "\t-{CHAR,WORD}_WEAK_SEMI_CRF\n"
					+ "\t-{CHAR,WORD}_SEMI_CRF";
		}
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException, ClassNotFoundException, NoSuchFieldException, SecurityException, InterruptedException, IllegalArgumentException, IllegalAccessException{
		
		boolean serializeModel = true;
		String timestamp = Calendar.getInstance().getTime().toString();
		String modelPath = timestamp+".model";
		String logPath = timestamp+".log";
		String brownPath = null;
		boolean useCoNLLData = false;
		boolean useGoldTokenization = false;
		Algorithm algo = null;
		
		String train_filename = null;
		String test_filename = null;
		String result_filename = null;
		SMSNPInstance[] trainInstances = null;
		SMSNPInstance[] testInstances = null;
		Map<String, String> brownMap = null;
		
		int maxLength = 0;
		int maxSegmentLength = 0;
		boolean findMaxLength = true;
		boolean findMaxSegmentLength = true;
		boolean writeModelText = false;
		NetworkConfig._numThreads = 4;
		NetworkConfig.L2_REGULARIZATION_CONSTANT = 0.125;
		NetworkConfig.objtol = 1e-6;
		String weightInit = "random";
		
		int maxNumIterations = 5000;
		
		String[] features = new String[0];
		
		TokenizerMethod tokenizerMethod = TokenizerMethod.REGEX;
		
		int numExamplesPrinted = 10;
		
		int argIndex = 0;
		String[] moreArgs = new String[0];
		while(argIndex < args.length){
			String arg = args[argIndex];
			if(arg.charAt(0) == '-'){
				switch(arg.substring(1)){
				case "modelPath":
					serializeModel = true;
					modelPath = args[argIndex+1];
					argIndex += 2;
					break;
				case "writeModelText":
					writeModelText = true;
					argIndex += 1;
					break;
				case "inCONLLFormat":
					useCoNLLData = true;
					argIndex += 1;
					break;
				case "trainPath":
					train_filename = args[argIndex+1];
					argIndex += 2;
					break;
				case "testPath":
					test_filename = args[argIndex+1];
					argIndex += 2;
					break;
				case "resultPath":
					result_filename = args[argIndex+1];
					argIndex += 2;
					break;
				case "maxLength":
					maxLength = Integer.parseInt(args[argIndex+1]);
					findMaxLength = false;
					argIndex += 2;
					break;
				case "maxSegmentLength":
					maxSegmentLength = Integer.parseInt(args[argIndex+1]);
					findMaxSegmentLength = false;
					argIndex += 2;
					break;
				case "nThreads":
					NetworkConfig._numThreads = Integer.parseInt(args[argIndex+1]);
					argIndex += 2;
					break;
				case "l2":
					NetworkConfig.L2_REGULARIZATION_CONSTANT = Double.parseDouble(args[argIndex+1]);
					argIndex += 2;
					break;
				case "weightInit":
					weightInit = args[argIndex+1];
					if(weightInit.equals("random")){
						NetworkConfig.RANDOM_INIT_WEIGHT = true;
					} else {
						NetworkConfig.RANDOM_INIT_WEIGHT = false;
						NetworkConfig.FEATURE_INIT_WEIGHT = Double.parseDouble(weightInit);
					}
					argIndex += 2;
					break;
				case "objtol":
					NetworkConfig.objtol = Double.parseDouble(args[argIndex+1]);
					argIndex += 2;
					break;
				case "maxIter":
					maxNumIterations = Integer.parseInt(args[argIndex+1]);
					argIndex += 2;
					break;
				case "logPath":
					logPath = args[argIndex+1];
					argIndex += 2;
					break;
				case "brownPath":
					brownPath = args[argIndex+1];
					argIndex += 2;
					break;
				case "algo":
					try{
						algo = Algorithm.valueOf(args[argIndex+1].toUpperCase());
					} catch (IllegalArgumentException e){
						throw new IllegalArgumentException("Unrecognized algorithm: "+args[argIndex+1]+"\n"+Algorithm.helpString());
					}
					argIndex += 2;
					break;
				case "tokenizer":
					tokenizerMethod = TokenizerMethod.valueOf(args[argIndex+1].toUpperCase());
					argIndex += 2;
					break;
				case "useGoldTokenization":
					useGoldTokenization = true;
					argIndex += 1;
					break;
				case "features":
					features = args[argIndex+1].split(",");
					argIndex += 2;
					break;
				case "numExamplesPrinted":
					numExamplesPrinted = Integer.parseInt(args[argIndex+1]);
					argIndex += 2;
					break;
				case "h":
				case "help":
					printHelp();
					System.exit(0);
				case "-":
					moreArgs = Arrays.copyOfRange(args, argIndex+1, args.length);
					argIndex = args.length;
					break;
				default:
					throw new IllegalArgumentException("Unrecognized argument: "+arg);
				}
			} else {
				throw new IllegalArgumentException("Error while parsing: "+arg);
			}
		}
		if(algo == null){
			System.out.println(Algorithm.helpString());
			printHelp();
			System.exit(0);
		}
		if(brownPath != null){
			brownMap = new HashMap<String, String>();
			Scanner input = new Scanner(new File(brownPath));
			while(input.hasNextLine()){
				String[] tokens = input.nextLine().split("\t");
				brownMap.put(tokens[1], tokens[0]);
			}
			input.close();
		}
		
		PrintStream outstream = null;
		if(logPath != null){
			outstream = new PrintStream(logPath, "UTF-8");
		}

		FeatureManager fm = null;
		NetworkCompiler compiler = null;
		NetworkModel model = null;
		if(algo != Algorithm.TOKENIZED_GOLD){
			if(train_filename != null){
				List<SMSNPInstance> trainInstancesList;
				if(useCoNLLData){
					trainInstancesList = Arrays.asList(SMSNPUtil.readCoNLLData(train_filename, true, false));
				} else {
					trainInstancesList = Arrays.asList(SMSNPUtil.readData(train_filename, true, false));
				}
				
				SpanLabel[] labels = SpanLabel.LABELS.values().toArray(new SpanLabel[SpanLabel.LABELS.size()]);

				int totalSegments = 0;
				int totalIgnored = 0;
				for(int instanceIdx = trainInstancesList.size()-1; instanceIdx >= 0; instanceIdx--){
					SMSNPInstance instance = trainInstancesList.get(instanceIdx);
					int size = -1;
					if(algo.requireTokenized()){
						instance.getInputTokenized(tokenizerMethod, useGoldTokenization, true);
						instance.getOutputTokenized(tokenizerMethod, useGoldTokenization, false);
						size = instance.getInputTokenized().length;
					} else {
						size = instance.size();
					}
					if(findMaxLength){
						maxLength = Math.max(maxLength, size);
					} else if(size > maxLength){
						System.err.println(String.format("Ignoring instance (ID=%d, length=%d) because it is longer than max length %d", instance.getInstanceId(), size, maxLength));
						trainInstancesList.remove(instanceIdx);
						continue;
					}
					List<Span> output = instance.output;
					if(findMaxSegmentLength){ // Max span length is not set, set as the longest span
						if(algo.requireTokenized()){
							List<WordLabel> outputTokenized = instance.getOutputTokenized();
							int start = 0;
							for(int pos=0; pos<outputTokenized.size(); pos++){
								WordLabel label = outputTokenized.get(pos);
								if(pos == outputTokenized.size()-1 || label.form.startsWith("O") || outputTokenized.get(pos+1).id != label.id){
									maxSegmentLength = Math.max(maxSegmentLength, pos-start+1);
									start = pos+1;
								}
							}
						} else {
							for(Span span: output){
								maxSegmentLength = Math.max(maxSegmentLength, span.end-span.start);
							}
						}
					} else { // Max span length is set, ignore those spans longer than that
						if(maxSegmentLength > 1){ // Unless span length is 1, in which case reduce to linear CRF, do not ignore spans
							if(algo.requireTokenized()){
								List<WordLabel> outputTokenized = instance.getOutputTokenized();
								int start = 0;
								for(int pos=0; pos<outputTokenized.size(); pos++){
									WordLabel label = outputTokenized.get(pos);
									if(pos == outputTokenized.size()-1 || label.form.startsWith("O") || outputTokenized.get(pos+1).id != label.id){
										if(pos-start+1 > maxSegmentLength){
											totalIgnored += 1;
											for(int i=start; i<=pos; i++){
												outputTokenized.set(i, WordLabel.get("O"));
											}
										}
										start = pos+1;
										totalSegments += 1;
									}
								}
							} else {
								int localIgnored = 0;
								int localTotal = 0;
								for(int spanIdx=output.size()-1; spanIdx>=0; spanIdx--){
									Span span = output.get(spanIdx);
									localTotal += 1;
									if(span.end-span.start > maxSegmentLength){
										localIgnored += 1;
										// Tokenize the span to make each token a single O span, hopefully making it shorter
										List<Span> wordSpans = SMSNPUtil.getWordSpans(instance.input.substring(span.start, span.end), tokenizerMethod, null);
										output.remove(spanIdx);
										for(int wordSpanIdx=wordSpans.size()-1; wordSpanIdx>=0; wordSpanIdx--){
											Span wordSpan = wordSpans.get(wordSpanIdx);
											if(wordSpan.end-wordSpan.start > maxSegmentLength){
												// One of the tokens is still too long, ignore the instance altogether
												System.err.println(String.format("Ignoring instance (ID=%d) because one of its spans (%s) is longer than max span length %d", instance.getInstanceId(), instance.input.substring(wordSpan.start+span.start, wordSpan.end+span.start), maxSegmentLength));
												trainInstancesList.remove(instanceIdx);
												localIgnored = 0;
												localTotal = 0;
												spanIdx = -1;
												wordSpanIdx = -1;
												continue;
											}
											output.add(spanIdx, new Span(wordSpan.start, wordSpan.end, SpanLabel.get("O")));
										}
									}
								}
								totalIgnored += localIgnored;
								totalSegments += localTotal;
							}
						}
					}
				}
				if(totalIgnored > 0){
					System.err.println(String.format("Ignored %d/%d spans", totalIgnored, totalSegments));
				}
				
				trainInstances = trainInstancesList.toArray(new SMSNPInstance[trainInstancesList.size()]);
				
				NetworkConfig.TRAIN_MODE_IS_GENERATIVE = false;
				NetworkConfig._CACHE_FEATURES_DURING_TRAINING = true;
				
				int size = trainInstances.length;
				
				print("Read.."+size+" instances.", true, outstream, System.err);
				
				WordLabel[] wordLabels = WordLabel.LABELS.values().toArray(new WordLabel[WordLabel.LABELS.size()]);
				switch(algo){
				case LINEAR_CRF:
					fm = new LinearCRFFeatureManager(new GlobalNetworkParam(), tokenizerMethod, brownMap, features, moreArgs);
					compiler = new LinearCRFNetworkCompiler(wordLabels, tokenizerMethod);
					break;
				case CHAR_SEMI_CRF:
					fm = new CharSemiCRFFeatureManager(new GlobalNetworkParam(), tokenizerMethod, brownMap, features, moreArgs);
					compiler = new CharSemiCRFNetworkCompiler(labels, maxLength, maxSegmentLength);
					break;
				case CHAR_WEAK_SEMI_CRF:
					fm = new CharWeakSemiCRFFeatureManager(new GlobalNetworkParam(), tokenizerMethod, brownMap, features, moreArgs);
					compiler = new CharWeakSemiCRFNetworkCompiler(labels, maxLength, maxSegmentLength);
					break;
				case WORD_SEMI_CRF:
					fm = new WordSemiCRFFeatureManager(new GlobalNetworkParam(), tokenizerMethod, brownMap, features, moreArgs);
					compiler = new WordSemiCRFNetworkCompiler(wordLabels, maxLength, maxSegmentLength);
					break;
				case WORD_WEAK_SEMI_CRF:
					fm = new WordWeakSemiCRFFeatureManager(new GlobalNetworkParam(), tokenizerMethod, brownMap, features, moreArgs);
					compiler = new WordWeakSemiCRFNetworkCompiler(wordLabels, maxLength, maxSegmentLength);
					break;
				case TOKENIZED_GOLD: // Won't happen since we are inside `if (algo != Algorithm.TOKENIZED_GOLD)` block
					break;
				}
				
				model = NetworkConfig.TRAIN_MODE_IS_GENERATIVE ? GenerativeNetworkModel.create(fm, compiler) : DiscriminativeNetworkModel.create(fm, compiler);
	
				/* ******************* *
				 * Main training phase *
				 * ******************* */
				model.train(trainInstances, maxNumIterations);
				
				if(serializeModel){
					print("Writing object...", false, outstream, System.out);
					long startTime = System.currentTimeMillis();
					ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(modelPath));
					oos.writeObject(model);
					oos.close();
					long endTime = System.currentTimeMillis();
					print(String.format("Done in %.3fs", (endTime-startTime)/1000.0), true, outstream, System.out);
				}
			} else {
				print("Reading object...", false, outstream, System.out);
				long startTime = System.currentTimeMillis();
				ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelPath));
				model = (NetworkModel)ois.readObject();
				ois.close();
				Field _fm = NetworkModel.class.getDeclaredField("_fm");
				_fm.setAccessible(true);
				fm = (FeatureManager)_fm.get(model);
				Field _compiler = NetworkModel.class.getDeclaredField("_compiler");
				_compiler.setAccessible(true);
				compiler = (NetworkCompiler)_compiler.get(model);

				long endTime = System.currentTimeMillis();
				print(String.format("Done in %.3fs", (endTime-startTime)/1000.0), true, outstream, System.out);
			}
			if(writeModelText){
				PrintStream modelTextWriter = new PrintStream(modelPath+".txt");
				modelTextWriter.println("Algorithm: "+algo);
				modelTextWriter.println("Serialize?: "+serializeModel);
				modelTextWriter.println("Model path: "+modelPath);
				modelTextWriter.println("In CoNLL?: "+useCoNLLData);
				modelTextWriter.println("Train path: "+train_filename);
				modelTextWriter.println("Test path: "+test_filename);
				modelTextWriter.println("Max length: "+maxLength);
				modelTextWriter.println("Max span: "+maxSegmentLength);
				modelTextWriter.println("#Threads: "+NetworkConfig._numThreads);
				modelTextWriter.println("L2 param: "+NetworkConfig.L2_REGULARIZATION_CONSTANT);
				modelTextWriter.println("Weight init: "+weightInit);
				modelTextWriter.println("objtol: "+NetworkConfig.objtol);
				modelTextWriter.println("Max iter: "+maxNumIterations);
				modelTextWriter.println("Tokenizer: "+tokenizerMethod);
				modelTextWriter.println("Features: "+Arrays.asList(features));
				modelTextWriter.println();
				modelTextWriter.println("Labels:");
				List<?> labelsUsed = new ArrayList<Object>();
				switch(algo){
				case LINEAR_CRF:
					labelsUsed = Arrays.asList(((LinearCRFNetworkCompiler)compiler)._labels);
					break;
				case CHAR_SEMI_CRF:
					labelsUsed = Arrays.asList(((CharSemiCRFNetworkCompiler)compiler).labels);
					break;
				case CHAR_WEAK_SEMI_CRF:
					labelsUsed = Arrays.asList(((CharWeakSemiCRFNetworkCompiler)compiler).labels);
					break;
				case WORD_SEMI_CRF:
					labelsUsed = Arrays.asList(((WordSemiCRFNetworkCompiler)compiler).labels);
					break;
				case WORD_WEAK_SEMI_CRF:
					labelsUsed = Arrays.asList(((WordWeakSemiCRFNetworkCompiler)compiler).labels);
					break;
				case TOKENIZED_GOLD:
					break;
				}
				for(Object obj: labelsUsed){
					modelTextWriter.println(obj);
				}
				GlobalNetworkParam paramG = fm.getParam_G();
				modelTextWriter.println("Num features: "+paramG.countFeatures());
				modelTextWriter.println("Features:");
				HashMap<String, HashMap<String, HashMap<String, Integer>>> featureIntMap = paramG.getFeatureIntMap();
				for(String featureType: sorted(featureIntMap.keySet())){
					modelTextWriter.println(featureType);
					HashMap<String, HashMap<String, Integer>> outputInputMap = featureIntMap.get(featureType);
					for(String output: sorted(outputInputMap.keySet())){
						modelTextWriter.println("\t"+output);
						HashMap<String, Integer> inputMap = outputInputMap.get(output);
						for(String input: sorted(inputMap.keySet())){
							int featureId = inputMap.get(input);
							modelTextWriter.println("\t\t"+input+" "+featureId+" "+fm.getParam_G().getWeight(featureId));
						}
					}
				}
				modelTextWriter.close();
			}
		}
		
		if(test_filename != null){
			if(useCoNLLData){
				testInstances = SMSNPUtil.readCoNLLData(test_filename, false, false);
			} else {
				testInstances = SMSNPUtil.readData(test_filename, false, false);
			}
			if(algo.requireTokenized()){
				for(SMSNPInstance instance: testInstances){
					instance.getInputTokenized(tokenizerMethod, useGoldTokenization, true);
					instance.getOutputTokenized(tokenizerMethod, useGoldTokenization, false);
				}
			}
			Instance[] predictions = null;
			if(algo != Algorithm.TOKENIZED_GOLD){
				predictions = model.decode(testInstances);
				List<SMSNPInstance> predictionsList = new ArrayList<SMSNPInstance>();
				for(Instance instance: predictions){
					predictionsList.add((SMSNPInstance)instance);
				}
				predictionsList.sort(Comparator.comparing(Instance::getInstanceId));
				if(result_filename == null){
					result_filename = test_filename+".result";
				}
				PrintStream result = new PrintStream(result_filename);
				if(algo.requireTokenized()){
					for(SMSNPInstance instance: predictionsList){
						result.println(instance.toCoNLLString());
					}
				} else {
					for(SMSNPInstance instance: predictionsList){
						result.println(instance.toString());
					}
				}
				result.close();
			} else {
				for(SMSNPInstance instance: testInstances){
					instance.setPredictionTokenized(instance.getOutputTokenized());
				}
				predictions = testInstances;
			}
			SMSNPEvaluator.evaluate(predictions, outstream, numExamplesPrinted);
		}
	}
	
	private static List<String> sorted(Set<String> coll){
		List<String> result = new ArrayList<String>(coll);
		Collections.sort(result);
		return result;
	}
	
	private static void printHelp(){
		System.out.println("Options:\n"
				+ "-modelPath <modelPath>\n"
				+ "\tSerialize model to <modelPath>\n"
				+ "-writeModelText\n"
				+ "\tWrite the model in text version for debugging purpose\n"
				+ "-trainPath <trainPath>\n"
				+ "\tTake training file from <trainPath>. If not specified, no training is performed\n"
				+ "\tWill attempt to load the model if test is specified\n"
				+ "-testPath <testPath>\n"
				+ "\tTake test file from <testPath>. If not specified, no test is performed\n"
				+ "-resultPath <testPath>\n"
				+ "\tPrint result from testing to <resultPath>. If not specified, it is based on the test name\n"
				+ "-inCONLLFormat\n"
				+ "\tWhether the input file is in CoNLL format. Default to false\n"
				+ "-maxLength <n>\n"
				+ "\tSet the maximum input length that will be supported to <n>.\n"
				+ "\tDefault to maximum length in training set\n"
				+ "-maxSegmentLength <n>\n"
				+ "\tSet the maximum segment length to <n>. Default to maximum in training set\n"
				+ "-nThreads <n>\n"
				+ "\tSet the number of threads to <n>. Default to 4\n"
				+ "-l2 <value>\n"
				+ "\tSet the L2 regularization parameter weight to <value>. Default to 0.01\n"
				+ "-weightInit <\"random\" or value>\n"
				+ "\tWeight initialization. If \"random\", the weights will be randomly assigned values between\n"
				+ "\t-0.05 to 0.05 (uniform distribution). Otherwise, it will be set to the value provided.\n"
				+ "\tDefault to random\n"
				+ "-objtol <value>\n"
				+ "\tStop when the improvement of objective function is less than <value>. Default to 0.01\n"
				+ "\tNote that the training will also stop when the ratio of change is\n"
				+ "\tless than 0.01% for 3 consecutive iterations\n"
				+ "-maxIter <n>\n"
				+ "\tSet the maximum number of iterations to <n>. Default to 5000\n"
				+ "-logPath <logPath>\n"
				+ "\tPrint output and evaluation result to file at <logPath>.\n"
				+ "\tNote that the output will still be printed to STDOUT\n"
				+ "-algo\n"
				+ "\tThe algorithm to be used:\n"
				+ "\t-LINEAR_CRF: Use linear-chain CRF\n"
				+ "\t-{CHAR,WORD}_WEAK_SEMI_CRF: Use the weak version of semi-CRF\n"
				+ "\t-{CHAR,WORD}_SEMI_CRF: Use the semi-CRF\n"
				+ "\t-TOKENIZED_GOLD: Use the gold span as the prediction, but after going through tokenization\n"
				+ "\t                 To check the best performance when only tokenized data is available\n"
				+ "-tokenizer\n"
				+ "\tThe tokenizer method to be used: whitespace or regex. Default to regex\n"
				+ "-useGoldTokenization\n"
				+ "\tTokenize the files using the gold information, if available\n"
				+ "-features\n"
				+ "\tThe features to be used. The available features depend on the algorithm used.\n"
				+ "-numExamplesPrinted\n"
				+ "\tSpecify the number of examples printed during evaluation. Default to 10\n"
				);
	}

}

