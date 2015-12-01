package com.statnlp.experiment.smsnp;

import static com.statnlp.experiment.smsnp.SMSNPIOUtil.print;

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
import java.util.List;

import com.statnlp.commons.types.Instance;
import com.statnlp.hybridnetworks.DiscriminativeNetworkModel;
import com.statnlp.hybridnetworks.FeatureManager;
import com.statnlp.hybridnetworks.GenerativeNetworkModel;
import com.statnlp.hybridnetworks.GlobalNetworkParam;
import com.statnlp.hybridnetworks.NetworkCompiler;
import com.statnlp.hybridnetworks.NetworkConfig;
import com.statnlp.hybridnetworks.NetworkModel;

import justhalf.nlp.tokenizer.WhitespaceTokenizer;

public class Main {
	
	public static void main(String[] args) throws FileNotFoundException, IOException, ClassNotFoundException, NoSuchFieldException, SecurityException, InterruptedException, IllegalArgumentException, IllegalAccessException{
		
		boolean serializeModel = false;
		String modelPath = "experiments/SMSNP.100.model";
		String logPath = "logs/SMSNP.100.log";
		boolean useCoNLLData = false;
		boolean useWeakSemiCRF = true;
		
		String train_filename;
		String test_filename;
		SMSNPInstance[] trainInstances;
		SMSNPInstance[] testInstances;
		
		if(useCoNLLData){
			train_filename = "data/SMSNP.conll.train.100";
			test_filename = "data/SMSNP.conll.dev.100";
		} else {
			train_filename = "data/SMSNP.train.500";
			test_filename = "data/SMSNP.dev.100";
		}
		int maxLength = 0;
		int maxSpan = 0;
		boolean findMaxLength = true;
		boolean findMaxSpan = true;
		NetworkConfig._numThreads = 4;
		NetworkConfig.L2_REGULARIZATION_CONSTANT = 0.01;
		NetworkConfig.objtol = 1e-2;
		
		int maxNumIterations = 5000;
		
		int argIndex = 0;
		while(argIndex < args.length){
			String arg = args[argIndex];
			if(arg.charAt(0) == '-'){
				switch(arg.substring(1)){
				case "serializeTo":
					serializeModel = true;
					modelPath = args[argIndex+1];
					argIndex += 2;
					break;
				case "noSerialize":
					serializeModel = false;
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
				case "noTest":
					test_filename = null;
					argIndex += 1;
					break;
				case "maxLength":
					maxLength = Integer.parseInt(args[argIndex+1]);
					findMaxLength = false;
					argIndex += 2;
					break;
				case "maxSpan":
					maxSpan = Integer.parseInt(args[argIndex+1]);
					findMaxSpan = false;
					argIndex += 2;
					break;
				case "nThreads":
					NetworkConfig._numThreads = Integer.parseInt(args[argIndex+1]);
					argIndex += 2;
					break;
				case "l2reg":
					NetworkConfig.L2_REGULARIZATION_CONSTANT = Double.parseDouble(args[argIndex+1]);
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
				case "logFile":
					logPath = args[argIndex+1];
					argIndex += 2;
					break;
				case "useWeakSemiCRF":
					useWeakSemiCRF = true;
					argIndex += 1;
					break;
				case "h":
				case "help":
					System.out.println("Options:\n"
							+ "-serializeTo <modelPath>\n"
							+ "\tSerialize to <modelPath>\n"
							+ "-noSerialize\n"
							+ "\tDo not serialize model\n"
							+ "-inCONLLFormat\n"
							+ "\tWhether the input file is in CoNLL format\n"
							+ "-trainPath <trainPath>\n"
							+ "\tTake training file from <trainPath>\n"
							+ "-testPath <testPath>\n"
							+ "\tTake test file from <testPath>\n"
							+ "-noTest\n"
							+ "\tNo testing will be performed\n"
							+ "-maxLength <n>\n"
							+ "\tSet the maximum input length that will be supported to <n>. Default to maximum length in training and test set\n"
							+ "-maxSpan <n>\n"
							+ "\tSet hte maximum span length to <n>. Default to maximum in training set\n"
							+ "-nThreads <n>\n"
							+ "\tSet the number of threads to <n>. Default to 4\n"
							+ "-l2reg <value>\n"
							+ "\tSet the L2 regularization parameter weight to <value>. Default to 0.01\n"
							+ "-objtol <value>\n"
							+ "\tStop when the improvement of objective function is less than <value>. Default to 0.01\n"
							+ "-maxIter <n>\n"
							+ "\tSet the maximum number of iterations to <n>. Default to 5000\n"
							+ "-logFile <logPath>\n"
							+ "\tPrint output to file at <logPath>. Note that the output will still be printed to STDOUT\n"
							+ "-useWeakSemiCRF\n"
							+ "\tUse the weak version of semi-CRF");
					System.exit(0);
				default:
					throw new IllegalArgumentException("Unrecognized argument: "+arg);
				}
			}
		}
		
		if(useCoNLLData){
			trainInstances = SMSNPIOUtil.readCoNLLData(train_filename, true, false);
			testInstances = SMSNPIOUtil.readCoNLLData(test_filename, false, false);
		} else {
			trainInstances = SMSNPIOUtil.readData(train_filename, true, false);
			testInstances = SMSNPIOUtil.readData(test_filename, false, false);
		}
		
		Label[] labels = Label.LABELS.values().toArray(new Label[Label.LABELS.size()]);
		
		PrintStream outstream = null;
		if(logPath != null){
			outstream = new PrintStream(logPath, "UTF-8");
		}
		
		for(SMSNPInstance instance: trainInstances){
			if(findMaxLength){
				maxLength = Math.max(maxLength, instance.size());
			}
			if(findMaxSpan){
				for(Span span: instance.output){
					maxSpan = Math.max(maxSpan, span.end-span.start);
				}
			}
		}
		if(findMaxLength){
			for(SMSNPInstance instance: testInstances){
				maxLength = Math.max(maxLength, instance.size());
			}
		}
		
		NetworkConfig.TRAIN_MODE_IS_GENERATIVE = false;
		NetworkConfig._CACHE_FEATURES_DURING_TRAINING = true;
		
		int size = trainInstances.length;
		
		print("Read.."+size+" instances.", outstream, System.err);
		
		FeatureManager fm = null;
		NetworkCompiler compiler = null;
		if(useWeakSemiCRF){
			fm = new WeakSemiCRFFeatureManager(new GlobalNetworkParam());
			compiler = new WeakSemiCRFNetworkCompiler(labels, maxLength, maxSpan);
		} else {
			fm = new SemiCRFFeatureManager(new GlobalNetworkParam());
			compiler = new SemiCRFNetworkCompiler(labels, maxLength, maxSpan);
		}
		
		NetworkModel model = NetworkConfig.TRAIN_MODE_IS_GENERATIVE ? GenerativeNetworkModel.create(fm, compiler) : DiscriminativeNetworkModel.create(fm, compiler);
		
		if(serializeModel){
			if(new File(modelPath).exists()){
				print("Reading object...", outstream, System.out);
				long startTime = System.currentTimeMillis();
				ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelPath));
				model = (NetworkModel)ois.readObject();
				ois.close();
				Field _fm = NetworkModel.class.getDeclaredField("_fm");
				_fm.setAccessible(true);
				fm = (FeatureManager)_fm.get(model);
				if(fm instanceof WeakSemiCRFFeatureManager){
					((WeakSemiCRFFeatureManager)fm).tokenizer = new WhitespaceTokenizer();
				}
				long endTime = System.currentTimeMillis();
				print(String.format("Done in %.3fs\n", (endTime-startTime)/1000.0), outstream, System.out);
			} else {
				model.train(trainInstances, maxNumIterations);
				print("Writing object...", outstream, System.out);
				long startTime = System.currentTimeMillis();
				ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(modelPath));
				oos.writeObject(model);
				oos.close();
				long endTime = System.currentTimeMillis();
				print(String.format("Done in %.3fs\n", (endTime-startTime)/1000.0), outstream, System.out);
			}
		} else {
			model.train(trainInstances, maxNumIterations);
		}
		print("Weights:", outstream);
		print(arrayToString(fm.getParam_G().getWeights()), outstream);
		
		Instance[] predictions = model.decode(testInstances);
		SMSNPEvaluator.evaluate(predictions, outstream);
	}
	
	private static String arrayToString(double[] array){
		List<Double> list = new ArrayList<Double>();
		for(double value: array){
			list.add(value);
		}
		return list.toString();
	}

}
