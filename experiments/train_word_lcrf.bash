# Script to do training
# Each invocation of this script will produce 5 models, one for each l2

# Initialization
if [ -z ${run_name+x} ]; then
    run_name="lcrf-trigram"  # CHANGE THIS RUN NAME
fi
algo="LINEAR_CRF"
if [ -z ${tokenizer+x} ]; then
    tokenizer="regex"
fi
if [ -z ${additional_features+x} ]; then
    additional_features=""
fi
find_l2=true
if [ -z ${l2+x} ]; then
    find_l2=false
else
    l2opt=${l2}
fi
if [ -z ${memory_size+x} ]; then
    memory_size=15                   # CHANGE DEFAULT MEMORY USAGE
fi
if [ -z ${train_file+x} ]; then
    train_file="../data/SMSNP.train"    # CHANGE DEFAULT TRAINING DATA
fi
if [ -z ${test_file+x} ]; then
    test_file="../data/SMSNP.dev"       # CHANGE DEFAULT TEST DATA
fi
if [ -z ${n_threads+x} ]; then
    n_threads=4                      # CHANGE DEFAULT NUMBER OF THREADS
fi
if [ -z ${brown_path+x} ]; then
    brown_path="../63-c100-p1.out/paths"
fi
if [ -z ${experiment_dir+x} ]; then
    experiment_dir=${run_name}
fi
mkdir -p ${experiment_dir}

#child_pids=()
for l2 in "0.125" "0.25" "0.5" "1.0" "2.0"; do
    if [ !${find_l2} ]; then
        l2=${l2opt}
    fi
    #(  # Using sub-shell
    model_file=${run_name}-${l2}.model
    result_file=${run_name}-${l2}.result
    log_file=${run_name}-${l2}.log
    run_file=${run_name}-${l2}.runlog
    trap "cat ${experiment_dir}/${run_file} >> error.log; exit 1" ERR
    java -Xmx${memory_size}g -Xms${memory_size}g \
         -jar ../target/experiments-smsnp-1.0-SNAPSHOT.jar \
         -trainPath $train_file \
         -testPath $test_file \
         -modelPath ${experiment_dir}/${model_file} \
         -resultPath ${experiment_dir}/${result_file} \
         -brownPath ${brown_path} \
         -nThreads $n_threads \
         -algo ${algo} \
         -tokenizer ${tokenizer} \
         -features word,transition${additional_features} \
         -l2 $l2 \
         -logPath ${experiment_dir}/${log_file} \
         -- \
         -word_only_left_window \
         -word_half_window_size 2 \
         -prefix_length 3 \
         -suffix_length 3 \
         > >(tee ${experiment_dir}/${run_file}) 2>&1
         #> > ${experiment_dir}/${run_file} 2>&1  # Use this if using sub-shell
    #) &
    #child_pids+=($!)
    if [ !${find_l2} ]; then
        break
    fi
done
#for child_pid in ${child_pids[@]}; do
#    wait $child_pid
#done
echo "Experiments done"
