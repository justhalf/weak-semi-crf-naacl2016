/** Statistical Natural Language Processing System
    Copyright (C) 2014-2016  Lu, Wei

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
package com.statnlp.hybridnetworks;

import static com.statnlp.commons.Utils.print;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.statnlp.commons.types.Instance;
import com.statnlp.hybridnetworks.NetworkConfig.ModelType;

public abstract class NetworkModel implements Serializable{
	
	private static final Random RANDOM = new Random(NetworkConfig.RANDOM_BATCH_SEED);

	private static final long serialVersionUID = 8695006398137564299L;
	
	//the global feature manager.
	protected FeatureManager _fm;
	//the builder
	protected NetworkCompiler _compiler;
	//the list of instances.
	protected transient Instance[] _allInstances;
	protected transient Network[] unlabeledNetworkByInstanceId;
	protected transient Network[] labeledNetworkByInstanceId;
	//the number of threads.
	protected transient int _numThreads = NetworkConfig._numThreads;
	//the local learners.
	private transient LocalNetworkLearnerThread[] _learners;
	//the local decoder.
	private transient LocalNetworkDecoderThread[] _decoders;
	private transient PrintStream[] outstreams = new PrintStream[]{System.out};
	
	public NetworkModel(FeatureManager fm, NetworkCompiler compiler, PrintStream... outstreams){
		this._fm = fm;
		this._numThreads = NetworkConfig._numThreads;
		this._compiler = compiler;
		if(outstreams == null){
			outstreams = new PrintStream[0];
		}
		this.outstreams = new PrintStream[outstreams.length+1];
		this.outstreams[0] = System.out;
		for(int i=0; i<outstreams.length; i++){
			this.outstreams[i+1] = outstreams[i];
		}
	}
	
	public int getNumThreads(){
		return this._numThreads;
	}
	
	public Network getLabeledNetwork(int instanceId){
		return labeledNetworkByInstanceId[instanceId-1];
	}
	
	public Network getUnlabeledNetwork(int instanceId){
		return unlabeledNetworkByInstanceId[instanceId-1];
	}
	
	protected abstract Instance[][] splitInstancesForTrain();
	
	public Instance[][] splitInstancesForTest() {
		
		System.err.println("#instances="+this._allInstances.length);
		
		Instance[][] insts = new Instance[this._numThreads][];

		ArrayList<ArrayList<Instance>> insts_list = new ArrayList<ArrayList<Instance>>();
		int threadId;
		for(threadId = 0; threadId<this._numThreads; threadId++){
			insts_list.add(new ArrayList<Instance>());
		}
		
		threadId = 0;
		for(int k = 0; k<this._allInstances.length; k++){
			Instance inst = this._allInstances[k];
			insts_list.get(threadId).add(inst);
			threadId = (threadId+1)%this._numThreads;
		}
		
		for(threadId = 0; threadId<this._numThreads; threadId++){
			int size = insts_list.get(threadId).size();
			insts[threadId] = new Instance[size];
			for(int i = 0; i < size; i++){
				Instance inst = insts_list.get(threadId).get(i);
				insts[threadId][i] = inst;
			}
			print("Thread "+threadId+" has "+insts[threadId].length+" instances.", outstreams);
		}
		
		return insts;
	}
	
	public void train(Instance[] allInstances, int maxNumIterations) throws InterruptedException{
		
		this._numThreads = NetworkConfig._numThreads;
		
		this._allInstances = allInstances;
		for(int k = 0; k<this._allInstances.length; k++){
//			System.err.println(k);
			this._allInstances[k].setInstanceId(k+1);
		}
		this._fm.getParam_G().setInstsNum(this._allInstances.length);
		HashSet<Integer> batchInstIds = new HashSet<Integer>();
		ArrayList<Integer> instIds = new ArrayList<Integer>();
		for(int i=0;i<_allInstances.length;i++) instIds.add(i+1);
		
		//create the threads.
		this._learners = new LocalNetworkLearnerThread[this._numThreads];
		
		Instance[][] insts = this.splitInstancesForTrain();
		
		// The first touch
		touch(insts, false);
		
		for(int threadId=0; threadId<this._numThreads; threadId++){
			if(NetworkConfig._BUILD_FEATURES_FROM_LABELED_ONLY){
				// We extract features only from labeled instance in the first touch, so we don't know what
				// features are present in each thread. So copy all features to each thread.
				// Since we extract only from labeled instances, the feature index will be smaller
				this._fm.addIntoLocalFeatures(this._learners[threadId].getLocalNetworkParam()._globalFeature2LocalFeature);
			}
			this._learners[threadId].getLocalNetworkParam().finalizeIt();
		}

		//complete the type2int map. only in generative model
		if(NetworkConfig.TRAIN_MODE_IS_GENERATIVE){
			this._fm.completeType2Int(); 
		}
		
		//finalize the features.
		this._fm.getParam_G().lockIt();
		
		if(NetworkConfig._BUILD_FEATURES_FROM_LABELED_ONLY && NetworkConfig._CACHE_FEATURES_DURING_TRAINING){
			touch(insts, true); // Touch again to cache the features, both in labeled and unlabeled
		}
		
		if(NetworkConfig._CACHE_FEATURES_DURING_TRAINING){
			for(int threadId=0; threadId<this._numThreads; threadId++){
				// This was previously in each LocalNetworkLearnerThread finalizeIt, but moved here since
				// when we call finalizeIt above, it should not delete this variable first, because we were
				// using it in the second touch.
				this._learners[threadId].getLocalNetworkParam()._globalFeature2LocalFeature = null;
			}
		}
		
		ExecutorService pool = Executors.newFixedThreadPool(this._numThreads);
		List<Callable<Void>> callables = Arrays.asList((Callable<Void>[])this._learners);
		
		int multiplier = 1;
		if(NetworkConfig.MODEL_TYPE == ModelType.SSVM || NetworkConfig.MODEL_TYPE == ModelType.SOFTMAX_MARGIN){
			multiplier = -1;
		}
		
		double obj_old = Double.NEGATIVE_INFINITY;
		//run the EM-style algorithm now...
		long startTime = System.currentTimeMillis();
		try{
			for(int it = 0; it<=maxNumIterations; it++){
				//at each iteration, shuffle the inst ids. and reset the set, which is already in the learner thread
				if(NetworkConfig.USE_BATCH_SGD){
					batchInstIds.clear();
					Collections.shuffle(instIds, RANDOM);
					int size = NetworkConfig.batchSize >= this._allInstances.length ? this._allInstances.length:NetworkConfig.batchSize; 
					for(int iid = 0; iid<size; iid++){
						batchInstIds.add(instIds.get(iid));
					}
				}
				for(LocalNetworkLearnerThread learner: this._learners){
					learner.setIterationNumber(it);
					if(NetworkConfig.USE_BATCH_SGD) learner.setInstanceIdSet(batchInstIds);
				}
				long time = System.currentTimeMillis();
				List<Future<Void>> results = pool.invokeAll(callables);
				for(Future<Void> result: results){
					try{
						result.get(); // To ensure any exception is thrown
					} catch (ExecutionException e){
						throw new RuntimeException(e);
					}
				}
				boolean done = true;
				boolean lastIter = (it == maxNumIterations);
				if(lastIter){
					done = this._fm.update(true);
				} else {
					done = this._fm.update();
				}
				time = System.currentTimeMillis() - time;
				double obj = this._fm.getParam_G().getObj_old();
				print(String.format("Iteration %d: Obj=%-18.12f Time=%.3fs %.12f Total time: %.3fs", it, multiplier*obj, time/1000.0, obj/obj_old, (System.currentTimeMillis()-startTime)/1000.0), outstreams);
	//			System.out.println("Iteration "+it+"\tObjective="+obj+"\tTime="+time/1000.0+" seconds."+"\t"+obj/obj_old);
				if(NetworkConfig.TRAIN_MODE_IS_GENERATIVE && it>1 && obj<obj_old && Math.abs(obj-obj_old)>1E-5){
					throw new RuntimeException("Error:\n"+obj_old+"\n>\n"+obj);
				}
				obj_old = obj;
				if(lastIter){
					print("Training completes. The specified number of iterations ("+it+") has passed.", outstreams);
					break;
				}
				if(done){
					print("Training completes. No significant progress (<objtol) after "+it+" iterations.", outstreams);
					break;
				}
			}
		} finally {
			pool.shutdown();
		}
	}

	private void touch(Instance[][] insts, boolean keepExisting) throws InterruptedException {
		if(NetworkConfig._SEQUENTIAL_FEATURE_EXTRACTION || NetworkConfig._numThreads == 1){
			for(int threadId = 0; threadId<this._numThreads; threadId++){
				if(!keepExisting){
					this._learners[threadId] = new LocalNetworkLearnerThread(threadId, this._fm, insts[threadId], this._compiler, 0);
				} else {
					this._learners[threadId] = this._learners[threadId].copyThread();
				}
				this._learners[threadId].touch();
				System.err.println("Okay..thread "+threadId+" touched.");
			}
		} else {
			for(int threadId = 0; threadId < this._numThreads; threadId++){
				if(!keepExisting){
					this._learners[threadId] = new LocalNetworkLearnerThread(threadId, this._fm, insts[threadId], this._compiler, -1);
				} else {
					this._learners[threadId] = this._learners[threadId].copyThread();
				}
				this._learners[threadId].setTouch();
				this._learners[threadId].start();
			}
			for(int threadId = 0; threadId < this._numThreads; threadId++){
				this._learners[threadId].join();
				this._learners[threadId].setUnTouch();
			}
			if(!keepExisting){
				this._fm.mergeSubFeaturesToGlobalFeatures();
			}
		}
		if(labeledNetworkByInstanceId == null || unlabeledNetworkByInstanceId == null){
			labeledNetworkByInstanceId = new Network[this._allInstances.length];
			unlabeledNetworkByInstanceId = new Network[this._allInstances.length];
			Network[] arr;
			for(int threadId=0; threadId < insts.length; threadId++){
				LocalNetworkLearnerThread learner = this._learners[threadId];
				for(int networkId=0; networkId < insts[threadId].length; networkId++){
					Instance instance = insts[threadId][networkId];
					int instanceId = instance.getInstanceId();
					if(instanceId < 0){
						arr = unlabeledNetworkByInstanceId;
						instanceId = -instanceId;
					} else {
						arr = labeledNetworkByInstanceId;
					}
					instanceId -= 1;
					arr[instanceId] = learner.getNetwork(networkId);
				}
			}
			if(unlabeledNetworkByInstanceId[0] == null){
				arr = labeledNetworkByInstanceId;
				labeledNetworkByInstanceId = unlabeledNetworkByInstanceId;
				unlabeledNetworkByInstanceId = arr;
			}
		}
	}
	
	public Instance[] decode(Instance[] allInstances) throws InterruptedException {
		return decode(allInstances, false);
	}
	
	public Instance[] decode(Instance[] allInstances, boolean cacheFeatures) throws InterruptedException{
		
//		if(NetworkConfig.TRAIN_MODE_IS_GENERATIVE){
//			this._fm.getParam_G().expandFeaturesForGenerativeModelDuringTesting();
//		}
		
		this._numThreads = NetworkConfig._numThreads;
		System.err.println("#threads:"+this._numThreads);
		
		Instance[] results = new Instance[allInstances.length];
		
		//all the instances.
		this._allInstances = allInstances;
		
		//create the threads.
		if(this._decoders == null || !cacheFeatures){
			this._decoders = new LocalNetworkDecoderThread[this._numThreads];
		}
		
		Instance[][] insts = this.splitInstancesForTest();
		
		//distribute the works into different threads.
		for(int threadId = 0; threadId<this._numThreads; threadId++){
			if(cacheFeatures && this._decoders[threadId] != null){
				this._decoders[threadId] = new LocalNetworkDecoderThread(threadId, this._fm, insts[threadId], this._compiler, this._decoders[threadId].getParam(), true);
			} else {
				this._decoders[threadId] = new LocalNetworkDecoderThread(threadId, this._fm, insts[threadId], this._compiler, true);
			}
		}
		
		System.err.println("Okay. Decoding started.");
		
		long time = System.currentTimeMillis();
		for(int threadId = 0; threadId<this._numThreads; threadId++){
			this._decoders[threadId].start();
		}
		for(int threadId = 0; threadId<this._numThreads; threadId++){
			this._decoders[threadId].join();
		}
		
		System.err.println("Okay. Decoding done.");
		time = System.currentTimeMillis() - time;
		System.err.println("Overall decoding time = "+ time/1000.0 +" secs.");
		
		int k = 0;
		for(int threadId = 0; threadId<this._numThreads; threadId++){
			Instance[] outputs = this._decoders[threadId].getOutputs();
			for(Instance output : outputs){
				results[k++] = output;
			}
		}
		
		return results;
	}
	
}
