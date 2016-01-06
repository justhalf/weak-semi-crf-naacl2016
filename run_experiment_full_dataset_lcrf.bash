# To run the experiments on full dataset, using our LinearCRF
dry_run=true  # Set this to true to just print the experiments that are going to be executed
retrain_existing=true  # If the model with the same name is present, no retraining is done, unless this value is true
retest_existing=false  # If the model with the same name is present, no retesting is done, unless this value is true

experiment_dir="experiments/full_dataset" # The directory to store all the results
base_features="word,transition" # The base features used by LCRF

base_train_path="data/SMSNP.train" # The training file
base_test_path="data/SMSNP"  # The base filename for test files. This will be appended with ".dev" or ".test"
brown_path="63-c100-p1.out/paths" # The Brown clustering file
algo="lcrf"

if $dry_run; then
    echo "Running in dry run mode"
fi
for tokenizer in "regex"; do # "whitespace"; do
    for use_gold in ""; do # "-useGoldTokenization"; do
        for test_file in "dev"; do # "test"; do
            base_experiment_name="${algo}.${tokenizer}.${test_file}"
            base_model_name="${algo}.${tokenizer}"
            if [ -n "${use_gold}" ]; then
                base_experiment_name="${base_experiment_name}.gold_tokenized"
                base_model_name="${base_model_name}.gold_tokenized"
            fi
            for use_brown in false true; do
                for with_affix in false true; do
                    for l2 in "0.125" "0.25" "0.5" "1.0" "2.0"; do
                        experiment_name="${base_experiment_name}"
                        model_name="${base_model_name}"
                        features="${base_features}"
                        if $use_brown; then
                            experiment_name+=".brown"
                            model_name+=".brown"
                            features+=",brown_cluster"
                        fi
                        if $with_affix; then
                            experiment_name+=".affix"
                            model_name+=".affix"
                            features+=",prefix,suffix"
                        fi
                        experiment_name+=".${l2}"
                        model_name+=".${l2}"
                        if [ -a "${experiment_dir}/${model_name}.model" ] && ! ${retrain_existing}; then
                            if ${retest_existing}; then
                                train_path=""
                            else
                                continue
                            fi
                        else
                            train_path="-trainPath ${base_train_path}"
                        fi
                        echo ${experiment_name} ${use_gold} ${train_path} ${model_name}
                        if ! ${dry_run}; then
                            time java -Xmx6g -Xms6g -jar target/experiments-smsnp-1.0-SNAPSHOT.jar \
                                ${train_path} \
                                -testPath ${base_test_path}.${test_file} \
                                -tokenizer ${tokenizer} \
                                -l2 ${l2} \
                                ${use_gold} \
                                -brownPath ${brown_path} \
                                -algo LINEAR_CRF \
                                -weightInit random \
                                -logPath ${experiment_dir}/${experiment_name}.log \
                                -modelPath ${experiment_dir}/${model_name}.model \
                                -resultPath ${experiment_dir}/${experiment_name}.result \
                                -features ${features} \
                                -writeModelText \
                                -numExamplesPrinted 0 \
                                -- \
                                -word_only_left_window \
                                -word_half_window_size 1 \
                                2>&1 | tee ${experiment_dir}/${experiment_name}.runlog
                        fi
                    done
                done
            done
        done
    done
done

