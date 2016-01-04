# To run the experiments on full dataset, using CRF++ and our LinearCRF
dry_run=true  # Set this to true to just print the experiments that are going to be executed
retest_existing=false  # If the model with the same name is present, no retesting is done, unless this value is true
experiment_dir="experiments/full_dataset" # The directory to store all the results
lcrf_features="word,transition,brown_cluster" # The base features used by LCRF
do_tokenized_gold=false # Whether to use gold-tokenized data as the input (to investigate upper bound given ideal tokenization)
brown_path=63-c100-p1.out/paths # The Brown clustering file
if $dry_run; then
    echo "Running in dry run mode"
fi
for algo in "lcrf"; do # "crfpp"; do
    for tokenizer in "regex"; do # "whitespace"; do
        for use_gold in ""; do # "-useGoldTokenization"; do
            for test_file in "dev"; do # "test"; do
                base_experiment_name="${algo}.${tokenizer}.${test_file}"
                base_model_name="${algo}.${tokenizer}"
                if [ -n "${use_gold}" ]; then
                    base_experiment_name="${base_experiment_name}.gold_tokenized"
                    base_model_name="${base_model_name}.gold_tokenized"
                fi
                if [ "${algo}" = "lcrf" ]; then
                    base_experiment_name+=".brown"
                    base_model_name+=".brown"
                    for with_subfix in false; do # true; do
                        for l2 in "0.25"; do #"0.5" "1.0" "2.0"; do
                            if $with_subfix; then
                                experiment_name="${base_experiment_name}.withsubfix"
                                model_name="${base_model_name}.withsubfix"
                                features="${lcrf_features},prefix,suffix"
                            else
                                experiment_name="${base_experiment_name}"
                                model_name="${base_model_name}"
                                features="${lcrf_features}"
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
                            echo ${experiment_name} ${disabled} ${use_gold} ${train_path} ${model_name}
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
                else
                    templates=("" "-templatePath experiments/template " "-templatePath experiments/template-full")
                    template_idx=0
                    for template_idx in 0 1 2; do
                        template=${templates[template_idx]}
                        if [ $template_idx -eq 1 ]; then
                            experiment_name="${base_experiment_name}.2bef"
                            model_name="${base_model_name}.2bef"
                        elif [ $template_idx -eq 2 ]; then
                            experiment_name="${base_experiment_name}.full"
                            model_name="${base_model_name}.full"
                        else
                            experiment_name="${base_experiment_name}"
                            model_name="${base_model_name}"
                        fi
                        if [ -a "${experiment_dir}/${model_name}.model" ]; then
                            if ${retest_existing}; then
                                train_path=""
                            else
                                continue
                            fi
                        else
                            train_path="-trainPath data/SMSNP.train"
                        fi
                        echo ${experiment_name} ${template} ${use_gold} ${train_path} ${model_name}
                        if ! ${dry_run}; then
                            time java -cp target/experiments-smsnp-1.0-SNAPSHOT.jar com.statnlp.experiment.smsnp.CRFPPMain \
                                ${train_path} \
                                -testPath data/SMSNP.${test_file} \
                                -tokenizer ${tokenizer} \
                                ${use_gold} \
                                -C 4.0 \
                                ${template} \
                                -logPath ${experiment_dir}/${experiment_name}.log \
                                -modelPath ${experiment_dir}/${model_name}.model \
                                -resultPath ${experiment_dir}/${experiment_name}.result \
                                -writeModelText \
                                -numExamplesPrinted 0 \
                                2>&1 | tee ${experiment_dir}/${experiment_name}.runlog
                        fi
                    done
                    experiment_name="${base_experiment_name}.brown"
                    model_name="${base_model_name}.brown"
                    brown_path="63-c100-p1.out/paths"
                    if [ -a "${experiment_dir}/${model_name}.model" ]; then
                        if ${retest_existing}; then
                            train_path=""
                        else
                            continue
                        fi
                    else
                        train_path="-trainPath data/SMSNP.train"
                    fi
                    echo ${experiment_name} ${template} ${use_gold} ${train_path} ${model_name} -brownPath ${brown_path}
                    if ! ${dry_run}; then
                        time java -cp target/experiments-smsnp-1.0-SNAPSHOT.jar com.statnlp.experiment.smsnp.CRFPPMain \
                            ${train_path} \
                            -testPath data/SMSNP.${test_file} \
                            -brownPath ${brown_path} \
                            -tokenizer ${tokenizer} \
                            ${use_gold} \
                            -C 4.0 \
                            -logPath ${experiment_dir}/${experiment_name}.log \
                            -modelPath ${experiment_dir}/${model_name}.model \
                            -resultPath ${experiment_dir}/${experiment_name}.result \
                            -writeModelText \
                            -numExamplesPrinted 0 \
                            2>&1 | tee ${experiment_dir}/${experiment_name}.runlog
                    fi
                fi
            done
        done
    done
done
if ${do_tokenized_gold}; then
    for test_file in "train" "dev" "test"; do
        for tokenizer in "regex" "whitespace"; do
            for use_gold in "" "-useGoldTokenization"; do
                experiment_name=tokenized_gold.${tokenizer}.${test_file}
                if [ -n "${use_gold}" ]; then
                    experiment_name=${experiment_name}.gold_tokenized
                fi
                if [ -a "${experiment_dir}/${experiment_name}.log" ]; then
                    if ! ${retest_existing}; then
                        continue
                    fi
                fi
                echo ${experiment_name} ${use_gold}
                if ! ${dry_run}; then
                    time java -jar target/experiments-smsnp-1.0-SNAPSHOT.jar \
                        -algo TOKENIZED_GOLD \
                        -testPath data/SMSNP.${test_file} \
                        ${use_gold} \
                        -logPath ${experiment_dir}/${experiment_name}.log
                fi
            done
        done
    done
fi
