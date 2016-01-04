# To run the experiments in test set using LinearCRF using optimal parameters found in development set
dry_run=false
retest_existing=true
experiment_dir="experiments/full_dataset"
lcrf_features="word,transition"
brown_path=63-c100-p1.out/paths
for algo in "lcrf"; do # "crfpp"; do
    for tokenizer in "regex"; do # "whitespace"; do
        for use_gold in ""; do # "-useGoldTokenization"; do
            for test_file in "test"; do
                base_experiment_name="${algo}.${tokenizer}.${test_file}"
                base_model_name="${algo}.${tokenizer}"
                if [ -n "${use_gold}" ]; then
                    base_experiment_name="${base_experiment_name}.gold_tokenized"
                    base_model_name="${base_model_name}.gold_tokenized"
                fi
                for use_brown in false true; do
                    for with_subfix in false true; do
                        experiment_name="${base_experiment_name}"
                        model_name="${base_model_name}"
                        features="${lcrf_features}"
                        if $use_brown; then
                            experiment_name+=".brown"
                            model_name+=".brown"
                            features+=",brown_cluster"
                        fi
                        if $with_subfix; then
                            experiment_name+=".withsubfix"
                            model_name+=".withsubfix"
                            features+=",prefix,suffix"
                        fi
                        if $with_subfix; then
                            l2="1.0"
                        elif $use_brown; then
                            l2="0.5"
                        else
                            l2="0.125"
                        fi
                        experiment_name+=".${l2}"
                        model_name+=".${l2}"
                        if [ -a "${experiment_dir}/${model_name}.model" ]; then
                            if ${retest_existing}; then
                                train_path=""
                            else
                                continue
                            fi
                        else
                            train_path="-trainPath data/SMSNP.train"
                        fi
                        echo ${experiment_name} ${use_gold} ${train_path} ${model_name}
                        if ! ${dry_run}; then
                            time java -Xmx14g -Xms14g -jar target/experiments-smsnp-1.0-SNAPSHOT.jar \
                                ${train_path} \
                                -testPath data/SMSNP.${test_file} \
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
